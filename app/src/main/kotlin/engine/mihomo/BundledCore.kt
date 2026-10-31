// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import android.content.Context
import app.AppState
import app.ProjectInfo
import app.modes.RunModeTun
import engine.mihomo.binding.MihomoBridge
import features.logs.AndroidAppLogger
import kotlinx.serialization.SerialName
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
    if (BundledCore.id != MeowCoreId) return this
    val defaults = AppState()
    val resetRunMode = runMode == RunModeTun
    return copy(
        runMode = if (resetRunMode) defaults.runMode else runMode,
        enableAutoConnect = if (resetRunMode) defaults.enableAutoConnect else enableAutoConnect,
        enableRootBootScript = if (resetRunMode) defaults.enableRootBootScript else enableRootBootScript,
        mihomoTunStack = defaults.mihomoTunStack,
        mihomoTunCongestionController = defaults.mihomoTunCongestionController,
        dnsRespectRules = defaults.dnsRespectRules,
        dnsPreferH3 = defaults.dnsPreferH3,
        connectionTestUnifiedDelay = defaults.connectionTestUnifiedDelay,
        enableGeodataMode = defaults.enableGeodataMode,
        mihomoGeodataLoader = defaults.mihomoGeodataLoader,
    )
}

@Serializable
internal data class CoreDnsCapabilities(
    @SerialName("respect-rules")
    val respectRules: Boolean = false,
    @SerialName("prefer-h3")
    val preferH3: Boolean = false,
)

@Serializable
internal data class CoreCapabilities(
    val core: String = "",
    val version: String = "",
    val tunStacks: List<String> = emptyList(),
    val selectableCongestionController: Boolean = false,
    val congestionControllers: List<String> = emptyList(),
    val congestionControllerStacks: List<String> = emptyList(),
    val rootTun: Boolean = false,
    val dnsOptions: CoreDnsCapabilities = CoreDnsCapabilities(),
) {
    val appliesUnifiedDelay: Boolean
        get() = core != MeowCoreId

    fun supportsRunMode(runMode: Int): Boolean {
        return runMode != RunModeTun || rootTun
    }

    fun effectiveTunStack(savedStack: String): String {
        if (savedStack in tunStacks) return savedStack
        return tunStacks.firstOrNull() ?: savedStack
    }
}

internal object CoreCapabilitiesStore {
    val current: CoreCapabilities by lazy {
        Json { ignoreUnknownKeys = true }.decodeFromString<CoreCapabilities>(MihomoBridge.capabilities())
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
