// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.dns

import engine.network.DnsEvent

internal enum class DnsRoute {
    Direct,
    Proxy,
    Unknown,
}

internal data class DnsRecord(
    val id: Long,
    val event: DnsEvent,
    val app: String,
    val appPackage: String,
) {
    val timestampMillis: Long get() = event.time
    val domain: String get() = event.qname
    val recordType: String get() = dnsTypeLabel(event.qtype)
    val upstream: String get() = event.upstream.orEmpty()
    val elapsedMillis: Long get() = event.delay
    val cached: Boolean get() = event.answerSource == "cache"
    val failed: Boolean get() = event.status != "answered" || (event.rcode ?: 0) != 0
    val route: DnsRoute
        get() = when (event.route) {
            "direct" -> DnsRoute.Direct
            "proxy" -> DnsRoute.Proxy
            else -> DnsRoute.Unknown
        }
}

internal fun dnsTypeLabel(type: Int): String = when (type) {
    1 -> "A"
    2 -> "NS"
    5 -> "CNAME"
    6 -> "SOA"
    12 -> "PTR"
    15 -> "MX"
    16 -> "TXT"
    28 -> "AAAA"
    33 -> "SRV"
    35 -> "NAPTR"
    43 -> "DS"
    46 -> "RRSIG"
    48 -> "DNSKEY"
    64 -> "SVCB"
    65 -> "HTTPS"
    255 -> "ANY"
    257 -> "CAA"
    else -> "TYPE$type"
}

internal fun dnsRcodeLabel(rcode: Int): String = when (rcode) {
    0 -> "NOERROR"
    1 -> "FORMERR"
    2 -> "SERVFAIL"
    3 -> "NXDOMAIN"
    4 -> "NOTIMP"
    5 -> "REFUSED"
    else -> rcode.toString()
}
