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

internal enum class ProbeTarget {
    General,
    Cloudflare,
}

internal data class PublicProbeEndpoint(
    val family: AddressFamily,
    val target: ProbeTarget = ProbeTarget.General,
    val url: String,
    val host: String,
)

internal enum class PublicProbeError {
    Timeout,
    Network,
    InvalidResponse,
    Unavailable,
}

internal sealed interface PublicProbeAttempt {
    val endpointHost: String

    data class Success(
        val address: String,
        val durationMillis: Long,
        override val endpointHost: String,
        val target: ProbeTarget = ProbeTarget.General,
        val country: String = "",
        val countryCode: String = "",
        val region: String = "",
        val city: String = "",
        val isp: String = "",
        val colo: String = "",
        val warp: String = "",
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
    val target: ProbeTarget = ProbeTarget.General,
    val country: String = "",
    val countryCode: String = "",
    val region: String = "",
    val city: String = "",
    val isp: String = "",
    val colo: String = "",
    val warp: String = "",
) {
    fun locationSummary(coloLabel: String): String {
        val emoji = countryCodeToEmoji(countryCode)
        val parts = mutableListOf<String>()
        if (country.isNotBlank()) parts += country
        val regionAndCity = listOf(region, city)
            .filter { it.isNotBlank() && !it.equals(country, ignoreCase = true) }
            .distinct()
            .joinToString(" ")
        if (regionAndCity.isNotBlank()) parts += regionAndCity
        if (colo.isNotBlank()) {
            parts += coloLabel
        }
        if (isp.isNotBlank()) parts += isp
        if (warp.equals("on", ignoreCase = true)) {
            parts += "WARP"
        }
        val text = parts.joinToString(" · ")
        return if (emoji.isNotBlank()) "$emoji $text" else text
    }
}

internal data class PublicNetworkProbeState(
    val ipv4: PublicAddressProbeResult = PublicAddressProbeResult(target = ProbeTarget.General),
    val ipv6: PublicAddressProbeResult = PublicAddressProbeResult(target = ProbeTarget.General),
    val cloudflareIpv4: PublicAddressProbeResult = PublicAddressProbeResult(target = ProbeTarget.Cloudflare),
    val cloudflareIpv6: PublicAddressProbeResult = PublicAddressProbeResult(target = ProbeTarget.Cloudflare),
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

internal data class ParsedPublicProbeAddress(
    val address: String,
    val country: String = "",
    val countryCode: String = "",
    val region: String = "",
    val city: String = "",
    val isp: String = "",
    val colo: String = "",
    val warp: String = "",
)

internal fun countryCodeToEmoji(countryCode: String?): String {
    if (countryCode == null || countryCode.length != 2) return ""
    val code = countryCode.uppercase()
    if (!code.all { it in 'A'..'Z' }) return ""
    val firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
    val secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
}

internal sealed interface PublicProbeParseOutcome {
    data class Success(val parsed: ParsedPublicProbeAddress) : PublicProbeParseOutcome
    data object FamilyUnavailable : PublicProbeParseOutcome
    data object Invalid : PublicProbeParseOutcome
}

internal fun parsePublicProbeOutcome(
    body: String,
    family: AddressFamily,
    target: ProbeTarget = ProbeTarget.General,
): PublicProbeParseOutcome {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return PublicProbeParseOutcome.Invalid

    // 1. Cloudflare cdn-cgi/trace 格式解析 (key=value)
    if (trimmed.contains("fl=") || trimmed.contains("colo=") || target == ProbeTarget.Cloudflare) {
        val lines = trimmed.lineSequence().map(String::trim).filter { it.contains('=') }
        val map = lines.associate { line ->
            val key = line.substringBefore('=').trim()
            val value = line.substringAfter('=').trim()
            key to value
        }
        val rawIp = map["ip"]
        if (rawIp != null) {
            val matched = when (family) {
                AddressFamily.Ipv4 -> isIpv4Address(rawIp)
                AddressFamily.Ipv6 -> isIpv6Address(rawIp)
            }
            if (matched) {
                val loc = map["loc"].orEmpty()
                val colo = map["colo"].orEmpty()
                val warp = map["warp"].orEmpty()
                return PublicProbeParseOutcome.Success(
                    ParsedPublicProbeAddress(
                        address = rawIp,
                        countryCode = loc,
                        colo = colo,
                        warp = warp,
                    ),
                )
            }
            if ((family == AddressFamily.Ipv6 && isIpv4Address(rawIp)) ||
                (family == AddressFamily.Ipv4 && isIpv6Address(rawIp))
            ) {
                return PublicProbeParseOutcome.FamilyUnavailable
            }
        }
    }

    // 2. 标准 JSON 格式解析 (geojs.io / ipinfo / ipify 等)
    if (trimmed.startsWith('{')) {
        return runCatching {
            val json = NetworkProbeJson.parseToJsonElement(trimmed).jsonObject
            val rawAddress = PublicAddressJsonKeys.firstNotNullOfOrNull { key ->
                json[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            } ?: return@runCatching PublicProbeParseOutcome.Invalid

            val matched = when (family) {
                AddressFamily.Ipv4 -> isIpv4Address(rawAddress)
                AddressFamily.Ipv6 -> isIpv6Address(rawAddress)
            }
            if (!matched) {
                if ((family == AddressFamily.Ipv6 && isIpv4Address(rawAddress)) ||
                    (family == AddressFamily.Ipv4 && isIpv6Address(rawAddress))
                ) {
                    return@runCatching PublicProbeParseOutcome.FamilyUnavailable
                }
                return@runCatching PublicProbeParseOutcome.Invalid
            }

            val country = json["country"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val countryCode = (json["country_code"] ?: json["countryCode"])?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val region = (json["region"] ?: json["region_name"])?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val city = json["city"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val isp = (json["organization_name"] ?: json["organization"] ?: json["isp"] ?: json["org"])
                ?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

            PublicProbeParseOutcome.Success(
                ParsedPublicProbeAddress(
                    address = rawAddress,
                    country = country,
                    countryCode = countryCode,
                    region = region,
                    city = city,
                    isp = isp,
                ),
            )
        }.getOrDefault(PublicProbeParseOutcome.Invalid)
    }

    // 3. 纯文本单行 IP
    val rawAddress = trimmed.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
        ?: return PublicProbeParseOutcome.Invalid
    val matched = when (family) {
        AddressFamily.Ipv4 -> isIpv4Address(rawAddress)
        AddressFamily.Ipv6 -> isIpv6Address(rawAddress)
    }
    if (matched) {
        return PublicProbeParseOutcome.Success(ParsedPublicProbeAddress(address = rawAddress))
    }
    if ((family == AddressFamily.Ipv6 && isIpv4Address(rawAddress)) ||
        (family == AddressFamily.Ipv4 && isIpv6Address(rawAddress))
    ) {
        return PublicProbeParseOutcome.FamilyUnavailable
    }
    return PublicProbeParseOutcome.Invalid
}

internal data class PublicProbeBatch(
    val ipv4: PublicProbeAttempt,
    val ipv6: PublicProbeAttempt,
    val cloudflareIpv4: PublicProbeAttempt,
    val cloudflareIpv6: PublicProbeAttempt,
)

internal data class FamilyProbeBatch(
    val general: PublicProbeAttempt,
    val cloudflare: PublicProbeAttempt,
)

internal fun applyPublicProbeAttempts(
    previous: PublicNetworkProbeState,
    batch: PublicProbeBatch,
    completedAtMillis: Long,
): PublicNetworkProbeState {
    return PublicNetworkProbeState(
        ipv4 = previous.ipv4.applyAttempt(batch.ipv4, completedAtMillis),
        ipv6 = previous.ipv6.applyAttempt(batch.ipv6, completedAtMillis),
        cloudflareIpv4 = previous.cloudflareIpv4.applyAttempt(batch.cloudflareIpv4, completedAtMillis),
        cloudflareIpv6 = previous.cloudflareIpv6.applyAttempt(batch.cloudflareIpv6, completedAtMillis),
        refreshing = false,
        lastCompletedAtMillis = completedAtMillis,
    )
}

internal fun applyPublicProbeAttempt(
    previous: PublicNetworkProbeState,
    family: AddressFamily,
    batch: FamilyProbeBatch,
    completedAtMillis: Long,
): PublicNetworkProbeState {
    return when (family) {
        AddressFamily.Ipv4 -> previous.copy(
            ipv4 = previous.ipv4.applyAttempt(batch.general, completedAtMillis),
            cloudflareIpv4 = previous.cloudflareIpv4.applyAttempt(batch.cloudflare, completedAtMillis),
            refreshing = false,
            lastCompletedAtMillis = completedAtMillis,
        )
        AddressFamily.Ipv6 -> previous.copy(
            ipv6 = previous.ipv6.applyAttempt(batch.general, completedAtMillis),
            cloudflareIpv6 = previous.cloudflareIpv6.applyAttempt(batch.cloudflare, completedAtMillis),
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
            target = attempt.target,
            country = attempt.country,
            countryCode = attempt.countryCode,
            region = attempt.region,
            city = attempt.city,
            isp = attempt.isp,
            colo = attempt.colo,
            warp = attempt.warp,
        )

        is PublicProbeAttempt.Failure -> copy(
            address = if (attempt.error == PublicProbeError.Unavailable) "" else address,
            endpointHost = attempt.endpointHost,
            error = attempt.error,
            errorMessage = attempt.message,
            stale = if (attempt.error == PublicProbeError.Unavailable) false else address.isNotEmpty(),
        )
    }
}

internal val DefaultPublicProbeEndpoints = listOf(
    PublicProbeEndpoint(
        family = AddressFamily.Ipv4,
        target = ProbeTarget.General,
        url = "https://ipv4.geojs.io/v1/ip/geo.json",
        host = "ipv4.geojs.io",
    ),
    PublicProbeEndpoint(
        family = AddressFamily.Ipv6,
        target = ProbeTarget.General,
        url = "https://ipv6.geojs.io/v1/ip/geo.json",
        host = "ipv6.geojs.io",
    ),
    PublicProbeEndpoint(
        family = AddressFamily.Ipv4,
        target = ProbeTarget.Cloudflare,
        url = "https://ipv4.icanhazip.com/cdn-cgi/trace",
        host = "ipv4.icanhazip.com",
    ),
    PublicProbeEndpoint(
        family = AddressFamily.Ipv6,
        target = ProbeTarget.Cloudflare,
        url = "https://ipv6.icanhazip.com/cdn-cgi/trace",
        host = "ipv6.icanhazip.com",
    ),
)

private val NetworkProbeJson = Json { ignoreUnknownKeys = true }
private val PublicAddressJsonKeys = listOf("ip", "query", "address")
