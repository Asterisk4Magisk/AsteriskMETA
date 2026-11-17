// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.net.UnknownHostException

/**
 * A resolver the configuration names, in the form Mihomo writes it. A server is either a plain
 * address (`8.8.8.8`), an encrypted address with a scheme (`tls://8.8.8.8`, `https://dns.pub/dns-query`)
 * or the `system` keyword.
 */
internal data class DnsServerSpec(
    val raw: String,
) {
    val scheme: String
        get() {
            val separator = raw.indexOf("://")
            if (separator <= 0) return DnsPlainScheme
            return raw.substring(0, separator).lowercase()
        }

    /** The host of the server, without the scheme, the path, the parameters and the port. */
    val host: String
        get() {
            val separator = raw.indexOf("://")
            val withoutScheme = if (separator > 0) raw.substring(separator + 3) else raw
            val authority = withoutScheme
                .substringBefore('/')
                .substringBefore('#')
                .substringBefore('?')
                .trim()
            return authority.hostOnly()
        }

    /** The port of the server, when the authority carries one. */
    val port: Int
        get() {
            val separator = raw.indexOf("://")
            val withoutScheme = if (separator > 0) raw.substring(separator + 3) else raw
            val authority = withoutScheme
                .substringBefore('/')
                .substringBefore('#')
                .substringBefore('?')
                .trim()
            return authority.portOrDefault()
        }

    val isSystem: Boolean
        get() = raw.trim().equals("system", ignoreCase = true)

    val isPlainAddress: Boolean
        get() = scheme == DnsPlainScheme
}

/**
 * The host of an authority, without the port. An IPv6 authority is written in brackets, so the
 * port is separated from it by the last colon.
 */
private fun String.hostOnly(): String {
    if (startsWith("[")) {
        val closing = indexOf(']')
        if (closing > 0) return substring(1, closing)
    }
    val colon = lastIndexOf(':')
    if (colon <= 0) return this
    // A bare IPv6 address has several colons and carries no port.
    if (count { character -> character == ':' } > 1) return this
    return substring(0, colon)
}

/**
 * The port of an authority, when it carries one.
 */
private fun String.portOrDefault(): Int {
    if (startsWith("[")) {
        val closing = indexOf(']')
        if (closing <= 0) return DnsDefaultPort
        val tail = substring(closing + 1)
        if (!tail.startsWith(":")) return DnsDefaultPort
        return tail.removePrefix(":").toIntOrNull() ?: DnsDefaultPort
    }
    val colon = lastIndexOf(':')
    if (colon <= 0) return DnsDefaultPort
    if (count { character -> character == ':' } > 1) return DnsDefaultPort
    return substring(colon + 1).toIntOrNull() ?: DnsDefaultPort
}

/**
 * The result of one resolution: the addresses of a host and the server that answered.
 */
internal data class DnsResolutionResult(
    val addresses: List<DnsResolvedAddress>,
    val server: String = "",
)

/**
 * Resolves a host name with the servers a Mihomo configuration names.
 *
 * The policy the configuration carries is honoured as Mihomo does: a dedicated policy for the
 * node domain wins, the general proxy-server nameserver is the fallback, and the system resolver
 * is the last resort.
 */
internal class DnsResolver(
    private val plainQueryTimeoutMillis: Int = DnsPlainQueryTimeoutMillis,
    private val dohQueryTimeoutMillis: Int = DnsDohQueryTimeoutMillis,
) {
    /**
     * Resolves [host] against the servers of [policy], asking for every record type in [types].
     * The addresses of all servers are merged, keeping the order they were seen in.
     */
    suspend fun resolve(
        host: String,
        policy: DnsResolutionPolicy,
        types: List<DnsRecordType> = listOf(DnsRecordType.A, DnsRecordType.AAAA),
    ): DnsResolutionResult {
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) return DnsResolutionResult(emptyList())
        val servers = policy.serversFor(trimmedHost)
        if (servers.isEmpty()) {
            val addresses = resolveHostWithSystemResolver(trimmedHost)
            return DnsResolutionResult(addresses = addresses, server = DnsSystemServerLabel)
        }
        val addresses = mutableListOf<DnsResolvedAddress>()
        var answeredBy = ""
        for (server in servers) {
            val spec = DnsServerSpec(server)
            val resolved = runCatching { query(spec, trimmedHost, types) }.getOrDefault(emptyList())
            if (resolved.isEmpty()) continue
            answeredBy = spec.raw
            resolved.forEach { address ->
                if (addresses.none { existing -> existing.address == address.address }) {
                    addresses += address
                }
            }
            // One answering server is enough; the rest would only repeat the same hosts.
            if (addresses.isNotEmpty()) break
        }
        if (addresses.isEmpty()) {
            val fallback = resolveHostWithSystemResolver(trimmedHost)
            return DnsResolutionResult(addresses = fallback, server = DnsSystemServerLabel)
        }
        return DnsResolutionResult(addresses = addresses, server = answeredBy)
    }

    private suspend fun query(
        server: DnsServerSpec,
        host: String,
        types: List<DnsRecordType>,
    ): List<DnsResolvedAddress> = withContext(Dispatchers.IO) {
        when {
            server.isSystem -> resolveHostWithSystemResolver(host)
            server.scheme == DnsDohScheme || server.scheme == DnsDohPlainScheme ->
                queryDoh(server, host, types)

            else -> queryPlain(server, host, types)
        }
    }

    /**
     * A plain DNS query over UDP. The server address is a literal address in every configuration
     * that uses this form, so no bootstrap resolution is needed.
     */
    private fun queryPlain(
        server: DnsServerSpec,
        host: String,
        types: List<DnsRecordType>,
    ): List<DnsResolvedAddress> {
        val address = try {
            java.net.InetAddress.getByName(server.host)
        } catch (_: UnknownHostException) {
            return emptyList()
        }
        val socket = DatagramSocket()
        return try {
            socket.soTimeout = plainQueryTimeoutMillis
            val addresses = mutableListOf<DnsResolvedAddress>()
            types.forEach { type ->
                val question = DnsQuestion(host, type)
                val request = question.toWireMessage()
                val packet = DatagramPacket(request, request.size, address, server.port)
                socket.send(packet)
                val buffer = ByteArray(DnsMaxResponseBytes)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                parseDnsResponseAddresses(buffer.copyOf(response.length), type).forEach { resolved ->
                    addresses += DnsResolvedAddress(address = resolved, type = type)
                }
            }
            addresses
        } catch (_: Exception) {
            emptyList()
        } finally {
            socket.close()
        }
    }

    /**
     * A DNS query over HTTPS. The wire format of RFC 8484 is used, because it is what every
     * encrypted resolver accepts and it needs no JSON dialect.
     */
    private fun queryDoh(
        server: DnsServerSpec,
        host: String,
        types: List<DnsRecordType>,
    ): List<DnsResolvedAddress> {
        val endpoint = server.raw.substringBefore('#').substringBefore('?')
        val url = try {
            URI(endpoint).toURL()
        } catch (_: Exception) {
            return emptyList()
        }
        val addresses = mutableListOf<DnsResolvedAddress>()
        types.forEach { type ->
            val question = DnsQuestion(host, type)
            val request = question.toWireMessage()
            val response = runCatching { postDoh(url, request) }.getOrNull() ?: return@forEach
            parseDnsResponseAddresses(response, type).forEach { resolved ->
                addresses += DnsResolvedAddress(address = resolved, type = type)
            }
        }
        return addresses
    }

    private fun postDoh(url: URL, request: ByteArray): ByteArray {
        val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = dohQueryTimeoutMillis
            readTimeout = dohQueryTimeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", DnsDohContentType)
            setRequestProperty("Accept", DnsDohContentType)
        }
        return try {
            connection.outputStream.use { output -> output.write(request) }
            val status = connection.responseCode
            if (status !in 200..299) error("HTTP $status")
            connection.inputStream.use { input -> input.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}

private const val DnsPlainScheme = "plain"
private const val DnsDohScheme = "https"
private const val DnsDohPlainScheme = "http"
private const val DnsDefaultPort = 53
private const val DnsPlainQueryTimeoutMillis = 5_000
private const val DnsDohQueryTimeoutMillis = 8_000
private const val DnsMaxResponseBytes = 4_096
private const val DnsSystemServerLabel = "system"
private const val DnsDohContentType = "application/dns-message"

