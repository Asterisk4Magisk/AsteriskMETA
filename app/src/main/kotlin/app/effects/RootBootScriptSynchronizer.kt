// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.AppState
import app.MihomoProfileState
import app.modes.isRootRunMode
import data.AndroidAppStateStore
import engine.proxy.withResolvedDynamicLocalProxyPort
import features.logs.AndroidAppLogger
import features.settings.usecase.RootBootScriptResult
import features.settings.usecase.RootBootScriptUseCase
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
internal fun RootBootScriptSynchronizer(
    stateStore: AndroidAppStateStore,
    rootBootScriptUseCase: RootBootScriptUseCase,
) {
    LaunchedEffect(stateStore, rootBootScriptUseCase) {
        stateStore.state
            .map { state -> state.toRootBootScriptRefresh() }
            .distinctUntilChanged { previous, next -> previous.signature == next.signature }
            .conflate()
            .collect { refresh ->
                val state = refresh.appState.withResolvedDynamicLocalProxyPort()
                if (state != refresh.appState) {
                    stateStore.update { currentState ->
                        if (currentState == refresh.appState) state else currentState
                    }
                    return@collect
                }
                if (!state.enableRootBootScript || !state.runMode.isRootRunMode()) {
                    return@collect
                }
                when (val result = rootBootScriptUseCase.refresh(state)) {
                    RootBootScriptResult.Success -> Unit
                    RootBootScriptResult.RootUnavailable -> AndroidAppLogger.warn(
                        LogTag,
                        "Skipped ROOT boot script refresh because root access is unavailable",
                    )
                    is RootBootScriptResult.Failed -> AndroidAppLogger.warn(
                        LogTag,
                        "Failed to refresh ROOT boot script",
                        result.error,
                    )
                }
            }
    }
}

private data class RootBootScriptRefresh(
    val appState: AppState,
    val signature: RootBootScriptSignature,
)

private data class RootBootScriptSignature(
    val enabled: Boolean,
    val runMode: Int,
    val mihomoMode: Int,
    val mihomoTunStack: Int,
    val selectedMihomoProfileId: Int,
    val mihomoProfiles: List<MihomoProfileState>,
    val mihomoControlPort: String,
    val mihomoControlSecret: String,
    val enableLocalDns: Boolean,
    val coreLogLevel: Int,
    val resourceFileSource: Int,
    val customResourceFileGeoIpUrl: String,
    val customResourceFileGeoSiteUrl: String,
    val customResourceFileMmdbUrl: String,
    val customResourceFileAsnUrl: String,
    val enableSniffer: Boolean,
    val enableSnifferOverrideDestination: Boolean,
    val enableIpv6: Boolean,
    val enableIpv6Prefer: Boolean,
    val overrideDns: Boolean,
    val dnsPreferH3: Boolean,
    val dnsUseHosts: Boolean,
    val dnsUseSystemHosts: Boolean,
    val dnsRespectRules: Boolean,
    val dnsEnhancedMode: Int,
    val dnsFakeIpRange: String,
    val dnsFakeIpFilter: List<String>,
    val dnsDefaultNameserver: List<String>,
    val dnsNameserver: List<String>,
    val dnsNameserverPolicy: List<String>,
    val dnsProxyServerNameserver: List<String>,
    val dnsFallback: List<String>,
    val dnsFallbackFilterGeoip: Boolean,
    val dnsFallbackFilterGeoipCode: String,
    val dnsFallbackFilterGeosite: List<String>,
    val dnsFallbackFilterIpcidr: List<String>,
    val dnsFallbackFilterDomain: List<String>,
    val dnsHosts: List<String>,
    val localProxyPort: String,
    val enableDynamicLocalProxyPort: Boolean,
    val localProxyListenAllInterfaces: Boolean,
    val localProxyUsername: String,
    val localProxyPassword: String,
    val transparentProxyPort: String,
    val socks5ProxyPort: String,
    val tunMtu: String,
    val tunIpv4Cidr: String,
    val tunIpv6Cidr: String,
    val externalInterfaces: List<String>,
    val ignoredInterfaces: List<String>,
    val privateAddressCidrs: List<String>,
    val proxyAppListMode: Int,
    val proxyAppListSelectedApps: List<String>,
)

private fun AppState.toRootBootScriptRefresh(): RootBootScriptRefresh {
    return RootBootScriptRefresh(
        appState = this,
        signature = RootBootScriptSignature(
            enabled = enableRootBootScript,
            runMode = runMode,
            mihomoMode = mihomoMode,
            mihomoTunStack = mihomoTunStack,
            selectedMihomoProfileId = selectedMihomoProfileId,
            mihomoProfiles = mihomoProfiles,
            mihomoControlPort = mihomoControlPort,
            mihomoControlSecret = mihomoControlSecret,
            enableLocalDns = enableLocalDns,
            coreLogLevel = coreLogLevel,
            resourceFileSource = resourceFileSource,
            customResourceFileGeoIpUrl = customResourceFileGeoIpUrl,
            customResourceFileGeoSiteUrl = customResourceFileGeoSiteUrl,
            customResourceFileMmdbUrl = customResourceFileMmdbUrl,
            customResourceFileAsnUrl = customResourceFileAsnUrl,
            enableSniffer = enableSniffer,
            enableSnifferOverrideDestination = enableSnifferOverrideDestination,
            enableIpv6 = enableIpv6,
            enableIpv6Prefer = enableIpv6Prefer,
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
            localProxyPort = localProxyPort,
            enableDynamicLocalProxyPort = enableDynamicLocalProxyPort,
            localProxyListenAllInterfaces = localProxyListenAllInterfaces,
            localProxyUsername = localProxyUsername,
            localProxyPassword = localProxyPassword,
            transparentProxyPort = transparentProxyPort,
            socks5ProxyPort = socks5ProxyPort,
            tunMtu = tunMtu,
            tunIpv4Cidr = tunIpv4Cidr,
            tunIpv6Cidr = tunIpv6Cidr,
            externalInterfaces = externalInterfaces,
            ignoredInterfaces = ignoredInterfaces,
            privateAddressCidrs = privateAddressCidrs,
            proxyAppListMode = proxyAppListMode,
            proxyAppListSelectedApps = proxyAppListSelectedApps,
        ),
    )
}

private const val LogTag = "RootBootScript"
