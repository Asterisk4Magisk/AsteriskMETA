// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import android.content.Context
import app.AppState
import engine.proxy.FlowMetadataInboundTag
import engine.proxy.flowMetadataOptions
import engine.proxy.usesFlowMetadata

internal fun ByteArray.withFlowMetadata(
    context: Context,
    state: AppState,
    runMode: Int = state.runMode,
    exposePorts: Boolean = true,
): ByteArray {
    if (!exposePorts || !state.usesFlowMetadata(runMode)) return this

    val root = requireNotNull(toString(Charsets.UTF_8).loadMihomoProfileObject()).toMutableMap()
    val options = context.flowMetadataOptions()
    val listeners = (root["listeners"] as? List<*>).orEmpty().filterNot {
        (it as? Map<*, *>)?.get("name") == FlowMetadataInboundTag
    }
    root["listeners"] = listeners + mapOf(
        "name" to FlowMetadataInboundTag,
        "type" to if (BundledCore.id == MeowCoreId) "socks5" else "socks",
        "listen" to options.listenAddress,
        "port" to options.port,
        "udp" to true,
        "users" to listOf(mapOf("username" to options.username, "password" to options.password)),
    )
    return dumpMihomoProfileYaml(root).toByteArray(Charsets.UTF_8)
}
