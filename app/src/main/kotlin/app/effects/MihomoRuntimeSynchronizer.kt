// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.AppState
import app.withMihomoRestartApplied
import app.modes.isRootRunMode
import data.AndroidAppStateStore
import engine.proxy.AndroidProxyEngine
import features.mihomo.provider.MihomoProviderUsageStateHolder

internal suspend fun runMihomoRuntimeSynchronization(
    initialState: AppState,
    updateRuntime: (AppState) -> Unit,
    updateProviderUsage: (AppState) -> Unit,
    synchronizeStatus: suspend () -> Unit,
    collectStates: suspend ((AppState) -> Unit) -> Unit,
) {
    updateRuntime(initialState)
    updateProviderUsage(initialState)
    synchronizeStatus()
    collectStates { appState ->
        updateRuntime(appState)
        updateProviderUsage(appState)
    }
}

@Composable
internal fun MihomoRuntimeSynchronizer(
    stateStore: AndroidAppStateStore,
    proxyEngine: AndroidProxyEngine,
    mihomoRuntimeLifecycle: MihomoRuntimeLifecycleCoordinator,
    mihomoProviderUsage: MihomoProviderUsageStateHolder,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    LaunchedEffect(stateStore, proxyEngine, mihomoRuntimeLifecycle, mihomoProviderUsage) {
        runMihomoRuntimeSynchronization(
            initialState = stateStore.state.value,
            updateRuntime = mihomoRuntimeLifecycle::updateAppState,
            updateProviderUsage = mihomoProviderUsage::initialize,
            synchronizeStatus = {
                syncStartupProxyStatus(
                    stateStore = stateStore,
                    proxyEngine = proxyEngine,
                    updateAppState = updateAppState,
                )
            },
            collectStates = { onState ->
                stateStore.state.collect { appState ->
                    onState(appState)
                }
            },
        )
    }
}

private suspend fun syncStartupProxyStatus(
    stateStore: AndroidAppStateStore,
    proxyEngine: AndroidProxyEngine,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    val currentState = stateStore.state.value
    if (!currentState.shouldCheckProxyStatusBeforeRuntimeSync()) {
        return
    }
    val status = runCatching { proxyEngine.status(currentState.runMode, currentState) }.getOrNull() ?: return
    updateAppState { state ->
        val runMode = status.runMode.takeIf { mode -> mode?.isRootRunMode() == true } ?: state.runMode
        state.copy(
            proxyRunning = status.running,
            runMode = runMode,
        ).let { updated -> if (status.running) updated else updated.withMihomoRestartApplied() }
    }
}

private fun AppState.shouldCheckProxyStatusBeforeRuntimeSync(): Boolean {
    return proxyRunning || runMode.isRootRunMode() || pendingMihomoRestartProfileId != 0
}
