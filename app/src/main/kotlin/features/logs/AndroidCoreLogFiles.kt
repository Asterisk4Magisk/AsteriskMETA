// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import android.content.Context
import java.io.File

internal fun Context.androidMihomoErrorLog(): CoreLogFile {
    return CoreLogFile(path = androidCoreLogErrorFile().absolutePath, defaultLevel = "error")
}

internal fun Context.androidCoreLogErrorFile(): File {
    return File(androidClashLogDirectory(), "error.log")
}

internal fun Context.androidAppLogcatFile(): File {
    return File(androidClashLogDirectory(), "logcat.log")
}

private fun Context.androidClashLogDirectory(): File {
    return File(filesDir, AndroidClashLogDirectoryPath).apply {
        mkdirs()
    }
}

private const val AndroidClashLogDirectoryPath = "clash/logs"
