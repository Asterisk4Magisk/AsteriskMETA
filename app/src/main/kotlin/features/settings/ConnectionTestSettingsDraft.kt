// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import app.AppState

internal data class ConnectionTestSettingsDraft(
    val url: String = DefaultConnectionTestUrl,
    val concurrent: Int = DefaultConnectionTestConcurrent,
    val timeoutMillis: Int = DefaultConnectionTestTimeoutMillis,
    val overrideUnifiedDelay: Boolean = DefaultConnectionTestOverrideUnifiedDelay,
)

internal fun AppState.toConnectionTestSettingsDraft() = ConnectionTestSettingsDraft(
    url = connectionTestUrl,
    concurrent = connectionTestConcurrent,
    timeoutMillis = connectionTestTimeoutMillis,
    overrideUnifiedDelay = overrideUnifiedDelay,
)

internal fun AppState.withConnectionTestSettings(draft: ConnectionTestSettingsDraft): AppState {
    require(isValidConnectionTestUrl(draft.url))
    return copy(
        connectionTestUrl = draft.url,
        connectionTestConcurrent = draft.concurrent,
        connectionTestTimeoutMillis = draft.timeoutMillis,
        overrideUnifiedDelay = draft.overrideUnifiedDelay,
    ).withNormalizedConnectionTestSettings()
}
