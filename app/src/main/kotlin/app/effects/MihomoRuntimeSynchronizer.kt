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

@Composable
internal fun MihomoRuntimeSynchronizer(
    stateStore: AndroidAppStateStore,
    proxyEngine: AndroidProxyEngine,
    mihomoRuntimeLifecycle: MihomoRuntimeLifecycleCoordinator,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    LaunchedEffect(stateStore, proxyEngine, mihomoRuntimeLifecycle) {
        syncStartupProxyStatus(
            stateStore = stateStore,
            proxyEngine = proxyEngine,
            updateAppState = updateAppState,
        )
        stateStore.state
            .collect { appState ->
                mihomoRuntimeLifecycle.updateAppState(appState)
            }
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
