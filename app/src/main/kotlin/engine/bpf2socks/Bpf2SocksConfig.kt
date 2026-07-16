// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.bpf2socks

import app.modes.ProxyAppListModeWhitelist
import engine.proxy.LocalProxyOptions
import engine.proxy.toLocalProxyOptions
import engine.proxy.toLocalProxyOptionsOrNull
import engine.root.RootBpf2SocksCgroupPath
import engine.root.RootBpf2SocksDefaultBridgePort
import engine.root.RootBpf2SocksListenAddress
import engine.root.RootBpf2SocksPinnedObjectDir
import engine.root.RootBpf2SocksSocksInboundAddress
import engine.root.RootBpf2SocksTokenIpv4Prefix
import engine.root.RootBpf2SocksTokenIpv6Prefix
import engine.root.RootConfigBuildContext
import engine.root.AsteriskdConfig
import engine.root.AsteriskdMode
import engine.root.RootIptablesConfig
import engine.root.RootModeStartConfig
import engine.root.RootProxyAppWhitelistSystemUids
import engine.root.RootRuntimeLayout
import engine.root.RootStartConfig
import engine.root.buildAsteriskdConfig
import engine.root.bpf2SocksBridgePortValue
import engine.root.bpf2socksConfigPath
import engine.root.bpf2socksPidPath
import engine.root.rootEbpfDirectCidrPathV4
import engine.root.rootEbpfDirectCidrPathV6
import engine.root.tun2SocksInternalProxyPortValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import engine.mihomo.raw.runtimeIpv6Enabled

@Serializable
internal data class Bpf2SocksConfig(
    val version: Int = 1,
    val bridgeListenAddress: String = RootBpf2SocksListenAddress,
    val bridgePort: Int = RootBpf2SocksDefaultBridgePort,
    val tokenIpv4Prefix: String = RootBpf2SocksTokenIpv4Prefix,
    val tokenIpv6Prefix: String = RootBpf2SocksTokenIpv6Prefix,
    val pinnedObjectDir: String = RootBpf2SocksPinnedObjectDir,
    val cgroupPath: String = RootBpf2SocksCgroupPath,
    val workerCount: Int = 0,
    val tcpBufferSize: Int = 65536,
    val maxTcpSessions: Int = 4096,
    val tcpConnectTimeoutMilliseconds: Int = 10000,
    val tcpIdleTimeoutMilliseconds: Int = 300000,
    val udpSocketBufferSize: Int = 524288,
    val udpBatchSize: Int = 10,
    val maxUdpSessions: Int = 4096,
    val maxUdpBindings: Int = 16384,
    val udpIdleTimeoutSeconds: Int = 60,
    val maxUdpPendingBytes: Int = 64 * 1024 * 1024,
    val dnsTransactionTimeoutMilliseconds: Int = 60000,
    val socksHost: String,
    val socksPort: Int,
    val enableIpv6: Boolean,
    val enableDnsHijack: Boolean,
    val hotspotInterfacePrefixes: List<String> = emptyList(),
    val ignoredInterfaces: List<String> = emptyList(),
    val proxyPrivateCidrsV4: List<String> = emptyList(),
    val bypassPrivateCidrsV4: List<String> = emptyList(),
    val proxyPrivateCidrsV6: List<String> = emptyList(),
    val bypassPrivateCidrsV6: List<String> = emptyList(),
    val policy: Bpf2SocksPolicy,
)

@Serializable
internal data class Bpf2SocksPolicy(
    val mode: Int,
    val uids: List<Int>,
    val bypassUids: List<Int>,
    val bypassDirectCidrs: Boolean,
    val directCidrPathV4: String,
    val directCidrPathV6: String,
)

internal data class Bpf2SocksControlPaths(
    val executablePath: String,
    val configPath: String,
    val pidPath: String,
)

internal data class Bpf2SocksStartConfig(
    override val root: RootStartConfig,
    override val localProxyOptions: LocalProxyOptions?,
    val controlPaths: Bpf2SocksControlPaths,
    val bpf2socksConfig: Bpf2SocksConfig,
    val directCidrSourcePathsV4: List<String>,
    val directCidrSourcePathsV6: List<String>,
    override val asteriskdConfig: AsteriskdConfig,
) : RootModeStartConfig

internal fun Bpf2SocksConfig.toJsonString(): String {
    return Bpf2SocksJson.encodeToString(this)
}

internal fun RootConfigBuildContext.buildBpf2SocksStartConfig(): Bpf2SocksStartConfig {
    val appState = this.appState
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
    val iptablesConfig = buildRootIptablesConfig(Bpf2SocksBasePolicyConfig)
        .copy(enableEbpfRules = true)
    val bpf2SocksPolicy = iptablesConfig.toBpf2SocksPolicy(
        directCidrPathV4 = rootStartConfig.runtimeLayout.rootEbpfDirectCidrPathV4,
        directCidrPathV6 = rootStartConfig.runtimeLayout.rootEbpfDirectCidrPathV6,
    )
    return Bpf2SocksStartConfig(
        root = rootStartConfig,
        localProxyOptions = localProxyOptions,
        controlPaths = rootStartConfig.runtimeLayout.buildBpf2SocksControlPaths(),
        bpf2socksConfig = buildBpf2SocksConfig(
            bridgePort = appState.bpf2SocksBridgePortValue(),
            socksPort = socksPort,
            enableIpv6 = rawConfig.runtimeIpv6Enabled(appState.enableIpv6),
            enableDnsHijack = rootStartConfig.enableLocalDns,
            iptablesConfig = iptablesConfig,
            policy = bpf2SocksPolicy,
        ),
        directCidrSourcePathsV4 = listOf(resourceFilePaths.directCidrIpv4Path),
        directCidrSourcePathsV6 = listOf(resourceFilePaths.directCidrIpv6Path),
        asteriskdConfig = rootStartConfig.buildAsteriskdConfig(
            mode = AsteriskdMode.Bpf2Socks,
            iptablesConfig = iptablesConfig,
            virtualInterfaces = emptyList(),
        ),
    )
}

private fun RootRuntimeLayout.buildBpf2SocksControlPaths(): Bpf2SocksControlPaths {
    return Bpf2SocksControlPaths(
        executablePath = bpf2socksPath,
        configPath = bpf2socksConfigPath,
        pidPath = bpf2socksPidPath,
    )
}

private fun buildBpf2SocksConfig(
    bridgePort: Int,
    socksPort: Int,
    enableIpv6: Boolean,
    enableDnsHijack: Boolean,
    iptablesConfig: RootIptablesConfig,
    policy: Bpf2SocksPolicy,
): Bpf2SocksConfig {
    return Bpf2SocksConfig(
        bridgePort = bridgePort,
        socksHost = RootBpf2SocksSocksInboundAddress,
        socksPort = socksPort,
        enableIpv6 = enableIpv6,
        enableDnsHijack = enableDnsHijack,
        hotspotInterfacePrefixes = iptablesConfig.externalInterfacePrefixes,
        ignoredInterfaces = iptablesConfig.ignoredInterfaces,
        proxyPrivateCidrsV4 = iptablesConfig.proxyPrivateIpv4Cidrs,
        bypassPrivateCidrsV4 = iptablesConfig.bypassPrivateIpv4Cidrs,
        proxyPrivateCidrsV6 = iptablesConfig.proxyPrivateIpv6Cidrs,
        bypassPrivateCidrsV6 = iptablesConfig.bypassPrivateIpv6Cidrs,
        policy = policy,
    )
}

private fun RootIptablesConfig.toBpf2SocksPolicy(
    directCidrPathV4: String,
    directCidrPathV6: String,
): Bpf2SocksPolicy {
    val proxyUids = if (proxyAppListMode == ProxyAppListModeWhitelist) {
        proxyApplicationUids + RootProxyAppWhitelistSystemUids
    } else {
        proxyApplicationUids
    }
    return Bpf2SocksPolicy(
        mode = proxyAppListMode,
        uids = proxyUids.distinct().sorted(),
        bypassUids = forcedBypassUids.distinct().sorted(),
        bypassDirectCidrs = enableEbpfDirectCidrBypass,
        directCidrPathV4 = directCidrPathV4,
        directCidrPathV6 = directCidrPathV6,
    )
}

private val Bpf2SocksBasePolicyConfig = RootIptablesConfig(
    mark = "",
    ipv4Table = "",
    ipv6Table = "",
)

private val Bpf2SocksJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
}
