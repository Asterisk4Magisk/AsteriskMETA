// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring

import android.content.Context
import android.os.SystemClock
import app.modes.RunModeVpnService
import engine.mihomo.runtime.connectionCounts
import app.modes.isRootRunMode
import data.AndroidAppStateStore
import engine.mihomo.raw.loadSelectedRawConfig
import engine.mihomo.raw.usesRawMihomoConfig
import engine.proxy.toLocalProxyOptionsOrNull
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import system.AndroidRootShellGateway
import kotlin.time.Duration.Companion.milliseconds
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime
import engine.proxy.toLocalProxyOptions
import features.monitoring.network.PublicProbeProxy
import java.net.InetSocketAddress
import java.net.Proxy
import utils.encodeBase64

internal class MonitoringRepository(
    private val appScope: CoroutineScope,
    context: Context,
    rootAccess: AndroidRootShellGateway,
    private val stateStore: AndroidAppStateStore,
    private val mihomoRuntime: MihomoRuntimeRepository,
) {
    private val appContext = context.applicationContext
    private val processStatsSource = AndroidProcessStatsSource(context, rootAccess)
    private val networkMonitor = AndroidNetworkMonitor(context)
    private val publicNetworkProbeClient = PublicNetworkProbeClient(
        proxyProvider = { resolvePublicProbeProxy() },
    )
    private val trafficLedgerStore = TrafficLedgerStore(context)
    private var previousProcessSnapshot: ProcessTickSnapshot? = null
    private var previousProcessSource: ProcessStatsSourceKind? = null
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
                            launch { collectTrafficStats(interval) }
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
    ) = coroutineScope {
        launch { collectProcessStats(intervalMillis, recordHistory) }
        launch { collectMemoryStats(maxOf(intervalMillis, MemoryMonitoringIntervalMillis)) }
    }

    private suspend fun collectProcessStats(
        intervalMillis: Long,
        recordHistory: Boolean,
    ) {
        while (currentCoroutineContext().isActive) {
            val appState = stateStore.state.value
            if (!appState.proxyRunning) {
                previousProcessSnapshot = null
                previousProcessSource = null
                mutableState.update { current ->
                    current.copy(
                        resource = current.resource.copy(
                            cpuPercent = null,
                            source = null,
                            uptimeMillis = null,
                            uptimeMeasuredAtElapsedMillis = 0L,
                            processId = null,
                        ),
                    )
                }
            } else {
                val reading = processStatsSource.read(appState)
                val isVpnMode = appState.runMode == RunModeVpnService
                val baseline = previousProcessSnapshot.takeIf {
                    previousProcessSource == reading?.source
                }
                val sameProcess = baseline != null && reading != null &&
                    baseline.pid == reading.snapshot.pid &&
                    baseline.startTimeTicks == reading.snapshot.startTimeTicks
                val cpuPercent = reading?.snapshot?.let { current ->
                    calculateProcessCpuPercent(baseline, current)
                }
                if (reading != null || isVpnMode) {
                    previousProcessSnapshot = reading?.snapshot
                    previousProcessSource = reading?.source
                }
                val timestampMillis = System.currentTimeMillis()
                mutableState.update { current ->
                    val latestAppState = stateStore.state.value
                    if (!latestAppState.proxyRunning || latestAppState.runMode != appState.runMode) {
                        return@update current
                    }
                    val fallbackResource = current.resource.takeUnless { isVpnMode }
                    val memoryBytes = if (isVpnMode) reading?.memoryBytes else current.resource.memoryBytes
                    val sample = ProcessStatsSample(
                        timestampMillis = timestampMillis,
                        cpuPercent = cpuPercent,
                        memoryBytes = memoryBytes,
                    )
                    val history = if (recordHistory) {
                        val previousSamples = current.resource.oneHourSamples.takeIf {
                            reading == null || current.resource.source == reading.source
                        }.orEmpty()
                        appendProcessStatsSample(previousSamples, sample)
                    } else {
                        null
                    }
                    current.copy(
                        resource = current.resource.copy(
                            cpuPercent = when {
                                cpuPercent != null -> cpuPercent
                                reading == null || sameProcess -> fallbackResource?.cpuPercent
                                else -> null
                            },
                            memoryBytes = memoryBytes,
                            memoryLimitBytes = if (isVpnMode) null else current.resource.memoryLimitBytes,
                            source = reading?.source ?: fallbackResource?.source,
                            uptimeMillis = reading?.uptimeMillis ?: fallbackResource?.uptimeMillis,
                            uptimeMeasuredAtElapsedMillis = if (reading?.uptimeMillis != null) {
                                SystemClock.elapsedRealtime()
                            } else {
                                fallbackResource?.uptimeMeasuredAtElapsedMillis ?: 0L
                            },
                            processId = reading?.snapshot?.pid ?: fallbackResource?.processId,
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

    private suspend fun collectMemoryStats(intervalMillis: Long) {
        while (currentCoroutineContext().isActive) {
            val appState = stateStore.state.value
            if (!appState.proxyRunning) {
                mutableState.update { current ->
                    current.copy(
                        resource = current.resource.copy(
                            memoryBytes = null,
                            memoryLimitBytes = null,
                        ),
                    )
                }
            } else if (appState.runMode.isRootRunMode()) {
                val memoryBytes = mihomoRuntime.refreshMemoryNow(appState)
                val memoryLimitBytes = mihomoRuntime.state.value.memory.osLimitBytes.takeIf { it > 0L }
                mutableState.update { current ->
                    val latestAppState = stateStore.state.value
                    if (!latestAppState.proxyRunning || latestAppState.runMode != appState.runMode) {
                        return@update current
                    }
                    current.copy(
                        resource = current.resource.copy(
                            memoryBytes = memoryBytes ?: current.resource.memoryBytes,
                            memoryLimitBytes = memoryLimitBytes ?: current.resource.memoryLimitBytes,
                        ),
                    )
                }
            }
            delay(intervalMillis.milliseconds)
        }
    }

    private suspend fun collectTrafficStats(intervalMillis: Long) {
        try {
            while (currentCoroutineContext().isActive) {
                val runtime = mihomoRuntime.state.value
                val appState = stateStore.state.value
                val connected = appState.proxyRunning && runtime.running && runtime.traffic.connected
                val now = System.currentTimeMillis()
                val today = localTrafficDay(now)
                if (!connected) {
                    updateTrafficSummary(
                        ledger = trafficLedgerStore.snapshot(),
                        today = today,
                        runtimeTraffic = null,
                    )
                } else {
                    val runtimeTraffic = runtime.traffic
                    val observedAt = runtimeTraffic.latest.timestampMillis
                    val sourceId = if (appState.runMode.isRootRunMode()) {
                        "root"
                    } else {
                        "embedded"
                    }
                    val reduction = trafficLedgerStore.update(
                        TrafficLedgerSample(
                            uploadTotalBytes = runtimeTraffic.totalUp,
                            downloadTotalBytes = runtimeTraffic.totalDown,
                            observedAtMillis = observedAt,
                            localDay = localTrafficDay(observedAt),
                            sourceId = sourceId,
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
                        ),
                    )
                }
                delay(intervalMillis.milliseconds)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                trafficLedgerStore.flush()
            }
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
        if (!includeDetails) {
            mihomoRuntime.getConnectionCounts(appState)
                .onSuccess { counts ->
                    mutableState.update { current ->
                        current.copy(
                            connections = MonitoringConnectionsSummary(
                                activeCount = counts.activeCount,
                                directCount = counts.directCount,
                                proxyCount = counts.proxyCount,
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
                                snapshot = MihomoConnectionsState(),
                                status = ConnectionMonitorStatus.Error,
                                error = error.message.orEmpty(),
                                stale = false,
                            ),
                        )
                    }
                }
            return@withLock
        }
        mihomoRuntime.getConnections(appState)
            .onSuccess { snapshot ->
                val counts = snapshot.connections.connectionCounts()
                mutableState.update { current ->
                    val detailSnapshot = deriveConnectionRates(
                        previous = current.connections.snapshot.takeIf { it.updatedAtMillis > 0L },
                        current = snapshot,
                    )
                    current.copy(
                        connections = MonitoringConnectionsSummary(
                            activeCount = counts.activeCount,
                            directCount = counts.directCount,
                            proxyCount = counts.proxyCount,
                            uploadBytesPerSecond = detailSnapshot.connections
                                .sumKnownRates(MihomoConnection::uploadBytesPerSecond),
                            downloadBytesPerSecond = detailSnapshot.connections
                                .sumKnownRates(MihomoConnection::downloadBytesPerSecond),
                            sessionUploadBytes = detailSnapshot.uploadTotalBytes,
                            sessionDownloadBytes = detailSnapshot.downloadTotalBytes,
                            snapshot = detailSnapshot,
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
                            snapshot = current.connections.snapshot,
                            status = ConnectionMonitorStatus.Error,
                            error = error.message.orEmpty(),
                            stale = current.connections.snapshot.updatedAtMillis > 0L,
                        ),
                    )
                }
            }
    }

    private fun updateTrafficSummary(
        ledger: features.monitoring.traffic.TrafficLedger,
        today: String,
        runtimeTraffic: TrafficRuntimeSummary?,
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
                ),
            )
        }
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
                                    batch = checkNotNull(batch),
                                    completedAtMillis = completedAt,
                                )
                            } else {
                                applyPublicProbeAttempt(
                                    previous = current.network.publicProbe,
                                    family = family,
                                    batch = checkNotNull(single),
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

    private fun resolvePublicProbeProxy(): PublicProbeProxy? {
        val appState = stateStore.state.value
        // The control runtime can remain connected while the proxy service is stopped.
        if (!appState.proxyRunning) return null
        val runtimeOptions = LocalProxyRuntime.current()
        val usesRawConfig = appState.usesRawMihomoConfig()
        val options = runtimeOptions ?: if (usesRawConfig) {
            appContext.loadSelectedRawConfig(appState)?.snapshot?.toLocalProxyOptionsOrNull()
        } else {
            appState.toLocalProxyOptions()
        }
        checkNotNull(options) { "No compatible local proxy inbound" }
        val host = options.listenAddress.takeIf { it != "0.0.0.0" } ?: LocalProxyLoopbackAddress
        val authHeader = if (options.username.isNotBlank()) {
            "Basic " + "${options.username}:${options.password}".encodeBase64()
        } else {
            null
        }
        return PublicProbeProxy(
            proxy = Proxy(if (usesRawConfig) Proxy.Type.SOCKS else Proxy.Type.HTTP, InetSocketAddress(host, options.port)),
            host = host,
            port = options.port,
            username = options.username,
            password = options.password,
            proxyAuthorization = authHeader,
        )
    }
}

private data class TrafficRuntimeSummary(
    val uploadBytesPerSecond: Long,
    val downloadBytesPerSecond: Long,
    val sessionUploadBytes: Long,
    val sessionDownloadBytes: Long,
)

private fun List<MihomoConnection>.sumKnownRates(selector: (MihomoConnection) -> Long?): Long? {
    val rates = mapNotNull(selector)
    if (rates.isEmpty()) return null
    return rates.fold(0L) { total, rate ->
        if (Long.MAX_VALUE - total < rate.coerceAtLeast(0L)) Long.MAX_VALUE else total + rate.coerceAtLeast(0L)
    }
}

private const val MemoryMonitoringIntervalMillis = 3_000L
private const val LogTag = "MonitoringRepository"
