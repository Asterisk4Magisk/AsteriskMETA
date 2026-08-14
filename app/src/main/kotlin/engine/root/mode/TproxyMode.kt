// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.mode

import app.AppState
import engine.proxy.LocalProxyOptions
import engine.proxy.toLocalProxyOptions
import engine.network.toPortOrNull
import engine.network.NetworkLimits
import engine.proxy.toLocalProxyOptionsOrNull
import engine.root.config.RootConfigBuildContext
import engine.root.daemon.config.AsteriskdConfig
import engine.root.daemon.config.AsteriskdMode
import engine.root.daemon.config.AsteriskdModeOptions
import engine.root.config.RootIptablesConfig
import engine.root.config.RootModeStartConfig
import engine.root.config.RootStartConfig
import engine.root.config.buildAsteriskdConfig

internal val TproxyBaseIptablesConfig = RootIptablesConfig(
    mark = TproxyFwmark,
    ipv4Table = TproxyRouteTable,
    ipv6Table = TproxyRouteTable,
)

internal fun RootConfigBuildContext.buildTproxyStartConfig(): RootModeStartConfig {
    val appState = this.appState
    val tproxyPort = rawConfig?.let { config ->
        requireNotNull(config.tproxyPort.value) {
            "Raw Mihomo configuration requires tproxy-port for TPROXY mode"
        }
    } ?: appState.tproxyPortValue()
    val rootStartConfig = buildRootStartConfig()
    val iptablesConfig = buildRootIptablesConfig(TproxyBaseIptablesConfig)
    return RootModeStartConfig(
        root = rootStartConfig,
        localProxyOptions = rawConfig?.toLocalProxyOptionsOrNull() ?: appState.toLocalProxyOptions().takeIf { rawConfig == null },
        asteriskdConfig = rootStartConfig.buildAsteriskdConfig(
            mode = AsteriskdMode.Tproxy,
            iptablesConfig = iptablesConfig,
            virtualInterfaces = listOf(TproxyDummyDevice),
            modeOptions = AsteriskdModeOptions(
                transparentPort = tproxyPort,
                tunnelName = null,
            ),
        ),
    )
}

internal const val DefaultTproxyPort = NetworkLimits.PORT_MAX
private const val TproxyFwmark = "0x20000000/0x60000000"
private const val TproxyRouteTable = "160"
private const val TproxyDummyDevice = "xdummy"
internal const val TproxyDummyAddress = "fd01:5ca1:ab1e:8d97:497f:8b48:b9aa:85cd/128"
internal const val TproxyDummyFwmark = "0x40000000/0x60000000"
internal const val TproxyDummyRouteTable = "164"
internal const val TproxyPreroutingChain = "ASTERISK_TPROXY_PREROUTING"
internal const val TproxyOutputChain = "ASTERISK_TPROXY_OUTPUT"
internal const val TproxyPrerouting6Chain = "ASTERISK_TPROXY6_PREROUTING"
internal const val TproxyOutput6Chain = "ASTERISK_TPROXY6_OUTPUT"

private fun AppState.tproxyPortValue(): Int {
    return transparentProxyPort.toPortOrNull() ?: DefaultTproxyPort
}
