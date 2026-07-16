// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import android.content.Context
import android.content.Intent
import app.R
import app.modes.RunModeBpf2Socks
import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import app.modes.RunModeTproxy
import app.modes.RunModeVpnService
import engine.proxy.mode.AndroidModeProxyEngine
import engine.root.RootModeEngine
import engine.bpf2socks.Bpf2SocksRootRunner
import engine.bpf2socks.buildBpf2SocksStartConfig
import engine.stats.MihomoTrafficStatsNotificationService
import engine.stats.toMihomoTrafficStatsRuntime
import engine.mihomo.withResolvedMihomoControlPort
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.raw.requireStartableRawConfig
import engine.mihomo.raw.usesRawMihomoConfig
import engine.mihomo.raw.loadSelectedRawConfig
import engine.tun.TunRootRunner
import engine.tun.buildTunStartConfig
import engine.tproxy.TproxyRootRunner
import engine.tproxy.buildTproxyStartConfig
import engine.tun2socks.Tun2SocksRootRunner
import engine.tun2socks.buildTun2SocksStartConfig
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
    private val tproxyEngine = RootModeEngine(
        context = appContext,
        rootAccess = rootAccess,
        runner = TproxyRootRunner(rootAccess),
        runMode = RunModeTproxy,
        rootRequiredErrorResId = R.string.error_tproxy_root_required,
        startFailedErrorResId = R.string.error_tproxy_start_failed,
        modeName = "TPROXY",
        logTag = "TproxyEngine",
        buildConfig = { rootContext -> rootContext.buildTproxyStartConfig() },
    )
    private val tunEngine = RootModeEngine(
        context = appContext,
        rootAccess = rootAccess,
        runner = TunRootRunner(rootAccess),
        runMode = RunModeTun,
        rootRequiredErrorResId = R.string.error_tun_root_required,
        startFailedErrorResId = R.string.error_tun_start_failed,
        modeName = "TUN",
        logTag = "TunEngine",
        buildConfig = { rootContext -> rootContext.buildTunStartConfig() },
    )
    private val tun2SocksEngine = RootModeEngine(
        context = appContext,
        rootAccess = rootAccess,
        runner = Tun2SocksRootRunner(rootAccess),
        runMode = RunModeTun2Socks,
        rootRequiredErrorResId = R.string.error_tun2socks_root_required,
        startFailedErrorResId = R.string.error_tun2socks_start_failed,
        modeName = "TUN2SOCKS",
        logTag = "Tun2SocksEngine",
        buildConfig = { rootContext -> rootContext.buildTun2SocksStartConfig() },
    )
    private val bpf2SocksEngine = RootModeEngine(
        context = appContext,
        rootAccess = rootAccess,
        runner = Bpf2SocksRootRunner(rootAccess),
        runMode = RunModeBpf2Socks,
        rootRequiredErrorResId = R.string.error_bpf2socks_root_required,
        startFailedErrorResId = R.string.error_bpf2socks_start_failed,
        modeName = "BPF2SOCKS",
        logTag = "Bpf2SocksEngine",
        buildConfig = { rootContext -> rootContext.buildBpf2SocksStartConfig() },
    )
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
        startUnlocked(request)
    }

    suspend fun status(
        preferredRunMode: Int? = null,
        appState: app.AppState? = null,
    ): ProxyEngineStatus = operationMutex.withLock {
        statusUnlocked(preferredRunMode, appState)
    }

    private suspend fun startUnlocked(request: ProxyEngineStartRequest): ProxyEngineStatus = withContext(Dispatchers.Default) {
        val raw = appContext.requireStartableRawConfig(request.appState)
        if (raw == null) {
            // Build once before any notification, engine replacement, VPN permission,
            // Root command, or routing change. This makes an explicit restart atomic
            // with respect to invalid YAML and override-script failures.
            MihomoProfileFactory.buildProfileBytes(appContext, request.appState)
        }
        val validatedRequest = request.copy(
            rawConfig = raw?.first,
            rawConfigCheck = raw?.second,
        )
        MihomoTrafficStatsNotificationService.reconcile(appContext, null)
        val resolvedRequest = validatedRequest.copy(
            appState = if (validatedRequest.appState.usesRawMihomoConfig()) {
                validatedRequest.appState
            } else {
                validatedRequest.appState
                    .withResolvedDynamicLocalProxyPort()
                    .withResolvedMihomoControlPort()
            },
        )
        val nextEngine = when (resolvedRequest.appState.runMode) {
            RunModeTproxy -> tproxyEngine
            RunModeTun -> tunEngine
            RunModeTun2Socks -> tun2SocksEngine
            RunModeBpf2Socks -> bpf2SocksEngine
            else -> vpnMihomoEngine
        }
        val currentEngine = activeEngine ?: findEngineToStop(resolvedRequest.appState.runMode)
        if (currentEngine != null && currentEngine !== nextEngine) {
            currentEngine.stop()
        }
        activeEngine = nextEngine
        try {
            val status = nextEngine.start(resolvedRequest)
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
            ?: tproxyEngine.takeIf { it.status().running }
            ?: tunEngine.takeIf { it.status().running }
            ?: tun2SocksEngine.takeIf { it.status().running }
            ?: bpf2SocksEngine.takeIf { it.status().running }
            ?: vpnMihomoEngine.takeIf { it.status().running }
            ?: tproxyEngine.takeIf { it.ownsRuntime() }
            ?: tunEngine.takeIf { it.ownsRuntime() }
            ?: tun2SocksEngine.takeIf { it.ownsRuntime() }
            ?: bpf2SocksEngine.takeIf { it.ownsRuntime() }
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
            fallbackStatus = preferredStatus
        }

        listOf(tproxyEngine, tunEngine, tun2SocksEngine, bpf2SocksEngine, vpnMihomoEngine)
            .filterNot { engine -> engine.runMode == preferredRunMode }
            .forEach { engine ->
                val status = engine.status()
                if (status.running) {
                    activeEngine = engine
                    return@withContext status
                        .withTrafficStatsReconciled(appState)
                }
            }

        activeEngine = null
        (fallbackStatus ?: ProxyEngineStatus(running = false, runMode = preferredRunMode))
            .withTrafficStatsReconciled(appState)
    }

    private fun Int.engine(): AndroidModeProxyEngine {
        return when (this) {
            RunModeTproxy -> tproxyEngine
            RunModeTun -> tunEngine
            RunModeTun2Socks -> tun2SocksEngine
            RunModeBpf2Socks -> bpf2SocksEngine
            else -> vpnMihomoEngine
        }
    }

    private suspend fun AndroidModeProxyEngine.ownsRootRuntime(): Boolean {
        return this is RootModeEngine<*> && ownsRuntime()
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
