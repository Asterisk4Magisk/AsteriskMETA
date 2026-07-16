// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.traffic

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.R
import features.monitoring.MonitoringIntent
import features.monitoring.MonitoringScaffold
import features.monitoring.MonitoringSectionCard
import features.monitoring.MonitoringTrafficSpeedSample
import features.monitoring.MonitoringValueRow
import features.monitoring.ObserveMonitoring
import ui.layout.rememberPageGutter
import utils.toReadableBytes

@Composable
internal fun TrafficMonitorPage(padding: PaddingValues) {
    val horizontalPadding = rememberPageGutter()
    val services = LocalAppServices.current
    val monitoring by services.monitoring.state.collectAsState()
    val traffic = monitoring.traffic
    ObserveMonitoring(MonitoringIntent.Traffic)

    MonitoringScaffold(
        title = stringResource(R.string.monitor_traffic_title),
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
            item("today") {
                MonitoringSectionCard(stringResource(R.string.monitor_traffic_today), TrafficContentModifier) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            traffic.today.total.toReadableBytes(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        MonitoringValueRow(
                            stringResource(R.string.monitor_connections_download),
                            traffic.today.download.toReadableBytes(),
                        )
                        MonitoringValueRow(
                            stringResource(R.string.monitor_connections_upload),
                            traffic.today.upload.toReadableBytes(),
                        )
                    }
                }
            }
            item("session") {
                MonitoringSectionCard(stringResource(R.string.monitor_traffic_session), TrafficContentModifier) {
                    Column {
                        MonitoringValueRow(
                            stringResource(R.string.monitor_traffic_session_total),
                            combinedBytes(traffic.sessionDownloadBytes, traffic.sessionUploadBytes),
                        )
                        MonitoringValueRow(
                            stringResource(R.string.monitor_connections_download),
                            traffic.sessionDownloadBytes?.toReadableBytes() ?: "—",
                        )
                        MonitoringValueRow(
                            stringResource(R.string.monitor_connections_upload),
                            traffic.sessionUploadBytes?.toReadableBytes() ?: "—",
                        )
                        MonitoringValueRow(
                            stringResource(R.string.monitor_traffic_runtime),
                            monitoring.resource.uptimeMillis?.let { DateUtils.formatElapsedTime(it / 1_000L) } ?: "—",
                        )
                    }
                }
            }
            item("speed") {
                TrafficSpeedCard(
                    samples = traffic.speedSamples,
                )
            }
            item("seven_days") {
                SevenDayTrafficCard(traffic.dailyTotals, traffic.sevenDays)
            }
            item("thirty_days") {
                MonitoringSectionCard(stringResource(R.string.monitor_traffic_30_days), TrafficContentModifier) {
                    Column {
                        MonitoringValueRow(
                            stringResource(R.string.monitor_traffic_total),
                            traffic.thirtyDays.total.toReadableBytes(),
                        )
                        MonitoringValueRow(
                            stringResource(R.string.monitor_connections_download),
                            traffic.thirtyDays.download.toReadableBytes(),
                        )
                        MonitoringValueRow(
                            stringResource(R.string.monitor_connections_upload),
                            traffic.thirtyDays.upload.toReadableBytes(),
                        )
                        Text(
                            stringResource(R.string.monitor_traffic_local_notice),
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficSpeedCard(
    samples: List<MonitoringTrafficSpeedSample>,
) {
    val downloadColor = MaterialTheme.colorScheme.primary
    val uploadColor = MaterialTheme.colorScheme.tertiary
    MonitoringSectionCard(
        title = stringResource(R.string.monitor_traffic_trend),
        modifier = TrafficContentModifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrafficSpeedChart(samples, downloadColor, uploadColor)
            Text(
                stringResource(R.string.monitor_traffic_last_5_minutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrafficSpeedChart(samples: List<MonitoringTrafficSpeedSample>, downloadColor: Color, uploadColor: Color) {
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val end = samples.lastOrNull()?.timestampMillis ?: System.currentTimeMillis()
        val start = end - FiveMinutesMillis
        val visible = samples.filter { it.timestampMillis >= start }
        val max = maxOf(
            1L,
            visible.maxOfOrNull { sample -> maxOf(sample.downloadBytesPerSecond, sample.uploadBytesPerSecond) } ?: 0L,
        ).toDouble()
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(Color.Gray.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        fun drawSeries(color: Color, selector: (MonitoringTrafficSpeedSample) -> Long) {
            var previous: Pair<MonitoringTrafficSpeedSample, Offset>? = null
            visible.forEach { sample ->
                val point = Offset(
                    x = ((sample.timestampMillis - start).toFloat() / FiveMinutesMillis.toFloat()) * size.width,
                    y = size.height - (selector(sample).toDouble() / max).toFloat() * size.height,
                )
                previous?.takeIf { (old) -> sample.timestampMillis - old.timestampMillis <= 3_500L }
                    ?.let { (_, oldPoint) -> drawLine(color, oldPoint, point, 2.dp.toPx(), cap = StrokeCap.Round) }
                previous = sample to point
            }
        }
        drawSeries(downloadColor, MonitoringTrafficSpeedSample::downloadBytesPerSecond)
        drawSeries(uploadColor, MonitoringTrafficSpeedSample::uploadBytesPerSecond)
    }
}

@Composable
private fun SevenDayTrafficCard(dailyTotals: Map<String, TrafficBytes>, total: TrafficBytes) {
    val today = localTrafficDay(System.currentTimeMillis())
    val days = localTrafficDaysEndingAt(today, 7)
    val values = days.map { day -> dailyTotals[day] ?: TrafficBytes() }
    val downloadColor = MaterialTheme.colorScheme.primary
    val uploadColor = MaterialTheme.colorScheme.tertiary
    MonitoringSectionCard(stringResource(R.string.monitor_traffic_7_days), TrafficContentModifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(total.total.toReadableBytes(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                val max = maxOf(1L, values.maxOfOrNull { it.total } ?: 0L).toFloat()
                val groupWidth = size.width / values.size.coerceAtLeast(1)
                val barWidth = groupWidth * 0.26f
                values.forEachIndexed { index, bytes ->
                    val center = groupWidth * (index + 0.5f)
                    val downloadHeight = size.height * (bytes.download / max)
                    val uploadHeight = size.height * (bytes.upload / max)
                    drawLine(
                        downloadColor,
                        Offset(center - barWidth * 0.65f, size.height),
                        Offset(center - barWidth * 0.65f, size.height - downloadHeight),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        uploadColor,
                        Offset(center + barWidth * 0.65f, size.height),
                        Offset(center + barWidth * 0.65f, size.height - uploadHeight),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                days.forEach { day ->
                    Text(day.takeLast(5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.End),
            ) {
                TrafficLegend(downloadColor, stringResource(R.string.monitor_connections_download))
                TrafficLegend(uploadColor, stringResource(R.string.monitor_connections_upload))
            }
        }
    }
}

@Composable
private fun TrafficLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.height(3.dp).widthIn(min = 20.dp, max = 20.dp)) {
            drawLine(color, Offset(0f, center.y), Offset(size.width, center.y), size.height, cap = StrokeCap.Round)
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun combinedBytes(download: Long?, upload: Long?): String {
    if (download == null && upload == null) return "—"
    val safeDownload = download?.coerceAtLeast(0L) ?: 0L
    val safeUpload = upload?.coerceAtLeast(0L) ?: 0L
    val total = if (Long.MAX_VALUE - safeDownload < safeUpload) Long.MAX_VALUE else safeDownload + safeUpload
    return total.toReadableBytes()
}

private const val FiveMinutesMillis = 5L * 60L * 1_000L
private val TrafficContentModifier = Modifier.fillMaxWidth().widthIn(max = 840.dp)
