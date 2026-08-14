// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.mode

import app.AppState
import engine.proxy.LocalProxyOptions
import engine.proxy.toLocalProxyOptions
import engine.proxy.toLocalProxyOptionsOrNull
import engine.root.config.RootConfigBuildContext
import engine.root.daemon.config.AsteriskdConfig
import engine.root.daemon.config.AsteriskdHevSocks5TunnelHelper
import engine.root.daemon.config.AsteriskdMode
import engine.root.daemon.config.AsteriskdModeOptions
import engine.root.config.RootIptablesConfig
import engine.root.config.RootModeStartConfig
import engine.root.config.RootStartConfig
import engine.root.config.buildAsteriskdConfig
import engine.root.config.tun2SocksInternalProxyPortValue
import engine.vpn.TunOptions
import engine.vpn.toTunOptions
import engine.mihomo.raw.runtimeIpv6Enabled

internal val Tun2SocksBaseIptablesConfig = RootIptablesConfig(
    mark = Tun2SocksFwmark,
    ipv4Table = Tun2SocksRouteTable,
    ipv6Table = Tun2SocksRouteTable,
)

internal fun RootConfigBuildContext.buildTun2SocksStartConfig(): RootModeStartConfig {
    val appState = this.appState
    val tunOptions = appState.toTunOptions()
    val localProxyOptions = rawConfig?.let { config ->
        requireNotNull(config.toLocalProxyOptionsOrNull()) {
            "Raw Mihomo configuration requires a SOCKS or Mixed inbound for Tun2Socks mode"
        }
    } ?: appState.toLocalProxyOptions()
    val socks5ProxyPort = rawConfig?.let { config ->
        requireNotNull(config.socksInbound.value?.port) {
            "Raw Mihomo configuration requires a SOCKS or Mixed inbound for Tun2Socks mode"
        }
    } ?: appState.tun2SocksInternalProxyPortValue()
    val rootStartConfig = buildRootStartConfig()
    val iptablesConfig = buildRootIptablesConfig(Tun2SocksBaseIptablesConfig)
    return RootModeStartConfig(
        root = rootStartConfig,
        localProxyOptions = localProxyOptions,
        asteriskdConfig = rootStartConfig.buildAsteriskdConfig(
            mode = AsteriskdMode.Tun2Socks,
            iptablesConfig = iptablesConfig,
            virtualInterfaces = listOf("asterisk0"),
            modeOptions = AsteriskdModeOptions(transparentPort = null, tunnelName = null),
            helper = AsteriskdHevSocks5TunnelHelper(
                executablePath = rootStartConfig.runtimePaths.hevSocks5TunnelExecutablePath,
                socksHost = Tun2SocksListenAddress,
                socksPort = socks5ProxyPort,
                tunnelName = "asterisk0",
                mtu = tunOptions.mtu,
                ipv4Address = tunOptions.ipv4Address.address,
                ipv6Address = tunOptions.ipv6Address.address.takeIf {
                    rawConfig.runtimeIpv6Enabled(appState.enableIpv6)
                },
                multiQueue = true,
                tcpFastOpen = true,
            ),
        ),
    )
}

internal const val Tun2SocksListenAddress = "127.0.0.1"
internal const val DefaultTun2SocksProxyPort = 65534
private const val Tun2SocksFwmark = "0x20000000/0x60000000"
private const val Tun2SocksRouteTable = "168"
internal const val Tun2SocksPreroutingChain = "ASTERISK_TUN_PREROUTING"
internal const val Tun2SocksOutputChain = "ASTERISK_TUN_OUTPUT"
internal const val Tun2SocksForwardChain = "ASTERISK_TUN_FORWARD"
internal const val Tun2SocksPrerouting6Chain = "ASTERISK_TUN6_PREROUTING"
internal const val Tun2SocksOutput6Chain = "ASTERISK_TUN6_OUTPUT"
internal const val Tun2SocksForward6Chain = "ASTERISK_TUN6_FORWARD"
