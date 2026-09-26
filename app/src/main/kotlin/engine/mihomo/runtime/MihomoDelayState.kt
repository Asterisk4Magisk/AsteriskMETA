// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

internal fun MihomoProxiesState.withDelayResult(
    result: MihomoDelayResult,
    testedAtMillis: Long = System.currentTimeMillis(),
): MihomoProxiesState {
    if (result.measurements.isEmpty()) return this
    val updatedNodes = nodes.map { node ->
        val measurement = result.measurement(node.id) ?: return@map node
        node.copy(
            delay = measurement.delay,
            delayStatus = measurement.status,
            delayError = measurement.error,
            delayUpdatedAtMillis = testedAtMillis,
        )
    }
    return copy(
        nodes = updatedNodes,
        nodeById = updatedNodes.associateBy(MihomoProxyNode::id),
        updatedAtMillis = System.currentTimeMillis(),
    )
}

internal fun MihomoProxiesState.withPreservedDelays(previous: MihomoProxiesState): MihomoProxiesState {
    val nodes = nodes.map { node ->
        val previousNode = previous.nodeById[node.id] ?: return@map node
        if (previousNode.delayStatus == null) return@map node
        val previousTime = previousNode.delayUpdatedAtMillis
        val snapshotTime = node.delayUpdatedAtMillis
        // A refreshed snapshot may still contain history from before the manual test.
        // Only a newer measurement can replace a result whose time is known.
        val preservePrevious = node.delayStatus == null ||
            (previousTime != null && (snapshotTime == null || snapshotTime <= previousTime))
        if (preservePrevious) {
            node.copy(
                delay = previousNode.delay,
                delayStatus = previousNode.delayStatus,
                delayError = previousNode.delayError,
                delayUpdatedAtMillis = previousTime,
            )
        } else {
            node
        }
    }
    return copy(nodes = nodes, nodeById = nodes.associateBy(MihomoProxyNode::id))
}

internal fun MihomoProxyProviderRuntimeDetail.withDelayResult(
    result: MihomoDelayResult,
): MihomoProxyProviderRuntimeDetail {
    if (result.measurements.isEmpty()) return this
    return copy(
        nodes = nodes.map { node ->
            val id = MihomoProxyNodeId(node.name, name)
            val measurement = result.measurement(id) ?: return@map node
            node.copy(
                delay = measurement.delay,
                delayStatus = measurement.status,
                delayError = measurement.error,
            )
        },
    )
}
