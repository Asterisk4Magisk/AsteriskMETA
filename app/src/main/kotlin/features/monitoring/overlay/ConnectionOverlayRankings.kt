// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.overlay

import engine.mihomo.runtime.MihomoConnection
import engine.proxy.RuntimeRouteRule
import features.monitoring.dns.DnsRecord
import features.monitoring.totalRate
import java.util.Locale
import java.util.PriorityQueue

internal data class OverlayConnectionRank(
    val id: String,
    val destination: String,
    val processLabel: String,
    val rate: Long?,
)

internal data class OverlayPinnedConnection(
    val row: OverlayConnectionRank,
    val sourceAddress: String,
    val packageName: String?,
    val uploadRate: Long?,
    val downloadRate: Long?,
)

internal fun MihomoConnection.toOverlayPinnedConnection(packageName: String?) = OverlayPinnedConnection(
    row = toOverlayRank(totalRate()),
    sourceAddress = sourceAddress,
    packageName = packageName,
    uploadRate = uploadBytesPerSecond,
    downloadRate = downloadBytesPerSecond,
)

private fun MihomoConnection.toOverlayRank(rate: Long?) = OverlayConnectionRank(
    id = id,
    destination = destinationAddress,
    processLabel = process,
    rate = rate,
)

internal data class OverlayDnsRank(
    val domain: String,
    val lookups: Int,
    val latestRecord: DnsRecord? = null,
)

internal fun rankOverlayConnections(connections: List<MihomoConnection>): List<OverlayConnectionRank> {
    val order = compareByDescending<OverlayConnectionRank> { it.rate }.thenBy { it.id }
    val highest = PriorityQueue(OverlayRankLimit, order.reversed())
    for (connection in connections) {
        val rate = connection.totalRate()
        val last = highest.peek()
        if (highest.size == OverlayRankLimit && last != null) {
            val rateOrder = compareValues(rate, last.rate)
            if (rateOrder < 0 || (rateOrder == 0 && connection.id >= last.id)) continue
            highest.poll()
        }
        highest.add(connection.toOverlayRank(rate))
    }
    return highest.sortedWith(order)
}

internal fun rankOverlayDns(
    records: List<DnsRecord>,
    selectedDomain: String?,
): Pair<List<OverlayDnsRank>, OverlayDnsRank?> {
    val lookups = HashMap<String, Int>()
    var latestSelection: DnsRecord? = null
    for (record in records) {
        if (record.domain.isBlank()) continue
        val domain = record.domain.trimEnd('.').lowercase(Locale.ROOT).ifEmpty { "." }
        lookups[domain] = lookups.getOrDefault(domain, 0) + 1
        if (domain == selectedDomain) latestSelection = record
    }
    val rows = lookups.entries.sortedWith(
        compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key },
    ).take(OverlayRankLimit).map { OverlayDnsRank(it.key, it.value) }
    val selection = selectedDomain?.let { domain ->
        latestSelection?.let { OverlayDnsRank(domain, lookups.getValue(domain), it) }
    }
    return rows to selection
}

internal fun rankOverlayRules(rules: List<RuntimeRouteRule>): List<RuntimeRouteRule> {
    val order = compareByDescending<RuntimeRouteRule> { it.hits }.thenBy { it.index }
    val highest = PriorityQueue(OverlayRankLimit, order.reversed())
    for (rule in rules) {
        if (rule.hits == null) continue
        if (highest.size == OverlayRankLimit) {
            if (order.compare(rule, highest.element()) >= 0) continue
            highest.poll()
        }
        highest.add(rule)
    }
    return highest.sortedWith(order)
}

private const val OverlayRankLimit = 10
