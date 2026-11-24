// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

/**
 * One DNS exchange observed by the core.
 *
 * The core reports every lookup through its debug log stream, so the DNS monitor
 * reconstructs the records from those lines instead of polling a query history the
 * external controller does not expose.
 */
internal data class MihomoDnsRecord(
    /** The unique id the collector assigned to the record. */
    val id: Long = -1L,
    /** Epoch milliseconds when the core answered the lookup. */
    val timestampMillis: Long,
    /** The queried name, for example `cl-199.911-gt2-rs.com`. */
    val domain: String,
    /** The record type the core queried, for example `A`, `AAAA` or `HTTPS`. */
    val recordType: String,
    /** The resolved addresses, empty when the lookup failed or returned no address. */
    val addresses: List<String> = emptyList(),
    /** The upstream server the core asked, for example `https://dns.google/dns-query`. */
    val upstream: String = "",
    /** The route the core took to reach the upstream. */
    val route: MihomoDnsRoute = MihomoDnsRoute.Unknown,
    /** How long the core waited for the upstream answer. */
    val elapsedMillis: Long? = null,
    /** Whether the core answered the lookup from its own DNS cache. */
    val cached: Boolean = false,
    /** Whether the lookup was rejected, for example with `NXDOMAIN`. */
    val failed: Boolean = false,
    /** The app the query was made for, empty when the app is not known yet. */
    val app: String = "",
    /** The package of that app, empty when the app is not known yet. */
    val appPackage: String = "",
)

/** How the core reached the upstream server. */
internal enum class MihomoDnsRoute {
    /** The upstream was reached through the proxy. */
    Proxy,

    /** The upstream was reached directly. */
    Direct,

    /** The upstream does not belong to a known group. */
    Unknown,
}

/**
 * Parses the `[DNS]` lines the core writes to its debug log stream.
 *
 * The core emits one line per upstream attempt and one line per answer:
 * `[DNS] resolve example.com A from https://dns.google/dns-query` and
 * `[DNS] example.com --> [1.2.3.4] A from https://dns.google/dns-query`.
 *
 * The parser keeps the pending lookups, so a request and the answer that follows it
 * can arrive in different lines.
 */
internal class MihomoDnsLogParser(
    /** The package of this app, whose own lookups read as the app itself. */
    private val selfPackage: String? = null,
) {
    private val pending = LinkedHashMap<String, PendingLookup>()

    /** The app each host belongs to, learned from the core's process notices. */
    private val appByHost = LinkedHashMap<String, String>()

    /** The package behind each host, which loads the app's icon. */
    private val appPackageByHost = LinkedHashMap<String, String>()

    /** Whether a process notice arrived since the records were enriched last. */
    var hasUnreadApps = false
        private set

    /**
     * Extracts the DNS records from one core log line.
     *
     * The lines that carry no record, such as the hijack notices, produce no output.
     */
    fun parse(line: String, nowMillis: Long = System.currentTimeMillis()): List<MihomoDnsRecord> {
        val trimmed = line.trim()
        if (trimmed.startsWith(PkgLogPrefix)) {
            parsePkg(trimmed)
            return emptyList()
        }
        return when (val parsed = parseLine(line, nowMillis)) {
            is ParsedLine.Resolve -> {
                pending[parsed.key] = parsed.lookup
                emptyList()
            }
            is ParsedLine.Answer -> {
                val lookup = pending.remove(parsed.key)
                listOf(
                    parsed.answer.copy(
                        domain = lookup?.domain ?: parsed.answer.domain,
                        elapsedMillis = lookup?.let { parsed.answer.timestampMillis - it.timestampMillis },
                    ),
                )
            }
            null -> emptyList()
        }
    }

    /**
     * Fills in the app of the records the process notices have since revealed.
     *
     * A connection usually starts after the lookup it needed, so the notices name the
     * app a moment later than the record.
     */
    fun enrichApps(records: List<MihomoDnsRecord>): List<MihomoDnsRecord> {
        if (!hasUnreadApps) return records
        val enriched = records.map { record ->
            when {
                record.app.isEmpty() -> record.copy(app = appFor(record), appPackage = appPackageFor(record))
                record.appPackage.isEmpty() -> record.copy(appPackage = appPackageFor(record))
                else -> record
            }
        }
        hasUnreadApps = false
        return enriched
    }

    /** The app the lookup was made for, from the domain or the resolved addresses. */
    internal fun appFor(record: MihomoDnsRecord): String {
        appByHost[record.domain]?.let { name -> return name }
        record.addresses.firstNotNullOfOrNull { address -> appByHost[address] }?.let { name -> return name }
        return ""
    }

    /** The package of the app the lookup was made for, which loads the icon. */
    internal fun appPackageFor(record: MihomoDnsRecord): String {
        appPackageByHost[record.domain]?.let { name -> return name }
        record.addresses.firstNotNullOfOrNull { address -> appPackageByHost[address] }?.let { name -> return name }
        return ""
    }

    /**
     * Remembers the app a connection belongs to.
     *
     * The core names the connection destination, which is the domain the lookup asked
     * for or one of the addresses it answered with.
     */
    private fun parsePkg(line: String) {
        val body = line.removePrefix(PkgLogPrefix).trim()
        val arrowIndex = body.indexOf(" --> ")
        if (arrowIndex <= 0) return
        val rest = body.substring(arrowIndex + 5).trim()
        val byIndex = rest.lastIndexOf(" by ")
        if (byIndex <= 0) return
        val host = hostWithoutPort(rest.substring(0, byIndex).trim())
        val app = rest.substring(byIndex + 4).trim()
            .substringAfter('[', missingDelimiterValue = "")
            .substringBefore(']')
            .trim()
        if (host.isEmpty() || app.isEmpty()) return
        val name = if (app == selfPackage) SelfAppName else app
        if (appByHost.put(host, name) == null) hasUnreadApps = true
        appPackageByHost[host] = app
    }

    private fun hostWithoutPort(host: String): String = when {
        host.startsWith("[") -> host.substringBefore(']')
        else -> host.substringBeforeLast(':', missingDelimiterValue = host)
    }

    private fun parseLine(line: String, nowMillis: Long): ParsedLine? {
        val trimmed = line.trim()
        if (!trimmed.startsWith(DnsLogPrefix)) return null
        val body = trimmed.removePrefix(DnsLogPrefix).trim()
        if (body.isEmpty()) return null

        // A cache hit never reaches an upstream, so the core writes it without the
        // "from" suffix: "[DNS] cache hit example.com --> [1.2.3.4] A, expire at ...".
        if (body.startsWith(CacheHitMarker)) {
            return parseCacheHit(body.removePrefix(CacheHitMarker).trim(), nowMillis)
        }

        val fromIndex = body.lastIndexOf(FromMarker)
        if (fromIndex <= 0) return null
        val head = body.substring(0, fromIndex).trim()
        val upstream = body.substring(fromIndex + FromMarker.length).trim()
        if (upstream.isEmpty()) return null

        // The core writes "domain --> [answer] type" for answers and
        // "resolve domain type" for the start of a lookup.
        val arrowIndex = head.indexOf("-->")
        if (arrowIndex > 0) {
            val domain = head.substring(0, arrowIndex).trim()
            val answer = head.substring(arrowIndex + 3).trim()
            if (domain.isEmpty() || answer.isEmpty()) return null
            val (addresses, recordType) = splitAnswer(answer)
            return ParsedLine.Answer(
                key = lookupKey(domain, recordType),
                answer = MihomoDnsRecord(
                    timestampMillis = nowMillis,
                    domain = domain,
                    recordType = recordType,
                    addresses = addresses,
                    upstream = upstream,
                    failed = isFailure(recordType, addresses),
                ),
            )
        }

        if (!head.startsWith("resolve ")) return null
        val resolveBody = head.removePrefix("resolve ").trim()
        val separator = resolveBody.lastIndexOf(' ')
        if (separator <= 0) return null
        val domain = resolveBody.substring(0, separator).trim()
        val recordType = resolveBody.substring(separator + 1).trim()
        if (domain.isEmpty() || recordType.isEmpty()) return null
        return ParsedLine.Resolve(
            key = lookupKey(domain, recordType),
            lookup = PendingLookup(domain = domain, timestampMillis = nowMillis),
        )
    }

    private fun parseCacheHit(body: String, nowMillis: Long): ParsedLine? {
        val arrowIndex = body.indexOf("-->")
        if (arrowIndex <= 0) return null
        val domain = body.substring(0, arrowIndex).trim()
        val answer = body.substring(arrowIndex + 3).trim()
        if (domain.isEmpty() || answer.isEmpty()) return null
        // The core appends the cache expiry to the answer, which is not part of the record.
        val expiryIndex = answer.indexOf(", expire at ")
        val answerBody = if (expiryIndex >= 0) answer.substring(0, expiryIndex).trim() else answer
        val (addresses, recordType) = splitAnswer(answerBody)
        return ParsedLine.Answer(
            key = lookupKey(domain, recordType),
            answer = MihomoDnsRecord(
                timestampMillis = nowMillis,
                domain = domain,
                recordType = recordType,
                addresses = addresses,
                cached = true,
                failed = isFailure(recordType, addresses),
            ),
        )
    }

    /**
     * Splits an answer body into its addresses and record type.
     *
     * The core writes `[1.2.3.4]` for address records and a bracketed summary for
     * `HTTPS` records, which keeps the type in front of the summary.
     */
    private fun splitAnswer(answer: String): Pair<List<String>, String> {
        val closing = answer.lastIndexOf(']')
        if (!answer.startsWith('[') || closing < 0) {
            return emptyList<String>() to answer
        }
        val body = answer.substring(1, closing).trim()
        val recordType = answer.substring(closing + 1).trim()
        // The summary of a service binding record holds the parameters of the record
        // rather than addresses, so it stays in one piece.
        if (isServiceBindingType(recordType)) {
            return listOf(body).filter { it.isNotEmpty() } to recordType
        }
        val addresses = body
            .split(' ', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return addresses to recordType.ifEmpty { "A" }
    }

    private fun isServiceBindingType(recordType: String): Boolean =
        recordType.equals("HTTPS", ignoreCase = true) ||
            recordType.equals("SVCB", ignoreCase = true)

    private fun isFailure(recordType: String, addresses: List<String>): Boolean {
        if (addresses.isNotEmpty()) return false
        return recordType.contains("NXDOMAIN", ignoreCase = true) ||
            recordType.contains("REFUSED", ignoreCase = true) ||
            recordType.contains("FAIL", ignoreCase = true)
    }

    private fun lookupKey(domain: String, recordType: String): String = "$domain|${recordType.uppercase()}"

    private sealed interface ParsedLine {
        data class Resolve(val key: String, val lookup: PendingLookup) : ParsedLine

        data class Answer(val key: String, val answer: MihomoDnsRecord) : ParsedLine
    }

    private data class PendingLookup(
        val domain: String,
        val timestampMillis: Long,
    )

    private companion object {
        const val DnsLogPrefix = "[DNS] "

        /** The core prefixes a process notice with this marker. */
        const val PkgLogPrefix = "[PKG] "

        /** The name the app itself reads as. */
        const val SelfAppName = "AsteriskMETA"

        /** The upstream part of a line, such as `https://dns.google/dns-query`. */
        const val FromMarker = " from "

        /** The core prefixes a cache hit with this marker. */
        const val CacheHitMarker = "cache hit "
    }
}
