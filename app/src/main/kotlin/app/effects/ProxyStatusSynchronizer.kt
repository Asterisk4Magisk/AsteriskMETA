// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.AppState
import app.withMihomoRestartApplied
import app.modes.isRootRunMode
import data.AndroidAppStateStore
import engine.proxy.AndroidProxyEngine
import engine.proxy.ProxyEngineStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy

@Composable
internal fun ProxyStatusSynchronizer(
    stateStore: AndroidAppStateStore,
    proxyEngine: AndroidProxyEngine,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var foregroundSyncGeneration by remember(stateStore, proxyEngine) { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner, stateStore, proxyEngine) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                foregroundSyncGeneration += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(stateStore, proxyEngine, foregroundSyncGeneration) {
        observeProxyStatus(
            states = stateStore.state,
            readStatus = { snapshot ->
                runCatching { proxyEngine.status(snapshot.runMode, snapshot) }.getOrNull()
            },
            updateAppState = updateAppState,
        )
    }
}

internal suspend fun observeProxyStatus(
    states: Flow<AppState>,
    readStatus: suspend (AppState) -> ProxyEngineStatus?,
    updateAppState: (((AppState) -> AppState) -> Unit),
) {
    states
        .distinctUntilChangedBy { state ->
            Triple(state.runMode, state.proxyRunning, state.pendingMihomoRestartProfileId)
        }
        .collect { snapshot ->
            synchronizeProxyStatus(
                currentState = { snapshot },
                readStatus = readStatus,
                updateAppState = updateAppState,
            )
        }
}

internal suspend fun synchronizeProxyStatus(
    currentState: () -> AppState,
    readStatus: suspend (AppState) -> ProxyEngineStatus?,
    updateAppState: (((AppState) -> AppState) -> Unit),
): Boolean {
    val snapshot = currentState()
    val shouldCheckRuntime = snapshot.runMode.isRootRunMode() ||
        snapshot.proxyRunning ||
        snapshot.pendingMihomoRestartProfileId != 0
    if (!shouldCheckRuntime) return true

    val status = readStatus(snapshot) ?: return false
    updateAppState { state ->
        if (
            state.runMode != snapshot.runMode ||
            state.proxyRunning != snapshot.proxyRunning ||
            state.pendingMihomoRestartProfileId != snapshot.pendingMihomoRestartProfileId
        ) {
            return@updateAppState state
        }
        val synchronizedRunMode = status.runMode
            ?.takeIf { status.running && it.isRootRunMode() }
            ?: state.runMode
        val updated = if (
            state.proxyRunning == status.running &&
            state.runMode == synchronizedRunMode
        ) {
            state
        } else {
            state.copy(
                runMode = synchronizedRunMode,
                proxyRunning = status.running,
            )
        }
        if (status.running) updated else updated.withMihomoRestartApplied()
    }
    return true
}
