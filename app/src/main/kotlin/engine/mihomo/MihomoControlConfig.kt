// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import android.content.Context
import app.AppState
import app.modes.RunModeVpnService
import engine.mihomo.raw.usesRawMihomoConfig
import engine.vpn.VpnSession
import engine.network.findAvailableTcpPort
import engine.network.isTcpPortAvailable
import engine.network.toPortOrNull
import engine.root.RootModeEngine
import engine.vpn.VpnDefaults
import java.io.File

internal const val MihomoControlHost = "127.0.0.1"
internal const val DefaultMihomoControlPort = 9090
internal const val DefaultMihomoDelayTestUrl = app.DefaultConnectionTestUrl
internal const val DefaultMihomoDelayTimeoutMillis = 5000

internal data class MihomoControlConfig(
    val host: String = MihomoControlHost,
    val port: Int = DefaultMihomoControlPort,
    val secret: String = "",
    val scheme: String = "http",
    val unixSocketPath: String? = null,
) {
    val address: String
        get() = if (":" in host) "[$host]:$port" else "$host:$port"

    val baseUrl: String
        get() = "$scheme://$address"

    override fun toString(): String {
        return "MihomoControlConfig(host=$host, port=$port, scheme=$scheme, unixSocket=${unixSocketPath != null}, secret=<redacted>)"
    }
}

internal fun AppState.mihomoControlConfig(): MihomoControlConfig {
    return MihomoControlConfig(
        port = mihomoControlPort.toPortOrNull() ?: DefaultMihomoControlPort,
        secret = mihomoControlSecret.trim(),
    )
}

internal fun Context.mihomoControllerSocket(): File = File(filesDir, "clash/controller.sock")

internal fun AppState.mihomoControlConfig(context: Context, runMode: Int = this.runMode): MihomoControlConfig {
    val control = mihomoControlConfig()
    return if (!usesRawMihomoConfig() && runMode == RunModeVpnService && VpnSession.isRunning(context)) {
        control.copy(unixSocketPath = context.mihomoControllerSocket().absolutePath)
    } else {
        control
    }
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
        add(transparentProxyPort.toPortOrNull() ?: RootModeEngine.DefaultTproxyPort)
        add(socks5ProxyPort.toPortOrNull() ?: RootModeEngine.DefaultTun2SocksProxyPort)
        add(bpf2SocksBridgePort.toPortOrNull() ?: RootModeEngine.DefaultBpf2SocksBridgePort)
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
