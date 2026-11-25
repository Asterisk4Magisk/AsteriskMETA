// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.dns

import android.content.Context
import engine.network.DnsEventUpdate
import engine.network.DnsEventsUnavailable
import features.logs.AndroidAppLogger
import features.monitoring.ConnectionMonitorStatus
import features.monitoring.MonitoringAppLookup
import features.monitoring.MonitoringDnsSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DnsMonitorTarget(
    val runMode: Int,
    val runtimeEpoch: Long?,
    val connected: Boolean,
)

internal class DnsMonitorCollector(context: Context) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val applicationContext = context.applicationContext
    private val mutableState = MutableStateFlow(MonitoringDnsSummary())
    val state = mutableState.asStateFlow()
    private var session: Pair<Int, Long?>? = null
    private var nextId = 0L

    suspend fun observe(
        target: Flow<DnsMonitorTarget?>,
        events: () -> Flow<DnsEventUpdate>,
    ) = withContext(dispatcher) {
        target.collectLatest targetChanged@{ current ->
            val nextSession = current?.let { target ->
                val epoch = target.runtimeEpoch ?: session?.takeIf { it.first == target.runMode }?.second
                target.runMode to epoch
            }
            if (session != nextSession) {
                session = nextSession
                mutableState.value = MonitoringDnsSummary(
                    status = if (current == null) {
                        ConnectionMonitorStatus.ServiceStopped
                    } else {
                        ConnectionMonitorStatus.Loading
                    },
                )
            }
            if (current == null || !current.connected) return@targetChanged

            val appLookup = MonitoringAppLookup(applicationContext, cacheLimit = MaxRecords)
            val records = ArrayDeque<DnsRecord>(MaxRecords).apply {
                addAll(mutableState.value.records)
            }
            while (true) {
                var unpublished = false
                try {
                    coroutineScope {
                        var pendingPublish: Job? = null
                        events().collect { update ->
                            when (update) {
                                DnsEventUpdate.Ready -> mutableState.update {
                                    it.copy(status = ConnectionMonitorStatus.Available, unsupported = false)
                                }

                                is DnsEventUpdate.Received -> {
                                    val event = update.event
                                    val packageName = event.uid?.let { uid ->
                                        appLookup.packagesForUid(uid).singleOrNull()
                                    }
                                    val app = packageName?.let(appLookup::labelForPackage) ?: packageName.orEmpty()
                                    if (records.size == MaxRecords) records.removeFirst()
                                    records.addLast(DnsRecord(nextId++, event, app, packageName.orEmpty()))
                                    unpublished = true
                                    if (pendingPublish == null) {
                                        pendingPublish = launch {
                                            delay(PublishIntervalMillis)
                                            mutableState.value = MonitoringDnsSummary(
                                                status = ConnectionMonitorStatus.Available,
                                                records = records.toList(),
                                            )
                                            unpublished = false
                                            pendingPublish = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: DnsEventsUnavailable) {
                    mutableState.update {
                        it.copy(status = ConnectionMonitorStatus.Error, unsupported = true)
                    }
                    return@targetChanged
                } catch (error: Exception) {
                    if (mutableState.value.status != ConnectionMonitorStatus.Error) {
                        AndroidAppLogger.warn(LogTag, "DNS event stream failed (${error.javaClass.simpleName})")
                    }
                } finally {
                    if (unpublished) mutableState.update { it.copy(records = records.toList()) }
                }
                mutableState.update {
                    it.copy(
                        status = ConnectionMonitorStatus.Error,
                        unsupported = false,
                    )
                }
                delay(ReconnectDelayMillis)
            }
        }
    }

    private companion object {
        const val LogTag = "DnsMonitorCollector"
        const val MaxRecords = 500
        const val PublishIntervalMillis = 250L
        const val ReconnectDelayMillis = 1_000L
    }
}
