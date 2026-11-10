// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import android.content.Context
import app.AppState
import app.withCompatibleProxyAppListMode
import app.MihomoProfileState
import app.MihomoRuleOverrideState
import app.effectiveLocalDnsEnabled
import app.modes.MihomoModeDirect
import app.modes.MihomoModeGlobal
import app.modes.MihomoTunStackGvisor
import app.modes.MihomoTunStackMixed
import app.modes.MihomoTunStackMips
import app.modes.RunModeBpf2Socks
import app.modes.RunModeTun
import app.modes.RunModeVpnService
import app.modes.RunModeTproxy
import app.modes.RunModeTun2Socks
import app.modes.isRootRunMode
import app.resourceFileUpdateSource
import app.rootIpv6DataPathEnabled
import engine.network.isIpv4CidrAddress
import engine.network.toPortOrNull
import engine.proxy.LocalProxyLoopbackAddress
import engine.root.config.resolveRootProxyApplicationUids
import engine.root.RootModeEngine
import engine.vpn.VpnDefaults
import engine.vpn.toTunOptions
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.RepresentToNode
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.common.ScalarStyle
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.representer.StandardRepresenter
import utils.toTrimmedNonEmptyDistinctList

internal const val Bpf2SocksRuntimeMarkerKey = "x-asteriskmeta-root-bpf2socks"
internal const val MihomoTproxyInboundName = "asterisk-tproxy"
internal const val MihomoTunDevice = "asterisk0"
internal const val MihomoTunInboundName = "asterisk-tun"
internal const val MihomoTunRuntimeMarkerKey = "x-asteriskmeta-root-tun"

internal const val DefaultMihomoTunCongestionController = "cubic"
internal val MihomoTunCongestionControllers = listOf("cubic", "reno", "bbr", "bbr3")

internal fun normalizeMihomoTunCongestionController(value: String): String =
    value.takeIf { it in MihomoTunCongestionControllers } ?: DefaultMihomoTunCongestionController

internal val AppState.effectiveMihomoTunCongestionController: String
    get() = if (mihomoTunStack == MihomoTunStackMips) {
        normalizeMihomoTunCongestionController(mihomoTunCongestionController)
    } else {
        ""
    }

internal object MihomoProfileFactory {
    fun buildProfileBytes(
        context: Context,
        appState: AppState,
        runMode: Int = appState.runMode,
        exposePorts: Boolean = true,
        useRootProviderPaths: Boolean = runMode.isRootRunMode(),
    ): ByteArray {
        val selectedProfile = appState.selectedMihomoProfileOrNull()
            ?: error(MihomoProfileMissingErrorMessage)
        val sourceBytes = context.mihomoProfileContentStore().readBytes(selectedProfile)
        return resolveMihomoRuntimeProfileBytes(
            sourceBytes = sourceBytes,
            disableOverrides = selectedProfile.disableOverrides,
        ) { rawContent ->
            rawContent.trim().withAsteriskRuntimeOverrides(
                appState, selectedProfile, runMode, exposePorts,
                if (runMode == RunModeTun) context.resolveRootProxyApplicationUids(appState.proxyAppListSelectedApps) else emptyList(),
                useRootProviderPaths,
                context.mihomoControllerSocket().absolutePath,
            )
        }
    }

    fun buildProfile(
        context: Context,
        appState: AppState,
        runMode: Int = appState.runMode,
        exposePorts: Boolean = true,
        useRootProviderPaths: Boolean = runMode.isRootRunMode(),
    ): String {
        return buildProfileBytes(context, appState, runMode, exposePorts, useRootProviderPaths).toString(Charsets.UTF_8)
    }

    /**
     * Compiles [profile] from [sourceContent] without touching any runtime: nothing is written,
     * no core is loaded and no port is allocated. [profile] and [sourceContent] may be unsaved
     * drafts. The result has not been parsed by a core.
     */
    fun compilePreview(
        context: Context,
        appState: AppState,
        profile: MihomoProfileState,
        sourceContent: String,
        runMode: Int = appState.runMode,
    ): MihomoProfileCompilation {
        val source = sourceContent.trim().loadMihomoProfileObject()
            ?: error(MihomoProfileObjectRequiredErrorMessage)
        return source.compileMihomoProfile(
            appState, profile, runMode, exposePorts = true,
            if (runMode == RunModeTun) context.resolveRootProxyApplicationUids(appState.proxyAppListSelectedApps) else emptyList(),
            useRootProviderPaths = runMode.isRootRunMode(),
            context.mihomoControllerSocket().absolutePath,
        )
    }

    /**
     * The settings a compile applies over the override script's output, so a script debug run
     * shows what the core would receive. Nothing is written and no core is loaded.
     */
    fun managedStage(
        context: Context,
        appState: AppState,
        runMode: Int = appState.runMode,
    ): (Map<String, Any?>) -> Map<String, Any?> {
        val tunApplicationUids =
            if (runMode == RunModeTun) context.resolveRootProxyApplicationUids(appState.proxyAppListSelectedApps) else emptyList()
        val controllerSocketPath = context.mihomoControllerSocket().absolutePath
        return { scripted ->
            scripted.toAsteriskRuntimeProfileObject(
                appState, runMode, exposePorts = true, tunApplicationUids = tunApplicationUids,
                useRootProviderPaths = runMode.isRootRunMode(), controllerSocketPath = controllerSocketPath,
            )
        }
    }

    fun tunStack(appState: AppState): String {
        return when (appState.mihomoTunStack) {
            MihomoTunStackGvisor -> "gvisor"
            MihomoTunStackMixed -> "mixed"
            MihomoTunStackMips -> "mips"
            else -> "system"
        }
    }

}

private fun String.withAsteriskRuntimeOverrides(
    appState: AppState,
    selectedProfile: MihomoProfileState?,
    runMode: Int,
    exposePorts: Boolean,
    tunApplicationUids: List<Int>,
    useRootProviderPaths: Boolean,
    controllerSocketPath: String,
): String {
    // The managed settings replace keys of a parsed object; only raw configuration mode passes
    // other documents through.
    val source = loadMihomoProfileObject() ?: error(MihomoProfileObjectRequiredErrorMessage)
    return source.compileMihomoProfile(
        appState, selectedProfile, runMode, exposePorts, tunApplicationUids,
        useRootProviderPaths, controllerSocketPath,
    ).finalYaml
}

/** Every stage of one compile, so a preview can show them without running the script again. */
internal class MihomoProfileCompilation(
    /** The source configuration after the GUI rule override: what the script receives. */
    val scriptInput: Map<String, Any?>,
    val ruleDiagnostics: MihomoRuleOverrideDiagnostics,
    val finalYaml: String,
) {
    val scriptInputYaml: String by lazy { dumpYaml(normalizeYamlValue(scriptInput)) }
}

/**
 * The managed pipeline of a parsed source configuration: GUI rule override, then the override
 * script, then the settings AsteriskMETA owns. The start, the hot reload and the preview all
 * compile through here.
 */
internal fun Map<String, Any?>.compileMihomoProfile(
    appState: AppState,
    profile: MihomoProfileState?,
    runMode: Int,
    exposePorts: Boolean,
    tunApplicationUids: List<Int>,
    useRootProviderPaths: Boolean,
    controllerSocketPath: String?,
    scriptEngine: MihomoProfileScriptEngine = DefaultMihomoProfileScriptEngine,
): MihomoProfileCompilation {
    val ruled = applyMihomoRuleOverride(profile.effectiveRuleOverride())
    val scripted = ruled.config.applyMihomoProfileScriptOverride(profile, appState.mihomoOverrideScripts, scriptEngine)
    return MihomoProfileCompilation(
        scriptInput = ruled.config,
        ruleDiagnostics = ruled.diagnostics,
        finalYaml = scripted.toAsteriskRuntimeProfileYaml(
            appState, runMode, exposePorts = exposePorts, tunApplicationUids = tunApplicationUids,
            useRootProviderPaths = useRootProviderPaths, controllerSocketPath = controllerSocketPath,
        ),
    )
}

/** The rule override a compile applies; raw configuration mode keeps the stored one unused. */
internal fun MihomoProfileState?.effectiveRuleOverride(): MihomoRuleOverrideState =
    this?.takeUnless(MihomoProfileState::disableOverrides)?.ruleOverride ?: MihomoRuleOverrideState()

/** A parsed YAML document whose non-BMP characters are still escaped; see [toMihomoProfileObject]. */
internal class ParsedMihomoYaml(
    private val escaped: EscapedYamlContent,
    private val root: Any?,
) {
    /**
     * The configuration object with real Unicode names, or `null` when the document is not a
     * mapping. Rule overrides and scripts work on this object, so a rule that names a policy
     * group with a flag emoji matches what the user typed and nothing downstream sees the
     * escape tokens.
     */
    fun toMihomoProfileObject(): Map<String, Any?>? {
        val mapping = root as? Map<*, *> ?: return null
        val profile = linkedMapOf<String, Any?>()
        mapping.forEach { (key, value) ->
            val name = key as? String ?: return@forEach
            // Normalizing first rejects self-referencing anchors before the restore recurses.
            profile[escaped.restore(name)] = escaped.restoreParsedValue(normalizeYamlValue(value))
        }
        return profile
    }
}

/** Parses a YAML document; throws on a syntax error. */
internal fun String.parseMihomoYaml(): ParsedMihomoYaml {
    val escaped = escapeSupplementaryYamlCodePoints()
    return ParsedMihomoYaml(escaped, Load(MihomoYamlLoadSettings).loadFromString(escaped.value))
}

/** [parseMihomoYaml] as a configuration object, or `null` when the document is not a mapping. */
internal fun String.loadMihomoProfileObject(): Map<String, Any?>? = parseMihomoYaml().toMihomoProfileObject()

internal fun Map<String, Any?>.toAsteriskRuntimeProfileYaml(
    appState: AppState,
    runMode: Int,
    forceDns: Boolean = false,
    exposePorts: Boolean = true,
    tunApplicationUids: List<Int>,
    useRootProviderPaths: Boolean = runMode.isRootRunMode(),
    controllerSocketPath: String? = null,
): String = dumpMihomoProfileYaml(
    toAsteriskRuntimeProfileObject(
        appState, runMode, forceDns, exposePorts, tunApplicationUids, useRootProviderPaths, controllerSocketPath,
    ),
)

/** The settings AsteriskMETA owns, applied over a script's output; [toAsteriskRuntimeProfileYaml] dumps it. */
internal fun Map<String, Any?>.toAsteriskRuntimeProfileObject(
    appState: AppState,
    runMode: Int,
    forceDns: Boolean = false,
    exposePorts: Boolean = true,
    tunApplicationUids: List<Int>,
    useRootProviderPaths: Boolean = runMode.isRootRunMode(),
    controllerSocketPath: String? = null,
): Map<String, Any?> {
    val updated = linkedMapOf<String, Any?>()
    val managedKeys = AsteriskManagedTopLevelKeys
    forEach { (name, value) ->
        if (name !in managedKeys) {
            updated[name] = normalizeYamlValue(value)
        }
    }
    if (useRootProviderPaths) {
        updated.putCmfaRootProviderPaths()
    }
    updated.putAsteriskRuntimeOverrides(appState, runMode, forceDns = forceDns, exposePorts = exposePorts, tunApplicationUids = tunApplicationUids, controllerSocketPath = controllerSocketPath)
    @Suppress("UNCHECKED_CAST")
    return normalizeYamlValue(updated) as Map<String, Any?>
}

/** Dumps a compiled configuration exactly as the core receives it. */
internal fun dumpMihomoProfileYaml(profile: Map<String, Any?>): String = dumpYaml(normalizeYamlValue(profile))

private fun MutableMap<String, Any?>.putAsteriskRuntimeOverrides(
    appState: AppState,
    runMode: Int,
    forceDns: Boolean = false,
    exposePorts: Boolean = true,
    tunApplicationUids: List<Int>,
    controllerSocketPath: String?,
) {
    val mixedPort = appState.localProxyPort.toPortOrNull() ?: VpnDefaults.LOCAL_PROXY_PORT
    val tproxyPort = appState.transparentProxyPort.toPortOrNull() ?: RootModeEngine.DefaultTproxyPort
    val socksPort = appState.socks5ProxyPort.toPortOrNull() ?: RootModeEngine.DefaultTun2SocksProxyPort
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
            put("listeners", listOf(appState.toMihomoTproxyListenerYamlMap(tproxyPort)))
        }
        if (runMode == RunModeTun2Socks || runMode == RunModeBpf2Socks) {
            put("socks-port", socksPort)
        }
        if (runMode == RunModeBpf2Socks) {
            put(Bpf2SocksRuntimeMarkerKey, true)
        }
        if (runMode == RunModeTun) {
            put(MihomoTunRuntimeMarkerKey, true)
            put("listeners", listOf(appState.toMihomoTunListenerYamlMap(tunApplicationUids, this["rule-providers"])))
        }
        if (runMode == RunModeVpnService) {
            put("external-controller-unix", requireNotNull(controllerSocketPath))
        }
        // The app's own client always reaches the controller through loopback.
        put("external-controller", appState.mihomoControlListenAddress(control))
        put("secret", control.secret)
        appState.toMihomoControlCorsYamlMap()?.let { cors -> put("external-controller-cors", cors) }
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
    put("unified-delay", appState.connectionTestUnifiedDelay)
    put("log-level", appState.mihomoLogLevelName())
    put("geodata-mode", appState.enableGeodataMode)
    put("geodata-loader", appState.mihomoGeodataLoaderName())
    put("ipv6", appState.enableIpv6)
    put("geox-url", appState.toMihomoGeoXUrlYamlMap())
    put("profile", normalizedProfileStoreSelected(this["profile"]))
    if (appState.overrideSniffer) {
        put("sniffer", appState.toMihomoSnifferYamlMap())
    }
    appState.mihomoFindProcessModeName()?.let { mode -> put("find-process-mode", mode) }
    if (appState.overrideMihomoExternalUi) {
        putMihomoExternalUiOverrides(appState)
    }
    // Standby cores only serve the UI and must never change a selection. The key is omitted while
    // disabled, so an uploaded official core receives exactly the configuration it would without it.
    appState.toMihomoAutoSwitchOptions(active = exposePorts).takeIf { it.enable }?.let { options ->
        put(MihomoAutoSwitchConfigKey, options.toYamlMap())
    }
    putDnsOverrides(appState, forceDns)
    putDnsHijackOverrides(appState, runMode)
    putNtpWriteToSystemOverride()
}

internal fun resolveMihomoRuntimeProfileBytes(
    sourceBytes: ByteArray,
    disableOverrides: Boolean,
    normalTransform: (String) -> String,
): ByteArray {
    if (sourceBytes.isEmpty() || sourceBytes.toString(Charsets.UTF_8).isBlank()) {
        error(MihomoProfileEmptyErrorMessage)
    }
    if (disableOverrides) return sourceBytes.copyOf()
    return normalTransform(sourceBytes.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
}

private fun AppState.requiresMihomoLanAccess(runMode: Int): Boolean {
    val sharedInterfaces = if (runMode == RunModeTun) tunSharedNetworkInterfaces else externalInterfaces
    val hasRootSharing = runMode.isRootRunMode() &&
        sharedInterfaces.toTrimmedNonEmptyDistinctList().isNotEmpty()
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

private fun AppState.mihomoControlListenAddress(control: MihomoControlConfig): String =
    if (mihomoControlListenAllInterfaces) "0.0.0.0:${control.port}" else control.address

/** `null` keeps Mihomo's default, which allows every origin and private network access. */
private fun AppState.toMihomoControlCorsYamlMap(): Map<String, Any?>? {
    val origins = mihomoControlCorsAllowOrigins.toTrimmedNonEmptyDistinctList()
    if (origins.isEmpty() && mihomoControlCorsAllowPrivateNetwork) return null
    return linkedMapOf(
        "allow-origins" to origins.ifEmpty { listOf("*") },
        "allow-private-network" to mihomoControlCorsAllowPrivateNetwork,
    )
}

private fun MutableMap<String, Any?>.putMihomoExternalUiOverrides(appState: AppState) {
    listOf(
        "external-ui" to appState.mihomoExternalUi,
        "external-ui-url" to appState.mihomoExternalUiUrl,
        "external-ui-name" to appState.mihomoExternalUiName,
    ).forEach { (key, value) ->
        val trimmed = value.trim()
        if (trimmed.isEmpty()) remove(key) else put(key, trimmed)
    }
}

private fun AppState.mihomoFindProcessModeName(): String? =
    MihomoFindProcessModeValues.getOrNull(mihomoFindProcessMode)?.takeIf(String::isNotEmpty)

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
        "mmdb" to if (BundledCore.usesRealMmdb) source.countryMmdbUrl else source.mmdbUrl,
        "asn" to source.asnUrl,
    ).apply {
        if (BundledCore.usesRealMmdb) remove("geoip")
    }
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

private fun MutableMap<String, Any?>.putDnsHijackOverrides(
    appState: AppState,
    runMode: Int,
) {
    val requiresDnsHijackRule = runMode.isRootRunMode() ||
        (runMode == RunModeVpnService && appState.enableVpnHevTun)
    if (!requiresDnsHijackRule || !appState.effectiveLocalDnsEnabled) {
        return
    }
    put("proxies", normalizedProxiesWithDnsOut(this["proxies"]))
    put("rules", managedMihomoDnsRules((this["rules"] as? List<*>)?.map(::normalizeYamlValue).orEmpty(), runMode))
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

internal fun AppState.toMihomoTunListenerYamlMap(
    applicationUids: List<Int>,
    ruleProviders: Any?,
): Map<String, Any?> {
    val tunOptions = toTunOptions()
    return linkedMapOf<String, Any?>(
        "name" to MihomoTunInboundName,
        "type" to "tun",
        "device" to MihomoTunDevice,
        "stack" to MihomoProfileFactory.tunStack(this),
        "auto-route" to true,
        "auto-detect-interface" to true,
        "auto-redirect" to true,
        "mtu" to tunOptions.mtu,
        "inet4-address" to listOf("${tunOptions.ipv4Address.address}/${tunOptions.ipv4Address.prefixLength}"),
    ).apply {
        putAll(mihomoRootTunPolicy(
            withCompatibleProxyAppListMode().proxyAppListMode,
            applicationUids, tunSharedNetworkInterfaces,
            tunBypassRuleSetTags, ruleProviders,
        ))
        effectiveMihomoTunCongestionController.takeIf(String::isNotEmpty)?.let {
            put("congestion-controller", it)
        }
        if (effectiveLocalDnsEnabled) {
            put(
                "dns-hijack",
                if (enableIpv6) listOf("0.0.0.0:53", "[::]:53")
                else listOf("0.0.0.0:53"),
            )
        }
        if (enableIpv6) {
            put("inet6-address", listOf("${tunOptions.ipv6Address.address}/${tunOptions.ipv6Address.prefixLength}"))
        }
    }
}

private fun AppState.toMihomoTproxyListenerYamlMap(port: Int): Map<String, Any?> {
    return linkedMapOf(
        "name" to MihomoTproxyInboundName,
        "type" to "tproxy",
        "listen" to if (rootIpv6DataPathEnabled) "::" else "0.0.0.0",
        "port" to port,
        "udp" to true,
    )
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
    if (!enabled || any { it.equals(SystemDnsServer, ignoreCase = true) || it.equals("system", ignoreCase = true) }) {
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
    "unified-delay",
    "log-level",
    "geodata-mode",
    "geodata-loader",
    "ipv6",
    MihomoTunRuntimeMarkerKey,
    Bpf2SocksRuntimeMarkerKey,
    "geox-url",
    MihomoAutoSwitchConfigKey,
)

private const val NameserverPolicySeparator = "=>"
private const val SystemDnsServer = "system://"
internal const val MihomoProfileMissingErrorMessage = "No Mihomo configuration selected"
internal const val MihomoProfileEmptyErrorMessage = "Selected Mihomo configuration has no YAML content"
internal const val MihomoProfileObjectRequiredErrorMessage = "Configuration is not a YAML object"

private val LocalProxySkipAuthPrefixes = listOf("127.0.0.1/8", "::1/128")
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
