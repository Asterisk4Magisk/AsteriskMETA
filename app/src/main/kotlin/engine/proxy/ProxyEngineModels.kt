// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState

data class ProxyEngineStartRequest(
    val appState: AppState,
)

data class ProxyEngineStatus(
    val running: Boolean,
    val runMode: Int? = null,
    val appState: AppState? = null,
)
