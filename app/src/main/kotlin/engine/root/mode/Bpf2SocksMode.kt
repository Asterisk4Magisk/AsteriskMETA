// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.mode

import engine.mihomo.raw.runtimeIpv6Enabled
import engine.proxy.LocalProxyOptions
import engine.proxy.toLocalProxyOptions
import engine.proxy.toLocalProxyOptionsOrNull
import engine.root.daemon.config.AsteriskdBpf2SocksHelper
import engine.root.daemon.config.AsteriskdConfig
import engine.root.daemon.config.AsteriskdMode
import engine.root.daemon.config.AsteriskdModeOptions
import engine.root.config.RootConfigBuildContext
import engine.root.config.RootIptablesConfig
import engine.root.config.RootModeStartConfig
import engine.root.config.RootStartConfig
import engine.root.config.bpf2SocksBridgePortValue
import engine.root.config.buildAsteriskdConfig
import engine.root.config.tun2SocksInternalProxyPortValue

private const val RootBpf2SocksListenAddress = "0.0.0.0"
private const val RootBpf2SocksSocksInboundAddress = "127.0.0.1"

internal fun RootConfigBuildContext.buildBpf2SocksStartConfig(): RootModeStartConfig {
    val appState = appState
    val localProxyOptions = rawConfig?.let { config ->
        requireNotNull(config.toLocalProxyOptionsOrNull()) {
            "Raw Mihomo configuration requires a SOCKS or Mixed inbound for BPF mode"
        }
    } ?: appState.toLocalProxyOptions()
    val socksPort = rawConfig?.let { config ->
        requireNotNull(config.socksInbound.value?.port) {
            "Raw Mihomo configuration requires a SOCKS or Mixed inbound for BPF mode"
        }
    } ?: appState.tun2SocksInternalProxyPortValue()
    val rootStartConfig = buildRootStartConfig()
    val iptablesConfig = buildRootIptablesConfig(Bpf2SocksBasePolicyConfig).copy(enableEbpfRules = true)
    require(rootStartConfig.enableIpv6 == rawConfig.runtimeIpv6Enabled(appState.enableIpv6))
    return RootModeStartConfig(
        root = rootStartConfig,
        localProxyOptions = localProxyOptions,
        asteriskdConfig = rootStartConfig.buildAsteriskdConfig(
            mode = AsteriskdMode.Bpf2Socks,
            iptablesConfig = iptablesConfig,
            virtualInterfaces = emptyList(),
            modeOptions = AsteriskdModeOptions(transparentPort = null, tunnelName = null),
            helper = AsteriskdBpf2SocksHelper(
                executablePath = rootStartConfig.runtimePaths.bpf2SocksExecutablePath,
                bridgeListenAddress = RootBpf2SocksListenAddress,
                bridgePort = appState.bpf2SocksBridgePortValue(),
                socksHost = RootBpf2SocksSocksInboundAddress,
                socksPort = socksPort,
            ),
        ),
    )
}

private val Bpf2SocksBasePolicyConfig = RootIptablesConfig(
    mark = "",
    ipv4Table = "",
    ipv6Table = "",
)
