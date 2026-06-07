// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import engine.network.isIpAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

internal suspend fun detectMihomoNetworkAddress(): String = withContext(Dispatchers.IO) {
    val errors = mutableListOf<String>()
    for (endpoint in NetworkAddressDetectionEndpoints) {
        val address = runCatching { endpoint.readNetworkAddress() }
            .onFailure { error ->
                errors += "${endpoint.host}: ${error.message ?: error::class.java.simpleName}"
            }
            .getOrNull()
        if (address != null) {
            return@withContext address
        }
    }
    error(errors.joinToString("; ").ifBlank { "Unable to detect network address" })
}

private fun URI.readNetworkAddress(): String {
    val connection = (toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = NetworkAddressDetectionTimeoutMillis
        readTimeout = NetworkAddressDetectionTimeoutMillis
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "AsteriskMETA")
    }
    return try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            error("HTTP $responseCode")
        }
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
        parseNetworkAddressResponse(this, body).displayText()
    } finally {
        connection.disconnect()
    }
}

private fun parseNetworkAddressResponse(
    endpoint: URI,
    body: String,
): NetworkAddressDetectionResult {
    val trimmed = body.trim()
    val result = if (trimmed.startsWith("{")) {
        parseJsonNetworkAddress(trimmed)
    } else {
        NetworkAddressDetectionResult(
            address = trimmed.lineSequence()
                .map(String::trim)
                .firstOrNull(String::isNotEmpty)
                .orEmpty(),
        )
    }
    return result.validate(endpoint)
}

private fun parseJsonNetworkAddress(body: String): NetworkAddressDetectionResult {
    val json = JSONObject(body)
    return NetworkAddressDetectionResult(
        address = json.firstString("ip", "query", "address"),
        countryCode = json.firstString("country", "country_code", "countryCode", "cc", "country_iso"),
    )
}

private fun JSONObject.firstString(vararg keys: String): String {
    keys.forEach { key ->
        optString(key).trim().takeIf(String::isNotEmpty)?.let { value -> return value }
    }
    return ""
}

private fun NetworkAddressDetectionResult.validate(endpoint: URI): NetworkAddressDetectionResult {
    val address = address.trim()
    val countryCode = countryCode?.trim()?.uppercase()
    if (!isIpAddress(address)) {
        error("${endpoint.host}: invalid IP address response")
    }
    if (countryCode == null || !isCountryCode(countryCode)) {
        error("${endpoint.host}: invalid country code response")
    }
    return copy(
        address = address,
        countryCode = countryCode,
    )
}

private fun NetworkAddressDetectionResult.displayText(): String {
    return countryCode?.let { code -> "($code)$address" } ?: address
}

private fun isCountryCode(value: String): Boolean {
    return value.length in 2..3 && value.all { char -> char in 'A'..'Z' }
}

private data class NetworkAddressDetectionResult(
    val address: String,
    val countryCode: String? = null,
)

private val NetworkAddressDetectionEndpoints = listOf(
    URI("https://ifconfig.co/json"),
    URI("https://ipinfo.io/json"),
)

private const val NetworkAddressDetectionTimeoutMillis = 5_000
