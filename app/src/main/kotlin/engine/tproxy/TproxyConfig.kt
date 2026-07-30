// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.tproxy

import app.AppState
import engine.proxy.LocalProxyOptions
import engine.proxy.toLocalProxyOptions
import engine.network.toPortOrNull
import engine.proxy.toLocalProxyOptionsOrNull
import engine.root.RootConfigBuildContext
import engine.root.AsteriskdConfig
import engine.root.AsteriskdBypassConsumerChains
import engine.root.AsteriskdMode
import engine.root.RootEbpfRuntimeConfig
import engine.root.RootIptablesConfig
import engine.root.RootModeStartConfig
import engine.root.RootStartConfig
import engine.root.buildAsteriskdConfig

internal data class TproxyStartConfig(
    override val root: RootStartConfig,
    override val localProxyOptions: LocalProxyOptions?,
    val tproxyPort: Int,
    val iptablesConfig: RootIptablesConfig,
    override val asteriskdConfig: AsteriskdConfig,
    override val rootEbpfConfig: RootEbpfRuntimeConfig?,
) : RootModeStartConfig

internal val TproxyBaseIptablesConfig = RootIptablesConfig(
    mark = TproxyFwmark,
    ipv4Table = TproxyRouteTable,
    ipv6Table = TproxyRouteTable,
)

internal fun RootConfigBuildContext.buildTproxyStartConfig(): TproxyStartConfig {
    val appState = this.appState
    val tproxyPort = rawConfig?.let { config ->
        requireNotNull(config.tproxyPort.value) {
            "Raw Mihomo configuration requires tproxy-port for TPROXY mode"
        }
    } ?: appState.tproxyPortValue()
    val rootStartConfig = buildRootStartConfig()
    val iptablesConfig = buildRootIptablesConfig(TproxyBaseIptablesConfig)
    return TproxyStartConfig(
        root = rootStartConfig,
        localProxyOptions = rawConfig?.toLocalProxyOptionsOrNull() ?: appState.toLocalProxyOptions().takeIf { rawConfig == null },
        tproxyPort = tproxyPort,
        iptablesConfig = iptablesConfig,
        asteriskdConfig = rootStartConfig.buildAsteriskdConfig(
            mode = AsteriskdMode.Tproxy,
            iptablesConfig = iptablesConfig,
            virtualInterfaces = listOf(TproxyDummyDevice),
            bypassConsumerChains = AsteriskdBypassConsumerChains(
                ipv4 = listOf(TproxyPreroutingChain, TproxyOutputChain),
                ipv6 = listOf(TproxyPrerouting6Chain, TproxyOutput6Chain),
            ),
        ),
        rootEbpfConfig = buildRootEbpfRuntimeConfig(iptablesConfig),
    )
}

private fun AppState.tproxyPortValue(): Int {
    return transparentProxyPort.toPortOrNull() ?: DefaultTproxyPort
}
