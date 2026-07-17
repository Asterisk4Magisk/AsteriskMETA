// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring

import android.content.Context
import app.modes.isRootRunMode
import data.AndroidAppStateStore
import engine.mihomo.runtime.MihomoConnection
import engine.mihomo.runtime.MihomoConnectionsState
import engine.mihomo.runtime.MihomoRuntimeRepository
import features.logs.AndroidAppLogger
import features.monitoring.network.AddressFamily
import features.monitoring.network.AndroidNetworkMonitor
import features.monitoring.network.PublicNetworkProbeClient
import features.monitoring.network.PublicNetworkProbeMemoryCache
import features.monitoring.network.applyPublicProbeAttempt
import features.monitoring.network.applyPublicProbeAttempts
import features.monitoring.resource.AndroidProcessStatsSource
import features.monitoring.resource.ProcessStatsSample
import features.monitoring.resource.ProcessStatsSourceKind
import features.monitoring.resource.ProcessTickSnapshot
import features.monitoring.resource.appendProcessStatsSample
import features.monitoring.resource.calculateProcessCpuPercent
import features.monitoring.traffic.TrafficLedgerSample
import features.monitoring.traffic.TrafficLedgerStore
import features.monitoring.traffic.localTrafficDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import system.AndroidRootShellGateway
import kotlin.time.Duration.Companion.milliseconds

internal class MonitoringRepository(
    private val appScope: CoroutineScope,
    context: Context,
    rootAccess: AndroidRootShellGateway,
    private val stateStore: AndroidAppStateStore,
    private val mihomoRuntime: MihomoRuntimeRepository,
) {
    private val processStatsSource = AndroidProcessStatsSource(context, rootAccess)
    private val networkMonitor = AndroidNetworkMonitor(context)
    private val publicNetworkProbeClient = PublicNetworkProbeClient()
    private val trafficLedgerStore = TrafficLedgerStore(context)
    private var trafficWasConnected = false
    private var trafficSessionId = trafficLedgerStore.snapshot().baseline?.sessionId
    private var trafficSessionSequence = 0L
    private var networkPageWasVisible = false
    private var networkPageSessionId: String? = null
    private var publicProbeGeneration = 0L
    private var publicProbeJob: Job? = null
    private val connectionRefreshMutex = Mutex()
    private val observerCounts = MutableStateFlow<Map<MonitoringIntent, Int>>(emptyMap())
    private val observerRegistry = MonitoringObserverRegistry { counts ->
        observerCounts.value = counts
    }
    private val mutableState = MutableStateFlow(
        MonitoringState(
            network = MonitoringNetworkSummary(publicProbe = PublicNetworkProbeMemoryCache.read()),
        ),
    )

    val state: StateFlow<MonitoringState> = mutableState.asStateFlow()

    init {
        appScope.launch {
            observerCounts
                .map(::resolveMonitoringPlan)
                .distinctUntilChanged()
                .collectLatest { plan ->
                    updateNetworkPageVisibility(plan.networkPageVisible)
                    if (plan.refreshLocalNetworkOnEnter) refreshLocalNetworkSnapshot()
                    coroutineScope {
                        plan.runtimeSummaryIntervalMillis?.let { interval ->
                            launch {
                                while (currentCoroutineContext().isActive) {
                                    refreshRuntimeSummary()
                                    delay(interval.milliseconds)
                                }
                            }
                        }
                        plan.resourceIntervalMillis?.let { interval ->
                            launch { collectResourceStats(interval, plan.recordResourceHistory) }
                        }
                        plan.connectionIntervalMillis?.let { interval ->
                            launch { collectConnections(interval, plan.collectConnectionDetails) }
                        }
                        plan.trafficIntervalMillis?.let { interval ->
                            launch { collectTrafficStats(interval, plan.recordTrafficHistory) }
                        }
                    }
                }
        }
    }

    fun observe(intent: MonitoringIntent, pageSessionId: String? = null): MonitoringObservation {
        if (intent == MonitoringIntent.Network && pageSessionId != null) {
            networkPageSessionId = pageSessionId
        }
        observerRegistry.acquire(intent)
        return MonitoringObservation {
            observerRegistry.release(intent)
        }
    }

    fun refreshPublicNetworkProbe(family: AddressFamily? = null) {
        if (!networkPageWasVisible) return
        startPublicNetworkProbe(family)
    }

    fun refreshNetworkStatus() {
        if (!networkPageWasVisible) return
        refreshLocalNetworkSnapshot()
        startPublicNetworkProbe()
    }

    suspend fun closeConnection(connectionId: String): Result<Unit> {
        val connectionIsVisible = mutableState.value.connections.snapshot.connections.any { connection ->
            connection.id == connectionId
        }
        if (!connectionIsVisible) {
            AndroidAppLogger.debug(LogTag, "Discarded close request for vanished connection: $connectionId")
            return Result.success(Unit)
        }
        val result = mihomoRuntime.closeConnection(stateStore.state.value, connectionId)
        if (result.isFailure) return result.map { }
        if (result.getOrNull() == false) {
            AndroidAppLogger.debug(LogTag, "Connection vanished before it could be closed: $connectionId")
        }
        refreshConnections()
        return Result.success(Unit)
    }

    suspend fun closeAllConnections(): Result<Unit> {
        val result = mihomoRuntime.closeAllConnections(stateStore.state.value)
        if (result.isSuccess) refreshConnections()
        return result
    }

    private fun refreshRuntimeSummary() {
        val runtime = mihomoRuntime.state.value
        mutableState.update { current ->
            current.copy(
                serviceRunning = runtime.running,
                network = current.network.copy(proxyConnected = runtime.running),
            )
        }
    }

    private suspend fun collectResourceStats(
        intervalMillis: Long,
        recordHistory: Boolean,
    ) {
        var previousSnapshot: ProcessTickSnapshot? = null
        var previousSource: ProcessStatsSourceKind? = null
        while (currentCoroutineContext().isActive) {
            val appState = stateStore.state.value
            if (!appState.proxyRunning) {
                previousSnapshot = null
                previousSource = null
                mutableState.update { current ->
                    current.copy(
                        resource = current.resource.copy(
                            cpuPercent = null,
                            memoryBytes = null,
                            source = null,
                            uptimeMillis = null,
                            processId = null,
                            memoryLimitBytes = null,
                        ),
                    )
                }
            } else {
                val reading = processStatsSource.read(appState)
                val baseline = previousSnapshot.takeIf { previousSource == reading?.source }
                val cpuPercent = reading?.snapshot?.let { current ->
                    calculateProcessCpuPercent(baseline, current)
                }
                previousSnapshot = reading?.snapshot
                previousSource = reading?.source
                val memoryBytes = mihomoRuntime.refreshMemoryNow(appState)
                val timestampMillis = System.currentTimeMillis()
                mutableState.update { current ->
                    val sample = ProcessStatsSample(
                        timestampMillis = timestampMillis,
                        cpuPercent = cpuPercent,
                        memoryBytes = memoryBytes,
                    )
                    val history = if (recordHistory) {
                        appendProcessStatsSample(current.resource.oneHourSamples, sample)
                    } else {
                        null
                    }
                    current.copy(
                        resource = current.resource.copy(
                            cpuPercent = cpuPercent,
                            memoryBytes = memoryBytes,
                            source = reading?.source,
                            uptimeMillis = reading?.uptimeMillis,
                            processId = reading?.snapshot?.pid,
                            memoryLimitBytes = mihomoRuntime.state.value.memory.osLimitBytes.takeIf { it > 0L },
                            sampleIntervalMillis = intervalMillis,
                            fifteenMinuteSamples = history?.fifteenMinutes.orEmpty(),
                            oneHourSamples = history?.oneHour.orEmpty(),
                        ),
                    )
                }
            }
            delay(intervalMillis.milliseconds)
        }
    }

    private suspend fun collectTrafficStats(
        intervalMillis: Long,
        recordHistory: Boolean,
    ) {
        while (currentCoroutineContext().isActive) {
            val runtime = mihomoRuntime.state.value
            val appState = stateStore.state.value
            val connected = appState.proxyRunning && runtime.running && runtime.traffic.connected
            val now = System.currentTimeMillis()
            val today = localTrafficDay(now)
            if (!connected) {
                trafficWasConnected = false
                trafficSessionId = null
                updateTrafficSummary(
                    ledger = trafficLedgerStore.snapshot(),
                    today = today,
                    runtimeTraffic = null,
                    recordHistory = recordHistory,
                )
            } else {
                if (!trafficWasConnected) {
                    trafficSessionId = trafficSessionId ?: newTrafficSessionId(now)
                }
                trafficWasConnected = true
                val runtimeTraffic = runtime.traffic
                val observedAt = runtimeTraffic.latest.timestampMillis
                val reduction = trafficLedgerStore.update(
                    TrafficLedgerSample(
                        sessionId = checkNotNull(trafficSessionId),
                        uploadTotalBytes = runtimeTraffic.totalUp,
                        downloadTotalBytes = runtimeTraffic.totalDown,
                        observedAtMillis = observedAt,
                        localDay = localTrafficDay(observedAt),
                        sourceId = if (appState.runMode.isRootRunMode()) {
                            "root:${appState.runMode}"
                        } else {
                            "embedded:${appState.runMode}"
                        },
                    ),
                )
                updateTrafficSummary(
                    ledger = reduction.ledger,
                    today = today,
                    runtimeTraffic = TrafficRuntimeSummary(
                        uploadBytesPerSecond = runtimeTraffic.latest.up,
                        downloadBytesPerSecond = runtimeTraffic.latest.down,
                        sessionUploadBytes = runtimeTraffic.totalUp,
                        sessionDownloadBytes = runtimeTraffic.totalDown,
                        sampleTimestampMillis = runtimeTraffic.latest.timestampMillis,
                    ),
                    recordHistory = recordHistory,
                )
            }
            delay(intervalMillis.milliseconds)
        }
    }

    private suspend fun collectConnections(
        intervalMillis: Long,
        includeDetails: Boolean,
    ) {
        while (currentCoroutineContext().isActive) {
            refreshConnections(includeDetails)
            delay(intervalMillis.milliseconds)
        }
    }

    private suspend fun refreshConnections(includeDetails: Boolean = true) = connectionRefreshMutex.withLock {
        val appState = stateStore.state.value
        if (!appState.proxyRunning) {
            mutableState.update { current ->
                current.copy(
                    connections = MonitoringConnectionsSummary(status = ConnectionMonitorStatus.ServiceStopped),
                )
            }
            return@withLock
        }
        mutableState.update { current ->
            when {
                !includeDetails && current.connections.snapshot.updatedAtMillis > 0L -> {
                    current.copy(connections = current.connections.copy(snapshot = MihomoConnectionsState()))
                }

                includeDetails && current.connections.snapshot.updatedAtMillis == 0L -> {
                current.copy(connections = current.connections.copy(status = ConnectionMonitorStatus.Loading, error = ""))
                }

                else -> current
            }
        }
        mihomoRuntime.getConnections(appState)
            .onSuccess { snapshot ->
                mutableState.update { current ->
                    val detailSnapshot = if (includeDetails) {
                        deriveConnectionRates(
                            previous = current.connections.snapshot.takeIf { it.updatedAtMillis > 0L },
                            current = snapshot,
                        )
                    } else {
                        null
                    }
                    val displayedSnapshot = detailSnapshot ?: MihomoConnectionsState()
                    val sourceSnapshot = detailSnapshot ?: snapshot
                    current.copy(
                        connections = MonitoringConnectionsSummary(
                            activeCount = sourceSnapshot.connections.size,
                            uploadBytesPerSecond = detailSnapshot?.connections
                                ?.sumKnownRates(MihomoConnection::uploadBytesPerSecond),
                            downloadBytesPerSecond = detailSnapshot?.connections
                                ?.sumKnownRates(MihomoConnection::downloadBytesPerSecond),
                            sessionUploadBytes = detailSnapshot?.uploadTotalBytes,
                            sessionDownloadBytes = detailSnapshot?.downloadTotalBytes,
                            snapshot = displayedSnapshot,
                            status = ConnectionMonitorStatus.Available,
                        ),
                    )
                }
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                mutableState.update { current ->
                    current.copy(
                        connections = current.connections.copy(
                            snapshot = if (includeDetails) current.connections.snapshot else MihomoConnectionsState(),
                            status = ConnectionMonitorStatus.Error,
                            error = error.message.orEmpty(),
                            stale = includeDetails && current.connections.snapshot.updatedAtMillis > 0L,
                        ),
                    )
                }
            }
    }

    private fun updateTrafficSummary(
        ledger: features.monitoring.traffic.TrafficLedger,
        today: String,
        runtimeTraffic: TrafficRuntimeSummary?,
        recordHistory: Boolean,
    ) {
        mutableState.update { current ->
            current.copy(
                traffic = current.traffic.copy(
                    uploadBytesPerSecond = runtimeTraffic?.uploadBytesPerSecond,
                    downloadBytesPerSecond = runtimeTraffic?.downloadBytesPerSecond,
                    sessionUploadBytes = runtimeTraffic?.sessionUploadBytes,
                    sessionDownloadBytes = runtimeTraffic?.sessionDownloadBytes,
                    today = ledger.totalForDay(today),
                    sevenDays = ledger.totalForLastDays(7, today),
                    thirtyDays = ledger.totalForLastDays(30, today),
                    dailyTotals = ledger.days,
                    speedSamples = if (!recordHistory) {
                        emptyList()
                    } else runtimeTraffic?.let { summary ->
                        val existing = current.traffic.speedSamples
                        val withLatest = if (existing.lastOrNull()?.timestampMillis == summary.sampleTimestampMillis) {
                            existing
                        } else {
                            existing + MonitoringTrafficSpeedSample(
                                timestampMillis = summary.sampleTimestampMillis,
                                uploadBytesPerSecond = summary.uploadBytesPerSecond,
                                downloadBytesPerSecond = summary.downloadBytesPerSecond,
                            )
                        }
                        withLatest.filter { sample -> sample.timestampMillis >= System.currentTimeMillis() - TrafficSpeedHistoryMillis }
                            .takeLast(MaxTrafficSpeedSamples)
                    } ?: current.traffic.speedSamples,
                ),
            )
        }
    }

    private fun newTrafficSessionId(now: Long): String {
        trafficSessionSequence += 1L
        return "$now-$trafficSessionSequence"
    }

    private fun updateNetworkPageVisibility(visible: Boolean) {
        if (visible && !networkPageWasVisible) {
            networkPageWasVisible = true
            if (PublicNetworkProbeMemoryCache.shouldProbe(networkPageSessionId)) {
                refreshLocalNetworkSnapshot()
                startPublicNetworkProbe()
            }
        } else if (!visible && networkPageWasVisible) {
            networkPageWasVisible = false
            publicProbeGeneration += 1L
            publicProbeJob?.cancel()
            publicProbeJob = null
            mutableState.update { current ->
                current.copy(
                    network = current.network.copy(
                        publicProbe = current.network.publicProbe.copy(refreshing = false),
                    ),
                )
            }
        }
    }

    private fun refreshLocalNetworkSnapshot() {
        val snapshot = networkMonitor.snapshot()
        mutableState.update { current ->
            current.copy(network = current.network.copy(local = snapshot))
        }
    }

    private fun startPublicNetworkProbe(family: AddressFamily? = null) {
        publicProbeGeneration += 1L
        val generation = publicProbeGeneration
        publicProbeJob?.cancel()
        mutableState.update { current ->
            current.copy(
                network = current.network.copy(
                    publicProbe = current.network.publicProbe.copy(refreshing = true),
                ),
            )
        }
        publicProbeJob = appScope.launch {
            try {
                if (generation != publicProbeGeneration || !networkPageWasVisible) return@launch
                val batch = if (family == null) publicNetworkProbeClient.probe() else null
                val single = family?.let { selected -> publicNetworkProbeClient.probe(selected) }
                if (generation != publicProbeGeneration || !networkPageWasVisible) return@launch
                val completedAt = System.currentTimeMillis()
                mutableState.update { current ->
                    val next = current.copy(
                        network = current.network.copy(
                            publicProbe = if (family == null) {
                                applyPublicProbeAttempts(
                                    previous = current.network.publicProbe,
                                    ipv4 = checkNotNull(batch).first,
                                    ipv6 = batch.second,
                                    completedAtMillis = completedAt,
                                )
                            } else {
                                applyPublicProbeAttempt(
                                    previous = current.network.publicProbe,
                                    family = family,
                                    attempt = checkNotNull(single),
                                    completedAtMillis = completedAt,
                                )
                            },
                        ),
                    )
                    PublicNetworkProbeMemoryCache.write(next.network.publicProbe)
                    next
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                if (generation == publicProbeGeneration && networkPageWasVisible) {
                    mutableState.update { current ->
                        current.copy(
                            network = current.network.copy(
                                publicProbe = current.network.publicProbe.copy(refreshing = false),
                            ),
                        )
                    }
                }
            }
        }
    }
}

private data class TrafficRuntimeSummary(
    val uploadBytesPerSecond: Long,
    val downloadBytesPerSecond: Long,
    val sessionUploadBytes: Long,
    val sessionDownloadBytes: Long,
    val sampleTimestampMillis: Long,
)

private fun List<MihomoConnection>.sumKnownRates(selector: (MihomoConnection) -> Long?): Long? {
    val rates = mapNotNull(selector)
    if (rates.isEmpty()) return null
    return rates.fold(0L) { total, rate ->
        if (Long.MAX_VALUE - total < rate.coerceAtLeast(0L)) Long.MAX_VALUE else total + rate.coerceAtLeast(0L)
    }
}

private const val TrafficSpeedHistoryMillis = 5L * 60L * 1_000L
private const val MaxTrafficSpeedSamples = 301
private const val LogTag = "MonitoringRepository"
