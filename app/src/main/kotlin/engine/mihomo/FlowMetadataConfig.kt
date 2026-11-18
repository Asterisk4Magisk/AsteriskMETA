// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import engine.proxy.FlowMetadataInboundTag
import engine.proxy.LocalProxyOptions

internal fun Map<String, Any?>.withFlowMetadata(options: LocalProxyOptions?): Map<String, Any?> {
    if (options == null) return this

    val listeners = (this["listeners"] as? List<*>).orEmpty().filterNot {
        (it as? Map<*, *>)?.get("name") == FlowMetadataInboundTag
    }
    val listener = mapOf(
        "name" to FlowMetadataInboundTag,
        "type" to if (BundledCore.id == MeowCoreId) "socks5" else "socks",
        "listen" to options.listenAddress,
        "port" to options.port,
        "udp" to true,
        "users" to listOf(mapOf("username" to options.username, "password" to options.password)),
    )
    return this + ("listeners" to (listeners + listener))
}
