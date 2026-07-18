// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.resource

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.R
import features.monitoring.MonitoringIntent
import features.monitoring.MonitoringResourceFocusState
import features.monitoring.MonitoringResourceSummary
import features.monitoring.MonitoringScaffold
import features.monitoring.MonitoringSectionCard
import features.monitoring.MonitoringStatusHeader
import features.monitoring.MonitoringValueRow
import features.monitoring.ObserveMonitoring
import features.monitoring.buildMonitoringResourceFocusState
import ui.components.AsteriskFilterChip
import ui.layout.rememberPageGutter
import utils.toReadableBytes
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun ResourceMonitorPage(padding: PaddingValues) {
    val horizontalPadding = rememberPageGutter()
    val services = LocalAppServices.current
    val monitoring by services.monitoring.state.collectAsState()
    val resource = monitoring.resource
    var range by rememberSaveable { mutableStateOf(ResourceChartRange.FifteenMinutes) }
    ObserveMonitoring(MonitoringIntent.Resource)

    MonitoringScaffold(
        title = stringResource(R.string.monitor_resource_title),
        outerPadding = padding,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                top = contentPadding.calculateTopPadding() + 8.dp,
                end = horizontalPadding,
                bottom = contentPadding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item("status") {
                ResourceMonitorStatus(
                    state = buildMonitoringResourceFocusState(
                        serviceRunning = monitoring.serviceRunning,
                        summary = resource,
                    ),
                )
            }
            if (monitoring.serviceRunning) {
                item("source") {
                    Column(modifier = ResourceContentModifier) {
                        resource.source?.let { source ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.defaultMinSize(minHeight = 32.dp),
                            ) {
                                Text(
                                    resourceSourceLabel(source),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (source == ProcessStatsSourceKind.CoreProcess) {
                                        R.string.monitor_resource_root_explanation
                                    } else {
                                        R.string.monitor_resource_embedded_explanation
                                    },
                                ),
                                modifier = Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } ?: Text(
                            text = stringResource(R.string.monitor_data_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item("range") {
                    Row(
                        modifier = ResourceContentModifier,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ResourceChartRange.entries.forEach { option ->
                            AsteriskFilterChip(
                                selected = range == option,
                                onClick = { range = option },
                                label = stringResource(
                                    if (option == ResourceChartRange.FifteenMinutes) {
                                        R.string.monitor_resource_15_minutes
                                    } else {
                                        R.string.monitor_resource_1_hour
                                    },
                                ),
                            )
                        }
                    }
                }
                item("chart") {
                    ResourceTrendCard(resource = resource, range = range)
                }
                item("details") {
                    ResourceDetails(resource)
                }
            }
        }
    }
}

@Composable
private fun ResourceMonitorStatus(state: MonitoringResourceFocusState) {
    MonitoringStatusHeader(
        title = stringResource(R.string.monitor_resource_cpu),
        value = state.cpuPercent?.let { "%.1f%%".format(it) } ?: "—",
        summary = if (state.serviceRunning) {
            stringResource(
                R.string.monitor_resource_focus_memory,
                state.memoryBytes?.toReadableBytes() ?: "—",
            )
        } else {
            stringResource(R.string.monitor_service_not_enabled)
        },
        modifier = ResourceContentModifier,
    )
}

@Composable
private fun ResourceTrendCard(resource: MonitoringResourceSummary, range: ResourceChartRange) {
    val samples = if (range == ResourceChartRange.FifteenMinutes) {
        resource.fifteenMinuteSamples
    } else {
        resource.oneHourSamples
    }
    val cpuColor = MaterialTheme.colorScheme.primary
    val memoryColor = MaterialTheme.colorScheme.tertiary
    MonitoringSectionCard(stringResource(R.string.monitor_resource_trend), ResourceContentModifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LegendLabel(cpuColor, stringResource(R.string.monitor_resource_cpu))
                LegendLabel(memoryColor, stringResource(R.string.monitor_resource_memory))
            }
            ResourceChart(
                samples = samples,
                windowMillis = range.windowMillis,
                expectedIntervalMillis = resource.sampleIntervalMillis ?: 1_000L,
                cpuColor = cpuColor,
                memoryColor = memoryColor,
            )
            if (samples.isEmpty()) {
                Text(
                    stringResource(R.string.monitor_no_samples),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegendLabel(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Canvas(Modifier.height(3.dp).widthIn(min = 22.dp, max = 22.dp)) {
            drawLine(color, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = size.height, cap = StrokeCap.Round)
        }
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ResourceChart(
    samples: List<ProcessStatsSample>,
    windowMillis: Long,
    expectedIntervalMillis: Long,
    cpuColor: Color,
    memoryColor: Color,
) {
    Canvas(modifier = Modifier.fillMaxWidth().height(210.dp)) {
        val endTime = samples.lastOrNull()?.timestampMillis ?: System.currentTimeMillis()
        val startTime = endTime - windowMillis
        val visible = samples.filter { it.timestampMillis >= startTime }
        var observedCpuMax = 0.0
        var observedMemoryMax = 0L
        visible.forEach { sample ->
            observedCpuMax = maxOf(observedCpuMax, sample.cpuPercent ?: 0.0)
            observedMemoryMax = maxOf(observedMemoryMax, sample.memoryBytes ?: 0L)
        }
        val cpuMax = maxOf(100.0, observedCpuMax)
        val memoryMax = maxOf(1L, observedMemoryMax).toDouble()
        val maximumDrawPoints = (size.width / 2.dp.toPx()).roundToInt().coerceAtLeast(MinimumChartDrawPoints)
        val cpuSamples = visible.downsampleForChart(maximumDrawPoints, ProcessStatsSample::cpuPercent)
        val memorySamples = visible.downsampleForChart(maximumDrawPoints) { sample ->
            sample.memoryBytes?.toDouble()
        }
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(
                color = Color.Gray.copy(alpha = 0.18f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawSampleSeries(
            samples = cpuSamples,
            startTime = startTime,
            windowMillis = windowMillis,
            gapMillis = (expectedIntervalMillis * 5L) / 2L,
            color = cpuColor,
        ) { sample -> sample.cpuPercent?.div(cpuMax) }
        drawSampleSeries(
            samples = memorySamples,
            startTime = startTime,
            windowMillis = windowMillis,
            gapMillis = (expectedIntervalMillis * 5L) / 2L,
            color = memoryColor,
        ) { sample -> sample.memoryBytes?.toDouble()?.div(memoryMax) }
    }
}

private fun List<ProcessStatsSample>.downsampleForChart(
    maximumPoints: Int,
    value: (ProcessStatsSample) -> Double?,
): List<ProcessStatsSample> {
    if (size <= maximumPoints || size <= 2) return this

    val bucketCount = ((maximumPoints - 2) / ChartPointsPerBucket).coerceAtLeast(1)
    val interiorSize = size - 2
    val bucketSize = ceil(interiorSize.toDouble() / bucketCount).toInt().coerceAtLeast(1)
    val selectedIndices = ArrayList<Int>(maximumPoints + 2)
    selectedIndices += 0

    var bucketStart = 1
    while (bucketStart < lastIndex) {
        val bucketEnd = minOf(lastIndex, bucketStart + bucketSize)
        var minimumIndex: Int? = null
        var maximumIndex: Int? = null
        var minimumValue = Double.POSITIVE_INFINITY
        var maximumValue = Double.NEGATIVE_INFINITY
        for (index in bucketStart until bucketEnd) {
            val current = value(this[index]) ?: continue
            if (current < minimumValue) {
                minimumValue = current
                minimumIndex = index
            }
            if (current > maximumValue) {
                maximumValue = current
                maximumIndex = index
            }
        }
        listOfNotNull(bucketStart, minimumIndex, maximumIndex, bucketEnd - 1)
            .distinct()
            .sorted()
            .forEach(selectedIndices::add)
        bucketStart = bucketEnd
    }
    selectedIndices += lastIndex
    return selectedIndices.distinct().map(::get)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSampleSeries(
    samples: List<ProcessStatsSample>,
    startTime: Long,
    windowMillis: Long,
    gapMillis: Long,
    color: Color,
    value: (ProcessStatsSample) -> Double?,
) {
    var previous: Pair<ProcessStatsSample, Offset>? = null
    samples.forEach { sample ->
        val normalized = value(sample)?.coerceIn(0.0, 1.0) ?: run {
            previous = null
            return@forEach
        }
        val point = Offset(
            x = ((sample.timestampMillis - startTime).toFloat() / windowMillis.toFloat()) * size.width,
            y = size.height - normalized.toFloat() * size.height,
        )
        previous?.takeIf { (old) -> sample.timestampMillis - old.timestampMillis <= gapMillis }
            ?.let { (_, oldPoint) ->
                drawLine(color, oldPoint, point, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        previous = sample to point
    }
}

@Composable
private fun ResourceDetails(resource: MonitoringResourceSummary) {
    var cpuPeak: Double? = null
    var memoryPeak: Long? = null
    resource.oneHourSamples.forEach { sample ->
        sample.cpuPercent?.let { value -> cpuPeak = maxOf(cpuPeak ?: value, value) }
        sample.memoryBytes?.let { value -> memoryPeak = maxOf(memoryPeak ?: value, value) }
    }
    MonitoringSectionCard(stringResource(R.string.monitor_resource_details), ResourceContentModifier) {
        Column {
            MonitoringValueRow(stringResource(R.string.monitor_resource_cpu_peak), cpuPeak?.let { "%.1f%%".format(it) } ?: "—")
            MonitoringValueRow(stringResource(R.string.monitor_resource_memory_peak), memoryPeak?.toReadableBytes() ?: "—")
            MonitoringValueRow(stringResource(R.string.monitor_resource_pid), resource.processId?.toString() ?: "—")
            MonitoringValueRow(
                stringResource(R.string.monitor_resource_interval),
                resource.sampleIntervalMillis?.let { stringResource(R.string.monitor_milliseconds, it) } ?: "—",
            )
        }
    }
}

@Composable
private fun resourceSourceLabel(source: ProcessStatsSourceKind): String = stringResource(
    if (source == ProcessStatsSourceKind.CoreProcess) {
        R.string.monitor_resource_source_root
    } else {
        R.string.monitor_resource_source_embedded
    },
)

private enum class ResourceChartRange(val windowMillis: Long) {
    FifteenMinutes(15L * 60L * 1_000L),
    OneHour(60L * 60L * 1_000L),
}

private const val MinimumChartDrawPoints = 64
private const val ChartPointsPerBucket = 4
private val ResourceContentModifier = Modifier.fillMaxWidth().widthIn(max = 840.dp)
