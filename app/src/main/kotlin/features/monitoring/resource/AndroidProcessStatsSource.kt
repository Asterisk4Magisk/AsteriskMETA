// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.resource

import android.content.Context
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
    private val context: Context,
    private val rootAccess: AndroidRootShellGateway,
) {
    private val rootSupervisor = RootSupervisorController(context, rootAccess)

    suspend fun read(appState: AppState): ProcessStatsReading? = withContext(Dispatchers.IO) {
        if (!appState.proxyRunning) return@withContext null
        if (appState.runMode.isRootRunMode()) readRootProcess() else readVpnProcess()
    }

    private suspend fun readVpnProcess(): ProcessStatsReading? {
        val stats = AsteriskVpnService.queryProcessStats(context) ?: return null
        val snapshot = buildAppProcessTickSnapshot(
            pid = stats.pid,
            processCpuMillis = stats.cpuTimeMillis,
            elapsedRealtimeMillis = stats.elapsedRealtimeMillis,
            processorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            processStartElapsedRealtimeMillis = stats.startElapsedRealtimeMillis,
        ) ?: return null
        val memoryBytes = stats.procStatm?.let { procStatm ->
            runCatching {
                parseProcessResidentMemoryBytes(
                    procStatm = procStatm,
                    pageSizeBytes = Os.sysconf(OsConstants._SC_PAGESIZE),
                )
            }.getOrNull()
        }
        return ProcessStatsReading(
            snapshot = snapshot,
            source = ProcessStatsSourceKind.VpnProcess,
            uptimeMillis = (stats.elapsedRealtimeMillis - stats.startElapsedRealtimeMillis).coerceAtLeast(0L),
            memoryBytes = memoryBytes,
        )
    }

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
