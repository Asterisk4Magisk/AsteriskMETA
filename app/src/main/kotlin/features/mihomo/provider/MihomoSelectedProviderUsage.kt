// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import app.AppState
import app.DefaultMihomoOverrideScriptId
import app.MihomoOverrideScriptState
import app.MihomoProfileState
import engine.mihomo.runtime.MihomoProxyProviderRuntimeDetail
import kotlin.coroutines.cancellation.CancellationException

internal data class MihomoProviderUsageLoadKey(
    val profileId: Int,
    val contentPath: String,
    val contentSha256: String,
    val contentSizeBytes: Long,
    val disableOverrides: Boolean,
    val ageSecretKey: String,
    val overrideScriptId: Int,
    val overrideScript: MihomoOverrideScriptState?,
    val proxyRunning: Boolean,
    val runMode: Int,
    val controlPort: String,
    val controlSecret: String,
    val reloadToken: Int,
)

internal fun MihomoProfileState.providerMetadataContentKey(): String {
    return contentSha256
        .takeIf(String::isNotBlank)
        ?.let { sha256 -> "profile:$sha256" }
        ?: "profile-path:$contentPath:$contentSizeBytes"
}

internal fun MihomoProfileState.toMihomoProviderUsageLoadKey(
    appState: AppState,
    reloadToken: Int,
): MihomoProviderUsageLoadKey = MihomoProviderUsageLoadKey(
    profileId = id,
    contentPath = contentPath,
    contentSha256 = contentSha256,
    contentSizeBytes = contentSizeBytes,
    disableOverrides = disableOverrides,
    ageSecretKey = ageSecretKey,
    overrideScriptId = overrideScriptId,
    overrideScript = appState.mihomoOverrideScripts
        .firstOrNull { script -> script.id == overrideScriptId },
    proxyRunning = appState.proxyRunning,
    runMode = appState.runMode,
    controlPort = appState.mihomoControlPort,
    controlSecret = appState.mihomoControlSecret,
    reloadToken = reloadToken,
)

internal suspend fun loadSelectedMihomoProviderNames(
    profile: MihomoProfileState,
    loadSource: suspend () -> List<String>,
    loadEffective: suspend () -> List<String>,
): List<String> {
    val customOverrideEnabled = !profile.disableOverrides &&
        profile.overrideScriptId != DefaultMihomoOverrideScriptId
    return if (customOverrideEnabled) loadEffective() else loadSource()
}

internal suspend fun loadMihomoProviderUsageStateCatching(
    load: suspend () -> MihomoProviderUsageLoadState,
): MihomoProviderUsageLoadState {
    return try {
        load()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        MihomoProviderUsageLoadState.Failed
    }
}

internal suspend fun resolveMihomoProviderUsageState(
    providerNames: List<String>,
    rawConfiguration: Boolean,
    proxyRunning: Boolean,
    fetchDetail: suspend (String) -> Result<MihomoProxyProviderRuntimeDetail>,
): MihomoProviderUsageLoadState {
    val preflightState = resolveMihomoProviderUsagePreflightState(
        providerCount = providerNames.size,
        rawConfiguration = rawConfiguration,
        proxyRunning = proxyRunning,
    )
    return preflightState ?: loadMihomoProviderUsage(
        providerNames = providerNames,
        fetchDetail = fetchDetail,
    ).toMihomoProviderUsageLoadState()
}
