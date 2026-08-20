// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.mode

import app.AppState
import engine.network.NetworkLimits
import engine.network.toPortOrNull
import engine.proxy.toLocalProxyOptions
import engine.proxy.toLocalProxyOptionsOrNull
import engine.root.config.RootConfigBuildContext
import engine.root.config.RootModeStartConfig
import engine.root.config.buildAsteriskdConfig
import engine.root.daemon.config.AsteriskdMode
import engine.root.daemon.config.AsteriskdModeOptions

internal fun RootConfigBuildContext.buildTproxyStartConfig(): RootModeStartConfig {
    val appState = this.appState
    val tproxyPort = rawConfig?.let { config ->
        requireNotNull(config.tproxyPort.value) {
            "Raw Mihomo configuration requires tproxy-port for TPROXY mode"
        }
    } ?: appState.tproxyPortValue()
    val rootStartConfig = buildRootStartConfig()
    val iptablesConfig = buildRootIptablesConfig()
    return RootModeStartConfig(
        root = rootStartConfig,
        localProxyOptions = rawConfig?.toLocalProxyOptionsOrNull() ?: appState.toLocalProxyOptions().takeIf { rawConfig == null },
        asteriskdConfig = rootStartConfig.buildAsteriskdConfig(
            mode = AsteriskdMode.Tproxy,
            iptablesConfig = iptablesConfig,
            virtualInterfaces = emptyList(),
            modeOptions = AsteriskdModeOptions(
                transparentPort = tproxyPort,
                tunnelName = null,
            ),
        ),
    )
}

internal const val DefaultTproxyPort = NetworkLimits.PORT_MAX

private fun AppState.tproxyPortValue(): Int {
    return transparentProxyPort.toPortOrNull() ?: DefaultTproxyPort
}
