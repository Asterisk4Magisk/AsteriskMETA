// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal fun parseMihomoRuntimeProxySnapshot(json: String): MihomoProxiesState {
    val root = RuntimeDelayJson.parseToJsonElement(json).asObject("runtime proxy snapshot")
    root.operationErrorOrNull()?.let(::error)
    val groupValues = root["groups"] as? JsonArray
        ?: error("Invalid Mihomo runtime proxy snapshot: groups is missing")
    val nodes = linkedMapOf<MihomoProxyNodeId, MihomoProxyNode>()
    val groups = groupValues.map { element ->
        val group = element.asObject("runtime proxy group")
        val name = group.requiredString("name")
        val members = (group["proxies"] as? JsonArray).orEmpty().map { memberElement ->
            val member = memberElement.asObject("runtime proxy")
            val id = member["id"].asObject("runtime proxy identity").toProxyNodeId()
            val type = member.string("type").ifBlank { "Proxy" }
            val rawDelay = member.int("delay")
            val delay = rawDelay?.takeIf { it > 0 && it != MihomoUntestedDelay }
            nodes[id] = MihomoProxyNode(
                id = id,
                title = member.string("title").ifBlank { id.name },
                subtitle = member.string("subtitle").ifBlank { type },
                type = type,
                delay = delay,
                delayStatus = delay?.let { MihomoDelayStatus.Success },
            )
            id
        }
        MihomoProxyGroup(
            name = name,
            type = group.string("type"),
            now = group.string("now"),
            all = members,
            hidden = group.boolean("hidden") ?: false,
            icon = group.string("icon"),
            testUrl = group.string("testUrl"),
        )
    }
    return MihomoProxiesState(
        groups = groups,
        nodes = nodes.values.toList(),
        nodeById = nodes,
        updatedAtMillis = System.currentTimeMillis(),
    )
}

internal fun parseMihomoDelayResult(json: String): MihomoDelayResult {
    val root = RuntimeDelayJson.parseToJsonElement(json).asObject("delay response")
    root.operationErrorOrNull()?.let(::error)
    val values = root["measurements"] as? JsonArray
        ?: error("Invalid Mihomo delay response: measurements is missing")
    val measurements = linkedMapOf<MihomoProxyNodeId, MihomoDelayMeasurement>()
    values.forEach { element ->
        val item = element.asObject("delay measurement")
        val id = item["id"].asObject("delay measurement identity").toProxyNodeId()
        val status = when (item.requiredString("status")) {
            "success" -> MihomoDelayStatus.Success
            "timeout" -> MihomoDelayStatus.Timeout
            "failed" -> MihomoDelayStatus.Failed
            else -> error("Invalid Mihomo delay response: unknown status")
        }
        val delay = item.int("delay")
            ?.takeIf { status == MihomoDelayStatus.Success && it > 0 }
        if (status == MihomoDelayStatus.Success && delay == null) {
            error("Invalid Mihomo delay response: successful measurement has no positive delay")
        }
        check(measurements[id] == null) {
            "Invalid Mihomo delay response: duplicate identity ${id.providerName.orEmpty()}/${id.name}"
        }
        measurements[id] = MihomoDelayMeasurement(
            id = id,
            status = status,
            delay = delay,
            error = item.string("error"),
        )
    }
    return MihomoDelayResult(measurements)
}

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

private fun JsonObject.toProxyNodeId(): MihomoProxyNodeId {
    return MihomoProxyNodeId(
        name = requiredString("name"),
        providerName = string("providerName").takeIf(String::isNotBlank),
    )
}

private fun JsonElement?.asObject(description: String): JsonObject {
    return this as? JsonObject ?: error("Invalid Mihomo $description")
}

private fun JsonObject.requiredString(name: String): String {
    return string(name).takeIf(String::isNotBlank)
        ?: error("Invalid Mihomo response: $name is missing")
}

private fun JsonObject.string(name: String): String {
    return (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
}

private fun JsonObject.int(name: String): Int? {
    val value = this[name] as? JsonPrimitive ?: return null
    return value.intOrNull ?: value.contentOrNull?.toIntOrNull()
}

private fun JsonObject.boolean(name: String): Boolean? {
    val value = this[name] as? JsonPrimitive ?: return null
    return value.booleanOrNull ?: value.contentOrNull?.toBooleanStrictOrNull()
}

private fun JsonObject.operationErrorOrNull(): String? {
    return string("error").takeIf(String::isNotBlank)
        ?: string("Error").takeIf(String::isNotBlank)
}

private val RuntimeDelayJson = Json { ignoreUnknownKeys = true }
private const val MihomoUntestedDelay = 65_535
