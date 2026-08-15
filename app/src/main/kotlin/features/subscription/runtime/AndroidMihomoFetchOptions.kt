// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import com.github.kr328.clash.core.model.FetchProxy
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime

internal data class AndroidSubscriptionFetchOptions(
    val useRunningProxy: Boolean = false,
)

internal fun AndroidSubscriptionFetchOptions.toCoreFetchProxy(): FetchProxy? {
    if (!useRunningProxy) return null
    val runtimeOptions = LocalProxyRuntime.current() ?: return null
    return FetchProxy(
        host = LocalProxyLoopbackAddress,
        port = runtimeOptions.port,
        username = runtimeOptions.username,
        password = runtimeOptions.password,
    )
}
