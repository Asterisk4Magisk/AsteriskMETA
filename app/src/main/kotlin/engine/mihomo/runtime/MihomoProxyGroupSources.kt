// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import engine.mihomo.loadMihomoProfileObject

internal data class MihomoProxyGroupSources(
    val providerNames: Set<String>,
    val proxyNames: Set<String>,
    val includeAllProxies: Boolean = false,
)

/** Read the configuration already supplied to the core, not a replay of profile scripts. */
internal fun String.mihomoProxyGroupSources(): Map<String, MihomoProxyGroupSources> {
    val profile = loadMihomoProfileObject() ?: return emptyMap()
    val providerNames = (profile["proxy-providers"] as? Map<*, *>)?.keys
        ?.filterIsInstance<String>()?.toSet().orEmpty()
    return (profile["proxy-groups"] as? List<*>).orEmpty().mapNotNull { value ->
        val group = value as? Map<*, *> ?: return@mapNotNull null
        val name = group["name"] as? String ?: return@mapNotNull null
        val includeAll = group["include-all"] == true
        name to MihomoProxyGroupSources(
            providerNames = if (includeAll || group["include-all-providers"] == true) {
                providerNames
            } else {
                (group["use"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
            },
            proxyNames = (group["proxies"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
            includeAllProxies = includeAll || group["include-all-proxies"] == true,
        )
    }.toMap()
}
