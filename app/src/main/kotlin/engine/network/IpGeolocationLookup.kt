// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI

/**
 * What is known about the network an address belongs to. The fields are the ones a user reads to
 * tell one node from another: the province, the network operator and the autonomous system.
 */
internal data class IpGeolocation(
    val address: String,
    val province: String = "",
    val organization: String = "",
    val asn: String = "",
    val country: String = "",
    val countryCode: String = "",
    val city: String = "",
) {
    val isEmpty: Boolean
        get() = province.isBlank() && organization.isBlank() && asn.isBlank() &&
            country.isBlank() && countryCode.isBlank() && city.isBlank()
}

/**
 * Looks up the network of an address.
 *
 * Two free sources are used together, because no single one of them answers the whole question:
 * DB-IP City Lite reports the province and the city, and the ASN database behind the same service
 * reports the autonomous system and its operator. Both are queried over their free endpoints, so
 * no key is needed and nothing has to be shipped with the app.
 *
 * Some networks are described by only one of the city databases: an address can carry a country
 * and a city and still have no province at all. A third free source is therefore asked for the
 * region, but only when the answer so far has none, so a complete answer costs no extra request.
 */
internal class IpGeolocationLookup(
    private val cityLookup: CityLookup = DbIpCityLookup(),
    private val asnLookup: AsnLookup = GeoJsAsnLookup(),
    private val regionFallback: CityLookup = IpInfoLookup(),
) {
    /**
     * Looks up [address]. The two sources are queried independently, so a failure of one still
     * leaves the answer of the other.
     */
    suspend fun lookup(address: String): IpGeolocation {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return IpGeolocation(address = "")
        val city = runCatching { cityLookup.lookup(trimmed) }.getOrNull() ?: IpGeolocation(address = trimmed)
        val asn = runCatching { asnLookup.lookup(trimmed) }.getOrNull() ?: IpGeolocation(address = trimmed)
        val answer = IpGeolocation(
            address = trimmed,
            province = city.province.ifBlank { asn.province },
            organization = asn.organization.ifBlank { city.organization },
            asn = asn.asn.ifBlank { city.asn },
            country = city.country.ifBlank { asn.country },
            countryCode = city.countryCode.ifBlank { asn.countryCode },
            city = city.city.ifBlank { asn.city },
        )
        if (answer.province.isNotBlank()) return answer
        val fallback = runCatching { regionFallback.lookup(trimmed) }.getOrNull() ?: return answer
        return answer.copy(
            province = fallback.province,
            city = answer.city.ifBlank { fallback.city },
            country = answer.country.ifBlank { fallback.country },
            countryCode = answer.countryCode.ifBlank { fallback.countryCode },
        )
    }
}

/** The city-level part of a lookup. */
internal interface CityLookup {
    suspend fun lookup(address: String): IpGeolocation
}

/** The autonomous-system part of a lookup. */
internal interface AsnLookup {
    suspend fun lookup(address: String): IpGeolocation
}

/**
 * The free endpoint of DB-IP City Lite. It reports the country, the province and the city of an
 * address.
 */
internal class DbIpCityLookup(
    private val endpoint: String = DbIpCityEndpoint,
) : CityLookup {
    override suspend fun lookup(address: String): IpGeolocation = withContext(Dispatchers.IO) {
        val body = httpGetText(endpoint + address, accept = "application/json") ?: return@withContext IpGeolocation(address)
        val json = runCatching { GeolocationJson.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@withContext IpGeolocation(address)
        IpGeolocation(
            address = address,
            province = json.stringOrEmpty("stateProv"),
            organization = json.stringOrEmpty("organization"),
            asn = json.stringOrEmpty("asn"),
            country = json.stringOrEmpty("countryName"),
            countryCode = json.stringOrEmpty("countryCode"),
            city = json.stringOrEmpty("city"),
        )
    }
}

/**
 * The ASN part of a lookup. The free GeoJS endpoint reports the autonomous system number, the
 * organization name and the region of an address.
 */
internal class GeoJsAsnLookup(
    private val endpointTemplate: String = GeoJsAsnEndpointTemplate,
) : AsnLookup {
    override suspend fun lookup(address: String): IpGeolocation = withContext(Dispatchers.IO) {
        val isIpv6 = address.contains(':')
        val endpoint = if (isIpv6) {
            endpointTemplate.replace("{family}", "ipv6")
        } else {
            endpointTemplate.replace("{family}", "ipv4")
        }
        val body = httpGetText(endpoint + address + ".json", accept = "application/json")
            ?: return@withContext IpGeolocation(address)
        val json = runCatching { GeolocationJson.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@withContext IpGeolocation(address)
        val organization = json.stringOrEmpty("organization")
        IpGeolocation(
            address = address,
            province = json.stringOrEmpty("region"),
            organization = json.stringOrEmpty("organization_name").ifBlank { organization },
            asn = json.asnLabel(),
            country = json.stringOrEmpty("country"),
            countryCode = json.stringOrEmpty("country_code"),
            city = json.stringOrEmpty("city"),
        )
    }
}

/**
 * The free ipinfo endpoint, used for the region of an address the city database had no region
 * for. It answers the country as a code rather than a name, so only the region and the city are
 * taken from it; the name of the country comes from the database that answered first.
 */
internal class IpInfoLookup(
    private val endpoint: String = IpInfoEndpoint,
) : CityLookup {
    override suspend fun lookup(address: String): IpGeolocation = withContext(Dispatchers.IO) {
        val body = httpGetText(endpoint + address + "/json", accept = "application/json")
            ?: return@withContext IpGeolocation(address)
        val json = runCatching { GeolocationJson.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@withContext IpGeolocation(address)
        IpGeolocation(
            address = address,
            province = json.stringOrEmpty("region"),
            countryCode = json.stringOrEmpty("country"),
            city = json.stringOrEmpty("city"),
        )
    }
}

/**
 * The autonomous system number of an answer, in the `AS15169` form. The number is reported on its
 * own by the source, and the organization already carries the same prefix.
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

private val GeolocationJson = Json { ignoreUnknownKeys = true }

/**
 * A plain GET request that returns the body as text. The answer is small, so the whole body is
 * read at once.
 */
private fun httpGetText(url: String, accept: String): String? {
    val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = HttpLookupTimeoutMillis
        readTimeout = HttpLookupTimeoutMillis
        instanceFollowRedirects = true
        useCaches = false
        setRequestProperty("Accept", accept)
        setRequestProperty("User-Agent", "AsteriskMETA")
    }
    return try {
        val status = connection.responseCode
        if (status !in 200..299) return null
        connection.inputStream.use { input -> input.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private const val DbIpCityEndpoint = "https://api.db-ip.com/v2/free/"
private const val IpInfoEndpoint = "https://ipinfo.io/"
private const val GeoJsAsnEndpointTemplate = "https://{family}.geojs.io/v1/ip/geo/"
private const val HttpLookupTimeoutMillis = 10_000
