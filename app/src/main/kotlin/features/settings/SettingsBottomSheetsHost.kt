// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.AppState
import app.LocalAppServices
import app.R
import features.settings.sheets.DnsSettingsBottomSheet
import features.settings.sheets.ExternalInterfacesBottomSheet
import features.settings.sheets.IgnoredInterfacesBottomSheet
import features.settings.sheets.LocalProxySettingsBottomSheet
import features.settings.sheets.PrivateAddressBottomSheet
import features.settings.sheets.SnifferSettingsBottomSheet
import features.settings.sheets.ServiceControlBottomSheet
import features.settings.sheets.TunBypassProvidersBottomSheet
import features.settings.sheets.TunSharedNetworkBottomSheet
import features.settings.sheets.TunSettingsBottomSheet
import features.settings.sheets.sanitizeExternalInterfaces
import features.settings.sheets.sanitizeIgnoredInterfaceSelectors
import features.settings.sheets.sanitizePrivateAddressCidrs
import app.modes.RunModeBpf2Socks
import app.modes.RunModeTun2Socks
import app.modes.RunModeTproxy
import app.modes.RunModeVpnService
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SettingsBottomSheetsHost(
    appState: AppState,
    sheetState: SettingsSheetState,
    tunStackOptions: List<String>,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val applyServiceControl = LocalAppServices.current.applyServiceControlUseCase
    val serviceControlFailedMessage = stringResource(R.string.settings_service_control_save_failed)
    var serviceControlSaving by remember { mutableStateOf(false) }
    var serviceControlError by remember { mutableStateOf<String?>(null) }
    LocalProxySettingsBottomSheet(
        show = sheetState.showLocalProxySettings,
        showInboundProxyPort = appState.runMode == RunModeTproxy ||
            appState.runMode == RunModeTun2Socks ||
            appState.runMode == RunModeBpf2Socks,
        useTun2SocksProxyPort = appState.runMode == RunModeTun2Socks,
        useBpf2SocksProxyPort = appState.runMode == RunModeBpf2Socks,
        lockInboundProxyPort = (appState.runMode == RunModeTproxy ||
            appState.runMode == RunModeTun2Socks ||
            appState.runMode == RunModeBpf2Socks) &&
            appState.proxyRunning,
        inboundProxyPort = if (appState.runMode == RunModeTun2Socks) {
            sheetState.localProxySettingsDraft.socks5ProxyPort
        } else if (appState.runMode == RunModeBpf2Socks) {
            sheetState.localProxySettingsDraft.socks5ProxyPort
        } else {
            sheetState.localProxySettingsDraft.transparentProxyPort
        },
        bpf2SocksBridgePort = sheetState.localProxySettingsDraft.bpf2SocksBridgePort,
        port = sheetState.localProxySettingsDraft.port,
        enableDynamicPort = sheetState.localProxySettingsDraft.enableDynamicPort,
        listenAllInterfaces = sheetState.localProxySettingsDraft.listenAllInterfaces,
        username = sheetState.localProxySettingsDraft.username,
        password = sheetState.localProxySettingsDraft.password,
        onInboundProxyPortChange = {
            sheetState.localProxySettingsDraft = if (appState.runMode == RunModeTun2Socks) {
                sheetState.localProxySettingsDraft.copy(socks5ProxyPort = it)
            } else if (appState.runMode == RunModeBpf2Socks) {
                sheetState.localProxySettingsDraft.copy(socks5ProxyPort = it)
            } else {
                sheetState.localProxySettingsDraft.copy(transparentProxyPort = it)
            }
        },
        onBpf2SocksBridgePortChange = {
            sheetState.localProxySettingsDraft = sheetState.localProxySettingsDraft.copy(bpf2SocksBridgePort = it)
        },
        onPortChange = {
            sheetState.localProxySettingsDraft = sheetState.localProxySettingsDraft.copy(
                port = it,
            )
        },
        onEnableDynamicPortChange = {
            sheetState.localProxySettingsDraft = sheetState.localProxySettingsDraft.copy(enableDynamicPort = it)
        },
        onListenAllInterfacesChange = {
            sheetState.localProxySettingsDraft = sheetState.localProxySettingsDraft.copy(listenAllInterfaces = it)
        },
        onUsernameChange = {
            sheetState.localProxySettingsDraft = sheetState.localProxySettingsDraft.copy(username = it)
        },
        onPasswordChange = {
            sheetState.localProxySettingsDraft = sheetState.localProxySettingsDraft.copy(password = it)
        },
        onDismissRequest = { sheetState.showLocalProxySettings = false },
        onSave = { inboundProxyPort, bpf2SocksBridgePort, port, enableDynamicPort, listenAllInterfaces, username, password ->
            updateAppState { state ->
                val lockInboundProxyPort = (state.runMode == RunModeTproxy ||
                    state.runMode == RunModeTun2Socks ||
                    state.runMode == RunModeBpf2Socks) &&
                    state.proxyRunning
                state.copy(
                    transparentProxyPort = when {
                        lockInboundProxyPort -> state.transparentProxyPort
                        state.runMode == RunModeTproxy -> inboundProxyPort
                        else -> state.transparentProxyPort
                    },
                    socks5ProxyPort = when {
                        lockInboundProxyPort -> state.socks5ProxyPort
                        state.runMode == RunModeTun2Socks || state.runMode == RunModeBpf2Socks -> inboundProxyPort
                        else -> state.socks5ProxyPort
                    },
                    bpf2SocksBridgePort = when {
                        lockInboundProxyPort -> state.bpf2SocksBridgePort
                        state.runMode == RunModeBpf2Socks -> bpf2SocksBridgePort
                        else -> state.bpf2SocksBridgePort
                    },
                    localProxyPort = port,
                    enableDynamicLocalProxyPort = enableDynamicPort,
                    localProxyListenAllInterfaces = listenAllInterfaces,
                    localProxyUsername = username,
                    localProxyPassword = password,
                )
            }
            sheetState.showLocalProxySettings = false
        },
    )
    TunSettingsBottomSheet(
        show = sheetState.showTunSettings,
        tunStackOptions = tunStackOptions,
        tunStack = sheetState.tunSettingsDraft.tunStack,
        mtu = sheetState.tunSettingsDraft.mtu,
        vpnDns = sheetState.tunSettingsDraft.vpnDns,
        ipv4Cidr = sheetState.tunSettingsDraft.ipv4Cidr,
        ipv6Cidr = sheetState.tunSettingsDraft.ipv6Cidr,
        showTunStack = appState.runMode != RunModeTun2Socks,
        showVpnDns = appState.runMode == RunModeVpnService,
        onTunStackChange = { sheetState.tunSettingsDraft = sheetState.tunSettingsDraft.copy(tunStack = it) },
        onMtuChange = {
            sheetState.tunSettingsDraft = sheetState.tunSettingsDraft.copy(mtu = it)
        },
        onVpnDnsChange = { sheetState.tunSettingsDraft = sheetState.tunSettingsDraft.copy(vpnDns = it) },
        onIpv4CidrChange = { sheetState.tunSettingsDraft = sheetState.tunSettingsDraft.copy(ipv4Cidr = it) },
        onIpv6CidrChange = { sheetState.tunSettingsDraft = sheetState.tunSettingsDraft.copy(ipv6Cidr = it) },
        onDismissRequest = { sheetState.showTunSettings = false },
        onSave = { tunStack, mtu, vpnDns, ipv4Cidr, ipv6Cidr ->
            updateAppState { state ->
                state.copy(
                    mihomoTunStack = if (state.runMode == RunModeTun2Socks) state.mihomoTunStack else tunStack,
                    tunMtu = mtu,
                    tunVpnDns = if (state.runMode == RunModeVpnService) vpnDns else state.tunVpnDns,
                    tunIpv4Cidr = ipv4Cidr,
                    tunIpv6Cidr = ipv6Cidr,
                )
            }
            sheetState.showTunSettings = false
        },
    )
    DnsSettingsBottomSheet(
        show = sheetState.showDnsSettings,
        draft = sheetState.dnsSettingsDraft,
        onDraftChange = { sheetState.dnsSettingsDraft = it },
        onDismissRequest = { sheetState.showDnsSettings = false },
        onSave = { draft ->
            updateAppState { state ->
                state.copy(
                    enableLocalDns = draft.enableLocalDns,
                    overrideDns = draft.overrideDns,
                    dnsPreferH3 = draft.dnsPreferH3,
                    dnsUseHosts = draft.dnsUseHosts,
                    dnsUseSystemHosts = draft.dnsUseSystemHosts,
                    dnsRespectRules = draft.dnsRespectRules,
                    dnsEnhancedMode = draft.dnsEnhancedMode,
                    dnsFakeIpRange = draft.dnsFakeIpRange,
                    dnsFakeIpFilter = draft.dnsFakeIpFilter,
                    dnsDefaultNameserver = draft.dnsDefaultNameserver,
                    dnsNameserver = draft.dnsNameserver,
                    dnsNameserverPolicy = draft.dnsNameserverPolicy,
                    dnsProxyServerNameserver = draft.dnsProxyServerNameserver,
                    dnsFallback = draft.dnsFallback,
                    dnsFallbackFilterGeoip = draft.dnsFallbackFilterGeoip,
                    dnsFallbackFilterGeoipCode = draft.dnsFallbackFilterGeoipCode,
                    dnsFallbackFilterGeosite = draft.dnsFallbackFilterGeosite,
                    dnsFallbackFilterIpcidr = draft.dnsFallbackFilterIpcidr,
                    dnsFallbackFilterDomain = draft.dnsFallbackFilterDomain,
                    dnsHosts = draft.dnsHosts,
                )
            }
            sheetState.showDnsSettings = false
        },
    )
    SnifferSettingsBottomSheet(
        show = sheetState.showSnifferSettings,
        draft = sheetState.snifferSettingsDraft,
        onDraftChange = { sheetState.snifferSettingsDraft = it },
        onDismissRequest = { sheetState.showSnifferSettings = false },
        onSave = { draft ->
            updateAppState { state ->
                state.copy(
                    enableSniffer = draft.enableSniffer,
                    enableSnifferOverrideDestination = draft.enableSnifferOverrideDestination,
                    snifferForceDnsMapping = draft.snifferForceDnsMapping,
                    snifferParsePureIp = draft.snifferParsePureIp,
                    snifferHttpPorts = draft.snifferHttpPorts,
                    snifferTlsPorts = draft.snifferTlsPorts,
                    snifferQuicPorts = draft.snifferQuicPorts,
                    snifferHttpOverrideDestinationMode = draft.snifferHttpOverrideDestinationMode,
                    snifferTlsOverrideDestinationMode = draft.snifferTlsOverrideDestinationMode,
                    snifferQuicOverrideDestinationMode = draft.snifferQuicOverrideDestinationMode,
                    snifferForceDomain = draft.snifferForceDomain,
                    snifferSkipDomain = draft.snifferSkipDomain,
                    snifferSkipSrcAddress = draft.snifferSkipSrcAddress,
                    snifferSkipDstAddress = draft.snifferSkipDstAddress,
                )
            }
            sheetState.showSnifferSettings = false
        },
    )
    TunBypassProvidersBottomSheet(
        show = sheetState.showTunBypassProviders,
        appState = appState,
        selectedNames = sheetState.tunBypassProviderNamesDraft,
        onSelectedNamesChange = { sheetState.tunBypassProviderNamesDraft = it },
        onDismissRequest = { sheetState.showTunBypassProviders = false },
        onSave = { names ->
            updateAppState { it.copy(tunBypassRuleProviderNames = names) }
            sheetState.showTunBypassProviders = false
        },
    )
    TunSharedNetworkBottomSheet(
        show = sheetState.showTunSharedNetwork,
        interfaces = sheetState.tunSharedNetworkInterfacesDraft,
        onInterfacesChange = { sheetState.tunSharedNetworkInterfacesDraft = it },
        onDismissRequest = { sheetState.showTunSharedNetwork = false },
        onSave = { interfaces ->
            updateAppState { it.copy(tunSharedNetworkInterfaces = interfaces) }
            sheetState.showTunSharedNetwork = false
        },
    )
    ExternalInterfacesBottomSheet(
        show = sheetState.showExternalInterfaces,
        selectedInterfaces = sheetState.externalInterfacesDraft,
        onSelectedInterfacesChange = { sheetState.externalInterfacesDraft = it.sanitizeExternalInterfaces() },
        onDismissRequest = { sheetState.showExternalInterfaces = false },
        onSave = { interfaces ->
            updateAppState { state -> state.copy(externalInterfaces = interfaces.sanitizeExternalInterfaces()) }
            sheetState.showExternalInterfaces = false
        },
    )
    ServiceControlBottomSheet(
        show = sheetState.showServiceControl,
        saving = serviceControlSaving,
        draft = sheetState.serviceControlDraft,
        runtimeError = serviceControlError,
        onDraftChange = {
            serviceControlError = null
            sheetState.serviceControlDraft = it
        },
        onDismissRequest = {
            if (!serviceControlSaving) sheetState.showServiceControl = false
        },
        onSave = { draft ->
            if (!serviceControlSaving) {
                val baseState = appState
                serviceControlSaving = true
                serviceControlError = null
                scope.launch {
                    try {
                        val applied = applyServiceControl.apply(baseState, draft)
                        updateAppState { current ->
                            current.copy(
                                serviceControl = applied.serviceControl,
                                proxyRunning = applied.proxyRunning,
                                localProxyPort = applied.localProxyPort,
                            )
                        }
                        sheetState.showServiceControl = false
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        AndroidAppLogger.error("ServiceControl", "Failed to restart asteriskd", error)
                        serviceControlError = error.message?.takeIf(String::isNotBlank)
                            ?: serviceControlFailedMessage
                    } finally {
                        serviceControlSaving = false
                    }
                }
            }
        },
    )
    IgnoredInterfacesBottomSheet(
        show = sheetState.showIgnoredInterfaces,
        selectedInterfaces = sheetState.ignoredInterfacesDraft,
        onSelectedInterfacesChange = {
            sheetState.ignoredInterfacesDraft = it.sanitizeIgnoredInterfaceSelectors()
        },
        onDismissRequest = { sheetState.closeIgnoredInterfaces() },
        onSave = { interfaces ->
            updateAppState { state ->
                state.copy(ignoredInterfaces = interfaces.sanitizeIgnoredInterfaceSelectors())
            }
            sheetState.closeIgnoredInterfaces()
        },
    )
    PrivateAddressBottomSheet(
        show = sheetState.showPrivateAddresses,
        selectedCidrs = sheetState.privateAddressCidrsDraft,
        onSelectedCidrsChange = { sheetState.privateAddressCidrsDraft = it.sanitizePrivateAddressCidrs() },
        onDismissRequest = { sheetState.showPrivateAddresses = false },
        onSave = { cidrs ->
            updateAppState { state -> state.copy(privateAddressCidrs = cidrs.sanitizePrivateAddressCidrs()) }
            sheetState.showPrivateAddresses = false
        },
    )
}
