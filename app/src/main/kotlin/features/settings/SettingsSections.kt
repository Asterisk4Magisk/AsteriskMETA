// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import app.modes.RunModeBpf2Socks
import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import app.modes.RunModeVpnService
import app.modes.isRootRunMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import ui.icons.AsteriskIcons as Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.R
import ui.theme.AsteriskMotion
import androidx.compose.ui.res.stringResource

@Composable
internal fun settingsTunStackOptions() = listOf(
    stringResource(R.string.settings_tun_stack_system),
    stringResource(R.string.settings_tun_stack_gvisor),
    stringResource(R.string.settings_tun_stack_mixed),
)

@Composable
internal fun SettingsThemeSection(
    colorModeOptions: List<String>,
    colorMode: Int,
    keyColorOptions: List<String>,
    seedIndex: Int,
    languageOptions: List<String>,
    languageMode: Int,
    onColorModeChange: (Int) -> Unit,
    onSeedIndexChange: (Int) -> Unit,
    onLanguageModeChange: (Int) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_theme))
    SettingsSectionCard {
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_color_mode),
            icon = Icons.Rounded.DarkMode,
            items = colorModeOptions,
            selectedIndex = colorMode,
            onSelectedIndexChange = onColorModeChange,
        )
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_theme_color),
            icon = Icons.Rounded.Palette,
            items = keyColorOptions,
            selectedIndex = seedIndex,
            onSelectedIndexChange = onSeedIndexChange,
        )
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_language),
            icon = Icons.Rounded.Language,
            items = languageOptions,
            selectedIndex = languageMode,
            onSelectedIndexChange = onLanguageModeChange,
        )
    }
}

@Composable
internal fun SettingsSubscriptionsSection(
    onOpenProxyAppList: () -> Unit,
    onOpenResourceManagement: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_configurations))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.proxy_app_list_title),
            icon = Icons.Rounded.Apps,
            summary = stringResource(R.string.proxy_app_list_settings_summary),
            onClick = onOpenProxyAppList,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_resource_management),
            icon = Icons.Rounded.Folder,
            summary = stringResource(R.string.settings_resource_management_summary),
            onClick = onOpenResourceManagement,
        )
    }
}

@Composable
internal fun SettingsCoreSection(
    snifferSettingsSummary: String,
    enableGeodataMode: Boolean,
    geodataLoaderOptions: List<String>,
    geodataLoader: Int,
    coreLogLevel: Int,
    enableLocalDns: Boolean,
    onOpenDnsSettings: () -> Unit,
    onOpenSnifferSettings: () -> Unit,
    onEnableGeodataModeChange: (Boolean) -> Unit,
    onGeodataLoaderChange: (Int) -> Unit,
    onCoreLogLevelChange: (Int) -> Unit,
    onEnableLocalDnsChange: (Boolean) -> Unit,
) {
    val rawState = LocalRawConfigState.current
    val raw = rawState.snapshot
    val rawEnabled = rawState.showsReadOnlyYamlValues
    val fromYaml = stringResource(R.string.settings_value_from_yaml)
    val notConfigured = stringResource(R.string.settings_value_not_configured)
    SmallTitle(text = stringResource(R.string.settings_core))
    SettingsSectionCard {
        if (!rawEnabled) {
            ArrowPreference(
                title = stringResource(R.string.settings_dns),
                icon = Icons.Rounded.Dns,
                summary = stringResource(R.string.settings_dns_summary),
                onClick = onOpenDnsSettings,
            )
            ArrowPreference(
                title = stringResource(R.string.settings_sniffer),
                icon = Icons.Rounded.TravelExplore,
                summary = snifferSettingsSummary,
                onClick = onOpenSnifferSettings,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_geodata_mode),
                icon = Icons.Rounded.Public,
                summary = stringResource(R.string.settings_geodata_mode_summary),
                checked = enableGeodataMode,
                onCheckedChange = onEnableGeodataModeChange,
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_geodata_loader),
                icon = Icons.Rounded.Storage,
                summary = stringResource(R.string.settings_geodata_loader_summary),
                items = geodataLoaderOptions,
                selectedIndex = geodataLoader.coerceIn(geodataLoaderOptions.indices),
                onSelectedIndexChange = onGeodataLoaderChange,
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_log_level),
                icon = Icons.Rounded.BugReport,
                items = SettingsLogLevelOptions,
                selectedIndex = coreLogLevel,
                onSelectedIndexChange = onCoreLogLevelChange,
            )
        } else {
            SwitchPreference(
                title = stringResource(R.string.settings_dns),
                icon = Icons.Rounded.Dns,
                summary = raw?.let { "DNS hijack · ${it.dnsHijack.path}" }
                    ?: rawState.unavailableReason.orEmpty(),
                checked = enableLocalDns,
                onCheckedChange = onEnableLocalDnsChange,
            )
            SettingsReadOnlyRow(
                title = stringResource(R.string.settings_sniffer),
                value = raw?.snifferEnabled?.value?.toString() ?: notConfigured,
                source = fromYaml,
                summary = raw?.snifferEnabled?.path ?: rawState.unavailableReason.orEmpty(),
                icon = Icons.Rounded.TravelExplore,
            )
            SettingsReadOnlyRow(
                title = stringResource(R.string.settings_geodata_mode),
                value = raw?.geodataMode?.value?.toString() ?: notConfigured,
                source = fromYaml,
                summary = raw?.geodataMode?.path ?: rawState.unavailableReason.orEmpty(),
                icon = Icons.Rounded.Public,
            )
            SettingsReadOnlyRow(
                title = stringResource(R.string.settings_geodata_loader),
                value = raw?.geodataLoader?.value ?: notConfigured,
                source = fromYaml,
                summary = raw?.geodataLoader?.path ?: rawState.unavailableReason.orEmpty(),
                icon = Icons.Rounded.Storage,
            )
            SettingsReadOnlyRow(
                title = stringResource(R.string.settings_log_level),
                value = raw?.logLevel?.value ?: notConfigured,
                source = fromYaml,
                summary = raw?.logLevel?.path ?: rawState.unavailableReason.orEmpty(),
                icon = Icons.Rounded.BugReport,
            )
        }
    }
}

@Composable
internal fun SettingsAdvancedSection(
    enableBroadcastControl: Boolean,
    enableIpv6: Boolean,
    enableIpv6Prefer: Boolean,
    runModeOptions: List<String>,
    selectedRunModeIndex: Int,
    overrideScriptSummary: String,
    onOpenOverrideScripts: () -> Unit,
    onEnableBroadcastControlChange: (Boolean) -> Unit,
    onEnableIpv6Change: (Boolean) -> Unit,
    onEnableIpv6PreferChange: (Boolean) -> Unit,
    onRunModeChange: (Int) -> Unit,
) {
    val rawState = LocalRawConfigState.current
    val raw = rawState.snapshot
    SmallTitle(text = stringResource(R.string.settings_advanced))
    SettingsSectionCard {
        SwitchPreference(
            title = stringResource(R.string.settings_broadcast_control),
            icon = Icons.Rounded.CellTower,
            summary = stringResource(R.string.settings_broadcast_control_summary),
            checked = enableBroadcastControl,
            onCheckedChange = onEnableBroadcastControlChange,
        )
        if (!rawState.showsReadOnlyYamlValues) {
            SwitchPreference(
                title = "IPv6",
                icon = Icons.Rounded.Public,
                summary = stringResource(R.string.settings_ipv6_summary),
                checked = enableIpv6,
                onCheckedChange = onEnableIpv6Change,
            )
            AnimatedVisibility(
                visible = enableIpv6,
                enter = AsteriskMotion.contentEnter(),
                exit = AsteriskMotion.contentExit(),
            ) {
                SwitchPreference(
                    title = stringResource(R.string.settings_ipv6_prefer),
                    icon = Icons.Rounded.Route,
                    summary = stringResource(R.string.settings_ipv6_prefer_summary),
                    checked = enableIpv6Prefer,
                    onCheckedChange = onEnableIpv6PreferChange,
                )
            }
            ArrowPreference(
                title = stringResource(R.string.mihomo_configuration_override_script),
                icon = Icons.Rounded.Code,
                summary = overrideScriptSummary,
                onClick = onOpenOverrideScripts,
            )
        } else {
            SettingsReadOnlyRow(
                title = "IPv6",
                value = raw?.ipv6?.value?.toString() ?: stringResource(R.string.settings_value_not_configured),
                source = stringResource(R.string.settings_value_from_yaml),
                summary = raw?.ipv6?.path ?: rawState.unavailableReason.orEmpty(),
                icon = Icons.Rounded.Public,
            )
            SettingsReadOnlyRow(
                title = stringResource(R.string.mihomo_configuration_override_script),
                value = stringResource(R.string.mihomo_configuration_override_script_stopped),
                source = stringResource(R.string.mihomo_configuration_raw_chip),
                icon = Icons.Rounded.Lock,
            )
        }
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_run_mode),
            icon = Icons.Rounded.AccountTree,
            items = runModeOptions,
            selectedIndex = selectedRunModeIndex.coerceIn(runModeOptions.indices),
            onSelectedIndexChange = onRunModeChange,
        )
    }
}

@Composable
internal fun SettingsProxyModeSections(
    runMode: Int,
    localProxySettingsSummary: String,
    enableTrafficStatsNotification: Boolean,
    enableVpnAppendHttpProxy: Boolean,
    enableVpnHevTun: Boolean,
    tunSettingsSummary: String,
    enableRootBootScript: Boolean,
    enableRootEbpfRules: Boolean,
    enableRootEbpfDirectCidrBypass: Boolean,
    enableIpv6: Boolean,
    enableRootIpv6Disabler: Boolean,
    externalInterfacesSummary: String,
    ignoredInterfacesSummary: String,
    privateAddressCidrsSummary: String,
    onOpenLocalProxySettings: () -> Unit,
    onEnableTrafficStatsNotificationChange: (Boolean) -> Unit,
    onEnableVpnAppendHttpProxyChange: (Boolean) -> Unit,
    onEnableVpnHevTunChange: (Boolean) -> Unit,
    onOpenTunSettings: () -> Unit,
    onEnableRootBootScriptChange: (Boolean) -> Unit,
    onEnableRootEbpfRulesChange: (Boolean) -> Unit,
    onEnableRootEbpfDirectCidrBypassChange: (Boolean) -> Unit,
    onEnableRootIpv6DisablerChange: (Boolean) -> Unit,
    onOpenExternalInterfaces: () -> Unit,
    onOpenIgnoredInterfaces: () -> Unit,
    onOpenPrivateAddresses: () -> Unit,
) {
    val rawState = LocalRawConfigState.current
    val raw = rawState.snapshot
    AnimatedVisibility(
        visible = runMode == RunModeVpnService,
        enter = AsteriskMotion.contentEnter(),
        exit = ExitTransition.None,
    ) {
        Column {
            SmallTitle(text = stringResource(R.string.settings_proxy_vpn_service))
            SettingsSectionCard {
                if (!rawState.showsReadOnlyYamlValues) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_local_proxy),
                        icon = Icons.Rounded.Router,
                        summary = localProxySettingsSummary,
                        onClick = onOpenLocalProxySettings,
                    )
                } else {
                    SettingsReadOnlyRow(
                        title = stringResource(R.string.settings_local_proxy),
                        value = raw?.socksInbound?.value?.port?.toString()
                            ?: stringResource(R.string.settings_value_not_configured),
                        source = stringResource(R.string.settings_value_from_yaml),
                        summary = raw?.socksInbound?.path ?: rawState.unavailableReason.orEmpty(),
                        icon = Icons.Rounded.Router,
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.settings_traffic_stats_notification),
                    icon = Icons.Rounded.Notifications,
                    summary = stringResource(R.string.settings_traffic_stats_notification_summary),
                    checked = enableTrafficStatsNotification,
                    onCheckedChange = onEnableTrafficStatsNotificationChange,
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_vpn_append_http_proxy),
                    icon = Icons.Rounded.Http,
                    summary = stringResource(R.string.settings_vpn_append_http_proxy_summary),
                    checked = enableVpnAppendHttpProxy,
                    onCheckedChange = onEnableVpnAppendHttpProxyChange,
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_vpn_hev_tun),
                    icon = Icons.Rounded.Memory,
                    summary = stringResource(R.string.settings_vpn_hev_tun_summary),
                    checked = enableVpnHevTun,
                    onCheckedChange = onEnableVpnHevTunChange,
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_tun),
                    icon = Icons.Rounded.SettingsInputComponent,
                    summary = tunSettingsSummary,
                    onClick = onOpenTunSettings,
                )
            }
        }
    }
    AnimatedVisibility(
        visible = runMode.isRootRunMode(),
        enter = AsteriskMotion.contentEnter(),
        exit = ExitTransition.None,
    ) {
        Column {
            SmallTitle(
                text = stringResource(
                    when (runMode) {
                        RunModeTun -> R.string.settings_proxy_tun
                        RunModeTun2Socks -> R.string.settings_proxy_tun2socks
                        RunModeBpf2Socks -> R.string.settings_proxy_bpf2socks
                        else -> R.string.settings_proxy_tproxy
                    },
                ),
            )
            SettingsSectionCard {
                AnimatedVisibility(
                    visible = runMode.isRootRunMode(),
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_root_boot_script),
                        icon = Icons.Rounded.PowerSettingsNew,
                        summary = stringResource(R.string.settings_root_boot_script_summary),
                        checked = enableRootBootScript,
                        onCheckedChange = onEnableRootBootScriptChange,
                    )
                }
                AnimatedVisibility(
                    visible = runMode != RunModeBpf2Socks,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_root_ebpf_matcher),
                        icon = Icons.Rounded.Security,
                        summary = stringResource(R.string.settings_root_ebpf_matcher_summary),
                        checked = enableRootEbpfRules,
                        onCheckedChange = onEnableRootEbpfRulesChange,
                    )
                }
                AnimatedVisibility(
                    visible = enableRootEbpfRules || runMode == RunModeBpf2Socks,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_root_ebpf_bypass_direct_cidrs),
                        icon = Icons.Rounded.Route,
                        summary = stringResource(R.string.settings_root_ebpf_bypass_direct_cidrs_summary),
                        checked = enableRootEbpfDirectCidrBypass,
                        onCheckedChange = onEnableRootEbpfDirectCidrBypassChange,
                    )
                }
                AnimatedVisibility(
                    visible = !enableIpv6,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_root_ipv6_disabler),
                        icon = Icons.Rounded.Public,
                        summary = stringResource(R.string.settings_root_ipv6_disabler_summary),
                        checked = enableRootIpv6Disabler,
                        onCheckedChange = onEnableRootIpv6DisablerChange,
                    )
                }
                if (!rawState.showsReadOnlyYamlValues) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_local_proxy),
                        icon = Icons.Rounded.Router,
                        summary = localProxySettingsSummary,
                        onClick = onOpenLocalProxySettings,
                    )
                } else {
                    SettingsReadOnlyRow(
                        title = stringResource(R.string.settings_local_proxy),
                        value = raw?.socksInbound?.value?.port?.toString()
                            ?: stringResource(R.string.settings_value_not_configured),
                        source = stringResource(R.string.settings_value_from_yaml),
                        summary = raw?.socksInbound?.path ?: rawState.unavailableReason.orEmpty(),
                        icon = Icons.Rounded.Router,
                    )
                }
                AnimatedVisibility(
                    visible = runMode == RunModeTun || runMode == RunModeTun2Socks,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                ) {
                    if (!rawState.showsReadOnlyYamlValues || runMode == RunModeTun2Socks) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_tun),
                            icon = Icons.Rounded.SettingsInputComponent,
                            summary = tunSettingsSummary,
                            onClick = onOpenTunSettings,
                        )
                    } else {
                        SettingsReadOnlyRow(
                            title = stringResource(R.string.settings_tun),
                            value = raw?.tunInbound?.value?.let { "${it.device} · ${it.stack} · ${it.mtu}" }
                                ?: stringResource(R.string.settings_value_not_configured),
                            source = stringResource(R.string.settings_value_from_yaml),
                            summary = raw?.tunInbound?.path ?: rawState.unavailableReason.orEmpty(),
                            icon = Icons.Rounded.SettingsInputComponent,
                        )
                    }
                }
                ArrowPreference(
                    title = stringResource(R.string.settings_external_interfaces),
                    icon = Icons.Rounded.Cable,
                    summary = externalInterfacesSummary,
                    onClick = onOpenExternalInterfaces,
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_ignored_interfaces),
                    icon = Icons.Rounded.Block,
                    summary = ignoredInterfacesSummary,
                    onClick = onOpenIgnoredInterfaces,
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_private_addresses),
                    icon = Icons.Rounded.HomeWork,
                    summary = privateAddressCidrsSummary,
                    onClick = onOpenPrivateAddresses,
                )
            }
        }
    }
}

@Composable
internal fun SettingsLogsSection(
    onOpenCoreLogs: () -> Unit,
    onOpenLogcatLogs: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_logs))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_core_logs),
            icon = Icons.AutoMirrored.Rounded.Article,
            onClick = onOpenCoreLogs,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_logcat),
            icon = Icons.Rounded.Terminal,
            onClick = onOpenLogcatLogs,
        )
    }
}

@Composable
internal fun SettingsAboutSection(
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_about))
    SettingsSectionCard(bottomPadding = 0.dp) {
        ArrowPreference(
            title = stringResource(R.string.settings_about_project),
            icon = Icons.AutoMirrored.Rounded.Help,
            onClick = onOpenAbout,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_open_source_licenses),
            icon = Icons.Rounded.Policy,
            onClick = onOpenLicenses,
        )
    }
}
