// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring

import engine.mihomo.runtime.MihomoConnection
import java.util.Locale

internal enum class ConnectionRouteFilter {
    All,
    Proxy,
    Direct,
}

internal enum class ConnectionSort {
    StartedAt,
    Traffic,
    Target,
}

internal fun resolveDisplayedConnections(
    latest: MonitoringConnectionsSummary,
    frozen: MonitoringConnectionsSummary,
    paused: Boolean,
): MonitoringConnectionsSummary = if (paused) frozen else latest

internal fun discardDisplayedConnection(
    summary: MonitoringConnectionsSummary,
    connectionId: String,
): MonitoringConnectionsSummary {
    val remaining = summary.snapshot.connections.filterNot { connection -> connection.id == connectionId }
    val removedCount = summary.snapshot.connections.size - remaining.size
    if (removedCount == 0) return summary
    return summary.copy(
        activeCount = summary.activeCount?.let { count -> (count - removedCount).coerceAtLeast(0) },
        snapshot = summary.snapshot.copy(connections = remaining),
    )
}

internal fun clearDisplayedConnections(
    summary: MonitoringConnectionsSummary,
): MonitoringConnectionsSummary = summary.copy(
    activeCount = 0,
    snapshot = summary.snapshot.copy(connections = emptyList()),
)

internal fun reduceConnections(
    connections: List<MihomoConnection>,
    query: String = "",
    route: ConnectionRouteFilter = ConnectionRouteFilter.All,
    sort: ConnectionSort = ConnectionSort.Traffic,
    descending: Boolean = true,
): List<MihomoConnection> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val filtered = connections.filter { connection ->
        val matchesRoute = when (route) {
            ConnectionRouteFilter.All -> true
            ConnectionRouteFilter.Proxy -> connection.hasKnownRouting() && !connection.isDirectConnection()
            ConnectionRouteFilter.Direct -> connection.isDirectConnection()
        }
        val matchesQuery = normalizedQuery.isEmpty() || connection.searchableText().contains(normalizedQuery)
        matchesRoute && matchesQuery
    }
    return filtered.sortedWith { first, second ->
        val keyComparison = when (sort) {
            ConnectionSort.StartedAt -> compareValues(first.startedAtMillis ?: Long.MIN_VALUE, second.startedAtMillis ?: Long.MIN_VALUE)
            ConnectionSort.Traffic -> compareValues(first.sortableRateOrTraffic(), second.sortableRateOrTraffic())
            ConnectionSort.Target -> first.destinationAddress.compareTo(second.destinationAddress, ignoreCase = true)
        }
        val directed = if (descending) -keyComparison else keyComparison
        if (directed != 0) directed else first.id.compareTo(second.id)
    }
}

internal fun deriveConnectionRates(
    previous: engine.mihomo.runtime.MihomoConnectionsState?,
    current: engine.mihomo.runtime.MihomoConnectionsState,
): engine.mihomo.runtime.MihomoConnectionsState {
    val elapsedMillis = current.updatedAtMillis - (previous?.updatedAtMillis ?: current.updatedAtMillis)
    if (previous == null || elapsedMillis <= 0L) return current
    val previousById = previous.connections.associateBy(MihomoConnection::id)
    return current.copy(
        connections = current.connections.map { connection ->
            val old = previousById[connection.id]
            connection.copy(
                uploadBytesPerSecond = old?.let {
                    bytesPerSecond(it.uploadBytes, connection.uploadBytes, elapsedMillis)
                },
                downloadBytesPerSecond = old?.let {
                    bytesPerSecond(it.downloadBytes, connection.downloadBytes, elapsedMillis)
                },
            )
        },
    )
}

private fun MihomoConnection.searchableText(): String {
    return buildString {
        append(destinationAddress)
        append(' ')
        append(sourceAddress)
        append(' ')
        append(process)
        append(' ')
        append(processPath)
        append(' ')
        append(chains.joinToString(" "))
        append(' ')
        append(providerChains.joinToString(" "))
        append(' ')
        append(rule)
        append(' ')
        append(rulePayload)
    }.lowercase(Locale.ROOT)
}

private fun MihomoConnection.totalBytes(): Long {
    val upload = uploadBytes.coerceAtLeast(0L)
    val download = downloadBytes.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - upload < download) Long.MAX_VALUE else upload + download
}

private fun MihomoConnection.isDirectConnection(): Boolean {
    return (chains + providerChains).any { chain -> chain.equals("DIRECT", ignoreCase = true) }
}

private fun MihomoConnection.hasKnownRouting(): Boolean {
    return chains.isNotEmpty() || providerChains.isNotEmpty()
}

private fun MihomoConnection.sortableRateOrTraffic(): Long {
    val upload = uploadBytesPerSecond
    val download = downloadBytesPerSecond
    return if (upload != null || download != null) {
        saturatedConnectionAdd(upload ?: 0L, download ?: 0L)
    } else {
        totalBytes()
    }
}

private fun bytesPerSecond(previous: Long, current: Long, elapsedMillis: Long): Long? {
    if (previous < 0L || current < previous || elapsedMillis <= 0L) return null
    return ((current - previous).toDouble() * 1_000.0 / elapsedMillis.toDouble())
        .coerceIn(0.0, Long.MAX_VALUE.toDouble())
        .toLong()
}

private fun saturatedConnectionAdd(first: Long, second: Long): Long {
    val safeFirst = first.coerceAtLeast(0L)
    val safeSecond = second.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - safeFirst < safeSecond) Long.MAX_VALUE else safeFirst + safeSecond
}
