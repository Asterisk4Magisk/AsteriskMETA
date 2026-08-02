// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import engine.mihomo.runtime.MihomoProxyProviderNode
import engine.mihomo.runtime.MihomoProxyProviderRuntimeDetail
import engine.mihomo.runtime.MihomoDelayResult
import engine.mihomo.runtime.MihomoDelayStatus
import engine.mihomo.runtime.withDelayResult
import features.mihomo.displayMihomoProtocolName

internal enum class ProviderReadiness {
    Loading,
    Ready,
    Empty,
    Error,
}

internal data class ProxyProviderFocusState(
    val readiness: ProviderReadiness,
    val providerCount: Int,
    val readyCount: Int,
    val nodeCount: Int,
    val updatedAtMillis: Long,
)

internal fun reduceProxyProviderFocusState(
    providerCount: Int,
    runtimeDetails: Collection<MihomoProxyProviderRuntimeDetail>,
    loading: Boolean,
    error: String,
): ProxyProviderFocusState {
    val readiness = when {
        error.isNotBlank() -> ProviderReadiness.Error
        loading -> ProviderReadiness.Loading
        providerCount == 0 -> ProviderReadiness.Empty
        else -> ProviderReadiness.Ready
    }
    return ProxyProviderFocusState(
        readiness = readiness,
        providerCount = providerCount.coerceAtLeast(0),
        readyCount = runtimeDetails.size.coerceAtMost(providerCount.coerceAtLeast(0)),
        nodeCount = runtimeDetails.sumOf { detail -> detail.nodes.size },
        updatedAtMillis = runtimeDetails.maxOfOrNull { detail -> detail.updatedAtMillis } ?: 0L,
    )
}

internal enum class ProxyProviderNodeFilter {
    All,
    Available,
    Timeout,
}

internal fun proxyProviderNodeFilterForPage(page: Int): ProxyProviderNodeFilter {
    return ProxyProviderNodeFilter.entries.getOrNull(page) ?: ProxyProviderNodeFilter.All
}

internal fun reduceMihomoProxyProviderNodes(
    nodes: List<MihomoProxyProviderNode>,
    query: String,
    filter: ProxyProviderNodeFilter = ProxyProviderNodeFilter.All,
): List<MihomoProxyProviderNode> {
    val normalizedQuery = query.trim()
    return nodes.filter { node ->
        val matchesQuery = normalizedQuery.isEmpty() || listOf(
            node.name,
            node.title,
            node.subtitle,
            node.type,
            node.type.displayMihomoProtocolName(),
        ).any { value -> value.contains(normalizedQuery, ignoreCase = true) }
        val matchesFilter = when (filter) {
            ProxyProviderNodeFilter.All -> true
            ProxyProviderNodeFilter.Available -> node.delayStatus == MihomoDelayStatus.Success
            ProxyProviderNodeFilter.Timeout -> node.delayStatus == MihomoDelayStatus.Timeout
        }
        matchesQuery && matchesFilter
    }
}

internal fun mergeMihomoProviderRuntimeRefresh(
    detail: MihomoProxyProviderRuntimeDetail,
    latestDelayResult: MihomoDelayResult?,
): MihomoProxyProviderRuntimeDetail {
    return latestDelayResult?.let(detail::withDelayResult) ?: detail
}

internal fun mergeMihomoProviderDelayResults(
    previous: MihomoDelayResult?,
    latest: MihomoDelayResult,
): MihomoDelayResult {
    if (previous == null || previous.measurements.isEmpty()) return latest
    if (latest.measurements.isEmpty()) return previous
    return MihomoDelayResult(previous.measurements + latest.measurements)
}

internal fun ruleProviderChipLabels(
    vehicle: String,
    behavior: String,
    format: String,
): List<String> = buildList {
    add(vehicle)
    behavior.takeIf(String::isNotBlank)?.let(::add)
    format.takeIf(String::isNotBlank)?.let(::add)
}
