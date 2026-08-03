// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal fun parseMihomoOfficialProxySnapshot(json: String): MihomoProxiesState {
    val root = OfficialApiJson.parseToJsonElement(json) as? JsonObject
        ?: error("Invalid Mihomo API proxy response")
    val values = root["proxies"] as? JsonObject
        ?: error("Invalid Mihomo API proxy response: proxies is missing")
    val nodes = linkedMapOf<MihomoProxyNodeId, MihomoProxyNode>()
    val groups = mutableListOf<MihomoProxyGroup>()
    values.forEach { (fallbackName, element) ->
        val item = element as? JsonObject ?: return@forEach
        val name = item.string("name").ifBlank { fallbackName }
        val id = MihomoProxyNodeId(name)
        val type = item.string("type").ifBlank { "Proxy" }
        val members = item.stringList("all").map(::MihomoProxyNodeId)
        if (members.isNotEmpty()) {
            groups += MihomoProxyGroup(
                name = name,
                type = type,
                now = item.string("now"),
                all = members,
                hidden = item.boolean("hidden") ?: false,
                icon = item.string("icon"),
                testUrl = item.string("testUrl"),
            )
        }
        nodes[id] = MihomoProxyNode(
            id = id,
            type = type,
            title = item.string("title").ifBlank { name },
            subtitle = item.string("subtitle").ifBlank { type },
            udp = item.boolean("udp") ?: false,
            delay = item.latestPositiveDelay(),
        )
    }
    return MihomoProxiesState(
        groups = groups,
        nodes = nodes.values.toList(),
        nodeById = nodes,
        updatedAtMillis = System.currentTimeMillis(),
    )
}

internal fun parseMihomoOfficialNodeDelay(
    json: String,
    id: MihomoProxyNodeId,
): MihomoDelayResult {
    val root = OfficialApiJson.parseToJsonElement(json) as? JsonObject
        ?: error("Invalid Mihomo API delay response")
    val delay = (root["delay"] as? JsonPrimitive)?.intValue()
        ?: error("Invalid Mihomo API delay response: delay is missing")
    val measurement = if (delay > 0) {
        MihomoDelayMeasurement(
            id = id,
            status = MihomoDelayStatus.Success,
            delay = delay,
        )
    } else {
        MihomoDelayMeasurement(
            id = id,
            status = MihomoDelayStatus.Failed,
            error = "delay test returned no positive delay",
        )
    }
    return MihomoDelayResult(mapOf(id to measurement))
}

internal fun mihomoBridgeNodeDelayResult(
    id: MihomoProxyNodeId,
    delay: Int,
    timeoutMillis: Int,
): MihomoDelayResult {
    val measurement = if (
        delay > 0 &&
        delay != MihomoOfficialUntestedDelay &&
        delay < timeoutMillis
    ) {
        MihomoDelayMeasurement(
            id = id,
            status = MihomoDelayStatus.Success,
            delay = delay,
        )
    } else {
        MihomoDelayMeasurement(
            id = id,
            status = MihomoDelayStatus.Timeout,
        )
    }
    return MihomoDelayResult(mapOf(id to measurement))
}

internal fun mihomoBridgeGroupDelayResult(
    delays: Map<String, Int>,
    expectedIds: List<MihomoProxyNodeId>,
    timeoutMillis: Int,
): MihomoDelayResult {
    val expectedIdsByName = expectedIds.groupBy(MihomoProxyNodeId::name)
    val measurements = linkedMapOf<MihomoProxyNodeId, MihomoDelayMeasurement>()
    delays.forEach { (name, delay) ->
        val ids = if (expectedIds.isEmpty()) {
            listOf(MihomoProxyNodeId(name))
        } else {
            expectedIdsByName[name].orEmpty()
        }
        ids.forEach { id ->
            measurements.putAll(
                mihomoBridgeNodeDelayResult(id, delay, timeoutMillis).measurements,
            )
        }
    }
    expectedIds.forEach { id ->
        measurements.putIfAbsent(
            id,
            MihomoDelayMeasurement(
                id = id,
                status = MihomoDelayStatus.Timeout,
            ),
        )
    }
    return MihomoDelayResult(measurements)
}

internal fun parseMihomoOfficialGroupDelay(
    json: String,
    expectedIds: List<MihomoProxyNodeId>,
): MihomoDelayResult {
    val root = OfficialApiJson.parseToJsonElement(json) as? JsonObject
        ?: error("Invalid Mihomo API group delay response")
    val expectedIdsByName = expectedIds.groupBy(MihomoProxyNodeId::name)
    val measurements = linkedMapOf<MihomoProxyNodeId, MihomoDelayMeasurement>()
    root.forEach { (name, element) ->
        val delay = (element as? JsonPrimitive)?.intValue()
        val ids = if (expectedIds.isEmpty()) {
            listOf(MihomoProxyNodeId(name))
        } else {
            expectedIdsByName[name].orEmpty()
        }
        ids.forEach { id ->
            measurements[id] = if (delay != null && delay > 0) {
                MihomoDelayMeasurement(
                    id = id,
                    status = MihomoDelayStatus.Success,
                    delay = delay,
                )
            } else {
                MihomoDelayMeasurement(
                    id = id,
                    status = MihomoDelayStatus.Timeout,
                )
            }
        }
    }
    expectedIds.forEach { id ->
        measurements.putIfAbsent(
            id,
            MihomoDelayMeasurement(
                id = id,
                status = MihomoDelayStatus.Timeout,
            ),
        )
    }
    return MihomoDelayResult(measurements)
}

internal fun MihomoDelayResult.withMissingMeasurements(
    expectedIds: Iterable<MihomoProxyNodeId>,
    status: MihomoDelayStatus = MihomoDelayStatus.Timeout,
    error: String = "",
): MihomoDelayResult {
    val completed = measurements.toMutableMap()
    expectedIds.forEach { id ->
        completed.putIfAbsent(
            id,
            MihomoDelayMeasurement(
                id = id,
                status = status,
                error = error,
            ),
        )
    }
    return if (completed.size == measurements.size) this else MihomoDelayResult(completed)
}

internal fun mihomoDelayFailureResult(
    ids: Iterable<MihomoProxyNodeId>,
    status: MihomoDelayStatus,
    error: String = "",
): MihomoDelayResult {
    return MihomoDelayResult(
        ids.associateWith { id ->
            MihomoDelayMeasurement(
                id = id,
                status = status,
                error = error,
            )
        },
    )
}

private fun JsonObject.string(name: String): String {
    return (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
}

private fun JsonObject.boolean(name: String): Boolean? {
    val value = this[name] as? JsonPrimitive ?: return null
    return value.booleanOrNull ?: value.contentOrNull?.toBooleanStrictOrNull()
}

private fun JsonObject.stringList(name: String): List<String> {
    return (this[name] as? JsonArray)
        .orEmpty()
        .mapNotNull { element ->
            (element as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        }
}

private fun JsonObject.latestPositiveDelay(): Int? {
    return (this["history"] as? JsonArray)
        .orEmpty()
        .asReversed()
        .firstNotNullOfOrNull { element ->
            ((element as? JsonObject)?.get("delay") as? JsonPrimitive)
                ?.intValue()
                ?.takeIf { delay -> delay in 1 until MihomoOfficialUntestedDelay }
        }
}

private fun JsonPrimitive.intValue(): Int? {
    return intOrNull ?: contentOrNull?.toIntOrNull()
}

private val OfficialApiJson = Json { ignoreUnknownKeys = true }
private const val MihomoOfficialUntestedDelay = 65_535
