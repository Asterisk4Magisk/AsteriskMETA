// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import app.AppServices
import app.AppState
import engine.proxy.ProxyServiceResult

internal suspend fun stopProxyServiceAfterProfileChange(
    appState: AppState,
    services: AppServices,
    updateAppState: ((AppState) -> AppState) -> Unit,
    stoppedMessage: String,
    stopFailedMessage: String,
) {
    if (!appState.proxyRunning) return
    when (val result = services.proxyServiceUseCase.stop(appState.runMode)) {
        is ProxyServiceResult.Success -> {
            updateAppState { state ->
                state.copy(
                    proxyRunning = result.proxyRunning,
                    localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                    mihomoControlPort = result.appState?.mihomoControlPort ?: state.mihomoControlPort,
                )
            }
            services.tipNotifier.show(stoppedMessage)
        }

        is ProxyServiceResult.Failed -> {
            updateAppState { state -> state.copy(proxyRunning = false) }
            services.tipNotifier.showError(result.error, stopFailedMessage)
        }
    }
}
