// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import android.content.Context
import app.AppState
import app.DefaultMihomoOverrideScriptId
import app.MihomoOverrideScriptState
import app.MihomoProfileState
import engine.mihomo.MihomoProfileContentStore
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.MihomoProviderMetadataCache
import engine.mihomo.parseMihomoProxyProviderNames
import engine.mihomo.selectedMihomoProfileOrNull
import engine.mihomo.runtime.MihomoProxyProviderRuntimeDetail
import engine.mihomo.runtime.MihomoRuntimeRepository
import engine.mihomo.raw.usesRawMihomoConfig
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
)

internal fun MihomoProfileState.providerMetadataContentKey(): String {
    return contentSha256
        .takeIf(String::isNotBlank)
        ?.let { sha256 -> "profile:$sha256" }
        ?: "profile-path:$contentPath:$contentSizeBytes"
}

internal fun MihomoProfileState.toMihomoProviderUsageLoadKey(
    appState: AppState,
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
)

internal fun AppState.selectedMihomoProviderUsageLoadKeyOrNull(): MihomoProviderUsageLoadKey? =
    selectedMihomoProfileOrNull()
        ?.takeIf { profile -> profile.hasContent }
        ?.toMihomoProviderUsageLoadKey(this)

internal fun refreshMihomoProviderUsageAfterSync(
    refreshRequired: Boolean,
    syncedProfileId: Int,
    appState: AppState,
    refresh: (AppState) -> Unit,
) {
    if (refreshRequired && appState.selectedMihomoProfileId == syncedProfileId) {
        refresh(appState)
    }
}

internal suspend fun loadSelectedMihomoProviderNames(
    profile: MihomoProfileState,
    loadSource: suspend () -> List<String>,
    loadEffective: suspend () -> List<String>,
): List<String> {
    val customOverrideEnabled = !profile.disableOverrides &&
        profile.overrideScriptId != DefaultMihomoOverrideScriptId
    return if (customOverrideEnabled) loadEffective() else loadSource()
}

internal suspend fun loadSelectedMihomoProviderUsageState(
    appState: AppState,
    loadSource: suspend (MihomoProfileState) -> List<String>,
    loadEffective: suspend () -> List<String>,
    fetchDetail: suspend (AppState, String) -> Result<MihomoProxyProviderRuntimeDetail>,
): MihomoProviderUsageLoadState {
    val profile = appState.selectedMihomoProfileOrNull()
        ?.takeIf { selected -> selected.hasContent }
        ?: return MihomoProviderUsageLoadState.Hidden
    return loadMihomoProviderUsageStateCatching {
        val providerNames = loadSelectedMihomoProviderNames(
            profile = profile,
            loadSource = { loadSource(profile) },
            loadEffective = loadEffective,
        )
        resolveMihomoProviderUsageState(
            providerNames = providerNames,
            rawConfiguration = appState.usesRawMihomoConfig(),
            proxyRunning = appState.proxyRunning,
        ) { providerName ->
            fetchDetail(appState, providerName)
        }
    }
}

internal suspend fun loadSelectedMihomoProviderUsageState(
    context: Context,
    contentStore: MihomoProfileContentStore,
    runtime: MihomoRuntimeRepository,
    appState: AppState,
): MihomoProviderUsageLoadState = loadSelectedMihomoProviderUsageState(
    appState = appState,
    loadSource = { profile ->
        withContext(Dispatchers.IO) {
            MihomoProviderMetadataCache.getProxyProviderNames(
                profile.providerMetadataContentKey(),
            ) {
                contentStore.useReader(profile) { reader ->
                    reader.parseMihomoProxyProviderNames()
                }
            }
        }
    },
    loadEffective = {
        withContext(Dispatchers.IO) {
            MihomoProfileFactory.buildProfile(context.applicationContext, appState)
                .parseMihomoProxyProviderNames()
        }
    },
    fetchDetail = { state, providerName ->
        runtime.getProxyProviderDetail(state, providerName).also { result ->
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
        }
    },
)

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
