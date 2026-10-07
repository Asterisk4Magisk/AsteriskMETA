// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

internal class AndroidNetworkMonitor(context: Context) {
    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    @Suppress("DEPRECATION")
    fun snapshot(): LocalNetworkSnapshot {
        return connectivityManager.readLocalNetworkSnapshot(connectivityManager.allNetworks.toList())
    }
}

internal data class PublicProbeProxy(
    val proxy: Proxy,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
    val proxyAuthorization: String? = null,
)

internal class PublicNetworkProbeClient(
    private val endpoints: List<PublicProbeEndpoint> = DefaultPublicProbeEndpoints,
    private val proxyProvider: (() -> PublicProbeProxy?)? = null,
) {
    suspend fun probe(): PublicProbeBatch = coroutineScope {
        val ipv4Endpoint = endpoints.first { it.family == AddressFamily.Ipv4 && it.target == ProbeTarget.General }
        val ipv6Endpoint = endpoints.first { it.family == AddressFamily.Ipv6 && it.target == ProbeTarget.General }
        val cfIpv4Endpoint = endpoints.first { it.family == AddressFamily.Ipv4 && it.target == ProbeTarget.Cloudflare }
        val cfIpv6Endpoint = endpoints.first { it.family == AddressFamily.Ipv6 && it.target == ProbeTarget.Cloudflare }
        val ipv4 = async { probeOne(ipv4Endpoint) }
        val ipv6 = async { probeOne(ipv6Endpoint) }
        val cfIpv4 = async { probeOne(cfIpv4Endpoint) }
        val cfIpv6 = async { probeOne(cfIpv6Endpoint) }
        PublicProbeBatch(
            ipv4 = ipv4.await(),
            ipv6 = ipv6.await(),
            cloudflareIpv4 = cfIpv4.await(),
            cloudflareIpv6 = cfIpv6.await(),
        )
    }

    suspend fun probe(family: AddressFamily): FamilyProbeBatch = coroutineScope {
        val generalEndpoint = endpoints.first { it.family == family && it.target == ProbeTarget.General }
        val cfEndpoint = endpoints.first { it.family == family && it.target == ProbeTarget.Cloudflare }
        val general = async { probeOne(generalEndpoint) }
        val cf = async { probeOne(cfEndpoint) }
        FamilyProbeBatch(
            general = general.await(),
            cloudflare = cf.await(),
        )
    }

    private suspend fun probeOne(endpoint: PublicProbeEndpoint): PublicProbeAttempt {
        return try {
            withTimeout(PublicProbeOverallTimeoutMillis.milliseconds) {
                executeRequest(endpoint)
            }
        } catch (_: TimeoutCancellationException) {
            PublicProbeAttempt.Failure(
                error = PublicProbeError.Timeout,
                message = "Timed out",
                endpointHost = endpoint.host,
            )
        }
    }

    private suspend fun executeRequest(endpoint: PublicProbeEndpoint): PublicProbeAttempt {
        return suspendCancellableCoroutine { continuation ->
            val connectionReference = AtomicReference<HttpURLConnection?>()
            continuation.invokeOnCancellation {
                connectionReference.getAndSet(null)?.disconnect()
            }
            Dispatchers.IO.dispatch(EmptyCoroutineContext) {
                if (!continuation.isActive) return@dispatch
                val startedAt = SystemClock.elapsedRealtime()
                val attempt = runCatching {
                    val probeProxy = proxyProvider?.invoke()
                    probeProxy.withAuthenticator {
                        val rawConnection = if (probeProxy != null) {
                            URI(endpoint.url).toURL().openConnection(probeProxy.proxy)
                        } else {
                            URI(endpoint.url).toURL().openConnection()
                        }
                        val connection = (rawConnection as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = PublicProbeSocketTimeoutMillis
                            readTimeout = PublicProbeSocketTimeoutMillis
                            instanceFollowRedirects = true
                            useCaches = false
                            setRequestProperty("Accept", "application/json, text/plain")
                            setRequestProperty("User-Agent", "AsteriskMETA")
                            probeProxy?.proxyAuthorization?.let { auth ->
                                setRequestProperty("Proxy-Authorization", auth)
                            }
                        }
                        connectionReference.set(connection)
                        if (!continuation.isActive) {
                            connection.disconnect()
                            return@runCatching PublicProbeAttempt.Failure(
                                PublicProbeError.Network,
                                "Cancelled",
                                endpoint.host,
                            )
                        }
                        try {
                            val status = connection.responseCode
                            if (status !in 200..299) error("HTTP $status")
                            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                                reader.readLimited(PublicProbeMaxResponseChars)
                            }
                            when (val outcome = parsePublicProbeOutcome(body, endpoint.family, endpoint.target)) {
                                is PublicProbeParseOutcome.Success -> {
                                    val parsed = outcome.parsed
                                    PublicProbeAttempt.Success(
                                        address = parsed.address,
                                        durationMillis = SystemClock.elapsedRealtime() - startedAt,
                                        endpointHost = endpoint.host,
                                        target = endpoint.target,
                                        country = parsed.country,
                                        countryCode = parsed.countryCode,
                                        region = parsed.region,
                                        city = parsed.city,
                                        isp = parsed.isp,
                                        colo = parsed.colo,
                                        warp = parsed.warp,
                                    )
                                }

                                PublicProbeParseOutcome.FamilyUnavailable -> {
                                    PublicProbeAttempt.Failure(
                                        PublicProbeError.Unavailable,
                                        "",
                                        endpoint.host,
                                    )
                                }

                                PublicProbeParseOutcome.Invalid -> {
                                    PublicProbeAttempt.Failure(
                                        PublicProbeError.InvalidResponse,
                                        "Invalid ${endpoint.family.name.uppercase()} address",
                                        endpoint.host,
                                    )
                                }
                            }
                        } finally {
                            connectionReference.compareAndSet(connection, null)
                            connection.disconnect()
                        }
                    }
                }.getOrElse { error ->
                    PublicProbeAttempt.Failure(
                        error = PublicProbeError.Network,
                        message = error.message?.take(160).orEmpty().ifBlank { "Request failed" },
                        endpointHost = endpoint.host,
                    )
                }
                if (continuation.isActive) continuation.resume(attempt)
            }
        }
    }
}

private fun ConnectivityManager.readLocalNetworkSnapshot(networks: List<Network>): LocalNetworkSnapshot {
    val selected = networks
        .mapNotNull { network ->
            val capabilities = getNetworkCapabilities(network) ?: return@mapNotNull null
            val linkProperties = getLinkProperties(network) ?: return@mapNotNull null
            NetworkCandidate(
                network = network,
                capabilities = capabilities,
                linkProperties = linkProperties,
            )
        }
        .filter { candidate ->
            candidate.capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        .maxWithOrNull(
            compareBy<NetworkCandidate> { candidate ->
                !candidate.capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }.thenBy { candidate ->
                candidate.capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }.thenBy { candidate ->
                candidate.network == activeNetwork
            },
        )
        ?: return LocalNetworkSnapshot(updatedAtMillis = System.currentTimeMillis())
    val capabilities = selected.capabilities
    val linkProperties = selected.linkProperties
    val addresses = linkProperties.linkAddresses
        .mapNotNull { linkAddress -> linkAddress.address.toLocalAddressOrNull() }
        .distinct()
    return LocalNetworkSnapshot(
        transport = capabilities.toNetworkTransport(),
        networkAvailable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        internetValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        interfaceName = linkProperties.interfaceName.orEmpty(),
        ipv4Addresses = addresses.filter { address -> address.contains('.') },
        ipv6Addresses = addresses.filter { address -> address.contains(':') },
        gateways = linkProperties.routes
            .asSequence()
            .filter { route -> route.isDefaultRoute }
            .mapNotNull { route -> route.gateway?.toLocalAddressOrNull() }
            .distinct()
            .toList(),
        dnsServers = linkProperties.dnsServers.mapNotNull(InetAddress::toLocalAddressOrNull).distinct(),
        updatedAtMillis = System.currentTimeMillis(),
    )
}

private fun NetworkCapabilities.toNetworkTransport(): NetworkTransport {
    return when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.Wifi
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.Cellular
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.Ethernet
        hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkTransport.Bluetooth
        hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkTransport.Vpn
        else -> NetworkTransport.Other
    }
}

private fun InetAddress.toLocalAddressOrNull(): String? {
    if (this !is Inet4Address && this !is Inet6Address) return null
    if (isAnyLocalAddress || isLoopbackAddress || isMulticastAddress) return null
    return hostAddress?.substringBefore('%')?.takeIf(String::isNotBlank)
}

private fun java.io.Reader.readLimited(maxChars: Int): String {
    val result = StringBuilder(maxChars.coerceAtMost(256))
    val buffer = CharArray(256)
    while (result.length <= maxChars) {
        val count = read(buffer, 0, minOf(buffer.size, maxChars + 1 - result.length))
        if (count < 0) break
        result.appendRange(buffer, 0, count)
    }
    if (result.length > maxChars) error("Response too large")
    return result.toString()
}

private data class NetworkCandidate(
    val network: Network,
    val capabilities: NetworkCapabilities,
    val linkProperties: LinkProperties,
)

private fun PublicProbeProxy.toAuthenticator(): Authenticator {
    return object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            if (requestingHost != host || requestingPort != port) return null
            return PasswordAuthentication(username, password.toCharArray())
        }
    }
}

private inline fun <T> PublicProbeProxy?.withAuthenticator(block: () -> T): T {
    if (this == null || username.isBlank()) return block()
    synchronized(PublicProbeAuthenticatorLock) {
        Authenticator.setDefault(toAuthenticator())
        return try {
            block()
        } finally {
            Authenticator.setDefault(null)
        }
    }
}

private val PublicProbeAuthenticatorLock = Any()
private const val PublicProbeSocketTimeoutMillis = 15_000
private const val PublicProbeOverallTimeoutMillis = 15_000L
private const val PublicProbeMaxResponseChars = 8_192
