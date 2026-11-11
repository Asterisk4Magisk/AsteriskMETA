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
)

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
    val delayUpdatedAtMillis: Long? = null,
) {
    val name: String
        get() = id.name

    val providerName: String?
        get() = id.providerName
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
    val topLevelProxyIds: Set<MihomoProxyNodeId> = nodes.mapTo(linkedSetOf(), MihomoProxyNode::id),
    val updatedAtMillis: Long = 0L,
) {
    fun node(id: MihomoProxyNodeId): MihomoProxyNode {
        return nodeById[id] ?: MihomoProxyNode(id = id, type = "Proxy")
    }

    fun node(name: String): MihomoProxyNode {
        return node(MihomoProxyNodeId(name))
    }
}

/**
 * The node carrying traffic: the first visible group (GLOBAL in Global mode, the profile's main
 * group in Rule mode, as the snapshot orders them) followed down nested groups. The id keeps the
 * provider of the member it was selected from. Nesting is shallow, so a linear lookup per level
 * beats building a map on every snapshot; the hop limit stops a selection cycle.
 */
internal fun MihomoProxiesState.activeNodeId(): MihomoProxyNodeId? {
    var group = groups.firstOrNull { candidate -> candidate.now.isNotBlank() } ?: return null
    repeat(groups.size) {
        val name = group.now
        val id = group.all.firstOrNull { member -> member.name == name } ?: MihomoProxyNodeId(name)
        val next = groups.firstOrNull { candidate -> candidate.name == name }
            ?.takeIf { nested -> nested.now.isNotBlank() && nested.now != name }
            ?: return id
        group = next
    }
    return MihomoProxyNodeId(group.now)
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
    // The running test is a background probe a user test may preempt; only meaningful while
    // [delayTestingTarget] is set, so the resets that clear the target need not clear it.
    val delayTestingAutomatic: Boolean = false,
    val lastError: String = "",
)

/** True when a user-started delay test may begin now (nothing runs, or only a background probe). */
internal val MihomoRuntimeState.canStartUserDelayTest: Boolean
    get() = delayTestingTarget == null || delayTestingAutomatic

internal data class MihomoDelayResult(
    val measurements: Map<MihomoProxyNodeId, MihomoDelayMeasurement> = emptyMap(),
) {
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
