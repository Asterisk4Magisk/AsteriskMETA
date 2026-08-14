// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import android.content.Context
import android.content.Intent
import app.modes.RunModeVpnService
import engine.proxy.mode.AndroidModeProxyEngine
import engine.root.RootModeEngine
import engine.stats.MihomoTrafficStatsNotificationService
import engine.stats.toMihomoTrafficStatsRuntime
import engine.mihomo.withResolvedMihomoControlPort
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.raw.requireStartableRawConfig
import engine.mihomo.raw.usesRawMihomoConfig
import engine.mihomo.raw.loadSelectedRawConfig
import engine.vpn.VpnMihomoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import system.AndroidRootShellGateway

internal class AndroidProxyEngine(
    context: Context,
    rootAccess: AndroidRootShellGateway,
    requestVpnPermission: suspend (Intent) -> Boolean,
) {
    private val appContext = context.applicationContext
    private val vpnMihomoEngine = VpnMihomoEngine(appContext, requestVpnPermission)
    private val rootEngines = RootModeEngine.createAll(appContext, rootAccess)
    private val rootEnginesByRunMode = rootEngines.associateBy(RootModeEngine::runMode)
    private val operationMutex = Mutex()
    private var activeEngine: AndroidModeProxyEngine? = null

    suspend fun start(request: ProxyEngineStartRequest): ProxyEngineStatus = operationMutex.withLock {
        startUnlocked(request)
    }

    suspend fun stop(preferredRunMode: Int? = null): ProxyEngineStatus = operationMutex.withLock {
        stopUnlocked(preferredRunMode)
    }

    suspend fun stopCurrentRunMode(runMode: Int): ProxyEngineStatus = operationMutex.withLock {
        stopRunModeUnlocked(runMode)
    }

    suspend fun restart(request: ProxyEngineStartRequest): ProxyEngineStatus = operationMutex.withLock {
        startUnlocked(request, explicitRestart = true)
    }

    suspend fun status(
        preferredRunMode: Int? = null,
        appState: app.AppState? = null,
    ): ProxyEngineStatus = operationMutex.withLock {
        statusUnlocked(preferredRunMode, appState)
    }

    private suspend fun startUnlocked(
        request: ProxyEngineStartRequest,
        explicitRestart: Boolean = false,
    ): ProxyEngineStatus = withContext(Dispatchers.Default) {
        val raw = appContext.requireStartableRawConfig(request.appState)
        val validatedRequest = request.copy(
            rawConfig = raw?.first,
            rawConfigCheck = raw?.second,
        )
        val requestedEngine = validatedRequest.appState.runMode.engine()
        if (shouldResumeRootBeforeResolvingPorts(explicitRestart, activeEngine != null, requestedEngine is RootModeEngine)) {
            requestedEngine as RootModeEngine
            requestedEngine.resumeIfRunning(validatedRequest)?.let { status ->
                activeEngine = requestedEngine
                val resumed = status.copy(appState = request.appState)
                MihomoTrafficStatsNotificationService.reconcile(
                    appContext,
                    validatedRequest.appState.toMihomoTrafficStatsRuntime(
                        runMode = status.runMode ?: validatedRequest.appState.runMode,
                        rawConfig = validatedRequest.rawConfig,
                    ),
                )
                return@withContext resumed
            }
        }
        // Resolve runtime ports, build, and validate the exact final profile before
        // notifications, engine replacement, VPN permission, Root commands, or routing changes.
        val resolvedRequest = validatedRequest.prepareNormalMihomoProfile(
            resolveAppState = { appState ->
                appState
                    .withResolvedDynamicLocalProxyPort()
                    .withResolvedMihomoControlPort()
            },
            buildProfileBytes = { appState ->
                MihomoProfileFactory.buildProfileBytes(appContext, appState)
            },
        )
        MihomoTrafficStatsNotificationService.reconcile(appContext, null)
        val nextEngine = resolvedRequest.appState.runMode.engine()
        val currentEngine = activeEngine ?: findEngineToStop(resolvedRequest.appState.runMode)
        val rootToRootRestart = explicitRestart &&
            currentEngine is RootModeEngine && nextEngine is RootModeEngine
        if (currentEngine != null && currentEngine !== nextEngine && !rootToRootRestart) {
            currentEngine.stop()
        }
        activeEngine = nextEngine
        try {
            val status = if (explicitRestart && nextEngine is RootModeEngine) {
                nextEngine.restart(resolvedRequest)
            } else {
                nextEngine.start(resolvedRequest)
            }
                .copy(
                    appState = if (request.appState.usesRawMihomoConfig()) {
                        request.appState
                    } else {
                        resolvedRequest.appState
                    },
                )
            val runtime = if (status.running) {
                resolvedRequest.appState.toMihomoTrafficStatsRuntime(
                    runMode = status.runMode ?: resolvedRequest.appState.runMode,
                    rawConfig = resolvedRequest.rawConfig,
                )
            } else {
                null
            }
            MihomoTrafficStatsNotificationService.reconcile(appContext, runtime)
            status
        } catch (error: Throwable) {
            MihomoTrafficStatsNotificationService.reconcile(appContext, null)
            throw error
        }
    }

    private suspend fun stopUnlocked(preferredRunMode: Int? = null): ProxyEngineStatus = withContext(Dispatchers.Default) {
        val engine = findEngineToStop(preferredRunMode)
        val stoppedMode = engine?.runMode
        engine?.stop()
        activeEngine = null
        MihomoTrafficStatsNotificationService.reconcile(appContext, null)
        ProxyEngineStatus(running = false, runMode = stoppedMode)
    }

    private suspend fun stopRunModeUnlocked(runMode: Int): ProxyEngineStatus = withContext(Dispatchers.Default) {
        val engine = runMode.engine()
        activeEngine
            ?.takeIf { active -> active !== engine }
            ?.stop()
        val status = engine.stop()
        activeEngine = null
        MihomoTrafficStatsNotificationService.reconcile(appContext, null)
        status
    }

    private suspend fun findEngineToStop(preferredRunMode: Int?): AndroidModeProxyEngine? {
        val preferredEngine = preferredRunMode?.engine()
        return activeEngine
            ?: preferredEngine?.takeIf { it.status().running }
            ?: preferredEngine?.takeIf { it.ownsRootRuntime() }
            ?: rootEngines.firstOrNull { engine -> engine.status().running }
            ?: vpnMihomoEngine.takeIf { it.status().running }
            ?: rootEngines.firstOrNull { engine -> engine.ownsRuntime() }
    }

    private suspend fun statusUnlocked(
        preferredRunMode: Int? = null,
        appState: app.AppState? = null,
    ): ProxyEngineStatus = withContext(Dispatchers.Default) {
        val activeStatus = activeEngine?.status()
        if (activeStatus?.running == true) {
            return@withContext activeStatus
                .withTrafficStatsReconciled(appState)
        }

        var fallbackStatus = activeStatus
        preferredRunMode?.engine()?.let { preferredEngine ->
            val preferredStatus = preferredEngine.status()
            if (preferredStatus.running) {
                activeEngine = preferredEngine
                return@withContext preferredStatus
                    .withTrafficStatsReconciled(appState)
            }
            if (preferredStatus.rootSnapshot != null || fallbackStatus?.rootSnapshot == null) {
                fallbackStatus = preferredStatus
            }
        }

        (rootEngines + vpnMihomoEngine)
            .filterNot { engine -> engine.runMode == preferredRunMode }
            .forEach { engine ->
                val status = engine.status()
                if (status.running) {
                    activeEngine = engine
                    return@withContext status
                        .withTrafficStatsReconciled(appState)
                }
                if (status.rootSnapshot != null && fallbackStatus?.rootSnapshot == null) {
                    fallbackStatus = status
                }
            }

        activeEngine = null
        (fallbackStatus ?: ProxyEngineStatus(running = false, runMode = preferredRunMode))
            .withTrafficStatsReconciled(appState)
    }

    private fun Int.engine(): AndroidModeProxyEngine {
        return rootEnginesByRunMode[this] ?: vpnMihomoEngine
    }

    private suspend fun AndroidModeProxyEngine.ownsRootRuntime(): Boolean {
        return this is RootModeEngine && ownsRuntime()
    }

    private fun ProxyEngineStatus.withTrafficStatsReconciled(appState: app.AppState?): ProxyEngineStatus {
        if (!running) {
            MihomoTrafficStatsNotificationService.reconcile(appContext, null)
            return this
        }
        val activeRunMode = runMode ?: appState?.runMode
        if (activeRunMode != RunModeVpnService) {
            MihomoTrafficStatsNotificationService.reconcile(appContext, null)
            return this
        }
        if (appState == null) {
            return this
        }
        val rawConfig = if (appState.usesRawMihomoConfig()) {
            appContext.loadSelectedRawConfig(appState)?.snapshot
        } else {
            null
        }
        val runtime = appState.toMihomoTrafficStatsRuntime(activeRunMode, rawConfig)
        MihomoTrafficStatsNotificationService.reconcile(appContext, runtime)
        return this
    }
}

internal fun shouldResumeRootBeforeResolvingPorts(
    explicitRestart: Boolean,
    hasActiveEngine: Boolean,
    requestedIsRoot: Boolean,
): Boolean = !explicitRestart && !hasActiveEngine && requestedIsRoot
