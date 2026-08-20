// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.mode

import app.AppState
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.MihomoTunDevice
import engine.proxy.toLocalProxyOptions
import engine.proxy.toLocalProxyOptionsOrNull
import engine.root.config.RootConfigBuildContext
import engine.root.config.RootModeStartConfig
import engine.root.config.buildAsteriskdConfig
import engine.root.daemon.config.AsteriskdMode
import engine.root.daemon.config.AsteriskdModeOptions
import engine.vpn.TunOptions
import engine.vpn.toTunOptions

internal data class MihomoTunConfig(
    val device: String,
    val stack: String,
    val mtu: Int,
    val ipv4Address: String,
    val ipv6Address: String?,
)

internal fun RootConfigBuildContext.buildTunStartConfig(): RootModeStartConfig {
    val appState = this.appState
    val rootStartConfig = buildRootStartConfig()
    val iptablesConfig = buildRootIptablesConfig()
    val tunConfig = rawConfig?.let { config ->
        val inbound = requireNotNull(config.tunInbound.value) {
            "Raw Mihomo configuration requires one compatible TUN inbound for Root TUN mode"
        }
        MihomoTunConfig(
            device = inbound.device,
            stack = inbound.stack,
            mtu = inbound.mtu,
            ipv4Address = inbound.ipv4Address,
            ipv6Address = inbound.ipv6Address,
        )
    } ?: appState.buildMihomoTunConfig(appState.toTunOptions())
    return RootModeStartConfig(
        root = rootStartConfig,
        localProxyOptions = rawConfig?.toLocalProxyOptionsOrNull() ?: appState.toLocalProxyOptions().takeIf { rawConfig == null },
        asteriskdConfig = rootStartConfig.buildAsteriskdConfig(
            mode = AsteriskdMode.Tun,
            iptablesConfig = iptablesConfig,
            virtualInterfaces = listOf(tunConfig.device),
            modeOptions = AsteriskdModeOptions(
                transparentPort = null,
                tunnelName = tunConfig.device,
            ),
        ),
    )
}

private fun AppState.buildMihomoTunConfig(tunOptions: TunOptions): MihomoTunConfig {
    return MihomoTunConfig(
        device = MihomoTunDevice,
        stack = MihomoProfileFactory.tunStack(this),
        mtu = tunOptions.mtu,
        ipv4Address = "${tunOptions.ipv4Address.address}/${tunOptions.ipv4Address.prefixLength}",
        ipv6Address = if (enableIpv6) {
            "${tunOptions.ipv6Address.address}/${tunOptions.ipv6Address.prefixLength}"
        } else {
            null
        },
    )
}
