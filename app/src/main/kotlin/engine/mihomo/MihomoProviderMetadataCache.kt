// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

internal object MihomoProviderMetadataCache {
    private val providerPresence = providerPresenceCache()
    private val proxyProviderPresence = object : LinkedHashMap<String, Boolean>(
        MaximumEntries,
        LoadFactor,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > MaximumEntries
        }
    }

    fun hasProviders(key: String, content: () -> String): Boolean {
        return providerPresence.cachedProviderPresence(key) { content().hasMihomoProviders() }
    }

    fun hasProxyProviders(key: String, content: () -> String): Boolean {
        return proxyProviderPresence.cachedProviderPresence(key) { content().hasMihomoProxyProviders() }
    }

    private fun providerPresenceCache() = object : LinkedHashMap<String, Boolean>(
        MaximumEntries,
        LoadFactor,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > MaximumEntries
        }
    }

    private fun MutableMap<String, Boolean>.cachedProviderPresence(
        key: String,
        detector: () -> Boolean,
    ): Boolean {
        synchronized(this) {
            if (containsKey(key)) return getValue(key)
        }
        val result = detector()
        synchronized(this) { this[key] = result }
        return result
    }

    private const val MaximumEntries = 64
    private const val LoadFactor = 0.75f
}
