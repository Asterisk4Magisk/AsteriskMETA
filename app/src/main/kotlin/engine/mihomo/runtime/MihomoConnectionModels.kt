// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
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

internal fun parseMihomoConnectionsJson(
    content: String,
    updatedAtMillis: Long = System.currentTimeMillis(),
): MihomoConnectionsState {
    val root = ConnectionJson.parseToJsonElement(content) as? JsonObject
        ?: error("Invalid Mihomo connections response")
    return MihomoConnectionsState(
        uploadTotalBytes = root.longValue("uploadTotal") ?: 0L,
        downloadTotalBytes = root.longValue("downloadTotal") ?: 0L,
        connections = root.arrayValue("connections")
            .orEmpty()
            .mapNotNull { element -> (element as? JsonObject)?.toMihomoConnectionOrNull() },
        updatedAtMillis = updatedAtMillis,
    )
}

private fun JsonObject.toMihomoConnectionOrNull(): MihomoConnection? {
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

private fun JsonObject.stringValue(name: String): String? {
    return (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
}

private fun JsonObject.longValue(name: String): Long? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull()
}

private fun JsonObject.objectValue(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.arrayValue(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.stringListValue(name: String): List<String> {
    return arrayValue(name)
        ?.mapNotNull { value -> (value as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        .orEmpty()
}

private val ConnectionJson = Json { ignoreUnknownKeys = true }
private val Rfc3339Regex = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$""")
