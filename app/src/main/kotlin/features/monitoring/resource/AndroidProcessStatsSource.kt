// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.resource

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import app.AppState
import app.modes.isRootRunMode
import engine.root.runtime.RootSupervisorController
import engine.root.runtime.runningCorePid
import engine.vpn.AsteriskVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import system.AndroidRootShellGateway
import system.ShellExecOptions
import utils.shellQuote
import java.io.File

internal enum class ProcessStatsSourceKind {
    CoreProcess,
    VpnProcess,
}

internal data class ProcessStatsReading(
    val snapshot: ProcessTickSnapshot,
    val source: ProcessStatsSourceKind,
    val uptimeMillis: Long?,
    val memoryBytes: Long? = null,
)

internal class AndroidProcessStatsSource(
    context: Context,
    private val rootAccess: AndroidRootShellGateway,
) {
    private val rootSupervisor = RootSupervisorController(context, rootAccess)
    private val activityManager = context.applicationContext.getSystemService(ActivityManager::class.java)
    private val vpnProcessName = context.packageName + AsteriskVpnService.ServiceProcessSuffix

    suspend fun read(appState: AppState): ProcessStatsReading? = withContext(Dispatchers.IO) {
        if (!appState.proxyRunning) return@withContext null
        if (appState.runMode.isRootRunMode()) readRootProcess() else readVpnProcess()
    }

    private fun readVpnProcess(): ProcessStatsReading? = runCatching {
        val runningProcess = activityManager?.runningAppProcesses?.firstOrNull { process ->
            process.uid == Process.myUid() && process.processName == vpnProcessName
        } ?: return@runCatching null
        val process = parseProcessStat(File("/proc/${runningProcess.pid}/stat").readText())
            ?: return@runCatching null
        val clockTicksPerSecond = Os.sysconf(OsConstants._SC_CLK_TCK)
        if (clockTicksPerSecond <= 0L) return@runCatching null
        val elapsedRealtimeMillis = SystemClock.elapsedRealtime()
        val processStartElapsedRealtimeMillis =
            Math.multiplyExact(process.startTimeTicks, 1_000L) / clockTicksPerSecond
        val snapshot = buildAppProcessTickSnapshot(
            pid = process.pid,
            processCpuMillis = Math.multiplyExact(process.processTicks, 1_000L) / clockTicksPerSecond,
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            processorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            processStartElapsedRealtimeMillis = processStartElapsedRealtimeMillis,
        ) ?: return@runCatching null
        val memoryBytes = runCatching {
            parseProcessResidentMemoryBytes(
                procStatm = File("/proc/${process.pid}/statm").readText(),
                pageSizeBytes = Os.sysconf(OsConstants._SC_PAGESIZE),
            )
        }.getOrNull()
        ProcessStatsReading(
            snapshot = snapshot,
            source = ProcessStatsSourceKind.VpnProcess,
            uptimeMillis = (elapsedRealtimeMillis - processStartElapsedRealtimeMillis).coerceAtLeast(0L),
            memoryBytes = memoryBytes,
        )
    }.getOrNull()

    private suspend fun readRootProcess(): ProcessStatsReading? {
        val pid = runCatching { rootSupervisor.runningCorePid() }.getOrNull() ?: return null
        val command = """
            pid=${pid.toString().shellQuote()}
            [ -r "/proc/${'$'}pid/stat" ] || exit 4
            IFS= read -r cpu_line < /proc/stat || exit 5
            printf '%s\n' "${'$'}cpu_line"
            cat "/proc/${'$'}pid/stat"
        """.trimIndent()
        val result = runCatching {
            rootAccess.exec(command, ShellExecOptions(logFailure = false))
        }.getOrNull() ?: return null
        if (result.errno != 0) return null
        val lines = result.stdout.lineSequence().filter(String::isNotBlank).toList()
        if (lines.size < 2) return null
        return buildReading(
            aggregateStat = lines.first(),
            processStat = lines.drop(1).joinToString("\n"),
            source = ProcessStatsSourceKind.CoreProcess,
        )
    }

    private fun buildReading(
        aggregateStat: String,
        processStat: String,
        source: ProcessStatsSourceKind,
    ): ProcessStatsReading? {
        val totalTicks = parseTotalCpuTicks(aggregateStat) ?: return null
        val process = parseProcessStat(processStat) ?: return null
        val processorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val snapshot = ProcessTickSnapshot(
            pid = process.pid,
            processTicks = process.processTicks,
            totalCpuTicks = totalTicks,
            processorCount = processorCount,
            startTimeTicks = process.startTimeTicks,
        )
        return ProcessStatsReading(
            snapshot = snapshot,
            source = source,
            uptimeMillis = processUptimeMillis(process.startTimeTicks),
        )
    }

    private fun processUptimeMillis(startTimeTicks: Long): Long? {
        val clockTicksPerSecond = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }
            .getOrNull()
            ?.takeIf { ticks -> ticks > 0L }
            ?: return null
        val startMillis = runCatching {
            Math.multiplyExact(startTimeTicks, 1_000L) / clockTicksPerSecond
        }.getOrNull() ?: return null
        return (SystemClock.elapsedRealtime() - startMillis).coerceAtLeast(0L)
    }
}
