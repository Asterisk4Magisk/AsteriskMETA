// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import app.AppState
import engine.network.isCidrAddress
import engine.network.toPortOrNull
import utils.toTrimmedNonEmptyDistinctList

internal fun AppState.toMihomoSnifferYamlMap(): Map<String, Any?> {
    return linkedMapOf(
        "enable" to enableSniffer,
        "force-dns-mapping" to snifferForceDnsMapping,
        "parse-pure-ip" to snifferParsePureIp,
        "override-destination" to enableSnifferOverrideDestination,
        "sniff" to linkedMapOf(
            "HTTP" to snifferProtocolYamlMap(
                ports = snifferHttpPorts.sanitizedSnifferPorts(DefaultMihomoSnifferHttpPorts),
                overrideMode = snifferHttpOverrideDestinationMode,
            ),
            "TLS" to snifferProtocolYamlMap(
                ports = snifferTlsPorts.sanitizedSnifferPorts(DefaultMihomoSnifferTlsPorts),
                overrideMode = snifferTlsOverrideDestinationMode,
            ),
            "QUIC" to snifferProtocolYamlMap(
                ports = snifferQuicPorts.sanitizedSnifferPorts(DefaultMihomoSnifferQuicPorts),
                overrideMode = snifferQuicOverrideDestinationMode,
            ),
        ),
        "force-domain" to snifferForceDomain.sanitizedSnifferDomainRules(),
        "skip-domain" to snifferSkipDomain.sanitizedSnifferDomainRules(),
        "skip-src-address" to snifferSkipSrcAddress.sanitizedSnifferCidrs(),
        "skip-dst-address" to snifferSkipDstAddress.sanitizedSnifferCidrs(),
    )
}

internal fun snifferProtocolYamlMap(
    ports: List<String>,
    overrideMode: Int,
): Map<String, Any?> {
    return linkedMapOf<String, Any?>("ports" to ports).apply {
        when (overrideMode) {
            MihomoSnifferProtocolOverrideEnabled -> put("override-destination", true)
            MihomoSnifferProtocolOverrideDisabled -> put("override-destination", false)
        }
    }
}

internal fun List<String>.sanitizedSnifferPorts(defaultPorts: List<String>): List<String> {
    return toTrimmedNonEmptyDistinctList()
        .filter(::isSnifferPortOrRange)
        .ifEmpty { defaultPorts }
}

internal fun List<String>.sanitizedSnifferDomainRules(): List<String> {
    return toTrimmedNonEmptyDistinctList().filter(::isSnifferDomainRule)
}

internal fun List<String>.sanitizedSnifferCidrs(): List<String> {
    return toTrimmedNonEmptyDistinctList().filter(::isCidrAddress)
}

internal fun isSnifferPortOrRange(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return false

    val separatorIndex = trimmed.indexOf('-')
    if (separatorIndex < 0) return trimmed.toPortOrNull() != null
    if (separatorIndex == 0 || separatorIndex == trimmed.lastIndex) return false
    if (trimmed.indexOf('-', startIndex = separatorIndex + 1) >= 0) return false

    val start = trimmed.substring(0, separatorIndex).toPortOrNull() ?: return false
    val end = trimmed.substring(separatorIndex + 1).toPortOrNull() ?: return false
    return start <= end
}

internal fun isSnifferDomainRule(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return false
    if (trimmed.startsWith("regexp:", ignoreCase = true)) {
        return trimmed.substringAfter(":").isNotBlank()
    }

    val supportedPrefix = trimmed.substringBefore(":", missingDelimiterValue = "")
        .lowercase()
        .takeIf { it in setOf("domain", "full", "keyword", "geosite", "rule-set", "ext") }
    if (supportedPrefix != null) {
        return trimmed.substringAfter(":").isNotBlank()
    }

    return !trimmed.contains("://") && !trimmed.contains("/")
}
