// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.home

import app.AppState
import app.MihomoSubscriptionInfo
import app.modes.MihomoModeDirect
import app.modes.MihomoModeGlobal
import app.modes.MihomoModeRule
import engine.mihomo.runtime.MihomoRuntimeState
import engine.mihomo.runtime.MihomoTrafficSample
import engine.mihomo.runtime.MihomoTrafficState
import engine.mihomo.selectedMihomoProfileOrNull
import features.mihomo.provider.MihomoProviderUsageItem
import features.mihomo.provider.MihomoProviderUsageKind
import features.mihomo.provider.MihomoProviderUsageLoadState
import features.monitoring.MonitoringState
import ui.theme.FocusTone
import utils.toReadableBytes

internal enum class HomeServiceStatus {
    Enabled,
    Disabled,
    Error,
}

internal fun homeFocusTone(status: HomeServiceStatus): FocusTone = when (status) {
    HomeServiceStatus.Enabled -> FocusTone.Primary
    HomeServiceStatus.Disabled -> FocusTone.Inactive
    HomeServiceStatus.Error -> FocusTone.Error
}

internal enum class HomePrimaryRowKind {
    Configuration,
    Node,
}

internal data class HomePrimaryRow(
    val kind: HomePrimaryRowKind,
    val value: String?,
)

internal enum class HomeNetworkRowKind {
    Ipv4,
    Ipv6,
}

internal data class HomeNetworkRow(
    val kind: HomeNetworkRowKind,
    val value: String?,
)

internal data class HomeSubscriptionSummary(
    val usedBytes: Long,
    val totalBytes: Long,
    val remainingBytes: Long,
    val expireAtSeconds: Long,
)

internal data class HomeSubscriptionContent(
    val showPrimary: Boolean,
    val showProviders: Boolean,
    val providersPending: Boolean = false,
    val providerItems: List<MihomoProviderUsageItem> = emptyList(),
) {
    val showProviderDivider: Boolean
        get() = showPrimary && showProviders

    val showEmpty: Boolean
        get() = !showPrimary && !showProviders && !providersPending
}

internal fun buildHomeSubscriptionContent(
    primary: HomeSubscriptionSummary?,
    providers: MihomoProviderUsageLoadState,
    providersPending: Boolean = false,
): HomeSubscriptionContent = HomeSubscriptionContent(
    showPrimary = primary != null,
    showProviders = providers != MihomoProviderUsageLoadState.Hidden,
    providersPending = providersPending,
    providerItems = (providers as? MihomoProviderUsageLoadState.Ready)?.summary?.items.orEmpty(),
)

internal fun MihomoProviderUsageItem.toHomeSubscriptionSummaryOrNull(): HomeSubscriptionSummary? {
    if (kind != MihomoProviderUsageKind.Metered) return null
    val used = usedBytes.coerceAtLeast(0L)
    val total = totalBytes.coerceAtLeast(0L)
    return HomeSubscriptionSummary(
        usedBytes = used,
        totalBytes = total,
        remainingBytes = (total - used).coerceAtLeast(0L),
        expireAtSeconds = expireAtSeconds.coerceAtLeast(0L),
    )
}

internal data class HomeControllerRuntimeState(
    val mihomoMode: Int?,
    val selectedNode: String?,
)

internal data class HomeControllerState(
    val serviceStatus: HomeServiceStatus,
    val runMode: Int,
    val mihomoMode: Int,
    val mihomoModeReadOnly: Boolean,
    val primaryRows: List<HomePrimaryRow>,
    val subscription: HomeSubscriptionSummary?,
)

internal data class HomeNetworkActivityState(
    val accumulatedUploadBytes: Long?,
    val accumulatedDownloadBytes: Long?,
    val uploadBytesPerSecond: Long?,
    val downloadBytesPerSecond: Long?,
    val networkSamples: List<MihomoTrafficSample>,
) {
    val hasNetworkSamples: Boolean
        get() = networkSamples.isNotEmpty()
}

internal data class HomeMonitoringOverviewState(
    val serviceRunning: Boolean,
    val resourceCpuPercent: Double?,
    val resourceMemoryBytes: Long?,
    val activeConnectionCount: Int?,
    val todayTrafficBytes: Long,
    val networkRows: List<HomeNetworkRow>,
)

internal data class HomeModeChange(
    val runtimeAppState: AppState,
    val persistSelection: Boolean,
    val patchRuntime: Boolean,
)

internal fun buildHomeControllerRuntimeState(
    runtimeState: MihomoRuntimeState,
): HomeControllerRuntimeState = HomeControllerRuntimeState(
    mihomoMode = runtimeState.configs.mode.toMihomoModeOrNull(),
    selectedNode = runtimeState.proxies.groups
        .firstOrNull { group -> group.now.isNotBlank() }
        ?.now,
)

internal fun buildHomeControllerState(
    appState: AppState,
    runtimeState: MihomoRuntimeState,
    rawMihomoMode: Int? = null,
): HomeControllerState = buildHomeControllerState(
    appState = appState,
    runtimeState = buildHomeControllerRuntimeState(runtimeState),
    rawMihomoMode = rawMihomoMode,
)

internal fun buildHomeControllerState(
    appState: AppState,
    runtimeState: HomeControllerRuntimeState,
    rawMihomoMode: Int? = null,
): HomeControllerState {
    val selectedProfile = appState.selectedMihomoProfileOrNull()
    val rawProfile = selectedProfile?.disableOverrides == true
    return HomeControllerState(
        serviceStatus = if (appState.proxyRunning) HomeServiceStatus.Enabled else HomeServiceStatus.Disabled,
        runMode = appState.runMode,
        mihomoMode = when {
            rawProfile && appState.proxyRunning -> runtimeState.mihomoMode ?: rawMihomoMode ?: appState.mihomoMode
            rawProfile -> rawMihomoMode ?: appState.mihomoMode
            else -> appState.mihomoMode
        },
        mihomoModeReadOnly = rawProfile && !appState.proxyRunning,
        primaryRows = listOf(
            HomePrimaryRow(HomePrimaryRowKind.Node, runtimeState.selectedNode),
            HomePrimaryRow(HomePrimaryRowKind.Configuration, selectedProfile?.name?.takeIf(String::isNotBlank)),
        ),
        subscription = selectedProfile?.subscriptionInfo?.toHomeSubscriptionSummaryOrNull(),
    )
}

internal fun buildHomeNetworkActivityState(
    appState: AppState,
    traffic: MihomoTrafficState,
    networkSamples: List<MihomoTrafficSample>,
): HomeNetworkActivityState {
    val trafficAvailable = appState.proxyRunning && traffic.connected
    return HomeNetworkActivityState(
        accumulatedUploadBytes = traffic.totalUp.takeIf { appState.proxyRunning },
        accumulatedDownloadBytes = traffic.totalDown.takeIf { appState.proxyRunning },
        uploadBytesPerSecond = traffic.latest.up.takeIf { trafficAvailable },
        downloadBytesPerSecond = traffic.latest.down.takeIf { trafficAvailable },
        networkSamples = networkSamples.takeLast(HomeNetworkSampleLimit).takeIf { trafficAvailable }.orEmpty(),
    )
}

internal fun buildHomeMonitoringOverviewState(
    monitoringState: MonitoringState,
): HomeMonitoringOverviewState = HomeMonitoringOverviewState(
    serviceRunning = monitoringState.serviceRunning,
    resourceCpuPercent = monitoringState.resource.cpuPercent,
    resourceMemoryBytes = monitoringState.resource.memoryBytes,
    activeConnectionCount = monitoringState.connections.activeCount,
    todayTrafficBytes = monitoringState.traffic.today.total,
    networkRows = listOf(
        HomeNetworkRow(HomeNetworkRowKind.Ipv4, monitoringState.network.local.ipv4Addresses.firstOrNull()),
        HomeNetworkRow(HomeNetworkRowKind.Ipv6, monitoringState.network.local.ipv6Addresses.firstOrNull()),
    ),
)

internal fun buildHomeModeChange(
    appState: AppState,
    currentMode: Int,
    requestedMode: Int,
): HomeModeChange? {
    if (requestedMode == currentMode) return null
    val rawProfile = appState.selectedMihomoProfileOrNull()?.disableOverrides == true
    if (rawProfile && !appState.proxyRunning) return null
    return HomeModeChange(
        runtimeAppState = appState.copy(mihomoMode = requestedMode),
        persistSelection = !rawProfile,
        patchRuntime = appState.proxyRunning,
    )
}

internal fun String?.toMihomoModeOrNull(): Int? = when (this?.lowercase()) {
    "global" -> MihomoModeGlobal
    "direct" -> MihomoModeDirect
    "rule" -> MihomoModeRule
    else -> null
}

internal fun formatHomeRuntimeBytes(bytes: Long?): String {
    return bytes?.toReadableBytes(keepTrailingZero = false) ?: HomeUnavailableValue
}

private fun MihomoSubscriptionInfo.toHomeSubscriptionSummaryOrNull(): HomeSubscriptionSummary? {
    if (!hasTraffic && expireAtSeconds <= 0L) return null
    val used = usedBytes.coerceAtLeast(0L)
    val total = totalBytes.coerceAtLeast(0L)
    return HomeSubscriptionSummary(
        usedBytes = used,
        totalBytes = total,
        remainingBytes = (total - used).coerceAtLeast(0L),
        expireAtSeconds = expireAtSeconds.coerceAtLeast(0L),
    )
}

private const val HomeNetworkSampleLimit = 60
internal const val HomeUnavailableValue = "—"
