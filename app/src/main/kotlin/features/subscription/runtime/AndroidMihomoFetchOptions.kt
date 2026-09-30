// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import engine.mihomo.binding.model.FetchProxy
import engine.mihomo.raw.MihomoRawConfigParser
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyOptions
import engine.proxy.LocalProxyRuntime
import engine.proxy.toLocalProxyOptionsOrNull
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class AndroidSubscriptionFetchOptions(
    val useRunningProxy: Boolean = false,
    val fallbackProxy: LocalProxyOptions? = null,
    val fallbackRawConfigPath: String? = null,
)

internal suspend fun AndroidSubscriptionFetchOptions.toCoreFetchProxy(): FetchProxy? {
    if (!useRunningProxy) return null
    val configuredOptions = if (fallbackRawConfigPath != null) {
        withContext(Dispatchers.IO) {
            try {
                MihomoRawConfigParser.parse(File(fallbackRawConfigPath).readBytes())
                    .snapshot?.toLocalProxyOptionsOrNull()
            } catch (_: IOException) {
                null
            }
        }
    } else {
        fallbackProxy
    }
    val runtimeOptions = availableLocalProxy(configuredOptions) ?: return null
    return FetchProxy(
        host = LocalProxyLoopbackAddress,
        port = runtimeOptions.port,
        username = runtimeOptions.username,
        password = runtimeOptions.password,
    )
}

/** Resolve a usable loopback SOCKS proxy without querying VPN/ROOT service state. */
private suspend fun availableLocalProxy(
    configuredOptions: LocalProxyOptions?,
): LocalProxyOptions? = withContext(Dispatchers.IO) {
    listOfNotNull(LocalProxyRuntime.current(), configuredOptions)
        .distinct()
        .firstOrNull { options ->
            ensureActive()
            options.acceptsSocksAuthentication()
        }
}

private fun LocalProxyOptions.acceptsSocksAuthentication(): Boolean {
    if (port !in 1..65535) return false
    val requiresAuthentication = username.isNotBlank()
    val userBytes = username.toByteArray(Charsets.UTF_8)
    val passwordBytes = password.toByteArray(Charsets.UTF_8)
    if (requiresAuthentication && (userBytes.size > 255 || passwordBytes.size > 255)) return false

    return try {
        Socket().use { socket ->
            socket.soTimeout = ProxyProbeTimeoutMillis
            socket.connect(
                InetSocketAddress(LocalProxyLoopbackAddress, port),
                ProxyProbeTimeoutMillis,
            )
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val method = if (requiresAuthentication) 2 else 0
            output.write(byteArrayOf(5, 1, method.toByte()))
            output.flush()
            if (input.read() != 5 || input.read() != method) return@use false
            if (!requiresAuthentication) return@use true

            output.write(byteArrayOf(1, userBytes.size.toByte()))
            output.write(userBytes)
            output.write(passwordBytes.size)
            output.write(passwordBytes)
            output.flush()
            input.read() == 1 && input.read() == 0
        }
    } catch (_: IOException) {
        false
    }
}

private const val ProxyProbeTimeoutMillis = 500
