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
    val history: List<MihomoTrafficSample> = emptyList(),
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

internal data class MihomoProxyNode(
    val name: String,
    val type: String,
    val udp: Boolean = false,
    val delay: Int? = null,
)

internal data class MihomoProxyGroup(
    val name: String,
    val type: String,
    val now: String = "",
    val all: List<String> = emptyList(),
    val hidden: Boolean = false,
    val icon: String = "",
    val testUrl: String = "",
)

internal data class MihomoProxiesState(
    val groups: List<MihomoProxyGroup> = emptyList(),
    val nodes: List<MihomoProxyNode> = emptyList(),
    val nodeByName: Map<String, MihomoProxyNode> = emptyMap(),
    val updatedAtMillis: Long = 0L,
) {
    fun node(name: String): MihomoProxyNode {
        return nodeByName[name] ?: MihomoProxyNode(name = name, type = "Proxy")
    }
}

internal data class MihomoProviderNode(
    val name: String,
    val title: String = "",
    val subtitle: String = "",
    val type: String = "",
    val delay: Int? = null,
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
    val nodes: List<MihomoProviderNode> = emptyList(),
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
    val delayTestingTarget: String? = null,
    val lastError: String = "",
)

internal data class MihomoDelayResult(
    val delays: Map<String, Int> = emptyMap(),
) {
    val firstDelay: Int?
        get() = delays.values.firstOrNull()
}
