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
import engine.mihomo.selectedMihomoProfileOrNull
import features.monitoring.MonitoringState
import utils.toReadableBytes
import ui.theme.FocusTone

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

internal data class HomeDisplayState(
    val serviceStatus: HomeServiceStatus,
    val runMode: Int,
    val mihomoMode: Int,
    val mihomoModeReadOnly: Boolean,
    val primaryRows: List<HomePrimaryRow>,
    val accumulatedUploadBytes: Long?,
    val accumulatedDownloadBytes: Long?,
    val uploadBytesPerSecond: Long?,
    val downloadBytesPerSecond: Long?,
    val networkSamples: List<MihomoTrafficSample>,
    val networkRows: List<HomeNetworkRow>,
    val subscription: HomeSubscriptionSummary?,
) {
    val hasNetworkSamples: Boolean
        get() = networkSamples.isNotEmpty()
}

internal data class HomeModeChange(
    val runtimeAppState: AppState,
    val persistSelection: Boolean,
    val patchRuntime: Boolean,
)

internal fun buildHomeDisplayState(
    appState: AppState,
    runtimeState: MihomoRuntimeState,
    rawMihomoMode: Int? = null,
    monitoringState: MonitoringState = MonitoringState(),
): HomeDisplayState {
    val selectedProfile = appState.selectedMihomoProfileOrNull()
    val rawProfile = selectedProfile?.disableOverrides == true
    val runtimeMihomoMode = runtimeState.configs.mode.toMihomoModeOrNull()
    val trafficAvailable = appState.proxyRunning && runtimeState.traffic.connected
    val selectedNode = runtimeState.proxies.groups
        .firstOrNull { group -> group.now.isNotBlank() }
        ?.now
    return HomeDisplayState(
        serviceStatus = if (appState.proxyRunning) HomeServiceStatus.Enabled else HomeServiceStatus.Disabled,
        runMode = appState.runMode,
        mihomoMode = when {
            rawProfile && appState.proxyRunning -> runtimeMihomoMode ?: rawMihomoMode ?: appState.mihomoMode
            rawProfile -> rawMihomoMode ?: appState.mihomoMode
            else -> appState.mihomoMode
        },
        mihomoModeReadOnly = rawProfile && !appState.proxyRunning,
        primaryRows = listOf(
            HomePrimaryRow(HomePrimaryRowKind.Node, selectedNode),
            HomePrimaryRow(HomePrimaryRowKind.Configuration, selectedProfile?.name?.takeIf(String::isNotBlank)),
        ),
        accumulatedUploadBytes = runtimeState.traffic.totalUp.takeIf { appState.proxyRunning },
        accumulatedDownloadBytes = runtimeState.traffic.totalDown.takeIf { appState.proxyRunning },
        uploadBytesPerSecond = runtimeState.traffic.latest.up.takeIf { trafficAvailable },
        downloadBytesPerSecond = runtimeState.traffic.latest.down.takeIf { trafficAvailable },
        networkSamples = if (trafficAvailable) runtimeState.traffic.history.takeLast(HomeNetworkSampleLimit) else emptyList(),
        networkRows = listOf(
            HomeNetworkRow(HomeNetworkRowKind.Ipv4, monitoringState.network.local.ipv4Addresses.firstOrNull()),
            HomeNetworkRow(HomeNetworkRowKind.Ipv6, monitoringState.network.local.ipv6Addresses.firstOrNull()),
        ),
        subscription = selectedProfile?.subscriptionInfo?.toHomeSubscriptionSummaryOrNull(),
    )
}

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
