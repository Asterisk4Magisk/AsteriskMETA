// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

/**
 * The public address of the caller, as seen by the endpoint it answered.
 */
internal data class PublicAddressProbeResult(
    val address: String = "",
    val country: String = "",
    val countryCode: String = "",
    val region: String = "",
    val city: String = "",
    val organization: String = "",
    val asn: String = "",
    val error: String = "",
) {
    val isSuccessful: Boolean
        get() = address.isNotBlank()
}

/**
 * Asks an endpoint which public address the request came from.
 *
 * The request can go through a local proxy, which is what makes the answer the address of the
 * tunnel rather than the address of the device.
 */
internal class PublicAddressProbe(
    private val endpoints: List<String> = DefaultPublicAddressProbeEndpoints,
) {
    /**
     * Probes the public address, optionally through [proxy]. The first endpoint that answers
     * wins, so a single unreachable endpoint does not fail the whole probe.
     */
    suspend fun probe(proxy: Proxy? = null): PublicAddressProbeResult = withContext(Dispatchers.IO) {
        var lastError = ""
        for (endpoint in endpoints) {
            val result = probeOne(endpoint, proxy)
            if (result.isSuccessful) return@withContext result
            lastError = result.error
        }
        PublicAddressProbeResult(error = lastError)
    }

    private fun probeOne(endpoint: String, proxy: Proxy?): PublicAddressProbeResult {
        val connection = try {
            val url = URI(endpoint).toURL()
            val raw = if (proxy != null) url.openConnection(proxy) else url.openConnection()
            (raw as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = ProbeTimeoutMillis
                readTimeout = ProbeTimeoutMillis
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", ProbeUserAgent)
            }
        } catch (error: Exception) {
            return PublicAddressProbeResult(error = error.message.orEmpty().ifBlank { "Request failed" })
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                return PublicAddressProbeResult(error = "HTTP $status")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
            parsePublicAddressProbe(body)
        } catch (error: Exception) {
            PublicAddressProbeResult(error = error.message.orEmpty().ifBlank { "Request failed" })
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Reads the answer of the endpoint. The GeoJS answer carries the address together with the
 * network it belongs to, so one request answers the whole question.
 */
internal fun parsePublicAddressProbe(body: String): PublicAddressProbeResult {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return PublicAddressProbeResult(error = "Empty response")
    if (!trimmed.startsWith("{")) {
        val address = trimmed.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty).orEmpty()
        return if (address.isNotBlank()) {
            PublicAddressProbeResult(address = address)
        } else {
            PublicAddressProbeResult(error = "Invalid response")
        }
    }
    return runCatching {
        val json = ProbeJson.parseToJsonElement(trimmed).jsonObject
        val address = json.stringOrEmpty("ip")
        PublicAddressProbeResult(
            address = address,
            country = json.stringOrEmpty("country"),
            countryCode = json.stringOrEmpty("country_code"),
            region = json.stringOrEmpty("region"),
            city = json.stringOrEmpty("city"),
            organization = json.stringOrEmpty("organization_name").ifBlank { json.stringOrEmpty("organization") },
            asn = json.asnLabel(),
        ).let { result ->
            if (result.address.isBlank()) result.copy(error = "Invalid response") else result
        }
    }.getOrElse {
        PublicAddressProbeResult(error = "Invalid response")
    }
}

/**
 * The autonomous system number of an answer, in the `AS15169` form. The endpoint reports the
 * number on its own, and the organization already carries the same prefix.
 */
private fun Map<String, kotlinx.serialization.json.JsonElement>.asnLabel(): String {
    val number = stringOrEmpty("asn")
    if (number.isBlank()) return ""
    val digits = number.filter { it.isDigit() }
    if (digits.isEmpty()) return number
    return "AS$digits"
}

private fun Map<String, kotlinx.serialization.json.JsonElement>.stringOrEmpty(key: String): String {
    val element = this[key] ?: return ""
    return runCatching { element.jsonPrimitive.content }.getOrNull().orEmpty().trim()
}

/**
 * A local proxy the probe goes through. The address is the tunnel the app publishes.
 */
internal data class LocalProxyTarget(
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val useSocks: Boolean = false,
) {
    val isUsable: Boolean
        get() = host.isNotBlank() && port > 0

    fun toProxy(): Proxy {
        val type = if (useSocks) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return Proxy(type, InetSocketAddress(host, port))
    }
}

private val ProbeJson = Json { ignoreUnknownKeys = true }

private const val ProbeTimeoutMillis = 15_000
private const val ProbeUserAgent = "AsteriskMETA"

/**
 * The endpoints the probe asks. The IPv4 and IPv6 forms are separate, so a host without IPv6
 * still gets an answer.
 */
internal val DefaultPublicAddressProbeEndpoints = listOf(
    "https://ipv4.geojs.io/v1/ip/geo.json",
    Ipv6PublicAddressProbeEndpoint,
)

/**
 * The endpoint that answers over IPv6 only. Asking it on its own is how the check finds out
 * whether the traffic has an IPv6 address at all.
 */
internal const val Ipv6PublicAddressProbeEndpoint = "https://ipv6.geojs.io/v1/ip/geo.json"
