// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import android.content.Context
import app.AppState
import app.modes.RunModeVpnService
import app.modes.isRootRunMode
import engine.mihomo.hasUsableMihomoProfile
import engine.mihomo.mihomoLogLevelName
import engine.mihomo.mihomoControlConfig
import engine.mihomo.selectedMihomoProfileOrNull
import engine.mihomo.MihomoControlConfig
import engine.mihomo.raw.loadSelectedRawConfig
import engine.mihomo.raw.usesRawMihomoConfig
import engine.proxy.ProxyEngineStartRequest
import engine.vpn.AndroidMihomoRuntime
import engine.vpn.VpnMihomoConfigFactory
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.ConnectException
import kotlin.time.Duration.Companion.milliseconds
import app.effects.MihomoRuntimeLifecycleTarget

internal class MihomoRuntimeRepository(
    private val appScope: CoroutineScope,
    context: Context,
    private val client: MihomoControlClient = MihomoControlClient(),
) : MihomoRuntimeLifecycleTarget {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(MihomoRuntimeState())
    private val trafficHistory = MihomoTrafficHistoryBuffer(MaxTrafficHistorySize)
    private val trafficHistoryLock = Any()
    private val runtimeStateLock = Any()
    private val runtimePreparationMutex = Mutex()
    private val proxyRefreshMutex = Mutex()
    private var monitorJob: Job? = null
    private var delayTestJob: Job? = null
    private var activeSignature: MihomoRuntimeSignature? = null
    private var preparedRuntimeSignature: MihomoPreparedRuntimeSignature? = null
    private var runtimeGeneration = 0L
    private var proxySnapshotConfigKey: Int? = null
    private val delayTestLock = Any()
    @Volatile
    private var lastLoggedRuntimeError = ""

    val state: StateFlow<MihomoRuntimeState> = mutableState.asStateFlow()

    internal fun trafficHistorySnapshot(limit: Int): List<MihomoTrafficSample> = synchronized(trafficHistoryLock) {
        trafficHistory.snapshot(limit)
    }

    init {
        appScope.launch(Dispatchers.IO) {
            refreshConnectivityState()
        }
    }

    fun start(appState: AppState) {
        if (!appState.hasUsableMihomoProfile()) {
            val shouldStopCore = activeSignature != null || AndroidMihomoRuntime.isLoaded()
            stopMonitor(resetSnapshots = false)
            if (shouldStopCore) {
                appScope.launch(Dispatchers.IO) {
                    runCatching { AndroidMihomoRuntime.stop(resetCore = true) }
                        .onFailure { error ->
                            AndroidAppLogger.warn(LogTag, "Failed to stop Mihomo runtime without configuration", error)
                        }
                    refreshConnectivityState()
                }
            } else {
                refreshConnectivity()
            }
            return
        }
        if (appState.usesRawMihomoConfig() && !appState.proxyRunning) {
            stopMonitor(resetSnapshots = false)
            appScope.launch(Dispatchers.IO) {
                runCatching { AndroidMihomoRuntime.stop(resetCore = true) }
                refreshConnectivityState()
            }
            return
        }
        val control = if (appState.usesRawMihomoConfig()) {
            runCatching { resolveMihomoControlConfig(appState) }.getOrElse { error ->
                stopMonitor(resetSnapshots = false)
                mutableState.update { current ->
                    current.copy(
                        running = false,
                        traffic = current.traffic.copy(connected = false),
                        lastError = error.message.orEmpty(),
                    )
                }
                refreshConnectivity()
                return
            }
        } else {
            resolveMihomoControlConfig(appState)
        }
        val backend = appState.mihomoRuntimeBackend()
        val trafficEnabled = appState.proxyRunning
        val signature = MihomoRuntimeSignature(
            control = control,
            backend = backend,
            trafficEnabled = trafficEnabled,
            runtimeConfigKey = appState.mihomoRuntimeConfigKey(backend),
        )
        val runtimeAlreadyActive = synchronized(runtimeStateLock) { activeSignature == signature } &&
            monitorJob?.isActive == true
        if (runtimeAlreadyActive) {
            return
        }
        val resetProxies = synchronized(runtimeStateLock) {
            proxySnapshotConfigKey != null && proxySnapshotConfigKey != signature.runtimeConfigKey
        }
        stopMonitor(resetSnapshots = false)
        val generation = synchronized(runtimeStateLock) {
            activeSignature = signature
            if (resetProxies) proxySnapshotConfigKey = null
            lastLoggedRuntimeError = ""
            mutableState.update { current ->
                current.copy(
                    running = true,
                    control = control,
                    traffic = current.traffic.copy(connected = false),
                    proxies = if (resetProxies) MihomoProxiesState() else current.proxies,
                    proxiesRefreshing = false,
                    lastError = "",
                )
            }
            runtimeGeneration
        }
        refreshConnectivity()
        monitorJob = appScope.launch(Dispatchers.IO) {
            runCatching { ensureInteractiveRuntime(appState, backend) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    reportRuntimeError("Mihomo runtime preparation", error)
                    mutableState.update { current ->
                        current.copy(
                            running = false,
                            traffic = current.traffic.copy(connected = false),
                            lastError = error.message.orEmpty(),
                        )
                    }
                    return@launch
                }
            if (trafficEnabled) {
                launch { collectTraffic(control, backend.useBridge()) }
            }
            launch {
                pollRuntime(
                    control = control,
                    useBridge = backend.useBridge(),
                    generation = generation,
                    configKey = signature.runtimeConfigKey,
                )
            }
        }
    }

    fun stop(resetSnapshots: Boolean = false) {
        stopMonitor(resetSnapshots)
        appScope.launch(Dispatchers.IO) {
            runCatching { AndroidMihomoRuntime.stop(resetCore = true) }
                .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to stop standby Mihomo runtime", error) }
            refreshConnectivityState()
        }
    }

    private fun stopMonitor(resetSnapshots: Boolean = false) {
        monitorJob?.cancel()
        monitorJob = null
        cancelDelayTest()
        if (resetSnapshots) {
            synchronized(trafficHistoryLock) { trafficHistory.clear() }
        }
        synchronized(runtimeStateLock) {
            runtimeGeneration += 1L
            activeSignature = null
            if (resetSnapshots) proxySnapshotConfigKey = null
            mutableState.update { current ->
                if (resetSnapshots) {
                    MihomoRuntimeState(
                        device = current.device,
                    )
                } else {
                    current.copy(
                        running = false,
                        traffic = current.traffic.copy(connected = false),
                        proxiesRefreshing = false,
                        delayTestingTarget = null,
                        lastError = "",
                    )
                }
            }
        }
    }

    suspend fun refresh(appState: AppState): Result<Unit> {
        return runCatching {
            val generation = currentRuntimeGeneration()
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend())
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                refreshRuntime(control, backend.useBridge(), generation, configKey)
            }
        }
    }

    suspend fun refreshProxies(appState: AppState): Result<Unit> {
        return runCatching {
            require(appState.hasUsableMihomoProfile()) { "Mihomo profile is not configured" }
            val generation = currentRuntimeGeneration()
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend())
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                refreshProxySnapshot(
                    control = control,
                    useBridge = backend.useBridge(),
                    generation = generation,
                    configKey = configKey,
                ).getOrThrow()
            }
        }
    }

    override fun resumeForForeground(appState: AppState) {
        start(appState)
    }

    override fun pauseForBackground(appState: AppState, releaseStandby: Boolean) {
        stopMonitor(resetSnapshots = false)
        if (releaseStandby) {
            appScope.launch(Dispatchers.IO) {
                AndroidMihomoRuntime.releaseStandby()
                refreshConnectivityState()
            }
        } else {
            refreshConnectivity()
        }
    }

    suspend fun getProxyProviderDetail(
        appState: AppState,
        providerName: String,
    ): Result<MihomoProxyProviderRuntimeDetail> {
        return runCatching {
            if (!appState.hasUsableMihomoProfile()) {
                error("Mihomo profile is not configured")
            }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                client.getProxyProvider(control, providerName, backend.useBridge())
            }
        }
    }

    suspend fun getConnections(appState: AppState): Result<MihomoConnectionsState> {
        return runCatching {
            require(appState.proxyRunning) { "Proxy service is not running" }
            require(appState.hasUsableMihomoProfile()) { "Mihomo profile is not configured" }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                client.getConnections(control, backend.useBridge())
            }
        }
    }

    suspend fun getConnectionCount(appState: AppState): Result<Int> {
        return runCatching {
            require(appState.proxyRunning) { "Proxy service is not running" }
            require(appState.hasUsableMihomoProfile()) { "Mihomo profile is not configured" }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                try {
                    client.getConnectionCount(control, backend.useBridge())
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    client.getConnections(control, backend.useBridge()).connections.size
                }
            }
        }
    }

    suspend fun closeConnection(appState: AppState, connectionId: String): Result<Boolean> {
        return runCatching {
            require(appState.proxyRunning) { "Proxy service is not running" }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                client.closeConnection(control, connectionId, backend.useBridge())
            }
        }
    }

    suspend fun closeAllConnections(appState: AppState): Result<Unit> {
        return runCatching {
            require(appState.proxyRunning) { "Proxy service is not running" }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                client.closeAllConnections(control, backend.useBridge())
            }
        }
    }

    suspend fun refreshProxyProvider(
        appState: AppState,
        providerName: String,
    ): Result<Unit> {
        return runCatching {
            if (!appState.hasUsableMihomoProfile()) {
                return@runCatching
            }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            val generation = currentRuntimeGeneration()
            val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend())
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                client.updateProxyProvider(control, providerName, backend.useBridge())
                refreshProxySnapshot(control, backend.useBridge(), generation, configKey, coalesce = false)
            }
        }
    }

    suspend fun patchMode(
        appState: AppState,
        mode: String,
    ): Result<Unit> {
        return runCatching {
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            val generation = currentRuntimeGeneration()
            val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend())
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                client.patchMode(control, mode, backend.useBridge())
                applyMode(mode, generation, configKey)
                refreshProxySnapshot(control, backend.useBridge(), generation, configKey, coalesce = false)
            }
        }
    }

    suspend fun patchLogLevel(appState: AppState): Result<Unit> {
        return runCatching {
            if (!appState.hasUsableMihomoProfile()) {
                return@runCatching
            }
            require(!appState.usesRawMihomoConfig()) { "Log level is read-only because it comes from YAML" }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                client.patchLogLevel(control, appState.mihomoLogLevelName(), backend.useBridge())
            }
        }
    }

    suspend fun selectProxy(
        appState: AppState,
        groupName: String,
        proxyName: String,
    ): Result<Unit> {
        return runCatching {
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            val generation = currentRuntimeGeneration()
            val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend())
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                client.selectProxy(control, groupName, proxyName, backend.useBridge())
                applySelectedProxy(groupName, proxyName, generation, configKey)
                refreshProxySnapshot(control, backend.useBridge(), generation, configKey, coalesce = false)
                refreshConnectivity()
            }
        }
    }

    fun refreshConnectivity() {
        appScope.launch(Dispatchers.IO) {
            refreshConnectivityState()
        }
    }

    suspend fun refreshMemoryNow(appState: AppState): Long? {
        return runCatching { refreshMemoryState(appState) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                reportRuntimeError("Mihomo memory refresh", error)
                mutableState.update { current -> current.copy(lastError = error.message.orEmpty()) }
            }
            .getOrNull()
    }

    suspend fun testProxyDelay(
        appState: AppState,
        proxyName: String,
    ): Result<MihomoDelayResult> {
        return startDelayTest(proxyName) {
            runProxyDelayTest(appState, proxyName)
        }.await()
    }

    suspend fun testGroupDelay(
        appState: AppState,
        groupName: String,
        testUrl: String,
    ): Result<MihomoDelayResult> {
        return startDelayTest(groupName) {
            runGroupDelayTest(appState, groupName, testUrl)
        }.await()
    }

    private fun startDelayTest(
        target: String,
        block: suspend () -> MihomoDelayResult,
    ): Deferred<Result<MihomoDelayResult>> {
        synchronized(delayTestLock) {
            if (delayTestJob?.isActive == true) {
                return CompletableDeferred(Result.failure(IllegalStateException("Mihomo delay test is already running")))
            }

            val result = CompletableDeferred<Result<MihomoDelayResult>>()
            delayTestJob = appScope.launch(Dispatchers.IO) {
                mutableState.update { current -> current.copy(delayTestingTarget = target) }
                try {
                    result.complete(runCatching { block() })
                } finally {
                    mutableState.update { current ->
                        if (current.delayTestingTarget == target) {
                            current.copy(delayTestingTarget = null)
                        } else {
                            current
                        }
                    }
                    synchronized(delayTestLock) {
                        delayTestJob = null
                    }
                }
            }
            delayTestJob?.invokeOnCompletion { error ->
                if (!result.isCompleted && error != null) {
                    result.complete(Result.failure(error))
                }
            }
            return result
        }
    }

    private fun cancelDelayTest() {
        synchronized(delayTestLock) {
            delayTestJob?.cancel()
            delayTestJob = null
        }
    }

    private suspend fun runProxyDelayTest(
        appState: AppState,
        proxyName: String,
    ): MihomoDelayResult {
        val control = resolveMihomoControlConfig(appState)
        val backend = resolveInteractiveBackend(appState, control)
        val generation = currentRuntimeGeneration()
        val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend())
        return withContext(Dispatchers.IO) {
            ensureInteractiveRuntime(appState, backend)
            client.testProxyDelay(control, proxyName, useBridge = backend.useBridge()).also { result ->
                applyDelays(result, generation, configKey)
                refreshProxySnapshot(control, backend.useBridge(), generation, configKey, coalesce = false)
            }
        }
    }

    private suspend fun runGroupDelayTest(
        appState: AppState,
        groupName: String,
        testUrl: String,
    ): MihomoDelayResult {
        val control = resolveMihomoControlConfig(appState)
        val backend = resolveInteractiveBackend(appState, control)
        val generation = currentRuntimeGeneration()
        val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend())
        return withContext(Dispatchers.IO) {
            ensureInteractiveRuntime(appState, backend)
            val expectedProxyNames = state.value.proxies.groups
                .firstOrNull { group -> group.name == groupName }
                ?.all
                .orEmpty()
            clearDelays(expectedProxyNames, generation, configKey)
            client.testGroupDelay(
                config = control,
                groupName = groupName,
                url = testUrl.ifBlank { engine.mihomo.DefaultMihomoDelayTestUrl },
                expectedProxyNames = expectedProxyNames,
                useBridge = backend.useBridge(),
            ).also { result ->
                applyDelays(result, generation, configKey)
                refreshProxySnapshot(control, backend.useBridge(), generation, configKey, coalesce = false)
            }
        }
    }

    private fun prepareRuntime(
        appState: AppState,
        backend: MihomoRuntimeBackend,
    ) {
        when (backend) {
            MihomoRuntimeBackend.Bridge -> {
                val preserveActiveTun = appState.runMode == RunModeVpnService
                if (preserveActiveTun && AndroidMihomoRuntime.isRunning()) {
                    return
                }
                AndroidMihomoRuntime.ensureLoaded(
                    context = appContext,
                    config = VpnMihomoConfigFactory.create(
                        context = appContext,
                        request = ProxyEngineStartRequest(appState),
                        exposePorts = appState.exposeBridgePorts(),
                    ),
                    preserveActiveTun = preserveActiveTun,
                )
            }

            MihomoRuntimeBackend.Api -> {
                if (!(appState.proxyRunning && appState.runMode == RunModeVpnService)) {
                    AndroidMihomoRuntime.stop(resetCore = true)
                }
            }
        }
    }

    private fun resolveMihomoControlConfig(appState: AppState): MihomoControlConfig {
        if (!appState.usesRawMihomoConfig()) return appState.mihomoControlConfig()
        require(appState.proxyRunning) { "Raw configuration API is available only while the proxy service is running" }
        val parsed = appContext.loadSelectedRawConfig(appState)
            ?: error("Raw configuration is not available")
        val snapshot = parsed.snapshot ?: error(parsed.error ?: "Raw configuration is invalid")
        return snapshot.api.value?.control
            ?: error(snapshot.api.problem ?: "Mihomo API is not configured in YAML")
    }

    private fun resolveInteractiveBackend(
        appState: AppState,
        control: MihomoControlConfig,
    ): MihomoRuntimeBackend {
        if (appState.usesRawMihomoConfig()) {
            require(appState.proxyRunning) { "Raw configuration API is available only while the proxy service is running" }
            return MihomoRuntimeBackend.Api
        }
        val backend = appState.mihomoRuntimeBackend()
        if (backend == MihomoRuntimeBackend.Api) {
            return backend
        }
        return if (appState.runMode.isRootRunMode() && client.isApiAvailable(control)) {
            MihomoRuntimeBackend.Api
        } else {
            backend
        }
    }

    private suspend fun ensureInteractiveRuntime(
        appState: AppState,
        backend: MihomoRuntimeBackend,
    ) {
        val signature = MihomoPreparedRuntimeSignature(
            backend = backend,
            appState = appState,
        )
        if (isInteractiveRuntimePrepared(appState, signature)) return

        withContext(Dispatchers.IO) {
            runtimePreparationMutex.withLock {
                if (isInteractiveRuntimePrepared(appState, signature)) return@withLock
                prepareRuntime(appState, backend)
                synchronized(runtimeStateLock) {
                    preparedRuntimeSignature = signature
                }
            }
        }
    }

    private fun isInteractiveRuntimePrepared(
        appState: AppState,
        signature: MihomoPreparedRuntimeSignature,
    ): Boolean {
        val signatureMatches = synchronized(runtimeStateLock) {
            preparedRuntimeSignature == signature
        }
        if (!signatureMatches) return false

        return when (signature.backend) {
            MihomoRuntimeBackend.Bridge -> AndroidMihomoRuntime.isLoaded()
            MihomoRuntimeBackend.Api -> {
                appState.proxyRunning && appState.runMode == RunModeVpnService ||
                    !AndroidMihomoRuntime.isLoaded()
            }
        }
    }

    private fun refreshDeviceState() {
        val device = collectMihomoDeviceState()
        mutableState.update { current -> current.copy(device = device) }
    }

    private fun refreshConnectivityState() {
        refreshDeviceState()
    }

    private suspend fun refreshMemoryState(appState: AppState): Long? {
        if (!appState.hasUsableMihomoProfile()) {
            mutableState.update { current -> current.copy(memory = MihomoMemoryState()) }
            return null
        }
        val control = resolveMihomoControlConfig(appState)
        val backend = resolveInteractiveBackend(appState, control)
        ensureInteractiveRuntime(appState, backend)
        val memory = runRuntimeRequest { client.getMemory(control, backend.useBridge()) }
        memory.exceptionOrNull()?.let { error -> reportRuntimeError("Mihomo API /memory", error) }
        mutableState.update { current ->
            current.copy(
                memory = memory.getOrNull() ?: current.memory,
                lastError = memory.exceptionOrNull()?.message.orEmpty(),
            )
        }
        return memory.getOrNull()?.inUseBytes?.takeIf { bytes -> bytes > 0L }
    }

    private suspend fun collectTraffic(
        control: MihomoControlConfig,
        useBridge: Boolean,
    ) {
        runCatching {
            client.traffic(control, useBridge).collect { sample ->
                synchronized(trafficHistoryLock) { trafficHistory.append(sample) }
                mutableState.update { current ->
                    current.copy(
                        traffic = current.traffic.copy(
                            latest = sample,
                            totalUp = sample.totalUp ?: (current.traffic.totalUp + sample.up),
                            totalDown = sample.totalDown ?: (current.traffic.totalDown + sample.down),
                            connected = true,
                        ),
                    )
                }
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            reportRuntimeError("Mihomo API /traffic", error)
            mutableState.update { current ->
                current.copy(
                    traffic = current.traffic.copy(connected = false),
                    lastError = error.message.orEmpty(),
                )
            }
        }
    }

    private suspend fun pollRuntime(
        control: MihomoControlConfig,
        useBridge: Boolean,
        generation: Long,
        configKey: Int,
    ) {
        runCatching { refreshRuntime(control, useBridge, generation, configKey) }
            .onFailure { error -> handleRuntimePollingFailure(error, generation, configKey) }
        while (currentCoroutineContext().isActive) {
            delay(ProxyFallbackPollIntervalMillis.milliseconds)
            runCatching { refreshProxySnapshot(control, useBridge, generation, configKey) }
                .onFailure { error ->
                    handleRuntimePollingFailure(error, generation, configKey)
                }
        }
    }

    private suspend fun refreshRuntime(
        control: MihomoControlConfig,
        useBridge: Boolean,
        generation: Long,
        configKey: Int,
    ) {
        refreshProxySnapshot(control, useBridge, generation, configKey)
        if (!isRuntimeRequestCurrent(generation, configKey)) return
        val memory = runRuntimeRequest { client.getMemory(control, useBridge) }
        val version = runRuntimeRequest { client.getVersion(control, useBridge) }
        memory.exceptionOrNull()?.let { error -> reportRuntimeError("Mihomo API /memory", error) }
        updateRuntimeStateIfCurrent(generation, configKey) { current ->
            current.copy(
                memory = memory.getOrNull() ?: current.memory,
                version = version.getOrNull() ?: current.version,
                lastError = memory.exceptionOrNull()?.message ?: current.lastError,
            )
        }
    }

    private suspend fun refreshProxySnapshot(
        control: MihomoControlConfig,
        useBridge: Boolean,
        generation: Long,
        configKey: Int,
        coalesce: Boolean = true,
    ): Result<Unit> {
        val observedUpdatedAtMillis = state.value.proxies.updatedAtMillis
        return proxyRefreshMutex.withLock {
            if (!isRuntimeRequestCurrent(generation, configKey)) return@withLock Result.success(Unit)
            val refreshedByAnotherRequest = synchronized(runtimeStateLock) {
                proxySnapshotConfigKey == configKey &&
                    state.value.proxies.updatedAtMillis > observedUpdatedAtMillis
            }
            if (coalesce && refreshedByAnotherRequest) return@withLock Result.success(Unit)

            updateRuntimeStateIfCurrent(generation, configKey) { current ->
                current.copy(proxiesRefreshing = true)
            }
            try {
                val configs = runRuntimeRequest { client.getConfigs(control, useBridge) }
                val mode = configs.getOrNull()?.mode ?: state.value.configs.mode
                val proxies = runRuntimeRequest { client.getProxies(control, useBridge, mode) }
                val committed = synchronized(runtimeStateLock) {
                    if (!isRuntimeRequestCurrentLocked(generation, configKey)) {
                        false
                    } else {
                        val refreshedProxies = proxies.getOrNull()
                        if (refreshedProxies != null) proxySnapshotConfigKey = configKey
                        mutableState.update { current ->
                            current.copy(
                                running = true,
                                control = control,
                                configs = configs.getOrNull() ?: current.configs,
                                proxies = refreshedProxies?.withPreservedDelays(current.proxies)
                                    ?: current.proxies,
                                proxiesRefreshing = false,
                                lastError = proxies.exceptionOrNull()?.message.orEmpty(),
                            )
                        }
                        true
                    }
                }
                if (!committed) return@withLock Result.success(Unit)
                configs.exceptionOrNull()?.let { error -> reportRuntimeError("Mihomo API /configs", error) }
                proxies.exceptionOrNull()?.let { error -> reportRuntimeError("Mihomo API /proxies", error) }
                if (configs.isSuccess && proxies.isSuccess) lastLoggedRuntimeError = ""
                proxies.map { }
            } finally {
                updateRuntimeStateIfCurrent(generation, configKey) { current ->
                    if (current.proxiesRefreshing) current.copy(proxiesRefreshing = false) else current
                }
            }
        }
    }

    private fun handleRuntimePollingFailure(
        error: Throwable,
        generation: Long,
        configKey: Int,
    ) {
        if (error is CancellationException) throw error
        if (!isRuntimeRequestCurrent(generation, configKey)) return
        reportRuntimeError("Mihomo runtime polling", error)
        updateRuntimeStateIfCurrent(generation, configKey) { current ->
            current.copy(lastError = error.message.orEmpty())
        }
    }

    private fun currentRuntimeGeneration(): Long = synchronized(runtimeStateLock) { runtimeGeneration }

    private fun isRuntimeRequestCurrent(generation: Long, configKey: Int): Boolean =
        synchronized(runtimeStateLock) { isRuntimeRequestCurrentLocked(generation, configKey) }

    private fun isRuntimeRequestCurrentLocked(generation: Long, configKey: Int): Boolean {
        return runtimeGeneration == generation && activeSignature?.runtimeConfigKey == configKey
    }

    private fun updateRuntimeStateIfCurrent(
        generation: Long,
        configKey: Int,
        transform: (MihomoRuntimeState) -> MihomoRuntimeState,
    ): Boolean = synchronized(runtimeStateLock) {
        if (!isRuntimeRequestCurrentLocked(generation, configKey)) {
            false
        } else {
            mutableState.update(transform)
            true
        }
    }

    private suspend fun <T> runRuntimeRequest(block: suspend () -> T): Result<T> {
        return runCatching { block() }
            .onFailure { error ->
                if (error is CancellationException) throw error
            }
    }

    private fun reportRuntimeError(source: String, error: Throwable) {
        val message = error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName
        if (error.isTransientControlConnectionFailure()) {
            val signature = "Mihomo control API unavailable: $message"
            if (signature != lastLoggedRuntimeError) {
                lastLoggedRuntimeError = signature
                AndroidAppLogger.debug(LogTag, signature)
            }
            return
        }
        val signature = "$source: $message"
        if (signature == lastLoggedRuntimeError) {
            return
        }
        lastLoggedRuntimeError = signature
        AndroidAppLogger.warn(LogTag, "$source failed: $message", error)
    }

    private fun applyDelays(
        result: MihomoDelayResult,
        generation: Long,
        configKey: Int,
    ) {
        if (result.delays.isEmpty()) return
        updateRuntimeStateIfCurrent(generation, configKey) { current ->
            val nodes = current.proxies.nodes.map { node ->
                result.delays[node.name]?.let { delay -> node.copy(delay = delay) } ?: node
            }
            current.copy(
                proxies = current.proxies.copy(
                    nodes = nodes,
                    nodeByName = nodes.associateBy(MihomoProxyNode::name),
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun applyMode(mode: String, generation: Long, configKey: Int) {
        updateRuntimeStateIfCurrent(generation, configKey) { current ->
            current.copy(
                configs = current.configs.copy(mode = mode),
            )
        }
    }

    private fun applySelectedProxy(
        groupName: String,
        proxyName: String,
        generation: Long,
        configKey: Int,
    ) {
        updateRuntimeStateIfCurrent(generation, configKey) { current ->
            val groups = current.proxies.groups.map { group ->
                if (group.name == groupName) {
                    group.copy(now = proxyName)
                } else {
                    group
                }
            }
            current.copy(
                proxies = current.proxies.copy(
                    groups = groups,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun clearDelays(
        proxyNames: List<String>,
        generation: Long,
        configKey: Int,
    ) {
        if (proxyNames.isEmpty()) return
        val targetNames = proxyNames.toSet()
        updateRuntimeStateIfCurrent(generation, configKey) { current ->
            val nodes = current.proxies.nodes.map { node ->
                if (node.name in targetNames) node.copy(delay = null) else node
            }
            current.copy(
                proxies = current.proxies.copy(
                    nodes = nodes,
                    nodeByName = nodes.associateBy(MihomoProxyNode::name),
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun MihomoProxiesState.withPreservedDelays(previous: MihomoProxiesState): MihomoProxiesState {
        val previousDelays = previous.nodes
            .mapNotNull { node -> node.delay?.let { delay -> node.name to delay } }
            .toMap()
        if (previousDelays.isEmpty()) return this
        val nodes = nodes.map { node ->
            if (node.delay != null) {
                node
            } else {
                previousDelays[node.name]?.let { delay -> node.copy(delay = delay) } ?: node
            }
        }
        return copy(
            nodes = nodes,
            nodeByName = nodes.associateBy(MihomoProxyNode::name),
        )
    }

    private data class MihomoRuntimeSignature(
        val control: MihomoControlConfig,
        val backend: MihomoRuntimeBackend,
        val trafficEnabled: Boolean,
        val runtimeConfigKey: Int,
    )

    private data class MihomoPreparedRuntimeSignature(
        val backend: MihomoRuntimeBackend,
        val appState: AppState,
    )

    private companion object {
        const val LogTag = "MihomoRuntime"
        const val ProxyFallbackPollIntervalMillis = 10_000L
        const val MaxTrafficHistorySize = 48
    }
}

private enum class MihomoRuntimeBackend {
    Bridge,
    Api,
}

private fun MihomoRuntimeBackend.useBridge(): Boolean {
    return this == MihomoRuntimeBackend.Bridge
}

private fun Throwable.isTransientControlConnectionFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is ConnectException) {
            return true
        }
        current = current.cause
    }
    return false
}

private fun AppState.mihomoRuntimeBackend(): MihomoRuntimeBackend {
    return if (usesRawMihomoConfig() || proxyRunning && runMode.isRootRunMode()) {
        MihomoRuntimeBackend.Api
    } else {
        MihomoRuntimeBackend.Bridge
    }
}

private fun AppState.mihomoRuntimeConfigKey(backend: MihomoRuntimeBackend): Int {
    if (usesRawMihomoConfig()) {
        return listOf(
            selectedMihomoProfileId,
            selectedMihomoProfileOrNull()?.contentSha256,
            selectedMihomoProfileOrNull()?.disableOverrides,
            proxyRunning,
            runMode,
        ).hashCode()
    }
    return when (backend) {
        MihomoRuntimeBackend.Api -> listOf(
            runMode,
            mihomoControlPort,
            mihomoControlSecret,
            selectedMihomoProfileId,
            selectedMihomoProfileOrNull()?.contentSha256,
            selectedMihomoProfileOrNull()?.disableOverrides,
            proxyRunning,
            mihomoMode,
        )

        MihomoRuntimeBackend.Bridge -> listOf(
            exposeBridgePorts(),
            selectedMihomoProfileId,
            mihomoProfiles,
            runMode,
            mihomoMode,
            enableGeodataMode,
            mihomoGeodataLoader,
            resourceFileSource,
            customResourceFileGeoIpUrl,
            customResourceFileGeoSiteUrl,
            customResourceFileMmdbUrl,
            customResourceFileAsnUrl,
            enableSniffer,
            enableSnifferOverrideDestination,
            enableLocalDns,
            enableIpv6,
            overrideDns,
            dnsPreferH3,
            dnsUseHosts,
            dnsUseSystemHosts,
            dnsRespectRules,
            dnsEnhancedMode,
            dnsFakeIpRange,
            dnsFakeIpFilter,
            dnsDefaultNameserver,
            dnsNameserver,
            dnsNameserverPolicy,
            dnsProxyServerNameserver,
            dnsFallback,
            dnsFallbackFilterGeoip,
            dnsFallbackFilterGeoipCode,
            dnsFallbackFilterGeosite,
            dnsFallbackFilterIpcidr,
            dnsFallbackFilterDomain,
            dnsHosts,
            localProxyPort.takeIf { exposeBridgePorts() },
            localProxyListenAllInterfaces.takeIf { exposeBridgePorts() },
            localProxyUsername.takeIf { exposeBridgePorts() },
            localProxyPassword.takeIf { exposeBridgePorts() },
            enableVpnAppendHttpProxy.takeIf { exposeBridgePorts() },
        )
    }.hashCode()
}

private fun AppState.exposeBridgePorts(): Boolean {
    return proxyRunning && !runMode.isRootRunMode()
}
