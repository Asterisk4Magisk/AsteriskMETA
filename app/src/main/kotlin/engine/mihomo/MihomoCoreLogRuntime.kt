// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import features.logs.AndroidAppLogger
import features.logs.AndroidCoreLogRepository
import features.logs.CoreLogFile
import features.logs.CoreLogFileTailer
import java.io.File

internal fun MihomoCoreLogPaths.startCoreLogTailers(): List<CoreLogFileTailer> {
    return buildList {
        add(
            CoreLogFileTailer(
                logFiles = listOf(errorLogFile()),
                repository = AndroidCoreLogRepository,
            ),
        )
    }.onEach { tailer -> tailer.start() }
}

internal fun MihomoCoreLogPaths.clearCoreLogs(logTag: String) {
    AndroidCoreLogRepository.clear()
    clearCoreLogFilesAsApp(
        logPaths = logFilePaths(),
        logTag = logTag,
    )
}

internal fun MihomoCoreLogPaths.logFilePaths(): List<String> {
    return listOf(errorLogPath).filter(String::isNotBlank)
}

internal fun clearCoreLogFilesAsApp(logPaths: List<String>, logTag: String) {
    logPaths
        .filter(String::isNotBlank)
        .forEach { logPath ->
            runCatching {
                File(logPath).apply {
                    parentFile?.mkdirs()
                    writeText("")
                }
            }.onFailure { error ->
                AndroidAppLogger.warn(logTag, "Failed to clear Mihomo log file: $logPath", error)
            }
        }
}

private fun MihomoCoreLogPaths.errorLogFile(): CoreLogFile {
    return CoreLogFile(path = errorLogPath, defaultLevel = "error")
}
