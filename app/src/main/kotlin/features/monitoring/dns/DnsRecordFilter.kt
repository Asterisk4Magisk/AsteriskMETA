// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.dns

import engine.mihomo.runtime.MihomoDnsRecord
import engine.mihomo.runtime.MihomoDnsRoute

/**
 * The filters the DNS monitor offers.
 *
 * The menu groups them into the source of the lookup and the type the core asked for,
 * but one tap picks one filter and replaces the previous pick, so the list is never
 * narrowed by two filters at once.
 */
internal enum class DnsRecordFilter {
    /** The core reached the upstream directly. */
    Direct,

    /** The core reached the upstream through the proxy. */
    Proxy,

    /** An address lookup for IPv4. */
    A,

    /** An address lookup for IPv6. */
    AAAA,

    /** A service binding lookup, which the core reports as `HTTPS` or `SVCB`. */
    Https,
}

/** The filters that say how the core reached the upstream. */
internal val DnsSourceFilter: List<DnsRecordFilter> = listOf(
    DnsRecordFilter.Direct,
    DnsRecordFilter.Proxy,
)

/** The filters that say what the core asked for. */
internal val DnsTypeFilter: List<DnsRecordFilter> = listOf(
    DnsRecordFilter.A,
    DnsRecordFilter.AAAA,
    DnsRecordFilter.Https,
)

/** Whether the record passes the picked filter, with no filter meaning every record. */
internal fun MihomoDnsRecord.matchesDnsFilter(filter: DnsRecordFilter?): Boolean = when (filter) {
    null -> true
    DnsRecordFilter.Direct -> route == MihomoDnsRoute.Direct
    DnsRecordFilter.Proxy -> route == MihomoDnsRoute.Proxy
    DnsRecordFilter.A -> recordType.equals("A", ignoreCase = true)
    DnsRecordFilter.AAAA -> recordType.equals("AAAA", ignoreCase = true)
    // The core reports the service binding type under either name.
    DnsRecordFilter.Https -> recordType.equals("HTTPS", ignoreCase = true) ||
        recordType.equals("SVCB", ignoreCase = true)
}

/** Whether the record matches the search term. */
internal fun MihomoDnsRecord.matchesDnsQuery(query: String): Boolean {
    val term = query.trim()
    if (term.isEmpty()) return true
    return domain.contains(term, ignoreCase = true) ||
        recordType.contains(term, ignoreCase = true) ||
        upstream.contains(term, ignoreCase = true) ||
        addresses.any { it.contains(term, ignoreCase = true) }
}

/** Filters the records by the picked filter and the search term. */
internal fun filterDnsRecords(
    records: List<MihomoDnsRecord>,
    filter: DnsRecordFilter?,
    query: String,
): List<MihomoDnsRecord> = records.filter { record ->
    record.matchesDnsFilter(filter) && record.matchesDnsQuery(query)
}
