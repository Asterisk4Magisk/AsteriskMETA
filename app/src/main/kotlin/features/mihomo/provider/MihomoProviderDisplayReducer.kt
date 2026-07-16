// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import engine.mihomo.runtime.MihomoProviderNode
import engine.mihomo.runtime.MihomoProxyProviderRuntimeDetail
import features.mihomo.displayMihomoProtocolName

internal enum class ProviderReadiness {
    Loading,
    Ready,
    Empty,
    Error,
}

internal data class ProviderFocusState(
    val readiness: ProviderReadiness,
    val providerCount: Int,
    val readyCount: Int,
    val nodeCount: Int,
    val updatedAtMillis: Long,
)

internal fun reduceProviderFocusState(
    providerCount: Int,
    runtimeDetails: Collection<MihomoProxyProviderRuntimeDetail>,
    loading: Boolean,
    error: String,
): ProviderFocusState {
    val readiness = when {
        error.isNotBlank() -> ProviderReadiness.Error
        loading -> ProviderReadiness.Loading
        providerCount == 0 -> ProviderReadiness.Empty
        else -> ProviderReadiness.Ready
    }
    return ProviderFocusState(
        readiness = readiness,
        providerCount = providerCount.coerceAtLeast(0),
        readyCount = runtimeDetails.size.coerceAtMost(providerCount.coerceAtLeast(0)),
        nodeCount = runtimeDetails.sumOf { detail -> detail.nodes.size },
        updatedAtMillis = runtimeDetails.maxOfOrNull { detail -> detail.updatedAtMillis } ?: 0L,
    )
}

internal enum class ProviderNodeFilter {
    All,
    Available,
    Timeout,
}

internal fun reduceMihomoProviderNodes(
    nodes: List<MihomoProviderNode>,
    query: String,
    filter: ProviderNodeFilter = ProviderNodeFilter.All,
): List<MihomoProviderNode> {
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
            ProviderNodeFilter.All -> true
            ProviderNodeFilter.Available -> node.delay?.let { it >= 0 } == true
            ProviderNodeFilter.Timeout -> node.delay?.let { it < 0 } == true
        }
        matchesQuery && matchesFilter
    }
}
