// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

internal object MihomoProviderMetadataCache {
    private val providerPresence = providerPresenceCache()
    private val proxyProviderNames = object : LinkedHashMap<String, List<String>>(
        MaximumEntries,
        LoadFactor,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<String>>?,
        ): Boolean {
            return size > MaximumEntries
        }
    }

    fun hasProviders(key: String, content: () -> String): Boolean {
        return providerPresence.cachedProviderPresence(key) { content().hasMihomoProviders() }
    }

    fun getProxyProviderNames(
        key: String,
        detector: () -> List<String>,
    ): List<String> {
        return proxyProviderNames.cachedProviderMetadata(key) { detector().toList() }
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
    ): Boolean = cachedProviderMetadata(key, detector)

    private fun <T> MutableMap<String, T>.cachedProviderMetadata(
        key: String,
        detector: () -> T,
    ): T {
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
