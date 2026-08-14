// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import app.modes.ColorModeSystem
import app.modes.LanguageModeSystem
import app.modes.MihomoModeRule
import app.modes.MihomoProxyLayoutAuto
import app.modes.MihomoProxySortDefault
import app.modes.MihomoTunStackGvisor
import app.modes.ProxyAppListModeGlobal
import app.modes.RunModeVpnService
import engine.root.RootModeEngine
import engine.vpn.VpnDefaults
import engine.mihomo.DefaultMihomoDnsDefaultNameserver
import engine.mihomo.DefaultMihomoDnsFakeIpFilter
import engine.mihomo.DefaultMihomoDnsFakeIpRange
import engine.mihomo.DefaultMihomoDnsFallback
import engine.mihomo.DefaultMihomoDnsFallbackFilterDomain
import engine.mihomo.DefaultMihomoDnsFallbackFilterIpcidr
import engine.mihomo.DefaultMihomoDnsNameserver
import engine.mihomo.DefaultMihomoDnsNameserverPolicy
import engine.mihomo.DefaultMihomoDnsProxyServerNameserver
import engine.mihomo.DefaultMihomoControlPort
import engine.mihomo.DefaultMihomoSnifferHttpPorts
import engine.mihomo.DefaultMihomoSnifferQuicPorts
import engine.mihomo.DefaultMihomoSnifferTlsPorts
import engine.mihomo.MihomoGeodataLoaderStandard
import engine.mihomo.MihomoDnsModeFakeIp
import engine.mihomo.MihomoDnsModeRedirHost
import engine.mihomo.MihomoSnifferProtocolOverrideFollowGlobal
import features.resources.ResourceFileSourceMetaCubeXGithub

data class AppState(
    val colorMode: Int = ColorModeSystem,
    val languageMode: Int = LanguageModeSystem,
    val seedIndex: Int = 0,

    val mihomoProfiles: List<MihomoProfileState> = DefaultMihomoProfiles,
    val nextMihomoProfileId: Int = 1,
    val selectedMihomoProfileId: Int = DefaultMihomoProfileId,
    val pendingMihomoRestartProfileId: Int = DefaultMihomoProfileId,
    val mihomoOverrideScripts: List<MihomoOverrideScriptState> = emptyList(),
    val nextMihomoOverrideScriptId: Int = 1,

    val runMode: Int = RunModeVpnService,
    val mihomoMode: Int = MihomoModeRule,
    val mihomoProxyExcludeNotSelectable: Boolean = false,
    val mihomoProxyLayout: Int = MihomoProxyLayoutAuto,
    val mihomoProxySort: Int = MihomoProxySortDefault,
    val mihomoTunStack: Int = MihomoTunStackGvisor,
    val mihomoControlPort: String = DefaultMihomoControlPort.toString(),
    val mihomoControlSecret: String = "",
    val enableLocalDns: Boolean = true,

    val localProxyPort: String = VpnDefaults.LOCAL_PROXY_PORT.toString(),
    val enableDynamicLocalProxyPort: Boolean = false,
    val localProxyListenAllInterfaces: Boolean = false,
    val localProxyUsername: String = "",
    val localProxyPassword: String = "",
    val enableVpnAppendHttpProxy: Boolean = false,
    val enableVpnHevTun: Boolean = false,
    val tunMtu: String = VpnDefaults.MTU.toString(),
    val tunVpnDns: String = VpnDefaults.IPV4_DNS,
    val tunIpv4Cidr: String = VpnDefaults.IPV4_CIDR,
    val tunIpv6Cidr: String = VpnDefaults.IPV6_CIDR,

    val proxyRunning: Boolean = false,

    val coreLogLevel: Int = 3,
    val enableGeodataMode: Boolean = false,
    val enableTrafficStatsNotification: Boolean = false,
    val enableBroadcastControl: Boolean = false,
    val mihomoGeodataLoader: Int = MihomoGeodataLoaderStandard,
    val resourceFileSource: Int = ResourceFileSourceMetaCubeXGithub,
    val customResourceFileGeoIpUrl: String = "",
    val customResourceFileGeoSiteUrl: String = "",
    val customResourceFileMmdbUrl: String = "",
    val customResourceFileAsnUrl: String = "",
    val customResourceFileDirectCidrIpv4Url: String = "",
    val customResourceFileDirectCidrIpv6Url: String = "",
    val customResourceFiles: List<CustomResourceFileState> = emptyList(),
    val nextCustomResourceFileId: Int = 1,
    val enableSniffer: Boolean = true,
    val enableSnifferOverrideDestination: Boolean = true,
    val snifferForceDnsMapping: Boolean = true,
    val snifferParsePureIp: Boolean = true,
    val snifferHttpPorts: List<String> = DefaultMihomoSnifferHttpPorts,
    val snifferTlsPorts: List<String> = DefaultMihomoSnifferTlsPorts,
    val snifferQuicPorts: List<String> = DefaultMihomoSnifferQuicPorts,
    val snifferHttpOverrideDestinationMode: Int = MihomoSnifferProtocolOverrideFollowGlobal,
    val snifferTlsOverrideDestinationMode: Int = MihomoSnifferProtocolOverrideFollowGlobal,
    val snifferQuicOverrideDestinationMode: Int = MihomoSnifferProtocolOverrideFollowGlobal,
    val snifferForceDomain: List<String> = emptyList(),
    val snifferSkipDomain: List<String> = emptyList(),
    val snifferSkipSrcAddress: List<String> = emptyList(),
    val snifferSkipDstAddress: List<String> = emptyList(),

    val enableIpv6: Boolean = false,
    val enableIpv6Prefer: Boolean = false,

    val overrideDns: Boolean = true,
    val dnsPreferH3: Boolean = false,
    val dnsUseHosts: Boolean = true,
    val dnsUseSystemHosts: Boolean = true,
    val dnsRespectRules: Boolean = false,
    val dnsEnhancedMode: Int = MihomoDnsModeRedirHost,
    val dnsFakeIpRange: String = DefaultMihomoDnsFakeIpRange,
    val dnsFakeIpFilter: List<String> = DefaultMihomoDnsFakeIpFilter,
    val dnsDefaultNameserver: List<String> = DefaultMihomoDnsDefaultNameserver,
    val dnsNameserver: List<String> = DefaultMihomoDnsNameserver,
    val dnsNameserverPolicy: List<String> = DefaultMihomoDnsNameserverPolicy,
    val dnsProxyServerNameserver: List<String> = DefaultMihomoDnsProxyServerNameserver,
    val dnsFallback: List<String> = DefaultMihomoDnsFallback,
    val dnsFallbackFilterGeoip: Boolean = true,
    val dnsFallbackFilterGeoipCode: String = "CN",
    val dnsFallbackFilterGeosite: List<String> = emptyList(),
    val dnsFallbackFilterIpcidr: List<String> = DefaultMihomoDnsFallbackFilterIpcidr,
    val dnsFallbackFilterDomain: List<String> = DefaultMihomoDnsFallbackFilterDomain,
    val dnsHosts: List<String> = emptyList(),

    val transparentProxyPort: String = RootModeEngine.DefaultTproxyPort.toString(),
    val enableRootBootScript: Boolean = false,
    val enableRootEbpfRules: Boolean = false,
    val enableRootEbpfDirectCidrBypass: Boolean = false,
    val enableRootIpv6Disabler: Boolean = false,
    val socks5ProxyPort: String = RootModeEngine.DefaultTun2SocksProxyPort.toString(),
    val bpf2SocksBridgePort: String = RootModeEngine.DefaultBpf2SocksBridgePort.toString(),

    val externalInterfaces: List<String> = emptyList(),
    val ignoredInterfaces: List<String> = emptyList(),
    val privateAddressCidrs: List<String> = emptyList(),

    val proxyAppListMode: Int = ProxyAppListModeGlobal,
    val proxyAppListSelectedApps: List<String> = emptyList(),
)

val AppState.effectiveLocalDnsEnabled: Boolean
    get() = enableLocalDns

val AppState.effectiveFakeIpEnabled: Boolean
    get() = effectiveLocalDnsEnabled && dnsEnhancedMode == MihomoDnsModeFakeIp

fun AppState.withMihomoRestartRequired(
    profileId: Int,
    contentChanged: Boolean = true,
): AppState {
    if (!contentChanged || !proxyRunning || selectedMihomoProfileId != profileId) return this
    return copy(pendingMihomoRestartProfileId = profileId)
}

fun AppState.withMihomoRestartApplied(): AppState =
    if (pendingMihomoRestartProfileId == DefaultMihomoProfileId) this
    else copy(pendingMihomoRestartProfileId = DefaultMihomoProfileId)
