// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

/**
 * The routing the proxy IP check needs so that it measures the group it names instead of
 * whatever the rules happen to pick.
 *
 * A request through the local proxy is routed by the rules, so the address it reports is the
 * address of whichever outbound the rules chose for that request. Measuring the exit of a
 * particular group therefore needs a route that belongs to that group alone: a selector the app
 * can point at the group under test, and a rule that sends the probe endpoints to that selector.
 *
 * The group is hidden and the rule only names the probe endpoints, so nothing else in the
 * configuration is affected.
 */
internal const val MihomoIpCheckProbeGroupName = "__asterisk_ip_check"

/** The built-in outbound the selector falls back to, so it is always usable. */
internal const val MihomoIpCheckProbeFallback = "DIRECT"

/**
 * The hosts the probe asks. The rule matches the suffix, so the IPv4 and IPv6 endpoints of the
 * same service are both covered.
 */
internal val MihomoIpCheckProbeDomains = listOf("geojs.io")

/**
 * Adds the selector the probe is routed through and the rule that sends the probe to it.
 *
 * The rule goes first: the probe has to leave through the group under test even when the
 * configuration has a rule for the same host, because otherwise the answer would describe the
 * configuration rather than the group.
 */
internal fun MutableMap<String, Any?>.putMihomoIpCheckProbeRouting(
    normalize: (Any?) -> Any?,
) {
    val groups: MutableList<Any?> = (this["proxy-groups"] as? List<*>)
        ?.mapNotNull { group -> normalize(group) }
        ?.toMutableList()
        ?: mutableListOf()
    // A configuration that already defines the name keeps its own group: two groups of the same
    // name would stop the core from starting, and the check is worth less than the tunnel.
    val taken = groups.any { group -> (group as? Map<*, *>)?.get("name") == MihomoIpCheckProbeGroupName }
    if (taken) return
    val members = groups
        .mapNotNull { group -> (group as? Map<*, *>)?.get("name") as? String }
        .filter { name -> name.isNotBlank() }
        .toMutableList()
    members += MihomoIpCheckProbeFallback
    groups += linkedMapOf<String, Any?>(
        "name" to MihomoIpCheckProbeGroupName,
        "type" to "select",
        "hidden" to true,
        "proxies" to members.distinct(),
    )
    put("proxy-groups", groups)

    val rules: List<Any?> = (this["rules"] as? List<*>)
        ?.mapNotNull { rule -> normalize(rule) }
        ?: emptyList()
    val probeRules = MihomoIpCheckProbeDomains.map { domain ->
        "DOMAIN-SUFFIX,$domain,$MihomoIpCheckProbeGroupName"
    }
    put("rules", probeRules + rules)
}
