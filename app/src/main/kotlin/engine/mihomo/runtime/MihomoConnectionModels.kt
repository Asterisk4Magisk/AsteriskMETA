// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import utils.intValue
import utils.longValue
import utils.arrayValue
import utils.objectValue
import utils.stringValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Locale

internal data class MihomoConnection(
    val id: String,
    val network: String = "",
    val inboundType: String = "",
    val sourceAddress: String = "",
    val destinationAddress: String = "",
    val process: String = "",
    val processPath: String = "",
    val uid: Long? = null,
    val outboundType: String = "",
    val chains: List<String> = emptyList(),
    val providerChains: List<String> = emptyList(),
    val rule: String = "",
    val rulePayload: String = "",
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val uploadBytesPerSecond: Long? = null,
    val downloadBytesPerSecond: Long? = null,
    val startedAtMillis: Long? = null,
)

internal data class MihomoConnectionsState(
    val uploadTotalBytes: Long = 0L,
    val downloadTotalBytes: Long = 0L,
    val connections: List<MihomoConnection> = emptyList(),
    val updatedAtMillis: Long = 0L,
)

internal data class MihomoRuleRuntimeItem(
    val type: String,
    val payload: String,
    val proxy: String = "",
    val ruleCount: Int = 0,
    val hits: Long = 0L,
    val lastHitAtMillis: Long? = null,
    val misses: Long = 0L,
    val lastMissAtMillis: Long? = null,
)

internal data class MihomoRulesState(
    val rules: List<MihomoRuleRuntimeItem> = emptyList(),
    val outbounds: Map<String, List<MihomoRuleOutbound>> = emptyMap(),
)

internal fun parseMihomoRulesJson(content: String): List<MihomoRuleRuntimeItem> {
    val root = ConnectionJson.parseToJsonElement(content) as? JsonObject
        ?: error("Invalid Mihomo rules response")
    return root.arrayValue("rules").orEmpty().mapNotNull { element ->
        val rule = element as? JsonObject ?: return@mapNotNull null
        // Match statistics live in the nested "extra" object; the top-level "hits" field
        // only exists on older cores and stays at zero there, so prefer "extra".
        val extra = rule.objectValue("extra")
        fun JsonObject.zeroTimeToNull(name: String): Long? =
            stringValue(name)?.toRfc3339MillisOrNull()?.takeIf { it > 0L }
        MihomoRuleRuntimeItem(
            type = rule.stringValue("type").orEmpty(),
            payload = rule.stringValue("payload").orEmpty(),
            proxy = rule.stringValue("proxy").orEmpty(),
            ruleCount = (rule.intValue("size") ?: 0).takeIf { it > 0 } ?: 0,
            hits = extra?.longValue("hitCount") ?: rule.longValue("hits") ?: 0L,
            lastHitAtMillis = extra?.zeroTimeToNull("hitAt"),
            misses = extra?.longValue("missCount") ?: 0L,
            lastMissAtMillis = extra?.zeroTimeToNull("missAt"),
        )
    }
}

internal fun parseMihomoConnectionsJson(
    content: String,
    updatedAtMillis: Long = System.currentTimeMillis(),
    proxies: MihomoProxiesState = MihomoProxiesState(),
): MihomoConnectionsState {
    val root = ConnectionJson.parseToJsonElement(content) as? JsonObject
        ?: error("Invalid Mihomo connections response")
    return MihomoConnectionsState(
        uploadTotalBytes = root.longValue("uploadTotal") ?: 0L,
        downloadTotalBytes = root.longValue("downloadTotal") ?: 0L,
        connections = root.arrayValue("connections")
            .orEmpty()
            .mapNotNull { element -> (element as? JsonObject)?.toMihomoConnectionOrNull(proxies) },
        updatedAtMillis = updatedAtMillis,
    )
}

internal fun parseMihomoConnectionCountsJson(
    content: String,
    proxies: MihomoProxiesState,
): ConnectionCounts {
    val root = ConnectionJson.parseToJsonElement(content) as? JsonObject
        ?: error("Invalid Mihomo connections response")
    var activeCount = 0
    var directCount = 0
    var proxyCount = 0
    root.arrayValue("connections").orEmpty().forEach { element ->
        val connection = element as? JsonObject ?: return@forEach
        if (connection.stringValue("id") == null) return@forEach
        activeCount += 1
        val outbound = connection.outboundId()
        val type = outbound?.let { proxies.nodeById[it]?.type }.orEmpty()
        when (resolveConnectionRoute(outbound?.name, type)) {
            ConnectionRouteKind.Direct -> directCount += 1
            ConnectionRouteKind.Proxy -> proxyCount += 1
            null -> Unit
        }
    }
    return ConnectionCounts(activeCount, directCount, proxyCount)
}

private fun JsonObject.outboundId(): MihomoProxyNodeId? {
    // Mihomo records the leaf first; provider names are positional and may be empty.
    val name = (arrayValue("chains")?.firstOrNull() as? JsonPrimitive)?.contentOrNull
        ?.takeIf(String::isNotBlank) ?: return null
    val provider = (arrayValue("providerChains")?.firstOrNull() as? JsonPrimitive)?.contentOrNull
        ?.takeIf(String::isNotBlank)
    return MihomoProxyNodeId(name, provider)
}

private fun JsonObject.toMihomoConnectionOrNull(proxies: MihomoProxiesState): MihomoConnection? {
    val id = stringValue("id") ?: return null
    val metadata = objectValue("metadata") ?: JsonObject(emptyMap())
    val sourceHost = metadata.stringValue("sourceIP").orEmpty()
    val sourcePort = metadata.longValue("sourcePort")?.toInt()
    val destinationHost = metadata.stringValue("sniffHost")
        ?: metadata.stringValue("host")
        ?: metadata.stringValue("destinationIP")
        .orEmpty()
    val destinationPort = metadata.longValue("destinationPort")?.toInt()
    return MihomoConnection(
        id = id,
        network = metadata.stringValue("network").orEmpty().lowercase(Locale.ROOT),
        inboundType = metadata.stringValue("type").orEmpty(),
        sourceAddress = formatConnectionAddress(sourceHost, sourcePort),
        destinationAddress = formatConnectionAddress(destinationHost, destinationPort),
        process = metadata.stringValue("process").orEmpty(),
        processPath = metadata.stringValue("processPath").orEmpty(),
        uid = metadata.longValue("uid"),
        outboundType = outboundId()?.let { proxies.nodeById[it]?.type }.orEmpty(),
        chains = stringListValue("chains"),
        providerChains = stringListValue("providerChains"),
        rule = stringValue("rule").orEmpty(),
        rulePayload = stringValue("rulePayload").orEmpty(),
        uploadBytes = longValue("upload") ?: 0L,
        downloadBytes = longValue("download") ?: 0L,
        startedAtMillis = stringValue("start")?.toRfc3339MillisOrNull(),
    )
}

private fun formatConnectionAddress(host: String, port: Int?): String {
    val normalizedHost = host.trim()
    if (normalizedHost.isEmpty()) return ""
    if (port == null || port <= 0) return normalizedHost
    val printableHost = if (':' in normalizedHost && !normalizedHost.startsWith("[")) {
        "[$normalizedHost]"
    } else {
        normalizedHost
    }
    return "$printableHost:$port"
}

private fun String.toRfc3339MillisOrNull(): Long? {
    val match = Rfc3339Regex.matchEntire(trim()) ?: return null
    val dateAndTime = match.groupValues[1]
    val fraction = match.groupValues[2]
    val zone = match.groupValues[3]
    val secondsMillis = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            isLenient = false
        }.parse(dateAndTime + zone)?.time
    }.getOrNull() ?: return null
    val fractionMillis = fraction.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    return secondsMillis + fractionMillis
}

private fun JsonObject.stringListValue(name: String): List<String> {
    return arrayValue(name)
        ?.mapNotNull { value -> (value as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        .orEmpty()
}

private val ConnectionJson = Json { ignoreUnknownKeys = true }
private val Rfc3339Regex = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$""")
