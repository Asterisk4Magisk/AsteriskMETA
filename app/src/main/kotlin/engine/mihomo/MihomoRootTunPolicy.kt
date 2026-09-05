// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeWhitelist
import utils.toTrimmedNonEmptyDistinctList

/** Only kernel routing policy: never translates bypass providers into DIRECT rules. */
internal fun mihomoRootTunPolicy(
    appListMode: Int,
    applicationUids: List<Int>,
    sharedInterfaces: List<String>,
    bypassProviderNames: List<String>,
    ruleProviders: Any?,
): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
    // lo keeps local OUTPUT capture, but prevents an empty selector from capturing all forwarding.
    val interfaces = sharedInterfaces.toTrimmedNonEmptyDistinctList().filterNot { it == "lo" }
    require(interfaces.all(::isMihomoTunSharedInterface)) { "TUN shared interfaces must be exact interface names" }
    put("include-interface", (listOf("lo") + interfaces).distinct())
    val uids = applicationUids.distinct().sorted()
    when (appListMode) {
        ProxyAppListModeBlacklist -> if (uids.isNotEmpty()) put("exclude-uid", uids)
        ProxyAppListModeWhitelist -> {
            // Preserve Android system DNS capture from the existing ROOT whitelist policy.
            put("include-uid", (uids + RootProxyAppWhitelistSystemUids).distinct().sorted())
        }
    }
    val usable = usableMihomoTunBypassProviders(ruleProviders)
    val selected = bypassProviderNames.toTrimmedNonEmptyDistinctList().filter { it in usable }
    if (selected.isNotEmpty()) put("route-exclude-address-set", selected)
}

internal fun usableMihomoTunBypassProviders(ruleProviders: Any?): Set<String> =
    (ruleProviders as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
        val name = key as? String ?: return@mapNotNull null
        val provider = (value as? Map<*, *>)?.normalizedProviderMap() ?: return@mapNotNull null
        if (!provider["behavior"].asProviderTextOrNull().equals("ipcidr", ignoreCase = true)) return@mapNotNull null
        val usable = when (provider["type"].asProviderTextOrNull()?.lowercase()) {
            "http" -> provider["url"].asProviderTextOrNull() != null
            "file" -> provider["path"].asProviderTextOrNull() != null
            "inline" -> (provider["payload"] as? List<*>)?.isNotEmpty() == true
            else -> false
        }
        name.takeIf { usable }
    }.toSet()

internal fun isMihomoTunSharedInterface(value: String): Boolean =
    value != "lo" && value.matches(Regex("[A-Za-z0-9_.-]{1,15}"))

private val RootProxyAppWhitelistSystemUids = listOf(0, 1052)
