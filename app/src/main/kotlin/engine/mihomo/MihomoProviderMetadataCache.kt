// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

internal object MihomoProviderMetadataCache {
    private val proxyProviderPresence = object : LinkedHashMap<String, Boolean>(
        MaximumEntries,
        LoadFactor,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > MaximumEntries
        }
    }

    fun hasProxyProviders(key: String, content: () -> String): Boolean {
        synchronized(proxyProviderPresence) {
            if (proxyProviderPresence.containsKey(key)) {
                return proxyProviderPresence.getValue(key)
            }
        }

        val result = content().hasMihomoProxyProviders()
        synchronized(proxyProviderPresence) {
            proxyProviderPresence[key] = result
        }
        return result
    }

    private const val MaximumEntries = 64
    private const val LoadFactor = 0.75f
}
