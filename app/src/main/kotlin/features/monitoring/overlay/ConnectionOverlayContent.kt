// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.AppState
import app.R
import app.modes.NetworkActivityStyleDotted
import app.modes.NetworkActivityStyleLines
import engine.mihomo.runtime.MihomoTrafficSample
import engine.proxy.RuntimeRouteRule
import features.monitoring.ConnectionMonitorStatus
import features.monitoring.ConnectionOverlayStats
import features.monitoring.ConnectionOverlayTrafficSampleLimit
import features.monitoring.MonitoringIntent
import features.monitoring.MonitoringRepository
import features.monitoring.connections.ConnectionAppResolver
import features.monitoring.dns.DnsRecord
import features.monitoring.dns.dnsRcodeLabel
import features.monitoring.dns.dnsStatusLabel
import features.monitoring.formatMonitoringRate
import features.monitoring.formatMonitoringRatePair
import features.monitoring.observeWhileStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ui.charts.buildNetworkActivityLineBands
import ui.charts.buildNetworkActivityWaveform
import ui.charts.drawDottedWaveform
import ui.charts.drawSmoothAreaLine
import ui.charts.rememberDottedWaveformRamp
import ui.components.AsteriskExpansionIndicator
import ui.components.AsteriskSegmentItem
import ui.components.AsteriskSegmentedControl
import ui.feedback.AndroidToastTipNotifier
import ui.theme.AsteriskMotion
import ui.theme.AsteriskShapeTokens
import ui.theme.LocalReduceMotion
import ui.theme.LocalSpacing
import utils.toReadableBytes
import java.text.DateFormat
import java.util.Date
import ui.icons.AsteriskIcons as Icons

private enum class OverlayDimension(val label: Int) {
    Connections(R.string.home_monitor_connections),
    Dns(R.string.monitor_filter_dns),
    Rules(R.string.monitor_filter_rules),
}

@Composable
internal fun ConnectionOverlayContent(
    monitoring: MonitoringRepository,
    appState: StateFlow<AppState>,
    minimized: Boolean,
    panelVisible: Boolean,
    buttonOffset: IntOffset,
    expansion: Animatable<Float, AnimationVector1D>,
    heightLimit: Dp,
    hasCustomSize: Boolean,
    onMinimize: () -> Unit,
    onMinimizeFinished: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragFinished: () -> Unit,
    onResize: (Boolean, Float, Float) -> Unit,
    onOverviewHeightChanged: (Int) -> Unit,
    onConnectionsVisibilityChanged: (Boolean) -> Unit,
) {
    var dimension by remember { mutableStateOf(OverlayDimension.Connections) }
    var selectedRankId by remember { mutableStateOf<String?>(null) }
    var closingConnection by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tipNotifier = remember(context) { AndroidToastTipNotifier(context) }
    val closeFailed = stringResource(R.string.monitor_connections_close_failed)
    val colors = MaterialTheme.colorScheme
    var laidOut by remember { mutableStateOf(false) }
    val spatial = AsteriskMotion.spatial<Float>()
    val target = if (panelVisible && !minimized && laidOut) 1f else 0f
    LaunchedEffect(target, spatial) {
        expansion.animateTo(target, spatial)
        if (minimized && panelVisible) onMinimizeFinished()
    }
    val panelInteractive = panelVisible && !minimized && expansion.targetValue == 1f && !expansion.isRunning
    fun closeConnection(id: String) {
        if (closingConnection || dimension != OverlayDimension.Connections || selectedRankId != id) return
        closingConnection = true
        scope.launch {
            try {
                monitoring.closeConnection(id)
                    .onSuccess {
                        if (dimension == OverlayDimension.Connections && selectedRankId == id) selectedRankId = null
                    }
                    .onFailure { tipNotifier.showError(it, closeFailed) }
            } finally {
                closingConnection = false
            }
        }
    }
    val (running, chartStyle) = if (panelVisible) {
        remember(appState) {
            appState.map { it.proxyRunning to it.networkActivityStyle }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = true to NetworkActivityStyleDotted).value
    } else false to NetworkActivityStyleDotted
    val stats = if (panelVisible) {
        remember(monitoring) { monitoring.connectionOverlayStats() }.collectAsStateWithLifecycle(ConnectionOverlayStats()).value
    } else ConnectionOverlayStats()

    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(max = heightLimit)
            .onGloballyPositioned { laidOut = true }
            .graphicsLayer {
                val progress = expansion.value.coerceIn(0f, 1f)
                val remaining = 1f - progress
                val buttonRadius = OverlayButtonAnchorSizeDp.dp.toPx() / 2f
                val iconSize = FloatingIconSizeDp.dp.toPx()
                scaleX = progress + remaining * (iconSize / size.width.coerceAtLeast(1f))
                scaleY = progress + remaining * (iconSize / size.height.coerceAtLeast(1f))
                // Translate the scaled panel's center to the button on the collapsed frame.
                translationX = (buttonOffset.x + buttonRadius - size.width / 2f) * remaining
                translationY = (buttonOffset.y + buttonRadius - size.height / 2f) * remaining
                alpha = (progress * 2f).coerceIn(0f, 1f)
            },
        shape = AsteriskShapeTokens.InnerContainer,
        color = colors.surfaceContainer.copy(alpha = 0.88f),
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Box {
            Column(Modifier.fillMaxWidth().then(if (hasCustomSize) Modifier.fillMaxHeight() else Modifier)) {
                Column(
                    Modifier.fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .onSizeChanged { onOverviewHeightChanged(it.height) },
                ) {
                    OverlaySummary(
                        stats = stats,
                        running = running,
                        chartStyle = chartStyle,
                        onMove = { dx, dy -> if (panelInteractive) onDrag(dx, dy) },
                        onMoveFinished = onDragFinished,
                        onMinimize = { if (panelInteractive) onMinimize() },
                    )
                }
                if (panelVisible && hasCustomSize) {
                    val dnsAvailable by monitoring.dnsAvailable.collectAsStateWithLifecycle(initialValue = null)
                    val dimensions = remember(dnsAvailable) {
                        OverlayDimension.entries.filter { it != OverlayDimension.Dns || dnsAvailable == true }
                    }
                    val activeDimension = dimension.takeIf { it in dimensions } ?: OverlayDimension.Connections
                    LaunchedEffect(dnsAvailable) {
                        if (dnsAvailable == false && dimension == OverlayDimension.Dns) {
                            dimension = OverlayDimension.Connections
                            selectedRankId = null
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val detailsFit = maxWidth >= 200.dp &&
                            maxHeight >= 124.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
                        Column {
                            AnimatedVisibility(
                                visible = detailsFit,
                                enter = AsteriskMotion.contentEnter(),
                                exit = AsteriskMotion.contentExit(),
                            ) {
                                OverlayRankings(
                                    monitoring = monitoring,
                                    active = detailsFit,
                                    dimensions = dimensions,
                                    dimension = activeDimension,
                                    onDimensionChange = { next ->
                                        if (dimension != next) selectedRankId = null
                                        dimension = next
                                    },
                                    selectedRankId = selectedRankId.takeIf { dimension == activeDimension },
                                    onCloseConnection = if (panelInteractive && !closingConnection) ::closeConnection else null,
                                    onConnectionsVisibilityChanged = onConnectionsVisibilityChanged,
                                    onSelectRank = { id ->
                                        selectedRankId = id.takeUnless { dimension == activeDimension && it == selectedRankId }
                                        dimension = activeDimension
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (panelInteractive) {
                OverlayResizeCorner(true, onResize, onDragFinished, Modifier.align(AbsoluteAlignment.BottomLeft))
                OverlayResizeCorner(false, onResize, onDragFinished, Modifier.align(AbsoluteAlignment.BottomRight))
            }
        }
    }
}

@Composable
internal fun ConnectionOverlayButton(
    expansion: Animatable<Float, AnimationVector1D>,
    interactive: Boolean,
    onRestore: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragFinished: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val drag by rememberUpdatedState(onDrag)
    val dragFinished by rememberUpdatedState(onDragFinished)
    val dragModifier = if (!interactive) Modifier else Modifier.systemGestureExclusion().pointerInput(Unit) {
        detectDragGestures(onDragEnd = { dragFinished() }, onDragCancel = { dragFinished() }) { change, amount ->
            change.consume()
            drag(amount.x, amount.y)
        }
    }
    OverlayIconButton(
        onClick = onRestore,
        enabled = interactive,
        modifier = Modifier.size(FloatingIconSizeDp.dp)
            .graphicsLayer { alpha = (1f - expansion.value * 2f).coerceIn(0f, 1f) }
            .then(dragModifier),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = AsteriskShapeTokens.Pill,
            color = colors.primaryContainer.copy(alpha = 0.55f),
            contentColor = colors.onPrimaryContainer,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.60f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_qs_proxy),
                    contentDescription = stringResource(R.string.monitor_overlay_restore),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private const val OverlayCloseIconSizeDp = 18

@Composable
private fun OverlayIconButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val viewConfiguration = LocalViewConfiguration.current
    val iconTouchConfiguration = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val minimumTouchTargetSize = DpSize.Zero
        }
    }
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides 0.dp,
        LocalViewConfiguration provides iconTouchConfiguration,
    ) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

@Composable
private fun OverlayRankings(
    monitoring: MonitoringRepository,
    active: Boolean,
    dimensions: List<OverlayDimension>,
    dimension: OverlayDimension,
    onDimensionChange: (OverlayDimension) -> Unit,
    selectedRankId: String?,
    onCloseConnection: ((String) -> Unit)?,
    onConnectionsVisibilityChanged: (Boolean) -> Unit,
    onSelectRank: (String) -> Unit,
) {
    val onVisibilityChanged by rememberUpdatedState(onConnectionsVisibilityChanged)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (active && dimension == OverlayDimension.Connections) {
        DisposableEffect(monitoring, lifecycle) {
            val observation = monitoring.observeWhileStarted(MonitoringIntent.Connections, lifecycle)
            onVisibilityChanged(true)
            onDispose {
                observation.close()
                onVisibilityChanged(false)
            }
        }
    }
    val spacing = LocalSpacing.current
    if (active && dimension == OverlayDimension.Rules) {
        DisposableEffect(monitoring, lifecycle) {
            val observation = monitoring.observeWhileStarted(MonitoringIntent.Rules, lifecycle)
            onDispose { observation.close() }
        }
    }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        if (dimensions.size > 1) {
            AsteriskSegmentedControl(
                items = dimensions.map { AsteriskSegmentItem(it, stringResource(it.label)) },
                selectedValue = dimension,
                onSelected = onDimensionChange,
                modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
                containerColor = Color.Transparent,
                checkedContainerColor = colors.primary.copy(alpha = 0.14f),
                checkedContentColor = colors.primary,
                contentPadding = PaddingValues(vertical = spacing.extraSmall),
                textStyle = MaterialTheme.typography.labelMedium,
            )
        }
        Crossfade(
            targetState = dimension,
            modifier = Modifier.weight(1f),
            animationSpec = AsteriskMotion.contentFade(),
            label = "overlay-ranking-dimension",
        ) { content ->
            if (content !in dimensions) return@Crossfade
            val selection = selectedRankId.takeIf { content == dimension }
            val onSelect: (String) -> Unit = { id -> if (content == dimension) onSelectRank(id) }
            when (content) {
                OverlayDimension.Connections -> OverlayConnectionRanking(
                    monitoring, selection, onSelect, onCloseConnection.takeIf { active && content == dimension },
                )
                OverlayDimension.Dns -> OverlayDnsRanking(monitoring, selection, onSelect)
                OverlayDimension.Rules -> OverlayRuleRanking(monitoring, selection, onSelect)
            }
        }
    }
}

@Composable
private fun OverlayResizeCorner(
    fromLeft: Boolean,
    onResize: (Boolean, Float, Float) -> Unit,
    onResizeFinished: () -> Unit,
    modifier: Modifier,
) {
    val resize by rememberUpdatedState(onResize)
    val resizeFinished by rememberUpdatedState(onResizeFinished)
    Box(
        modifier.size(width = 44.dp, height = 36.dp).pointerInput(fromLeft) {
            detectDragGestures(onDragEnd = { resizeFinished() }, onDragCancel = { resizeFinished() }) { change, amount ->
                change.consume()
                resize(fromLeft, amount.x, amount.y)
            }
        },
    )
}

@Composable
private fun OverlaySummary(
    stats: ConnectionOverlayStats,
    running: Boolean,
    chartStyle: Int,
    onMove: (Float, Float) -> Unit,
    onMoveFinished: () -> Unit,
    onMinimize: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val move by rememberUpdatedState(onMove)
    val moveFinished by rememberUpdatedState(onMoveFinished)
    val cpu = stats.cpuPercent?.takeIf { running }?.let { "%.1f%%".format(it) } ?: "—"
    val memory = stats.memoryBytes?.takeIf { running }?.toReadableBytes() ?: "—"
    val upload = stats.uploadRate.takeIf { running }
    val download = stats.downloadRate.takeIf { running }
    val samples = stats.trafficSamples.takeIf { running }.orEmpty()
    Column(
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
            detectDragGestures(onDragEnd = { moveFinished() }, onDragCancel = { moveFinished() }) { change, amount ->
                change.consume()
                move(amount.x, amount.y)
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlaySummaryMetrics(
                cpu = cpu,
                memory = memory,
                uploadedBytes = stats.sessionUploadBytes.takeIf { running },
                downloadedBytes = stats.sessionDownloadBytes.takeIf { running },
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.align(Alignment.Top).size(OverlayButtonAnchorSizeDp.dp),
                contentAlignment = Alignment.Center,
            ) {
                OverlayIconButton(
                    onClick = onMinimize,
                    modifier = Modifier.offset(x = OverlayCloseNudgeDp.dp, y = (-OverlayCloseNudgeDp).dp)
                        .size(FloatingIconSizeDp.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.monitor_overlay_minimize),
                        modifier = Modifier.size(OverlayCloseIconSizeDp.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(
                start = spacing.medium,
                end = spacing.medium,
                bottom = spacing.small,
            ),
        ) {
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            if (maxWidth >= 176.dp * fontScale) {
                val rateMaxWidth = maxWidth / 2
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlayTrafficTrend(samples, chartStyle, Modifier.weight(1f))
                    OverlayRatePair(upload, download, Modifier.widthIn(max = rateMaxWidth), stacked = true)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                    OverlayTrafficTrend(samples, chartStyle, Modifier.fillMaxWidth())
                    OverlayRatePair(upload, download)
                }
            }
        }
    }
}

@Composable
private fun OverlaySummaryMetrics(
    cpu: String,
    memory: String,
    uploadedBytes: Long?,
    downloadedBytes: Long?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    Layout(
        modifier = modifier,
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                OverlayIconMetric(
                    Icons.Rounded.Cpu, R.string.monitor_resource_card_cpu, cpu,
                    Modifier, iconSize = 16.dp,
                )
                OverlayIconMetric(
                    Icons.Rounded.Ram, R.string.monitor_resource_card_memory, memory,
                    Modifier, iconSize = 16.dp,
                )
            }
            if (uploadedBytes != null && downloadedBytes != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    OverlayIconMetric(
                        Icons.Rounded.ArrowUpward, R.string.monitor_overlay_total_upload,
                        uploadedBytes.toReadableBytes(), Modifier, tint = colors.tertiary,
                    )
                    OverlayIconMetric(
                        Icons.Rounded.ArrowDownward, R.string.monitor_overlay_total_download,
                        downloadedBytes.toReadableBytes(), Modifier, tint = colors.primary,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val resources = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val totalStart = resources.width + spacing.medium.roundToPx()
        val totals = measurables.getOrNull(1)?.measure(Constraints(maxHeight = constraints.maxHeight))
            ?.takeIf { totalStart + it.width <= constraints.maxWidth }
        val width = totals?.let { totalStart + it.width } ?: resources.width
        val height = constraints.constrainHeight(maxOf(resources.height, totals?.height ?: 0))
        layout(constraints.constrainWidth(width), height) {
            resources.placeRelative(0, (height - resources.height) / 2)
            totals?.let { it.placeRelative(totalStart, (height - it.height) / 2) }
        }
    }
}

@Composable
private fun OverlayTrafficTrend(
    samples: List<MihomoTrafficSample>,
    style: Int,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.home_network_activity)
    val chartModifier = modifier.height(OverlayChartHeightDp.dp).semantics { contentDescription = description }
    if (style == NetworkActivityStyleLines) {
        val bands = remember(samples) {
            buildNetworkActivityLineBands(
                samples, ConnectionOverlayTrafficSampleLimit, download = { it.down }, upload = { it.up },
            )
        }
        Canvas(chartModifier) {
            if (bands.sampleCount == 0) return@Canvas
            val strokeWidth = 1.5.dp.toPx()
            val endpointRadius = 2.dp.toPx()
            inset(horizontal = endpointRadius, vertical = endpointRadius) {
                drawLine(
                    color = colors.outlineVariant.copy(alpha = 0.72f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                if (bands.scaleBytes > 0L) {
                    drawOverlayTrafficSeries(bands.downloadEdge, colors.primary, strokeWidth, endpointRadius)
                    drawOverlayTrafficSeries(bands.uploadEdge, colors.tertiary, strokeWidth, endpointRadius)
                }
            }
        }
    } else {
        val waveform = remember(samples) {
            buildNetworkActivityWaveform(
                samples, ConnectionOverlayTrafficSampleLimit, download = { it.down }, upload = { it.up },
            )
        }
        val downloadRamp = rememberDottedWaveformRamp(colors.primary)
        val uploadRamp = rememberDottedWaveformRamp(colors.tertiary)
        Canvas(chartModifier) {
            if (waveform.sampleCount == 0) return@Canvas
            val dotRadius = 1.dp.toPx()
            val verticalPitch = 3.dp.toPx()
            val horizontalPitch = 4.dp.toPx()
            drawDottedWaveform(
                waveform.download, waveform.sampleCount, dotRadius, -1f, downloadRamp,
                verticalDotPitch = verticalPitch, horizontalDotPitch = horizontalPitch,
            )
            drawDottedWaveform(
                waveform.upload, waveform.sampleCount, dotRadius, 1f, uploadRamp,
                verticalDotPitch = verticalPitch, horizontalDotPitch = horizontalPitch,
            )
        }
    }
}

private fun DrawScope.drawOverlayTrafficSeries(
    values: FloatArray,
    color: Color,
    strokeWidth: Float,
    endpointRadius: Float,
) {
    drawSmoothAreaLine(values, color, strokeWidth, areaAlpha = 0.16f)
    val latest = values.last()
    if (latest > 0f) {
        drawCircle(color, radius = endpointRadius, center = Offset(size.width, size.height * (1f - latest)))
    }
}

@Composable
private fun OverlayRatePair(
    upload: Long?,
    download: Long?,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val metrics: @Composable (Modifier) -> Unit = { itemModifier ->
        OverlayIconMetric(
            Icons.Rounded.ArrowUpward, R.string.monitor_connections_upload, formatMonitoringRate(upload), itemModifier,
            tint = colors.tertiary,
        )
        OverlayIconMetric(
            Icons.Rounded.ArrowDownward, R.string.monitor_connections_download, formatMonitoringRate(download), itemModifier,
            tint = colors.primary,
        )
    }
    if (stacked) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            metrics(Modifier)
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.small)) {
            metrics(Modifier.weight(1f))
        }
    }
}

@Composable
private fun OverlayIconMetric(
    icon: ImageVector,
    description: Int,
    value: String,
    modifier: Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = 14.dp,
) {
    val styles = MaterialTheme.typography
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(description),
            modifier = Modifier.size(iconSize),
            tint = tint,
        )
        Text(
            text = value,
            style = styles.bodySmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverlayConnectionRanking(
    monitoring: MonitoringRepository,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onCloseConnection: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    val ranking by remember(monitoring, selectedId, context) {
        val resolver = selectedId?.let { ConnectionAppResolver(context) }
        monitoring.state.map { it.connections }.distinctUntilChanged().map { summary ->
            val connections = if (summary.stale) emptyList() else summary.snapshot.connections
            val pinned = selectedId?.let { id -> connections.find { it.id == id } }?.let { connection ->
                connection.toOverlayPinnedConnection(resolver?.resolve(connection)?.packageName)
            }
            Triple(summary.status, rankOverlayConnections(connections), pinned)
        }.flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(Triple(ConnectionMonitorStatus.Loading, emptyList(), null))
    val (status, rows, resolvedPinned) = ranking
    val pinned = resolvedPinned?.takeIf { it.row.id == selectedId }
    val pinnedKey = pinned?.row?.id
    val remaining = remember(rows, pinnedKey) { rows.withRoomForPinned(pinnedKey, OverlayConnectionRank::id) }
    OverlayRankList(
        status = status,
        emptyLabel = R.string.monitor_connections_empty,
        rows = remaining,
        itemKey = OverlayConnectionRank::id,
        pinnedKey = pinnedKey,
        pinnedContent = {
            OverlayPinnedItem(
                pinned = pinned,
                itemKey = { it.row.id },
                header = { OverlayPinnedHeader(it, onSelect) },
                details = { item ->
                    OverlayConnectionDetails(
                        connection = item,
                        onCollapse = { onSelect(item.row.id) },
                        onClose = onCloseConnection.takeIf { item.row.id == pinnedKey },
                    )
                },
            )
        },
    ) { row ->
        OverlayConnectionRow(row, onSelect)
    }
}

@Composable
private fun <T : Any> OverlayPinnedItem(
    pinned: T?,
    itemKey: (T) -> String,
    header: @Composable (T) -> Unit,
    details: @Composable (T) -> Unit,
) {
    var lastPinned by remember { mutableStateOf<T?>(null) }
    if (pinned != null) SideEffect { lastPinned = pinned }
    AnimatedVisibility(
        visible = pinned != null,
        enter = AsteriskMotion.contentEnter(),
        exit = AsteriskMotion.contentExit(),
    ) {
        val item = pinned ?: lastPinned
        if (item != null) {
            Column {
                header(item)
                key(itemKey(item)) {
                    val reveal = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(
                        visibleState = reveal,
                        enter = fadeIn(animationSpec = AsteriskMotion.contentFade()),
                    ) {
                        details(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayConnectionRow(
    row: OverlayConnectionRank,
    onSelect: (String) -> Unit,
) {
    OverlayRankRow(
        label = row.destination.ifBlank { stringResource(R.string.monitor_value_unknown) },
        source = row.processLabel,
        value = formatMonitoringRate(row.rate),
        onClick = { onSelect(row.id) },
    )
}

@Composable
private fun OverlayPinnedHeader(connection: OverlayPinnedConnection, onSelect: (String) -> Unit) {
    val spacing = LocalSpacing.current
    val styles = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val row = connection.row
    Row(
        modifier = Modifier.fillMaxWidth().clickable(
            onClickLabel = stringResource(R.string.monitor_connections_details),
            onClick = { onSelect(row.id) },
        ).padding(start = spacing.small, top = spacing.extraSmall, end = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            OverlayRankTitle(
                text = row.destination.ifBlank { stringResource(R.string.monitor_value_unknown) },
                style = styles.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        AsteriskExpansionIndicator(
            expanded = true,
            modifier = Modifier.size(16.dp),
            tint = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverlayConnectionDetails(
    connection: OverlayPinnedConnection,
    onCollapse: () -> Unit,
    onClose: ((String) -> Unit)?,
) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val closeButtonSize = 48.dp
    OverlayDetailContent {
        Box {
            Column(
                modifier = Modifier.fillMaxWidth().padding(end = closeButtonSize),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            ) {
                Text(
                    text = connection.packageName ?: stringResource(R.string.monitor_connections_unknown_source),
                    modifier = Modifier.fillMaxWidth().clickable(
                        onClickLabel = stringResource(R.string.monitor_connections_details),
                        onClick = onCollapse,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (connection.sourceAddress.isNotBlank()) {
                    OverlayDetailValue(R.string.monitor_connections_source, connection.sourceAddress)
                }
                OverlayDetailValue(
                    R.string.monitor_connections_rate,
                    formatMonitoringRatePair(connection.uploadRate, connection.downloadRate),
                )
            }
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                IconButton(
                    onClick = { onClose?.invoke(connection.row.id) },
                    modifier = Modifier.size(closeButtonSize),
                    enabled = onClose != null,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = colors.error),
                ) {
                    // Align the visible icon edge with the chevron above.
                    Box(Modifier.fillMaxSize().padding(end = 2.5.dp), contentAlignment = Alignment.CenterEnd) {
                        Icon(Icons.Rounded.LinkOff, stringResource(R.string.monitor_connections_close_one))
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayDetailContent(content: @Composable ColumnScope.() -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            start = spacing.small,
            end = spacing.small,
            bottom = spacing.extraSmall,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        content()
        HorizontalDivider(
            modifier = Modifier.padding(top = spacing.extraSmall),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun OverlayDetailValue(label: Int, value: String, maxLines: Int = 2) {
    val spacing = LocalSpacing.current
    val styles = MaterialTheme.typography
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
        Text(
            text = stringResource(label),
            modifier = Modifier.alignByBaseline(),
            style = styles.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f).alignByBaseline(),
            style = styles.bodySmall.copy(fontFeatureSettings = "tnum"),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverlayDnsRanking(
    monitoring: MonitoringRepository,
    selectedDomain: String?,
    onSelect: (String) -> Unit,
) {
    val ranking by remember(monitoring, selectedDomain) {
        monitoring.dns.map { summary ->
            Triple(summary.status, summary.unsupported, rankOverlayDns(summary.records, selectedDomain))
        }.flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(Triple(ConnectionMonitorStatus.Loading, false, emptyList<OverlayDnsRank>() to null))
    val (status, unsupported, ranked) = ranking
    val (rows, pinned) = ranked
    OverlayExpandableRankList(
        status = status,
        emptyLabel = R.string.monitor_dns_empty,
        rows = rows,
        pinned = pinned?.takeIf { it.domain == selectedDomain },
        itemKey = OverlayDnsRank::domain,
        title = OverlayDnsRank::domain,
        count = { it.lookups.toString() },
        detailsLabel = R.string.monitor_dns_detail_title,
        unsupported = unsupported,
        onSelect = onSelect,
        details = { it.latestRecord?.let { record -> OverlayDnsDetails(record) } },
    )
}

@Composable
private fun OverlayDnsDetails(record: DnsRecord) {
    OverlayDetailContent {
        OverlayDetailValue(R.string.monitor_dns_detail_type, record.recordType)
        OverlayDetailValue(R.string.monitor_dns_detail_status, dnsStatusLabel(record))
        record.event.rcode?.let { OverlayDetailValue(R.string.monitor_dns_detail_rcode, dnsRcodeLabel(it)) }
        OverlayDetailValue(R.string.monitor_dns_detail_delay, stringResource(R.string.monitor_dns_elapsed, record.elapsedMillis))
        if (record.upstream.isNotBlank()) OverlayDetailValue(R.string.monitor_dns_upstream, record.upstream)
        if (record.event.answers.isNotEmpty()) {
            val answers = remember(record.event.answers) { record.event.answers.take(3).joinToString("\n") { it.data } }
            OverlayDetailValue(R.string.monitor_dns_detail_answers, answers, maxLines = 3)
        }
    }
}

@Composable
private fun OverlayRuleRanking(
    monitoring: MonitoringRepository,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    val ruleIndex = selectedKey?.toIntOrNull()
    val ranking by remember(monitoring, ruleIndex) {
        monitoring.state.map { it.rules }.distinctUntilChanged().map { summary ->
            val rules = if (summary.stale) emptyList() else summary.rules
            val pinned = ruleIndex?.let { index -> rules.firstOrNull { it.index == index && it.hits != null } }
            val emptyLabel = if (summary.rules.isNotEmpty()) {
                R.string.monitor_rules_hits_unavailable
            } else {
                R.string.monitor_rules_empty
            }
            Triple(summary.status, emptyLabel, rankOverlayRules(rules) to pinned)
        }.flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(Triple(ConnectionMonitorStatus.Loading, R.string.monitor_rules_empty, emptyList<RuntimeRouteRule>() to null))
    val (status, emptyLabel, ranked) = ranking
    val (rows, pinned) = ranked
    OverlayExpandableRankList(
        status = status,
        emptyLabel = emptyLabel,
        rows = rows,
        pinned = pinned?.takeIf { it.index == ruleIndex },
        itemKey = { it.index.toString() },
        title = { it.payload.ifBlank { it.type } },
        count = { it.hits?.toString() ?: "—" },
        detailsLabel = R.string.monitor_rules_detail_title,
        onSelect = onSelect,
        details = { OverlayRuleDetails(it) },
    )
}

@Composable
private fun OverlayRuleDetails(rule: RuntimeRouteRule) {
    OverlayDetailContent {
        if (rule.payload.isNotBlank()) OverlayDetailValue(R.string.monitor_rules_detail_type, rule.type)
        if (rule.policy.isNotBlank()) OverlayDetailValue(R.string.monitor_rules_detail_policy, rule.policy)
        val lastHit = remember(rule.lastHitAtMillis) {
            rule.lastHitAtMillis?.let { millis ->
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(millis))
            }
        }
        val hitSummary = when {
            lastHit != null -> stringResource(R.string.monitor_rules_last_hit, lastHit)
            rule.hits == 0L -> stringResource(R.string.monitor_rules_never_hit)
            else -> stringResource(R.string.monitor_rules_unknown_hit)
        }
        Text(
            text = hitSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun <T : Any> OverlayExpandableRankList(
    status: ConnectionMonitorStatus,
    emptyLabel: Int,
    rows: List<T>,
    pinned: T?,
    itemKey: (T) -> String,
    title: (T) -> String,
    count: (T) -> String,
    detailsLabel: Int,
    onSelect: (String) -> Unit,
    unsupported: Boolean = false,
    details: @Composable (T) -> Unit,
) {
    val pinnedKey = pinned?.let(itemKey)
    val remaining = remember(rows, pinnedKey) { rows.withRoomForPinned(pinnedKey, itemKey) }
    val header: @Composable (T, Boolean) -> Unit = { item, expanded ->
        OverlayRankRow(
            label = title(item),
            value = count(item),
            expanded = expanded,
            detailsLabel = detailsLabel,
            onClick = { onSelect(itemKey(item)) },
        )
    }
    OverlayRankList(
        status = status,
        emptyLabel = emptyLabel,
        rows = remaining,
        itemKey = itemKey,
        unsupported = unsupported,
        pinnedKey = pinnedKey,
        pinnedContent = {
            OverlayPinnedItem(pinned, itemKey, header = { header(it, true) }, details = details)
        },
        rowContent = { header(it, false) },
    )
}

private fun <T> List<T>.withRoomForPinned(pinnedKey: String?, itemKey: (T) -> String): List<T> =
    if (pinnedKey == null) this
    else filterNot { itemKey(it) == pinnedKey }.take((size - 1).coerceAtLeast(0))

@Composable
private fun <T> OverlayRankList(
    status: ConnectionMonitorStatus,
    emptyLabel: Int,
    rows: List<T>,
    itemKey: (T) -> String,
    unsupported: Boolean = false,
    pinnedKey: String?,
    pinnedContent: @Composable () -> Unit,
    rowContent: @Composable (T) -> Unit,
) {
    val spacing = LocalSpacing.current
    val listState = rememberLazyListState()
    val reducedMotion = LocalReduceMotion.current
    val fade = AsteriskMotion.contentFade()
    LaunchedEffect(pinnedKey) {
        if (pinnedKey != null) {
            if (reducedMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }
    Box(Modifier.fillMaxSize().padding(horizontal = spacing.extraSmall)) {
        if (rows.isEmpty() && pinnedKey == null) {
            val message = when {
                unsupported -> R.string.monitor_dns_unsupported
                status == ConnectionMonitorStatus.ServiceStopped -> R.string.monitor_service_not_enabled
                status == ConnectionMonitorStatus.Error -> R.string.monitor_data_unavailable
                status == ConnectionMonitorStatus.Loading -> R.string.monitor_loading
                else -> emptyLabel
            }
            Text(
                text = stringResource(message),
                modifier = Modifier.align(Alignment.TopCenter).padding(spacing.medium),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = spacing.medium),
                state = listState,
            ) {
                item(key = "pinned") { pinnedContent() }
                // Keep the viewport anchored to rank positions as the order changes.
                items(rows) { row ->
                    // The pinned item's size transition already moves the rows below it.
                    Column(Modifier.animateItem(fadeInSpec = fade, placementSpec = null, fadeOutSpec = fade)) {
                        key(itemKey(row)) { rowContent(row) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayRankRow(
    label: String,
    value: String,
    source: String = "",
    expanded: Boolean = false,
    detailsLabel: Int = R.string.monitor_connections_details,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val styles = MaterialTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClickLabel = stringResource(detailsLabel), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            OverlayRankTitle(label, styles.bodyMedium)
            if (source.isNotBlank()) {
                Text(
                    text = source,
                    style = styles.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = value,
            modifier = Modifier.widthIn(min = 36.dp),
            style = styles.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            maxLines = 1,
            textAlign = TextAlign.End,
        )
        AsteriskExpansionIndicator(expanded = expanded, modifier = Modifier.size(16.dp), tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun OverlayRankTitle(text: String, style: TextStyle) {
    val marquee = if (LocalReduceMotion.current) {
        Modifier
    } else {
        val velocity = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 24.dp else (-24).dp
        Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = velocity)
    }
    Text(
        text = text,
        modifier = marquee,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}
