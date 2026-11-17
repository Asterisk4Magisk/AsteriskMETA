// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import engine.network.DnsResolutionPolicy

/**
 * The nameserver configuration of a Mihomo document, reduced to what a node-domain lookup needs.
 *
 * The `dns` section of the configuration names the servers that resolve the node domains:
 * `proxy-server-nameserver-policy` wins for a domain it matches, `proxy-server-nameserver` is the
 * fallback, and the general `nameserver-policy` and `nameserver` entries apply when no dedicated
 * proxy-server list exists.
 */
internal fun Map<*, *>.toDnsResolutionPolicy(): DnsResolutionPolicy {
    val dns = this["dns"] as? Map<*, *> ?: return DnsResolutionPolicy()
    return DnsResolutionPolicy(
        proxyServerNameserver = dns.stringList("proxy-server-nameserver"),
        proxyServerNameserverPolicy = dns.policyMap("proxy-server-nameserver-policy"),
        nameserver = dns.stringList("nameserver"),
        nameserverPolicy = dns.policyMap("nameserver-policy"),
        fallback = dns.stringList("fallback"),
    )
}

private fun Map<*, *>.stringList(key: String): List<String> {
    val value = this[key] ?: return emptyList()
    return when (value) {
        is String -> listOf(value.trim()).filter { it.isNotEmpty() }
        is List<*> -> value.mapNotNull { entry ->
            (entry as? String)?.trim()?.takeIf { it.isNotEmpty() }
        }

        else -> emptyList()
    }
}

private fun Map<*, *>.policyMap(key: String): Map<String, List<String>> {
    val value = this[key] as? Map<*, *> ?: return emptyMap()
    val policy = linkedMapOf<String, List<String>>()
    value.forEach { (rule, servers) ->
        val name = (rule as? String)?.trim().orEmpty()
        if (name.isEmpty()) return@forEach
        val list = when (servers) {
            is String -> listOf(servers.trim()).filter { it.isNotEmpty() }
            is List<*> -> servers.mapNotNull { entry ->
                (entry as? String)?.trim()?.takeIf { it.isNotEmpty() }
            }

            else -> emptyList()
        }
        if (list.isNotEmpty()) policy[name] = list
    }
    return policy
}
