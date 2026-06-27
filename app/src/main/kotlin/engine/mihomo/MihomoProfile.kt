// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import android.content.Context
import app.AppState
import app.DefaultMihomoOverrideScriptId
import app.MihomoProfileState
import app.effectiveLocalDnsEnabled
import app.modes.MihomoModeDirect
import app.modes.MihomoModeGlobal
import app.modes.MihomoTunStackGvisor
import app.modes.MihomoTunStackMixed
import app.modes.RunModeTun
import app.modes.RunModeVpnService
import app.modes.RunModeTproxy
import app.modes.RunModeTun2Socks
import app.modes.isRootRunMode
import app.resourceFileUpdateSource
import engine.network.isIpv4CidrAddress
import engine.network.toPortOrNull
import engine.proxy.LocalProxyLoopbackAddress
import engine.tun.MihomoTunDevice
import engine.tun.MihomoTunInboundName
import engine.tun.MihomoTunRuntimeMarkerKey
import engine.tproxy.DefaultTproxyPort
import engine.tun2socks.DefaultTun2SocksProxyPort
import engine.vpn.VpnDefaults
import engine.vpn.toTunOptions
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.RepresentToNode
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.common.ScalarStyle
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.representer.StandardRepresenter
import utils.toTrimmedNonEmptyDistinctList

internal object MihomoProfileFactory {
    fun buildProfile(
        context: Context,
        appState: AppState,
        runMode: Int = appState.runMode,
        exposePorts: Boolean = true,
    ): String {
        val selectedProfile = appState.selectedMihomoProfileOrNull()
            ?: error(MihomoProfileMissingErrorMessage)
        val rawContent = context.mihomoProfileContentStore().read(selectedProfile).trim()
        if (rawContent.isBlank()) {
            error(MihomoProfileEmptyErrorMessage)
        }
        return rawContent.withAsteriskRuntimeOverrides(appState, selectedProfile, runMode, exposePorts)
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
    if (runMode.isRootRunMode()) {
        updated.putCmfaRootProviderPaths()
    }
    updated.putAsteriskRuntimeOverrides(appState, runMode, forceDns = forceDns, exposePorts = exposePorts)
    return dumpYaml(normalizeYamlValue(updated))
}

private fun String.appendRuntimeOverrides(
    appState: AppState,
    runMode: Int,
    exposePorts: Boolean,
): String {
    val managed = linkedMapOf<String, Any?>()
    managed.putAsteriskRuntimeOverrides(appState, runMode, exposePorts = exposePorts)
    val overrideYaml = dumpYaml(normalizeYamlValue(managed))
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
        if (runMode == RunModeTun) {
            put(MihomoTunRuntimeMarkerKey, true)
            put("listeners", listOf(appState.toMihomoTunListenerYamlMap()))
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
    put("geodata-mode", appState.enableGeodataMode)
    put("geodata-loader", appState.mihomoGeodataLoaderName())
    put("ipv6", appState.enableIpv6)
    put("geox-url", appState.toMihomoGeoXUrlYamlMap())
    put("profile", normalizedProfileStoreSelected(this["profile"]))
    put("sniffer", appState.toMihomoSnifferYamlMap())
    putDnsOverrides(appState, forceDns)
    putUdpDnsHijackOverrides(appState, runMode)
    putNtpWriteToSystemOverride()
}

private fun AppState.requiresMihomoLanAccess(runMode: Int): Boolean {
    val hasRootSharing = runMode.isRootRunMode() &&
        externalInterfaces.toTrimmedNonEmptyDistinctList().isNotEmpty()
    return localProxyListenAllInterfaces || hasRootSharing
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
    forceDns: Boolean,
) {
    val existingDnsEnabled = isMihomoDnsEnabled(this["dns"])
    val enableDns = appState.effectiveLocalDnsEnabled
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
            val fakeIpRange = dnsFakeIpRange.trim()
                .takeIf(::isIpv4CidrAddress)
                ?: DefaultMihomoDnsFakeIpRange
            put("fake-ip-range", fakeIpRange)
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

private fun MutableMap<String, Any?>.putUdpDnsHijackOverrides(
    appState: AppState,
    runMode: Int,
) {
    val requiresUdpDnsHijackRule = runMode.isRootRunMode() ||
        (runMode == RunModeVpnService && appState.enableVpnHevTun)
    if (!requiresUdpDnsHijackRule || !appState.effectiveLocalDnsEnabled) {
        return
    }
    put("proxies", normalizedProxiesWithDnsOut(this["proxies"]))
    put("rules", normalizedRulesWithUdpDnsHijack(this["rules"]))
}

private fun MutableMap<String, Any?>.putNtpWriteToSystemOverride() {
    val ntp = linkedMapOf<String, Any?>()
    (this["ntp"] as? Map<*, *>)?.forEach { (key, value) ->
        val name = key as? String ?: return@forEach
        ntp[name] = normalizeYamlValue(value)
    }
    ntp["write-to-system"] = false
    put("ntp", ntp)
}

private fun AppState.toMihomoTunListenerYamlMap(): Map<String, Any?> {
    val tunOptions = toTunOptions()
    return linkedMapOf<String, Any?>(
        "name" to MihomoTunInboundName,
        "type" to "tun",
        "device" to MihomoTunDevice,
        "stack" to MihomoProfileFactory.tunStack(this),
        "auto-route" to false,
        "auto-detect-interface" to false,
        "auto-redirect" to false,
        "mtu" to tunOptions.mtu,
        "inet4-address" to listOf("${tunOptions.ipv4Address.address}/${tunOptions.ipv4Address.prefixLength}"),
    ).apply {
        if (enableLocalDns) {
            put("dns-hijack", listOf("0.0.0.0:53"))
        }
        if (enableIpv6) {
            put("inet6-address", listOf("${tunOptions.ipv6Address.address}/${tunOptions.ipv6Address.prefixLength}"))
        }
    }
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

internal fun AppState.selectedMihomoProfileOrNull(): MihomoProfileState? {
    return mihomoProfiles.firstOrNull { profile -> profile.id == selectedMihomoProfileId }
        ?: mihomoProfiles.firstOrNull()
}

internal fun AppState.hasUsableMihomoProfile(): Boolean {
    return selectedMihomoProfileOrNull()?.hasContent == true
}

internal fun AppState.requireUsableMihomoProfile() {
    val selectedProfile = selectedMihomoProfileOrNull() ?: error(MihomoProfileMissingErrorMessage)
    if (!selectedProfile.hasContent) {
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

private fun AppState.mihomoGeodataLoaderName(): String {
    return MihomoGeodataLoaderValues.getOrElse(mihomoGeodataLoader) {
        MihomoGeodataLoaderValues[MihomoGeodataLoaderStandard]
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
    "geodata-mode",
    "geodata-loader",
    "ipv6",
    MihomoTunRuntimeMarkerKey,
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

private fun dumpYaml(value: Any?): String {
    return Dump(
        YamlDumpSettings,
        SingleQuotedStringRepresenter(YamlDumpSettings),
    ).dumpToString(value)
}

private class SingleQuotedStringRepresenter(
    settings: DumpSettings,
) : StandardRepresenter(settings) {
    init {
        representers[String::class.java] = RepresentToNode { data ->
            ScalarNode(Tag.STR, data.toString(), ScalarStyle.SINGLE_QUOTED)
        }
    }

    override fun representMappingEntry(entry: Map.Entry<*, *>): NodeTuple {
        val keyNode = if (entry.key is String) {
            ScalarNode(Tag.STR, entry.key.toString(), ScalarStyle.PLAIN)
        } else {
            representData(entry.key)
        }
        return NodeTuple(keyNode, representData(entry.value))
    }
}
