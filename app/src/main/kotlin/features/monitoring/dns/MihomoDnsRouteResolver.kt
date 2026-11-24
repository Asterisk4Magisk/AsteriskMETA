// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.dns

import engine.mihomo.runtime.MihomoDnsRoute

/**
 * Tells whether a DNS lookup reached its upstream through the proxy or directly.
 *
 * The core keeps the proxy side and the direct side in separate name server lists, so
 * the upstream the core names in its log decides which side answered the lookup. The
 * core writes the upstream in its own shape, which adds the port and may drop the
 * scheme marker, so both sides reduce to a shared key before the comparison.
 */
internal class MihomoDnsRouteResolver(
    proxyNameservers: List<String>,
    directNameservers: List<String>,
) {
    private val proxyKeys = proxyNameservers.map(::upstreamKey).toSet()
    private val directKeys = directNameservers.map(::upstreamKey).toSet()

    /** Classifies the upstream the core named in its log. */
    fun resolve(upstream: String): MihomoDnsRoute {
        if (upstream.isBlank()) return MihomoDnsRoute.Unknown
        // A name server that names a proxy adapter in its fragment goes through it,
        // but the built-in direct adapter is the local network itself.
        val adapter = upstreamFragmentAdapter(upstream)
        if (adapter != null) {
            return if (adapter.equals(BuiltinDirectAdapter, ignoreCase = true)) {
                MihomoDnsRoute.Direct
            } else {
                MihomoDnsRoute.Proxy
            }
        }
        // The system resolver asks the local network's own servers, which never go
        // through the proxy.
        if (upstream.startsWith(SystemDnsMarker)) return MihomoDnsRoute.Direct
        val key = upstreamKey(upstream)
        return when {
            key in proxyKeys -> MihomoDnsRoute.Proxy
            key in directKeys -> MihomoDnsRoute.Direct
            else -> MihomoDnsRoute.Unknown
        }
    }
}

/**
 * Tells which proxy adapter the address names in its fragment, if any.
 *
 * The fragment carries the adapter name as a bare token, such as `#PROXY` or
 * `#PROXY&h3=true`, while the rest of the tokens come as pairs.
 */
private fun upstreamFragmentAdapter(upstream: String): String? {
    val fragment = upstream.substringAfter('#', missingDelimiterValue = "")
    if (fragment.isEmpty()) return null
    return fragment.split('&').firstOrNull { token -> token.isNotEmpty() && '=' !in token }
}

/** The built-in adapter that leaves through the local network. */
private const val BuiltinDirectAdapter = "DIRECT"

/** The marker the core puts in front of the system resolver's expanded servers. */
internal const val SystemDnsMarker = "system("

/**
 * Reduces an upstream address to a comparable key.
 *
 * The configuration may write `https://dns.alidns.com/dns-query` while the core logs
 * `https://dns.alidns.com:443/dns-query`, and a plain server may appear as `223.5.5.5`
 * in the configuration and as `udp/223.5.5.5:53` in the log. The key drops the scheme
 * marker, the port and the fragment, and keeps the host and the path, so the two
 * shapes of one server land on the same key.
 */
internal fun upstreamKey(value: String): String {
    var rest = value.substringBefore('#').trim()
    // The system resolver logs every system server it asked inside the parentheses,
    // and all of it is one resolver.
    if (rest.startsWith(SystemDnsMarker)) return "system"
    val schemeSeparator = rest.indexOf("://")
    if (schemeSeparator >= 0) {
        rest = rest.substring(schemeSeparator + 3)
    } else {
        // A plain transport marker, such as `udp/223.5.5.5:53` or `tcp/dns.google:853`.
        val marker = rest.substringBefore('/')
        if (marker.length <= 6 && marker.all { it.isLetter() }) {
            rest = rest.substringAfter('/')
        }
    }
    val path = if ('/' in rest) "/${rest.substringAfter('/')}" else ""
    var authority = rest.substringBefore('/')
    if (authority.startsWith("[")) {
        // An IPv6 authority keeps its brackets and drops the port after them.
        authority = authority.substringBefore(']') + "]"
    } else {
        authority = authority.substringBeforeLast(':', missingDelimiterValue = authority)
    }
    return authority + path
}
