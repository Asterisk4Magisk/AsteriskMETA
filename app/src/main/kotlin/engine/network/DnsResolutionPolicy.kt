// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.network

/**
 * The nameserver configuration of a Mihomo profile, reduced to what a node-domain lookup needs.
 *
 * `proxy-server-nameserver-policy` wins for a domain it matches, `proxy-server-nameserver` is the
 * fallback, and the general `nameserver-policy` and `nameserver` entries apply when no dedicated
 * proxy-server list exists.
 */
internal data class DnsResolutionPolicy(
    val proxyServerNameserver: List<String> = emptyList(),
    val proxyServerNameserverPolicy: Map<String, List<String>> = emptyMap(),
    val nameserver: List<String> = emptyList(),
    val nameserverPolicy: Map<String, List<String>> = emptyMap(),
    val fallback: List<String> = emptyList(),
) {
    /**
     * The servers to ask for [host]. A dedicated policy entry wins over the general policy, and
     * the proxy-server list is preferred over the general list, as Mihomo does.
     */
    fun serversFor(host: String): List<String> {
        val domainPolicy = proxyServerNameserverPolicy.matchFor(host)
        if (domainPolicy != null) return domainPolicy
        if (proxyServerNameserver.isNotEmpty()) return proxyServerNameserver
        val generalPolicy = nameserverPolicy.matchFor(host)
        if (generalPolicy != null) return generalPolicy
        if (nameserver.isNotEmpty()) return nameserver
        return fallback
    }
}

/**
 * The domain rules of a nameserver policy. A rule is a domain suffix pattern, the way Mihomo
 * writes it (`+.example.com`, `*.example.com` or an exact name).
 */
internal fun Map<String, List<String>>.matchFor(host: String): List<String>? {
    if (isEmpty()) return null
    val candidate = host.trim().lowercase()
    var bestMatch: List<String>? = null
    var bestLength = -1
    forEach { (rule, servers) ->
        if (servers.isEmpty()) return@forEach
        if (!rule.matchesDomain(candidate)) return@forEach
        // The longest matching rule is the most specific one and wins.
        if (rule.length > bestLength) {
            bestLength = rule.length
            bestMatch = servers
        }
    }
    return bestMatch
}

/** Whether a Mihomo domain rule matches a host name. */
internal fun String.matchesDomain(host: String): Boolean {
    val rule = trim().lowercase()
    if (rule.isEmpty()) return false
    return when {
        rule.startsWith("+.") -> {
            val suffix = rule.removePrefix("+.")
            host == suffix || host.endsWith(".$suffix")
        }

        rule.startsWith("*.") -> {
            val suffix = rule.removePrefix("*.")
            host.endsWith(".$suffix") && host.length > suffix.length + 1
        }

        rule.startsWith(".") -> host.endsWith(rule) && host.length > rule.length
        else -> host == rule
    }
}
