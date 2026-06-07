// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import android.content.Context
import features.logs.androidCoreLogErrorFile
import java.io.File

internal data class MihomoCoreLogPaths(
    val errorLogPath: String,
)

internal object MihomoTags {
    const val DNS_OUT = "dns-out"
}

internal fun Context.prepareMihomoCoreLogPaths(): MihomoCoreLogPaths {
    return MihomoCoreLogPaths(
        errorLogPath = androidCoreLogErrorFile().absolutePath,
    )
}

internal fun MihomoCoreLogPaths.logDirectoryPath(): String {
    return File(errorLogPath).parentFile?.absolutePath
        ?: error("Mihomo log directory is unavailable")
}
