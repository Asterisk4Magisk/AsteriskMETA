// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import app.modes.MihomoProxyLayoutAuto
import app.modes.MihomoProxyLayoutDouble
import app.modes.MihomoProxyLayoutMultiple
import app.modes.MihomoProxyLayoutSingle
import app.modes.MihomoProxySortDefault
import app.modes.MihomoProxySortDelay
import app.modes.MihomoProxySortName
import engine.mihomo.runtime.MihomoProxiesState
import engine.mihomo.runtime.MihomoProxyGroup
import engine.mihomo.runtime.MihomoProxyNodeId

internal fun MihomoProxyNodeId.toMihomoProxyLazyItemKey(): String {
    val provider = providerName
    return buildString {
        append("mihomo-proxy:")
        if (provider == null) {
            append("-1:")
        } else {
            append(provider.length).append(':').append(provider)
        }
        append(name.length).append(':').append(name)
    }
}

internal fun filterMihomoProxyGroups(
    proxies: MihomoProxiesState,
    excludeNotSelectable: Boolean,
): MihomoProxiesState {
    if (!excludeNotSelectable) return proxies
    return proxies.copy(groups = proxies.groups.filter(::isMihomoProxyGroupSelectable))
}

internal fun reduceMihomoProxyNodeNames(
    group: MihomoProxyGroup?,
    proxies: MihomoProxiesState,
    query: String,
    sort: Int,
): List<MihomoProxyNodeId> {
    val keyword = query.trim()
    return group?.all
        ?.filter { nodeId ->
            val node = proxies.node(nodeId)
            val displayType = node.type.displayMihomoProtocolName()
            keyword.isEmpty() ||
                node.name.contains(keyword, ignoreCase = true) ||
                node.providerName.orEmpty().contains(keyword, ignoreCase = true) ||
                node.type.contains(keyword, ignoreCase = true) ||
                displayType.contains(keyword, ignoreCase = true)
        }
        ?.sortMihomoProxyNodeNames(proxies, resolveMihomoProxySort(sort))
        .orEmpty()
}

internal fun resolveMihomoProxyLayout(layout: Int, isWideScreen: Boolean): Int {
    return when (layout) {
        MihomoProxyLayoutSingle, MihomoProxyLayoutDouble, MihomoProxyLayoutMultiple -> layout
        MihomoProxyLayoutAuto -> if (isWideScreen) MihomoProxyLayoutMultiple else MihomoProxyLayoutDouble
        else -> if (isWideScreen) MihomoProxyLayoutMultiple else MihomoProxyLayoutDouble
    }
}

internal fun resolveMihomoProxyColumns(layout: Int): Int {
    return when (layout) {
        MihomoProxyLayoutSingle -> 1
        MihomoProxyLayoutMultiple -> 3
        else -> 2
    }
}

internal fun resolveMihomoProxySort(sort: Int): Int {
    return when (sort) {
        MihomoProxySortName, MihomoProxySortDelay -> sort
        else -> MihomoProxySortDefault
    }
}

internal fun isMihomoProxyNodeCurrent(
    group: MihomoProxyGroup,
    nodeName: String,
    pendingSelections: Map<String, String>,
): Boolean {
    return (pendingSelections[group.name] ?: group.now) == nodeName
}

internal fun isMihomoProxyGroupSelectable(group: MihomoProxyGroup): Boolean {
    return when (group.type.normalizedMihomoGroupType()) {
        "select", "selector", "urltest", "fallback" -> true
        else -> false
    }
}

private fun List<MihomoProxyNodeId>.sortMihomoProxyNodeNames(
    proxies: MihomoProxiesState,
    sort: Int,
): List<MihomoProxyNodeId> {
    return when (sort) {
        MihomoProxySortName -> sortedWith(
            compareBy<MihomoProxyNodeId, String>(String.CASE_INSENSITIVE_ORDER) { nodeId ->
                proxies.node(nodeId).name
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { nodeId -> nodeId.providerName.orEmpty() },
        )
        MihomoProxySortDelay -> sortedWith(
            compareBy<MihomoProxyNodeId> { nodeId ->
                proxies.node(nodeId).delay.toMihomoProxyDelaySortValue()
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { nodeId ->
                proxies.node(nodeId).name
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { nodeId -> nodeId.providerName.orEmpty() },
        )
        else -> this
    }
}

private fun Int?.toMihomoProxyDelaySortValue(): Int {
    return when {
        this == null -> Int.MAX_VALUE
        this < 0 -> Int.MAX_VALUE - 1
        else -> this
    }
}

private fun String.normalizedMihomoGroupType(): String {
    return trim().lowercase().replace("-", "").replace("_", "").replace(" ", "")
}
