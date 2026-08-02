// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import engine.mihomo.MihomoControlConfig

internal data class MihomoTrafficSample(
    val up: Long = 0L,
    val down: Long = 0L,
    val totalUp: Long? = null,
    val totalDown: Long? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
) {
    val speed: Long
        get() = up + down
}

internal data class MihomoTrafficState(
    val latest: MihomoTrafficSample = MihomoTrafficSample(),
    val totalUp: Long = 0L,
    val totalDown: Long = 0L,
    val connected: Boolean = false,
)

internal data class MihomoMemoryState(
    val inUseBytes: Long = 0L,
    val osLimitBytes: Long = 0L,
)

internal data class MihomoDeviceState(
    val intranetIp: String = "",
    val updatedAtMillis: Long = 0L,
)

internal data class MihomoVersionState(
    val version: String = "",
)

internal data class MihomoConfigsState(
    val mode: String = "",
    val mixedPort: Int? = null,
)

internal data class MihomoProxyNodeId(
    val name: String,
    val providerName: String? = null,
)

internal enum class MihomoDelayStatus {
    Success,
    Timeout,
    Failed,
}

internal data class MihomoDelayMeasurement(
    val id: MihomoProxyNodeId,
    val status: MihomoDelayStatus,
    val delay: Int? = null,
    val error: String = "",
)

internal data class MihomoProxyNode(
    val id: MihomoProxyNodeId,
    val type: String,
    val title: String = id.name,
    val subtitle: String = type,
    val udp: Boolean = false,
    val delay: Int? = null,
    val delayStatus: MihomoDelayStatus? = delay?.let { MihomoDelayStatus.Success },
    val delayError: String = "",
) {
    val name: String
        get() = id.name

    val providerName: String?
        get() = id.providerName

    constructor(
        name: String,
        type: String,
        udp: Boolean = false,
        delay: Int? = null,
    ) : this(
        id = MihomoProxyNodeId(name),
        type = type,
        udp = udp,
        delay = delay,
    )
}

internal data class MihomoProxyGroup(
    val name: String,
    val type: String,
    val now: String = "",
    val all: List<MihomoProxyNodeId> = emptyList(),
    val hidden: Boolean = false,
    val icon: String = "",
    val testUrl: String = "",
)

internal data class MihomoProxiesState(
    val groups: List<MihomoProxyGroup> = emptyList(),
    val nodes: List<MihomoProxyNode> = emptyList(),
    val nodeById: Map<MihomoProxyNodeId, MihomoProxyNode> = nodes.associateBy(MihomoProxyNode::id),
    val updatedAtMillis: Long = 0L,
) {
    fun node(id: MihomoProxyNodeId): MihomoProxyNode {
        return nodeById[id] ?: MihomoProxyNode(id = id, type = "Proxy")
    }

    fun node(name: String): MihomoProxyNode {
        return node(MihomoProxyNodeId(name))
    }
}

internal data class MihomoProxyProviderNode(
    val name: String,
    val title: String = "",
    val subtitle: String = "",
    val type: String = "",
    val delay: Int? = null,
    val delayStatus: MihomoDelayStatus? = delay?.let { MihomoDelayStatus.Success },
    val delayError: String = "",
)

internal data class MihomoProviderSubscriptionInfo(
    val upload: Long = 0L,
    val download: Long = 0L,
    val total: Long = 0L,
    val expire: Long = 0L,
)

internal data class MihomoProxyProviderRuntimeDetail(
    val name: String = "",
    val type: String = "",
    val vehicleType: String = "",
    val updatedAtMillis: Long = 0L,
    val testUrl: String = "",
    val expectedStatus: String = "",
    val subscriptionInfo: MihomoProviderSubscriptionInfo? = null,
    val nodes: List<MihomoProxyProviderNode> = emptyList(),
)

internal data class MihomoRuleProviderRuntimeSummary(
    val name: String,
    val behavior: String = "",
    val format: String = "",
    val ruleCount: Int = 0,
    val type: String = "",
    val vehicleType: String = "",
    val updatedAtMillis: Long = 0L,
)

internal data class MihomoRuntimeState(
    val running: Boolean = false,
    val control: MihomoControlConfig = MihomoControlConfig(),
    val traffic: MihomoTrafficState = MihomoTrafficState(),
    val memory: MihomoMemoryState = MihomoMemoryState(),
    val device: MihomoDeviceState = MihomoDeviceState(),
    val version: MihomoVersionState = MihomoVersionState(),
    val configs: MihomoConfigsState = MihomoConfigsState(),
    val proxies: MihomoProxiesState = MihomoProxiesState(),
    val proxiesRefreshing: Boolean = false,
    val delayTestingTarget: MihomoDelayTarget? = null,
    val lastError: String = "",
)

internal data class MihomoDelayResult(
    val measurements: Map<MihomoProxyNodeId, MihomoDelayMeasurement> = emptyMap(),
) {
    val delays: Map<MihomoProxyNodeId, Int>
        get() = measurements.mapNotNull { (id, measurement) ->
            measurement.delay?.let { delay -> id to delay }
        }.toMap()

    val firstDelay: Int?
        get() = measurements.values.firstNotNullOfOrNull(MihomoDelayMeasurement::delay)

    fun measurement(id: MihomoProxyNodeId): MihomoDelayMeasurement? = measurements[id]
}

internal sealed interface MihomoDelayTarget {
    data class Node(val id: MihomoProxyNodeId) : MihomoDelayTarget

    data class Group(val name: String) : MihomoDelayTarget

    data class Provider(val name: String) : MihomoDelayTarget
}

internal fun MihomoRuntimeState.clearDelayTestingTarget(
    completedTarget: MihomoDelayTarget,
): MihomoRuntimeState {
    return if (delayTestingTarget == completedTarget) {
        copy(delayTestingTarget = null)
    } else {
        this
    }
}
