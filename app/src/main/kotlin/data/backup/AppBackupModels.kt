// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import app.DefaultMihomoOverrideScript
import app.DefaultMihomoOverrideScriptId
import app.DefaultMihomoProfileId
import app.DefaultMihomoProfileUpdateInterval
import app.DefaultMihomoProfileUserAgent
import app.MihomoProfileType
import kotlinx.serialization.Serializable

internal const val AppBackupFormat = "asteriskmeta-backup"
internal const val CurrentAppBackupVersion = 1

private val BackupDefaults = AppState()

@Serializable
internal data class AppBackupFile(
    val format: String = "",
    val version: Int = 0,
    val createdAtMillis: Long = 0L,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val data: AppBackupData = AppBackupData(),
)

@Serializable
internal data class AppBackupData(
    val settings: AppBackupSettings = AppBackupSettings(),
    val mihomoProfiles: List<AppBackupMihomoProfile> = emptyList(),
    val selectedMihomoProfileId: Int = DefaultMihomoProfileId,
    val mihomoOverrideScripts: List<AppBackupMihomoOverrideScript> = emptyList(),
    val proxyAppListSelectedApps: List<String> = emptyList(),
)

@Serializable
internal data class AppBackupSettings(
    val colorMode: Int = BackupDefaults.colorMode,
    val languageMode: Int = BackupDefaults.languageMode,
    val seedIndex: Int = BackupDefaults.seedIndex,
    val hideRecentTasks: Boolean = BackupDefaults.hideRecentTasks,
    val allowScreenshots: Boolean = BackupDefaults.allowScreenshots,
    val hideLauncherIcon: Boolean = BackupDefaults.hideLauncherIcon,
    val autoHideNavigationBar: Boolean = BackupDefaults.autoHideNavigationBar,
    val mihomoMode: Int = BackupDefaults.mihomoMode,
    val mihomoProxyInterruptConnections: Boolean = BackupDefaults.mihomoProxyInterruptConnections,
    val mihomoProxyExcludeNotSelectable: Boolean = BackupDefaults.mihomoProxyExcludeNotSelectable,
    val mihomoProxyLayout: Int = BackupDefaults.mihomoProxyLayout,
    val mihomoProxyGroupLayout: Int = BackupDefaults.mihomoProxyGroupLayout,
    val mihomoProxyGroupColumns: Int = BackupDefaults.mihomoProxyGroupColumns,
    val mihomoProxySort: Int = BackupDefaults.mihomoProxySort,
    val mihomoTunStack: Int = BackupDefaults.mihomoTunStack,
    val mihomoTunCongestionController: String = BackupDefaults.mihomoTunCongestionController,
    val mihomoControlPort: String = BackupDefaults.mihomoControlPort,
    val mihomoControlSecret: String = BackupDefaults.mihomoControlSecret,
    val enableLocalDns: Boolean = BackupDefaults.enableLocalDns,
    val localProxyPort: String = BackupDefaults.localProxyPort,
    val enableDynamicLocalProxyPort: Boolean = BackupDefaults.enableDynamicLocalProxyPort,
    val localProxyListenAllInterfaces: Boolean = BackupDefaults.localProxyListenAllInterfaces,
    val localProxyUsername: String = BackupDefaults.localProxyUsername,
    val localProxyPassword: String = BackupDefaults.localProxyPassword,
    val enableVpnAppendHttpProxy: Boolean = BackupDefaults.enableVpnAppendHttpProxy,
    val enableVpnHevTun: Boolean = BackupDefaults.enableVpnHevTun,
    val enableAutoConnect: Boolean = BackupDefaults.enableAutoConnect,
    val tunMtu: String = BackupDefaults.tunMtu,
    val tunVpnDns: String = BackupDefaults.tunVpnDns,
    val tunIpv4Cidr: String = BackupDefaults.tunIpv4Cidr,
    val tunIpv6Cidr: String = BackupDefaults.tunIpv6Cidr,
    val connectionTestUrl: String = BackupDefaults.connectionTestUrl,
    val connectionTestConcurrent: Int = BackupDefaults.connectionTestConcurrent,
    val connectionTestTimeoutMillis: Int = BackupDefaults.connectionTestTimeoutMillis,
    val connectionTestUnifiedDelay: Boolean = BackupDefaults.connectionTestUnifiedDelay,
    val coreLogLevel: Int = BackupDefaults.coreLogLevel,
    val enableGeodataMode: Boolean = BackupDefaults.enableGeodataMode,
    val enableTrafficStatsNotification: Boolean = BackupDefaults.enableTrafficStatsNotification,
    val enableBroadcastControl: Boolean = BackupDefaults.enableBroadcastControl,
    val mihomoGeodataLoader: Int = BackupDefaults.mihomoGeodataLoader,
    val enableResourceAutoUpdate: Boolean = BackupDefaults.enableResourceAutoUpdate,
    val resourceAutoUpdateInterval: String = BackupDefaults.resourceAutoUpdateInterval,
    val resourceFileSource: Int = BackupDefaults.resourceFileSource,
    val customResourceFileGeoIpUrl: String = BackupDefaults.customResourceFileGeoIpUrl,
    val customResourceFileGeoSiteUrl: String = BackupDefaults.customResourceFileGeoSiteUrl,
    val customResourceFileMmdbUrl: String = BackupDefaults.customResourceFileMmdbUrl,
    val customResourceFileAsnUrl: String = BackupDefaults.customResourceFileAsnUrl,
    val customResourceFileDirectCidrIpv4Url: String = BackupDefaults.customResourceFileDirectCidrIpv4Url,
    val customResourceFileDirectCidrIpv6Url: String = BackupDefaults.customResourceFileDirectCidrIpv6Url,
    val enableSniffer: Boolean = BackupDefaults.enableSniffer,
    val enableSnifferOverrideDestination: Boolean = BackupDefaults.enableSnifferOverrideDestination,
    val snifferForceDnsMapping: Boolean = BackupDefaults.snifferForceDnsMapping,
    val snifferParsePureIp: Boolean = BackupDefaults.snifferParsePureIp,
    val snifferHttpPorts: List<String> = BackupDefaults.snifferHttpPorts,
    val snifferTlsPorts: List<String> = BackupDefaults.snifferTlsPorts,
    val snifferQuicPorts: List<String> = BackupDefaults.snifferQuicPorts,
    val snifferHttpOverrideDestinationMode: Int = BackupDefaults.snifferHttpOverrideDestinationMode,
    val snifferTlsOverrideDestinationMode: Int = BackupDefaults.snifferTlsOverrideDestinationMode,
    val snifferQuicOverrideDestinationMode: Int = BackupDefaults.snifferQuicOverrideDestinationMode,
    val snifferForceDomain: List<String> = BackupDefaults.snifferForceDomain,
    val snifferSkipDomain: List<String> = BackupDefaults.snifferSkipDomain,
    val snifferSkipSrcAddress: List<String> = BackupDefaults.snifferSkipSrcAddress,
    val snifferSkipDstAddress: List<String> = BackupDefaults.snifferSkipDstAddress,
    val enableIpv6: Boolean = BackupDefaults.enableIpv6,
    val enableIpv6Prefer: Boolean = BackupDefaults.enableIpv6Prefer,
    val overrideDns: Boolean = BackupDefaults.overrideDns,
    val dnsPreferH3: Boolean = BackupDefaults.dnsPreferH3,
    val dnsUseHosts: Boolean = BackupDefaults.dnsUseHosts,
    val dnsUseSystemHosts: Boolean = BackupDefaults.dnsUseSystemHosts,
    val dnsRespectRules: Boolean = BackupDefaults.dnsRespectRules,
    val dnsEnhancedMode: Int = BackupDefaults.dnsEnhancedMode,
    val dnsFakeIpRange: String = BackupDefaults.dnsFakeIpRange,
    val dnsFakeIpFilter: List<String> = BackupDefaults.dnsFakeIpFilter,
    val dnsDefaultNameserver: List<String> = BackupDefaults.dnsDefaultNameserver,
    val dnsNameserver: List<String> = BackupDefaults.dnsNameserver,
    val dnsNameserverPolicy: List<String> = BackupDefaults.dnsNameserverPolicy,
    val dnsProxyServerNameserver: List<String> = BackupDefaults.dnsProxyServerNameserver,
    val dnsFallback: List<String> = BackupDefaults.dnsFallback,
    val dnsFallbackFilterGeoip: Boolean = BackupDefaults.dnsFallbackFilterGeoip,
    val dnsFallbackFilterGeoipCode: String = BackupDefaults.dnsFallbackFilterGeoipCode,
    val dnsFallbackFilterGeosite: List<String> = BackupDefaults.dnsFallbackFilterGeosite,
    val dnsFallbackFilterIpcidr: List<String> = BackupDefaults.dnsFallbackFilterIpcidr,
    val dnsFallbackFilterDomain: List<String> = BackupDefaults.dnsFallbackFilterDomain,
    val dnsHosts: List<String> = BackupDefaults.dnsHosts,
    val transparentProxyPort: String = BackupDefaults.transparentProxyPort,
    val enableRootEbpfDirectCidrBypass: Boolean = BackupDefaults.enableRootEbpfDirectCidrBypass,
    val enableRootIpv6Disabler: Boolean = BackupDefaults.enableRootIpv6Disabler,
    val socks5ProxyPort: String = BackupDefaults.socks5ProxyPort,
    val bpf2SocksBridgePort: String = BackupDefaults.bpf2SocksBridgePort,
    val serviceControl: AppBackupServiceControl = AppBackupServiceControl(),
    val autoSwitch: AppBackupAutoSwitch = AppBackupAutoSwitch(),
    val tunSharedNetworkInterfaces: List<String> = BackupDefaults.tunSharedNetworkInterfaces,
    val tunBypassRuleSetTags: List<String> = BackupDefaults.tunBypassRuleSetTags,
    val externalInterfaces: List<String> = BackupDefaults.externalInterfaces,
    val ignoredInterfaces: List<String> = BackupDefaults.ignoredInterfaces,
    val privateAddressCidrs: List<String> = BackupDefaults.privateAddressCidrs,
    val proxyAppListMode: Int = BackupDefaults.proxyAppListMode,
)

/** Profile IDs are restored unchanged, so [profileId] still names the same profile. */
@Serializable
internal data class AppBackupAutoSwitch(
    val enabled: Boolean = BackupDefaults.autoSwitch.enabled,
    val restore: Boolean = BackupDefaults.autoSwitch.restore,
    val profileId: Int = BackupDefaults.autoSwitch.profileId,
    val groups: List<String> = BackupDefaults.autoSwitch.groups,
    val preferredRegions: List<String> = BackupDefaults.autoSwitch.preferredRegions,
    val preferredKeywords: List<String> = BackupDefaults.autoSwitch.preferredKeywords,
    val failureThreshold: Int = BackupDefaults.autoSwitch.failureThreshold,
    val intervalSeconds: Int = BackupDefaults.autoSwitch.intervalSeconds,
    val expectedDelayMillis: Int = BackupDefaults.autoSwitch.expectedDelayMillis,
)

@Serializable
internal data class AppBackupServiceControl(
    val enabled: Boolean = BackupDefaults.serviceControl.enabled,
    val schedule: AppBackupServiceControlSchedule = AppBackupServiceControlSchedule(),
    val wifi: AppBackupServiceControlWifi = AppBackupServiceControlWifi(),
    val keyguard: AppBackupServiceControlKeyguard = AppBackupServiceControlKeyguard(),
)

@Serializable
internal data class AppBackupServiceControlSchedule(
    val enabled: Boolean = BackupDefaults.serviceControl.schedule.enabled,
    val startCron: String = BackupDefaults.serviceControl.schedule.startCron,
    val stopCron: String = BackupDefaults.serviceControl.schedule.stopCron,
)

@Serializable
internal data class AppBackupServiceControlWifi(
    val enabled: Boolean = BackupDefaults.serviceControl.wifi.enabled,
    val connectStart: AppBackupServiceControlWifiRule = AppBackupServiceControlWifiRule(),
    val connectStop: AppBackupServiceControlWifiRule = AppBackupServiceControlWifiRule(),
    val disconnectStart: AppBackupServiceControlWifiRule = AppBackupServiceControlWifiRule(),
    val disconnectStop: AppBackupServiceControlWifiRule = AppBackupServiceControlWifiRule(),
)

@Serializable
internal data class AppBackupServiceControlWifiRule(
    val enabled: Boolean = false,
    val ssids: List<String> = emptyList(),
    val bssids: List<String> = emptyList(),
)

@Serializable
internal data class AppBackupServiceControlKeyguard(
    val enabled: Boolean = false,
    val lockStart: Boolean = false,
    val lockStop: Boolean = false,
    val unlockStart: Boolean = false,
    val unlockStop: Boolean = false,
)

/**
 * A Mihomo profile with its configuration inlined as [content]. The app keeps
 * profile YAML in private files rather than in the state database, so the
 * backup carries the text and restore writes it back to fresh files.
 */
@Serializable
internal data class AppBackupMihomoProfile(
    val id: Int = 0,
    val name: String = "",
    val type: Int = MihomoProfileType.File.storageValue,
    val url: String = "",
    val userAgent: String = DefaultMihomoProfileUserAgent,
    val hwid: String = "",
    val updateInterval: String = DefaultMihomoProfileUpdateInterval,
    val updateViaProxy: Boolean = false,
    val ageSecretKey: String = "",
    val enabled: Boolean = true,
    val lastUpdatedAtMillis: Long = 0L,
    val subscriptionUploadBytes: Long = 0L,
    val subscriptionDownloadBytes: Long = 0L,
    val subscriptionTotalBytes: Long = 0L,
    val subscriptionExpireAtSeconds: Long = 0L,
    val overrideScriptId: Int = DefaultMihomoOverrideScriptId,
    val disableOverrides: Boolean = false,
    val syncFailed: Boolean = false,
    val content: String = "",
)

@Serializable
internal data class AppBackupMihomoOverrideScript(
    val id: Int = 0,
    val name: String = "",
    val content: String = DefaultMihomoOverrideScript,
)

/**
 * [restoredState] has no profile content references yet; [profileContents]
 * holds each profile's text by profile ID until restore writes the files.
 */
internal data class AppBackupRestorePreview(
    val backup: AppBackupFile,
    val restoredState: AppState,
    val profileContents: Map<Int, String>,
    val warnings: List<AppBackupWarning>,
) {
    val mihomoProfileCount: Int
        get() = restoredState.mihomoProfiles.size

    val mihomoOverrideScriptCount: Int
        get() = restoredState.mihomoOverrideScripts.size
}

internal sealed interface AppBackupWarning {
    data class MissingOverrideScriptReferences(
        val count: Int,
    ) : AppBackupWarning

    data class EmptyProfileContents(
        val count: Int,
    ) : AppBackupWarning
}
