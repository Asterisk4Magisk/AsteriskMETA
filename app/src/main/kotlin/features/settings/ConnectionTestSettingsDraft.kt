// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import app.AppState
import app.DefaultConnectionTestUrl
import app.DefaultConnectionTestConcurrent
import app.DefaultConnectionTestTimeoutMillis
import app.DefaultConnectionTestUnifiedDelay
import app.isValidConnectionTestUrl
import app.withNormalizedConnectionTestSettings

internal data class ConnectionTestSettingsDraft(
    val url: String = DefaultConnectionTestUrl,
    val concurrent: Int = DefaultConnectionTestConcurrent,
    val timeoutMillis: Int = DefaultConnectionTestTimeoutMillis,
    val unifiedDelay: Boolean = DefaultConnectionTestUnifiedDelay,
)

internal fun AppState.toConnectionTestSettingsDraft() = ConnectionTestSettingsDraft(
    url = connectionTestUrl,
    concurrent = connectionTestConcurrent,
    timeoutMillis = connectionTestTimeoutMillis,
    unifiedDelay = connectionTestUnifiedDelay,
)

internal fun AppState.withConnectionTestSettings(draft: ConnectionTestSettingsDraft): AppState {
    require(isValidConnectionTestUrl(draft.url))
    return copy(
        connectionTestUrl = draft.url,
        connectionTestConcurrent = draft.concurrent,
        connectionTestTimeoutMillis = draft.timeoutMillis,
        connectionTestUnifiedDelay = draft.unifiedDelay,
    ).withNormalizedConnectionTestSettings()
}
