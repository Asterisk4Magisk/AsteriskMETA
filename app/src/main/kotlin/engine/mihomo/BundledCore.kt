// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import android.content.Context
import app.AppState
import app.ProjectInfo
import app.modes.RunModeTun
import engine.mihomo.binding.MihomoBridge
import features.logs.AndroidAppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

internal object BundledCore {
    val id: String
        get() = ProjectInfo.CORE_ID

    val displayName: String
        get() = if (id == MeowCoreId) "Meow" else "Mihomo"

    val launcherFileName: String
        get() = "lib$id.so"

    val usesRealMmdb: Boolean
        get() = id == MeowCoreId
}

internal const val MihomoCoreId = "mihomo"
internal const val MeowCoreId = "meow"

internal fun AppState.withCompatibleCoreSettings(): AppState {
    if (BundledCore.id != MeowCoreId || runMode != RunModeTun) return this
    val defaults = AppState()
    return copy(
        runMode = defaults.runMode,
        enableAutoConnect = defaults.enableAutoConnect,
        enableRootBootScript = defaults.enableRootBootScript,
    )
}

@Serializable
internal data class CoreCapabilities(
    val rootTun: Boolean = false,
) {
    fun supportsRunMode(runMode: Int): Boolean {
        return runMode != RunModeTun || rootTun
    }
}

internal object CoreCapabilitiesStore {
    private val json = Json { ignoreUnknownKeys = true }

    val current: CoreCapabilities by lazy {
        json.decodeFromString<CoreCapabilities>(MihomoBridge.capabilities())
    }
}

internal object BundledCoreMarker {
    private const val FileName = "bundled-core-id"

    fun needsRepublish(context: Context): Boolean {
        val recorded = runCatching {
            File(context.noBackupFilesDir, FileName).takeIf(File::isFile)?.readText()?.trim()
        }.getOrNull()
        return recorded != ProjectInfo.CORE_ID
    }

    fun record(context: Context) {
        val directory = context.noBackupFilesDir
        if (!directory.exists() && !directory.mkdirs()) return
        runCatching { File(directory, FileName).writeText(ProjectInfo.CORE_ID) }
            .onFailure { error ->
                AndroidAppLogger.warn(LogTag, "Failed to record the bundled core id", error)
            }
    }
}

private const val LogTag = "BundledCore"
