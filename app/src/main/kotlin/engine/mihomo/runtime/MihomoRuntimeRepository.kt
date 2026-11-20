// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import engine.root.dns.withRootDnsMutation
import android.content.Context
import app.AppState
import app.ruleOverrideRuntimeProjection
import app.modes.RunModeVpnService
import app.modes.isRootRunMode
import engine.mihomo.hasUsableMihomoProfile
import engine.mihomo.loadMihomoProfileObject
import engine.mihomo.mihomoProxyServers
import engine.mihomo.mihomoLogLevelName
import engine.mihomo.mihomoControlConfig
import engine.mihomo.mihomoControllerSocket
import engine.network.ProxyIpProbeException
import engine.network.ProxyIpProbeRequest
import engine.network.ProxyIpProbeResponse
import engine.network.readOrCreateProxyIpProbeToken
import engine.mihomo.selectedMihomoProfileOrNull
import engine.mihomo.toMihomoAutoSwitchOptions
import engine.mihomo.MihomoControlConfig
import engine.mihomo.raw.loadSelectedRawConfig
import engine.mihomo.raw.usesRawMihomoConfig
import engine.proxy.ProxyEngineStartRequest
import engine.root.publication.publishRootProviderUpdates
import engine.vpn.VpnSession
import engine.vpn.AndroidMihomoRuntime
import engine.vpn.AsteriskVpnService
import engine.vpn.VpnMihomoConfigFactory
import engine.vpn.vpnSessionOverride
import features.resources.runtime.mihomoResourceFilePaths
import features.resources.runtime.mihomoResourceFilesDir
import features.resources.runtime.skipsBundledCoreConfigCheck
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import kotlin.time.Duration.Companion.milliseconds
import app.effects.MihomoRuntimeLifecycleTarget

/**
 * Rate limit for background probes, per node: the same node is probed at most once per
 * [intervalMillis], while a different node is always let through. Lives with the runtime, not the
 * UI, so recreating the activity does not reset it.
 */
internal class MihomoDelayProbeThrottle(private val intervalMillis: Long) {
    private var lastTarget: MihomoProxyNodeId? = null
    private var lastAtMillis = 0L

    @Synchronized
    fun take(target: MihomoProxyNodeId, nowMillis: Long): Boolean {
        if (target == lastTarget && nowMillis - lastAtMillis < intervalMillis) return false
        lastTarget = target
        lastAtMillis = nowMillis
        return true
    }
}

internal class MihomoRuntimeRepository(
    private val appScope: CoroutineScope,
    context: Context,
    private val client: MihomoControlClient = MihomoControlClient(),
) : MihomoRuntimeLifecycleTarget {
    private var supervisedRestart: (suspend (AppState) -> Unit)? = null

    fun bindSupervisedRestart(restart: suspend (AppState) -> Unit) {
        supervisedRestart = restart
    }
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(MihomoRuntimeState())
    private val trafficHistory = MihomoTrafficHistoryBuffer(TrafficHistoryCapacity)
    private val trafficHistoryLock = Any()
    private val runtimeStateLock = Any()
    private val runtimePreparationMutex = Mutex()
    private val proxySelectionMutex = Mutex()
    private val proxyRefreshMutex = Mutex()
    private val delayJobs = MihomoDelayJobs()
    private var monitorJob: Job? = null
    private val delayProbeThrottle = MihomoDelayProbeThrottle(DelayProbeThrottleMillis)
    private var activeSignature: MihomoRuntimeSignature? = null
    private var preparedRuntimeSignature: MihomoPreparedRuntimeSignature? = null
    private var runtimeGeneration = 0L
    private var proxySnapshotConfigKey: Int? = null
    private var proxySnapshotRevision = 0L
    private var proxySelectionAppliedRevision = 0L
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
        val backend = appState.mihomoRuntimeBackend(appContext)
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
                    runtimeEpoch = current.runtimeEpoch + 1L,
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
        if (resetSnapshots) {
            synchronized(trafficHistoryLock) { trafficHistory.clear() }
        }
        synchronized(runtimeStateLock) {
            runtimeGeneration += 1L
            delayJobs.cancel()
            activeSignature = null
            if (resetSnapshots) proxySnapshotConfigKey = null
            mutableState.update { current ->
                if (resetSnapshots) {
                    MihomoRuntimeState(
                        runtimeEpoch = current.runtimeEpoch,
                        device = current.device,
                    )
                } else {
                    current.copy(
                        running = false,
                        traffic = current.traffic.copy(connected = false),
                        proxiesRefreshing = false,
                        delayTestingTarget = null,
                        delayTestingPending = emptySet(),
                        delayTestingTotal = 0,
                        groupDelayTesting = emptyMap(),
                        nodeDelayTesting = emptySet(),
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
            val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend(appContext))
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
            val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend(appContext))
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                refreshProxySnapshot(
                    control = control,
                    useBridge = backend.useBridge(),
                    generation = generation,
                    configKey = configKey,
                    includeProviderNodes = true,
                ).getOrThrow()
            }
        }
    }

    /** Reads the active node from a running service with no standby core or monitor. */
    suspend fun readRunningProxyNodeName(appState: AppState): String? = withContext(Dispatchers.IO) {
        val control = resolveMihomoControlConfig(appState.copy(proxyRunning = true))
        val mode = client.getConfigs(control, useBridge = false).mode
        if (mode.equals("direct", ignoreCase = true)) return@withContext null
        client.getProxies(control, useBridge = false, mode = mode).activeNodeId()?.name?.takeIf(String::isNotBlank)
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
            require(
                !MihomoRuntimeMutation.ProfileReload.requiresSupervisedRestart(appState.runMode),
            ) {
                "ROOT runtime configuration changes require a supervised restart"
            }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                try {
                    appContext.withRootDnsMutation(appState) {
                        if (appState.runMode.isRootRunMode() && backend == MihomoRuntimeBackend.Api && !appState.usesRawMihomoConfig()) {
                            appContext.publishRootProviderUpdates()
                        }
                        if (appState.runMode == RunModeVpnService && backend == MihomoRuntimeBackend.Api) {
                            AsteriskVpnService.updateAgeSecretKey(
                                appContext,
                                appState.selectedMihomoProfileOrNull()?.ageSecretKey,
                            )
                        }
                        // Raw provider sync updates the active providers; configuration changes
                        // use the explicit restart path with their session controller overrides.
                        if (!appState.usesRawMihomoConfig()) {
                            mutableState.update { it.copy(runtimeEpoch = it.runtimeEpoch + 1L) }
                            client.reloadProfile(
                                config = control,
                                profilePath = publishedProfileFile().absolutePath,
                                reloadLocally = backend.useBridge(),
                            )
                        }
                        // Resume ROOT DNS after the mutation even when a provider is offline.
                        runCatching { client.updateHttpProxyProviders(control, backend.useBridge()) }
                    }.getOrThrow()
                } catch (error: CoreRestartRequiredException) {
                    if (!appState.runMode.isRootRunMode()) throw error
                    AndroidAppLogger.info(
                        LogTag,
                        "ROOT configuration reload needs a supervised restart: ${error.fields.joinToString(",")}",
                    )
                    val restart = supervisedRestart ?: throw error
                    restart(appState)
                }
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
                client.getConnections(control, backend.useBridge(), state.value.proxies)
            }
        }
    }

    suspend fun getConnectionCounts(appState: AppState): Result<ConnectionCounts> {
        return runCatching {
            require(appState.proxyRunning) { "Proxy service is not running" }
            require(appState.hasUsableMihomoProfile()) { "Mihomo profile is not configured" }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                client.getConnectionCounts(control, backend.useBridge(), state.value.proxies)
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

    suspend fun closeConnectionsForGroup(appState: AppState, groupName: String): Result<Unit> {
        return runMihomoRuntimeCatching {
            require(appState.proxyRunning) { "Proxy service is not running" }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                val useBridge = backend.useBridge()
                val connections = client.getConnections(control, useBridge).connections
                closeMihomoConnectionsForGroup(connections, groupName) { connectionId ->
                    client.closeConnection(control, connectionId, useBridge)
                }
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
            val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend(appContext))
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                appContext.withRootDnsMutation(appState) {
                    client.updateProxyProvider(control, providerName, backend.useBridge())
                }
                refreshProxySnapshot(
                    control, backend.useBridge(), generation, configKey,
                    coalesce = false,
                    includeProviderNodes = true,
                )
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
                appContext.withRootDnsMutation(appState) {
                    client.updateRuleProvider(control, providerName, backend.useBridge())
                }
            }
        }
    }

    suspend fun patchMode(
        appState: AppState,
        mode: String,
    ): Result<Unit> {
        return runCatching {
            require(
                !MihomoRuntimeMutation.Mode.requiresSupervisedRestart(appState.runMode),
            ) {
                "ROOT runtime configuration changes require a supervised restart"
            }
            val control = resolveMihomoControlConfig(appState)
            val backend = resolveInteractiveBackend(appState, control)
            val generation = currentRuntimeGeneration()
            val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend(appContext))
            withContext(Dispatchers.IO) {
                ensureInteractiveRuntime(appState, backend)
                appContext.withRootDnsMutation(appState) {
                    client.patchMode(control, mode, backend.useBridge())
                }
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
            require(
                !MihomoRuntimeMutation.LogLevel.requiresSupervisedRestart(appState.runMode),
            ) {
                "ROOT runtime configuration changes require a supervised restart"
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
            val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend(appContext))
            synchronized(runtimeStateLock) {
                mutableState.update { current ->
                    current.copy(proxySelectionRevision = current.proxySelectionRevision + 1L)
                }
            }
            // Acquire on the caller's dispatcher so rapid taps keep their submission order.
            proxySelectionMutex.withLock {
                withContext(Dispatchers.IO) {
                    ensureInteractiveRuntime(appState, backend)
                    check(isRuntimeRequestCurrent(generation, configKey)) { "Mihomo runtime changed before selection" }
                    appContext.withRootDnsMutation(appState) {
                        client.selectProxy(control, groupName, proxyName, backend.useBridge())
                    }
                    applySelectedProxy(groupName, proxyName, generation, configKey)
                }
            }
            appScope.launch(Dispatchers.IO) {
                refreshProxySnapshot(control, backend.useBridge(), generation, configKey)
            }
            refreshConnectivity()
        }
    }

    fun refreshConnectivity() {
        appScope.launch(Dispatchers.IO) {
            refreshConnectivityState()
        }
    }

    suspend fun usesBundledCore(runMode: Int): Boolean = withContext(Dispatchers.IO) {
        !appContext.skipsBundledCoreConfigCheck(runMode)
    }

    suspend fun proxyEntryHosts(appState: AppState, node: MihomoProxyNodeId): List<String> =
        withContext(Dispatchers.IO) {
            if (node.fallbackGroupName != null) return@withContext emptyList()
            val file = publishedProfileFile()
            val profile = file.readText().loadMihomoProfileObject() ?: return@withContext emptyList()
            profile.mihomoProxyServers(
                nodeName = node.name,
                providerName = node.providerName,
                dataDir = checkNotNull(file.parentFile),
                rootRuntime = appState.runMode.isRootRunMode(),
                profileAgeSecretKey = appState.selectedMihomoProfileOrNull()?.ageSecretKey.orEmpty(),
            )
        }

    suspend fun probeProxyIp(appState: AppState, request: ProxyIpProbeRequest): ProxyIpProbeResponse =
        withContext(Dispatchers.IO) {
            val epoch = state.value.runtimeEpoch
            val control = localProxyIpProbeControl(appState) ?: throw ProxyIpProbeException("UNSUPPORTED")
            val token = readOrCreateProxyIpProbeToken(appContext.mihomoResourceFilesDir())
            if (state.value.runtimeEpoch != epoch || !state.value.running) throw ProxyIpProbeException("RUNTIME_CHANGED")
            val result = client.probeProxyIp(control, request, token)
            if (state.value.runtimeEpoch != epoch || !state.value.running) throw ProxyIpProbeException("RUNTIME_CHANGED")
            result
        }

    private fun localProxyIpProbeControl(appState: AppState): MihomoControlConfig? {
        if (!appState.proxyRunning || appContext.skipsBundledCoreConfigCheck(appState.runMode)) return null
        if (appState.runMode == RunModeVpnService) {
            if (!VpnSession.isRunning(appContext)) return null
            if (!appState.usesRawMihomoConfig()) return appState.mihomoControlConfig(appContext)
            val raw = appContext.loadSelectedRawConfig(appState)?.snapshot ?: return null
            val override = raw.controller.vpnSessionOverride(appContext.mihomoControllerSocket())
            val path = override.externalControllerUnix ?: return null
            if (File(path).canonicalFile.parentFile != appContext.mihomoResourceFilesDir().canonicalFile) return null
            return MihomoControlConfig(secret = override.secret.orEmpty(), unixSocketPath = path)
        }
        return runCatching { resolveMihomoControlConfig(appState) }.getOrNull()
            ?.takeIf { it.host == "127.0.0.1" || it.host == "::1" }
    }

    // Auto-switch runs inside the service core; these never prepare a standby runtime.
    suspend fun autoSwitchState(appState: AppState): Result<MihomoAutoSwitchState> = runMihomoRuntimeCatching {
        require(appState.proxyRunning) { "Automatic switching runs only while the proxy service is running" }
        if (!usesBundledCore(appState.runMode)) throw MihomoAutoSwitchUnsupportedException()
        val control = resolveMihomoControlConfig(appState)
        val backend = resolveInteractiveBackend(appState, control)
        withContext(Dispatchers.IO) {
            client.getAutoSwitchState(control, backend.useBridge())
        }
    }

    /** Hot-applies saved settings; a raw profile keeps its own asterisk-auto-switch value. */
    suspend fun syncAutoSwitch(appState: AppState): Result<Unit> = runMihomoRuntimeCatching {
        if (!appState.proxyRunning || appState.usesRawMihomoConfig() || !usesBundledCore(appState.runMode)) {
            return@runMihomoRuntimeCatching
        }
        val control = resolveMihomoControlConfig(appState)
        val useBridge = resolveInteractiveBackend(appState, control).useBridge()
        val desired = appState.toMihomoAutoSwitchOptions()
        withContext(Dispatchers.IO) {
            val current = client.getAutoSwitchState(control, useBridge).settings
            // Re-patching identical settings would restart the core's failure counting.
            if (!current.sameAs(desired)) {
                client.patchAutoSwitch(control, desired, useBridge)
            }
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

    fun focusDelayTestGroup(groupName: String?) {
        client.focusDelayTestOwner(groupName)
    }

    suspend fun testProxyDelay(
        appState: AppState,
        proxyId: MihomoProxyNodeId,
        testUrl: String = "",
        expectedStatus: String = "",
    ): Result<MihomoDelayResult> {
        val shared = state.value.proxies.canShareProxyDelay(proxyId)
        return startDelayTest(
            target = MihomoDelayTarget.Node(proxyId),
            exclusive = !shared,
            nodeId = proxyId.takeIf { shared },
        ) {
            runProxyDelayTest(appState, proxyId, testUrl, expectedStatus, shared)
        }.await()
    }

    /**
     * Background latency refresh for the node carrying traffic. It is skipped while any test runs,
     * shares slots with later single-node tests, and yields to group or provider batches. A node
     * probed within [DelayProbeThrottleMillis] is skipped, so a start followed by a résumé, or a
     * quick back-and-forth between apps, tests once.
     */
    suspend fun probeProxyDelay(
        appState: AppState,
        proxyId: MihomoProxyNodeId,
        nowMillis: Long,
    ) {
        if (!delayProbeThrottle.take(proxyId, nowMillis)) return
        val shared = state.value.proxies.canShareProxyDelay(proxyId)
        val probe = startDelayTest(
            target = MihomoDelayTarget.Node(proxyId),
            exclusive = !shared,
            preemptible = true,
        ) {
            runProxyDelayTest(appState, proxyId, testUrl = "", expectedStatus = "", shared = shared)
        }
        try {
            probe.await()
        } catch (error: CancellationException) {
            probe.cancel()
            throw error
        }
    }

    suspend fun testGroupDelay(
        appState: AppState,
        groupName: String,
        testUrl: String,
    ): Result<MihomoDelayResult> {
        return startDelayTest(
            groupName = groupName,
            exclusive = state.value.proxies.individualDelayTests(groupName) == null,
        ) {
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

    private suspend fun startDelayTest(
        target: MihomoDelayTarget? = null,
        groupName: String? = null,
        exclusive: Boolean = true,
        preemptible: Boolean = false,
        nodeId: MihomoProxyNodeId? = null,
        block: suspend () -> MihomoDelayResult,
    ): Deferred<Result<MihomoDelayResult>> {
        if (!preemptible && (groupName != null || exclusive)) {
            // Batches and core-scheduled group fallbacks take over from a background probe.
            delayJobs.preemptibleJob()?.cancelAndJoin()
        }
        currentCoroutineContext().ensureActive()
        val generation = currentRuntimeGeneration()
        val result = CompletableDeferred<Result<MihomoDelayResult>>()
        var outcome: Result<MihomoDelayResult>? = null
        val job = appScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            outcome = runMihomoRuntimeCatching {
                check(currentRuntimeGeneration() == generation) { "Mihomo runtime changed before its delay test" }
                block()
            }
        }
        synchronized(runtimeStateLock) {
            if (!delayJobs.tryAcquire(job, groupName, exclusive, preemptible, nodeId)) {
                job.cancel()
                return CompletableDeferred(Result.failure(IllegalStateException("Mihomo delay test is already running")))
            }
            mutableState.update { current ->
                if (groupName != null) {
                    current.copy(
                        groupDelayTesting = current.groupDelayTesting +
                            (groupName to MihomoDelayProgress(exclusive = exclusive)),
                    )
                } else if (nodeId != null) {
                    current.copy(nodeDelayTesting = current.nodeDelayTesting + nodeId)
                } else {
                    current.copy(
                        delayTestingTarget = target,
                        delayTestingAutomatic = preemptible,
                        delayTestingExclusive = exclusive,
                        delayTestingPending = emptySet(),
                        delayTestingTotal = 0,
                    )
                }
            }
            job.invokeOnCompletion { error ->
                synchronized(runtimeStateLock) {
                    if (delayJobs.release(job)) {
                        mutableState.update { current ->
                            if (groupName != null) {
                                current.copy(groupDelayTesting = current.groupDelayTesting - groupName)
                            } else if (nodeId != null) {
                                current.copy(nodeDelayTesting = current.nodeDelayTesting - nodeId)
                            } else {
                                current.copy(
                                    delayTestingTarget = null,
                                    delayTestingPending = emptySet(),
                                    delayTestingTotal = 0,
                                )
                            }
                        }
                    }
                }
                result.complete(outcome ?: Result.failure(error ?: CancellationException("Mihomo delay test cancelled")))
            }
            result.invokeOnCompletion { if (result.isCancelled) job.cancel() }
        }
        job.start()
        return result
    }

    private suspend fun runProxyDelayTest(
        appState: AppState,
        proxyId: MihomoProxyNodeId,
        testUrl: String,
        expectedStatus: String,
        shared: Boolean = false,
    ): MihomoDelayResult {
        return runInteractiveDelayTest(appState, sharedNodeTest = shared) { control, useBridge, onStarted, _ ->
            onStarted(listOf(proxyId))
            val proxies = state.value.proxies
            val measuredId = proxies.delayTestLeaf(proxyId)
                ?: error("Cannot resolve the selected leaf for ${proxyId.name}")
            val groupFallbackName = measuredId.fallbackGroupName
            check(!shared || groupFallbackName == null) { "Mihomo node changed before its delay test" }
            val url = testUrl.ifBlank { appState.connectionTestUrl }
            val result = if (groupFallbackName == null) {
                client.sharedProxyDelay(
                    config = control,
                    proxyId = measuredId,
                    url = url,
                    timeoutMillis = appState.connectionTestTimeoutMillis,
                    expectedStatus = expectedStatus,
                    concurrency = appState.connectionTestConcurrent,
                    useBridge = useBridge,
                )
            } else {
                client.testProxyDelay(
                    config = control,
                    proxyId = measuredId,
                    url = url,
                    timeoutMillis = appState.connectionTestTimeoutMillis,
                    expectedStatus = expectedStatus,
                    groupFallbackName = groupFallbackName,
                    useBridge = useBridge,
                )
            }
            result.withResultId(proxyId)
        }
    }

    private suspend fun runGroupDelayTest(
        appState: AppState,
        groupName: String,
        testUrl: String,
    ): MihomoDelayResult {
        return runInteractiveDelayTest(appState, progressGroupName = groupName) { control, useBridge, onStarted, onResult ->
            val proxies = state.value.proxies
            val group = proxies.groups.firstOrNull { group -> group.name == groupName }
            val expectedProxyIds = group?.all.orEmpty()
            val individualTests = proxies.individualDelayTests(groupName)
            // A queued request may outlive a selector/profile change. Never enter a native group
            // test under a slot acquired for ordinary shared work.
            val exclusive = state.value.groupDelayTesting[groupName]?.exclusive == true
            check(exclusive || individualTests != null) { "Mihomo group changed before its delay test started" }
            client.testGroupDelay(
                config = control,
                groupName = groupName,
                url = testUrl.ifBlank { appState.connectionTestUrl },
                timeoutMillis = appState.connectionTestTimeoutMillis,
                expectedProxyIds = expectedProxyIds,
                concurrency = appState.connectionTestConcurrent,
                owner = groupName,
                individualTests = individualTests.orEmpty(),
                onStarted = onStarted,
                onResult = onResult,
                useBridge = useBridge,
            )
        }
    }

    private fun prepareRuntime(
        appState: AppState,
        backend: MihomoRuntimeBackend,
    ) {
        when (backend) {
            MihomoRuntimeBackend.Bridge -> {
                if (appState.runMode == RunModeVpnService && VpnSession.isRunning(appContext)) {
                    AndroidMihomoRuntime.releaseStandby()
                    throw ConnectException("VPN session owns the Mihomo runtime")
                }
                val newlyLoaded = AndroidMihomoRuntime.ensureLoaded(
                    context = appContext,
                    config = VpnMihomoConfigFactory.create(
                        context = appContext,
                        request = ProxyEngineStartRequest(appState),
                        exposePorts = appState.exposeBridgePorts(),
                    ),
                )
                if (newlyLoaded) {
                    appScope.launch(Dispatchers.IO) {
                        try {
                            client.updateHttpProxyProviders(appState.mihomoControlConfig(appContext), useBridge = true)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            AndroidAppLogger.warn(LogTag, "Failed to refresh Standby provider usage", error)
                        }
                    }
                }
            }

            MihomoRuntimeBackend.Api -> {
                AndroidMihomoRuntime.releaseStandby()
            }
        }
    }

    private fun resolveMihomoControlConfig(appState: AppState): MihomoControlConfig {
        if (!appState.usesRawMihomoConfig()) return appState.mihomoControlConfig(appContext)
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
        val backend = appState.mihomoRuntimeBackend(appContext)
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
            currentCoroutineContext().ensureActive()
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
        return runInteractiveDelayTest(appState) { control, useBridge, onStarted, onResult ->
            client.testProviderDelay(
                config = control,
                providerName = providerName,
                concurrency = appState.connectionTestConcurrent,
                url = testUrl.ifBlank { appState.connectionTestUrl },
                timeoutMillis = appState.connectionTestTimeoutMillis,
                expectedStatus = expectedStatus,
                onStarted = onStarted,
                onResult = onResult,
                useBridge = useBridge,
            )
        }
    }

    /** Finish publishing measurements before releasing the test slot. Reconcile snapshots separately. */
    private suspend fun runInteractiveDelayTest(
        appState: AppState,
        progressGroupName: String? = null,
        sharedNodeTest: Boolean = false,
        test: suspend (
            control: MihomoControlConfig,
            useBridge: Boolean,
            onStarted: (List<MihomoProxyNodeId>) -> Unit,
            onResult: (MihomoDelayResult) -> Unit,
        ) -> MihomoDelayResult,
    ): MihomoDelayResult {
        val control = resolveMihomoControlConfig(appState)
        val backend = resolveInteractiveBackend(appState, control)
        val generation = currentRuntimeGeneration()
        val configKey = appState.mihomoRuntimeConfigKey(appState.mihomoRuntimeBackend(appContext))
        return withContext(Dispatchers.IO) {
            ensureInteractiveRuntime(appState, backend)
            currentCoroutineContext().ensureActive()
            check(isRuntimeRequestCurrent(generation, configKey)) { "Mihomo runtime changed before its delay test" }
            fun started(ids: List<MihomoProxyNodeId>) {
                if (!sharedNodeTest) updateDelayTestProgress(generation, configKey, ids, progressGroupName)
            }
            fun completed(result: MihomoDelayResult) {
                applyDelayTestResult(result, generation, configKey, progressGroupName, sharedNodeTest)
            }
            suspend fun runTest(onResult: (MihomoDelayResult) -> Unit): MihomoDelayResult =
                test(control, backend.useBridge(), ::started, onResult)
            val result = if (progressGroupName != null) {
                publishBatchedMihomoDelayResults(::completed, ::runTest)
            } else {
                publishMihomoDelayResults(::completed, ::runTest)
            }
            result.also {
                appScope.launch(Dispatchers.IO) {
                    refreshProxySnapshot(control, backend.useBridge(), generation, configKey)
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
        return signatureMatches && when (signature.backend) {
            MihomoRuntimeBackend.Bridge -> AndroidMihomoRuntime.isLoaded() &&
                !(appState.runMode == RunModeVpnService && VpnSession.isRunning(appContext))
            MihomoRuntimeBackend.Api -> {
                appState.runMode == RunModeVpnService && VpnSession.isRunning(appContext) ||
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
        refreshProxySnapshot(
            control, useBridge, generation, configKey, includeProviderNodes = true,
        )
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

    private fun publishedProfileFile(): File = File(appContext.mihomoResourceFilePaths().dataDir, "config.yaml")

    private suspend fun loadProxyGroupSources(useBridge: Boolean): Map<String, MihomoProxyGroupSources> {
        return runRuntimeRequest {
            val content = if (useBridge) {
                AndroidMihomoRuntime.loadedProfileContent()
            } else {
                publishedProfileFile().readText()
            }
            content?.mihomoProxyGroupSources().orEmpty()
        }.getOrElse {
            AndroidAppLogger.warn(LogTag, "Group provider sources are unavailable")
            emptyMap()
        }
    }

    private suspend fun refreshProxySnapshot(
        control: MihomoControlConfig,
        useBridge: Boolean,
        generation: Long,
        configKey: Int,
        coalesce: Boolean = true,
        includeProviderNodes: Boolean = false,
    ): Result<Unit> {
        val observedSnapshotRevision = synchronized(runtimeStateLock) { proxySnapshotRevision }
        return proxyRefreshMutex.withLock {
            if (!isRuntimeRequestCurrent(generation, configKey)) return@withLock Result.success(Unit)
            val refreshedByAnotherRequest = synchronized(runtimeStateLock) {
                proxySnapshotConfigKey == configKey && proxySnapshotRevision > observedSnapshotRevision
            }
            if (coalesce && !includeProviderNodes && refreshedByAnotherRequest) return@withLock Result.success(Unit)
            val observedSelectionRevision = synchronized(runtimeStateLock) { proxySelectionAppliedRevision }

            updateRuntimeStateIfCurrent(generation, configKey) { current ->
                current.copy(proxiesRefreshing = true)
            }
            try {
                val configs = runRuntimeRequest { client.getConfigs(control, useBridge) }
                val mode = configs.getOrNull()?.mode ?: state.value.configs.mode
                val proxies = runRuntimeRequest {
                    val groupSources = if (includeProviderNodes) loadProxyGroupSources(useBridge) else emptyMap()
                    client.getProxies(control, useBridge, mode, includeProviderNodes, groupSources)
                }
                val committed = synchronized(runtimeStateLock) {
                    if (!isRuntimeRequestCurrentLocked(generation, configKey)) {
                        false
                    } else {
                        val refreshedProxies = proxies.getOrNull()
                        if (refreshedProxies != null) {
                            proxySnapshotConfigKey = configKey
                            proxySnapshotRevision += 1L
                        }
                        mutableState.update { current ->
                            val reconciledProxies = refreshedProxies?.let { snapshot ->
                                (if (includeProviderNodes) snapshot else snapshot.withPreservedProviderNodes(current.proxies))
                                    .withPreservedDelays(current.proxies)
                                    .withPreservedSelections(
                                        current.proxies,
                                        preserve = proxySelectionAppliedRevision != observedSelectionRevision,
                                    )
                            }
                            current.copy(
                                running = true,
                                control = control,
                                configs = configs.getOrNull() ?: current.configs,
                                proxies = reconciledProxies ?: current.proxies,
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

    private fun updateDelayTestProgress(
        generation: Long,
        configKey: Int,
        ids: List<MihomoProxyNodeId>,
        groupName: String?,
    ) {
        val pending = ids.toSet()
        updateRuntimeStateIfCurrent(generation, configKey) { current ->
            if (groupName == null) {
                current.copy(delayTestingPending = pending, delayTestingTotal = pending.size)
            } else {
                val existing = current.groupDelayTesting[groupName]
                current.copy(
                    groupDelayTesting = current.groupDelayTesting +
                        (groupName to MihomoDelayProgress(pending, pending.size, existing?.exclusive == true)),
                )
            }
        }
    }

    private fun applyDelayTestResult(
        result: MihomoDelayResult,
        generation: Long,
        configKey: Int,
        groupName: String?,
        sharedNodeTest: Boolean,
    ) {
        if (result.measurements.isEmpty()) return
        updateRuntimeStateIfCurrent(generation, configKey) { current ->
            if (sharedNodeTest) {
                current.copy(proxies = current.proxies.withDelayResult(result))
            } else if (groupName == null) {
                current.copy(
                    proxies = current.proxies.withDelayResult(result),
                    delayTestingPending = current.delayTestingPending - result.measurements.keys,
                )
            } else {
                val progress = current.groupDelayTesting[groupName] ?: MihomoDelayProgress()
                current.copy(
                    proxies = current.proxies.withDelayResult(result),
                    groupDelayTesting = current.groupDelayTesting +
                        (groupName to progress.copy(pending = progress.pending - result.measurements.keys)),
                )
            }
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
        synchronized(runtimeStateLock) {
            if (!isRuntimeRequestCurrentLocked(generation, configKey)) return
            proxySelectionAppliedRevision += 1L
            fun selected(group: MihomoProxyGroup) =
                if (group.name == groupName) group.copy(now = proxyName) else group
            mutableState.update { current ->
                current.copy(
                    proxies = current.proxies.copy(
                        groups = current.proxies.groups.map(::selected),
                        allGroups = current.proxies.allGroups.map(::selected),
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
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
        const val DelayProbeThrottleMillis = 30_000L
        const val LogTag = "MihomoRuntime"
        const val ProxyFallbackPollIntervalMillis = 30_000L
    }
}

private fun saturatedTrafficAdd(previous: Long, increment: Long): Long {
    if (previous < 0L || increment <= 0L) return previous.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - previous < increment) Long.MAX_VALUE else previous + increment
}

internal suspend fun closeMihomoConnectionsForGroup(
    connections: List<MihomoConnection>,
    groupName: String,
    closeConnection: suspend (String) -> Unit,
): Unit = coroutineScope {
    val semaphore = Semaphore(8)
    val results = connections.filter { connection -> groupName in connection.chains }
        .map { connection ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    runMihomoRuntimeCatching { closeConnection(connection.id) }
                }
            }
        }.awaitAll()
    results.firstOrNull { it.isFailure }?.getOrThrow()
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

private fun AppState.mihomoRuntimeBackend(context: Context): MihomoRuntimeBackend {
    return if (
        usesRawMihomoConfig() || proxyRunning && runMode.isRootRunMode() ||
        runMode == RunModeVpnService && VpnSession.isRunning(context)
    ) {
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
            selectedMihomoProfileOrNull()?.overrideScriptId,
            selectedMihomoProfileOrNull()?.ruleOverrideRuntimeProjection(),
            proxyRunning,
            mihomoMode,
        )

        MihomoRuntimeBackend.Bridge -> listOf(
            exposeBridgePorts(),
            selectedMihomoProfileId,
            mihomoProfiles,
            runMode,
            mihomoMode,
            overrideUnifiedDelay,
            enableGeodataMode,
            mihomoGeodataLoader,
            mihomoFindProcessMode,
            mihomoControlPortFixed,
            mihomoControlListenAllInterfaces,
            mihomoControlCorsAllowOrigins,
            mihomoControlCorsAllowPrivateNetwork,
            overrideMihomoExternalUi,
            mihomoExternalUi,
            mihomoExternalUiUrl,
            mihomoExternalUiName,
            resourceFileSource,
            customResourceFileGeoIpUrl,
            customResourceFileGeoSiteUrl,
            customResourceFileMmdbUrl,
            customResourceFileCountryMmdbUrl,
            customResourceFileAsnUrl,
            overrideSniffer,
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
