// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.dns

internal enum class DnsRecordFilter {
    Direct,
    Proxy,
    A,
    AAAA,
    HttpsOrSvcb,
}

internal val DnsRouteFilters: List<DnsRecordFilter> = listOf(
    DnsRecordFilter.Direct,
    DnsRecordFilter.Proxy,
)

internal val DnsQueryTypeFilters: List<DnsRecordFilter> = listOf(
    DnsRecordFilter.A,
    DnsRecordFilter.AAAA,
    DnsRecordFilter.HttpsOrSvcb,
)

private fun DnsRecord.matchesDnsFilter(filter: DnsRecordFilter?): Boolean = when (filter) {
    null -> true
    DnsRecordFilter.Direct -> route == DnsRoute.Direct
    DnsRecordFilter.Proxy -> route == DnsRoute.Proxy
    DnsRecordFilter.A -> event.qtype == 1
    DnsRecordFilter.AAAA -> event.qtype == 28
    DnsRecordFilter.HttpsOrSvcb -> event.qtype == 64 || event.qtype == 65
}

private fun DnsRecord.matchesDnsQuery(term: String): Boolean {
    if (term.isEmpty()) return true
    return domain.contains(term, ignoreCase = true) ||
        recordType.contains(term, ignoreCase = true) ||
        upstream.contains(term, ignoreCase = true) ||
        app.contains(term, ignoreCase = true) ||
        appPackage.contains(term, ignoreCase = true) ||
        event.uid?.toString()?.contains(term) == true ||
        event.outboundTag?.contains(term, ignoreCase = true) == true ||
        event.answers.any { it.data.contains(term, ignoreCase = true) }
}

internal fun filterDnsRecords(
    records: List<DnsRecord>,
    filter: DnsRecordFilter?,
    query: String,
    upstream: String?,
): List<DnsRecord> {
    val term = query.trim()
    if (filter == null && term.isEmpty() && upstream == null) return records
    return records.filter { record ->
        (upstream == null || record.upstream == upstream) &&
            record.matchesDnsFilter(filter) && record.matchesDnsQuery(term)
    }
}
