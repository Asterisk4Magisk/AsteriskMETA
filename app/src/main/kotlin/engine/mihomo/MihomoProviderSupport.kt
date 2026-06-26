// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

internal enum class MihomoProviderType(
    val topLevelKey: String,
    val cmfaPrefix: String,
) {
    Proxy(topLevelKey = "proxy-providers", cmfaPrefix = "proxies"),
    Rule(topLevelKey = "rule-providers", cmfaPrefix = "rules"),
}

internal fun MutableMap<String, Any?>.putCmfaRootProviderPaths() {
    MihomoProviderType.entries.forEach { type ->
        val providers = this[type.topLevelKey] as? Map<*, *> ?: return@forEach
        val updatedProviders = linkedMapOf<String, Any?>()
        providers.forEach { (providerName, providerValue) ->
            val name = providerName as? String ?: return@forEach
            val provider = providerValue as? Map<*, *>
            if (provider == null) {
                updatedProviders[name] = providerValue
                return@forEach
            }
            val updatedProvider = provider.normalizedProviderMap()
            updatedProvider.cmfaProviderPath(type.cmfaPrefix)?.let { path ->
                updatedProvider["path"] = "$CmfaProvidersDirectory/$path"
            }
            updatedProviders[name] = updatedProvider
        }
        this[type.topLevelKey] = updatedProviders
    }
}

internal fun String.hasMihomoProxyProviders(): Boolean {
    return hasMihomoProvider(MihomoProviderType.Proxy)
}
