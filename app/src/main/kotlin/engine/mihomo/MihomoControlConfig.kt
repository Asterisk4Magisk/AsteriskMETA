// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import app.AppState
import engine.network.findAvailableTcpPort
import engine.network.isTcpPortAvailable
import engine.network.toPortOrNull
import engine.tproxy.DefaultTproxyPort
import engine.tun2socks.DefaultTun2SocksProxyPort
import engine.vpn.VpnDefaults

internal const val MihomoControlHost = "127.0.0.1"
internal const val DefaultMihomoControlPort = 9090
internal const val DefaultMihomoDelayTestUrl = "https://www.gstatic.com/generate_204"
internal const val DefaultMihomoDelayTimeoutMillis = 5000

internal data class MihomoControlConfig(
    val host: String = MihomoControlHost,
    val port: Int = DefaultMihomoControlPort,
    val secret: String = "",
) {
    val address: String
        get() = "$host:$port"

    val baseUrl: String
        get() = "http://$address"
}

internal fun AppState.mihomoControlConfig(): MihomoControlConfig {
    return MihomoControlConfig(
        port = mihomoControlPort.toPortOrNull() ?: DefaultMihomoControlPort,
        secret = mihomoControlSecret.trim(),
    )
}

internal fun AppState.withResolvedMihomoControlPort(): AppState {
    val configuredPort = mihomoControlPort.toPortOrNull()
    val excludedPorts = mihomoControlExcludedPorts()
    val resolvedPort = when {
        configuredPort != null &&
            configuredPort !in excludedPorts &&
            isTcpPortAvailable(MihomoControlHost, configuredPort) -> configuredPort

        else -> availableMihomoControlPort(excludedPorts) ?: configuredPort ?: DefaultMihomoControlPort
    }
    val resolvedPortText = resolvedPort.toString()
    return if (mihomoControlPort == resolvedPortText) this else copy(mihomoControlPort = resolvedPortText)
}

private fun AppState.mihomoControlExcludedPorts(): Set<Int> {
    return buildSet {
        add(localProxyPort.toPortOrNull() ?: VpnDefaults.LOCAL_PROXY_PORT)
        add(transparentProxyPort.toPortOrNull() ?: DefaultTproxyPort)
        add(socks5ProxyPort.toPortOrNull() ?: DefaultTun2SocksProxyPort)
    }
}

private fun availableMihomoControlPort(excludedPorts: Set<Int>): Int? {
    return findAvailableTcpPort(
        listenAddress = MihomoControlHost,
        excludedPorts = excludedPorts,
        attempts = RandomPortAttempts,
    )
}

private const val RandomPortAttempts = 32
