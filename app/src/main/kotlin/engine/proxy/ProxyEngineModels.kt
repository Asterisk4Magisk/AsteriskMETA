// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState
import engine.mihomo.raw.MihomoRawConfigCheckResult
import engine.mihomo.raw.MihomoRawConfigSnapshot

internal data class ProxyEngineStartRequest(
    val appState: AppState,
    val rawConfig: MihomoRawConfigSnapshot? = null,
    val rawConfigCheck: MihomoRawConfigCheckResult? = null,
)

internal data class ProxyEngineStatus(
    val running: Boolean,
    val runMode: Int? = null,
    val appState: AppState? = null,
)
