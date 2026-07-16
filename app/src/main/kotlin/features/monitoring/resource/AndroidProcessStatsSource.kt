// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.resource

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import app.AppState
import app.modes.isRootRunMode
import engine.root.RootPidFileName
import features.resources.runtime.mihomoResourceFilesDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import system.AndroidRootShellGateway
import system.ShellExecOptions
import utils.shellQuote
import java.io.File

internal enum class ProcessStatsSourceKind {
    CoreProcess,
    AppProcess,
}

internal data class ProcessStatsReading(
    val snapshot: ProcessTickSnapshot,
    val source: ProcessStatsSourceKind,
    val uptimeMillis: Long?,
)

internal class AndroidProcessStatsSource(
    context: Context,
    private val rootAccess: AndroidRootShellGateway,
) {
    private val rootPidPath = File(context.applicationContext.mihomoResourceFilesDir(), RootPidFileName).absolutePath

    suspend fun read(appState: AppState): ProcessStatsReading? = withContext(Dispatchers.IO) {
        if (!appState.proxyRunning) return@withContext null
        if (appState.runMode.isRootRunMode()) readRootProcess() else readAppProcess()
    }

    private fun readAppProcess(): ProcessStatsReading? {
        val elapsedRealtimeMillis = SystemClock.elapsedRealtime()
        val processStartElapsedRealtimeMillis = Process.getStartElapsedRealtime()
        val snapshot = buildAppProcessTickSnapshot(
            pid = Process.myPid(),
            processCpuMillis = Process.getElapsedCpuTime(),
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            processorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            processStartElapsedRealtimeMillis = processStartElapsedRealtimeMillis,
        ) ?: return null
        return ProcessStatsReading(
            snapshot = snapshot,
            source = ProcessStatsSourceKind.AppProcess,
            uptimeMillis = (elapsedRealtimeMillis - processStartElapsedRealtimeMillis).coerceAtLeast(0L),
        )
    }

    private suspend fun readRootProcess(): ProcessStatsReading? {
        val command = """
            pid_file=${rootPidPath.shellQuote()}
            [ -r "${'$'}pid_file" ] || exit 2
            pid=${'$'}(cat "${'$'}pid_file" 2>/dev/null)
            case "${'$'}pid" in ''|*[!0-9]*) exit 3 ;; esac
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
