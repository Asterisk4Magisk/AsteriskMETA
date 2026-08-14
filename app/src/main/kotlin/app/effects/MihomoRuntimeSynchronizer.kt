// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.AppState
import data.AndroidAppStateStore
import features.mihomo.provider.MihomoProviderUsageStateHolder

internal suspend fun runMihomoRuntimeSynchronization(
    initialState: AppState,
    updateRuntime: (AppState) -> Unit,
    updateProviderUsage: (AppState) -> Unit,
    collectStates: suspend ((AppState) -> Unit) -> Unit,
) {
    updateRuntime(initialState)
    updateProviderUsage(initialState)
    collectStates { appState ->
        updateRuntime(appState)
        updateProviderUsage(appState)
    }
}

@Composable
internal fun MihomoRuntimeSynchronizer(
    stateStore: AndroidAppStateStore,
    mihomoRuntimeLifecycle: MihomoRuntimeLifecycleCoordinator,
    mihomoProviderUsage: MihomoProviderUsageStateHolder,
) {
    LaunchedEffect(stateStore, mihomoRuntimeLifecycle, mihomoProviderUsage) {
        runMihomoRuntimeSynchronization(
            initialState = stateStore.state.value,
            updateRuntime = mihomoRuntimeLifecycle::updateAppState,
            updateProviderUsage = mihomoProviderUsage::initialize,
            collectStates = { onState ->
                stateStore.state.collect { appState ->
                    onState(appState)
                }
            },
        )
    }
}
