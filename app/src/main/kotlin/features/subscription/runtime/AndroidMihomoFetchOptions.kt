// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import com.github.kr328.clash.core.model.FetchProxy
import engine.network.isPort
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime

internal data class AndroidSubscriptionFetchOptions(
    val useRunningProxy: Boolean = false,
    val fallbackProxyPort: Int? = null,
    val fallbackProxyUsername: String = "",
    val fallbackProxyPassword: String = "",
)

internal fun AndroidSubscriptionFetchOptions.toCoreFetchProxy(): FetchProxy? {
    if (!useRunningProxy) return null
    val runtimeOptions = LocalProxyRuntime.current()
    val port = runtimeOptions?.port
        ?: fallbackProxyPort?.takeIf(Int::isPort)
        ?: error("Local proxy port is unavailable")
    return FetchProxy(
        host = LocalProxyLoopbackAddress,
        port = port,
        username = runtimeOptions?.username ?: fallbackProxyUsername,
        password = runtimeOptions?.password ?: fallbackProxyPassword,
    )
}
