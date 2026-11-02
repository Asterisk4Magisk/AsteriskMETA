// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import app.AppState
import app.R
import app.modes.RunModeVpnService
import data.AndroidAppStateStore
import engine.mihomo.MihomoControlConfig
import engine.mihomo.logDirectoryPath
import engine.mihomo.mihomoControlConfig
import engine.mihomo.mihomoControllerSocket
import engine.mihomo.mihomoLogLevelName
import engine.mihomo.raw.loadSelectedRawConfig
import engine.mihomo.raw.usesRawMihomoConfig
import engine.mihomo.runtime.MihomoControlClient
import engine.mihomo.runtime.StreamReconnectDelayMillis
import engine.proxy.ProxyEngineStartRequest
import engine.proxy.ProxyEngineStatus
import engine.proxy.mode.AndroidModeProxyEngine
import features.logs.AndroidAppLogger
import features.logs.AndroidCoreLogRepository
import features.logs.clearVpnServiceLogsAsApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import kotlin.time.Duration.Companion.milliseconds

internal class VpnMihomoEngine(
    private val context: Context,
    private val requestVpnPermission: suspend (Intent) -> Boolean,
    private val runtimeRunning: () -> Boolean = { AsteriskVpnService.isRunning(context) },
) : AndroidModeProxyEngine {
    override val runMode: Int = RunModeVpnService

    private val logStreamLock = Any()
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val controlClient = MihomoControlClient()
    private var logStreamJob: Job? = null

    override suspend fun start(request: ProxyEngineStartRequest): ProxyEngineStatus {
        return startWithPolicy(request, explicitRestart = false)
    }

    suspend fun restart(request: ProxyEngineStartRequest): ProxyEngineStatus {
        return startWithPolicy(request, explicitRestart = true)
    }

    private suspend fun startWithPolicy(
        request: ProxyEngineStartRequest,
        explicitRestart: Boolean,
    ): ProxyEngineStatus {
        if (!shouldLaunchVpnService(runtimeRunning(), explicitRestart)) return status()
        launch(request)
        return status()
    }

    private suspend fun launch(request: ProxyEngineStartRequest) {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null && !requestVpnPermission(prepareIntent)) {
            error(context.getString(R.string.error_vpn_permission_denied))
        }
        try {
            val config = VpnMihomoConfigFactory.create(context, request)
            stopLogStream()
            clearVpnServiceLogsAsApp(File(config.coreLogPaths.logDirectoryPath()), LogTag)
            try {
                AsteriskVpnService.start(context = context, config = config)
            } catch (error: VpnServiceStartException) {
                if (error.cause !is VpnServiceProcessExitedException) throw error
                // The service process kills itself when the previous core does not shut down
                // in time, which takes this start with it. A fresh process starts cleanly.
                AndroidAppLogger.warn(LogTag, "VPN Service process exited during the start; starting it again", error)
                AsteriskVpnService.start(context = context, config = config)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            AndroidAppLogger.error(LogTag, "Failed to start VPN Service", error)
            throw error
        }
    }

    override suspend fun stop(): ProxyEngineStatus {
        // Without a session there is nothing to stop, and asking would launch the service process.
        if (runtimeRunning()) AsteriskVpnService.stop(context)
        return status()
    }

    override suspend fun status(): ProxyEngineStatus = withContext(Dispatchers.IO) {
        val running = runtimeRunning()
        // The local proxy is read from the session on demand (LocalProxyRuntime), and the
        // watch reports the end of the tunnel, however it ends, to this process.
        if (running) VpnSession.watch(context)
        syncLogStream(running)
        ProxyEngineStatus(
            running = running,
            runMode = runMode,
        )
    }

    private fun syncLogStream(running: Boolean) {
        if (!running) {
            stopLogStream()
            return
        }
        synchronized(logStreamLock) {
            if (logStreamJob?.isActive == true) return
            logStreamJob = logScope.launch {
                AndroidAppStateStore.get(context).state
                    .map(::logStreamConfig)
                    .distinctUntilChanged()
                    .collectLatest { config -> if (config != null) collectLogs(config) }
            }
        }
    }

    private fun logStreamConfig(state: AppState): VpnLogStreamConfig? {
        if (state.usesRawMihomoConfig()) {
            val snapshot = runCatching { context.loadSelectedRawConfig(state)?.snapshot }.getOrNull() ?: return null
            val override = snapshot.controller.vpnSessionOverride(context.mihomoControllerSocket())
            return VpnLogStreamConfig(
                control = MihomoControlConfig(
                    secret = override.secret.orEmpty(),
                    unixSocketPath = override.externalControllerUnix,
                ),
                level = snapshot.logLevel.value?.takeIf(String::isNotBlank) ?: "info",
            )
        }
        return VpnLogStreamConfig(
            control = state.mihomoControlConfig(context, RunModeVpnService),
            level = state.mihomoLogLevelName(),
        )
    }

    private suspend fun collectLogs(config: VpnLogStreamConfig) {
        while (currentCoroutineContext().isActive && runtimeRunning()) {
            try {
                controlClient.streamLogs(config.control, config.level).collect { record ->
                    AndroidCoreLogRepository.appendPersisted(record.level, record.message, record.timestampMillis)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                currentCoroutineContext().ensureActive()
                // The session lock is authoritative, including when the service crashed.
                if (!runtimeRunning()) return
                if (error !is ConnectException) {
                    AndroidAppLogger.warn(LogTag, "Failed to collect Mihomo core logs", error)
                }
            }
            if (!runtimeRunning()) return
            delay(StreamReconnectDelayMillis.milliseconds)
        }
    }

    private fun stopLogStream() {
        synchronized(logStreamLock) {
            logStreamJob?.cancel()
            logStreamJob = null
        }
    }

    private companion object {
        const val LogTag = "VpnMihomoEngine"
    }
}

private data class VpnLogStreamConfig(val control: MihomoControlConfig, val level: String)

internal fun shouldLaunchVpnService(isRunning: Boolean, explicitRestart: Boolean): Boolean {
    return explicitRestart || !isRunning
}
