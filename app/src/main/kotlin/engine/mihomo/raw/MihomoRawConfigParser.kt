// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.raw

import engine.mihomo.MihomoControlConfig
import engine.mihomo.MihomoYamlLoadSettings
import engine.mihomo.sha256Hex
import engine.network.isCidrAddress
import engine.network.toPortOrNull
import org.snakeyaml.engine.v2.api.Load

internal object MihomoRawConfigParser {
    fun parse(sourceBytes: ByteArray): MihomoRawConfigParseResult {
        if (sourceBytes.isEmpty()) {
            return MihomoRawConfigParseResult(sourceBytes, error = "Configuration is empty")
        }
        val text = sourceBytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val loaded = runCatching { Load(MihomoYamlLoadSettings).loadFromString(text) }
            .getOrElse { error ->
                return MihomoRawConfigParseResult(
                    sourceBytes,
                    error = "Invalid YAML: ${error.message.orEmpty()}",
                )
            }
        val root = loaded as? Map<*, *>
            ?: return MihomoRawConfigParseResult(sourceBytes, error = "YAML root must be an object")
        return MihomoRawConfigParseResult(
            sourceBytes = sourceBytes,
            snapshot = parseSnapshot(root, sourceBytes.sha256Hex()),
        )
    }
}

private fun parseSnapshot(root: Map<*, *>, sourceSha256: String): MihomoRawConfigSnapshot {
    val mode = root.stringField("mode")
    val logLevel = root.stringField("log-level")
    val ipv6 = root.booleanField("ipv6")
    val geodataMode = root.booleanField("geodata-mode")
    val geodataLoader = root.stringField("geodata-loader")
    val sniffer = root.mapValue("sniffer")
    val dns = root.mapValue("dns")
    return MihomoRawConfigSnapshot(
        sourceSha256 = sourceSha256,
        mode = mode,
        logLevel = logLevel,
        ipv6 = ipv6,
        geodataMode = geodataMode,
        geodataLoader = geodataLoader,
        snifferEnabled = RawConfigField(
            value = sniffer?.get("enable") as? Boolean,
            path = "sniffer.enable",
            problem = if (sniffer == null || sniffer["enable"] is Boolean) null else "Expected a boolean",
        ),
        dnsEnabled = RawConfigField(
            value = dns?.get("enable") as? Boolean,
            path = "dns.enable",
            problem = if (dns == null || dns["enable"] is Boolean) null else "Expected a boolean",
        ),
        api = parseApi(root),
        tproxyPort = root.portField("tproxy-port"),
        socksInbound = parseSocksInbound(root),
        tunInbound = parseTunInbound(root),
        dnsHijack = parseDnsHijack(root),
    )
}

private fun parseApi(root: Map<*, *>): RawConfigField<MihomoRawApiConfig> {
    val candidates = buildList {
        root["external-controller"]?.toString()?.takeIf(String::isNotBlank)?.let { add(Triple(it, "external-controller", "http")) }
        root["external-controller-tls"]?.toString()?.takeIf(String::isNotBlank)?.let {
            add(Triple(it, "external-controller-tls", "https"))
        }
    }
    if (candidates.isEmpty()) return RawConfigField(path = "external-controller")
    if (candidates.size > 1) {
        return RawConfigField(path = "external-controller", problem = "Conflicting controller endpoints")
    }
    val (raw, path, defaultScheme) = candidates.single()
    val parsed = parseController(raw, defaultScheme)
        ?: return RawConfigField(path = path, problem = "Unsupported or invalid TCP controller endpoint")
    return RawConfigField(
        value = MihomoRawApiConfig(
            control = parsed.copy(secret = root["secret"]?.toString().orEmpty()),
            hasSecret = !root["secret"]?.toString().isNullOrEmpty(),
        ),
        path = path,
    )
}

private fun parseController(rawValue: String, defaultScheme: String): MihomoControlConfig? {
    val raw = rawValue.trim()
    if (raw.startsWith("unix:", true) || raw.startsWith("pipe:", true)) return null
    val explicitScheme = raw.substringBefore("://", missingDelimiterValue = "")
    val scheme = explicitScheme.takeIf(String::isNotEmpty)?.lowercase() ?: defaultScheme
    if (scheme !in setOf("http", "https")) return null
    val authority = if (explicitScheme.isEmpty()) raw else raw.substringAfter("://")
    if ('/' in authority || '?' in authority || '#' in authority) return null
    val host: String
    val portText: String
    if (authority.startsWith("[")) {
        val close = authority.indexOf(']')
        if (close <= 1 || close + 2 > authority.length || authority.getOrNull(close + 1) != ':') return null
        host = authority.substring(1, close)
        portText = authority.substring(close + 2)
    } else {
        if (authority.count { it == ':' } != 1) return null
        host = authority.substringBefore(':')
        portText = authority.substringAfter(':')
    }
    val port = portText.toPortOrNull() ?: return null
    val connectionHost = when (host.trim()) {
        "", "0.0.0.0", "*" -> "127.0.0.1"
        "::", "[::]" -> "::1"
        else -> host.trim()
    }
    return MihomoControlConfig(host = connectionHost, port = port, scheme = scheme)
}

private fun parseSocksInbound(root: Map<*, *>): RawConfigField<MihomoRawSocksInbound> {
    val candidates = buildList {
        listOf("mixed-port", "socks-port").forEach { path ->
            root[path].portOrNull()?.let { port -> add(MihomoRawSocksInbound(port, path)) }
        }
        (root["listeners"] as? List<*>)?.forEachIndexed { index, item ->
            val listener = item as? Map<*, *> ?: return@forEachIndexed
            val type = listener["type"]?.toString()?.lowercase()
            if (type !in setOf("socks", "mixed")) return@forEachIndexed
            listener["port"].portOrNull()?.let { port ->
                add(MihomoRawSocksInbound(port, "listeners[$index].port"))
            }
        }
    }
    return when (candidates.size) {
        0 -> RawConfigField(path = "mixed-port / socks-port / listeners")
        1 -> RawConfigField(value = candidates.single(), path = candidates.single().path)
        else -> RawConfigField(
            path = "mixed-port / socks-port / listeners",
            problem = "Conflicting SOCKS-compatible inbounds",
        )
    }
}

private fun parseTunInbound(root: Map<*, *>): RawConfigField<MihomoRawTunInbound> {
    val candidates = mutableListOf<Pair<String, Map<*, *>>>()
    root.mapValue("tun")?.takeIf { it["enable"] == true }?.let { candidates += "tun" to it }
    (root["listeners"] as? List<*>)?.forEachIndexed { index, item ->
        val listener = item as? Map<*, *> ?: return@forEachIndexed
        if (listener["type"]?.toString()?.equals("tun", true) == true) {
            candidates += "listeners[$index]" to listener
        }
    }
    if (candidates.isEmpty()) return RawConfigField(path = "tun / listeners[type=tun]")
    if (candidates.size > 1) {
        return RawConfigField(path = "tun / listeners[type=tun]", problem = "Conflicting TUN inbounds")
    }
    val (path, value) = candidates.single()
    val device = value["device"]?.toString().orEmpty().trim()
    val stack = value["stack"]?.toString().orEmpty().trim().lowercase()
    val mtu = value["mtu"].intOrNull()
    val ipv4 = value.firstString("inet4-address") ?: value.firstString("inet4_address")
    val ipv6 = value.firstString("inet6-address") ?: value.firstString("inet6_address")
    val valid = device.isNotBlank() && stack in setOf("system", "gvisor", "mixed") &&
        mtu != null && mtu in 576..9000 && ipv4 != null && isCidrAddress(ipv4) && ":" !in ipv4
    if (!valid) return RawConfigField(path = path, problem = "TUN inbound is incomplete or invalid")
    return RawConfigField(
        value = MihomoRawTunInbound(device, stack, mtu, ipv4, ipv6, path),
        path = path,
    )
}

private fun parseDnsHijack(root: Map<*, *>): RawConfigField<MihomoRawDnsHijack> {
    val dnsEnabled = root.mapValue("dns")?.get("enable") == true
    val dnsOutbounds = (root["proxies"] as? List<*>)
        .orEmpty()
        .mapNotNull { it as? Map<*, *> }
        .filter { it["type"]?.toString()?.equals("dns", true) == true }
        .mapNotNull { it["name"]?.toString() }
        .toSet()
    val match = (root["rules"] as? List<*>)
        .orEmpty()
        .mapNotNull { it?.toString() }
        .firstOrNull { rule ->
            val compact = rule.replace(" ", "").uppercase()
            val target = compact.substringAfterLast(',').trim(')', '(', ' ')
            val matchesPort53 = "DST-PORT,53" in compact || "DST-PORT,53-53" in compact
            val explicitlyTcpOnly = "NETWORK,TCP" in compact && "NETWORK,UDP" !in compact
            matchesPort53 && !explicitlyTcpOnly && target in dnsOutbounds.map(String::uppercase)
        }
    val outbound = match?.substringAfterLast(',')?.trim(')', '(', ' ')
    return RawConfigField(
        value = MihomoRawDnsHijack(
            dnsEnabled = dnsEnabled,
            proven = dnsEnabled && match != null,
            matchedRule = match,
            outbound = outbound,
        ),
        path = "dns.enable + rules + proxies[type=dns]",
    )
}

private fun Map<*, *>.stringField(path: String): RawConfigField<String> {
    val raw = this[path] ?: return RawConfigField(path = path)
    return if (raw is String) RawConfigField(raw, path) else RawConfigField(path = path, problem = "Expected text")
}

private fun Map<*, *>.booleanField(path: String): RawConfigField<Boolean> {
    val raw = this[path] ?: return RawConfigField(path = path)
    return if (raw is Boolean) RawConfigField(raw, path) else RawConfigField(path = path, problem = "Expected a boolean")
}

private fun Map<*, *>.portField(path: String): RawConfigField<Int> {
    val raw = this[path] ?: return RawConfigField(path = path)
    val port = raw.portOrNull()
    return if (port != null) RawConfigField(port, path) else RawConfigField(path = path, problem = "Invalid port")
}

private fun Map<*, *>.mapValue(key: String): Map<*, *>? = this[key] as? Map<*, *>
private fun Any?.intOrNull(): Int? = when (this) {
    is Number -> toInt()
    is String -> toIntOrNull()
    else -> null
}
private fun Any?.portOrNull(): Int? = intOrNull()?.takeIf { it in 1..65535 }
private fun Map<*, *>.firstString(key: String): String? = when (val raw = this[key]) {
    is String -> raw
    is List<*> -> raw.firstOrNull()?.toString()
    else -> null
}?.trim()?.takeIf(String::isNotEmpty)
