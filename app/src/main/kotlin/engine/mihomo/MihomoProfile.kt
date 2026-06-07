// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import app.AppState
import app.DefaultMihomoOverrideScriptId
import app.MihomoProfileState
import app.modes.MihomoModeDirect
import app.modes.MihomoModeGlobal
import app.modes.MihomoTunStackGvisor
import app.modes.MihomoTunStackMixed
import app.modes.RunModeVpnService
import app.modes.RunModeTproxy
import app.modes.RunModeTun2Socks
import app.resourceFileUpdateSource
import engine.network.toPortOrNull
import engine.proxy.LocalProxyLoopbackAddress
import engine.tproxy.DefaultTproxyPort
import engine.tun2socks.DefaultTun2SocksProxyPort
import engine.vpn.VpnDefaults
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.RepresentToNode
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.common.ScalarStyle
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.representer.StandardRepresenter
import utils.toTrimmedNonEmptyDistinctList

internal object MihomoProfileFactory {
    fun buildProfile(
        appState: AppState,
        runMode: Int = appState.runMode,
        exposePorts: Boolean = true,
    ): String {
        val selectedProfile = appState.selectedMihomoProfileOrNull()
            ?: error(MihomoProfileMissingErrorMessage)
        val rawContent = selectedProfile.content.trim()
        if (rawContent.isBlank()) {
            error(MihomoProfileEmptyErrorMessage)
        }
        return rawContent.withAsteriskRuntimeOverrides(appState, selectedProfile, runMode, exposePorts)
    }

    fun selectedProfileName(appState: AppState): String {
        return appState.selectedMihomoProfileOrNull()?.name?.takeIf(String::isNotBlank) ?: "No configuration"
    }

    fun tunStack(appState: AppState): String {
        return when (appState.mihomoTunStack) {
            MihomoTunStackGvisor -> "gvisor"
            MihomoTunStackMixed -> "mixed"
            else -> "system"
        }
    }

}

private fun String.withAsteriskRuntimeOverrides(
    appState: AppState,
    selectedProfile: MihomoProfileState?,
    runMode: Int,
    exposePorts: Boolean,
): String {
    val escaped = escapeSupplementaryYamlCodePoints()
    val root = runCatching {
        Load(LoadSettings.builder().build()).loadFromString(escaped.value) as? Map<*, *>
    }.getOrNull()

    if (root == null) {
        if (selectedProfile?.overrideScriptId != DefaultMihomoOverrideScriptId) {
            error("Override script requires a YAML object profile")
        }
        return appendRuntimeOverrides(appState, runMode, exposePorts)
    }

    val rawProfile = linkedMapOf<String, Any?>()
    root.forEach { (key, value) ->
        val name = key as? String ?: return@forEach
        rawProfile[name] = normalizeYamlValue(value)
    }
    return escaped.restore(
        rawProfile
            .applyMihomoProfileScriptOverride(selectedProfile, appState.mihomoOverrideScripts)
            .toAsteriskRuntimeProfileYaml(appState, runMode, exposePorts = exposePorts),
    )
}

private fun Map<String, Any?>.toAsteriskRuntimeProfileYaml(
    appState: AppState,
    runMode: Int,
    forceDns: Boolean = false,
    exposePorts: Boolean = true,
): String {
    val updated = linkedMapOf<String, Any?>()
    val managedKeys = AsteriskManagedTopLevelKeys
    forEach { (name, value) ->
        if (name !in managedKeys) {
            updated[name] = normalizeYamlValue(value)
        }
    }
    if (runMode == RunModeTproxy || runMode == RunModeTun2Socks) {
        updated.putCmfaRootProviderPaths()
    }
    updated.putAsteriskRuntimeOverrides(appState, runMode, forceDns = forceDns, exposePorts = exposePorts)
    return YamlDump.dumpToString(updated)
}

private fun String.appendRuntimeOverrides(
    appState: AppState,
    runMode: Int,
    exposePorts: Boolean,
): String {
    val managed = linkedMapOf<String, Any?>()
    managed.putAsteriskRuntimeOverrides(appState, runMode, exposePorts = exposePorts)
    val overrideYaml = YamlDump.dumpToString(managed)
    return trimEnd() + "\n\n# AsteriskMETA runtime overrides\n" + overrideYaml
}

private fun MutableMap<String, Any?>.putAsteriskRuntimeOverrides(
    appState: AppState,
    runMode: Int,
    forceDns: Boolean = false,
    exposePorts: Boolean = true,
) {
    val mixedPort = appState.localProxyPort.toPortOrNull() ?: VpnDefaults.LOCAL_PROXY_PORT
    val tproxyPort = appState.transparentProxyPort.toPortOrNull() ?: DefaultTproxyPort
    val socksPort = appState.socks5ProxyPort.toPortOrNull() ?: DefaultTun2SocksProxyPort
    val allowLan = appState.requiresMihomoLanAccess(runMode)
    val bindAddress = if (allowLan) "*" else LocalProxyLoopbackAddress
    val control = appState.mihomoControlConfig()

    if (exposePorts) {
        putDisabledInboundOverrides(disableMixed = false)
        put("mixed-port", mixedPort)
        appState.localProxyAuthentication().takeIf(List<String>::isNotEmpty)?.let { authentication ->
            put("authentication", authentication)
            if (runMode == RunModeVpnService && appState.enableVpnAppendHttpProxy) {
                put("skip-auth-prefixes", LocalProxySkipAuthPrefixes)
            }
        }
        if (runMode == RunModeTproxy) {
            put("tproxy-port", tproxyPort)
        }
        if (runMode == RunModeTun2Socks) {
            put("socks-port", socksPort)
        }
        put("external-controller", control.address)
        put("secret", control.secret)
        put("allow-lan", allowLan)
        put("bind-address", bindAddress)
    } else {
        putDisabledInboundOverrides(disableMixed = true)
        put("external-controller", "")
        put("secret", "")
        put("allow-lan", false)
        put("bind-address", LocalProxyLoopbackAddress)
    }
    put("mode", appState.mihomoModeName())
    put("log-level", appState.mihomoLogLevelName())
    put("ipv6", appState.enableIpv6)
    put("geox-url", appState.toMihomoGeoXUrlYamlMap())
    put("profile", normalizedProfileStoreSelected(this["profile"]))
    put("sniffer", appState.toMihomoSnifferYamlMap())
    putDnsOverrides(appState, runMode, forceDns)
    putRootDnsHijackOverrides(appState, runMode)
}

private fun AppState.requiresMihomoLanAccess(runMode: Int): Boolean {
    val hasRootSharing = runMode.requiresRootSharingLanAccess() &&
        externalInterfaces.toTrimmedNonEmptyDistinctList().isNotEmpty()
    return localProxyListenAllInterfaces || hasRootSharing
}

private fun Int.requiresRootSharingLanAccess(): Boolean {
    return this == RunModeTproxy || this == RunModeTun2Socks
}

private fun MutableMap<String, Any?>.putDisabledInboundOverrides(disableMixed: Boolean) {
    put("port", 0)
    put("socks-port", 0)
    put("redir-port", 0)
    put("tproxy-port", 0)
    if (disableMixed) {
        put("mixed-port", 0)
    }
    put("listeners", emptyList<Any>())
    put("tun", linkedMapOf<String, Any?>("enable" to false))
}

private fun AppState.localProxyAuthentication(): List<String> {
    val username = localProxyUsername.trim()
    if (username.isBlank()) {
        return emptyList()
    }
    return listOf("$username:$localProxyPassword")
}

private fun AppState.toMihomoGeoXUrlYamlMap(): Map<String, String> {
    val source = resourceFileUpdateSource()
    return linkedMapOf(
        "geoip" to source.geoIpUrl,
        "geosite" to source.geoSiteUrl,
        "mmdb" to source.mmdbUrl,
        "asn" to source.asnUrl,
    )
}

private fun AppState.toMihomoSnifferYamlMap(): Map<String, Any?> {
    return linkedMapOf(
        "enable" to enableSniffer,
        "override-destination" to enableSnifferOverrideDestination,
        "sniff" to linkedMapOf(
            "TLS" to linkedMapOf(
                "ports" to DefaultMihomoSnifferTlsPorts,
            ),
            "HTTP" to linkedMapOf(
                "ports" to DefaultMihomoSnifferHttpPorts,
            ),
            "QUIC" to linkedMapOf(
                "ports" to DefaultMihomoSnifferQuicPorts,
            ),
        ),
    )
}

private fun MutableMap<String, Any?>.putDnsOverrides(
    appState: AppState,
    runMode: Int,
    forceDns: Boolean,
) {
    val existingDnsEnabled = isMihomoDnsEnabled(this["dns"])
    val enableDns = appState.effectiveLocalDnsEnabledFor(runMode)
    if (forceDns || appState.overrideDns || !existingDnsEnabled) {
        put(
            "dns",
            appState.toMihomoDnsYamlMap(
                appendSystemDns = enableDns && !forceDns && !existingDnsEnabled,
                enable = enableDns,
            ),
        )
    } else if (!enableDns) {
        put("dns", normalizedMihomoDnsWithEnable(this["dns"], enable = false))
    }
    val hosts = mergedMihomoHosts(
        existingHosts = this["hosts"],
        appStateHosts = appState.dnsHosts,
    )
    if (hosts.isNotEmpty()) {
        put("hosts", hosts)
    }
}

private fun isMihomoDnsEnabled(value: Any?): Boolean {
    return ((value as? Map<*, *>)?.get("enable") as? Boolean) == true
}

private fun normalizedMihomoDnsWithEnable(
    value: Any?,
    enable: Boolean,
): Map<String, Any?> {
    return linkedMapOf<String, Any?>().apply {
        (value as? Map<*, *>)?.forEach { (key, childValue) ->
            val name = key as? String ?: return@forEach
            put(name, normalizeYamlValue(childValue))
        }
        put("enable", enable)
    }
}

private fun AppState.toMihomoDnsYamlMap(
    appendSystemDns: Boolean,
    enable: Boolean,
): Map<String, Any?> {
    val nameservers = dnsNameserver.toTrimmedNonEmptyDistinctList()
        .appendSystemDnsIfNeeded(appendSystemDns)
        .ifEmpty { DefaultMihomoDnsNameserver }
    val fallback = dnsFallback.toTrimmedNonEmptyDistinctList()
    val proxyServerNameserver = dnsProxyServerNameserver.toTrimmedNonEmptyDistinctList()
    return linkedMapOf<String, Any?>().apply {
        put("enable", enable)
        put("prefer-h3", dnsPreferH3)
        put("use-hosts", dnsUseHosts)
        put("use-system-hosts", dnsUseSystemHosts)
        put("respect-rules", dnsRespectRules)
        put("ipv6", enableIpv6)
        put("default-nameserver", dnsDefaultNameserver.toTrimmedNonEmptyDistinctList().ifEmpty {
            DefaultMihomoDnsDefaultNameserver
        })
        put("enhanced-mode", dnsEnhancedModeName())
        if (dnsEnhancedMode == MihomoDnsModeFakeIp) {
            put("fake-ip-range", dnsFakeIpRange.trim().ifBlank { DefaultMihomoDnsFakeIpRange })
            put("fake-ip-filter", dnsFakeIpFilter.toTrimmedNonEmptyDistinctList())
        }
        val nameserverPolicy = dnsNameserverPolicy.toMihomoNameserverPolicy()
        if (nameserverPolicy.isNotEmpty()) {
            put("nameserver-policy", nameserverPolicy)
        }
        put("nameserver", nameservers)
        if (fallback.isNotEmpty()) {
            put("fallback", fallback)
        }
        if (proxyServerNameserver.isNotEmpty()) {
            put("proxy-server-nameserver", proxyServerNameserver)
        }
        put("fallback-filter", toMihomoFallbackFilterYamlMap())
    }
}

private fun MutableMap<String, Any?>.putRootDnsHijackOverrides(
    appState: AppState,
    runMode: Int,
) {
    val rootMode = runMode == RunModeTproxy || runMode == RunModeTun2Socks
    if (!rootMode || !appState.effectiveLocalDnsEnabledFor(runMode)) {
        return
    }
    put("proxies", normalizedProxiesWithDnsOut(this["proxies"]))
    put("rules", normalizedRulesWithUdpDnsHijack(this["rules"]))
}

private fun AppState.effectiveLocalDnsEnabledFor(runMode: Int): Boolean {
    return runMode == RunModeTproxy || runMode == RunModeTun2Socks || enableLocalDns
}

private fun normalizedProxiesWithDnsOut(value: Any?): List<Any?> {
    val proxies = (value as? List<*>)?.map(::normalizeYamlValue).orEmpty()
    if (proxies.any(::isDnsOutProxy)) {
        return proxies
    }
    return proxies + linkedMapOf<String, Any?>(
        "name" to MihomoTags.DNS_OUT,
        "type" to "dns",
    )
}

private fun isDnsOutProxy(value: Any?): Boolean {
    return ((value as? Map<*, *>)?.get("name") as? String) == MihomoTags.DNS_OUT
}

private fun normalizedRulesWithUdpDnsHijack(value: Any?): List<Any?> {
    val rules = (value as? List<*>)?.map(::normalizeYamlValue).orEmpty()
        .ifEmpty { listOf("MATCH,DIRECT") }
    if (rules.any { it == UdpDnsHijackRule }) {
        return rules
    }
    return listOf(UdpDnsHijackRule) + rules
}

private fun AppState.dnsEnhancedModeName(): String {
    return MihomoDnsModeValues.getOrElse(dnsEnhancedMode) { MihomoDnsModeValues[MihomoDnsModeRedirHost] }
}

private fun AppState.toMihomoFallbackFilterYamlMap(): Map<String, Any?> {
    return linkedMapOf<String, Any?>(
        "geoip" to dnsFallbackFilterGeoip,
        "geoip-code" to dnsFallbackFilterGeoipCode.trim().uppercase().ifBlank { "CN" },
        "geosite" to dnsFallbackFilterGeosite.toTrimmedNonEmptyDistinctList(),
        "ipcidr" to dnsFallbackFilterIpcidr.toTrimmedNonEmptyDistinctList(),
        "domain" to dnsFallbackFilterDomain.toTrimmedNonEmptyDistinctList(),
    )
}

private fun List<String>.appendSystemDnsIfNeeded(enabled: Boolean): List<String> {
    if (!enabled || any { it.equals(SystemDnsServer, ignoreCase = true) }) {
        return this
    }
    return this + SystemDnsServer
}

private fun List<String>.toMihomoNameserverPolicy(): Map<String, Any?> {
    val policy = linkedMapOf<String, Any?>()
    forEach { entry ->
        val separatorIndex = entry.indexOf(NameserverPolicySeparator)
        if (separatorIndex <= 0 || separatorIndex >= entry.length - NameserverPolicySeparator.length) {
            return@forEach
        }
        val rule = entry.substring(0, separatorIndex).trim()
        val servers = entry.substring(separatorIndex + NameserverPolicySeparator.length)
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        if (rule.isBlank() || servers.isEmpty()) {
            return@forEach
        }
        policy[rule] = if (servers.size == 1) servers.first() else servers
    }
    return policy
}

private fun mergedMihomoHosts(
    existingHosts: Any?,
    appStateHosts: List<String>,
): Map<String, Any?> {
    val hosts = linkedMapOf<String, Any?>()
    (existingHosts as? Map<*, *>)?.forEach { (key, value) ->
        val name = key as? String ?: return@forEach
        hosts[name] = normalizeYamlValue(value)
    }
    appStateHosts.forEach { entry ->
        val separatorIndex = entry.indexOf(':')
        if (separatorIndex <= 0 || separatorIndex >= entry.lastIndex) {
            return@forEach
        }
        val domain = entry.substring(0, separatorIndex).trim()
        val addresses = entry.substring(separatorIndex + 1)
            .split(",")
            .map { it.trim().trim('[', ']') }
            .filter(String::isNotEmpty)
            .distinct()
        if (domain.isNotBlank() && addresses.isNotEmpty()) {
            hosts[domain] = if (addresses.size == 1) addresses.first() else addresses
        }
    }
    return hosts
}

private fun normalizedProfileStoreSelected(value: Any?): Map<String, Any?> {
    val profile = linkedMapOf<String, Any?>()
    (value as? Map<*, *>)?.forEach { (key, childValue) ->
        val name = key as? String ?: return@forEach
        profile[name] = normalizeYamlValue(childValue)
    }
    profile["store-selected"] = true
    profile["store-fake-ip"] = true
    return profile
}

private fun normalizeYamlValue(value: Any?): Any? {
    return when (value) {
        is Map<*, *> -> linkedMapOf<String, Any?>().apply {
            value.forEach { (key, childValue) ->
                val name = key as? String ?: return@forEach
                put(name, normalizeYamlValue(childValue))
            }
        }
        is List<*> -> value.map(::normalizeYamlValue)
        else -> value
    }
}

internal fun AppState.selectedMihomoProfileOrNull(): MihomoProfileState? {
    return mihomoProfiles.firstOrNull { profile -> profile.id == selectedMihomoProfileId }
        ?: mihomoProfiles.firstOrNull()
}

internal fun AppState.hasUsableMihomoProfile(): Boolean {
    return selectedMihomoProfileOrNull()?.content?.isNotBlank() == true
}

internal fun AppState.requireUsableMihomoProfile() {
    val selectedProfile = selectedMihomoProfileOrNull() ?: error(MihomoProfileMissingErrorMessage)
    if (selectedProfile.content.isBlank()) {
        error(MihomoProfileEmptyErrorMessage)
    }
}

internal fun AppState.mihomoModeName(): String {
    return when (mihomoMode) {
        MihomoModeGlobal -> "global"
        MihomoModeDirect -> "direct"
        else -> "rule"
    }
}

internal fun AppState.mihomoLogLevelName(): String {
    return when (coreLogLevel) {
        0 -> "debug"
        2 -> "warning"
        3 -> "error"
        4 -> "silent"
        else -> "info"
    }
}

private val AsteriskManagedTopLevelKeys = setOf(
    "port",
    "socks-port",
    "redir-port",
    "tproxy-port",
    "mixed-port",
    "listeners",
    "tun",
    "authentication",
    "skip-auth-prefixes",
    "external-controller",
    "external-controller-cors",
    "external-controller-unix",
    "external-controller-pipe",
    "external-controller-tls",
    "secret",
    "allow-lan",
    "bind-address",
    "mode",
    "log-level",
    "ipv6",
    "geox-url",
    "sniffer",
)

private const val NameserverPolicySeparator = "=>"
private const val SystemDnsServer = "system://"
private const val UdpDnsHijackRule = "AND,((NETWORK,UDP),(DST-PORT,53)),dns-out"
internal const val MihomoProfileMissingErrorMessage = "No Mihomo configuration selected"
internal const val MihomoProfileEmptyErrorMessage = "Selected Mihomo configuration has no YAML content"

private val LocalProxySkipAuthPrefixes = listOf("127.0.0.1/8", "::1/128")
private val DefaultMihomoSnifferTlsPorts = listOf("443")
private val DefaultMihomoSnifferHttpPorts = listOf("80", "8080-8880")
private val DefaultMihomoSnifferQuicPorts = listOf("443")

private val YamlDumpSettings = DumpSettings.builder()
    .setDefaultFlowStyle(FlowStyle.BLOCK)
    .build()

private val YamlDump = Dump(
    YamlDumpSettings,
    SingleQuotedStringRepresenter(YamlDumpSettings),
)

private class SingleQuotedStringRepresenter(
    settings: DumpSettings,
) : StandardRepresenter(settings) {
    init {
        representers[String::class.java] = RepresentToNode { data ->
            ScalarNode(Tag.STR, data.toString(), ScalarStyle.SINGLE_QUOTED)
        }
    }
}
