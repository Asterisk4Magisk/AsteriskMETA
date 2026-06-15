// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import app.modes.RunModeVpnService
import app.modes.isRootRunMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.R
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

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
    isThemeColorMode: Boolean,
    onColorModeChange: (Int) -> Unit,
    onSeedIndexChange: (Int) -> Unit,
    onLanguageModeChange: (Int) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_theme))
    SettingsSectionCard {
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_color_mode),
            items = colorModeOptions,
            selectedIndex = colorMode,
            onSelectedIndexChange = onColorModeChange,
        )
        AnimatedVisibility(
            visible = isThemeColorMode,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_theme_color),
                items = keyColorOptions,
                selectedIndex = seedIndex,
                onSelectedIndexChange = onSeedIndexChange,
            )
        }
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_language),
            items = languageOptions,
            selectedIndex = languageMode,
            onSelectedIndexChange = onLanguageModeChange,
        )
    }
}

@Composable
internal fun SettingsSubscriptionsSection(
    onOpenConfigurationManagement: () -> Unit,
    onOpenResourceManagement: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_configurations))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_configuration_management),
            summary = stringResource(R.string.settings_configuration_management_summary),
            onClick = onOpenConfigurationManagement,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_resource_management),
            summary = stringResource(R.string.settings_resource_management_summary),
            onClick = onOpenResourceManagement,
        )
    }
}

@Composable
internal fun SettingsCoreSection(
    enableSniffer: Boolean,
    enableSnifferOverrideDestination: Boolean,
    enableGeodataMode: Boolean,
    geodataLoaderOptions: List<String>,
    geodataLoader: Int,
    coreLogLevel: Int,
    onOpenDnsSettings: () -> Unit,
    onEnableSnifferChange: (Boolean) -> Unit,
    onEnableSnifferOverrideDestinationChange: (Boolean) -> Unit,
    onEnableGeodataModeChange: (Boolean) -> Unit,
    onGeodataLoaderChange: (Int) -> Unit,
    onCoreLogLevelChange: (Int) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_core))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_dns),
            summary = stringResource(R.string.settings_dns_summary),
            onClick = onOpenDnsSettings,
        )
        SwitchPreference(
            title = stringResource(R.string.settings_sniffer),
            summary = stringResource(R.string.settings_sniffer_summary),
            checked = enableSniffer,
            onCheckedChange = onEnableSnifferChange,
        )
        AnimatedVisibility(
            visible = enableSniffer,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SwitchPreference(
                title = stringResource(R.string.settings_sniffer_override_destination),
                summary = stringResource(R.string.settings_sniffer_override_destination_summary),
                checked = enableSnifferOverrideDestination,
                onCheckedChange = onEnableSnifferOverrideDestinationChange,
            )
        }
        SwitchPreference(
            title = stringResource(R.string.settings_geodata_mode),
            summary = stringResource(R.string.settings_geodata_mode_summary),
            checked = enableGeodataMode,
            onCheckedChange = onEnableGeodataModeChange,
        )
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_geodata_loader),
            summary = stringResource(R.string.settings_geodata_loader_summary),
            items = geodataLoaderOptions,
            selectedIndex = geodataLoader.coerceIn(geodataLoaderOptions.indices),
            onSelectedIndexChange = onGeodataLoaderChange,
        )
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_log_level),
            items = SettingsLogLevelOptions,
            selectedIndex = coreLogLevel,
            onSelectedIndexChange = onCoreLogLevelChange,
        )
    }
}

@Composable
internal fun SettingsAdvancedSection(
    enableIpv6: Boolean,
    enableIpv6Prefer: Boolean,
    runModeOptions: List<String>,
    selectedRunModeIndex: Int,
    overrideScriptSummary: String,
    onOpenOverrideScripts: () -> Unit,
    onEnableIpv6Change: (Boolean) -> Unit,
    onEnableIpv6PreferChange: (Boolean) -> Unit,
    onRunModeChange: (Int) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_advanced))
    SettingsSectionCard {
        SwitchPreference(
            title = "IPv6",
            summary = stringResource(R.string.settings_ipv6_summary),
            checked = enableIpv6,
            onCheckedChange = onEnableIpv6Change,
        )
        AnimatedVisibility(
            visible = enableIpv6,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SwitchPreference(
                title = stringResource(R.string.settings_ipv6_prefer),
                summary = stringResource(R.string.settings_ipv6_prefer_summary),
                checked = enableIpv6Prefer,
                onCheckedChange = onEnableIpv6PreferChange,
            )
        }
        ArrowPreference(
            title = stringResource(R.string.mihomo_configuration_override_script),
            summary = overrideScriptSummary,
            onClick = onOpenOverrideScripts,
        )
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_run_mode),
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
    enableVpnAppendHttpProxy: Boolean,
    tunSettingsSummary: String,
    enableRootBootScript: Boolean,
    enableIpv6: Boolean,
    enableRootIpv6Disabler: Boolean,
    externalInterfacesSummary: String,
    ignoredInterfacesSummary: String,
    privateAddressCidrsSummary: String,
    onOpenLocalProxySettings: () -> Unit,
    onEnableVpnAppendHttpProxyChange: (Boolean) -> Unit,
    onOpenTunSettings: () -> Unit,
    onEnableRootBootScriptChange: (Boolean) -> Unit,
    onEnableRootIpv6DisablerChange: (Boolean) -> Unit,
    onOpenExternalInterfaces: () -> Unit,
    onOpenIgnoredInterfaces: () -> Unit,
    onOpenPrivateAddresses: () -> Unit,
) {
    AnimatedVisibility(
        visible = runMode == RunModeVpnService,
        enter = fadeIn() + expandVertically(),
        exit = ExitTransition.None,
    ) {
        Column {
            SmallTitle(text = stringResource(R.string.settings_proxy_vpn_service))
            SettingsSectionCard {
                ArrowPreference(
                    title = stringResource(R.string.settings_local_proxy),
                    summary = localProxySettingsSummary,
                    onClick = onOpenLocalProxySettings,
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_vpn_append_http_proxy),
                    summary = stringResource(R.string.settings_vpn_append_http_proxy_summary),
                    checked = enableVpnAppendHttpProxy,
                    onCheckedChange = onEnableVpnAppendHttpProxyChange,
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_tun),
                    summary = tunSettingsSummary,
                    onClick = onOpenTunSettings,
                )
            }
        }
    }
    AnimatedVisibility(
        visible = runMode.isRootRunMode(),
        enter = fadeIn() + expandVertically(),
        exit = ExitTransition.None,
    ) {
        Column {
            SmallTitle(
                text = stringResource(
                    when (runMode) {
                        RunModeTun -> R.string.settings_proxy_tun
                        RunModeTun2Socks -> R.string.settings_proxy_tun2socks
                        else -> R.string.settings_proxy_tproxy
                    },
                ),
            )
            SettingsSectionCard {
                AnimatedVisibility(
                    visible = runMode.isRootRunMode(),
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_root_boot_script),
                        summary = stringResource(R.string.settings_root_boot_script_summary),
                        checked = enableRootBootScript,
                        onCheckedChange = onEnableRootBootScriptChange,
                    )
                }
                AnimatedVisibility(
                    visible = !enableIpv6,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_root_ipv6_disabler),
                        summary = stringResource(R.string.settings_root_ipv6_disabler_summary),
                        checked = enableRootIpv6Disabler,
                        onCheckedChange = onEnableRootIpv6DisablerChange,
                    )
                }
                ArrowPreference(
                    title = stringResource(R.string.settings_local_proxy),
                    summary = localProxySettingsSummary,
                    onClick = onOpenLocalProxySettings,
                )
                AnimatedVisibility(
                    visible = runMode == RunModeTun || runMode == RunModeTun2Socks,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_tun),
                        summary = tunSettingsSummary,
                        onClick = onOpenTunSettings,
                    )
                }
                ArrowPreference(
                    title = stringResource(R.string.settings_external_interfaces),
                    summary = externalInterfacesSummary,
                    onClick = onOpenExternalInterfaces,
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_ignored_interfaces),
                    summary = ignoredInterfacesSummary,
                    onClick = onOpenIgnoredInterfaces,
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_private_addresses),
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
            onClick = onOpenCoreLogs,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_logcat),
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
            onClick = onOpenAbout,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_open_source_licenses),
            onClick = onOpenLicenses,
        )
    }
}
