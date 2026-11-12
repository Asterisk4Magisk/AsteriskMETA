// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import java.util.Locale

internal enum class ConnectionRouteKind {
    Proxy,
    Direct,
}

internal data class ConnectionCounts(
    val activeCount: Int,
    val directCount: Int,
    val proxyCount: Int,
)

internal fun List<MihomoConnection>.connectionCounts(): ConnectionCounts {
    var directCount = 0
    var proxyCount = 0
    forEach { connection ->
        when (connection.resolvedConnectionRoute()) {
            ConnectionRouteKind.Direct -> directCount += 1
            ConnectionRouteKind.Proxy -> proxyCount += 1
            null -> Unit
        }
    }
    return ConnectionCounts(size, directCount, proxyCount)
}

internal fun MihomoConnection.resolvedConnectionRoute(): ConnectionRouteKind? =
    resolveConnectionRoute(chains.firstOrNull(), outboundType)

internal fun resolveConnectionRoute(outbound: String?, outboundType: String): ConnectionRouteKind? =
    when (outboundType.lowercase(Locale.ROOT)) {
        "direct" -> ConnectionRouteKind.Direct
        "reject", "rejectdrop", "pass" -> null
        "", "proxy" -> when (outbound) {
            null, "", "REJECT", "REJECT-DROP", "PASS" -> null
            "DIRECT" -> ConnectionRouteKind.Direct
            else -> ConnectionRouteKind.Proxy
        }
        else -> ConnectionRouteKind.Proxy
}
