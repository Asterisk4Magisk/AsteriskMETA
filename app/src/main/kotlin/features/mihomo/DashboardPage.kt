// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(
    kotlinx.coroutines.FlowPreview::class,
)

package features.mihomo

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.AppServices
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalHomeServiceControl
import app.LocalIsWideScreen
import app.LocalMainDestinationState
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.modes.MihomoModeDirect
import app.modes.MihomoModeGlobal
import app.modes.MihomoModeRule
import app.modes.NetworkActivityStyleLines
import app.modes.RunModeBpf2Socks
import app.modes.RunModeTproxy
import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import app.modes.isRootRunMode
import app.navigation.MainDestination
import engine.mihomo.mihomoModeName
import engine.mihomo.raw.MihomoRawConfigParser
import engine.mihomo.runtime.MihomoDelayStatus
import engine.mihomo.runtime.MihomoDelayTarget
import engine.mihomo.runtime.MihomoProxiesState
import engine.mihomo.runtime.MihomoRuntimeRepository
import engine.mihomo.runtime.MihomoRuntimeState
import engine.mihomo.runtime.MihomoTrafficSample
import engine.mihomo.runtime.activeNodeId
import engine.mihomo.runtime.canStartUserDelayTest
import engine.mihomo.runtime.MihomoTrafficState
import engine.mihomo.selectedMihomoProfileOrNull
import features.home.HomeControllerState
import features.home.HomeMonitoringOverviewState
import features.home.HomeNetworkActivityState
import features.home.HomeNetworkRowKind
import features.home.HomeNetworkSampleLimit
import features.home.HomePrimaryRowKind
import features.home.HomeServiceStatus
import features.home.HomeSubscriptionSummary
import features.home.HomeUnavailableValue
import features.home.buildHomeControllerRuntimeState
import features.home.buildHomeControllerState
import features.home.buildHomeModeChange
import features.home.buildHomeMonitoringOverviewState
import features.home.buildHomeNetworkActivityState
import features.home.buildNetworkActivityLineBands
import features.home.buildNetworkActivityWaveform
import features.home.buildHomeSubscriptionContent
import features.home.formatHomeRuntimeBytes
import features.home.formatHomeServiceUptime
import features.home.homeFocusTone
import features.home.toHomeSubscriptionSummaryOrNull
import features.home.toMihomoModeOrNull
import features.mihomo.provider.MihomoProviderUsageItem
import features.mihomo.provider.MihomoProviderUsageKind
import features.mihomo.provider.MihomoProviderUsageLoadState
import features.mihomo.provider.resolveSharedMihomoProviderUsageState
import features.mihomo.provider.selectedMihomoProviderUsageLoadKeyOrNull
import features.monitoring.MonitoringIntent
import features.monitoring.ObserveMonitoring
import features.monitoring.resource.ProcessStatsSourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskFocusSurface
import ui.components.AsteriskPageCard
import ui.components.AsteriskScaffold
import ui.components.AsteriskSegmentItem
import ui.components.AsteriskSegmentedControl
import ui.components.AsteriskTopAppBar
import ui.charts.DottedWaveformDotRadius
import ui.charts.drawDottedWaveform
import ui.charts.drawSmoothAreaLine
import ui.charts.rememberDottedWaveformRamp
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.theme.ExpressiveShapeRole
import ui.theme.FocusDensity
import utils.toReadableBytes
import kotlin.time.Duration.Companion.milliseconds
import app.navigation.Route as AppRoute
import ui.icons.AsteriskIcons as Icons
import utils.toReadableDateOrDash

private data class HomeTrafficPresentation(
    val traffic: MihomoTrafficState,
    val samples: List<MihomoTrafficSample>,
)

private fun AppServices.homeControllerRuntimeSnapshot() =
    buildHomeControllerRuntimeState(mihomoRuntime.state.value)

private fun AppServices.homeTrafficPresentationSnapshot(): HomeTrafficPresentation {
    val runtimeState = mihomoRuntime.state.value
    return HomeTrafficPresentation(
        traffic = runtimeState.traffic,
        samples = mihomoRuntime.trafficHistorySnapshot(HomeNetworkSampleLimit),
    )
}

private fun AppServices.homeMonitoringOverviewSnapshot() =
    buildHomeMonitoringOverviewState(monitoring.state.value)

@Composable
fun MihomoDashboardPage(
    padding: PaddingValues,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val controllerRuntimeState by remember(services.mihomoRuntime) {
        services.mihomoRuntime.state
            .map(::buildHomeControllerRuntimeState)
            .distinctUntilChanged()
    }.collectAsState(initial = services.homeControllerRuntimeSnapshot())
    val trafficPresentation by remember(services.mihomoRuntime) {
        services.mihomoRuntime.state
            .map { state -> state.traffic }
            .sample(HomeUiSampleIntervalMillis.milliseconds)
            .map { traffic ->
                HomeTrafficPresentation(
                    traffic = traffic,
                    samples = services.mihomoRuntime.trafficHistorySnapshot(HomeNetworkSampleLimit),
                )
            }
            .distinctUntilChanged()
    }.collectAsState(initial = services.homeTrafficPresentationSnapshot())
    val monitoringOverviewState by remember(services.monitoring) {
        services.monitoring.state
            .map(::buildHomeMonitoringOverviewState)
            .distinctUntilChanged()
            .sample(HomeUiSampleIntervalMillis.milliseconds)
    }.collectAsState(initial = services.homeMonitoringOverviewSnapshot())
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val mainDestinationState = LocalMainDestinationState.current
    val listState = rememberLazyListState()
    ObserveMonitoring(MonitoringIntent.Home)
    val serviceControl = LocalHomeServiceControl.current
    val operationInProgress = serviceControl.busy
    val selectedProfile = appState.selectedMihomoProfileOrNull()
    val keyedProviderUsageState by services.mihomoProviderUsage.state.collectAsState()
    val providerUsageLoadKey = appState.selectedMihomoProviderUsageLoadKeyOrNull()
    val providerUsageState = resolveSharedMihomoProviderUsageState(
        keyedState = keyedProviderUsageState,
        expectedKey = providerUsageLoadKey,
    )
    val providerUsagePending = providerUsageLoadKey != null &&
        keyedProviderUsageState.loadKey != providerUsageLoadKey
    val rawMihomoMode by produceState<Int?>(
        initialValue = null,
        key1 = selectedProfile?.id,
        key2 = selectedProfile?.contentSha256,
        key3 = selectedProfile?.disableOverrides,
    ) {
        value = if (selectedProfile?.disableOverrides == true && selectedProfile.hasContent) {
            withContext(Dispatchers.IO) {
                runCatching {
                    MihomoRawConfigParser.parse(
                        services.mihomoProfileContentStore.readBytes(selectedProfile),
                    ).snapshot?.mode?.value.toMihomoModeOrNull()
                }.getOrNull()
            }
        } else {
            null
        }
    }
    val controllerState = remember(appState, controllerRuntimeState, rawMihomoMode) {
        buildHomeControllerState(appState, controllerRuntimeState, rawMihomoMode)
    }
    val networkActivityState = remember(appState.proxyRunning, trafficPresentation) {
        buildHomeNetworkActivityState(
            appState = appState,
            traffic = trafficPresentation.traffic,
            networkSamples = trafficPresentation.samples,
        )
    }
    val latestAppState = rememberUpdatedState(appState)
    val latestControllerState = rememberUpdatedState(controllerState)

    val modeFailedMessage = stringResource(R.string.home_mode_change_failed)

    fun changeMode(mode: Int) {
        if (serviceControl.busy) return
        val stateSnapshot = latestAppState.value
        val modeChange = buildHomeModeChange(
            appState = stateSnapshot,
            currentMode = latestControllerState.value.mihomoMode,
            requestedMode = mode,
        ) ?: return
        val previousMode = stateSnapshot.mihomoMode
        if (modeChange.persistSelection) {
            updateAppState { state -> state.copy(mihomoMode = mode) }
        }
        if (modeChange.patchRuntime) {
            serviceControl.modeOperationInProgress = true
            services.appScope.launch {
                try {
                    services.mihomoRuntime.patchMode(
                        modeChange.runtimeAppState,
                        modeChange.runtimeAppState.mihomoModeName(),
                    )
                        .onFailure { error ->
                            if (modeChange.persistSelection) {
                                updateAppState { state ->
                                    if (state.mihomoMode == mode) state.copy(mihomoMode = previousMode) else state
                                }
                            }
                            services.tipNotifier.showError(error, modeFailedMessage)
                        }
                } finally {
                    serviceControl.modeOperationInProgress = false
                }
            }
        }
    }

    AsteriskScaffold(
        topBar = {
            AsteriskTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val listPadding = pageListPadding(contentPadding, bottomExtra = 24.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = listPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("controller") {
                HomeControllerCard(
                    controllerState = controllerState,
                    operationInProgress = operationInProgress,
                    onModeSelected = ::changeMode,
                    onOpenConfiguration = { mainDestinationState?.select(MainDestination.Configurations) },
                    onOpenNode = { mainDestinationState?.select(MainDestination.Proxies) },
                )
            }
            item("network_activity") {
                NetworkActivityCard(
                    networkActivityState = networkActivityState,
                    chartStyle = appState.networkActivityStyle,
                )
            }
            item("monitoring_row_one") {
                Row(
                    modifier = HomeContentModifier.height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MonitoringEntryCard(
                        title = stringResource(R.string.home_monitor_resource),
                        icon = Icons.Rounded.Memory,
                        prominent = true,
                        onClick = { navigator.push(AppRoute.ResourceMonitor) },
                        modifier = Modifier.weight(1f),
                    ) {
                        TileInlineMetric(
                            label = stringResource(R.string.home_resource_cpu_label),
                            value = homeResourceCpuText(monitoringOverviewState),
                        )
                        Spacer(Modifier.height(4.dp))
                        TileInlineMetric(
                            label = stringResource(R.string.home_resource_memory_label),
                            value = homeResourceMemoryText(monitoringOverviewState),
                        )
                    }
                    MonitoringEntryCard(
                        title = stringResource(R.string.home_monitor_connections),
                        icon = Icons.Rounded.Lan,
                        onClick = { navigator.push(AppRoute.ConnectionsMonitor) },
                        modifier = Modifier.weight(1f),
                    ) {
                        val count = monitoringOverviewState.activeConnectionCount
                        if (count == null) {
                            Text(
                                text = stringResource(R.string.home_value_unavailable),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFeatureSettings = "tnum",
                                    ),
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = pluralStringResource(R.plurals.home_connections_unit, count),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 5.dp),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            item("monitoring_row_two") {
                Row(
                    modifier = HomeContentModifier.height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MonitoringEntryCard(
                        title = stringResource(R.string.home_monitor_traffic),
                        icon = Icons.Rounded.DataUsage,
                        onClick = { navigator.push(AppRoute.TrafficMonitor) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.home_traffic_summary,
                                monitoringOverviewState.todayTrafficBytes.toReadableBytes(),
                            ),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFeatureSettings = "tnum",
                            ),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TrafficMetric(
                                icon = Icons.Rounded.Upload,
                                label = stringResource(R.string.home_accumulated_upload),
                                value = formatHomeRuntimeBytes(networkActivityState.accumulatedUploadBytes),
                                modifier = Modifier.weight(1f),
                            )
                            TrafficMetric(
                                icon = Icons.Rounded.Download,
                                label = stringResource(R.string.home_accumulated_download),
                                value = formatHomeRuntimeBytes(networkActivityState.accumulatedDownloadBytes),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    MonitoringEntryCard(
                        title = stringResource(R.string.home_monitor_network),
                        icon = Icons.Rounded.Public,
                        prominent = true,
                        onClick = { navigator.push(AppRoute.NetworkMonitor) },
                        modifier = Modifier.weight(1f),
                    ) {
                        val (ipv4, ipv6) = homeNetworkValues(monitoringOverviewState)
                        TileStackedMetric(label = stringResource(R.string.common_ipv4), value = ipv4)
                        Spacer(Modifier.height(4.dp))
                        TileStackedMetric(label = stringResource(R.string.common_ipv6), value = ipv6)
                    }
                }
            }
            item("subscription") {
                SubscriptionSummaryCard(
                    summary = controllerState.subscription,
                    providerUsageState = providerUsageState,
                    providerUsagePending = providerUsagePending,
                    onProviderUsageRetry = {
                        services.mihomoProviderUsage.refresh(appState)
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeControllerCard(
    controllerState: HomeControllerState,
    operationInProgress: Boolean,
    onModeSelected: (Int) -> Unit,
    onOpenConfiguration: () -> Unit,
    onOpenNode: () -> Unit,
) {
    val uptime = rememberServiceUptimeText()
    // The runtime line keeps its slot when the service is down, so the status
    // card never changes height between running and stopped states.
    val runtimeText = uptime ?: HomeServiceUptimePlaceholder
    val serviceEnabled = controllerState.serviceStatus == HomeServiceStatus.Enabled
    // Stopped uses the error scheme so the hero reads as "needs attention".
    val heroContainer = if (serviceEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val heroContent = if (serviceEnabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onError
    }
    // The secondary layer is the base card: the accent card sits inside it and
    // the strip below that inner card is the "chin" holding the rows and the mode switch.
    Surface(
        modifier = HomeContentModifier,
        shape = RoundedCornerShape(HomeChinOuterRadius),
        color = homeChinBaseColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HomeChinInset),
        ) {
            AsteriskFocusSurface(
                title = if (serviceEnabled) {
                    stringResource(R.string.home_service_enabled)
                } else {
                    stringResource(R.string.home_service_disabled)
                },
                modifier = Modifier.fillMaxWidth(),
                density = FocusDensity.Large,
                tone = homeFocusTone(controllerState.serviceStatus),
                // The runtime sits right under the service status line.
                summary = runtimeText,
                // Monospace keeps the --:--:-- placeholder exactly as wide as the
                // running clock, so the card never resizes when the service starts.
                summaryStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                summaryContentDescription = uptime
                    ?.let { value -> stringResource(R.string.home_service_uptime, value) }
                    ?: stringResource(
                        if (serviceEnabled) R.string.home_service_enabled else R.string.home_service_disabled,
                    ),
                stateIcon = if (serviceEnabled) Icons.Rounded.VpnLock else Icons.Rounded.PublicOff,
                // The inner card is the accent one, matching the accent colour
                // picked in Settings > Personalization.
                containerColor = heroContainer,
                contentColor = heroContent,
                stateIconTint = heroContent,
                // Inset by exactly the radius difference, so the gap to the
                // base card stays even along the straight edges and around the
                // corner arcs alike.
                shapeOverride = RoundedCornerShape(HomeChinInnerRadius),
                primaryAction = {
                    ServiceModeChip(
                        modeLabel = runModeLabel(controllerState.runMode),
                        // On the error card a translucent chip washes out, so the
                        // tag inverts instead: onError fill, error-coloured text.
                        containerColor = if (serviceEnabled) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onError
                        },
                        contentColor = if (serviceEnabled) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                },
                forcePrimaryActionStacked = shouldStackHomeServiceRuntimeSummary(),
            )
            controllerState.primaryRows
                .filter { row -> row.kind == HomePrimaryRowKind.Node }
                .forEach { row ->
                    HomeCurrentNodeRow(
                        nodeName = row.value,
                        mihomoMode = controllerState.mihomoMode,
                        serviceEnabled = serviceEnabled,
                        onOpenProxies = onOpenNode,
                    )
                }
            controllerState.primaryRows
                .filter { row -> row.kind == HomePrimaryRowKind.Configuration }
                .forEach { row ->
                    HomeChinRow(
                        icon = Icons.Rounded.Description,
                        title = stringResource(R.string.home_current_profile),
                        value = row.value ?: stringResource(R.string.mihomo_dashboard_no_profile),
                        onClick = onOpenConfiguration,
                    )
                }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Same side edges as the accent card, so every layer lines
                    // up; the strip only adds the vertical breathing room.
                    .padding(top = HomeChinStripPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AsteriskSegmentedControl(
                    items = homeModeOptions().map { option ->
                        AsteriskSegmentItem(value = option.mode, label = option.label)
                    },
                    selectedValue = controllerState.mihomoMode,
                    onSelected = onModeSelected,
                    enabled = !controllerState.mihomoModeReadOnly && !operationInProgress,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    textStyle = MaterialTheme.typography.labelMedium,
                )
                if (controllerState.mihomoModeReadOnly) {
                    Text(
                        text = stringResource(R.string.settings_value_from_yaml),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** The slice of the runtime the current-node row reads; it changes only with the proxy snapshot or a test. */
private data class HomeNodeRuntime(
    val running: Boolean = false,
    val proxies: MihomoProxiesState = MihomoProxiesState(),
    val delayTestingTarget: MihomoDelayTarget? = null,
)

private fun MihomoRuntimeRepository.homeNodeRuntimeSnapshot() = state.value.toHomeNodeRuntime()

private fun MihomoRuntimeState.toHomeNodeRuntime() = HomeNodeRuntime(
    running = running,
    proxies = proxies,
    delayTestingTarget = delayTestingTarget,
)

/**
 * Current proxy: caption + name, no surface of its own, opening the proxy tab. Kept apart from the
 * hero so a proxy snapshot or a delay test recomposes only this row. The chip re-tests the node the
 * selection resolves to; Direct mode and the built-in outbounds have nothing to test.
 */
@Composable
private fun HomeCurrentNodeRow(
    nodeName: String?,
    mihomoMode: Int,
    serviceEnabled: Boolean,
    onOpenProxies: () -> Unit,
) {
    val services = LocalAppServices.current
    val mihomoRuntime = services.mihomoRuntime
    // The unsampled flow pushes a traffic sample every second; map it down first.
    val runtime by remember(mihomoRuntime) {
        mihomoRuntime.state
            .map { state -> state.toHomeNodeRuntime() }
            .distinctUntilChanged()
    }.collectAsState(initial = mihomoRuntime.homeNodeRuntimeSnapshot())
    val appState by LocalAppStateStore.current.collectAppState()
    val latestAppState = rememberUpdatedState(appState)
    val activeNode = remember(runtime.proxies) { runtime.proxies.activeNodeId() }
    val testableNode = activeNode?.takeIf { id ->
        serviceEnabled && runtime.running && mihomoMode != MihomoModeDirect &&
            !(id.providerName == null && id.name.uppercase() in HomeBuiltInOutbounds)
    }
    HomeChinRow(
        icon = Icons.Rounded.Dns,
        title = stringResource(R.string.home_current_proxy),
        value = nodeName ?: stringResource(R.string.home_no_proxy),
        onClick = onOpenProxies,
    ) {
        if (testableNode != null) {
            val node = runtime.proxies.node(testableNode)
            val testing = when (val target = runtime.delayTestingTarget) {
                is MihomoDelayTarget.Node -> target.id == testableNode
                is MihomoDelayTarget.Group -> runtime.proxies.groups.any { group ->
                    group.name == target.name && testableNode in group.all
                }
                is MihomoDelayTarget.Provider -> target.name == testableNode.providerName
                null -> false
            }
            NodeDelayChip(
                testing = testing,
                delay = node.delay,
                delayStatus = node.delayStatus,
                onClick = {
                    // A user test preempts a background probe; it never stacks on another user test.
                    if (mihomoRuntime.state.value.canStartUserDelayTest) {
                        services.appScope.launch {
                            mihomoRuntime.testProxyDelay(latestAppState.value, testableNode)
                        }
                    }
                },
            )
            Spacer(Modifier.width(6.dp))
        }
    }
}

/** One row of the chin: glyph, caption above a value, an optional trailing slot and a chevron. */
@Composable
private fun HomeChinRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Latency readout for the active node: tap re-tests it. */
@Composable
private fun NodeDelayChip(
    testing: Boolean,
    delay: Int?,
    delayStatus: MihomoDelayStatus?,
    onClick: () -> Unit,
) {
    // Same reading as the proxy list, so both show one status for the node.
    val text = when {
        testing -> stringResource(R.string.home_node_delay_testing)
        delayStatus == MihomoDelayStatus.Timeout -> stringResource(R.string.mihomo_proxies_delay_timeout)
        delayStatus == MihomoDelayStatus.Failed -> stringResource(R.string.mihomo_proxies_delay_failed)
        delayStatus == null || delay == null -> stringResource(R.string.home_node_delay_unknown)
        else -> stringResource(R.string.home_node_delay_value, delay)
    }
    val tint = if (testing) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        mihomoDelayTint(delay, delayStatus)
    }
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun homeChinBaseColor(): Color = lerp(
    MaterialTheme.colorScheme.secondaryContainer,
    MaterialTheme.colorScheme.surface,
    0.55f,
)

@Composable
private fun NetworkActivityCard(
    networkActivityState: HomeNetworkActivityState,
    chartStyle: Int,
) {
    // The card wraps its content and only the plot has a fixed height, so a large font scale grows
    // the card instead of squeezing the chart away.
    AsteriskPageCard(modifier = HomeContentModifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = stringResource(R.string.home_network_activity),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    // Never let the caption stack one glyph per line when the rates get wide.
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                // Live speeds keep their heading size and sit side by side in the
                // card's top-right corner; the plot below spans the full width. The group may
                // take more room than the caption, and the values shrink to fit instead of
                // ellipsizing or pushing the caption into a single-glyph column.
                Row(
                    modifier = Modifier.weight(1.6f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    NetworkSpeedValue(
                        icon = Icons.Rounded.ArrowUpward,
                        iconDescription = stringResource(R.string.monitor_connections_upload),
                        tint = MaterialTheme.colorScheme.tertiary,
                        value = formatHomeSpeed(networkActivityState.uploadBytesPerSecond),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(NetworkSpeedPairSpacing))
                    NetworkSpeedValue(
                        icon = Icons.Rounded.ArrowDownward,
                        iconDescription = stringResource(R.string.monitor_connections_download),
                        tint = MaterialTheme.colorScheme.primary,
                        value = formatHomeSpeed(networkActivityState.downloadBytesPerSecond),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (networkActivityState.hasNetworkSamples) {
                val chartModifier = Modifier
                    .fillMaxWidth()
                    .height(NetworkActivityChartHeight)
                if (chartStyle == NetworkActivityStyleLines) {
                    NetworkActivityLineChart(
                        state = networkActivityState,
                        modifier = chartModifier,
                    )
                } else {
                    NetworkActivityChart(
                        state = networkActivityState,
                        modifier = chartModifier,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NetworkActivityChartHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.home_no_network_activity),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * Line variant of the network activity chart: download and upload are drawn as two smooth curves on
 * one shared scale (each taller than the other by exactly their ratio, since both are bytes per
 * second), and each curve carries a decorative gradient down to the baseline.
 */
@Composable
private fun NetworkActivityLineChart(
    state: HomeNetworkActivityState,
    modifier: Modifier = Modifier,
) {
    val samples = state.networkSamples
    val bands = remember(samples) { buildNetworkActivityLineBands(samples, NetworkActivitySlotCount) }
    val downloadColor = MaterialTheme.colorScheme.primary
    val uploadColor = MaterialTheme.colorScheme.tertiary
    Column(modifier = modifier) {
        // Both curves share one scale, so the axis carries one label: the top of that scale.
        // It gets a thin row of its own so the plot keeps the full card width.
        NetworkActivityAxisLabel(
            scaleBytes = bands.scaleBytes,
            modifier = Modifier.align(Alignment.End),
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (bands.sampleCount == 0) return@Canvas
            val strokeWidth = NetworkActivityAreaStrokeWidth.toPx()
            drawSmoothAreaLine(values = bands.downloadEdge, color = downloadColor, strokeWidth = strokeWidth)
            drawSmoothAreaLine(values = bands.uploadEdge, color = uploadColor, strokeWidth = strokeWidth)
        }
    }
}

@Composable
private fun NetworkSpeedValue(
    icon: ImageVector,
    iconDescription: String,
    tint: Color,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            tint = tint,
            modifier = Modifier.size(NetworkSpeedIconSize),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            // Tabular figures keep the two rates from jittering as digits change.
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 22.sp),
        )
    }
}

@Composable
private fun NetworkActivityChart(
    state: HomeNetworkActivityState,
    modifier: Modifier = Modifier,
) {
    val samples = state.networkSamples
    val waveform = remember(samples) { buildNetworkActivityWaveform(samples, NetworkActivitySlotCount) }
    val downloadRamp = rememberDottedWaveformRamp(MaterialTheme.colorScheme.primary)
    val uploadRamp = rememberDottedWaveformRamp(MaterialTheme.colorScheme.tertiary)
    Column(modifier = modifier) {
        NetworkActivityAxisLabel(
            scaleBytes = waveform.downloadScaleBytes,
            modifier = Modifier.align(Alignment.End),
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (waveform.sampleCount == 0) return@Canvas
            val dotRadius = DottedWaveformDotRadius.toPx()
            drawDottedWaveform(
                amplitudes = waveform.download,
                slotCount = NetworkActivitySlotCount,
                dotRadius = dotRadius,
                direction = NetworkActivityDownloadDirection,
                ramp = downloadRamp,
                verticalDotPitch = dotRadius * NetworkActivityVerticalPitchScale,
            )
            drawDottedWaveform(
                amplitudes = waveform.upload,
                slotCount = NetworkActivitySlotCount,
                dotRadius = dotRadius,
                direction = NetworkActivityUploadDirection,
                ramp = uploadRamp,
                verticalDotPitch = dotRadius * NetworkActivityVerticalPitchScale,
            )
        }
        // The far edge of each half owns its own scale, so a slow upload never
        // inherits the download range.
        NetworkActivityAxisLabel(
            scaleBytes = waveform.uploadScaleBytes,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun NetworkActivityAxisLabel(
    scaleBytes: Long,
    modifier: Modifier = Modifier,
) {
    if (scaleBytes <= 0L) return
    Text(
        text = formatHomeSpeed(scaleBytes),
        modifier = modifier.heightIn(min = NetworkActivityAxisLabelHeight),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun SubscriptionSummaryCard(
    summary: HomeSubscriptionSummary?,
    providerUsageState: MihomoProviderUsageLoadState,
    providerUsagePending: Boolean,
    onProviderUsageRetry: () -> Unit,
) {
    val content = buildHomeSubscriptionContent(
        primary = summary,
        providers = providerUsageState,
        providersPending = providerUsagePending,
    )
    AsteriskPageCard(modifier = HomeContentModifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.home_subscription_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (content.showEmpty) {
                Text(
                    text = stringResource(R.string.home_no_subscription),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (content.showPrimary) {
                requireNotNull(summary)
                SubscriptionUsage(summary)
            }
            if (content.showProviders) {
                if (content.showProviderDivider) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                HomeProviderUsageSummary(
                    state = providerUsageState,
                    providerItems = content.providerItems,
                    onRetry = onProviderUsageRetry,
                )
            }
        }
    }
}

@Composable
private fun HomeProviderUsageSummary(
    state: MihomoProviderUsageLoadState,
    providerItems: List<MihomoProviderUsageItem>,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.mihomo_profile_provider_usage_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when (state) {
            MihomoProviderUsageLoadState.Hidden -> Unit
            MihomoProviderUsageLoadState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.mihomo_profile_provider_usage_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            MihomoProviderUsageLoadState.RequiresProxyRunning -> Text(
                text = stringResource(R.string.mihomo_profile_provider_usage_requires_proxy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MihomoProviderUsageLoadState.Failed -> Surface(
                onClick = onRetry,
                color = Color.Transparent,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.mihomo_profile_provider_usage_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.mihomo_profile_provider_usage_retry),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            is MihomoProviderUsageLoadState.Ready -> HomeProviderUsageReady(providerItems)
        }
    }
}

@Composable
private fun HomeProviderUsageReady(items: List<MihomoProviderUsageItem>) {
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.mihomo_proxy_providers_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            HomeProviderUsageItemRow(item)
        }
    }
}

@Composable
private fun HomeProviderUsageItemRow(item: MihomoProviderUsageItem) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        when (item.kind) {
            MihomoProviderUsageKind.Metered -> {
                SubscriptionUsage(requireNotNull(item.toHomeSubscriptionSummaryOrNull()))
            }

            MihomoProviderUsageKind.Unlimited -> {
                Text(
                    text = stringResource(R.string.mihomo_provider_traffic_unlimited),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                SubscriptionExpiry(item.expireAtSeconds)
            }

            MihomoProviderUsageKind.Missing -> ProviderUsageStatus(
                text = stringResource(R.string.mihomo_profile_provider_usage_missing),
            )

            MihomoProviderUsageKind.Unavailable -> ProviderUsageStatus(
                text = stringResource(R.string.mihomo_profile_provider_usage_unavailable),
                error = true,
            )
        }
    }
}

@Composable
private fun ProviderUsageStatus(
    text: String,
    error: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SubscriptionUsage(summary: HomeSubscriptionSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SubscriptionMetric(
                stringResource(R.string.home_subscription_used),
                summary.usedBytes.toReadableBytes(),
            )
            SubscriptionMetric(
                stringResource(R.string.home_subscription_total),
                summary.totalBytes.toReadableBytes(),
            )
            SubscriptionMetric(
                stringResource(R.string.home_subscription_remaining),
                summary.remainingBytes.toReadableBytes(),
            )
        }
        SubscriptionExpiry(summary.expireAtSeconds)
    }
}

@Composable
private fun SubscriptionExpiry(expireAtSeconds: Long) {
    Text(
        text = stringResource(
            R.string.home_subscription_expiry,
            formatExpiry(expireAtSeconds),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SubscriptionMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MonitoringEntryCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    AsteriskExpressiveCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = HomeMonitoringTileHeight),
        role = if (prominent) ExpressiveShapeRole.GroupLarge else ExpressiveShapeRole.ContentCard,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Caption-sized title next to its glyph: the numbers below are what the tile is for.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Label above its value, e.g. the two addresses on the network tile. */
@Composable
private fun TileStackedMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Label beside its value, e.g. CPU and memory on the resource tile. */
@Composable
private fun TileInlineMetric(label: String, value: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrafficMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label $value"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp),
        )
    }
}

@Composable
private fun runModeLabel(runMode: Int): String {
    return stringResource(
        when (runMode) {
            RunModeTproxy -> R.string.settings_run_mode_tproxy
            RunModeTun -> R.string.settings_run_mode_tun
            RunModeTun2Socks -> R.string.settings_run_mode_tun2socks
            RunModeBpf2Socks -> R.string.settings_run_mode_bpf2socks
            else -> R.string.settings_run_mode_vpn_service
        },
    )
}

@Composable
private fun homeModeOptions(): List<HomeModeOption> {
    return listOf(
        HomeModeOption(MihomoModeRule, stringResource(R.string.mihomo_mode_rule)),
        HomeModeOption(MihomoModeGlobal, stringResource(R.string.mihomo_mode_global)),
        HomeModeOption(MihomoModeDirect, stringResource(R.string.mihomo_mode_direct)),
    )
}

@Composable
private fun homeResourceCpuText(monitoringState: HomeMonitoringOverviewState): String {
    if (!monitoringState.serviceRunning) return stringResource(R.string.home_value_unavailable)
    return monitoringState.resourceCpuPercent
        ?.let { value -> "%.1f%%".format(value) }
        ?: stringResource(R.string.home_value_unavailable)
}

@Composable
private fun homeResourceMemoryText(monitoringState: HomeMonitoringOverviewState): String {
    if (!monitoringState.serviceRunning) return stringResource(R.string.home_value_unavailable)
    return monitoringState.resourceMemoryBytes
        ?.toReadableBytes()
        ?: stringResource(R.string.home_value_unavailable)
}

@Composable
private fun homeNetworkValues(homeState: HomeMonitoringOverviewState): Pair<String, String> {
    val unavailable = stringResource(R.string.home_value_unavailable)
    val ipv4 = homeState.networkRows
        .firstOrNull { row -> row.kind == HomeNetworkRowKind.Ipv4 }
        ?.value ?: unavailable
    val ipv6 = homeState.networkRows
        .firstOrNull { row -> row.kind == HomeNetworkRowKind.Ipv6 }
        ?.value ?: unavailable
    return ipv4 to ipv6
}

private fun formatHomeSpeed(bytes: Long?): String {
    return if (bytes == null) formatHomeRuntimeBytes(null) else "${formatHomeRuntimeBytes(bytes)}/s"
}

private fun formatExpiry(expireAtSeconds: Long): String {
    if (expireAtSeconds <= 0L) return HomeUnavailableValue
    return (expireAtSeconds * 1_000L).toReadableDateOrDash()
}

private data class HomeModeOption(
    val mode: Int,
    val label: String,
)

private const val HomeUiSampleIntervalMillis = 1_000L

private const val HomeServiceUptimePlaceholder = "--:--:--"

// Mihomo's built-in outbounds; the core has no latency to report for them.
private val HomeBuiltInOutbounds = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE")

// The x axis keeps this many fixed columns no matter how many samples are buffered; the newest
// sample sits in the rightmost column. It mirrors the sample limit of the home traffic state.
private const val NetworkActivitySlotCount = HomeNetworkSampleLimit

// The lattice pitch doubles as the vertical step, so a wide card would only get a few rows; this
// keeps rows at 2.6 dot diameters instead, which is fine enough to read a burst.
private const val NetworkActivityVerticalPitchScale = 2.6f

private val HomeMonitoringTileHeight = 132.dp

private val NetworkActivityChartHeight = 120.dp

/** One thin row per axis label; the plot keeps everything else. */
private val NetworkActivityAxisLabelHeight = 14.dp
private val NetworkSpeedIconSize = 18.dp
private val NetworkActivityAreaStrokeWidth = 2.dp
private val NetworkSpeedPairSpacing = 14.dp

// Download grows upwards from the middle, upload grows downwards; both share the same x positions.
private const val NetworkActivityDownloadDirection = -1f
private const val NetworkActivityUploadDirection = 1f

private val HomeContentModifier = Modifier
    .fillMaxWidth()
    .widthIn(max = 840.dp)

// The secondary base card, the accent card nested inside it, and the inset
// that leaves the chin strip visible below the inner card.
private val HomeChinOuterRadius = 28.dp
private val HomeChinInnerRadius = 22.dp
// Equal to outer - inner radius, so the ring between the two cards is even
// along the edges and around the rounded corners.
private val HomeChinInset = 6.dp
private val HomeChinStripPadding = 6.dp

@Composable
private fun shouldStackHomeServiceRuntimeSummary(): Boolean =
    LocalWindowInfo.current.containerSize.width / LocalDensity.current.density < 360f

@Composable
private fun rememberServiceUptimeText(): String? {
    val appState by LocalAppStateStore.current.collectAppState()
    val services = LocalAppServices.current
    val uptimeMillis by produceState<Long?>(null, appState.proxyRunning, appState.runMode) {
        value = null
        if (!appState.proxyRunning) return@produceState
        val expectedSource = if (appState.runMode.isRootRunMode()) {
            ProcessStatsSourceKind.CoreProcess
        } else {
            ProcessStatsSourceKind.VpnProcess
        }
        while (true) {
            val resource = services.monitoring.state.value.resource
            value = resource.uptimeMillis
                .takeIf { resource.source == expectedSource }
                ?.let { it + (SystemClock.elapsedRealtime() - resource.uptimeMeasuredAtElapsedMillis).coerceAtLeast(0L) }
            delay(1_000L.milliseconds)
        }
    }
    if (!appState.proxyRunning) return null
    return formatHomeServiceUptime(uptimeMillis)
}

@Composable
private fun ServiceModeChip(
    modeLabel: String,
    containerColor: Color,
    contentColor: Color,
) {
    // Muted chip: secondaryContainer on onSecondaryContainer.
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = modeLabel,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
