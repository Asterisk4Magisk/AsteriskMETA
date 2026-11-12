// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring

import engine.mihomo.runtime.MihomoConnectionsState
import engine.mihomo.runtime.connectionCounts
import features.monitoring.resource.ProcessStatsSample
import features.monitoring.resource.ProcessStatsSourceKind
import features.monitoring.network.LocalNetworkSnapshot
import features.monitoring.network.PublicNetworkProbeState
import features.monitoring.traffic.TrafficBytes

internal data class MonitoringResourceSummary(
    val cpuPercent: Double? = null,
    val memoryBytes: Long? = null,
    val source: ProcessStatsSourceKind? = null,
    val uptimeMillis: Long? = null,
    val uptimeMeasuredAtElapsedMillis: Long = 0L,
    val processId: Int? = null,
    val memoryLimitBytes: Long? = null,
    val sampleIntervalMillis: Long? = null,
    val fifteenMinuteSamples: List<ProcessStatsSample> = emptyList(),
    val oneHourSamples: List<ProcessStatsSample> = emptyList(),
)

internal enum class ConnectionMonitorStatus {
    ServiceStopped,
    Loading,
    Available,
    Error,
}

internal data class MonitoringConnectionsSummary(
    val activeCount: Int? = null,
    val directCount: Int? = null,
    val proxyCount: Int? = null,
    val uploadBytesPerSecond: Long? = null,
    val downloadBytesPerSecond: Long? = null,
    val sessionUploadBytes: Long? = null,
    val sessionDownloadBytes: Long? = null,
    val snapshot: MihomoConnectionsState = MihomoConnectionsState(),
    val status: ConnectionMonitorStatus = ConnectionMonitorStatus.ServiceStopped,
    val error: String = "",
    val stale: Boolean = false,
)

internal data class MonitoringConnectionsFocusState(
    val status: ConnectionMonitorStatus,
    val activeCount: Int?,
    val proxyCount: Int,
    val directCount: Int,
    val error: String = "",
)

internal fun buildMonitoringConnectionsFocusState(
    summary: MonitoringConnectionsSummary,
): MonitoringConnectionsFocusState {
    val counts = summary.snapshot.connections.connectionCounts()
    return MonitoringConnectionsFocusState(
        status = summary.status,
        activeCount = summary.activeCount,
        proxyCount = counts.proxyCount,
        directCount = counts.directCount,
        error = summary.error,
    )
}

internal data class MonitoringTrafficSpeedSample(
    val timestampMillis: Long,
    val uploadBytesPerSecond: Long,
    val downloadBytesPerSecond: Long,
)

internal data class MonitoringTrafficSummary(
    val uploadBytesPerSecond: Long? = null,
    val downloadBytesPerSecond: Long? = null,
    val sessionUploadBytes: Long? = null,
    val sessionDownloadBytes: Long? = null,
    val today: TrafficBytes = TrafficBytes(),
    val sevenDays: TrafficBytes = TrafficBytes(),
    val thirtyDays: TrafficBytes = TrafficBytes(),
    val dailyTotals: Map<String, TrafficBytes> = emptyMap(),
    val speedSamples: List<MonitoringTrafficSpeedSample> = emptyList(),
)

internal data class MonitoringNetworkSummary(
    val proxyConnected: Boolean = false,
    val local: LocalNetworkSnapshot = LocalNetworkSnapshot(),
    val publicProbe: PublicNetworkProbeState = PublicNetworkProbeState(),
)

internal data class MonitoringState(
    val serviceRunning: Boolean = false,
    val resource: MonitoringResourceSummary = MonitoringResourceSummary(),
    val connections: MonitoringConnectionsSummary = MonitoringConnectionsSummary(),
    val traffic: MonitoringTrafficSummary = MonitoringTrafficSummary(),
    val network: MonitoringNetworkSummary = MonitoringNetworkSummary(),
)
