// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import app.AppState
import engine.mihomo.MihomoDnsModeRedirHost

internal data class TunSettingsDraft(
    val tunStack: Int = 0,
    val mtu: String = "",
    val vpnDns: String = "",
    val ipv4Cidr: String = "",
    val ipv6Cidr: String = "",
)

internal fun AppState.toTunSettingsDraft(): TunSettingsDraft {
    return TunSettingsDraft(
        tunStack = mihomoTunStack,
        mtu = tunMtu,
        vpnDns = tunVpnDns,
        ipv4Cidr = tunIpv4Cidr,
        ipv6Cidr = tunIpv6Cidr,
    )
}

internal data class LocalProxySettingsDraft(
    val transparentProxyPort: String = "",
    val socks5ProxyPort: String = "",
    val port: String = "",
    val enableDynamicPort: Boolean = false,
    val listenAllInterfaces: Boolean = false,
    val username: String = "",
    val password: String = "",
)

internal fun AppState.toLocalProxySettingsDraft(): LocalProxySettingsDraft {
    return LocalProxySettingsDraft(
        transparentProxyPort = transparentProxyPort,
        socks5ProxyPort = socks5ProxyPort,
        port = localProxyPort,
        enableDynamicPort = enableDynamicLocalProxyPort,
        listenAllInterfaces = localProxyListenAllInterfaces,
        username = localProxyUsername,
        password = localProxyPassword,
    )
}

internal data class DnsSettingsDraft(
    val enableLocalDns: Boolean = true,
    val overrideDns: Boolean = true,
    val dnsPreferH3: Boolean = false,
    val dnsUseHosts: Boolean = true,
    val dnsUseSystemHosts: Boolean = true,
    val dnsRespectRules: Boolean = false,
    val dnsEnhancedMode: Int = MihomoDnsModeRedirHost,
    val dnsFakeIpRange: String = "",
    val dnsFakeIpFilter: List<String> = emptyList(),
    val dnsDefaultNameserver: List<String> = emptyList(),
    val dnsNameserver: List<String> = emptyList(),
    val dnsNameserverPolicy: List<String> = emptyList(),
    val dnsProxyServerNameserver: List<String> = emptyList(),
    val dnsFallback: List<String> = emptyList(),
    val dnsFallbackFilterGeoip: Boolean = true,
    val dnsFallbackFilterGeoipCode: String = "",
    val dnsFallbackFilterGeosite: List<String> = emptyList(),
    val dnsFallbackFilterIpcidr: List<String> = emptyList(),
    val dnsFallbackFilterDomain: List<String> = emptyList(),
    val dnsHosts: List<String> = emptyList(),
)

internal fun AppState.toDnsSettingsDraft(): DnsSettingsDraft {
    return DnsSettingsDraft(
        enableLocalDns = enableLocalDns,
        overrideDns = overrideDns,
        dnsPreferH3 = dnsPreferH3,
        dnsUseHosts = dnsUseHosts,
        dnsUseSystemHosts = dnsUseSystemHosts,
        dnsRespectRules = dnsRespectRules,
        dnsEnhancedMode = dnsEnhancedMode,
        dnsFakeIpRange = dnsFakeIpRange,
        dnsFakeIpFilter = dnsFakeIpFilter,
        dnsDefaultNameserver = dnsDefaultNameserver,
        dnsNameserver = dnsNameserver,
        dnsNameserverPolicy = dnsNameserverPolicy,
        dnsProxyServerNameserver = dnsProxyServerNameserver,
        dnsFallback = dnsFallback,
        dnsFallbackFilterGeoip = dnsFallbackFilterGeoip,
        dnsFallbackFilterGeoipCode = dnsFallbackFilterGeoipCode,
        dnsFallbackFilterGeosite = dnsFallbackFilterGeosite,
        dnsFallbackFilterIpcidr = dnsFallbackFilterIpcidr,
        dnsFallbackFilterDomain = dnsFallbackFilterDomain,
        dnsHosts = dnsHosts,
    )
}

