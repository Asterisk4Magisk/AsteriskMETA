// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import app.AppServices
import app.AppState
import app.withMihomoRestartApplied
import engine.mihomo.selectedMihomoProfileOrNull
import engine.proxy.ProxyServiceResult
import features.home.HomeServiceOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Whether the shared service controls are free, so a pending profile can be applied now. */
internal val AppServices.profileApplyAvailable: Boolean
    get() = homeServiceOperationState.operation == HomeServiceOperation.Idle &&
        !homeServiceOperationState.modeOperationInProgress

/**
 * Applies the pending profile change to the running core: an in-place reload in VPN mode, a
 * supervised restart in ROOT modes (see [engine.proxy.ProxyServiceUseCase.applyConfiguration]).
 *
 * Runs on the app scope and claims the home power button's operation state, so leaving the
 * page cannot drop the result halfway and the two controls exclude each other. [onApplied]
 * runs only on success and only while [isPageActive], so a page that was already left is
 * never popped or updated. Returns false when another service operation is in progress.
 */
internal fun AppServices.launchPendingProfileApply(
    currentState: () -> AppState,
    updateAppState: ((AppState) -> AppState) -> Unit,
    appliedMessage: String,
    failedMessage: String,
    isPageActive: () -> Boolean,
    onApplied: () -> Unit,
): Boolean {
    if (!profileApplyAvailable) return false
    val snapshot = currentState()
    homeServiceOperationState.operation = HomeServiceOperation.Applying
    appScope.launch(Dispatchers.Main.immediate) {
        try {
            when (val result = proxyServiceUseCase.applyConfiguration(snapshot)) {
                is ProxyServiceResult.Success -> {
                    updateAppState { state ->
                        val applied = state.copy(
                            proxyRunning = result.proxyRunning,
                            localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                            mihomoControlPort = result.appState?.mihomoControlPort ?: state.mihomoControlPort,
                        )
                        // The core now runs [snapshot]'s profile. If that profile changed again
                        // while the apply was in flight, the newer change is still pending.
                        if (state.selectedMihomoProfileOrNull() == snapshot.selectedMihomoProfileOrNull()) {
                            applied.withMihomoRestartApplied()
                        } else {
                            applied
                        }
                    }
                    if (result.proxyRunning) tipNotifier.show(appliedMessage)
                    if (isPageActive()) onApplied()
                }

                is ProxyServiceResult.Failed -> {
                    // A failed restart may already have stopped the core, so read back what is
                    // really running instead of assuming either way.
                    val running = proxyServiceUseCase.isRunning(snapshot)
                    if (running != null) {
                        updateAppState { state -> state.copy(proxyRunning = running) }
                    }
                    tipNotifier.showError(result.error, failedMessage)
                }
            }
        } finally {
            homeServiceOperationState.operation = HomeServiceOperation.Idle
        }
    }
    return true
}
