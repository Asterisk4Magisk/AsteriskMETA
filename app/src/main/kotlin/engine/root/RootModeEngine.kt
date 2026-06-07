// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import android.content.Context
import engine.proxy.LocalProxyRuntime
import engine.proxy.ProxyEngineStartRequest
import engine.proxy.ProxyEngineStatus
import engine.proxy.mode.AndroidModeProxyEngine
import engine.mihomo.clearCoreLogs
import engine.mihomo.startCoreLogTailers
import features.logs.AndroidAppLogger
import features.logs.CoreLogFileTailer
import system.AndroidRootShellGateway
import java.io.File

internal class RootModeEngine<Config : RootModeStartConfig>(
    private val context: Context,
    private val rootAccess: AndroidRootShellGateway,
    private val runner: RootModeRunner<Config>,
    override val runMode: Int,
    private val rootRequiredErrorResId: Int,
    private val startFailedErrorResId: Int,
    private val modeName: String,
    private val logTag: String,
    private val buildConfig: (RootConfigBuildContext) -> Config,
) : AndroidModeProxyEngine {
    private var logFileTailers: List<CoreLogFileTailer> = emptyList()

    override suspend fun start(request: ProxyEngineStartRequest): ProxyEngineStatus {
        if (!rootAccess.hasRootAccess()) {
            error(context.getString(rootRequiredErrorResId))
        }
        stop()
        val rootContext = context.prepareRootConfigBuildContext(request)
        val config = buildConfig(rootContext)
        if (!File(config.root.runtimeLayout.mihomoCorePath).canExecute()) {
            File(config.root.runtimeLayout.mihomoCorePath).setExecutable(true, false)
        }
        runner.prepareCoreLogFiles(config.root.coreLogPaths)
        config.root.coreLogPaths.clearCoreLogs(logTag)
        logFileTailers = config.root.coreLogPaths.startCoreLogTailers()
        runCatching {
            runner.start(config)
            LocalProxyRuntime.update(config.localProxyOptions)
            if (rootContext.appState.enableRootBootScript) {
                runner.installBootScript(config)
            } else {
                runner.uninstallBootScript(config.root)
            }
        }.onFailure { error ->
            runCatching { runner.stop(config.root.runtimeLayout) }
                .onFailure { stopError -> AndroidAppLogger.warn(logTag, "Failed to clean up $modeName after startup failure", stopError) }
            LocalProxyRuntime.clear()
            logFileTailers.forEach { tailer -> tailer.stop() }
            logFileTailers = emptyList()
            AndroidAppLogger.error(logTag, "Failed to start $modeName mode", error)
            throw IllegalStateException(
                context.getString(startFailedErrorResId, error.message.orEmpty()),
                error,
            )
        }
        return status()
    }

    override suspend fun stop(): ProxyEngineStatus {
        logFileTailers.forEach { tailer -> tailer.stop() }
        logFileTailers = emptyList()
        runCatching {
            runner.stop(context.prepareRootRuntimeLayout())
        }.onFailure { error ->
            AndroidAppLogger.warn(logTag, "Failed to stop $modeName mode", error)
        }
        LocalProxyRuntime.clear()
        return status()
    }

    suspend fun ownsRuntime(): Boolean {
        return runner.ownsRuntime(context.prepareRootRuntimeLayout())
    }

    override suspend fun status(): ProxyEngineStatus {
        val running = runner.isRunning(context.prepareRootRuntimeLayout())
        return ProxyEngineStatus(running = running, runMode = runMode)
    }
}
