// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.tun2socks

import android.content.Context
import app.AppState
import engine.proxy.LocalProxyOptions
import engine.proxy.toLocalProxyOptions
import engine.root.RootConfigBuildContext
import engine.root.RootIptablesConfig
import engine.root.RootModeStartConfig
import engine.root.RootRuntimeLayout
import engine.root.RootStartConfig
import engine.root.tun2SocksInternalProxyPortValue
import engine.root.toRootRuntimeLayout
import engine.vpn.TunOptions
import engine.vpn.toTunOptions
import engine.mihomo.MihomoCoreLogPaths
import engine.mihomo.prepareMihomoCoreLogPaths
import features.resources.runtime.prepareMihomoResourceFilePaths
import java.io.File

internal data class Tun2SocksStartConfig(
    override val root: RootStartConfig,
    override val localProxyOptions: LocalProxyOptions,
    val hevSocks5TunnelConfig: HevSocks5TunnelConfig,
    val iptablesConfig: RootIptablesConfig,
) : RootModeStartConfig

internal data class HevSocks5TunnelConfig(
    val executablePath: String,
    val configPath: String,
    val pidPath: String,
    val logPath: String,
    val socksPort: Int,
    val mtu: Int,
    val ipv4Address: String,
    val ipv6Address: String?,
)

internal val Tun2SocksBaseIptablesConfig = RootIptablesConfig(
    mark = Tun2SocksFwmark,
    ipv4Table = Tun2SocksRouteTable,
    ipv6Table = Tun2SocksRouteTable,
)

internal fun RootConfigBuildContext.buildTun2SocksStartConfig(): Tun2SocksStartConfig {
    val appState = this.appState
    val tunOptions = appState.toTunOptions()
    val localProxyOptions = appState.toLocalProxyOptions()
    val socks5ProxyPort = appState.tun2SocksInternalProxyPortValue()
    val rootStartConfig = buildRootStartConfig()
    return Tun2SocksStartConfig(
        root = rootStartConfig,
        localProxyOptions = localProxyOptions,
        hevSocks5TunnelConfig = buildHevSocks5TunnelConfig(
            runtimeLayout = rootStartConfig.runtimeLayout,
            coreLogPaths = rootStartConfig.coreLogPaths,
            socks5ProxyPort = socks5ProxyPort,
            tunOptions = tunOptions,
            enableIpv6 = appState.enableIpv6,
        ),
        iptablesConfig = buildRootIptablesConfig(
            base = Tun2SocksBaseIptablesConfig,
            ignoredLocalInterfaceNames = setOf("asterisk0"),
        ),
    )
}

internal fun Context.prepareHevSocks5TunnelConfig(appState: AppState): HevSocks5TunnelConfig {
    val runtimeLayout = prepareMihomoResourceFilePaths().toRootRuntimeLayout()
    return buildHevSocks5TunnelConfig(
        runtimeLayout = runtimeLayout,
        coreLogPaths = prepareMihomoCoreLogPaths(),
        socks5ProxyPort = appState.tun2SocksInternalProxyPortValue(),
        tunOptions = appState.toTunOptions(),
        enableIpv6 = appState.enableIpv6,
    )
}

private fun buildHevSocks5TunnelConfig(
    runtimeLayout: RootRuntimeLayout,
    coreLogPaths: MihomoCoreLogPaths,
    socks5ProxyPort: Int,
    tunOptions: TunOptions,
    enableIpv6: Boolean,
): HevSocks5TunnelConfig {
    val dataDir = File(runtimeLayout.dataDir)
    return HevSocks5TunnelConfig(
        executablePath = runtimeLayout.hevSocks5TunnelPath,
        configPath = File(dataDir, HevSocks5TunnelConfigFileName).absolutePath,
        pidPath = File(dataDir, HevSocks5TunnelPidFileName).absolutePath,
        logPath = coreLogPaths.hevSocks5TunnelLogFile().absolutePath,
        socksPort = socks5ProxyPort,
        mtu = tunOptions.mtu,
        ipv4Address = tunOptions.ipv4Address.address,
        ipv6Address = tunOptions.ipv6Address.address.takeIf { enableIpv6 },
    )
}
