// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.usecase

import android.content.Context
import app.AppState
import app.modes.isRootRunMode
import engine.proxy.ProxyEngineStartRequest
import engine.root.RootModeEngine
import engine.root.runtime.RootFailureKind
import engine.root.runtime.RootOperationResult
import engine.root.runtime.RootRequestedAction
import engine.root.runtime.RootSupervisorController
import engine.root.runtime.toAppLogMessage
import features.logs.AndroidAppLogger
import system.AndroidRootShellGateway
import kotlin.coroutines.cancellation.CancellationException

internal class RootBootScriptUseCase(
    context: Context,
    private val rootAccess: AndroidRootShellGateway,
    private val operationGate: RootBootScriptOperationGate = RootBootScriptOperationGate(),
) {
    private val appContext = context.applicationContext
    private val controller = RootSupervisorController(appContext, rootAccess)

    suspend fun setEnabled(
        state: AppState,
        enabled: Boolean,
    ): RootBootScriptResult = operationGate.exclusive {
        if (!rootAccess.hasRootAccess()) {
            return@exclusive RootBootScriptResult.RootUnavailable
        }
        if (enabled) {
            install(state)
        } else {
            uninstallUnlocked()
        }
    }

    suspend fun refresh(
        state: AppState,
        isStillCurrent: () -> Boolean = { true },
    ): RootBootScriptResult = operationGate.exclusive {
        if (!isStillCurrent() || !state.enableRootBootScript) {
            return@exclusive RootBootScriptResult.Success
        }
        if (!rootAccess.hasRootAccess()) {
            return@exclusive RootBootScriptResult.RootUnavailable
        }
        install(state)
    }

    suspend fun uninstall(rootAccessVerified: Boolean = false): RootBootScriptResult =
        operationGate.exclusive {
            if (!rootAccessVerified && !rootAccess.hasRootAccess()) {
                return@exclusive RootBootScriptResult.RootUnavailable
            }
            uninstallUnlocked()
        }

    private suspend fun uninstallUnlocked(): RootBootScriptResult =
        runCatching {
            controller.removeBoot()
        }.fold(
            onSuccess = { RootBootScriptResult.Success },
            onFailure = { error -> error.toRootBootScriptFailure() },
        )

    private suspend fun install(state: AppState): RootBootScriptResult {
        return runCatching {
            val request = ProxyEngineStartRequest(state)
            if (state.runMode.isRootRunMode()) {
                installRootBootScript(state.runMode, request)
            }
        }.fold(
            onSuccess = { RootBootScriptResult.Success },
            onFailure = { error -> error.toRootBootScriptFailure() },
        )
    }

    private suspend fun installRootBootScript(
        runMode: Int,
        request: ProxyEngineStartRequest,
    ) {
        val config = RootModeEngine.prepareConfig(appContext, runMode, request)
        controller.publishBoot(config.root, config.asteriskdConfig)
    }
}

private fun Throwable.toRootBootScriptFailure(): RootBootScriptResult.Failed {
    if (this is CancellationException) throw this
    val operationResult = RootOperationResult.Failure(RootFailureKind.InternalFailure)
    AndroidAppLogger.error(
        RootBootLogTag,
        operationResult.toAppLogMessage(RootRequestedAction.BootRefresh),
    )
    return RootBootScriptResult.Failed(this)
}

internal sealed interface RootBootScriptResult {
    data object Success : RootBootScriptResult

    data object RootUnavailable : RootBootScriptResult

    data class Failed(val error: Throwable) : RootBootScriptResult
}

private const val RootBootLogTag = "RootBootScript"
