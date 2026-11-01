// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import android.os.Build
import app.modes.RunModeBpf2Socks
import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import app.modes.RunModeVpnService
import features.enhancement.EnhancementModulePreference
import app.modes.supportsAppListEnhancement
import app.modes.isRootRunMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import ui.icons.AsteriskIcons as Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import app.R
import system.AndroidLauncherIcon
import ui.components.IconAccent
import ui.components.WarningConfirmDialog
import ui.theme.AsteriskMotion
import androidx.compose.ui.res.stringResource

@Composable
internal fun settingsTunStackOptions() = listOf(
    stringResource(R.string.settings_tun_stack_system),
    stringResource(R.string.settings_tun_stack_gvisor),
    stringResource(R.string.settings_tun_stack_mixed),
    stringResource(R.string.settings_tun_stack_mips),
)

@Composable
internal fun SettingsAppSection(
    languageOptions: List<String>,
    languageMode: Int,
    colorModeOptions: List<String>,
    colorMode: Int,
    keyColorOptions: List<String>,
    seedIndex: Int,
    onColorModeChange: (Int) -> Unit,
    onSeedIndexChange: (Int) -> Unit,
    onLanguageModeChange: (Int) -> Unit,
    autoHideNavigationBar: Boolean,
    onAutoHideNavigationBarChange: (Boolean) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_app))
    SettingsSectionCard {
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_language),
            icon = Icons.Rounded.Language,
            items = languageOptions,
            selectedIndex = languageMode,
            onSelectedIndexChange = onLanguageModeChange,
            accent = IconAccent.MaskBlue,
        )
    }
    ThemeSettingsContent(
        colorModeOptions = colorModeOptions,
        colorMode = colorMode,
        keyColorOptions = keyColorOptions,
        seedIndex = seedIndex,
        onColorModeChange = onColorModeChange,
        onSeedIndexChange = onSeedIndexChange,
    )
    val autoHideNavigationBarTitle = stringResource(R.string.settings_auto_hide_navigation_bar)
    val autoHideNavigationBarSummary = stringResource(R.string.settings_auto_hide_navigation_bar_summary)
    if (settingsRowMatchesQuery(autoHideNavigationBarTitle, autoHideNavigationBarSummary, autoHideNavigationBar.toString())) {
        SettingsSectionCard {
            SwitchPreference(
                title = autoHideNavigationBarTitle,
                icon = Icons.Rounded.ExpandMore,
                summary = autoHideNavigationBarSummary,
                checked = autoHideNavigationBar,
                onCheckedChange = onAutoHideNavigationBarChange,
                accent = IconAccent.MaskBlueVariant,
            )
        }
    }
}

@Composable
internal fun SettingsPrivacySection(
    hideRecentTasks: Boolean,
    onHideRecentTasksChange: (Boolean) -> Unit,
    allowScreenshots: Boolean,
    onAllowScreenshotsChange: (Boolean) -> Unit,
    hideLauncherIcon: Boolean,
    onHideLauncherIconChange: (Boolean) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_privacy))
    SettingsSectionCard {
        SwitchPreference(
            title = stringResource(R.string.settings_hide_recent_tasks),
            icon = Icons.Rounded.Apps,
            summary = stringResource(R.string.settings_hide_recent_tasks_summary),
            checked = hideRecentTasks,
            onCheckedChange = onHideRecentTasksChange,
            accent = IconAccent.MaskGrey,
        )
        SwitchPreference(
            title = stringResource(R.string.settings_allow_screenshots),
            icon = Icons.Rounded.Lock,
            summary = stringResource(R.string.settings_allow_screenshots_summary),
            checked = allowScreenshots,
            onCheckedChange = onAllowScreenshotsChange,
            accent = IconAccent.MaskPurple,
        )
        if (AndroidLauncherIcon.isSupported) {
            HideLauncherIconPreference(
                hideLauncherIcon = hideLauncherIcon,
                onHideLauncherIconChange = onHideLauncherIconChange,
            )
        }
    }
}

@Composable
private fun HideLauncherIconPreference(
    hideLauncherIcon: Boolean,
    onHideLauncherIconChange: (Boolean) -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }
    val dialCode = AndroidLauncherIcon.DIAL_CODE
    SwitchPreference(
        title = stringResource(R.string.settings_hide_launcher_icon),
        icon = Icons.Rounded.Widgets,
        summary = stringResource(R.string.settings_hide_launcher_icon_summary, dialCode),
        checked = hideLauncherIcon,
        onCheckedChange = { hide ->
            if (hide) {
                showConfirm = true
            } else {
                onHideLauncherIconChange(false)
            }
        },
        accent = IconAccent.MaskPink,
    )
    WarningConfirmDialog(
        show = showConfirm,
        title = stringResource(R.string.settings_hide_launcher_icon),
        summary = stringResource(R.string.settings_hide_launcher_icon_confirm, dialCode),
        dismissText = stringResource(R.string.common_cancel),
        confirmText = stringResource(R.string.common_ok),
        onDismissRequest = { showConfirm = false },
        onConfirm = {
            showConfirm = false
            onHideLauncherIconChange(true)
        },
    )
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
    onOpenConnectionTest: () -> Unit,
    onOpenAutoSwitch: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenResourceManagement: () -> Unit,
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
        ArrowPreference(
            title = stringResource(R.string.settings_resource_management),
            accent = IconAccent.MaskOrange,
            icon = Icons.Rounded.Folder,
            summary = stringResource(R.string.settings_resource_management_summary),
            onClick = onOpenResourceManagement,
        )
        if (!rawEnabled) {
            ArrowPreference(
                title = stringResource(R.string.settings_dns),
                accent = IconAccent.MaskBlueVariant,
                icon = Icons.Rounded.Dns,
                summary = stringResource(R.string.settings_dns_summary),
                onClick = onOpenDnsSettings,
            )
            ArrowPreference(
                title = stringResource(R.string.settings_sniffer),
                accent = IconAccent.MaskGreen,
                icon = Icons.Rounded.TravelExplore,
                summary = snifferSettingsSummary,
                onClick = onOpenSnifferSettings,
            )
            if (!engine.mihomo.BundledCore.usesRealMmdb) {
                SwitchPreference(
                    title = stringResource(R.string.settings_geodata_mode),
                    accent = IconAccent.MaskBlue,
                    icon = Icons.Rounded.Public,
                    summary = stringResource(R.string.settings_geodata_mode_summary),
                    checked = enableGeodataMode,
                    onCheckedChange = onEnableGeodataModeChange,
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_geodata_loader),
                    accent = IconAccent.MaskGrey,
                    icon = Icons.Rounded.Storage,
                    summary = stringResource(R.string.settings_geodata_loader_summary),
                    items = geodataLoaderOptions,
                    selectedIndex = geodataLoader.coerceIn(geodataLoaderOptions.indices),
                    onSelectedIndexChange = onGeodataLoaderChange,
                )
            }
        } else {
            SwitchPreference(
                title = stringResource(R.string.settings_dns),
                accent = IconAccent.MaskBlueVariant,
                icon = Icons.Rounded.Dns,
                summary = raw?.let { "DNS hijack · ${it.dnsHijack.path}" }
                    ?: rawState.unavailableReason.orEmpty(),
                checked = enableLocalDns,
                onCheckedChange = onEnableLocalDnsChange,
            )
            SettingsReadOnlyRow(
                title = stringResource(R.string.settings_sniffer),
                accent = IconAccent.MaskGreen,
                value = raw?.snifferEnabled?.value?.toString() ?: notConfigured,
                source = fromYaml,
                summary = raw?.snifferEnabled?.path ?: rawState.unavailableReason.orEmpty(),
                icon = Icons.Rounded.TravelExplore,
            )
            SettingsReadOnlyRow(
                title = stringResource(R.string.settings_geodata_mode),
                accent = IconAccent.MaskBlue,
                value = raw?.geodataMode?.value?.toString() ?: notConfigured,
                source = fromYaml,
                summary = raw?.geodataMode?.path ?: rawState.unavailableReason.orEmpty(),
                icon = Icons.Rounded.Public,
            )
            SettingsReadOnlyRow(
                title = stringResource(R.string.settings_geodata_loader),
                accent = IconAccent.MaskGrey,
                value = raw?.geodataLoader?.value ?: notConfigured,
                source = fromYaml,
                summary = raw?.geodataLoader?.path ?: rawState.unavailableReason.orEmpty(),
                icon = Icons.Rounded.Storage,
            )
        }
        ArrowPreference(
            title = stringResource(R.string.proxy_app_list_title),
            accent = IconAccent.MaskPurple,
            icon = Icons.Rounded.Apps,
            summary = stringResource(R.string.settings_app_management_summary),
            onClick = onOpenApps,
        )
        ArrowPreference(
            title = stringResource(R.string.connection_test_settings_title),
            icon = Icons.Rounded.Speed,
            summary = stringResource(R.string.connection_test_settings_summary),
            onClick = onOpenConnectionTest,
            accent = IconAccent.MaskBlue,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_auto_switch),
            summary = stringResource(R.string.settings_auto_switch_summary),
            icon = Icons.Rounded.Sync,
            accent = IconAccent.MaskGreen,
            onClick = onOpenAutoSwitch,
        )
        if (!rawEnabled) {
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_log_level),
                accent = IconAccent.MaskRed,
                icon = Icons.Rounded.BugReport,
                items = SettingsLogLevelOptions,
                selectedIndex = coreLogLevel,
                onSelectedIndexChange = onCoreLogLevelChange,
            )
        } else {
            SettingsReadOnlyRow(
                title = stringResource(R.string.settings_log_level),
                accent = IconAccent.MaskRed,
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
            accent = IconAccent.MaskYellow,
            icon = Icons.Rounded.CellTower,
            summary = stringResource(R.string.settings_broadcast_control_summary),
            checked = enableBroadcastControl,
            onCheckedChange = onEnableBroadcastControlChange,
        )
        if (!rawState.showsReadOnlyYamlValues) {
            SwitchPreference(
                title = "IPv6",
                accent = IconAccent.MaskBlue,
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
                    accent = IconAccent.MaskGreen,
                    icon = Icons.Rounded.Route,
                    summary = stringResource(R.string.settings_ipv6_prefer_summary),
                    checked = enableIpv6Prefer,
                    onCheckedChange = onEnableIpv6PreferChange,
                )
            }
            ArrowPreference(
                title = stringResource(R.string.mihomo_profile_override_script),
                accent = IconAccent.MaskPurple,
                icon = Icons.Rounded.Code,
                summary = overrideScriptSummary,
                onClick = onOpenOverrideScripts,
            )
        } else {
            SettingsReadOnlyRow(
                title = "IPv6",
                accent = IconAccent.MaskBlue,
                value = raw?.ipv6?.value?.toString() ?: stringResource(R.string.settings_value_not_configured),
                source = stringResource(R.string.settings_value_from_yaml),
                summary = raw?.ipv6?.path ?: rawState.unavailableReason.orEmpty(),
                icon = Icons.Rounded.Public,
            )
            SettingsReadOnlyRow(
                title = stringResource(R.string.mihomo_profile_override_script),
                accent = IconAccent.MaskPurple,
                value = stringResource(R.string.mihomo_profile_override_script_stopped),
                source = stringResource(R.string.mihomo_profile_raw_chip),
                icon = Icons.Rounded.Lock,
            )
        }
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_run_mode),
            accent = IconAccent.MaskPurple,
            icon = Icons.Rounded.SettingsEthernet,
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
    enableAutoConnect: Boolean,
    tunSettingsSummary: String,
    enableRootBootScript: Boolean,
    enableRootEbpfRules: Boolean,
    enableRootEbpfDirectCidrBypass: Boolean,
    enableIpv6: Boolean,
    enableRootIpv6Disabler: Boolean,
    externalInterfacesSummary: String,
    ignoredInterfacesSummary: String,
    privateAddressCidrsSummary: String,
    tunBypassRuleSetsSummary: String,
    onOpenTunBypassRuleSets: () -> Unit,
    onOpenLocalProxySettings: () -> Unit,
    onEnableTrafficStatsNotificationChange: (Boolean) -> Unit,
    onEnableVpnAppendHttpProxyChange: (Boolean) -> Unit,
    onEnableVpnHevTunChange: (Boolean) -> Unit,
    onEnableAutoConnectChange: (Boolean) -> Unit,
    onOpenTunSettings: () -> Unit,
    onEnableRootBootScriptChange: (Boolean) -> Unit,
    onEnableRootEbpfRulesChange: (Boolean) -> Unit,
    onEnableRootEbpfDirectCidrBypassChange: (Boolean) -> Unit,
    onEnableRootIpv6DisablerChange: (Boolean) -> Unit,
    onOpenExternalInterfaces: () -> Unit,
    onOpenServiceControl: () -> Unit,
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
                SwitchPreference(
                    title = stringResource(R.string.settings_start_on_boot),
                    icon = Icons.Rounded.PowerSettingsNew,
                    summary = stringResource(R.string.settings_auto_connect_summary),
                    checked = enableAutoConnect,
                    onCheckedChange = onEnableAutoConnectChange,
                    accent = IconAccent.MaskPink,
                )
                if (!rawState.showsReadOnlyYamlValues) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_local_proxy),
                        accent = IconAccent.MaskBlue,
                        icon = Icons.Rounded.Router,
                        summary = localProxySettingsSummary,
                        onClick = onOpenLocalProxySettings,
                    )
                } else {
                    SettingsReadOnlyRow(
                        title = stringResource(R.string.settings_local_proxy),
                        accent = IconAccent.MaskBlue,
                        value = raw?.socksInbound?.value?.port?.toString()
                            ?: stringResource(R.string.settings_value_not_configured),
                        source = stringResource(R.string.settings_value_from_yaml),
                        summary = raw?.socksInbound?.path ?: rawState.unavailableReason.orEmpty(),
                        icon = Icons.Rounded.Router,
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.settings_traffic_stats_notification),
                    accent = IconAccent.MaskPink,
                    icon = Icons.Rounded.Notifications,
                    summary = stringResource(R.string.settings_traffic_stats_notification_summary),
                    checked = enableTrafficStatsNotification,
                    onCheckedChange = onEnableTrafficStatsNotificationChange,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_vpn_append_http_proxy),
                        accent = IconAccent.MaskOrange,
                        icon = Icons.Rounded.Http,
                        summary = stringResource(R.string.settings_vpn_append_http_proxy_summary),
                        checked = enableVpnAppendHttpProxy,
                        onCheckedChange = onEnableVpnAppendHttpProxyChange,
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.settings_vpn_hev_tun),
                    accent = IconAccent.MaskYellow,
                    icon = Icons.Rounded.Memory,
                    summary = stringResource(R.string.settings_vpn_hev_tun_summary),
                    checked = enableVpnHevTun,
                    onCheckedChange = onEnableVpnHevTunChange,
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_tun),
                    accent = IconAccent.MaskBlueVariant,
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
                        title = stringResource(R.string.settings_start_on_boot),
                        accent = IconAccent.MaskPink,
                        icon = Icons.Rounded.PowerSettingsNew,
                        summary = stringResource(R.string.settings_root_boot_script_summary),
                        checked = enableRootBootScript,
                        onCheckedChange = onEnableRootBootScriptChange,
                    )
                }
                ArrowPreference(
                    title = stringResource(R.string.settings_service_control),
                    accent = IconAccent.MaskPink,
                    icon = Icons.Rounded.PowerSettingsNew,
                    summary = stringResource(R.string.settings_service_control_summary),
                    onClick = onOpenServiceControl,
                )
                if (runMode == RunModeTun && !rawState.showsReadOnlyYamlValues) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_root_ebpf_bypass_direct_cidrs),
                        accent = IconAccent.MaskGreen,
                        icon = Icons.Rounded.Route,
                        summary = tunBypassRuleSetsSummary,
                        onClick = onOpenTunBypassRuleSets,
                    )
                }
                AnimatedVisibility(
                    visible = runMode != RunModeBpf2Socks && runMode != RunModeTun,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_root_ebpf_matcher),
                        accent = IconAccent.MaskPurple,
                        icon = Icons.Rounded.Security,
                        summary = stringResource(R.string.settings_root_ebpf_matcher_summary),
                        checked = enableRootEbpfRules,
                        onCheckedChange = onEnableRootEbpfRulesChange,
                    )
                }
                AnimatedVisibility(
                    visible = runMode != RunModeTun && (enableRootEbpfRules || runMode == RunModeBpf2Socks),
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_root_ebpf_bypass_direct_cidrs),
                        accent = IconAccent.MaskGreen,
                        icon = Icons.Rounded.Route,
                        summary = stringResource(R.string.settings_root_ebpf_bypass_direct_cidrs_summary),
                        checked = enableRootEbpfDirectCidrBypass,
                        onCheckedChange = onEnableRootEbpfDirectCidrBypassChange,
                    )
                }
                if (runMode.supportsAppListEnhancement()) {
                    EnhancementModulePreference()
                }
                AnimatedVisibility(
                    visible = !enableIpv6,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_root_ipv6_disabler),
                        accent = IconAccent.MaskBlue,
                        icon = Icons.Rounded.Public,
                        summary = stringResource(R.string.settings_root_ipv6_disabler_summary),
                        checked = enableRootIpv6Disabler,
                        onCheckedChange = onEnableRootIpv6DisablerChange,
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.settings_traffic_stats_notification),
                    accent = IconAccent.MaskPink,
                    icon = Icons.Rounded.Notifications,
                    summary = stringResource(R.string.settings_traffic_stats_notification_summary),
                    checked = enableTrafficStatsNotification,
                    onCheckedChange = onEnableTrafficStatsNotificationChange,
                )
                if (!rawState.showsReadOnlyYamlValues) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_local_proxy),
                        accent = IconAccent.MaskBlue,
                        icon = Icons.Rounded.Router,
                        summary = localProxySettingsSummary,
                        onClick = onOpenLocalProxySettings,
                    )
                } else {
                    SettingsReadOnlyRow(
                        title = stringResource(R.string.settings_local_proxy),
                        accent = IconAccent.MaskBlue,
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
                            accent = IconAccent.MaskBlueVariant,
                            icon = Icons.Rounded.SettingsInputComponent,
                            summary = tunSettingsSummary,
                            onClick = onOpenTunSettings,
                        )
                    } else {
                        SettingsReadOnlyRow(
                            title = stringResource(R.string.settings_tun),
                            accent = IconAccent.MaskBlueVariant,
                            value = raw?.tunInbound?.value?.let { "${it.device} · ${it.stack} · ${it.mtu}" }
                                ?: stringResource(R.string.settings_value_not_configured),
                            source = stringResource(R.string.settings_value_from_yaml),
                            summary = raw?.tunInbound?.path ?: rawState.unavailableReason.orEmpty(),
                            icon = Icons.Rounded.SettingsInputComponent,
                        )
                    }
                }
                ArrowPreference(
                    title = stringResource(if (runMode == RunModeTun) R.string.settings_tun_shared_network else R.string.settings_external_interfaces),
                    accent = IconAccent.MaskGrey,
                    icon = Icons.Rounded.Cable,
                    summary = externalInterfacesSummary,
                    onClick = onOpenExternalInterfaces,
                )
                if (runMode != RunModeTun) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_ignored_interfaces),
                        accent = IconAccent.MaskRed,
                        icon = Icons.Rounded.Block,
                        summary = ignoredInterfacesSummary,
                        onClick = onOpenIgnoredInterfaces,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_private_addresses),
                        accent = IconAccent.MaskOrange,
                        icon = Icons.Rounded.HomeWork,
                        summary = privateAddressCidrsSummary,
                        onClick = onOpenPrivateAddresses,
                    )
                }
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
            title = stringResource(R.string.core_logs_title),
            accent = IconAccent.MaskGreen,
            icon = Icons.AutoMirrored.Rounded.Article,
            onClick = onOpenCoreLogs,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_logcat),
            accent = IconAccent.MaskPurple,
            icon = Icons.Rounded.Terminal,
            onClick = onOpenLogcatLogs,
        )
    }
}

@Composable
internal fun SettingsBackupRestoreSection(
    progressText: String?,
    onBackupUserData: () -> Unit,
    onRestoreUserData: () -> Unit,
) {
    val busy = progressText != null
    SmallTitle(text = stringResource(R.string.settings_backup_restore))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_backup_user_data),
            summary = stringResource(R.string.settings_backup_user_data_summary),
            icon = Icons.Rounded.FileUpload,
            enabled = !busy,
            onClick = onBackupUserData,
            accent = IconAccent.MaskYellow,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_restore_user_data),
            summary = stringResource(R.string.settings_restore_user_data_summary),
            icon = Icons.Rounded.FileDownload,
            enabled = !busy,
            onClick = onRestoreUserData,
            accent = IconAccent.MaskYellow,
        )
        AnimatedVisibility(
            visible = progressText != null,
            enter = AsteriskMotion.contentEnter(),
            exit = AsteriskMotion.contentExit(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = progressText.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun SettingsAboutSection(
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.about_title))
    SettingsSectionCard(bottomPadding = 0.dp) {
        ArrowPreference(
            title = stringResource(R.string.settings_about_project),
            accent = IconAccent.MaskBlue,
            icon = Icons.AutoMirrored.Rounded.Help,
            onClick = onOpenAbout,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_open_source_licenses),
            accent = IconAccent.MaskGrey,
            icon = Icons.Rounded.Policy,
            onClick = onOpenLicenses,
        )
    }
}
