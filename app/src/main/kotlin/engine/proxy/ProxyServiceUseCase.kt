// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState
import engine.mihomo.requireUsableMihomoProfile

internal class ProxyServiceUseCase(
    private val proxyEngine: AndroidProxyEngine,
) {
    suspend fun toggle(state: AppState): ProxyServiceResult {
        return if (state.proxyRunning) {
            stop(state.runMode)
        } else {
            start(state)
        }
    }

    suspend fun restart(state: AppState): ProxyServiceResult {
        return runCatching {
            state.requireUsableMihomoProfile()
            proxyEngine.restart(ProxyEngineStartRequest(state))
        }.fold(
            onSuccess = { status -> ProxyServiceResult.Success(proxyRunning = status.running, appState = status.appState) },
            onFailure = { error -> ProxyServiceResult.Failed(error) },
        )
    }

    private suspend fun start(state: AppState): ProxyServiceResult {
        return runCatching {
            state.requireUsableMihomoProfile()
            proxyEngine.start(ProxyEngineStartRequest(state))
        }.fold(
            onSuccess = { status -> ProxyServiceResult.Success(proxyRunning = status.running, appState = status.appState) },
            onFailure = { error -> ProxyServiceResult.Failed(error) },
        )
    }

    suspend fun stop(runMode: Int): ProxyServiceResult {
        return runCatching { proxyEngine.stop(runMode) }.fold(
            onSuccess = { status -> ProxyServiceResult.Success(proxyRunning = status.running, appState = status.appState) },
            onFailure = { error -> ProxyServiceResult.Failed(error) },
        )
    }
}

internal sealed interface ProxyServiceResult {
    data class Success(
        val proxyRunning: Boolean,
        val appState: AppState? = null,
    ) : ProxyServiceResult

    data class Failed(val error: Throwable) : ProxyServiceResult
}
