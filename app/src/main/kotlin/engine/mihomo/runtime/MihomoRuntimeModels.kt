// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import engine.mihomo.MihomoControlConfig
import java.util.concurrent.atomic.AtomicLong

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
    val fallbackGroupName: String? = null,
)

internal enum class MihomoDelayStatus {
    Success,
    Timeout,
    Failed,
}

internal data class MihomoDelayTaskKey(
    val measuredId: MihomoProxyNodeId,
    val url: String,
    val timeoutMillis: Int,
    val expectedStatus: String,
    val control: MihomoControlConfig,
    val useBridge: Boolean,
)

internal data class MihomoDelayTest(
    val resultId: MihomoProxyNodeId,
    val measuredId: MihomoProxyNodeId = resultId,
)

// Order measurements before shared requests or publication batches can reorder their delivery.
private val delayMeasurementSequence = AtomicLong()

internal data class MihomoDelayMeasurement(
    val id: MihomoProxyNodeId,
    val status: MihomoDelayStatus,
    val delay: Int? = null,
    val error: String = "",
    val measuredAtMillis: Long = System.currentTimeMillis(),
    val sequence: Long = delayMeasurementSequence.incrementAndGet(),
)

internal data class MihomoProxyNode(
    val id: MihomoProxyNodeId,
    val type: String,
    val title: String = id.name,
    val subtitle: String = type,
    val udp: Boolean = false,
    val icon: String = "",
    val delay: Int? = null,
    val delayStatus: MihomoDelayStatus? = delay?.let { MihomoDelayStatus.Success },
    val delayError: String = "",
    val delayUpdatedAtMillis: Long? = null,
    val delayMeasurementSequence: Long? = null,
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
    // Hidden groups remain part of leaf resolution even though the UI does not list them.
    val allGroups: List<MihomoProxyGroup> = groups,
    val groupSources: Map<String, MihomoProxyGroupSources> = emptyMap(),
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
    val group = groups.firstOrNull { candidate -> candidate.now.isNotBlank() } ?: return null
    val id = group.all.firstOrNull { member -> member.name == group.now } ?: MihomoProxyNodeId(group.now)
    return delayTestLeaf(id)
}

internal data class MihomoProxyProviderNode(
    val name: String,
    val title: String = "",
    val subtitle: String = "",
    val type: String = "",
    val delay: Int? = null,
    val delayStatus: MihomoDelayStatus? = delay?.let { MihomoDelayStatus.Success },
    val delayError: String = "",
    val delayUpdatedAtMillis: Long? = null,
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
) {
    val isCompatible: Boolean
        get() = vehicleType.equals("Compatible", ignoreCase = true)
}

internal data class MihomoRuleProviderRuntimeSummary(
    val name: String,
    val behavior: String = "",
    val format: String = "",
    val ruleCount: Int = 0,
    val type: String = "",
    val vehicleType: String = "",
    val updatedAtMillis: Long = 0L,
)

internal data class MihomoDelayProgress(
    val pending: Set<MihomoProxyNodeId> = emptySet(),
    val total: Int = 0,
    val exclusive: Boolean = false,
) {
    val completed: Int
        get() = total - pending.size
}

internal data class MihomoRuntimeState(
    val running: Boolean = false,
    val runtimeEpoch: Long = 0L,
    val control: MihomoControlConfig = MihomoControlConfig(),
    val traffic: MihomoTrafficState = MihomoTrafficState(),
    val memory: MihomoMemoryState = MihomoMemoryState(),
    val device: MihomoDeviceState = MihomoDeviceState(),
    val version: MihomoVersionState = MihomoVersionState(),
    val configs: MihomoConfigsState = MihomoConfigsState(),
    val proxies: MihomoProxiesState = MihomoProxiesState(),
    val proxiesRefreshing: Boolean = false,
    val proxySelectionRevision: Long = 0L,
    val delayTestingTarget: MihomoDelayTarget? = null,
    val delayTestingPending: Set<MihomoProxyNodeId> = emptySet(),
    val delayTestingTotal: Int = 0,
    val groupDelayTesting: Map<String, MihomoDelayProgress> = emptyMap(),
    val nodeDelayTesting: Set<MihomoProxyNodeId> = emptySet(),
    // Properties of [delayTestingTarget]; ignored once that target is cleared.
    val delayTestingAutomatic: Boolean = false,
    val delayTestingExclusive: Boolean = false,
    val lastError: String = "",
)

internal val MihomoRuntimeState.canStartUserDelayTest: Boolean
    get() = nodeDelayTesting.isEmpty() && groupDelayTesting.isEmpty() &&
        (delayTestingTarget == null || delayTestingAutomatic)

internal val MihomoRuntimeState.canStartSharedProxyDelayTest: Boolean
    get() = (delayTestingTarget == null || delayTestingAutomatic && !delayTestingExclusive) &&
        groupDelayTesting.values.none(MihomoDelayProgress::exclusive)

internal val MihomoRuntimeState.delayTestingNodeIds: Set<MihomoProxyNodeId>
    get() = nodeDelayTesting + delayTestingPending +
        groupDelayTesting.values.flatMapTo(linkedSetOf(), MihomoDelayProgress::pending)

internal fun MihomoRuntimeState.canStartProxyDelayTest(id: MihomoProxyNodeId): Boolean {
    return id !in nodeDelayTesting && if (proxies.canShareProxyDelay(id)) canStartSharedProxyDelayTest else canStartUserDelayTest
}

internal data class MihomoDelayResult(
    val measurements: Map<MihomoProxyNodeId, MihomoDelayMeasurement> = emptyMap(),
) {
    fun measurement(id: MihomoProxyNodeId): MihomoDelayMeasurement? = measurements[id]
}

internal fun MihomoDelayResult.withResultId(id: MihomoProxyNodeId): MihomoDelayResult {
    val measurement = measurements.values.singleOrNull() ?: return this
    return MihomoDelayResult(measurements + (id to measurement.copy(id = id)))
}

internal sealed interface MihomoDelayTarget {
    data class Node(val id: MihomoProxyNodeId) : MihomoDelayTarget

    data class Provider(val name: String) : MihomoDelayTarget
}

internal fun MihomoProxiesState.withPreservedSelections(
    previous: MihomoProxiesState,
    preserve: Boolean,
): MihomoProxiesState {
    if (!preserve) return this
    val previousGroups = previous.allGroups.associateBy(MihomoProxyGroup::name)
    fun selected(group: MihomoProxyGroup): MihomoProxyGroup {
        val selectedName = previousGroups[group.name]?.now ?: return group
        return if (group.all.any { it.name == selectedName }) group.copy(now = selectedName) else group
    }
    return copy(groups = groups.map(::selected), allGroups = allGroups.map(::selected))
}
