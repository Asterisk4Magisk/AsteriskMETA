// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import engine.network.NetworkDefaults
import app.AppState

const val DefaultConnectionTestUrl = NetworkDefaults.CONNECTIVITY_CHECK_URL
const val DefaultConnectionTestConcurrent = 8
const val DefaultConnectionTestTimeoutMillis = 5000
const val DefaultConnectionTestOverrideUnifiedDelay = true

internal const val ConnectionTestDefaultsVersion = 1
private const val LegacyDefaultConnectionTestUrl = "http://cp.cloudflare.com/"
private const val LegacyDefaultConnectionTestConcurrent = 5

internal data class ConnectionTestDefaultsMigration(val url: String, val concurrent: Int, val version: Int)

internal fun migrateConnectionTestDefaults(url: String, concurrent: Int, version: Int): ConnectionTestDefaultsMigration {
    if (version >= ConnectionTestDefaultsVersion) return ConnectionTestDefaultsMigration(url, concurrent, version)
    // Older settings store values, not user intent. Migrate exact old defaults once; after that,
    // explicitly choosing those same values is supported, including through backup/restore.
    return ConnectionTestDefaultsMigration(
        url = if (url.trim() == LegacyDefaultConnectionTestUrl) DefaultConnectionTestUrl else url,
        concurrent = if (concurrent == LegacyDefaultConnectionTestConcurrent) DefaultConnectionTestConcurrent else concurrent,
        version = ConnectionTestDefaultsVersion,
    )
}

data class ConnectionTestIntBounds(val min: Int, val max: Int)
val ConnectionTestConcurrentBounds = ConnectionTestIntBounds(min = 1, max = 32)
val ConnectionTestTimeoutBounds = ConnectionTestIntBounds(min = 1000, max = 8000)
const val ConnectionTestTimeoutStepMillis = 500

fun isValidConnectionTestUrl(url: String): Boolean {
    val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}

fun AppState.withNormalizedConnectionTestSettings(): AppState = copy(
    connectionTestUrl = connectionTestUrl.trim().takeIf(::isValidConnectionTestUrl) ?: DefaultConnectionTestUrl,
    connectionTestConcurrent = connectionTestConcurrent.coerceIn(
        ConnectionTestConcurrentBounds.min,
        ConnectionTestConcurrentBounds.max,
    ),
    connectionTestTimeoutMillis = connectionTestTimeoutMillis.coerceIn(
        ConnectionTestTimeoutBounds.min,
        ConnectionTestTimeoutBounds.max,
    ),
)
