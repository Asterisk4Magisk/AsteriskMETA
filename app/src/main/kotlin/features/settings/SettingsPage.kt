// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.settings

import app.LocalAppChromeState
import app.LocalAppStateStore
import app.LocalAppServices
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.modes.ColorModeThemeDark
import app.modes.ColorModeThemeSystem
import app.collectAppState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import app.modes.RunModeTproxy
import app.modes.RunModeVpnService
import app.modes.isRootRunMode
import app.ProjectInfo
import app.R
import engine.mihomo.MihomoGeodataLoaderValues
import engine.proxy.withResolvedDynamicLocalProxyPort
import features.settings.sheets.externalInterfacesSummary
import features.settings.sheets.ignoredInterfacesSummary
import features.settings.sheets.privateAddressCidrsSummary
import features.settings.sheets.tunSettingsSummary
import features.settings.usecase.SwitchRunModeResult
import features.settings.usecase.RootBootScriptResult
import features.settings.usecase.RootEbpfProbeResult
import kotlinx.coroutines.launch
import app.navigation.Route
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import ui.KeyColors
import ui.components.WarningConfirmDialog
import ui.text.formatTemplate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

@Composable
fun SettingsPage(
    padding: PaddingValues,
) {
    val languageMode = LocalAppChromeState.current.languageMode
    val isWideScreen = LocalIsWideScreen.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_title),
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    subtitle = "v${ProjectInfo.VERSION_NAME} (${ProjectInfo.VERSION_CODE})",
                )
            }
        },
    ) { innerPadding ->
        SettingsContent(
            innerPadding = innerPadding,
            outerPadding = padding,
            topAppBarScrollBehavior = topAppBarScrollBehavior,
        )
    }
}

@Composable
private fun SettingsContent(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val isWideScreen = LocalIsWideScreen.current
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val services = LocalAppServices.current
    val networkInterfaces = services.networkInterfaces
    val switchRunModeUseCase = services.switchRunModeUseCase
    val rootBootScriptUseCase = services.rootBootScriptUseCase
    val rootEbpfProbeUseCase = services.rootEbpfProbeUseCase
    val tipNotifier = services.tipNotifier
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var runModeSwitchInProgress by rememberSaveable { mutableStateOf(false) }
    var rootBootScriptSwitchInProgress by rememberSaveable { mutableStateOf(false) }
    var rootEbpfSwitchInProgress by rememberSaveable { mutableStateOf(false) }
    var showRootEbpfSelinuxPolicyWarning by rememberSaveable { mutableStateOf(false) }
    val contentPadding = pageContentPaddingWithCutout(
        innerPadding = innerPadding,
        outerPadding = outerPadding,
        isWideScreen = isWideScreen,
    )
    val listPadding = pageListPadding(contentPadding)

    val isThemeColorMode = appState.colorMode in ColorModeThemeSystem..ColorModeThemeDark
    val colorModeOptions = listOf(
        stringResource(R.string.option_follow_system),
        stringResource(R.string.option_light),
        stringResource(R.string.option_dark),
        stringResource(R.string.option_theme_system),
        stringResource(R.string.option_theme_light),
        stringResource(R.string.option_theme_dark),
    )
    val languageOptions = listOf(
        stringResource(R.string.option_follow_system),
        stringResource(R.string.option_english),
        stringResource(R.string.option_simplified_chinese),
    )
    val runModeItems = listOf(
        RunModeVpnService to stringResource(R.string.settings_run_mode_vpn_service),
        RunModeTproxy to stringResource(R.string.settings_run_mode_tproxy),
        RunModeTun to stringResource(R.string.settings_run_mode_tun),
        RunModeTun2Socks to stringResource(R.string.settings_run_mode_tun2socks),
    )
    val runModeOptions = runModeItems.map { item -> item.second }
    val selectedRunModeIndex = runModeItems
        .indexOfFirst { item -> item.first == appState.runMode }
        .takeIf { index -> index >= 0 }
        ?: 0
    val tunStackOptions = settingsTunStackOptions()
    val keyColorOptions = listOf(
        stringResource(R.string.theme_color_default),
        stringResource(R.string.theme_color_blue),
        stringResource(R.string.theme_color_green),
        stringResource(R.string.theme_color_violet),
        stringResource(R.string.theme_color_yellow),
        stringResource(R.string.theme_color_orange),
        stringResource(R.string.theme_color_rose),
        stringResource(R.string.theme_color_cyan),
    ).take(KeyColors.size + 1)
    val rootRequiredMessage = stringResource(R.string.settings_root_required)
    val rootBootScriptFailedMessage = stringResource(R.string.settings_root_boot_script_failed)
    val rootEbpfMatcherFailedMessage = stringResource(R.string.settings_root_ebpf_matcher_failed)
    val rootEbpfMatcherUnsupportedMessage = stringResource(R.string.settings_root_ebpf_matcher_unsupported)
    val rootEbpfSelinuxPolicyWarningTitle = stringResource(R.string.settings_root_ebpf_selinux_policy_warning_title)
    val rootEbpfSelinuxPolicyWarningSummary = stringResource(R.string.settings_root_ebpf_selinux_policy_warning_summary)
    val rootEbpfSelinuxPolicyWarningConfirm = stringResource(R.string.settings_root_ebpf_selinux_policy_warning_confirm)
    val serviceStoppedMessage = stringResource(R.string.proxy_service_stopped)
    val logLevelFailedMessage = stringResource(R.string.settings_log_level)
    val ignoredInterfacesErrorDetail = stringResource(R.string.settings_ignored_interfaces_error_detail)
    val localProxySettingsSummary = localProxySettingsSummary(
        runMode = appState.runMode,
        port = appState.localProxyPort,
        listenAllInterfaces = appState.localProxyListenAllInterfaces,
        transparentProxyPort = appState.transparentProxyPort,
        socks5ProxyPort = appState.socks5ProxyPort,
    )
    val externalInterfacesSummary = externalInterfacesSummary(appState.externalInterfaces)
    val ignoredInterfacesSummary = ignoredInterfacesSummary(appState.ignoredInterfaces)
    val privateAddressCidrsSummary = privateAddressCidrsSummary(appState.privateAddressCidrs)
    val overrideScriptSummary = stringResource(R.string.mihomo_override_scripts_count)
        .formatTemplate("count" to appState.mihomoOverrideScripts.size)
    val tunSettingsSummary = tunSettingsSummary(
        tunStack = tunStackOptions[appState.mihomoTunStack.coerceIn(tunStackOptions.indices)],
        mtu = appState.tunMtu,
        vpnDns = appState.tunVpnDns,
        ipv4Cidr = appState.tunIpv4Cidr,
        ipv6Cidr = appState.tunIpv6Cidr,
        showTunStack = appState.runMode != RunModeTun2Socks,
        showVpnDns = appState.runMode == RunModeVpnService,
    )
    val sheetState = rememberSettingsSheetState(updateAppState)

    Box {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.pageScrollModifiers(
                topAppBarScrollBehavior,
            ),
            contentPadding = listPadding,
        ) {
            item(key = "settings_theme") {
                SettingsThemeSection(
                    colorModeOptions = colorModeOptions,
                    colorMode = appState.colorMode,
                    keyColorOptions = keyColorOptions,
                    seedIndex = appState.seedIndex,
                    languageOptions = languageOptions,
                    languageMode = appState.languageMode,
                    isThemeColorMode = isThemeColorMode,
                    onColorModeChange = { index -> updateAppState { state -> state.copy(colorMode = index) } },
                    onSeedIndexChange = { index -> updateAppState { state -> state.copy(seedIndex = index) } },
                    onLanguageModeChange = { index -> updateAppState { state -> state.copy(languageMode = index) } },
                )
            }
            item(key = "settings_subscriptions") {
                SettingsSubscriptionsSection(
                    onOpenConfigurationManagement = { navigator.push(Route.MihomoProfileList) },
                    onOpenResourceManagement = { navigator.push(Route.ResourceManagement) },
                )
            }
            item(key = "settings_core") {
                SettingsCoreSection(
                    enableSniffer = appState.enableSniffer,
                    enableSnifferOverrideDestination = appState.enableSnifferOverrideDestination,
                    enableGeodataMode = appState.enableGeodataMode,
                    geodataLoaderOptions = MihomoGeodataLoaderValues,
                    geodataLoader = appState.mihomoGeodataLoader,
                    coreLogLevel = appState.coreLogLevel,
                    onOpenDnsSettings = { sheetState.openDnsSettings(appState) },
                    onEnableSnifferChange = { enabled ->
                        updateAppState { state -> state.copy(enableSniffer = enabled) }
                    },
                    onEnableSnifferOverrideDestinationChange = { enabled ->
                        updateAppState { state -> state.copy(enableSnifferOverrideDestination = enabled) }
                    },
                    onEnableGeodataModeChange = { enabled ->
                        updateAppState { state -> state.copy(enableGeodataMode = enabled) }
                    },
                    onGeodataLoaderChange = { index ->
                        updateAppState { state -> state.copy(mihomoGeodataLoader = index) }
                    },
                    onCoreLogLevelChange = { index ->
                        if (index != appState.coreLogLevel) {
                            val nextState = appState.copy(coreLogLevel = index)
                            updateAppState { state -> state.copy(coreLogLevel = index) }
                            scope.launch {
                                services.mihomoRuntime.patchLogLevel(nextState)
                                    .onFailure { error -> tipNotifier.showError(error, logLevelFailedMessage) }
                            }
                        }
                    },
                )
            }
            item(key = "settings_run_mode") {
                SettingsAdvancedSection(
                    enableIpv6 = appState.enableIpv6,
                    enableIpv6Prefer = appState.enableIpv6Prefer,
                    runModeOptions = runModeOptions,
                    selectedRunModeIndex = selectedRunModeIndex,
                    overrideScriptSummary = overrideScriptSummary,
                    onOpenOverrideScripts = {
                        navigator.push(Route.MihomoOverrideScripts)
                    },
                    onEnableIpv6Change = { enabled ->
                        updateAppState { state -> state.copy(enableIpv6 = enabled) }
                    },
                    onEnableIpv6PreferChange = { enabled ->
                        updateAppState { state -> state.copy(enableIpv6Prefer = enabled) }
                    },
                    onRunModeChange = { index ->
                        val targetRunMode = runModeItems.getOrNull(index)?.first ?: RunModeVpnService
                        if (targetRunMode != appState.runMode && !runModeSwitchInProgress) {
                            runModeSwitchInProgress = true
                            val stateSnapshot = appState
                            val switchJob = services.appScope.launch {
                                when (val result = switchRunModeUseCase.switchRunMode(stateSnapshot, targetRunMode)) {
                                    is SwitchRunModeResult.Success -> {
                                        updateAppState { state ->
                                            state.copy(
                                                runMode = result.runMode,
                                                proxyRunning = result.proxyRunning,
                                                enableRootBootScript = false,
                                                enableRootEbpfRules = state.enableRootEbpfRules && result.runMode.isRootRunMode(),
                                            )
                                        }
                                    }

                                    is SwitchRunModeResult.RootUnavailable -> {
                                        updateAppState { state -> state.copy(proxyRunning = result.proxyRunning) }
                                        tipNotifier.show(rootRequiredMessage)
                                    }

                                    is SwitchRunModeResult.StopFailed -> {
                                        tipNotifier.showError(result.error, serviceStoppedMessage)
                                    }
                                }
                            }
                            scope.launch {
                                try {
                                    switchJob.join()
                                } finally {
                                    runModeSwitchInProgress = false
                                }
                            }
                        }
                    },
                )
            }
            item(key = "settings_proxy") {
                SettingsProxyModeSections(
                    runMode = appState.runMode,
                    localProxySettingsSummary = localProxySettingsSummary,
                    enableVpnAppendHttpProxy = appState.enableVpnAppendHttpProxy,
                    enableVpnHevTun = appState.enableVpnHevTun,
                    tunSettingsSummary = tunSettingsSummary,
                    enableRootBootScript = appState.enableRootBootScript,
                    enableRootEbpfRules = appState.enableRootEbpfRules,
                    enableRootEbpfDirectCidrBypass = appState.enableRootEbpfDirectCidrBypass,
                    enableIpv6 = appState.enableIpv6,
                    enableRootIpv6Disabler = appState.enableRootIpv6Disabler,
                    externalInterfacesSummary = externalInterfacesSummary,
                    ignoredInterfacesSummary = ignoredInterfacesSummary,
                    privateAddressCidrsSummary = privateAddressCidrsSummary,
                    onOpenLocalProxySettings = { sheetState.openLocalProxySettings(appState) },
                    onEnableVpnAppendHttpProxyChange = { enabled ->
                        updateAppState { state -> state.copy(enableVpnAppendHttpProxy = enabled) }
                    },
                    onEnableVpnHevTunChange = { enabled ->
                        updateAppState { state -> state.copy(enableVpnHevTun = enabled) }
                    },
                    onOpenTunSettings = { sheetState.openTunSettings(appState) },
                    onEnableRootBootScriptChange = { enabled ->
                        if (!rootBootScriptSwitchInProgress) {
                            rootBootScriptSwitchInProgress = true
                            val stateSnapshot = appState
                            val bootScriptState = if (enabled) {
                                stateSnapshot.withResolvedDynamicLocalProxyPort()
                            } else {
                                stateSnapshot
                            }
                            val bootScriptJob = services.appScope.launch {
                                when (val result = rootBootScriptUseCase.setEnabled(bootScriptState, enabled)) {
                                    RootBootScriptResult.Success -> {
                                        updateAppState { state ->
                                            state.copy(
                                                enableRootBootScript = enabled,
                                                localProxyPort = bootScriptState.localProxyPort,
                                            )
                                        }
                                    }

                                    RootBootScriptResult.RootUnavailable -> {
                                        tipNotifier.show(rootRequiredMessage)
                                    }

                                    is RootBootScriptResult.Failed -> {
                                        tipNotifier.showError(result.error, rootBootScriptFailedMessage)
                                    }
                                }
                            }
                            scope.launch {
                                try {
                                    bootScriptJob.join()
                                } finally {
                                    rootBootScriptSwitchInProgress = false
                                }
                            }
                        }
                    },
                    onEnableRootEbpfRulesChange = { enabled ->
                        if (!enabled) {
                            updateAppState { state -> state.copy(enableRootEbpfRules = false) }
                            return@SettingsProxyModeSections
                        }
                        if (!rootEbpfSwitchInProgress) {
                            rootEbpfSwitchInProgress = true
                            val stateSnapshot = appState
                            val probeJob = services.appScope.launch {
                                when (val result = rootEbpfProbeUseCase.probe(stateSnapshot)) {
                                    is RootEbpfProbeResult.Success -> {
                                        if (result.selinuxPolicyApplicator == null) {
                                            showRootEbpfSelinuxPolicyWarning = true
                                        } else {
                                            updateAppState { state -> state.copy(enableRootEbpfRules = true) }
                                        }
                                    }

                                    is RootEbpfProbeResult.Unsupported -> {
                                        tipNotifier.show(
                                            result.probe.message.ifBlank { rootEbpfMatcherUnsupportedMessage },
                                        )
                                    }

                                    RootEbpfProbeResult.RootUnavailable -> {
                                        tipNotifier.show(rootRequiredMessage)
                                    }

                                    is RootEbpfProbeResult.Failed -> {
                                        tipNotifier.showError(result.error, rootEbpfMatcherFailedMessage)
                                    }
                                }
                            }
                            scope.launch {
                                try {
                                    probeJob.join()
                                } finally {
                                    rootEbpfSwitchInProgress = false
                                }
                            }
                        }
                    },
                    onEnableRootEbpfDirectCidrBypassChange = { enabled ->
                        updateAppState { state -> state.copy(enableRootEbpfDirectCidrBypass = enabled) }
                    },
                    onEnableRootIpv6DisablerChange = { enabled ->
                        updateAppState { state -> state.copy(enableRootIpv6Disabler = enabled) }
                    },
                    onOpenExternalInterfaces = { sheetState.openExternalInterfaces(appState) },
                    onOpenIgnoredInterfaces = {
                        sheetState.openIgnoredInterfaces(appState)
                        scope.launch {
                            sheetState.loadIgnoredInterfaces(
                                appState = appState,
                                networkInterfaces = networkInterfaces,
                                errorDetail = ignoredInterfacesErrorDetail,
                            )
                        }
                    },
                    onOpenPrivateAddresses = { sheetState.openPrivateAddresses(appState) },
                )
            }
            item(key = "settings_logs") {
                SettingsLogsSection(
                    onOpenCoreLogs = { navigator.push(Route.CoreLogs) },
                    onOpenLogcatLogs = { navigator.push(Route.LogcatLogs) },
                )
            }
            item(key = "settings_about") {
                SettingsAboutSection(
                    onOpenAbout = { navigator.push(Route.About) },
                    onOpenLicenses = { navigator.push(Route.License) },
                )
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = contentPadding,
        )
        SettingsBottomSheetsHost(
            appState = appState,
            sheetState = sheetState,
            tunStackOptions = tunStackOptions,
            updateAppState = updateAppState,
        )
        WarningConfirmDialog(
            show = showRootEbpfSelinuxPolicyWarning,
            title = rootEbpfSelinuxPolicyWarningTitle,
            summary = rootEbpfSelinuxPolicyWarningSummary,
            dismissText = stringResource(R.string.common_cancel),
            confirmText = rootEbpfSelinuxPolicyWarningConfirm,
            onDismissRequest = { showRootEbpfSelinuxPolicyWarning = false },
            onConfirm = {
                updateAppState { state -> state.copy(enableRootEbpfRules = true) }
                showRootEbpfSelinuxPolicyWarning = false
            },
        )
    }
}
