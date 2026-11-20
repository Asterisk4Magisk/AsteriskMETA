// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import io.ktor.http.decodeURLPart
import io.ktor.http.parseQueryString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.io.encoding.Base64

internal fun String.mihomoProviderShareLinkServer(nodeName: String): String? {
    val links = if ("://" in this) this else trim().decodeProviderBase64() ?: return null
    val occurrences = mutableMapOf<String, Int>()
    for (line in links.lineSequence()) {
        for ((name, server) in line.trimEnd(' ', '\r').providerShareLinkAddresses()) {
            // Match Mihomo's name allocation before provider filters and fixed prefixes or suffixes.
            val index = occurrences[name] ?: 0
            occurrences[name] = index + 1
            val uniqueName = if (index == 0) name else "$name-${index.toString().padStart(2, '0')}"
            if (uniqueName == nodeName) return server.takeIf(String::isNotBlank)
        }
    }
    return null
}

private data class ProviderShareLinkAddress(val name: String, val server: String)

private fun String.providerShareLinkAddresses(): List<ProviderShareLinkAddress> {
    val scheme = substringBefore("://").lowercase()
    if (scheme !in ProviderShareLinkSchemes) return emptyList()
    val body = substringAfter("://", "")
    val name = substringAfter('#', "").decodeProviderUrlPart() ?: return emptyList()
    if (scheme == "vmess") {
        body.decodeProviderBase64()?.let { decoded ->
            val json = runCatching { Json.parseToJsonElement(decoded) as? JsonObject }.getOrNull()
            val label = (json?.get("ps") as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                ?: return emptyList()
            val server = (json["add"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            return listOf(ProviderShareLinkAddress(label, server))
        }
    }
    if (scheme == "ssr") {
        val decoded = body.decodeProviderBase64() ?: return emptyList()
        val query = parseQueryString(decoded.substringAfter("/?", ""))
        val fields = decoded.substringBefore("/?").split(':')
        if ("/?" !in decoded || fields.size != 6) return emptyList()
        val label = query["remarks"]?.decodeProviderBase64().orEmpty()
        return listOf(ProviderShareLinkAddress(label, fields.first()))
    }

    val address = body.substringBefore('#').substringBefore('?').substringBefore('/').substringAfterLast('@')
    val addressPort = address.substringAfterLast(':', "").takeIf { !address.endsWith(']') }.orEmpty()
    val portHopping = scheme in PortHoppingShareLinks && !address.startsWith('[')
    val portCharacters = if (portHopping) "0123456789,-" else "0123456789"
    if (addressPort.isNotEmpty() && (addressPort.first() !in '0'..'9' || addressPort.any { it !in portCharacters })) {
        return emptyList()
    }
    val endpoint = when (scheme) {
        "ss" if ':' !in address -> {
            val decoded = address.decodeProviderBase64() ?: return listOf(ProviderShareLinkAddress(name, ""))
            decoded.substringAfterLast('@')
        }
        "vless" if ':' !in address -> address.decodeProviderBase64() ?: address
        else -> address
    }
    val server = if (endpoint.startsWith('[')) {
        endpoint.substringAfter('[').substringBefore(']', "")
    } else {
        endpoint.substringBefore(':')
    }
    val port = endpoint.substringAfterLast(':', "").takeIf { !endpoint.endsWith(']') }.orEmpty()
    if (scheme == "mierus") {
        if (server.isEmpty()) return emptyList()
        val parameters = parseQueryString(body.substringBefore('#').substringAfter('?', ""))
        val ports = parameters.getAll("port").orEmpty()
        val protocols = parameters.getAll("protocol").orEmpty()
        if (ports.size != protocols.size) return emptyList()
        val label = name.ifEmpty { parameters["profile"].orEmpty() }.ifEmpty { server }
        return ports.zip(protocols) { value, protocol -> ProviderShareLinkAddress("$label:$value/$protocol", server) }
    }
    val label = if (scheme in ProviderShareLinksNamedByAddress) {
        if (server.isEmpty() || port.isEmpty()) return emptyList()
        name.ifEmpty { "$server:$port" }
    } else {
        name
    }
    return listOf(ProviderShareLinkAddress(label, server))
}

private fun String.decodeProviderUrlPart(): String? = runCatching { decodeURLPart() }.getOrNull()

private fun String.decodeProviderBase64(): String? {
    val value = replace("\n", "").replace("\r", "")
    return runCatching { ProviderBase64.decode(value).toString(Charsets.UTF_8) }.getOrNull()
        ?: runCatching { ProviderBase64Url.decode(value).toString(Charsets.UTF_8) }.getOrNull()
}

private val ProviderBase64 = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
private val ProviderBase64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

private val PortHoppingShareLinks = setOf("hysteria2", "hy2", "hysteria2+realm", "hy2+realm")
private val ProviderShareLinksNamedByAddress = setOf("socks", "socks5", "socks5h", "http", "https", "anytls")
private val ProviderShareLinkSchemes = ProviderShareLinksNamedByAddress + PortHoppingShareLinks + setOf(
    "ss", "ssr", "vmess", "vless", "trojan", "tuic", "hysteria", "mierus",
)
