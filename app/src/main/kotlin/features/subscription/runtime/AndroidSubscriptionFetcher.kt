// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import app.MihomoSubscriptionInfo
import engine.network.isPort
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime
import features.subscription.DefaultSubscriptionUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import utils.encodeBase64
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.IDN
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URI
import java.net.URL

internal class AndroidSubscriptionFetcher {
    suspend fun fetchWithMetadata(
        url: String,
        userAgent: String,
        options: AndroidSubscriptionFetchOptions,
    ): AndroidSubscriptionFetchResult = withContext(Dispatchers.IO) {
        val proxy = options.toProxy()
        proxy.withAuthenticator {
            fetchWithRedirects(
                url = url.toIdnUrl(),
                userAgent = userAgent.ifBlank { DefaultSubscriptionUserAgent },
                proxy = proxy,
            )
        }
    }
}

internal data class AndroidSubscriptionFetchResult(
    val content: String,
    val subscriptionInfo: MihomoSubscriptionInfo = MihomoSubscriptionInfo(),
)

internal data class AndroidSubscriptionFetchOptions(
    val useRunningProxy: Boolean = false,
    val fallbackProxyPort: Int? = null,
    val fallbackProxyUsername: String = "",
    val fallbackProxyPassword: String = "",
)

private data class AndroidSubscriptionProxy(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
)

private const val MaxRedirects = 3
private val ProxyAuthenticatorLock = Any()

private fun AndroidSubscriptionFetchOptions.toProxy(): AndroidSubscriptionProxy? {
    if (!useRunningProxy) return null
    val runtimeOptions = LocalProxyRuntime.current()
    val port = runtimeOptions?.port
        ?: fallbackProxyPort?.takeIf(Int::isPort)
        ?: error("Local proxy port is unavailable")
    return AndroidSubscriptionProxy(
        host = LocalProxyLoopbackAddress,
        port = port,
        username = runtimeOptions?.username ?: fallbackProxyUsername,
        password = runtimeOptions?.password ?: fallbackProxyPassword,
    )
}

private fun fetchWithRedirects(
    url: String,
    userAgent: String,
    proxy: AndroidSubscriptionProxy?,
): AndroidSubscriptionFetchResult {
    var currentUrl = url
    repeat(MaxRedirects) {
        val connection = currentUrl.toConnection(proxy)
        try {
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Connection", "close")
            connection.setEmbeddedBasicAuth(currentUrl)

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: error("Redirect location missing")
                currentUrl = URI(currentUrl).resolve(location).toString()
                return@repeat
            }
            if (code !in 200..299) {
                error("HTTP $code")
            }
            return AndroidSubscriptionFetchResult(
                content = connection.inputStream.bufferedReader()
                    .use { reader -> reader.readText() },
                subscriptionInfo = connection.getHeaderField("subscription-userinfo")
                    .toMihomoSubscriptionInfo(),
            )
        } finally {
            connection.disconnect()
        }
    }
    error("Too many redirects")
}

private fun String?.toMihomoSubscriptionInfo(): MihomoSubscriptionInfo {
    if (isNullOrBlank()) return MihomoSubscriptionInfo()
    val values = split(";")
        .mapNotNull { entry ->
            val parts = entry.trim().split("=", limit = 2)
            val key = parts.getOrNull(0)?.trim()?.lowercase()?.takeIf(String::isNotBlank)
            val value = parts.getOrNull(1)?.trim()?.toLongOrNull()
            if (key == null || value == null) null else key to value.coerceAtLeast(0L)
        }
        .toMap()
    return MihomoSubscriptionInfo(
        uploadBytes = values["upload"] ?: 0L,
        downloadBytes = values["download"] ?: 0L,
        totalBytes = values["total"] ?: 0L,
        expireAtSeconds = values["expire"] ?: 0L,
    )
}

private fun String.toConnection(proxy: AndroidSubscriptionProxy?): HttpURLConnection {
    val connection = if (proxy == null) {
        URI(this).toURL().openConnection()
    } else {
        URI(this).toURL().openConnection(proxy.toJavaProxy())
    }
    return (connection as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 60_000
        instanceFollowRedirects = false
        requestMethod = "GET"
    }
}

private fun AndroidSubscriptionProxy.toJavaProxy(): Proxy {
    return Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
}

private inline fun <T> AndroidSubscriptionProxy?.withAuthenticator(block: () -> T): T {
    if (this == null || username.isBlank()) return block()
    synchronized(ProxyAuthenticatorLock) {
        Authenticator.setDefault(toAuthenticator())
        return try {
            block()
        } finally {
            Authenticator.setDefault(null)
        }
    }
}

private fun AndroidSubscriptionProxy.toAuthenticator(): Authenticator {
    return object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            if (requestingHost != host || requestingPort != port) return null
            return PasswordAuthentication(username, password.toCharArray())
        }
    }
}

private fun HttpURLConnection.setEmbeddedBasicAuth(rawUrl: String) {
    val userInfo = runCatching { URL(rawUrl).userInfo }.getOrNull() ?: return
    val parts = userInfo.split(":", limit = 2)
    val user = parts.getOrElse(0) { "" }
    val password = parts.getOrElse(1) { "" }
    val token = "$user:$password".encodeBase64()
    setRequestProperty("Authorization", "Basic $token")
}

private fun String.toIdnUrl(): String {
    val parsedUrl = URL(this)
    val host = parsedUrl.host
    val asciiHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED)
    return if (host == asciiHost) this else replace(host, asciiHost)
}
