// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.mihomo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.AppState
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalMainDestinationState
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.withMihomoRestartApplied
import app.modes.MihomoModeDirect
import app.modes.MihomoModeGlobal
import app.modes.MihomoModeRule
import app.modes.RunModeBpf2Socks
import app.modes.RunModeTproxy
import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import app.navigation.MainDestination
import app.navigation.Route as AppRoute
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.theme.AsteriskMotion
import engine.mihomo.MihomoProfileEmptyErrorMessage
import engine.mihomo.MihomoProfileMissingErrorMessage
import engine.mihomo.mihomoModeName
import engine.mihomo.selectedMihomoProfileOrNull
import engine.mihomo.raw.MihomoRawConfigParser
import engine.proxy.ProxyServiceResult
import features.home.HomeDisplayState
import features.home.HomeNetworkRowKind
import features.home.HomePrimaryRowKind
import features.home.HomeServiceStatus
import features.home.buildHomeModeChange
import features.home.buildHomeDisplayState
import features.home.formatHomeRuntimeBytes
import features.home.homeFocusTone
import features.home.toMihomoModeOrNull
import features.monitoring.MonitoringIntent
import features.monitoring.ObserveMonitoring
import features.monitoring.MonitoringState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import utils.toReadableBytes
import java.text.DateFormat
import java.util.Date
import ui.theme.AsteriskShapeTokens
import ui.components.AsteriskPageCard
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskFocusSurface
import ui.components.AsteriskSegmentItem
import ui.components.AsteriskSegmentedControl
import ui.theme.ExpressiveShapeRole
import ui.theme.FocusDensity

@Composable
fun MihomoDashboardPage(
    padding: PaddingValues,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val runtimeState by services.mihomoRuntime.state.collectAsState()
    val monitoringState by services.monitoring.state.collectAsState()
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val mainDestinationState = LocalMainDestinationState.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    ObserveMonitoring(MonitoringIntent.Home)
    var operationInProgress by rememberSaveable { mutableStateOf(false) }
    val selectedProfile = appState.selectedMihomoProfileOrNull()
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
    val homeState = remember(appState, runtimeState, rawMihomoMode, monitoringState) {
        buildHomeDisplayState(appState, runtimeState, rawMihomoMode, monitoringState)
    }
    val latestAppState = rememberUpdatedState(appState)
    val latestHomeState = rememberUpdatedState(homeState)

    val startFailedMessage = stringResource(R.string.mihomo_dashboard_start_failed)
    val startNoConfigurationMessage = stringResource(R.string.mihomo_dashboard_start_no_configuration)
    val startEmptyConfigurationMessage = stringResource(R.string.mihomo_dashboard_start_empty_configuration)
    val stopFailedMessage = stringResource(R.string.mihomo_dashboard_stop_failed)
    val serviceStartedMessage = stringResource(R.string.proxy_service_started)
    val serviceStoppedMessage = stringResource(R.string.proxy_service_stopped)
    val modeFailedMessage = stringResource(R.string.home_mode_change_failed)

    suspend fun handleProxyServiceResult(result: ProxyServiceResult, wasRunning: Boolean) {
        when (result) {
            is ProxyServiceResult.Success -> {
                updateAppState { state ->
                    state.copy(
                        proxyRunning = result.proxyRunning,
                        localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                        mihomoControlPort = result.appState?.mihomoControlPort ?: state.mihomoControlPort,
                    ).withMihomoRestartApplied()
                }
                services.tipNotifier.show(if (result.proxyRunning) serviceStartedMessage else serviceStoppedMessage)
            }

            is ProxyServiceResult.Failed -> {
                updateAppState { state -> state.copy(proxyRunning = false) }
                val localizedStartMessage = if (wasRunning) {
                    null
                } else {
                    result.error.mihomoProfileStartFailureMessage(
                        missingConfigurationMessage = startNoConfigurationMessage,
                        emptyConfigurationMessage = startEmptyConfigurationMessage,
                    )
                }
                if (localizedStartMessage != null) {
                    services.tipNotifier.show(localizedStartMessage)
                } else {
                    services.tipNotifier.showError(
                        result.error,
                        if (wasRunning) stopFailedMessage else startFailedMessage,
                    )
                }
            }
        }
    }

    fun toggleService() {
        if (operationInProgress) return
        val stateSnapshot = appState
        val wasRunning = stateSnapshot.proxyRunning
        operationInProgress = true
        val operationJob = services.appScope.launch {
            handleProxyServiceResult(services.proxyServiceUseCase.toggle(stateSnapshot), wasRunning)
        }
        scope.launch {
            try {
                operationJob.join()
            } finally {
                operationInProgress = false
            }
        }
    }

    fun changeMode(mode: Int) {
        val stateSnapshot = latestAppState.value
        val modeChange = buildHomeModeChange(
            appState = stateSnapshot,
            currentMode = latestHomeState.value.mihomoMode,
            requestedMode = mode,
        ) ?: return
        val previousMode = stateSnapshot.mihomoMode
        if (modeChange.persistSelection) {
            updateAppState { state -> state.copy(mihomoMode = mode) }
        }
        if (modeChange.patchRuntime) {
            scope.launch {
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
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
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
                    appState = appState,
                    homeState = homeState,
                    operationInProgress = operationInProgress,
                    onToggleService = ::toggleService,
                    onModeSelected = ::changeMode,
                    onOpenConfiguration = { navigator.push(AppRoute.MihomoProfileList) },
                    onOpenNode = { mainDestinationState?.select(MainDestination.Proxies) },
                )
            }
            item("network_activity") {
                NetworkActivityCard(homeState)
            }
            item("monitoring_row_one") {
                Row(
                    modifier = HomeContentModifier,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MonitoringEntryCard(
                        title = stringResource(R.string.home_monitor_resource),
                        summary = homeResourceSummary(monitoringState),
                        icon = Icons.Rounded.Memory,
                        prominent = true,
                        onClick = { navigator.push(AppRoute.ResourceMonitor) },
                        modifier = Modifier.weight(1f),
                    )
                    MonitoringEntryCard(
                        title = stringResource(R.string.home_monitor_connections),
                        summary = monitoringState.connections.activeCount?.let { count ->
                            pluralStringResource(R.plurals.home_connections_summary, count, count)
                        } ?: stringResource(R.string.home_value_unavailable),
                        icon = Icons.Rounded.Lan,
                        onClick = { navigator.push(AppRoute.ConnectionsMonitor) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item("monitoring_row_two") {
                Row(
                    modifier = HomeContentModifier,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MonitoringEntryCard(
                        title = stringResource(R.string.home_monitor_traffic),
                        summary = stringResource(
                            R.string.home_traffic_summary,
                            monitoringState.traffic.today.total.toReadableBytes(),
                        ),
                        icon = Icons.Rounded.DataUsage,
                        onClick = { navigator.push(AppRoute.TrafficMonitor) },
                        modifier = Modifier.weight(1f),
                    )
                    MonitoringEntryCard(
                        title = stringResource(R.string.home_monitor_network),
                        summary = homeNetworkSummary(homeState),
                        icon = Icons.Rounded.Public,
                        prominent = true,
                        onClick = { navigator.push(AppRoute.NetworkMonitor) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item("subscription") {
                SubscriptionSummaryCard(homeState)
            }
        }
    }
}

@Composable
private fun HomeControllerCard(
    appState: AppState,
    homeState: HomeDisplayState,
    operationInProgress: Boolean,
    onToggleService: () -> Unit,
    onModeSelected: (Int) -> Unit,
    onOpenConfiguration: () -> Unit,
    onOpenNode: () -> Unit,
) {
    val serviceMotion = AsteriskMotion.fastEffects<Float>()
    val serviceSwitchAlpha by animateFloatAsState(
        targetValue = if (operationInProgress) 0f else 1f,
        animationSpec = serviceMotion,
        label = "home-service-switch-alpha",
    )
    AsteriskFocusSurface(
        title = if (homeState.serviceStatus == HomeServiceStatus.Enabled) {
            stringResource(R.string.home_service_enabled)
        } else {
            stringResource(R.string.home_service_disabled)
        },
        modifier = HomeContentModifier,
        density = FocusDensity.Large,
        tone = homeFocusTone(homeState.serviceStatus),
        summary = runModeLabel(homeState.runMode),
        stateIcon = Icons.Rounded.PowerSettingsNew,
        metrics = {
            HomeFocusMetric(
                icon = Icons.Rounded.Upload,
                label = stringResource(R.string.home_accumulated_upload),
                value = formatHomeRuntimeBytes(homeState.accumulatedUploadBytes),
                modifier = Modifier.weight(1f),
            )
            HomeFocusMetric(
                icon = Icons.Rounded.Download,
                label = stringResource(R.string.home_accumulated_download),
                value = formatHomeRuntimeBytes(homeState.accumulatedDownloadBytes),
                modifier = Modifier.weight(1f),
            )
        },
        primaryAction = {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Switch(
                    checked = homeState.serviceStatus == HomeServiceStatus.Enabled,
                    onCheckedChange = { onToggleService() },
                    modifier = Modifier.alpha(serviceSwitchAlpha),
                    enabled = !operationInProgress,
                )
                AnimatedVisibility(
                    visible = operationInProgress,
                    enter = fadeIn(animationSpec = serviceMotion),
                    exit = fadeOut(animationSpec = serviceMotion),
                    label = "home-service-loading",
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        },
    ) {
        Box(modifier = Modifier.offset(y = HomeControllerContentOffset)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (
                    appState.proxyRunning &&
                    appState.pendingMihomoRestartProfileId == appState.selectedMihomoProfileId
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = onOpenConfiguration,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                text = stringResource(R.string.mihomo_configuration_restart_required),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        }
                    }
                }

                homeState.primaryRows
                    .filter { row -> row.kind == HomePrimaryRowKind.Node }
                    .forEach { row ->
                        HomePrimaryRow(
                            icon = Icons.Rounded.Route,
                            label = stringResource(R.string.home_current_node),
                            value = row.value ?: stringResource(R.string.home_no_node),
                            onClick = onOpenNode,
                        )
                    }

                homeState.primaryRows
                    .filter { row -> row.kind == HomePrimaryRowKind.Configuration }
                    .forEach { row ->
                        HomePrimaryRow(
                            icon = Icons.Rounded.Description,
                            label = stringResource(R.string.home_current_configuration),
                            value = row.value ?: stringResource(R.string.mihomo_dashboard_no_configuration),
                            onClick = onOpenConfiguration,
                        )
                    }

                AsteriskSegmentedControl(
                    items = homeModeOptions().map { option ->
                        AsteriskSegmentItem(value = option.mode, label = option.label)
                    },
                    selectedValue = homeState.mihomoMode,
                    onSelected = onModeSelected,
                    enabled = !homeState.mihomoModeReadOnly,
                )
                if (homeState.mihomoModeReadOnly) {
                    Text(
                        text = stringResource(R.string.settings_value_from_yaml),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFocusMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    HomeControllerItemContent(
        modifier = modifier.offset(y = HomeControllerContentOffset),
        icon = icon,
        iconSize = HomeControllerTrafficIconSize,
        iconOffsetY = HomeControllerTrafficIconOffsetY,
        label = label,
        value = value,
    )
}

@Composable
private fun HomePrimaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.medium,
    ) {
        HomeControllerItemContent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            icon = icon,
            label = label,
            value = value,
            trailingIcon = Icons.Rounded.ChevronRight,
        )
    }
}

@Composable
private fun HomeControllerItemContent(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = HomeControllerItemIconSize,
    iconOffsetY: Dp = 0.dp,
    trailingIcon: ImageVector? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(HomeControllerItemIconSlotSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize).offset(y = iconOffsetY),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = HomeControllerItemTextSpacing),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NetworkActivityCard(homeState: HomeDisplayState) {
    AsteriskPageCard(modifier = HomeContentModifier.height(180.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_network_activity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "↑ ${formatHomeSpeed(homeState.uploadBytesPerSecond)}   ↓ ${formatHomeSpeed(homeState.downloadBytesPerSecond)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (homeState.hasNetworkSamples) {
                NetworkActivityChart(
                    state = homeState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
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

@Composable
private fun NetworkActivityChart(
    state: HomeDisplayState,
    modifier: Modifier = Modifier,
) {
    val uploadColor = MaterialTheme.colorScheme.tertiary
    val downloadColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val samples = state.networkSamples
        val maxValue = samples.maxOfOrNull { sample -> maxOf(sample.up, sample.down) }?.coerceAtLeast(1L) ?: 1L
        val baseline = size.height - 2.dp.toPx()
        drawLine(
            color = baselineColor,
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        if (samples.size < 2) return@Canvas
        val step = size.width / (samples.lastIndex.coerceAtLeast(1))
        fun point(index: Int, value: Long): Offset {
            val fraction = value.toFloat() / maxValue.toFloat()
            return Offset(index * step, baseline - fraction.coerceIn(0f, 1f) * baseline)
        }
        samples.zipWithNext().forEachIndexed { index, (first, second) ->
            drawLine(
                color = uploadColor,
                start = point(index, first.up),
                end = point(index + 1, second.up),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = downloadColor,
                start = point(index, first.down),
                end = point(index + 1, second.down),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SubscriptionSummaryCard(homeState: HomeDisplayState) {
    val summary = homeState.subscription
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
            if (summary == null) {
                Text(
                    text = stringResource(R.string.home_no_subscription),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SubscriptionMetric(stringResource(R.string.home_subscription_used), summary.usedBytes.toReadableBytes())
                    SubscriptionMetric(stringResource(R.string.home_subscription_total), summary.totalBytes.toReadableBytes())
                    SubscriptionMetric(stringResource(R.string.home_subscription_remaining), summary.remainingBytes.toReadableBytes())
                }
                Text(
                    text = stringResource(
                        R.string.home_subscription_expiry,
                        formatExpiry(summary.expireAtSeconds),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    AsteriskExpressiveCard(
        onClick = onClick,
        modifier = modifier.height(148.dp),
        role = if (prominent) ExpressiveShapeRole.GroupLarge else ExpressiveShapeRole.ContentCard,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = AsteriskShapeTokens.SmallContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
private fun homeResourceSummary(monitoringState: MonitoringState): String {
    if (!monitoringState.serviceRunning) return stringResource(R.string.home_value_unavailable)
    val cpu = monitoringState.resource.cpuPercent
        ?.let { value -> "%.1f%%".format(value) }
        ?: stringResource(R.string.home_value_unavailable)
    val memory = monitoringState.resource.memoryBytes
        ?.toReadableBytes()
        ?: stringResource(R.string.home_value_unavailable)
    return stringResource(R.string.home_resource_summary, cpu, memory)
}

@Composable
private fun homeNetworkSummary(homeState: HomeDisplayState): String {
    val ipv4 = homeState.networkRows
        .firstOrNull { row -> row.kind == HomeNetworkRowKind.Ipv4 }
        ?.value ?: "—"
    val ipv6 = homeState.networkRows
        .firstOrNull { row -> row.kind == HomeNetworkRowKind.Ipv6 }
        ?.value ?: "—"
    return "IPv4 · $ipv4\nIPv6 · $ipv6"
}

private fun formatHomeSpeed(bytes: Long?): String {
    return if (bytes == null) formatHomeRuntimeBytes(null) else "${formatHomeRuntimeBytes(bytes)}/s"
}

private fun formatExpiry(expireAtSeconds: Long): String {
    if (expireAtSeconds <= 0L) return "—"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expireAtSeconds * 1_000L))
}

private fun Throwable.mihomoProfileStartFailureMessage(
    missingConfigurationMessage: String,
    emptyConfigurationMessage: String,
): String? {
    return when (message) {
        MihomoProfileMissingErrorMessage -> missingConfigurationMessage
        MihomoProfileEmptyErrorMessage -> emptyConfigurationMessage
        else -> null
    }
}

private data class HomeModeOption(
    val mode: Int,
    val label: String,
)

private val HomeContentModifier = Modifier
    .fillMaxWidth()
    .widthIn(max = 840.dp)

private val HomeControllerItemIconSlotSize = 28.dp
private val HomeControllerItemIconSize = 24.dp
private val HomeControllerTrafficIconSize = 28.dp
private val HomeControllerTrafficIconOffsetY = 0.5.dp
private val HomeControllerContentOffset = 6.dp
private val HomeControllerItemTextSpacing = 14.dp
