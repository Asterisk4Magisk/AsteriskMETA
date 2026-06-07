// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import app.AppState
import features.settings.sheets.DnsSettingsBottomSheet
import features.settings.sheets.ExternalInterfacesBottomSheet
import features.settings.sheets.IgnoredInterfacesBottomSheet
import features.settings.sheets.LocalProxySettingsBottomSheet
import features.settings.sheets.PrivateAddressBottomSheet
import features.settings.sheets.TunSettingsBottomSheet
import features.settings.sheets.orderedBy
import features.settings.sheets.sanitizeExternalInterfaces
import features.settings.sheets.sanitizePrivateAddressCidrs
import app.modes.RunModeTun2Socks
import app.modes.RunModeTproxy
import app.modes.RunModeVpnService

@Composable
internal fun SettingsBottomSheetsHost(
    appState: AppState,
    sheetState: SettingsSheetState,
    tunStackOptions: List<String>,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    LocalProxySettingsBottomSheet(
        show = sheetState.showLocalProxySettings,
        showInboundProxyPort = appState.runMode == RunModeTproxy || appState.runMode == RunModeTun2Socks,
        useTun2SocksProxyPort = appState.runMode == RunModeTun2Socks,
        lockInboundProxyPort = (appState.runMode == RunModeTproxy || appState.runMode == RunModeTun2Socks) &&
            appState.proxyRunning,
        inboundProxyPort = if (appState.runMode == RunModeTun2Socks) {
            sheetState.localProxySettingsDraft.socks5ProxyPort
        } else {
            sheetState.localProxySettingsDraft.transparentProxyPort
        },
        port = sheetState.localProxySettingsDraft.port,
        enableDynamicPort = sheetState.localProxySettingsDraft.enableDynamicPort,
        listenAllInterfaces = sheetState.localProxySettingsDraft.listenAllInterfaces,
        username = sheetState.localProxySettingsDraft.username,
        password = sheetState.localProxySettingsDraft.password,
        onInboundProxyPortChange = {
            sheetState.localProxySettingsDraft = if (appState.runMode == RunModeTun2Socks) {
                sheetState.localProxySettingsDraft.copy(socks5ProxyPort = it)
            } else {
                sheetState.localProxySettingsDraft.copy(transparentProxyPort = it)
            }
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
        onSave = { inboundProxyPort, port, enableDynamicPort, listenAllInterfaces, username, password ->
            updateAppState { state ->
                val lockInboundProxyPort = (state.runMode == RunModeTproxy || state.runMode == RunModeTun2Socks) &&
                    state.proxyRunning
                state.copy(
                    transparentProxyPort = when {
                        lockInboundProxyPort -> state.transparentProxyPort
                        state.runMode == RunModeTproxy -> inboundProxyPort
                        else -> state.transparentProxyPort
                    },
                    socks5ProxyPort = when {
                        lockInboundProxyPort -> state.socks5ProxyPort
                        state.runMode == RunModeTun2Socks -> inboundProxyPort
                        else -> state.socks5ProxyPort
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
        forceEnableLocalDns = appState.runMode == RunModeTproxy || appState.runMode == RunModeTun2Socks,
        onDraftChange = { sheetState.dnsSettingsDraft = it },
        onDismissRequest = { sheetState.showDnsSettings = false },
        onSave = { draft ->
            updateAppState { state ->
                val forceEnableLocalDns = state.runMode == RunModeTproxy || state.runMode == RunModeTun2Socks
                state.copy(
                    enableLocalDns = if (forceEnableLocalDns) true else draft.enableLocalDns,
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
    IgnoredInterfacesBottomSheet(
        show = sheetState.showIgnoredInterfaces,
        interfaces = sheetState.ignoredInterfaceOptions,
        selectedInterfaces = sheetState.ignoredInterfacesDraft,
        loading = sheetState.ignoredInterfacesLoading,
        errorMessage = sheetState.ignoredInterfacesError,
        onSelectedInterfacesChange = {
            sheetState.ignoredInterfacesDraft = it.orderedBy(sheetState.ignoredInterfaceOptions)
        },
        onDismissRequest = { sheetState.closeIgnoredInterfaces() },
        onSave = { interfaces ->
            updateAppState { state ->
                state.copy(ignoredInterfaces = interfaces.orderedBy(sheetState.ignoredInterfaceOptions))
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
