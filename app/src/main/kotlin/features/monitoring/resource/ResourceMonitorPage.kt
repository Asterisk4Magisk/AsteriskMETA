// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.resource

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import android.text.format.DateUtils
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import features.monitoring.MonitoringIntent
import features.monitoring.MonitoringResourceSummary
import features.monitoring.MonitoringScaffold
import features.monitoring.MonitoringSectionCard
import features.monitoring.MonitoringStatusHeader
import features.monitoring.MonitoringValueRow
import features.monitoring.ObserveMonitoring
import app.R
import ui.charts.ChartAxisLabelMinWidth
import ui.charts.ChartAxisLabelSpacing
import ui.charts.drawSmoothAreaLine
import ui.components.AsteriskFilterChip
import ui.components.AsteriskSegmentItem
import ui.components.AsteriskSegmentedControl
import ui.layout.rememberPageGutter
import utils.toReadableBytes
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun ResourceMonitorPage(padding: PaddingValues) {
    val horizontalPadding = rememberPageGutter()
    val services = LocalAppServices.current
    val monitoring by services.monitoring.state.collectAsState()
    val resource = monitoring.resource
    var sourceInfo by rememberSaveable { mutableStateOf<ProcessStatsSourceKind?>(null) }
    var range by rememberSaveable { mutableStateOf(ResourceChartRange.FifteenMinutes) }
    val chartWindowSamples = resource.samplesFor(range)
    val chart = remember(chartWindowSamples, range, resource.sampleIntervalMillis) {
        buildResourceChartData(
            samples = chartWindowSamples,
            windowMillis = range.windowMillis,
            intervalMillis = resource.sampleIntervalMillis ?: 1_000L,
        )
    }
    // The resource intent also keeps the connection count and today's traffic current, at the
    // lightweight home cadence, for the details card.
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
            if (!monitoring.serviceRunning) {
                item("status") {
                    MonitoringStatusHeader(
                        title = stringResource(R.string.monitor_resource_cpu),
                        value = "—",
                        summary = stringResource(R.string.monitor_service_not_enabled),
                        modifier = ResourceContentModifier,
                        compactStatus = true,
                    )
                }
            }
            if (monitoring.serviceRunning) {
                item("process") {
                    Row(
                        modifier = ResourceContentModifier,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        resource.source?.let { source ->
                            AsteriskFilterChip(
                                selected = true,
                                onClick = { sourceInfo = source },
                                label = resourceSourceLabel(source),
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Info,
                                        contentDescription = stringResource(R.string.monitor_resource_source_info),
                                    )
                                },
                            )
                        } ?: Text(
                            text = stringResource(R.string.monitor_data_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item("range") {
                    AsteriskSegmentedControl(
                        items = ResourceChartRange.entries.map { option ->
                            AsteriskSegmentItem(
                                option,
                                stringResource(
                                    if (option == ResourceChartRange.FifteenMinutes) {
                                        R.string.monitor_resource_15_minutes
                                    } else {
                                        R.string.monitor_resource_1_hour
                                    },
                                ),
                            )
                        },
                        selectedValue = range,
                        onSelected = { option -> range = option },
                        modifier = ResourceContentModifier,
                    )
                }
                item("cpu_card") {
                    ResourceMetricCard(
                        title = stringResource(R.string.monitor_resource_card_cpu),
                        value = resource.cpuPercent?.let { "%.1f%%".format(it) } ?: "—",
                        amplitudes = chart.cpu,
                        scaleLabel = chart.cpuScaleLabel,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item("memory_card") {
                    ResourceMetricCard(
                        title = stringResource(R.string.monitor_resource_card_memory),
                        value = resource.memoryBytes?.toReadableBytes() ?: "—",
                        amplitudes = chart.memory,
                        scaleLabel = chart.memoryScaleLabel,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                item("details") {
                    ResourceDetails(
                        resource = resource,
                        todayTrafficBytes = monitoring.traffic.today.total,
                        activeConnectionCount = monitoring.connections.activeCount,
                    )
                }
            }
        }
    }
    sourceInfo?.let { source ->
        AlertDialog(
            onDismissRequest = { sourceInfo = null },
            title = { Text(stringResource(R.string.monitor_resource_source_info)) },
            text = {
                Text(stringResource(
                    when (source) {
                        ProcessStatsSourceKind.CoreProcess -> R.string.monitor_resource_root_explanation
                        ProcessStatsSourceKind.VpnProcess -> R.string.monitor_resource_vpn_explanation
                    },
                ))
            },
            confirmButton = {
                TextButton(onClick = { sourceInfo = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun ResourceMetricCard(
    title: String,
    value: String,
    amplitudes: FloatArray,
    scaleLabel: String?,
    color: Color,
) {
    MonitoringSectionCard(title, ResourceContentModifier) {
        // The section card wraps its content in a Box, so the value and the plot need an explicit
        // column: without it the two would be drawn on top of each other.
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            ResourceMetricPlot(amplitudes = amplitudes, scaleLabel = scaleLabel, color = color)
        }
    }
}

@Composable
private fun ResourceMetricPlot(
    amplitudes: FloatArray,
    scaleLabel: String?,
    color: Color,
) {
    if (amplitudes.all { value -> value.isNaN() }) {
        Text(
            stringResource(R.string.monitor_no_samples),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(modifier = Modifier.fillMaxWidth().height(ResourceChartHeight)) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            drawSmoothAreaLine(
                values = amplitudes,
                color = color,
                strokeWidth = ResourceChartLineWidth.toPx(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = ChartAxisLabelSpacing)
                .widthIn(min = ChartAxisLabelMinWidth),
            horizontalAlignment = Alignment.End,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.TopEnd,
            ) {
                ResourceAxisLabel(scaleLabel)
            }
        }
    }
}

@Composable
private fun ResourceAxisLabel(text: String?) {
    if (text == null) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/**
 * Each series is a smooth line over equal time buckets; a bucket keeps its peak, so a spike is never
 * averaged away. CPU and memory have their own scale, and the axis label states that scale's top.
 */
private class ResourceChartData(
    val cpu: FloatArray,
    val memory: FloatArray,
    val cpuScalePercent: Double,
    val memoryScaleBytes: Long,
) {
    val cpuScaleLabel: String?
        get() = cpuScalePercent.takeIf { it > 0.0 }?.let { value -> "%.1f%%".format(value) }

    val memoryScaleLabel: String?
        get() = memoryScaleBytes.takeIf { it > 0L }?.toReadableBytes()
}

private class ResourceSampleStats(
    val cpuAverage: Double?,
    val cpuPeak: Double?,
    val memoryAverage: Long?,
    val memoryPeak: Long?,
)

private fun MonitoringResourceSummary.samplesFor(range: ResourceChartRange): List<ProcessStatsSample> =
    if (range == ResourceChartRange.FifteenMinutes) fifteenMinuteSamples else oneHourSamples

private fun buildResourceChartData(
    samples: List<ProcessStatsSample>,
    windowMillis: Long,
    intervalMillis: Long,
): ResourceChartData {
    if (samples.isEmpty() || windowMillis <= 0L) {
        return ResourceChartData(FloatArray(0), FloatArray(0), 0.0, 0L)
    }
    val endTime = samples.last().timestampMillis
    val interval = intervalMillis.coerceAtLeast(1L)
    // While the history is shorter than the range the axis spans only the history, so an early
    // session fills the card instead of being squeezed into its right-hand edge.
    val visibleWindow = (endTime - samples.first().timestampMillis).coerceIn(interval, windowMillis)
    // A bucket covers at least two sampling intervals: the real sampling period runs a little longer
    // than the nominal one (the read itself takes time), and one-interval buckets would then leave
    // empty buckets that break the line.
    val bucketMillis = maxOf(
        (visibleWindow + ResourceChartSlots - 2) / (ResourceChartSlots - 1),
        interval * ResourceChartMinBucketIntervals,
    )
    val slots = (visibleWindow / bucketMillis + 1).toInt().coerceIn(2, ResourceChartSlots)
    var cpuPeak = 0.0
    var memoryPeak = 0L
    // NaN marks a bucket without any sample, so the line breaks there instead of dropping to zero.
    val cpu = FloatArray(slots) { Float.NaN }
    val memory = FloatArray(slots) { Float.NaN }
    for (index in samples.indices.reversed()) {
        val sample = samples[index]
        // Buckets are aligned to the newest sample, which always owns the rightmost one.
        val slot = slots - 1 - ((endTime - sample.timestampMillis) / bucketMillis).toInt()
        if (slot < 0) break
        sample.cpuPercent?.let { value ->
            cpuPeak = maxOf(cpuPeak, value)
            cpu[slot] = if (cpu[slot].isNaN()) value.toFloat() else maxOf(cpu[slot], value.toFloat())
        }
        sample.memoryBytes?.let { value ->
            memoryPeak = maxOf(memoryPeak, value)
            memory[slot] = if (memory[slot].isNaN()) value.toFloat() else maxOf(memory[slot], value.toFloat())
        }
    }
    // 10% headroom keeps a nearly constant series off the top edge. CPU also has a floor, so an idle
    // core's sub-percent noise stays a low line instead of swinging across the whole plot.
    val cpuScale = maxOf(cpuPeak * ResourceChartHeadroom, ResourceCpuScaleFloorPercent)
    val memoryScale = (maxOf(memoryPeak, 1L) * ResourceChartHeadroom).toLong()
    for (slot in 0 until slots) {
        cpu[slot] = (cpu[slot] / cpuScale).toFloat()
        memory[slot] = memory[slot] / memoryScale
    }
    return ResourceChartData(
        cpu = cpu,
        memory = memory,
        cpuScalePercent = cpuScale,
        memoryScaleBytes = memoryScale,
    )
}

private fun buildResourceSampleStats(samples: List<ProcessStatsSample>): ResourceSampleStats {
    var cpuPeak: Double? = null
    var cpuSum = 0.0
    var cpuCount = 0
    var memoryPeak: Long? = null
    var memorySum = 0.0
    var memoryCount = 0
    samples.forEach { sample ->
        sample.cpuPercent?.let { value ->
            cpuPeak = maxOf(cpuPeak ?: value, value)
            cpuSum += value
            cpuCount += 1
        }
        sample.memoryBytes?.let { value ->
            memoryPeak = maxOf(memoryPeak ?: value, value)
            memorySum += value
            memoryCount += 1
        }
    }
    return ResourceSampleStats(
        cpuAverage = if (cpuCount > 0) cpuSum / cpuCount else null,
        cpuPeak = cpuPeak,
        memoryAverage = if (memoryCount > 0) (memorySum / memoryCount).toLong() else null,
        memoryPeak = memoryPeak,
    )
}

@Composable
private fun ResourceDetails(
    resource: MonitoringResourceSummary,
    todayTrafficBytes: Long,
    activeConnectionCount: Int?,
) {
    // Averages and peaks cover the whole recorded history, independent of the chart range above.
    val stats = remember(resource.oneHourSamples) { buildResourceSampleStats(resource.oneHourSamples) }
    MonitoringSectionCard(stringResource(R.string.monitor_resource_details), ResourceContentModifier) {
        Column {
            MonitoringValueRow(
                stringResource(R.string.monitor_resource_cpu_average),
                stats.cpuAverage?.let { "%.1f%%".format(it) } ?: "—",
            )
            MonitoringValueRow(
                stringResource(R.string.monitor_resource_cpu_peak),
                stats.cpuPeak?.let { "%.1f%%".format(it) } ?: "—",
            )
            MonitoringValueRow(
                stringResource(R.string.monitor_resource_memory_average),
                stats.memoryAverage?.toReadableBytes() ?: "—",
            )
            MonitoringValueRow(
                stringResource(R.string.monitor_resource_memory_peak),
                stats.memoryPeak?.toReadableBytes() ?: "—",
            )
            MonitoringValueRow(
                stringResource(R.string.monitor_resource_uptime),
                resource.uptimeMillis?.let(::formatResourceUptime) ?: "—",
            )
            MonitoringValueRow(
                stringResource(R.string.monitor_resource_today_traffic),
                todayTrafficBytes.toReadableBytes(),
            )
            MonitoringValueRow(
                stringResource(R.string.monitor_resource_active_connections),
                activeConnectionCount?.toString() ?: "—",
            )
            MonitoringValueRow(stringResource(R.string.monitor_resource_pid), resource.processId?.toString() ?: "—")
            MonitoringValueRow(
                stringResource(R.string.monitor_resource_interval),
                resource.sampleIntervalMillis?.let { stringResource(R.string.monitor_milliseconds, it) } ?: "—",
            )
        }
    }
}

private fun formatResourceUptime(uptimeMillis: Long): String =
    DateUtils.formatElapsedTime(uptimeMillis.coerceAtLeast(0L) / 1_000L)

@Composable
private fun resourceSourceLabel(source: ProcessStatsSourceKind): String = stringResource(
    when (source) {
        ProcessStatsSourceKind.CoreProcess -> R.string.monitor_resource_source_root
        ProcessStatsSourceKind.VpnProcess -> R.string.monitor_resource_source_vpn
    },
)

private enum class ResourceChartRange(val windowMillis: Long) {
    FifteenMinutes(15L * 60L * 1_000L),
    OneHour(60L * 60L * 1_000L),
}

private const val ResourceChartSlots = 64
private const val ResourceChartHeadroom = 1.1
private const val ResourceChartMinBucketIntervals = 2L
private const val ResourceCpuScaleFloorPercent = 5.0

private val ResourceChartHeight = 160.dp
private val ResourceChartLineWidth = 2.dp
private val ResourceContentModifier = Modifier.fillMaxWidth().widthIn(max = 840.dp)
