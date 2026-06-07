// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.mihomo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.AppState
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalUpdateAppState
import app.MihomoProfileState
import app.R
import app.collectAppState
import app.modes.MihomoModeDirect
import app.modes.MihomoModeGlobal
import app.modes.MihomoModeRule
import engine.mihomo.MihomoProfileEmptyErrorMessage
import engine.mihomo.MihomoProfileMissingErrorMessage
import engine.mihomo.mihomoModeName
import engine.mihomo.runtime.MihomoNetworkDetectionState
import engine.mihomo.runtime.MihomoRuntimeState
import engine.mihomo.selectedMihomoProfileOrNull
import engine.proxy.ProxyServiceResult
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowListPopup
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import utils.toReadableBytes

@Composable
fun MihomoDashboardPage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val runtimeState by services.mihomoRuntime.state.collectAsState()
    val proxyServiceUseCase = services.proxyServiceUseCase
    val tipNotifier = services.tipNotifier
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var operationInProgress by rememberSaveable { mutableStateOf(false) }
    val startFailedMessage = stringResource(R.string.mihomo_dashboard_start_failed)
    val startNoConfigurationMessage = stringResource(R.string.mihomo_dashboard_start_no_configuration)
    val startEmptyConfigurationMessage = stringResource(R.string.mihomo_dashboard_start_empty_configuration)
    val stopFailedMessage = stringResource(R.string.mihomo_dashboard_stop_failed)
    val serviceStartedMessage = stringResource(R.string.proxy_service_started)
    val serviceStoppedMessage = stringResource(R.string.proxy_service_stopped)
    val modeFailedMessage = stringResource(R.string.mihomo_dashboard_mode)

    suspend fun handleProxyServiceResult(result: ProxyServiceResult, wasRunning: Boolean) {
        when (result) {
            is ProxyServiceResult.Success -> {
                updateAppState { state ->
                    state.copy(
                        proxyRunning = result.proxyRunning,
                        localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                    )
                }
                tipNotifier.show(if (result.proxyRunning) serviceStartedMessage else serviceStoppedMessage)
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
                    tipNotifier.show(localizedStartMessage)
                } else {
                    tipNotifier.showError(
                        result.error,
                        if (wasRunning) stopFailedMessage else startFailedMessage,
                    )
                }
            }
        }
    }

    fun runProxyOperation() {
        if (operationInProgress) return
        scope.launch {
            val wasRunning = appState.proxyRunning
            operationInProgress = true
            try {
                handleProxyServiceResult(proxyServiceUseCase.toggle(appState), wasRunning)
            } finally {
                operationInProgress = false
            }
        }
    }

    fun changeMode(mode: Int) {
        if (mode == appState.mihomoMode) return
        val nextState = appState.copy(mihomoMode = mode)
        updateAppState { state -> state.copy(mihomoMode = mode) }
        if (appState.proxyRunning) {
            scope.launch {
                services.mihomoRuntime.patchMode(nextState, nextState.mihomoModeName())
                    .onFailure { error -> tipNotifier.showError(error, modeFailedMessage) }
            }
        }
    }

    fun selectProfile(profileId: Int) {
        if (profileId == appState.selectedMihomoProfileId) return
        val previousState = appState
        updateAppState { state ->
            if (profileId == state.selectedMihomoProfileId) {
                state
            } else {
                state.copy(selectedMihomoProfileId = profileId)
            }
        }
        scope.launch {
            stopProxyServiceAfterProfileChange(
                appState = previousState,
                services = services,
                updateAppState = updateAppState,
                stoppedMessage = serviceStoppedMessage,
                stopFailedMessage = stopFailedMessage,
            )
        }
    }

    Scaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(R.string.mihomo_dashboard_title),
                isWideScreen = isWideScreen,
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
            extraTop = 18.dp,
        )
        val listPadding = pageListPadding(contentPadding, bottomExtra = 112.dp)

        Box {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = listPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("speed") {
                    NetworkSpeedPanel(runtimeState)
                }
                item("dashboard_grid") {
                    DashboardMosaic(
                        appState = appState,
                        runtimeState = runtimeState,
                        onModeSelected = ::changeMode,
                        onIntranetIpRefresh = services.mihomoRuntime::refreshDeviceMetrics,
                        onNetworkDetectionRefresh = services.mihomoRuntime::refreshNetworkDetection,
                        onMemoryRefresh = { services.mihomoRuntime.refreshMemory(appState) },
                        onProfileSelected = ::selectProfile,
                    )
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
            DashboardStartToolbar(
                running = appState.proxyRunning,
                operationInProgress = operationInProgress,
                onToggle = ::runProxyOperation,
                bottomPadding = contentPadding.calculateBottomPadding(),
                modifier = Modifier
                    .align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun NetworkSpeedPanel(runtimeState: MihomoRuntimeState) {
    val trafficColors = dashboardTrafficColors()
    val downloadLineColor = trafficColors.download
    val uploadLineColor = trafficColors.upload
    val downloadFillTopColor = downloadLineColor.copy(alpha = 0.20f)
    val downloadFillBottomColor = downloadLineColor.copy(alpha = 0.02f)
    val uploadFillTopColor = uploadLineColor.copy(alpha = 0.18f)
    val uploadFillBottomColor = uploadLineColor.copy(alpha = 0.02f)
    val baselineLineColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.42f)
    val targetMaxSpeed = runtimeState.traffic.history
        .takeLast(SpeedChartVisibleSamples)
        .maxOfOrNull { sample -> maxOf(sample.up, sample.down) }
        ?.coerceAtLeast(1L)
        ?.toFloat()
        ?: 1f
    val animatedMaxSpeed by animateFloatAsState(
        targetValue = targetMaxSpeed,
        animationSpec = tween(durationMillis = SpeedChartScaleAnimationMillis),
        label = "speedChartMaxSpeed",
    )

    DashboardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(168.dp),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelTitle(
                    title = stringResource(R.string.mihomo_dashboard_network_speed),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "↑ ${runtimeState.traffic.latest.up.toReadableBytes(keepTrailingZero = true)}/s",
                        style = MiuixTheme.textStyles.body2,
                        color = uploadLineColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "↓ ${runtimeState.traffic.latest.down.toReadableBytes(keepTrailingZero = true)}/s",
                        style = MiuixTheme.textStyles.body2,
                        color = downloadLineColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
            ) {
                val samples = runtimeState.traffic.history.takeLast(SpeedChartVisibleSamples)
                val baselineY = size.height * 0.82f
                val chartTop = size.height * 0.08f
                drawLine(
                    color = baselineLineColor,
                    start = Offset(0f, baselineY),
                    end = Offset(size.width, baselineY),
                    strokeWidth = 4.2f,
                    cap = StrokeCap.Round,
                )
                if (samples.size >= 2 && samples.any { sample -> sample.up > 0L || sample.down > 0L }) {
                    val maxSpeed = animatedMaxSpeed.coerceAtLeast(1f)
                    val step = size.width / (SpeedChartVisibleSamples - 1)
                    val firstX = size.width - (samples.size - 1) * step
                    fun speedPoints(speedOf: (engine.mihomo.runtime.MihomoTrafficSample) -> Long): List<Offset> {
                        return samples.mapIndexed { index, sample ->
                            val x = firstX + index * step
                            val fraction = (speedOf(sample).toFloat() / maxSpeed).coerceIn(0f, 1f)
                            val y = baselineY - fraction * (baselineY - chartTop)
                            Offset(x, y)
                        }
                    }
                    val uploadPoints = speedPoints { sample -> sample.up }
                    val downloadPoints = speedPoints { sample -> sample.down }
                    val uploadCurvePath = Path().apply {
                        addSmoothCurve(uploadPoints, minY = chartTop, maxY = baselineY)
                    }
                    val uploadAreaPath = Path().apply {
                        addSmoothCurveArea(uploadPoints, baselineY, minY = chartTop, maxY = baselineY)
                    }
                    val downloadCurvePath = Path().apply {
                        addSmoothCurve(downloadPoints, minY = chartTop, maxY = baselineY)
                    }
                    val downloadAreaPath = Path().apply {
                        addSmoothCurveArea(downloadPoints, baselineY, minY = chartTop, maxY = baselineY)
                    }
                    val uploadFillBrush = Brush.verticalGradient(
                        colors = listOf(uploadFillTopColor, uploadFillBottomColor),
                        startY = chartTop,
                        endY = baselineY,
                    )
                    val downloadFillBrush = Brush.verticalGradient(
                        colors = listOf(downloadFillTopColor, downloadFillBottomColor),
                        startY = chartTop,
                        endY = baselineY,
                    )
                    clipRect {
                        drawPath(
                            path = downloadAreaPath,
                            brush = downloadFillBrush,
                        )
                        drawPath(
                            path = uploadAreaPath,
                            brush = uploadFillBrush,
                        )
                        drawPath(
                            path = downloadCurvePath,
                            color = downloadLineColor,
                            style = Stroke(
                                width = 4.2f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                        drawPath(
                            path = uploadCurvePath,
                            color = uploadLineColor,
                            style = Stroke(
                                width = 4.2f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardMosaic(
    appState: AppState,
    runtimeState: MihomoRuntimeState,
    onModeSelected: (Int) -> Unit,
    onIntranetIpRefresh: () -> Unit,
    onNetworkDetectionRefresh: () -> Unit,
    onMemoryRefresh: () -> Unit,
    onProfileSelected: (Int) -> Unit,
) {
    val selectedProfile = appState.selectedMihomoProfileOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(DashboardMosaicGap),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DashboardMosaicGap),
        ) {
            OutboundModeRadioCard(
                selectedMode = appState.mihomoMode,
                onModeSelected = onModeSelected,
            )
            SimpleInfoCard(
                title = stringResource(R.string.mihomo_dashboard_local_ip),
                value = runtimeState.device.intranetIp.ifBlank {
                    stringResource(R.string.mihomo_dashboard_unavailable)
                },
                allowValueWrap = true,
                onClick = onIntranetIpRefresh,
            )
            NetworkDetectionCard(
                networkDetection = runtimeState.networkDetection,
                onClick = onNetworkDetectionRefresh,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DashboardMosaicGap),
        ) {
            CurrentProfileCard(
                profile = selectedProfile,
                profiles = appState.mihomoProfiles,
                selectedProfileId = selectedProfile?.id ?: appState.selectedMihomoProfileId,
                onProfileSelected = onProfileSelected,
            )
            TrafficUsageCard(runtimeState)
            SimpleInfoCard(
                title = stringResource(R.string.mihomo_dashboard_memory),
                value = if (runtimeState.memory.inUseBytes > 0L) {
                    runtimeState.memory.inUseBytes.toReadableBytes(keepTrailingZero = true)
                } else {
                    stringResource(R.string.mihomo_dashboard_unavailable)
                },
                onClick = onMemoryRefresh,
            )
        }
    }
}

@Composable
private fun OutboundModeRadioCard(
    selectedMode: Int,
    onModeSelected: (Int) -> Unit,
) {
    DashboardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(DashboardLargeCardHeight),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PanelTitle(
                title = stringResource(R.string.mihomo_dashboard_mode),
            )
            dashboardModeOptions().forEach { option ->
                val interactionSource = remember(option.mode) { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onModeSelected(option.mode) },
                        )
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioDot(selected = selectedMode == option.mode)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = option.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkDetectionCard(
    networkDetection: MihomoNetworkDetectionState,
    onClick: () -> Unit,
) {
    val unavailable = stringResource(R.string.mihomo_dashboard_unavailable)
    val checking = stringResource(R.string.mihomo_dashboard_network_detection_checking)
    val interactionSource = remember { MutableInteractionSource() }
    DashboardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(DashboardSmallCardHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !networkDetection.checking,
                onClick = onClick,
            ),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.mihomo_dashboard_network_detection),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = when {
                networkDetection.checking -> checking
                networkDetection.address.isNotBlank() -> networkDetection.address
                else -> unavailable
            },
            fontSize = 13.sp,
            lineHeight = 16.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
        )
    }
}

@Composable
private fun TrafficUsageCard(runtimeState: MihomoRuntimeState) {
    val trafficColors = dashboardTrafficColors()
    DashboardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(DashboardLargeCardHeight),
    ) {
        PanelTitle(
            title = stringResource(R.string.mihomo_dashboard_traffic_usage),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrafficDonut(
                up = runtimeState.traffic.totalUp,
                down = runtimeState.traffic.totalDown,
                uploadColor = trafficColors.upload,
                downloadColor = trafficColors.download,
                modifier = Modifier.size(76.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrafficLegend(
                    label = stringResource(R.string.mihomo_dashboard_upload),
                    color = trafficColors.upload,
                )
                TrafficLegend(
                    label = stringResource(R.string.mihomo_dashboard_download),
                    color = trafficColors.download,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        TrafficValueRow("↑", runtimeState.traffic.totalUp, trafficColors.upload)
        TrafficValueRow("↓", runtimeState.traffic.totalDown, trafficColors.download)
    }
}

@Composable
private fun SimpleInfoCard(
    title: String,
    value: String,
    selected: Boolean = false,
    allowValueWrap: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    DashboardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(DashboardSmallCardHeight)
            .then(clickModifier),
        insideMargin = if (allowValueWrap) {
            PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        } else {
            PaddingValues(16.dp)
        },
        selected = selected,
    ) {
        PanelTitle(
            title = title,
        )
        Spacer(Modifier.height(if (allowValueWrap) 6.dp else 10.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = value,
            fontSize = if (allowValueWrap) 12.sp else 17.sp,
            lineHeight = if (allowValueWrap) 13.sp else 17.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = if (allowValueWrap) 3 else 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = allowValueWrap,
        )
    }
}

@Composable
private fun CurrentProfileCard(
    profile: MihomoProfileState?,
    profiles: List<MihomoProfileState>,
    selectedProfileId: Int,
    onProfileSelected: (Int) -> Unit,
) {
    var showProfileMenu by remember { mutableStateOf(false) }
    val name = profile?.name?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.mihomo_dashboard_no_configuration)
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Box {
        DashboardPanel(
            modifier = Modifier
                .fillMaxWidth()
                .height(DashboardSmallCardHeight)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = profiles.isNotEmpty(),
                    onClick = {
                        showProfileMenu = true
                    },
                ),
        ) {
            PanelTitle(
                title = stringResource(R.string.mihomo_dashboard_profile),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        WindowListPopup(
            show = showProfileMenu,
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = {
                showProfileMenu = false
            },
        ) {
            val dismissState = LocalDismissState.current
            ListPopupColumn {
                profiles.forEachIndexed { index, profileItem ->
                    key(profileItem.id) {
                        DropdownImpl(
                            text = profileItem.name.ifBlank { "-" },
                            optionSize = profiles.size,
                            isSelected = profileItem.id == selectedProfileId,
                            onSelectedIndexChange = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (profileItem.id != selectedProfileId) {
                                    onProfileSelected(profileItem.id)
                                }
                                showProfileMenu = false
                                dismissState?.invoke()
                            },
                            index = index,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardPanel(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = PaddingValues(16.dp),
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = mihomoDashboardCardColor(selected)),
        insideMargin = insideMargin,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        content()
    }
}

@Composable
private fun mihomoDashboardCardColor(selected: Boolean = false): Color {
    return MiuixTheme.colorScheme.primary.copy(alpha = if (selected) 0.18f else 0.12f)
}

@Composable
private fun dashboardTrafficColors(): DashboardTrafficColors {
    val primary = MiuixTheme.colorScheme.primary
    return DashboardTrafficColors(
        upload = primary.toSpeedChartContrastColor(),
        download = primary.deepenForSpeedChart(),
    )
}

@Composable
private fun PanelTitle(
    title: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val selectedColor = MiuixTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(22.dp)) {
        drawCircle(
            color = if (selected) selectedColor else color,
            radius = size.minDimension / 2.2f,
            style = Stroke(width = 3f),
        )
        if (selected) {
            drawCircle(
                color = selectedColor,
                radius = size.minDimension / 4.2f,
            )
        }
    }
}

@Composable
private fun TrafficDonut(
    up: Long,
    down: Long,
    uploadColor: Color,
    downloadColor: Color,
    modifier: Modifier = Modifier,
) {
    val total = up + down
    val hasTraffic = total > 0L
    val upSweep = if (hasTraffic) 360f * up / total.toFloat() else 180f
    val downSweep = if (hasTraffic) 360f * down / total.toFloat() else 180f
    val uploadStartAngle = 180f - upSweep / 2f
    val downloadStartAngle = uploadStartAngle + upSweep
    val trackColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.30f)
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 16f, cap = StrokeCap.Round)
        val inset = 12f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
        if (!hasTraffic || down > 0L) {
            drawArc(
                color = downloadColor,
                startAngle = downloadStartAngle,
                sweepAngle = downSweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        if (!hasTraffic || up > 0L) {
            drawArc(
                color = uploadColor,
                startAngle = uploadStartAngle,
                sweepAngle = upSweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
    }
}

@Composable
private fun TrafficLegend(
    label: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun TrafficValueRow(
    arrow: String,
    bytes: Long,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = arrow,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
        Text(
            text = bytes.toReadableBytes(keepTrailingZero = true),
            fontSize = 13.sp,
            color = color,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DashboardStartToolbar(
    running: Boolean,
    operationInProgress: Boolean,
    onToggle: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(
            end = 20.dp,
            bottom = bottomPadding + MihomoFloatingToolbarBottomSpacing,
        ),
    ) {
        FloatingToolbar(
            color = MiuixTheme.colorScheme.primary,
            cornerRadius = 32.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = MihomoFloatingToolbarVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    modifier = Modifier.size(MihomoFloatingToolbarButtonSize),
                    onClick = {
                        if (!operationInProgress) {
                            onToggle()
                        }
                    },
                ) {
                    Icon(
                        modifier = Modifier.size(26.dp),
                        imageVector = if (running) MiuixIcons.Pause else MiuixIcons.Play,
                        contentDescription = if (running) {
                            stringResource(R.string.proxy_service_stop)
                        } else {
                            stringResource(R.string.proxy_service_start)
                        },
                        tint = MiuixTheme.colorScheme.onPrimary.copy(
                            alpha = if (operationInProgress) 0.45f else 1f,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun dashboardModeOptions(): List<DashboardModeOption> {
    return listOf(
        DashboardModeOption(MihomoModeRule, stringResource(R.string.mihomo_mode_rule)),
        DashboardModeOption(MihomoModeGlobal, stringResource(R.string.mihomo_mode_global)),
        DashboardModeOption(MihomoModeDirect, stringResource(R.string.mihomo_mode_direct)),
    )
}

private data class DashboardModeOption(
    val mode: Int,
    val label: String,
)

private data class DashboardTrafficColors(
    val upload: Color,
    val download: Color,
)

private fun Path.addSmoothCurve(
    points: List<Offset>,
    minY: Float,
    maxY: Float,
) {
    if (points.isEmpty()) return
    moveTo(points.first().x, points.first().y.coerceIn(minY, maxY))
    addSmoothCurveSegments(points, minY, maxY)
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

private fun Path.addSmoothCurveArea(
    points: List<Offset>,
    baselineY: Float,
    minY: Float,
    maxY: Float,
) {
    if (points.isEmpty()) return
    moveTo(points.first().x, baselineY)
    lineTo(points.first().x, points.first().y.coerceIn(minY, maxY))
    addSmoothCurveSegments(points, minY, maxY)
    lineTo(points.last().x, baselineY)
    close()
}

private fun Path.addSmoothCurveSegments(
    points: List<Offset>,
    minY: Float,
    maxY: Float,
) {
    if (points.size < 2) return
    if (points.size == 2) {
        lineTo(points.last().x, points.last().y.coerceIn(minY, maxY))
        return
    }
    for (index in 0 until points.lastIndex) {
        val previous = points.getOrElse(index - 1) { points[index] }
        val current = points[index]
        val next = points[index + 1]
        val following = points.getOrElse(index + 2) { next }
        val firstControl = Offset(
            x = current.x + (next.x - previous.x) / SpeedChartCurveTension,
            y = (current.y + (next.y - previous.y) / SpeedChartCurveTension).coerceIn(minY, maxY),
        )
        val secondControl = Offset(
            x = next.x - (following.x - current.x) / SpeedChartCurveTension,
            y = (next.y - (following.y - current.y) / SpeedChartCurveTension).coerceIn(minY, maxY),
        )
        cubicTo(
            x1 = firstControl.x,
            y1 = firstControl.y,
            x2 = secondControl.x,
            y2 = secondControl.y,
            x3 = next.x,
            y3 = next.y.coerceIn(minY, maxY),
        )
    }
}

private fun Color.deepenForSpeedChart(): Color {
    return Color(
        red = red * SpeedChartLineDarkenFactor,
        green = green * SpeedChartLineDarkenFactor,
        blue = blue * SpeedChartLineDarkenFactor,
        alpha = alpha,
    )
}

private fun Color.toSpeedChartContrastColor(): Color {
    return Color(
        red = ((1f - red) * SpeedChartContrastLineDarkenFactor).coerceIn(0f, 1f),
        green = ((1f - green) * SpeedChartContrastLineDarkenFactor).coerceIn(0f, 1f),
        blue = ((1f - blue) * SpeedChartContrastLineDarkenFactor).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

private const val SpeedChartVisibleSamples = 28
private const val SpeedChartScaleAnimationMillis = 360
private const val SpeedChartCurveTension = 6f
private const val SpeedChartLineDarkenFactor = 0.82f
private const val SpeedChartContrastLineDarkenFactor = 0.86f
private val DashboardMosaicGap: Dp = 12.dp
private val DashboardSmallCardHeight: Dp = 88.dp
private val DashboardLargeCardHeight: Dp = DashboardSmallCardHeight * 2 + DashboardMosaicGap
private val MihomoFloatingToolbarButtonSize = 52.dp
private val MihomoFloatingToolbarVerticalPadding = 8.dp
private val MihomoFloatingToolbarBottomSpacing = 16.dp
