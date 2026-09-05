// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.ProjectInfo
import app.R
import app.collectAppState
import app.modes.RunModeBpf2Socks
import app.modes.RunModeTproxy
import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import app.modes.RunModeVpnService
import app.modes.isRootRunMode
import app.navigation.Route
import app.withMihomoRestartApplied
import engine.mihomo.MihomoGeodataLoaderValues
import engine.mihomo.raw.MihomoRawConfigParser
import engine.proxy.withResolvedDynamicLocalProxyPort
import features.settings.sheets.externalInterfacesSummary
import features.settings.sheets.ignoredInterfacesSummary
import features.settings.sheets.privateAddressCidrsSummary
import features.settings.sheets.snifferSettingsSummary
import features.settings.sheets.tunSharedNetworkInterfacesSummary
import features.settings.sheets.tunSettingsSummary
import features.settings.usecase.RootBootScriptResult
import features.settings.usecase.RootEbpfProbeResult
import features.settings.usecase.SwitchRunModeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.KeyColors
import ui.components.AsteriskContentHeader
import ui.components.AsteriskPinnedSearchArea
import ui.components.WarningConfirmDialog
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageHorizontalPadding
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import ui.text.formatTemplate

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsPage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val topAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_title),
                    subtitle = "v${ProjectInfo.VERSION_NAME} (${ProjectInfo.VERSION_CODE})",
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                )
                AsteriskPinnedSearchArea(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.common_search),
                    clearContentDescription = stringResource(R.string.common_clear),
                )
            }
        },
    ) { innerPadding ->
        SettingsContent(
            innerPadding = innerPadding,
            outerPadding = padding,
            topAppBarScrollBehavior = topAppBarScrollBehavior,
            searchQuery = searchQuery,
        )
    }
}

@Composable
private fun SettingsContent(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues,
    topAppBarScrollBehavior: TopAppBarScrollBehavior,
    searchQuery: String,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val isWideScreen = LocalIsWideScreen.current
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val services = LocalAppServices.current
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

    val colorModeOptions = listOf(
        stringResource(R.string.option_follow_system),
        stringResource(R.string.option_light),
        stringResource(R.string.option_dark),
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
        RunModeBpf2Socks to stringResource(R.string.settings_run_mode_bpf2socks),
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
    val localProxySettingsSummary = localProxySettingsSummary(
        runMode = appState.runMode,
        port = appState.localProxyPort,
        listenAllInterfaces = appState.localProxyListenAllInterfaces,
        transparentProxyPort = appState.transparentProxyPort,
        bpf2SocksBridgePort = appState.bpf2SocksBridgePort,
        socks5ProxyPort = appState.socks5ProxyPort,
    )
    val externalInterfacesSummary = if (appState.runMode == RunModeTun) {
        tunSharedNetworkInterfacesSummary(appState.tunSharedNetworkInterfaces)
    } else externalInterfacesSummary(appState.externalInterfaces)
    val ignoredInterfacesSummary = ignoredInterfacesSummary(appState.ignoredInterfaces)
    val privateAddressCidrsSummary = privateAddressCidrsSummary(appState.privateAddressCidrs)
    val snifferSummary = snifferSettingsSummary(
        enableSniffer = appState.enableSniffer,
        snifferHttpPorts = appState.snifferHttpPorts,
        snifferTlsPorts = appState.snifferTlsPorts,
        snifferQuicPorts = appState.snifferQuicPorts,
    )
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
    val selectedProfile = appState.mihomoProfiles.firstOrNull { it.id == appState.selectedMihomoProfileId }
        ?: appState.mihomoProfiles.firstOrNull()
    val rawConfigState by produceState(
        initialValue = SettingsRawConfigState(enabled = selectedProfile?.disableOverrides == true),
        key1 = selectedProfile?.id,
        key2 = selectedProfile?.contentSha256,
        key3 = selectedProfile?.disableOverrides,
    ) {
        value = if (selectedProfile?.disableOverrides == true && selectedProfile.hasContent) {
            withContext(Dispatchers.IO) {
                val parsed = runCatching {
                    MihomoRawConfigParser.parse(services.mihomoProfileContentStore.readBytes(selectedProfile))
                }.getOrElse { error ->
                    engine.mihomo.raw.MihomoRawConfigParseResult(
                        sourceBytes = byteArrayOf(),
                        error = error.message ?: "Unable to read configuration",
                    )
                }
                SettingsRawConfigState(
                    enabled = true,
                    snapshot = parsed.snapshot,
                    parseError = parsed.error,
                )
            }
        } else {
            SettingsRawConfigState(enabled = selectedProfile?.disableOverrides == true)
        }
    }
    val nestedSearchEntries = settingsNestedSearchEntries(
        runMode = appState.runMode,
        onOpenTunBypassProviders = { sheetState.openTunBypassProviders(appState) },
        onOpenDns = { sheetState.openDnsSettings(appState) },
        onOpenSniffer = { sheetState.openSnifferSettings(appState) },
        onOpenLocalProxy = { sheetState.openLocalProxySettings(appState) },
        onOpenTun = { sheetState.openTunSettings(appState) },
        onOpenExternalInterfaces = {
            if (appState.runMode == RunModeTun) sheetState.openTunSharedNetwork(appState)
            else sheetState.openExternalInterfaces(appState)
        },
        onOpenServiceControl = { sheetState.openServiceControl(appState) },
        onOpenIgnoredInterfaces = { sheetState.openIgnoredInterfaces(appState) },
        onOpenPrivateAddresses = { sheetState.openPrivateAddresses(appState) },
    )
    val topLevelSearchItems = settingsTopLevelSearchItems(
        runMode = appState.runMode,
        colorModeOptions = colorModeOptions,
        colorMode = appState.colorMode,
        keyColorOptions = keyColorOptions,
        seedIndex = appState.seedIndex,
        languageOptions = languageOptions,
        languageMode = appState.languageMode,
        geodataLoaderOptions = MihomoGeodataLoaderValues,
        geodataLoader = appState.mihomoGeodataLoader,
        coreLogLevel = appState.coreLogLevel,
        runModeOptions = runModeOptions,
        selectedRunModeIndex = selectedRunModeIndex,
        snifferSummary = snifferSummary,
        localProxySummary = localProxySettingsSummary,
        overrideScriptSummary = overrideScriptSummary,
        tunSummary = tunSettingsSummary,
        externalInterfacesSummary = externalInterfacesSummary,
        ignoredInterfacesSummary = ignoredInterfacesSummary,
        privateAddressesSummary = privateAddressCidrsSummary,
    )
    val searchMatchCount = if (searchQuery.isBlank()) {
        0
    } else {
        filterSettingsItems(topLevelSearchItems, searchQuery).size +
            if (rawConfigState.enabled) 0 else filterSettingsSearchEntries(nestedSearchEntries, searchQuery).size
    }
    val searchFocusState = reduceSettingsSearchFocusState(searchQuery, searchMatchCount)

    SettingsRawConfigProvider(rawConfigState) {
    SettingsSearchProvider(searchQuery) {
    Box {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.pageScrollModifiers(
                topAppBarScrollBehavior,
            ),
            contentPadding = listPadding,
        ) {
            if (searchQuery.isNotBlank()) {
                item(key = "settings_search_status") {
                    SettingsSearchStatus(
                        state = searchFocusState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pageHorizontalPadding()
                            .padding(vertical = 8.dp),
                    )
                }
            }
            if (searchQuery.isNotBlank() && !rawConfigState.enabled) {
                item(key = "settings_nested_search_results") {
                    SettingsNestedSearchResults(
                        query = searchQuery,
                        entries = nestedSearchEntries,
                    )
                }
            }
            item(key = "settings_theme") {
                SettingsThemeSection(
                    colorModeOptions = colorModeOptions,
                    colorMode = appState.colorMode,
                    keyColorOptions = keyColorOptions,
                    seedIndex = appState.seedIndex,
                    languageOptions = languageOptions,
                    languageMode = appState.languageMode,
                    onColorModeChange = { index -> updateAppState { state -> state.copy(colorMode = index) } },
                    onSeedIndexChange = { index -> updateAppState { state -> state.copy(seedIndex = index) } },
                    onLanguageModeChange = { index -> updateAppState { state -> state.copy(languageMode = index) } },
                )
            }
            item(key = "settings_subscriptions") {
                SettingsSubscriptionsSection(
                    onOpenProxyAppList = { navigator.push(Route.ProxyAppList) },
                    onOpenResourceManagement = { navigator.push(Route.ResourceManagement) },
                )
            }
            item(key = "settings_core") {
                SettingsCoreSection(
                    snifferSettingsSummary = snifferSummary,
                    enableGeodataMode = appState.enableGeodataMode,
                    geodataLoaderOptions = MihomoGeodataLoaderValues,
                    geodataLoader = appState.mihomoGeodataLoader,
                    coreLogLevel = appState.coreLogLevel,
                    enableLocalDns = appState.enableLocalDns,
                    onOpenDnsSettings = { sheetState.openDnsSettings(appState) },
                    onOpenSnifferSettings = { sheetState.openSnifferSettings(appState) },
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
                    onEnableLocalDnsChange = { enabled ->
                        updateAppState { state -> state.copy(enableLocalDns = enabled) }
                    },
                )
            }
            item(key = "settings_run_mode") {
                SettingsAdvancedSection(
                    enableBroadcastControl = appState.enableBroadcastControl,
                    enableIpv6 = appState.enableIpv6,
                    enableIpv6Prefer = appState.enableIpv6Prefer,
                    runModeOptions = runModeOptions,
                    selectedRunModeIndex = selectedRunModeIndex,
                    overrideScriptSummary = overrideScriptSummary,
                    onOpenOverrideScripts = {
                        navigator.push(Route.MihomoOverrideScripts)
                    },
                    onEnableBroadcastControlChange = { enabled ->
                        updateAppState { state -> state.copy(enableBroadcastControl = enabled) }
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
                                            ).withMihomoRestartApplied()
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
                    enableTrafficStatsNotification = appState.enableTrafficStatsNotification,
                    enableVpnAppendHttpProxy = appState.enableVpnAppendHttpProxy,
                    enableVpnHevTun = appState.enableVpnHevTun,
                    tunSettingsSummary = tunSettingsSummary,
                    enableRootBootScript = appState.enableRootBootScript,
                    enableRootEbpfRules = appState.enableRootEbpfRules,
                    enableRootEbpfDirectCidrBypass = appState.enableRootEbpfDirectCidrBypass,
                    tunBypassProvidersSummary = appState.tunBypassRuleProviderNames.joinToString().ifEmpty { stringResource(R.string.settings_tun_bypass_providers_none) },
                    onOpenTunBypassProviders = { sheetState.openTunBypassProviders(appState) },
                    enableIpv6 = appState.enableIpv6,
                    enableRootIpv6Disabler = appState.enableRootIpv6Disabler,
                    externalInterfacesSummary = externalInterfacesSummary,
                    ignoredInterfacesSummary = ignoredInterfacesSummary,
                    privateAddressCidrsSummary = privateAddressCidrsSummary,
                    onOpenLocalProxySettings = { sheetState.openLocalProxySettings(appState) },
                    onEnableTrafficStatsNotificationChange = { enabled ->
                        updateAppState { state -> state.copy(enableTrafficStatsNotification = enabled) }
                    },
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
                    onOpenExternalInterfaces = {
                        if (appState.runMode == RunModeTun) sheetState.openTunSharedNetwork(appState)
                        else sheetState.openExternalInterfaces(appState)
                    },
                    onOpenServiceControl = { sheetState.openServiceControl(appState) },
                    onOpenIgnoredInterfaces = { sheetState.openIgnoredInterfaces(appState) },
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
    }
}

@Composable
private fun SettingsSearchStatus(
    state: SettingsSearchFocusState,
    modifier: Modifier = Modifier,
) {
    val summary = when (state.status) {
        SettingsSearchFocusStatus.Idle -> null
        SettingsSearchFocusStatus.Matches -> pluralStringResource(
            R.plurals.settings_search_match_count,
            state.matchCount,
            state.matchCount,
        )
        SettingsSearchFocusStatus.NoResults -> stringResource(R.string.settings_search_no_results)
    }
    AsteriskContentHeader(
        status = summary,
        modifier = modifier,
    ) {}
}
