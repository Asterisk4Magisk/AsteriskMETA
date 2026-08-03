// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import app.AppState
import app.DefaultMihomoProfileId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class MihomoProviderUsageStateHolder(
    private val scope: CoroutineScope,
    private val load: suspend (AppState) -> MihomoProviderUsageLoadState,
) {
    private val lock = Any()
    private val mutableState =
        MutableStateFlow(KeyedMihomoProviderUsageState<MihomoProviderUsageLoadKey>())

    val state: StateFlow<KeyedMihomoProviderUsageState<MihomoProviderUsageLoadKey>> =
        mutableState.asStateFlow()

    private var initialized = false
    private var acceptedProfileId = DefaultMihomoProfileId
    private var requestId = 0L
    private var activeJob: Job? = null

    fun initialize(appState: AppState) {
        synchronized(lock) {
            if (initialized) {
                updateLocked(appState, force = false)
                return
            }
            initialized = true
            acceptedProfileId = appState.selectedMihomoProfileId
            startLoadLocked(appState, force = false)
        }
    }

    fun updateAppState(appState: AppState) {
        synchronized(lock) {
            if (!initialized) {
                initialized = true
                acceptedProfileId = appState.selectedMihomoProfileId
                startLoadLocked(appState, force = false)
                return
            }
            updateLocked(appState, force = false)
        }
    }

    fun refresh(appState: AppState) {
        synchronized(lock) {
            if (!initialized) {
                initialized = true
                acceptedProfileId = appState.selectedMihomoProfileId
            }
            updateLocked(appState, force = true)
        }
    }

    private fun updateLocked(appState: AppState, force: Boolean) {
        if (!isSafeToLoadLocked(appState)) return
        acceptedProfileId = appState.selectedMihomoProfileId
        startLoadLocked(appState, force)
    }

    private fun isSafeToLoadLocked(appState: AppState): Boolean {
        if (!appState.proxyRunning) return true
        if (appState.selectedMihomoProfileId != acceptedProfileId) return false

        val selectedProfileId = appState.selectedMihomoProfileId
        val restartPending = selectedProfileId != DefaultMihomoProfileId &&
            appState.pendingMihomoRestartProfileId == selectedProfileId
        val requestedKey = appState.selectedMihomoProviderUsageLoadKeyOrNull()
        return !restartPending || requestedKey == mutableState.value.loadKey
    }

    private fun startLoadLocked(appState: AppState, force: Boolean) {
        val loadKey = appState.selectedMihomoProviderUsageLoadKeyOrNull()
        val current = mutableState.value
        if (!force && current.loadKey == loadKey) return

        requestId += 1L
        val currentRequestId = requestId
        activeJob?.cancel()

        if (loadKey == null) {
            activeJob = null
            mutableState.value = KeyedMihomoProviderUsageState()
            return
        }

        mutableState.value = KeyedMihomoProviderUsageState(
            loadKey = loadKey,
            state = MihomoProviderUsageLoadState.Loading,
        )
        activeJob = scope.launch {
            val loadedState = try {
                load(appState)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                MihomoProviderUsageLoadState.Failed
            }
            synchronized(lock) {
                if (requestId != currentRequestId || mutableState.value.loadKey != loadKey) {
                    return@synchronized
                }
                mutableState.value = KeyedMihomoProviderUsageState(
                    loadKey = loadKey,
                    state = loadedState,
                )
                activeJob = null
            }
        }
    }
}
