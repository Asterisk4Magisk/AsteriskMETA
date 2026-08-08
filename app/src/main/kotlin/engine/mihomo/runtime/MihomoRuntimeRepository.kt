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
import engine.root.prepareRootRuntimeLayout
import engine.vpn.AndroidMihomoRuntime
import engine.vpn.VpnMihomoConfigFactory
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
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

internal class MihomoDelayJobSlot {
    private var current: Job? = null

    @Synchronized
    fun tryAcquire(job: Job): Boolean {
        if (current != null) return false
        current = job
        return true
    }

    @Synchronized
    fun release(job: Job) {
        if (current === job) current = null
    }

    @Synchronized
    fun cancel() {
        current?.cancel()
    }
}

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
    private val delayJobSlot = MihomoDelayJobSlot()
    private var activeSignature: MihomoRuntimeSignature? = null
    private var preparedRuntimeSignature: MihomoPreparedRuntimeSignature? = null
    private var runtimeGeneration = 0L
    private var proxySnapshotConfigKey: Int? = null
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
                launch {
                    collectTraffic(
                        control = control,
                        useBridge = backend.useBridge(),
                        generation = generation,
                        configKey = signature.runtimeConfigKey,
                    )
                }
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

    suspend fun getRuleProviderSummaries(
        appState: AppState,
    ): Result<Map<String, MihomoRuleProviderRuntimeSummary>> {
        return runMihomoRuntimeCatching {
            if (!appState.hasUsableMihomoProfile()) {
                error("Mihomo profile is not configured")
            }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                client.getRuleProviders(control, backend.useBridge())
            }
        }
    }

    suspend fun reloadInteractiveProfileFromDisk(appState: AppState): Result<Unit> {
        return runCatching {
            require(appState.hasUsableMihomoProfile()) { "Mihomo profile is not configured" }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                client.reloadProfile(
                    config = control,
                    profilePath = appContext.prepareRootRuntimeLayout().configPath,
                    reloadLocally = backend.useBridge() || appState.runMode == RunModeVpnService,
                )
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
        return runMihomoRuntimeCatching {
            if (!appState.hasUsableMihomoProfile()) {
                return@runMihomoRuntimeCatching
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

    suspend fun refreshRuleProvider(
        appState: AppState,
        providerName: String,
    ): Result<Unit> {
        return runMihomoRuntimeCatching {
            if (!appState.hasUsableMihomoProfile()) {
                return@runMihomoRuntimeCatching
            }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                client.updateRuleProvider(control, providerName, backend.useBridge())
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
        proxyId: MihomoProxyNodeId,
        testUrl: String = engine.mihomo.DefaultMihomoDelayTestUrl,
        expectedStatus: String = "",
    ): Result<MihomoDelayResult> {
        return startDelayTest(MihomoDelayTarget.Node(proxyId)) {
            runProxyDelayTest(appState, proxyId, testUrl, expectedStatus)
        }.await()
    }

    suspend fun testGroupDelay(
        appState: AppState,
        groupName: String,
        testUrl: String,
    ): Result<MihomoDelayResult> {
        return startDelayTest(MihomoDelayTarget.Group(groupName)) {
            runGroupDelayTest(appState, groupName, testUrl)
        }.await()
    }

    suspend fun testProviderDelay(
        appState: AppState,
        providerName: String,
        testUrl: String,
        expectedStatus: String,
    ): Result<MihomoDelayResult> {
        return startDelayTest(MihomoDelayTarget.Provider(providerName)) {
            runProviderDelayTest(appState, providerName, testUrl, expectedStatus)
        }.await()
    }

    private fun startDelayTest(
        target: MihomoDelayTarget,
        block: suspend () -> MihomoDelayResult,
    ): Deferred<Result<MihomoDelayResult>> {
        val result = CompletableDeferred<Result<MihomoDelayResult>>()
        lateinit var job: Job
        job = appScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            mutableState.update { current -> current.copy(delayTestingTarget = target) }
            try {
                result.complete(runCatching { block() })
            } finally {
                mutableState.update { current -> current.clearDelayTestingTarget(target) }
            }
        }
        if (!delayJobSlot.tryAcquire(job)) {
            job.cancel()
            return CompletableDeferred(Result.failure(IllegalStateException("Mihomo delay test is already running")))
        }
        job.invokeOnCompletion { error ->
            delayJobSlot.release(job)
            if (!result.isCompleted && error != null) {
                result.complete(Result.failure(error))
            }
        }
        job.start()
        return result
    }

    private fun cancelDelayTest() {
        delayJobSlot.cancel()
    }

    private suspend fun runProxyDelayTest(
        appState: AppState,
        proxyId: MihomoProxyNodeId,
        testUrl: String,
        expectedStatus: String,
    ): MihomoDelayResult {
        val control = resolveMihomoControlConfig(appState)
        val backend = resolveInteractiveBackend(appState, control)
        val generation = currentRuntimeGeneration()
        val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend())
        return withContext(Dispatchers.IO) {
            ensureInteractiveRuntime(appState, backend)
            val groupFallbackName = state.value.proxies.groupFallbackFor(proxyId)
            client.testProxyDelay(
                config = control,
                proxyId = proxyId,
                url = testUrl.ifBlank { engine.mihomo.DefaultMihomoDelayTestUrl },
                expectedStatus = expectedStatus,
                groupFallbackName = groupFallbackName,
                useBridge = backend.useBridge(),
            ).also { result ->
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
            val expectedProxyIds = state.value.proxies.groups
                .firstOrNull { group -> group.name == groupName }
                ?.all
                .orEmpty()
            clearDelays(expectedProxyIds, generation, configKey)
            client.testGroupDelay(
                config = control,
                groupName = groupName,
                url = testUrl.ifBlank { engine.mihomo.DefaultMihomoDelayTestUrl },
                expectedProxyIds = expectedProxyIds,
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

    private suspend fun runProviderDelayTest(
        appState: AppState,
        providerName: String,
        testUrl: String,
        expectedStatus: String,
    ): MihomoDelayResult {
        val control = resolveMihomoControlConfig(appState)
        val backend = resolveInteractiveBackend(appState, control)
        val generation = currentRuntimeGeneration()
        val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend())
        return withContext(Dispatchers.IO) {
            ensureInteractiveRuntime(appState, backend)
            client.testProviderDelay(
                config = control,
                providerName = providerName,
                url = testUrl.ifBlank { engine.mihomo.DefaultMihomoDelayTestUrl },
                expectedStatus = expectedStatus,
                useBridge = backend.useBridge(),
            ).also { result ->
                applyDelays(result, generation, configKey)
                refreshProxySnapshot(control, backend.useBridge(), generation, configKey, coalesce = false)
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
        generation: Long,
        configKey: Int,
    ) {
        runCatching {
            client.traffic(control, useBridge).collect { sample ->
                if (!isRuntimeRequestCurrent(generation, configKey)) {
                    throw CancellationException("Stale Mihomo traffic collector")
                }
                synchronized(trafficHistoryLock) { trafficHistory.append(sample) }
                val updated = updateRuntimeStateIfCurrent(generation, configKey) { current ->
                    current.copy(
                        traffic = current.traffic.copy(
                            latest = sample,
                            totalUp = sample.totalUp ?: saturatedTrafficAdd(current.traffic.totalUp, sample.up),
                            totalDown = sample.totalDown ?: saturatedTrafficAdd(current.traffic.totalDown, sample.down),
                            connected = true,
                        ),
                    )
                }
                if (!updated) throw CancellationException("Stale Mihomo traffic collector")
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            reportRuntimeError("Mihomo API /traffic", error)
            updateRuntimeStateIfCurrent(generation, configKey) { current ->
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
        if (result.measurements.isEmpty()) return
        updateRuntimeStateIfCurrent(generation, configKey) { current ->
            current.copy(
                proxies = current.proxies.withDelayResult(result),
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
        proxyIds: List<MihomoProxyNodeId>,
        generation: Long,
        configKey: Int,
    ) {
        if (proxyIds.isEmpty()) return
        val targetIds = proxyIds.toSet()
        updateRuntimeStateIfCurrent(generation, configKey) { current ->
            val nodes = current.proxies.nodes.map { node ->
                if (node.id in targetIds) {
                    node.copy(delay = null, delayStatus = null, delayError = "")
                } else {
                    node
                }
            }
            current.copy(
                proxies = current.proxies.copy(
                    nodes = nodes,
                    nodeById = nodes.associateBy(MihomoProxyNode::id),
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun MihomoProxiesState.withPreservedDelays(previous: MihomoProxiesState): MihomoProxiesState {
        val previousDelayStates = previous.nodes
            .filter { node -> node.delayStatus != null }
            .associateBy(MihomoProxyNode::id)
        if (previousDelayStates.isEmpty()) return this
        val nodes = nodes.map { node ->
            if (node.delayStatus != null) {
                node
            } else {
                previousDelayStates[node.id]?.let { previousNode ->
                    node.copy(
                        delay = previousNode.delay,
                        delayStatus = previousNode.delayStatus,
                        delayError = previousNode.delayError,
                    )
                } ?: node
            }
        }
        return copy(
            nodes = nodes,
            nodeById = nodes.associateBy(MihomoProxyNode::id),
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
        const val ProxyFallbackPollIntervalMillis = 30_000L
        const val MaxTrafficHistorySize = 48
    }
}

private fun saturatedTrafficAdd(previous: Long, increment: Long): Long {
    if (previous < 0L || increment <= 0L) return previous.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - previous < increment) Long.MAX_VALUE else previous + increment
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
