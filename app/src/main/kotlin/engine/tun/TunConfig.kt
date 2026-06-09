// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.tun

import app.AppState
import engine.mihomo.MihomoProfileFactory
import engine.proxy.LocalProxyOptions
import engine.proxy.toLocalProxyOptions
import engine.root.RootConfigBuildContext
import engine.root.RootIptablesConfig
import engine.root.RootModeStartConfig
import engine.root.RootStartConfig
import engine.tun2socks.Tun2SocksBaseIptablesConfig
import engine.vpn.TunOptions
import engine.vpn.toTunOptions

internal data class TunStartConfig(
    override val root: RootStartConfig,
    override val localProxyOptions: LocalProxyOptions,
    val tunConfig: MihomoTunConfig,
    val iptablesConfig: RootIptablesConfig,
) : RootModeStartConfig

internal data class MihomoTunConfig(
    val device: String,
    val stack: String,
    val mtu: Int,
    val ipv4Address: String,
    val ipv6Address: String?,
)

internal val TunBaseIptablesConfig = Tun2SocksBaseIptablesConfig

internal fun RootConfigBuildContext.buildTunStartConfig(): TunStartConfig {
    val appState = this.appState
    val tunOptions = appState.toTunOptions()
    val rootStartConfig = buildRootStartConfig()
    return TunStartConfig(
        root = rootStartConfig,
        localProxyOptions = appState.toLocalProxyOptions(),
        tunConfig = appState.buildMihomoTunConfig(tunOptions),
        iptablesConfig = buildRootIptablesConfig(
            base = TunBaseIptablesConfig,
            ignoredLocalInterfaceNames = setOf(MihomoTunDevice),
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
