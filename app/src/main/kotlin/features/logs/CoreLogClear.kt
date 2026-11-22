// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import android.content.Context
import engine.mihomo.MihomoCoreLogPaths
import engine.mihomo.clearCoreLogFilesAsApp
import engine.mihomo.prepareMihomoCoreLogPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun Context.clearCoreLogFile(logFile: MihomoLogFile) {
    val logPath = applicationContext.prepareMihomoCoreLogPaths().pathOf(logFile)
    if (logPath.isBlank()) {
        return
    }

    withContext(Dispatchers.IO) {
        clearCoreLogFilesAsApp(
            logPaths = listOf(logPath),
            logTag = LogTag,
        )
    }
}

private fun MihomoCoreLogPaths.pathOf(logFile: MihomoLogFile): String {
    return when (logFile) {
        MihomoLogFile.Error -> errorLogPath
    }
}

internal enum class MihomoLogFile {
    Error,
}

private const val LogTag = "CoreLogClear"
