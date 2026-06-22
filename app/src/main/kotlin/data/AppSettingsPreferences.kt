// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data

import android.content.Context
import android.content.SharedPreferences
import app.AppState
import app.CustomResourceFileState
import androidx.core.content.edit
import java.util.UUID

internal class AppSettingsPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): AppState {
        val defaults = AppState()
        val customResourceFiles = preferences.getCustomResourceFileList(
            KeyCustomResourceFiles,
            defaults.customResourceFiles,
        )
        val nextCustomResourceFileId = maxOf(
            preferences.getInt(KeyNextCustomResourceFileId, defaults.nextCustomResourceFileId),
            (customResourceFiles.maxOfOrNull { file -> file.id } ?: 0) + 1,
        )
        val mihomoControlSecret = preferences.getString(KeyMihomoControlSecret, null)
            ?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString().also { secret ->
                preferences.edit { putString(KeyMihomoControlSecret, secret) }
            }

        return defaults.copy(
            colorMode = preferences.getInt(KeyColorMode, defaults.colorMode),
            languageMode = preferences.getInt(KeyLanguageMode, defaults.languageMode),
            seedIndex = preferences.getInt(KeySeedIndex, defaults.seedIndex),
            nextMihomoProfileId = preferences.getInt(KeyNextMihomoProfileId, defaults.nextMihomoProfileId),
            nextMihomoOverrideScriptId = preferences.getInt(
                KeyNextMihomoOverrideScriptId,
                defaults.nextMihomoOverrideScriptId,
            ),
            selectedMihomoProfileId = preferences.getInt(
                KeySelectedMihomoProfileId,
                defaults.selectedMihomoProfileId,
            ),
            runMode = preferences.getInt(KeyRunMode, defaults.runMode),
            mihomoMode = preferences.getInt(KeyMihomoMode, defaults.mihomoMode),
            mihomoProxyExcludeNotSelectable = preferences.getBoolean(
                KeyMihomoProxyExcludeNotSelectable,
                defaults.mihomoProxyExcludeNotSelectable,
            ),
            mihomoProxyLayout = preferences.getInt(KeyMihomoProxyLayout, defaults.mihomoProxyLayout),
            mihomoProxySort = preferences.getInt(KeyMihomoProxySort, defaults.mihomoProxySort),
            mihomoTunStack = preferences.getInt(KeyMihomoTunStack, defaults.mihomoTunStack),
            mihomoControlPort = preferences.getString(
                KeyMihomoControlPort,
                defaults.mihomoControlPort,
            ) ?: defaults.mihomoControlPort,
            mihomoControlSecret = mihomoControlSecret,
            enableLocalDns = preferences.getBoolean(KeyEnableLocalDns, defaults.enableLocalDns),
            localProxyPort = preferences.getString(KeyLocalProxyPort, defaults.localProxyPort) ?: defaults.localProxyPort,
            enableDynamicLocalProxyPort = preferences.getBoolean(
                KeyEnableDynamicLocalProxyPort,
                defaults.enableDynamicLocalProxyPort,
            ),
            localProxyListenAllInterfaces = preferences.getBoolean(
                KeyLocalProxyListenAllInterfaces,
                defaults.localProxyListenAllInterfaces,
            ),
            localProxyUsername = preferences.getString(
                KeyLocalProxyUsername,
                defaults.localProxyUsername,
            ) ?: defaults.localProxyUsername,
            localProxyPassword = preferences.getString(
                KeyLocalProxyPassword,
                defaults.localProxyPassword,
            ) ?: defaults.localProxyPassword,
            enableVpnAppendHttpProxy = preferences.getBoolean(
                KeyEnableVpnAppendHttpProxy,
                defaults.enableVpnAppendHttpProxy,
            ),
            tunMtu = preferences.getString(KeyTunMtu, defaults.tunMtu) ?: defaults.tunMtu,
            tunVpnDns = preferences.getString(KeyTunVpnDns, defaults.tunVpnDns) ?: defaults.tunVpnDns,
            tunIpv4Cidr = preferences.getString(KeyTunIpv4Cidr, defaults.tunIpv4Cidr) ?: defaults.tunIpv4Cidr,
            tunIpv6Cidr = preferences.getString(KeyTunIpv6Cidr, defaults.tunIpv6Cidr) ?: defaults.tunIpv6Cidr,
            coreLogLevel = preferences.getInt(KeyCoreLogLevel, defaults.coreLogLevel),
            enableGeodataMode = preferences.getBoolean(KeyEnableGeodataMode, defaults.enableGeodataMode),
            mihomoGeodataLoader = preferences.getInt(KeyMihomoGeodataLoader, defaults.mihomoGeodataLoader),
            resourceFileSource = preferences.getInt(KeyResourceFileSource, defaults.resourceFileSource),
            customResourceFileGeoIpUrl = preferences.getString(
                KeyCustomResourceFileGeoIpUrl,
                defaults.customResourceFileGeoIpUrl,
            ) ?: defaults.customResourceFileGeoIpUrl,
            customResourceFileGeoSiteUrl = preferences.getString(
                KeyCustomResourceFileGeoSiteUrl,
                defaults.customResourceFileGeoSiteUrl,
            ) ?: defaults.customResourceFileGeoSiteUrl,
            customResourceFileMmdbUrl = preferences.getString(
                KeyCustomResourceFileMmdbUrl,
                defaults.customResourceFileMmdbUrl,
            ) ?: defaults.customResourceFileMmdbUrl,
            customResourceFileAsnUrl = preferences.getString(
                KeyCustomResourceFileAsnUrl,
                defaults.customResourceFileAsnUrl,
            ) ?: defaults.customResourceFileAsnUrl,
            customResourceFileDirectCidrIpv4Url = preferences.getString(
                KeyCustomResourceFileDirectCidrIpv4Url,
                defaults.customResourceFileDirectCidrIpv4Url,
            ) ?: defaults.customResourceFileDirectCidrIpv4Url,
            customResourceFileDirectCidrIpv6Url = preferences.getString(
                KeyCustomResourceFileDirectCidrIpv6Url,
                defaults.customResourceFileDirectCidrIpv6Url,
            ) ?: defaults.customResourceFileDirectCidrIpv6Url,
            customResourceFiles = customResourceFiles,
            nextCustomResourceFileId = nextCustomResourceFileId,
            enableSniffer = preferences.getBoolean(KeyEnableSniffer, defaults.enableSniffer),
            enableSnifferOverrideDestination = preferences.getBoolean(
                KeyEnableSnifferOverrideDestination,
                defaults.enableSnifferOverrideDestination,
            ),
            enableIpv6 = preferences.getBoolean(KeyEnableIpv6, defaults.enableIpv6),
            enableIpv6Prefer = preferences.getBoolean(KeyEnableIpv6Prefer, defaults.enableIpv6Prefer),
            overrideDns = preferences.getBoolean(KeyOverrideDns, defaults.overrideDns),
            dnsPreferH3 = preferences.getBoolean(KeyDnsPreferH3, defaults.dnsPreferH3),
            dnsUseHosts = preferences.getBoolean(KeyDnsUseHosts, defaults.dnsUseHosts),
            dnsUseSystemHosts = preferences.getBoolean(KeyDnsUseSystemHosts, defaults.dnsUseSystemHosts),
            dnsRespectRules = preferences.getBoolean(KeyDnsRespectRules, defaults.dnsRespectRules),
            dnsEnhancedMode = preferences.getInt(KeyDnsEnhancedMode, defaults.dnsEnhancedMode),
            dnsFakeIpRange = preferences.getString(KeyDnsFakeIpRange, defaults.dnsFakeIpRange)
                ?: defaults.dnsFakeIpRange,
            dnsFakeIpFilter = preferences.getStringList(KeyDnsFakeIpFilter, defaults.dnsFakeIpFilter),
            dnsDefaultNameserver = preferences.getStringList(
                KeyDnsDefaultNameserver,
                defaults.dnsDefaultNameserver,
            ),
            dnsNameserver = preferences.getStringList(KeyDnsNameserver, defaults.dnsNameserver),
            dnsNameserverPolicy = preferences.getStringList(KeyDnsNameserverPolicy, defaults.dnsNameserverPolicy),
            dnsProxyServerNameserver = preferences.getStringList(
                KeyDnsProxyServerNameserver,
                defaults.dnsProxyServerNameserver,
            ),
            dnsFallback = preferences.getStringList(KeyDnsFallback, defaults.dnsFallback),
            dnsFallbackFilterGeoip = preferences.getBoolean(
                KeyDnsFallbackFilterGeoip,
                defaults.dnsFallbackFilterGeoip,
            ),
            dnsFallbackFilterGeoipCode = preferences.getString(
                KeyDnsFallbackFilterGeoipCode,
                defaults.dnsFallbackFilterGeoipCode,
            ) ?: defaults.dnsFallbackFilterGeoipCode,
            dnsFallbackFilterGeosite = preferences.getStringList(
                KeyDnsFallbackFilterGeosite,
                defaults.dnsFallbackFilterGeosite,
            ),
            dnsFallbackFilterIpcidr = preferences.getStringList(
                KeyDnsFallbackFilterIpcidr,
                defaults.dnsFallbackFilterIpcidr,
            ),
            dnsFallbackFilterDomain = preferences.getStringList(
                KeyDnsFallbackFilterDomain,
                defaults.dnsFallbackFilterDomain,
            ),
            dnsHosts = preferences.getStringList(KeyDnsHosts, defaults.dnsHosts),
            transparentProxyPort = preferences.getString(
                KeyTransparentProxyPort,
                defaults.transparentProxyPort,
            ) ?: defaults.transparentProxyPort,
            enableRootBootScript = preferences.getBoolean(
                KeyEnableRootBootScript,
                defaults.enableRootBootScript,
            ),
            enableRootEbpfRules = preferences.getBoolean(
                KeyEnableRootEbpfRules,
                defaults.enableRootEbpfRules,
            ),
            enableRootEbpfDirectCidrBypass = preferences.getBoolean(
                KeyEnableRootEbpfDirectCidrBypass,
                defaults.enableRootEbpfDirectCidrBypass,
            ),
            enableRootIpv6Disabler = preferences.getBoolean(
                KeyEnableRootIpv6Disabler,
                defaults.enableRootIpv6Disabler,
            ),
            socks5ProxyPort = preferences.getString(
                KeySocks5ProxyPort,
                defaults.socks5ProxyPort,
            ) ?: defaults.socks5ProxyPort,
            externalInterfaces = preferences.getStringList(KeyExternalInterfaces, defaults.externalInterfaces),
            ignoredInterfaces = preferences.getStringList(KeyIgnoredInterfaces, defaults.ignoredInterfaces),
            privateAddressCidrs = preferences.getStringList(KeyPrivateAddressCidrs, defaults.privateAddressCidrs),
            proxyAppListMode = preferences.getInt(KeyProxyAppListMode, defaults.proxyAppListMode),
        )
    }

    fun save(state: AppState) {
        preferences.edit { putAppState(state) }
    }

    private fun SharedPreferences.Editor.putAppState(state: AppState): SharedPreferences.Editor {
        return putInt(KeyColorMode, state.colorMode)
            .putInt(KeyLanguageMode, state.languageMode)
            .putInt(KeySeedIndex, state.seedIndex)
            .putInt(KeyNextMihomoProfileId, state.nextMihomoProfileId)
            .putInt(KeyNextMihomoOverrideScriptId, state.nextMihomoOverrideScriptId)
            .putInt(KeySelectedMihomoProfileId, state.selectedMihomoProfileId)
            .putInt(KeyRunMode, state.runMode)
            .putInt(KeyMihomoMode, state.mihomoMode)
            .putBoolean(KeyMihomoProxyExcludeNotSelectable, state.mihomoProxyExcludeNotSelectable)
            .putInt(KeyMihomoProxyLayout, state.mihomoProxyLayout)
            .putInt(KeyMihomoProxySort, state.mihomoProxySort)
            .putInt(KeyMihomoTunStack, state.mihomoTunStack)
            .putString(KeyMihomoControlPort, state.mihomoControlPort)
            .putString(KeyMihomoControlSecret, state.mihomoControlSecret)
            .putBoolean(KeyEnableLocalDns, state.enableLocalDns)
            .putString(KeyLocalProxyPort, state.localProxyPort)
            .putBoolean(KeyEnableDynamicLocalProxyPort, state.enableDynamicLocalProxyPort)
            .putBoolean(KeyLocalProxyListenAllInterfaces, state.localProxyListenAllInterfaces)
            .putString(KeyLocalProxyUsername, state.localProxyUsername)
            .putString(KeyLocalProxyPassword, state.localProxyPassword)
            .putBoolean(KeyEnableVpnAppendHttpProxy, state.enableVpnAppendHttpProxy)
            .putString(KeyTunMtu, state.tunMtu)
            .putString(KeyTunVpnDns, state.tunVpnDns)
            .putString(KeyTunIpv4Cidr, state.tunIpv4Cidr)
            .putString(KeyTunIpv6Cidr, state.tunIpv6Cidr)
            .putInt(KeyCoreLogLevel, state.coreLogLevel)
            .putBoolean(KeyEnableGeodataMode, state.enableGeodataMode)
            .putInt(KeyMihomoGeodataLoader, state.mihomoGeodataLoader)
            .putInt(KeyResourceFileSource, state.resourceFileSource)
            .putString(KeyCustomResourceFileGeoIpUrl, state.customResourceFileGeoIpUrl)
            .putString(KeyCustomResourceFileGeoSiteUrl, state.customResourceFileGeoSiteUrl)
            .putString(KeyCustomResourceFileMmdbUrl, state.customResourceFileMmdbUrl)
            .putString(KeyCustomResourceFileAsnUrl, state.customResourceFileAsnUrl)
            .putString(KeyCustomResourceFileDirectCidrIpv4Url, state.customResourceFileDirectCidrIpv4Url)
            .putString(KeyCustomResourceFileDirectCidrIpv6Url, state.customResourceFileDirectCidrIpv6Url)
            .putCustomResourceFileList(KeyCustomResourceFiles, state.customResourceFiles)
            .putInt(KeyNextCustomResourceFileId, state.nextCustomResourceFileId)
            .putBoolean(KeyEnableSniffer, state.enableSniffer)
            .putBoolean(KeyEnableSnifferOverrideDestination, state.enableSnifferOverrideDestination)
            .putBoolean(KeyEnableIpv6, state.enableIpv6)
            .putBoolean(KeyEnableIpv6Prefer, state.enableIpv6Prefer)
            .putBoolean(KeyOverrideDns, state.overrideDns)
            .putBoolean(KeyDnsPreferH3, state.dnsPreferH3)
            .putBoolean(KeyDnsUseHosts, state.dnsUseHosts)
            .putBoolean(KeyDnsUseSystemHosts, state.dnsUseSystemHosts)
            .putBoolean(KeyDnsRespectRules, state.dnsRespectRules)
            .putInt(KeyDnsEnhancedMode, state.dnsEnhancedMode)
            .putString(KeyDnsFakeIpRange, state.dnsFakeIpRange)
            .putStringList(KeyDnsFakeIpFilter, state.dnsFakeIpFilter)
            .putStringList(KeyDnsDefaultNameserver, state.dnsDefaultNameserver)
            .putStringList(KeyDnsNameserver, state.dnsNameserver)
            .putStringList(KeyDnsNameserverPolicy, state.dnsNameserverPolicy)
            .putStringList(KeyDnsProxyServerNameserver, state.dnsProxyServerNameserver)
            .putStringList(KeyDnsFallback, state.dnsFallback)
            .putBoolean(KeyDnsFallbackFilterGeoip, state.dnsFallbackFilterGeoip)
            .putString(KeyDnsFallbackFilterGeoipCode, state.dnsFallbackFilterGeoipCode)
            .putStringList(KeyDnsFallbackFilterGeosite, state.dnsFallbackFilterGeosite)
            .putStringList(KeyDnsFallbackFilterIpcidr, state.dnsFallbackFilterIpcidr)
            .putStringList(KeyDnsFallbackFilterDomain, state.dnsFallbackFilterDomain)
            .putStringList(KeyDnsHosts, state.dnsHosts)
            .putString(KeyTransparentProxyPort, state.transparentProxyPort)
            .putBoolean(KeyEnableRootBootScript, state.enableRootBootScript)
            .putBoolean(KeyEnableRootEbpfRules, state.enableRootEbpfRules)
            .putBoolean(KeyEnableRootEbpfDirectCidrBypass, state.enableRootEbpfDirectCidrBypass)
            .putBoolean(KeyEnableRootIpv6Disabler, state.enableRootIpv6Disabler)
            .putString(KeySocks5ProxyPort, state.socks5ProxyPort)
            .putStringList(KeyExternalInterfaces, state.externalInterfaces)
            .putStringList(KeyIgnoredInterfaces, state.ignoredInterfaces)
            .putStringList(KeyPrivateAddressCidrs, state.privateAddressCidrs)
            .putInt(KeyProxyAppListMode, state.proxyAppListMode)
    }

    private fun SharedPreferences.getStringList(key: String, defaultValue: List<String>): List<String> {
        return getString(key, null)?.let(StringListJson::decode) ?: defaultValue
    }

    private fun SharedPreferences.Editor.putStringList(
        key: String,
        values: List<String>,
    ): SharedPreferences.Editor {
        return putString(key, StringListJson.encode(values))
    }

    private fun SharedPreferences.getCustomResourceFileList(
        key: String,
        defaultValue: List<CustomResourceFileState>,
    ): List<CustomResourceFileState> {
        return getString(key, null)?.let(CustomResourceFileListJson::decode) ?: defaultValue
    }

    private fun SharedPreferences.Editor.putCustomResourceFileList(
        key: String,
        values: List<CustomResourceFileState>,
    ): SharedPreferences.Editor {
        return putString(key, CustomResourceFileListJson.encode(values))
    }
}

private const val PreferencesName = "asteriskmeta_settings"
private const val KeyColorMode = "color_mode"
private const val KeyLanguageMode = "language_mode"
private const val KeySeedIndex = "seed_index"
private const val KeyNextMihomoProfileId = "next_mihomo_profile_id"
private const val KeyNextMihomoOverrideScriptId = "next_mihomo_override_script_id"
private const val KeySelectedMihomoProfileId = "selected_mihomo_profile_id"
private const val KeyRunMode = "run_mode"
private const val KeyMihomoMode = "mihomo_mode"
private const val KeyMihomoProxyExcludeNotSelectable = "mihomo_proxy_exclude_not_selectable"
private const val KeyMihomoProxyLayout = "mihomo_proxy_layout"
private const val KeyMihomoProxySort = "mihomo_proxy_sort"
private const val KeyMihomoTunStack = "mihomo_tun_stack"
private const val KeyMihomoControlPort = "mihomo_control_port"
private const val KeyMihomoControlSecret = "mihomo_control_secret"
private const val KeyEnableLocalDns = "enable_local_dns"
private const val KeyLocalProxyPort = "local_proxy_port"
private const val KeyEnableDynamicLocalProxyPort = "enable_dynamic_local_proxy_port"
private const val KeyLocalProxyListenAllInterfaces = "local_proxy_listen_all_interfaces"
private const val KeyLocalProxyUsername = "local_proxy_username"
private const val KeyLocalProxyPassword = "local_proxy_password"
private const val KeyEnableVpnAppendHttpProxy = "enable_vpn_append_http_proxy"
private const val KeyTunMtu = "tun_mtu"
private const val KeyTunVpnDns = "tun_vpn_dns"
private const val KeyTunIpv4Cidr = "tun_ipv4_cidr"
private const val KeyTunIpv6Cidr = "tun_ipv6_cidr"
private const val KeyCoreLogLevel = "core_log_level"
private const val KeyEnableGeodataMode = "enable_geodata_mode"
private const val KeyMihomoGeodataLoader = "mihomo_geodata_loader"
private const val KeyResourceFileSource = "resource_file_source"
private const val KeyCustomResourceFileGeoIpUrl = "custom_resource_file_geoip_url"
private const val KeyCustomResourceFileGeoSiteUrl = "custom_resource_file_geosite_url"
private const val KeyCustomResourceFileMmdbUrl = "custom_resource_file_mmdb_url"
private const val KeyCustomResourceFileAsnUrl = "custom_resource_file_asn_url"
private const val KeyCustomResourceFileDirectCidrIpv4Url = "custom_resource_file_direct_cidr_ipv4_url"
private const val KeyCustomResourceFileDirectCidrIpv6Url = "custom_resource_file_direct_cidr_ipv6_url"
private const val KeyCustomResourceFiles = "custom_resource_files"
private const val KeyNextCustomResourceFileId = "next_custom_resource_file_id"
private const val KeyEnableSniffer = "enable_sniffer"
private const val KeyEnableSnifferOverrideDestination = "enable_sniffer_override_destination"
private const val KeyEnableIpv6 = "enable_ipv6"
private const val KeyEnableIpv6Prefer = "enable_ipv6_prefer"
private const val KeyOverrideDns = "override_dns"
private const val KeyDnsPreferH3 = "dns_prefer_h3"
private const val KeyDnsUseHosts = "dns_use_hosts"
private const val KeyDnsUseSystemHosts = "dns_use_system_hosts"
private const val KeyDnsRespectRules = "dns_respect_rules"
private const val KeyDnsEnhancedMode = "dns_enhanced_mode"
private const val KeyDnsFakeIpRange = "dns_fake_ip_range"
private const val KeyDnsFakeIpFilter = "dns_fake_ip_filter"
private const val KeyDnsDefaultNameserver = "dns_default_nameserver"
private const val KeyDnsNameserver = "dns_nameserver"
private const val KeyDnsNameserverPolicy = "dns_nameserver_policy"
private const val KeyDnsProxyServerNameserver = "dns_proxy_server_nameserver"
private const val KeyDnsFallback = "dns_fallback"
private const val KeyDnsFallbackFilterGeoip = "dns_fallback_filter_geoip"
private const val KeyDnsFallbackFilterGeoipCode = "dns_fallback_filter_geoip_code"
private const val KeyDnsFallbackFilterGeosite = "dns_fallback_filter_geosite"
private const val KeyDnsFallbackFilterIpcidr = "dns_fallback_filter_ipcidr"
private const val KeyDnsFallbackFilterDomain = "dns_fallback_filter_domain"
private const val KeyDnsHosts = "dns_hosts"
private const val KeyTransparentProxyPort = "transparent_proxy_port"
private const val KeyEnableRootBootScript = "enable_root_boot_script"
private const val KeyEnableRootEbpfRules = "enable_root_ebpf_rules"
private const val KeyEnableRootEbpfDirectCidrBypass = "enable_root_ebpf_direct_cidr_bypass"
private const val KeyEnableRootIpv6Disabler = "enable_root_ipv6_disabler"
private const val KeySocks5ProxyPort = "socks5_proxy_port"
private const val KeyExternalInterfaces = "external_interfaces"
private const val KeyIgnoredInterfaces = "ignored_interfaces"
private const val KeyPrivateAddressCidrs = "private_address_cidrs"
private const val KeyProxyAppListMode = "proxy_app_list_mode"
