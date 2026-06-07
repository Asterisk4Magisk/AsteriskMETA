// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import engine.proxy.LocalProxyOptions

internal data class VpnAppendHttpProxyOptions(
    val enabled: Boolean,
    val port: Int,
) {
    companion object {
        val Disabled = VpnAppendHttpProxyOptions(
            enabled = false,
            port = 0,
        )
    }
}

internal fun AppState.toVpnAppendHttpProxyOptions(localProxyOptions: LocalProxyOptions): VpnAppendHttpProxyOptions {
    if (!enableVpnAppendHttpProxy) {
        return VpnAppendHttpProxyOptions.Disabled
    }
    return VpnAppendHttpProxyOptions(
        enabled = true,
        port = localProxyOptions.port,
    )
}
