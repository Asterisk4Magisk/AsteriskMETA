// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

internal fun MihomoProxiesState.withDelayResult(result: MihomoDelayResult): MihomoProxiesState {
    if (result.measurements.isEmpty()) return this
    val updatedNodes = nodes.map { node ->
        val measurement = result.measurement(node.id) ?: return@map node
        node.copy(
            delay = measurement.delay,
            delayStatus = measurement.status,
            delayError = measurement.error,
        )
    }
    return copy(
        nodes = updatedNodes,
        nodeById = updatedNodes.associateBy(MihomoProxyNode::id),
        updatedAtMillis = System.currentTimeMillis(),
    )
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
