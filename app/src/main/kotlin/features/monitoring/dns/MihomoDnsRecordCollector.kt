// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.dns

import android.content.Context
import engine.mihomo.MihomoControlConfig
import engine.mihomo.runtime.MihomoControlClient
import engine.mihomo.runtime.MihomoDnsLogParser
import engine.mihomo.runtime.MihomoDnsRecord
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Collects the DNS lookups the core performed.
 *
 * The core reports every lookup on its debug log stream, so the collector subscribes to
 * that stream and turns the `[DNS]` lines into records. The subscription is independent
 * of the configured core log level because the core pushes debug events to every
 * subscriber before it decides whether to print them.
 */
internal class MihomoDnsRecordCollector(
    private val context: Context,
    private val controlClient: MihomoControlClient = MihomoControlClient(),
) {
    private val mutableRecords = MutableStateFlow<List<MihomoDnsRecord>>(emptyList())
    val records: StateFlow<List<MihomoDnsRecord>> = mutableRecords.asStateFlow()

    /** The source of the unique ids the record list keys its rows by. */
    private val idCounter = AtomicLong(0)

    /**
     * Streams the core log until the collector is cancelled.
     *
     * The core restarts on every profile change, so the stream reconnects instead of
     * ending the collection.
     */
    suspend fun collect(control: MihomoControlConfig, routeResolver: MihomoDnsRouteResolver) {
        while (currentCoroutineContext().isActive) {
            try {
                // The parser keeps the pending lookups, so a request and its answer can
                // arrive in different log lines.
                val parser = MihomoDnsLogParser(selfPackage = context.packageName)
                parserOfSession = parser
                controlClient.streamLogs(control, DnsLogLevel).collect { record ->
                    val parsed = parser.parse(record.message)
                    if (parsed.isNotEmpty()) {
                        publish(parsed, routeResolver)
                    } else if (parser.hasUnreadApps) {
                        // The core names the app of a connection a moment after the lookup
                        // it needed, so the records read their app as the notices arrive.
                        val enriched = parser.enrichApps(mutableRecords.value)
                        if (enriched != mutableRecords.value) mutableRecords.value = enriched
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                currentCoroutineContext().ensureActive()
                // Every failed attempt logs, because a silent reconnect loop would leave
                // the monitor empty with nothing to explain it.
                AndroidAppLogger.warn(LogTag, "Failed to collect DNS records", error)
            }
            delay(StreamReconnectDelayMillis)
        }
    }

    /** Clears the collected records, so every session starts from an empty list. */
    fun clear() {
        mutableRecords.value = emptyList()
    }

    private fun publish(
        records: List<MihomoDnsRecord>,
        routeResolver: MihomoDnsRouteResolver,
    ) {
        val prepared = records.map { record ->
            // A cache hit never names an upstream, so it reads the server that answered
            // the same name most recently, which is where its cached entry came from.
            val upstream = record.upstream.ifEmpty {
                if (record.cached) lastUpstreamByDomain[record.domain].orEmpty() else ""
            }
            if (!record.cached && record.upstream.isNotBlank()) {
                lastUpstreamByDomain[record.domain] = record.upstream
            }
            record.copy(
                id = idCounter.getAndIncrement(),
                upstream = upstream,
                route = routeResolver.resolve(upstream),
                app = record.app.ifEmpty { parserOfSession?.appFor(record).orEmpty() },
                appPackage = record.appPackage.ifEmpty { parserOfSession?.appPackageFor(record).orEmpty() },
            )
        }
        // The core answers the same lookup through several upstreams at once, so the
        // records from one burst can share a timestamp, a name and a type. The list
        // keys its rows by the id, so every record gets one before it is shown.
        val merged = (mutableRecords.value + prepared).takeLast(MaxRecords)
        mutableRecords.value = merged
    }

    /** The upstream that answered each name most recently, which the cache hits read. */
    private val lastUpstreamByDomain = LinkedHashMap<String, String>()

    /** The parser of the running session, which holds the app notices. */
    private var parserOfSession: MihomoDnsLogParser? = null

    private companion object {
        const val LogTag = "MihomoDnsRecordCollector"
        const val DnsLogLevel = "debug"
        const val StreamReconnectDelayMillis = 2_000L
        const val MaxRecords = 500
    }
}
