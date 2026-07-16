// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.network

import engine.network.isIpv4Address
import engine.network.isIpv6Address
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal enum class AddressFamily {
    Ipv4,
    Ipv6,
}

internal enum class NetworkTransport {
    None,
    Wifi,
    Cellular,
    Ethernet,
    Bluetooth,
    Vpn,
    Other,
}

internal data class LocalNetworkSnapshot(
    val transport: NetworkTransport = NetworkTransport.None,
    val networkAvailable: Boolean = false,
    val internetValidated: Boolean = false,
    val interfaceName: String = "",
    val ipv4Addresses: List<String> = emptyList(),
    val ipv6Addresses: List<String> = emptyList(),
    val gateways: List<String> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val updatedAtMillis: Long = 0L,
)

internal data class PublicProbeEndpoint(
    val family: AddressFamily,
    val url: String,
    val host: String,
)

internal enum class PublicProbeError {
    Timeout,
    Network,
    InvalidResponse,
}

internal sealed interface PublicProbeAttempt {
    val endpointHost: String

    data class Success(
        val address: String,
        val durationMillis: Long,
        override val endpointHost: String,
    ) : PublicProbeAttempt

    data class Failure(
        val error: PublicProbeError,
        val message: String,
        override val endpointHost: String,
    ) : PublicProbeAttempt
}

internal data class PublicAddressProbeResult(
    val address: String = "",
    val durationMillis: Long? = null,
    val endpointHost: String = "",
    val updatedAtMillis: Long = 0L,
    val error: PublicProbeError? = null,
    val errorMessage: String = "",
    val stale: Boolean = false,
)

internal data class PublicNetworkProbeState(
    val ipv4: PublicAddressProbeResult = PublicAddressProbeResult(),
    val ipv6: PublicAddressProbeResult = PublicAddressProbeResult(),
    val refreshing: Boolean = false,
    val lastCompletedAtMillis: Long = 0L,
)

internal object PublicNetworkProbeMemoryCache {
    private var state = PublicNetworkProbeState()
    private var lastPageSessionId: String? = null

    @Synchronized
    fun read(): PublicNetworkProbeState = state.copy(refreshing = false)

    @Synchronized
    fun write(next: PublicNetworkProbeState) {
        state = next.copy(refreshing = false)
    }

    @Synchronized
    fun shouldProbe(pageSessionId: String?): Boolean {
        if (pageSessionId == null || pageSessionId == lastPageSessionId) return false
        lastPageSessionId = pageSessionId
        return true
    }
}

internal fun parsePublicAddressResponse(body: String, family: AddressFamily): String? {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return null
    val address = if (trimmed.startsWith('{')) {
        runCatching {
            val json = NetworkProbeJson.parseToJsonElement(trimmed).jsonObject
            PublicAddressJsonKeys.firstNotNullOfOrNull { key ->
                json[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            }
        }.getOrNull()
    } else {
        trimmed.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
    } ?: return null
    return address.takeIf { value ->
        when (family) {
            AddressFamily.Ipv4 -> isIpv4Address(value)
            AddressFamily.Ipv6 -> isIpv6Address(value)
        }
    }
}

internal fun applyPublicProbeAttempts(
    previous: PublicNetworkProbeState,
    ipv4: PublicProbeAttempt,
    ipv6: PublicProbeAttempt,
    completedAtMillis: Long,
): PublicNetworkProbeState {
    return PublicNetworkProbeState(
        ipv4 = previous.ipv4.applyAttempt(ipv4, completedAtMillis),
        ipv6 = previous.ipv6.applyAttempt(ipv6, completedAtMillis),
        refreshing = false,
        lastCompletedAtMillis = completedAtMillis,
    )
}

internal fun applyPublicProbeAttempt(
    previous: PublicNetworkProbeState,
    family: AddressFamily,
    attempt: PublicProbeAttempt,
    completedAtMillis: Long,
): PublicNetworkProbeState {
    return when (family) {
        AddressFamily.Ipv4 -> previous.copy(
            ipv4 = previous.ipv4.applyAttempt(attempt, completedAtMillis),
            refreshing = false,
            lastCompletedAtMillis = completedAtMillis,
        )
        AddressFamily.Ipv6 -> previous.copy(
            ipv6 = previous.ipv6.applyAttempt(attempt, completedAtMillis),
            refreshing = false,
            lastCompletedAtMillis = completedAtMillis,
        )
    }
}

private fun PublicAddressProbeResult.applyAttempt(
    attempt: PublicProbeAttempt,
    completedAtMillis: Long,
): PublicAddressProbeResult {
    return when (attempt) {
        is PublicProbeAttempt.Success -> PublicAddressProbeResult(
            address = attempt.address,
            durationMillis = attempt.durationMillis,
            endpointHost = attempt.endpointHost,
            updatedAtMillis = completedAtMillis,
        )

        is PublicProbeAttempt.Failure -> copy(
            endpointHost = attempt.endpointHost,
            error = attempt.error,
            errorMessage = attempt.message,
            stale = address.isNotEmpty(),
        )
    }
}

internal val DefaultPublicProbeEndpoints = listOf(
    PublicProbeEndpoint(
        family = AddressFamily.Ipv4,
        url = "https://api4.ipify.org?format=json",
        host = "api4.ipify.org",
    ),
    PublicProbeEndpoint(
        family = AddressFamily.Ipv6,
        url = "https://api6.ipify.org?format=json",
        host = "api6.ipify.org",
    ),
)

private val NetworkProbeJson = Json { ignoreUnknownKeys = true }
private val PublicAddressJsonKeys = listOf("ip", "query", "address")
