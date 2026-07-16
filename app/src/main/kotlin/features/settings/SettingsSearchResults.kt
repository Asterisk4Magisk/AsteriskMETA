// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.R
import ui.icons.AsteriskIcons as Icons

internal data class SettingsSearchEntry(
    val title: String,
    val parent: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

internal fun filterSettingsSearchEntries(
    entries: List<SettingsSearchEntry>,
    query: String,
): List<SettingsSearchEntry> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return entries
    return entries.filter { entry ->
        entry.title.contains(normalizedQuery, ignoreCase = true) ||
            entry.parent.contains(normalizedQuery, ignoreCase = true)
    }
}

@Composable
internal fun settingsTopLevelSearchItems(
    colorModeOptions: List<String>,
    colorMode: Int,
    keyColorOptions: List<String>,
    seedIndex: Int,
    languageOptions: List<String>,
    languageMode: Int,
    geodataLoaderOptions: List<String>,
    geodataLoader: Int,
    coreLogLevel: Int,
    runModeOptions: List<String>,
    selectedRunModeIndex: Int,
    snifferSummary: String,
    localProxySummary: String,
    overrideScriptSummary: String,
    tunSummary: String,
    externalInterfacesSummary: String,
    ignoredInterfacesSummary: String,
    privateAddressesSummary: String,
): List<SettingsSearchItem> {
    fun optionValue(options: List<String>, index: Int): String =
        options.getOrNull(index).orEmpty()

    return listOf(
        SettingsSearchItem(
            SettingsSectionId.Theme,
            stringResource(R.string.settings_color_mode),
            value = optionValue(colorModeOptions, colorMode),
            optionText = colorModeOptions,
        ),
        SettingsSearchItem(
            SettingsSectionId.Theme,
            stringResource(R.string.settings_theme_color),
            value = optionValue(keyColorOptions, seedIndex),
            optionText = keyColorOptions,
        ),
        SettingsSearchItem(
            SettingsSectionId.Theme,
            stringResource(R.string.settings_language),
            value = optionValue(languageOptions, languageMode),
            optionText = languageOptions,
        ),
        SettingsSearchItem(
            SettingsSectionId.Configurations,
            stringResource(R.string.proxy_app_list_title),
            stringResource(R.string.proxy_app_list_settings_summary),
        ),
        SettingsSearchItem(
            SettingsSectionId.Configurations,
            stringResource(R.string.settings_resource_management),
            stringResource(R.string.settings_resource_management_summary),
        ),
        SettingsSearchItem(
            SettingsSectionId.Core,
            stringResource(R.string.settings_dns),
            stringResource(R.string.settings_dns_summary),
        ),
        SettingsSearchItem(SettingsSectionId.Core, stringResource(R.string.settings_sniffer), snifferSummary),
        SettingsSearchItem(
            SettingsSectionId.Core,
            stringResource(R.string.settings_geodata_mode),
            stringResource(R.string.settings_geodata_mode_summary),
        ),
        SettingsSearchItem(
            SettingsSectionId.Core,
            stringResource(R.string.settings_geodata_loader),
            stringResource(R.string.settings_geodata_loader_summary),
            optionValue(geodataLoaderOptions, geodataLoader),
            geodataLoaderOptions,
        ),
        SettingsSearchItem(
            SettingsSectionId.Core,
            stringResource(R.string.settings_log_level),
            value = optionValue(SettingsLogLevelOptions, coreLogLevel),
            optionText = SettingsLogLevelOptions,
        ),
        SettingsSearchItem(
            SettingsSectionId.Advanced,
            stringResource(R.string.settings_broadcast_control),
            stringResource(R.string.settings_broadcast_control_summary),
        ),
        SettingsSearchItem(SettingsSectionId.Advanced, "IPv6", stringResource(R.string.settings_ipv6_summary)),
        SettingsSearchItem(
            SettingsSectionId.Advanced,
            stringResource(R.string.settings_ipv6_prefer),
            stringResource(R.string.settings_ipv6_prefer_summary),
        ),
        SettingsSearchItem(
            SettingsSectionId.Advanced,
            stringResource(R.string.mihomo_configuration_override_script),
            overrideScriptSummary,
        ),
        SettingsSearchItem(
            SettingsSectionId.Advanced,
            stringResource(R.string.settings_run_mode),
            value = optionValue(runModeOptions, selectedRunModeIndex),
            optionText = runModeOptions,
        ),
        SettingsSearchItem(SettingsSectionId.Vpn, stringResource(R.string.settings_local_proxy), localProxySummary),
        SettingsSearchItem(
            SettingsSectionId.Vpn,
            stringResource(R.string.settings_traffic_stats_notification),
            stringResource(R.string.settings_traffic_stats_notification_summary),
        ),
        SettingsSearchItem(
            SettingsSectionId.Vpn,
            stringResource(R.string.settings_vpn_append_http_proxy),
            stringResource(R.string.settings_vpn_append_http_proxy_summary),
        ),
        SettingsSearchItem(
            SettingsSectionId.Vpn,
            stringResource(R.string.settings_vpn_hev_tun),
            stringResource(R.string.settings_vpn_hev_tun_summary),
        ),
        SettingsSearchItem(SettingsSectionId.Vpn, stringResource(R.string.settings_tun), tunSummary),
        SettingsSearchItem(
            SettingsSectionId.Tproxy,
            stringResource(R.string.settings_root_boot_script),
            stringResource(R.string.settings_root_boot_script_summary),
        ),
        SettingsSearchItem(
            SettingsSectionId.Tproxy,
            stringResource(R.string.settings_root_ebpf_matcher),
            stringResource(R.string.settings_root_ebpf_matcher_summary),
        ),
        SettingsSearchItem(
            SettingsSectionId.Tproxy,
            stringResource(R.string.settings_root_ebpf_bypass_direct_cidrs),
            stringResource(R.string.settings_root_ebpf_bypass_direct_cidrs_summary),
        ),
        SettingsSearchItem(
            SettingsSectionId.Tproxy,
            stringResource(R.string.settings_root_ipv6_disabler),
            stringResource(R.string.settings_root_ipv6_disabler_summary),
        ),
        SettingsSearchItem(SettingsSectionId.Tproxy, stringResource(R.string.settings_external_interfaces), externalInterfacesSummary),
        SettingsSearchItem(SettingsSectionId.Tproxy, stringResource(R.string.settings_ignored_interfaces), ignoredInterfacesSummary),
        SettingsSearchItem(SettingsSectionId.Tproxy, stringResource(R.string.settings_private_addresses), privateAddressesSummary),
        SettingsSearchItem(SettingsSectionId.Logs, stringResource(R.string.settings_core_logs)),
        SettingsSearchItem(SettingsSectionId.Logs, stringResource(R.string.settings_logcat)),
        SettingsSearchItem(SettingsSectionId.About, stringResource(R.string.settings_about_project)),
        SettingsSearchItem(SettingsSectionId.About, stringResource(R.string.settings_open_source_licenses)),
        SettingsSearchItem(
            SettingsSectionId.Advanced,
            title = "Boolean",
            optionText = listOf("true", "false"),
        ),
    )
}

@Composable
internal fun SettingsNestedSearchResults(
    query: String,
    entries: List<SettingsSearchEntry>,
) {
    val results = filterSettingsSearchEntries(entries, query)
    if (results.isEmpty()) return
    SmallTitle(text = stringResource(R.string.settings_search_results))
    SettingsSectionCard {
        results.forEach { entry ->
            SettingsActionRow(
                title = entry.title,
                summary = entry.parent,
                icon = entry.icon,
                onClick = entry.onClick,
            )
        }
    }
}

@Composable
internal fun settingsNestedSearchEntries(
    onOpenDns: () -> Unit,
    onOpenSniffer: () -> Unit,
    onOpenLocalProxy: () -> Unit,
    onOpenTun: () -> Unit,
    onOpenExternalInterfaces: () -> Unit,
    onOpenIgnoredInterfaces: () -> Unit,
    onOpenPrivateAddresses: () -> Unit,
): List<SettingsSearchEntry> {
    val dns = stringResource(R.string.settings_dns)
    val sniffer = stringResource(R.string.settings_sniffer)
    val localProxy = stringResource(R.string.settings_local_proxy)
    val tun = stringResource(R.string.settings_tun)
    val externalInterfaces = stringResource(R.string.settings_external_interfaces)
    val ignoredInterfaces = stringResource(R.string.settings_ignored_interfaces)
    val privateAddresses = stringResource(R.string.settings_private_addresses)

    val dnsItems = listOf(
        stringResource(R.string.settings_dns_override),
        stringResource(R.string.settings_dns_enhanced_mode),
        stringResource(R.string.settings_dns_respect_rules),
        stringResource(R.string.settings_dns_prefer_h3),
        stringResource(R.string.settings_dns_fake_ip_range),
        stringResource(R.string.settings_dns_fake_ip_filter),
        stringResource(R.string.settings_dns_default_nameserver),
        stringResource(R.string.settings_dns_nameserver),
        stringResource(R.string.settings_dns_proxy_server_nameserver),
        stringResource(R.string.settings_dns_fallback),
        stringResource(R.string.settings_dns_nameserver_policy),
        stringResource(R.string.settings_dns_geoip_filter),
        stringResource(R.string.settings_dns_geoip_code),
        stringResource(R.string.settings_dns_domain),
        stringResource(R.string.settings_dns_use_hosts),
        stringResource(R.string.settings_dns_use_system_hosts),
        stringResource(R.string.settings_dns_hosts),
    )
    val snifferItems = listOf(
        stringResource(R.string.settings_sniffer_enable),
        stringResource(R.string.settings_sniffer_override_destination),
        stringResource(R.string.settings_sniffer_force_dns_mapping),
        stringResource(R.string.settings_sniffer_parse_pure_ip),
        stringResource(R.string.settings_sniffer_http_ports),
        stringResource(R.string.settings_sniffer_tls_ports),
        stringResource(R.string.settings_sniffer_quic_ports),
        stringResource(R.string.settings_sniffer_http_override_destination),
        stringResource(R.string.settings_sniffer_tls_override_destination),
        stringResource(R.string.settings_sniffer_quic_override_destination),
        stringResource(R.string.settings_sniffer_force_domain),
        stringResource(R.string.settings_sniffer_skip_domain),
        stringResource(R.string.settings_sniffer_skip_src_address),
        stringResource(R.string.settings_sniffer_skip_dst_address),
    )
    val localProxyItems = listOf(
        stringResource(R.string.settings_local_proxy_port),
        stringResource(R.string.settings_local_proxy_dynamic_port),
        stringResource(R.string.settings_local_proxy_listen_all_interfaces),
        stringResource(R.string.settings_local_proxy_username),
        stringResource(R.string.settings_local_proxy_password),
    )
    val tunItems = listOf(
        stringResource(R.string.settings_tun_stack),
        stringResource(R.string.settings_tun_mtu),
        stringResource(R.string.settings_tun_vpn_dns),
        stringResource(R.string.settings_tun_ipv4_cidr),
        stringResource(R.string.settings_tun_ipv6_cidr),
    )
    val externalItems = listOf(
        stringResource(R.string.settings_external_interfaces_wifi),
        stringResource(R.string.settings_external_interfaces_usb),
        stringResource(R.string.settings_external_interfaces_bluetooth),
        stringResource(R.string.settings_external_interfaces_ethernet),
    )

    return buildList {
        dnsItems.forEach { add(SettingsSearchEntry(it, dns, Icons.Rounded.Dns, onOpenDns)) }
        snifferItems.forEach { add(SettingsSearchEntry(it, sniffer, Icons.Rounded.TravelExplore, onOpenSniffer)) }
        localProxyItems.forEach { add(SettingsSearchEntry(it, localProxy, Icons.Rounded.Router, onOpenLocalProxy)) }
        tunItems.forEach { add(SettingsSearchEntry(it, tun, Icons.Rounded.SettingsInputComponent, onOpenTun)) }
        externalItems.forEach { add(SettingsSearchEntry(it, externalInterfaces, Icons.Rounded.Cable, onOpenExternalInterfaces)) }
        add(SettingsSearchEntry(ignoredInterfaces, ignoredInterfaces, Icons.Rounded.Block, onOpenIgnoredInterfaces))
        add(SettingsSearchEntry(privateAddresses, privateAddresses, Icons.Rounded.HomeWork, onOpenPrivateAddresses))
    }
}
