// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.withNormalizedConnectionTestSettings
import app.AppState
import app.AutoSwitchSettings
import app.DefaultMihomoOverrideScriptId
import app.DefaultMihomoProfileId
import app.MihomoOverrideScriptState
import app.MihomoProfileState
import app.MihomoProfileType
import app.MihomoSubscriptionInfo
import app.ServiceControlKeyguard
import app.ServiceControlSchedule
import app.ServiceControlSettings
import app.ServiceControlWifi
import app.ServiceControlWifiRule
import app.modes.RunModeVpnService
import app.withCompatibleProxyAppListMode
import features.settings.servicecontrol.normalizeServiceControlSettings

/**
 * [profileContents] maps a profile ID to its configuration text; profiles
 * without stored content are absent and back up with empty content.
 */
internal fun AppState.toAppBackupFile(
    createdAtMillis: Long,
    appVersionName: String,
    appVersionCode: Int,
    profileContents: Map<Int, String>,
): AppBackupFile =
    AppBackupFile(
        format = AppBackupFormat,
        version = CurrentAppBackupVersion,
        createdAtMillis = createdAtMillis,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        data =
            AppBackupData(
                settings = toBackupSettings(),
                mihomoProfiles = mihomoProfiles.map { profile ->
                    profile.toBackup(content = profileContents[profile.id].orEmpty())
                },
                selectedMihomoProfileId = selectedMihomoProfileId,
                mihomoOverrideScripts = mihomoOverrideScripts.map(MihomoOverrideScriptState::toBackup),
                proxyAppListSelectedApps = proxyAppListSelectedApps,
            ),
    )

internal fun AppBackupFile.toRestorePreview(): AppBackupRestorePreview {
    val migrated = migrateAppBackup()
    migrated.data.validateForRestore()
    val restoredState = migrated.data.toAppState()
    val profileContents = migrated.data.mihomoProfiles
        .filter { profile -> profile.content.isNotBlank() }
        .associate { profile -> profile.id to profile.content }
    return AppBackupRestorePreview(
        backup = migrated,
        restoredState = restoredState,
        profileContents = profileContents,
        warnings = restoredState.restoreWarnings(profileContents),
    )
}

private fun AppState.toBackupSettings(): AppBackupSettings =
    AppBackupSettings(
        colorMode = colorMode,
        languageMode = languageMode,
        seedIndex = seedIndex,
        hideRecentTasks = hideRecentTasks,
        allowScreenshots = allowScreenshots,
        hideLauncherIcon = hideLauncherIcon,
        autoHideNavigationBar = autoHideNavigationBar,
        mihomoMode = mihomoMode,
        mihomoProxyExcludeNotSelectable = mihomoProxyExcludeNotSelectable,
        mihomoProxyLayout = mihomoProxyLayout,
        mihomoProxySort = mihomoProxySort,
        mihomoTunStack = mihomoTunStack,
        mihomoTunCongestionController = mihomoTunCongestionController,
        mihomoControlPort = mihomoControlPort,
        mihomoControlSecret = mihomoControlSecret,
        enableLocalDns = enableLocalDns,
        localProxyPort = localProxyPort,
        enableDynamicLocalProxyPort = enableDynamicLocalProxyPort,
        localProxyListenAllInterfaces = localProxyListenAllInterfaces,
        localProxyUsername = localProxyUsername,
        localProxyPassword = localProxyPassword,
        enableVpnAppendHttpProxy = enableVpnAppendHttpProxy,
        enableVpnHevTun = enableVpnHevTun,
        tunMtu = tunMtu,
        tunVpnDns = tunVpnDns,
        tunIpv4Cidr = tunIpv4Cidr,
        tunIpv6Cidr = tunIpv6Cidr,
        connectionTestUrl = connectionTestUrl,
        connectionTestConcurrent = connectionTestConcurrent,
        connectionTestTimeoutMillis = connectionTestTimeoutMillis,
        connectionTestUnifiedDelay = connectionTestUnifiedDelay,
        coreLogLevel = coreLogLevel,
        enableGeodataMode = enableGeodataMode,
        enableTrafficStatsNotification = enableTrafficStatsNotification,
        enableBroadcastControl = enableBroadcastControl,
        mihomoGeodataLoader = mihomoGeodataLoader,
        enableResourceAutoUpdate = enableResourceAutoUpdate,
        resourceAutoUpdateInterval = resourceAutoUpdateInterval,
        resourceFileSource = resourceFileSource,
        customResourceFileGeoIpUrl = customResourceFileGeoIpUrl,
        customResourceFileGeoSiteUrl = customResourceFileGeoSiteUrl,
        customResourceFileMmdbUrl = customResourceFileMmdbUrl,
        customResourceFileAsnUrl = customResourceFileAsnUrl,
        customResourceFileDirectCidrIpv4Url = customResourceFileDirectCidrIpv4Url,
        customResourceFileDirectCidrIpv6Url = customResourceFileDirectCidrIpv6Url,
        enableSniffer = enableSniffer,
        enableSnifferOverrideDestination = enableSnifferOverrideDestination,
        snifferForceDnsMapping = snifferForceDnsMapping,
        snifferParsePureIp = snifferParsePureIp,
        snifferHttpPorts = snifferHttpPorts,
        snifferTlsPorts = snifferTlsPorts,
        snifferQuicPorts = snifferQuicPorts,
        snifferHttpOverrideDestinationMode = snifferHttpOverrideDestinationMode,
        snifferTlsOverrideDestinationMode = snifferTlsOverrideDestinationMode,
        snifferQuicOverrideDestinationMode = snifferQuicOverrideDestinationMode,
        snifferForceDomain = snifferForceDomain,
        snifferSkipDomain = snifferSkipDomain,
        snifferSkipSrcAddress = snifferSkipSrcAddress,
        snifferSkipDstAddress = snifferSkipDstAddress,
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
        transparentProxyPort = transparentProxyPort,
        enableRootEbpfDirectCidrBypass = enableRootEbpfDirectCidrBypass,
        enableRootIpv6Disabler = enableRootIpv6Disabler,
        socks5ProxyPort = socks5ProxyPort,
        bpf2SocksBridgePort = bpf2SocksBridgePort,
        serviceControl = serviceControl.toBackup(),
        autoSwitch = autoSwitch.toBackup(),
        tunSharedNetworkInterfaces = tunSharedNetworkInterfaces,
        tunBypassRuleSetTags = tunBypassRuleSetTags,
        externalInterfaces = externalInterfaces,
        ignoredInterfaces = ignoredInterfaces,
        privateAddressCidrs = privateAddressCidrs,
        proxyAppListMode = proxyAppListMode,
    )

private fun AutoSwitchSettings.toBackup(): AppBackupAutoSwitch =
    AppBackupAutoSwitch(
        enabled = enabled,
        profileId = profileId,
        groups = groups,
        preferredRegions = preferredRegions,
        preferredKeywords = preferredKeywords,
        failureThreshold = failureThreshold,
        intervalSeconds = intervalSeconds,
        expectedDelayMillis = expectedDelayMillis,
    )

private fun AppBackupAutoSwitch.toState(): AutoSwitchSettings =
    AutoSwitchSettings(
        enabled = enabled,
        profileId = profileId,
        groups = groups,
        preferredRegions = preferredRegions,
        preferredKeywords = preferredKeywords,
        failureThreshold = failureThreshold,
        intervalSeconds = intervalSeconds,
        expectedDelayMillis = expectedDelayMillis,
    ).normalized()

private fun ServiceControlSettings.toBackup(): AppBackupServiceControl =
    AppBackupServiceControl(
        enabled = enabled,
        keyguard = AppBackupServiceControlKeyguard(
            enabled = keyguard.enabled,
            lockStart = keyguard.lockStart,
            lockStop = keyguard.lockStop,
            unlockStart = keyguard.unlockStart,
            unlockStop = keyguard.unlockStop,
        ),
        schedule = AppBackupServiceControlSchedule(
            enabled = schedule.enabled,
            startCron = schedule.startCron,
            stopCron = schedule.stopCron,
        ),
        wifi = AppBackupServiceControlWifi(
            enabled = wifi.enabled,
            connectStart = wifi.connectStart.toBackup(),
            connectStop = wifi.connectStop.toBackup(),
            disconnectStart = wifi.disconnectStart.toBackup(),
            disconnectStop = wifi.disconnectStop.toBackup(),
        ),
    )

private fun ServiceControlWifiRule.toBackup(): AppBackupServiceControlWifiRule =
    AppBackupServiceControlWifiRule(enabled = enabled, ssids = ssids, bssids = bssids)

private fun AppBackupServiceControl.toState(): ServiceControlSettings =
    ServiceControlSettings(
        enabled = enabled,
        keyguard = ServiceControlKeyguard(
            enabled = keyguard.enabled,
            lockStart = keyguard.lockStart,
            lockStop = keyguard.lockStop,
            unlockStart = keyguard.unlockStart,
            unlockStop = keyguard.unlockStop,
        ),
        schedule = ServiceControlSchedule(
            enabled = schedule.enabled,
            startCron = schedule.startCron,
            stopCron = schedule.stopCron,
        ),
        wifi = ServiceControlWifi(
            enabled = wifi.enabled,
            connectStart = wifi.connectStart.toState(),
            connectStop = wifi.connectStop.toState(),
            disconnectStart = wifi.disconnectStart.toState(),
            disconnectStop = wifi.disconnectStop.toState(),
        ),
    )

private fun AppBackupServiceControlWifiRule.toState(): ServiceControlWifiRule =
    ServiceControlWifiRule(enabled = enabled, ssids = ssids, bssids = bssids)

private fun MihomoProfileState.toBackup(content: String): AppBackupMihomoProfile =
    AppBackupMihomoProfile(
        id = id,
        name = name,
        type = type.storageValue,
        url = url,
        userAgent = userAgent,
        hwid = hwid,
        updateInterval = updateInterval,
        updateViaProxy = updateViaProxy,
        ageSecretKey = ageSecretKey,
        enabled = enabled,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        subscriptionUploadBytes = subscriptionInfo.uploadBytes,
        subscriptionDownloadBytes = subscriptionInfo.downloadBytes,
        subscriptionTotalBytes = subscriptionInfo.totalBytes,
        subscriptionExpireAtSeconds = subscriptionInfo.expireAtSeconds,
        overrideScriptId = overrideScriptId,
        disableOverrides = disableOverrides,
        syncFailed = syncFailed,
        content = content,
    )

private fun MihomoOverrideScriptState.toBackup(): AppBackupMihomoOverrideScript =
    AppBackupMihomoOverrideScript(
        id = id,
        name = name,
        content = content,
    )

private fun AppBackupData.toAppState(): AppState {
    val defaults = AppState()
    val restoredProfiles = mihomoProfiles.map(AppBackupMihomoProfile::toState)
    val restoredOverrideScripts = mihomoOverrideScripts.map(AppBackupMihomoOverrideScript::toState)
    val restoredSelectedProfileId = selectedMihomoProfileId
        .takeIf { id -> restoredProfiles.any { profile -> profile.id == id } }
        ?: restoredProfiles.firstOrNull()?.id
        ?: DefaultMihomoProfileId

    return defaults.copy(
        colorMode = settings.colorMode,
        languageMode = settings.languageMode,
        seedIndex = settings.seedIndex,
        hideRecentTasks = settings.hideRecentTasks,
        allowScreenshots = settings.allowScreenshots,
        hideLauncherIcon = settings.hideLauncherIcon,
        autoHideNavigationBar = settings.autoHideNavigationBar,
        mihomoProfiles = restoredProfiles,
        nextMihomoProfileId = nextId(defaults.nextMihomoProfileId, restoredProfiles.map(MihomoProfileState::id)),
        selectedMihomoProfileId = restoredSelectedProfileId,
        mihomoOverrideScripts = restoredOverrideScripts,
        nextMihomoOverrideScriptId = nextId(
            defaults.nextMihomoOverrideScriptId,
            restoredOverrideScripts.map(MihomoOverrideScriptState::id),
        ),
        runMode = RunModeVpnService,
        mihomoMode = settings.mihomoMode,
        mihomoProxyExcludeNotSelectable = settings.mihomoProxyExcludeNotSelectable,
        mihomoProxyLayout = settings.mihomoProxyLayout,
        mihomoProxySort = settings.mihomoProxySort,
        mihomoTunStack = settings.mihomoTunStack,
        mihomoTunCongestionController = settings.mihomoTunCongestionController,
        mihomoControlPort = settings.mihomoControlPort,
        mihomoControlSecret = settings.mihomoControlSecret,
        enableLocalDns = settings.enableLocalDns,
        localProxyPort = settings.localProxyPort,
        enableDynamicLocalProxyPort = settings.enableDynamicLocalProxyPort,
        localProxyListenAllInterfaces = settings.localProxyListenAllInterfaces,
        localProxyUsername = settings.localProxyUsername,
        localProxyPassword = settings.localProxyPassword,
        enableVpnAppendHttpProxy = settings.enableVpnAppendHttpProxy,
        enableVpnHevTun = settings.enableVpnHevTun,
        tunMtu = settings.tunMtu,
        tunVpnDns = settings.tunVpnDns,
        tunIpv4Cidr = settings.tunIpv4Cidr,
        tunIpv6Cidr = settings.tunIpv6Cidr,
        proxyRunning = false,
        connectionTestUrl = settings.connectionTestUrl,
        connectionTestConcurrent = settings.connectionTestConcurrent,
        connectionTestTimeoutMillis = settings.connectionTestTimeoutMillis,
        connectionTestUnifiedDelay = settings.connectionTestUnifiedDelay,
        coreLogLevel = settings.coreLogLevel,
        enableGeodataMode = settings.enableGeodataMode,
        enableTrafficStatsNotification = settings.enableTrafficStatsNotification,
        enableBroadcastControl = settings.enableBroadcastControl,
        mihomoGeodataLoader = settings.mihomoGeodataLoader,
        enableResourceAutoUpdate = settings.enableResourceAutoUpdate,
        resourceAutoUpdateInterval = settings.resourceAutoUpdateInterval,
        resourceFileSource = settings.resourceFileSource,
        customResourceFileGeoIpUrl = settings.customResourceFileGeoIpUrl,
        customResourceFileGeoSiteUrl = settings.customResourceFileGeoSiteUrl,
        customResourceFileMmdbUrl = settings.customResourceFileMmdbUrl,
        customResourceFileAsnUrl = settings.customResourceFileAsnUrl,
        customResourceFileDirectCidrIpv4Url = settings.customResourceFileDirectCidrIpv4Url,
        customResourceFileDirectCidrIpv6Url = settings.customResourceFileDirectCidrIpv6Url,
        enableSniffer = settings.enableSniffer,
        enableSnifferOverrideDestination = settings.enableSnifferOverrideDestination,
        snifferForceDnsMapping = settings.snifferForceDnsMapping,
        snifferParsePureIp = settings.snifferParsePureIp,
        snifferHttpPorts = settings.snifferHttpPorts,
        snifferTlsPorts = settings.snifferTlsPorts,
        snifferQuicPorts = settings.snifferQuicPorts,
        snifferHttpOverrideDestinationMode = settings.snifferHttpOverrideDestinationMode,
        snifferTlsOverrideDestinationMode = settings.snifferTlsOverrideDestinationMode,
        snifferQuicOverrideDestinationMode = settings.snifferQuicOverrideDestinationMode,
        snifferForceDomain = settings.snifferForceDomain,
        snifferSkipDomain = settings.snifferSkipDomain,
        snifferSkipSrcAddress = settings.snifferSkipSrcAddress,
        snifferSkipDstAddress = settings.snifferSkipDstAddress,
        enableIpv6 = settings.enableIpv6,
        enableIpv6Prefer = settings.enableIpv6Prefer,
        overrideDns = settings.overrideDns,
        dnsPreferH3 = settings.dnsPreferH3,
        dnsUseHosts = settings.dnsUseHosts,
        dnsUseSystemHosts = settings.dnsUseSystemHosts,
        dnsRespectRules = settings.dnsRespectRules,
        dnsEnhancedMode = settings.dnsEnhancedMode,
        dnsFakeIpRange = settings.dnsFakeIpRange,
        dnsFakeIpFilter = settings.dnsFakeIpFilter,
        dnsDefaultNameserver = settings.dnsDefaultNameserver,
        dnsNameserver = settings.dnsNameserver,
        dnsNameserverPolicy = settings.dnsNameserverPolicy,
        dnsProxyServerNameserver = settings.dnsProxyServerNameserver,
        dnsFallback = settings.dnsFallback,
        dnsFallbackFilterGeoip = settings.dnsFallbackFilterGeoip,
        dnsFallbackFilterGeoipCode = settings.dnsFallbackFilterGeoipCode,
        dnsFallbackFilterGeosite = settings.dnsFallbackFilterGeosite,
        dnsFallbackFilterIpcidr = settings.dnsFallbackFilterIpcidr,
        dnsFallbackFilterDomain = settings.dnsFallbackFilterDomain,
        dnsHosts = settings.dnsHosts,
        transparentProxyPort = settings.transparentProxyPort,
        enableRootBootScript = false,
        enableAppListEnhancement = false,
        enableRootEbpfRules = false,
        enableRootEbpfDirectCidrBypass = settings.enableRootEbpfDirectCidrBypass,
        enableRootIpv6Disabler = settings.enableRootIpv6Disabler,
        socks5ProxyPort = settings.socks5ProxyPort,
        bpf2SocksBridgePort = settings.bpf2SocksBridgePort,
        serviceControl = normalizeServiceControlSettings(settings.serviceControl.toState()),
        autoSwitch = settings.autoSwitch.toState(),
        tunSharedNetworkInterfaces = settings.tunSharedNetworkInterfaces,
        tunBypassRuleSetTags = settings.tunBypassRuleSetTags,
        externalInterfaces = settings.externalInterfaces,
        ignoredInterfaces = settings.ignoredInterfaces,
        privateAddressCidrs = settings.privateAddressCidrs,
        proxyAppListMode = settings.proxyAppListMode,
        proxyAppListSelectedApps = proxyAppListSelectedApps,
    ).withCompatibleProxyAppListMode().withNormalizedConnectionTestSettings()
}

private fun AppBackupMihomoProfile.toState(): MihomoProfileState =
    MihomoProfileState(
        id = id,
        name = name,
        type = MihomoProfileType.fromStorageValue(type),
        url = url,
        userAgent = userAgent,
        hwid = hwid,
        updateInterval = updateInterval,
        updateViaProxy = updateViaProxy,
        ageSecretKey = ageSecretKey,
        enabled = enabled,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        subscriptionInfo = MihomoSubscriptionInfo(
            uploadBytes = subscriptionUploadBytes.coerceAtLeast(0L),
            downloadBytes = subscriptionDownloadBytes.coerceAtLeast(0L),
            totalBytes = subscriptionTotalBytes.coerceAtLeast(0L),
            expireAtSeconds = subscriptionExpireAtSeconds.coerceAtLeast(0L),
        ),
        overrideScriptId = overrideScriptId,
        disableOverrides = disableOverrides,
        syncFailed = syncFailed,
    )

private fun AppBackupMihomoOverrideScript.toState(): MihomoOverrideScriptState =
    MihomoOverrideScriptState(
        id = id,
        name = name,
        content = content,
    )

private fun AppState.restoreWarnings(profileContents: Map<Int, String>): List<AppBackupWarning> {
    val overrideScriptIds = mihomoOverrideScripts.mapTo(mutableSetOf(), MihomoOverrideScriptState::id)
    val missingOverrideScriptCount = mihomoProfiles.count { profile ->
        profile.overrideScriptId != DefaultMihomoOverrideScriptId &&
            profile.overrideScriptId !in overrideScriptIds
    }
    // A URL profile without content syncs again; a file profile has no other source.
    val emptyProfileContentCount = mihomoProfiles.count { profile ->
        profile.type == MihomoProfileType.File && profile.id !in profileContents
    }
    return buildList {
        if (missingOverrideScriptCount > 0) {
            add(AppBackupWarning.MissingOverrideScriptReferences(missingOverrideScriptCount))
        }
        if (emptyProfileContentCount > 0) {
            add(AppBackupWarning.EmptyProfileContents(emptyProfileContentCount))
        }
    }
}

private fun nextId(defaultValue: Int, ids: List<Int>): Int =
    maxOf(defaultValue, (ids.maxOrNull() ?: 0) + 1)
