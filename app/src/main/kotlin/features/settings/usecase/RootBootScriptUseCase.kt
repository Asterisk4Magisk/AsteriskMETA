// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.usecase

import android.content.Context
import app.AppState
import app.modes.isRootRunMode
import engine.proxy.ProxyEngineStartRequest
import engine.root.runtime.RootFailureKind
import engine.root.runtime.RootOperationBlockedException
import engine.root.runtime.RootOperationResult
import engine.root.runtime.RootRequestedAction
import engine.root.runtime.toAppLogMessage
import engine.root.runtime.model.RootRuntimeOwner
import engine.root.runtime.RootRuntimeBusyException
import engine.root.runtime.RootRuntimeConflictException
import engine.root.runtime.RootSupervisorController
import engine.root.RootModeEngine
import features.logs.AndroidAppLogger
import kotlin.coroutines.cancellation.CancellationException
import system.AndroidRootShellGateway

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
        install(state, deferIfRuntimeBound = true)
    }

    suspend fun uninstall(rootAccessVerified: Boolean = false): RootBootScriptResult =
        operationGate.exclusive {
            if (!rootAccessVerified && !rootAccess.hasRootAccess()) {
                return@exclusive RootBootScriptResult.RootUnavailable
            }
            uninstallUnlocked()
        }

    suspend fun uninstallAndThen(
        rootAccessVerified: Boolean = false,
        afterUninstall: suspend () -> Unit,
    ): RootBootScriptResult = operationGate.exclusive {
        if (!rootAccessVerified && !rootAccess.hasRootAccess()) {
            return@exclusive RootBootScriptResult.RootUnavailable
        }
        val result = uninstallUnlocked()
        if (result == RootBootScriptResult.Success) {
            afterUninstall()
        }
        result
    }

    private suspend fun uninstallUnlocked(): RootBootScriptResult =
        runCatching {
            controller.removeBoot()
        }.fold(
            onSuccess = { RootBootScriptResult.Success },
            onFailure = { error -> error.toRootBootScriptFailure() },
        )

    private suspend fun install(
        state: AppState,
        deferIfRuntimeBound: Boolean = false,
    ): RootBootScriptResult {
        return runCatching {
            val request = ProxyEngineStartRequest(state)
            if (state.runMode.isRootRunMode()) {
                installRootBootScript(state.runMode, request, deferIfRuntimeBound)
            }
        }.fold(
            onSuccess = { RootBootScriptResult.Success },
            onFailure = { error -> error.toRootBootScriptFailure() },
        )
    }

    private suspend fun installRootBootScript(
        runMode: Int,
        request: ProxyEngineStartRequest,
        deferIfRuntimeBound: Boolean,
    ) {
        if (!controller.canPublishBoot(deferIfRuntimeBound)) return
        val config = RootModeEngine.prepareConfig(appContext, runMode, request)
        controller.publishBoot(config.root, config.asteriskdConfig)
    }
}

private fun Throwable.toRootBootScriptFailure(): RootBootScriptResult.Failed {
    if (this is CancellationException) throw this
    val operationResult = toRootBootOperationResult()
    AndroidAppLogger.error(
        RootBootLogTag,
        operationResult.toAppLogMessage(RootRequestedAction.BootRefresh),
    )
    val reportedError = when (this) {
        is RootRuntimeConflictException, is RootRuntimeBusyException -> RootOperationBlockedException(operationResult)
        else -> this
    }
    return RootBootScriptResult.Failed(reportedError)
}

private fun Throwable.toRootBootOperationResult(): RootOperationResult = when (this) {
    is RootRuntimeConflictException -> RootOperationResult.ForeignOwnerConflict(
        owner = RootRuntimeOwner.entries.single { owner -> owner.wireValue == snapshot.owner.wireValue },
    )
    is RootRuntimeBusyException -> RootOperationResult.Busy(
        RootRuntimeOwner.entries.single { owner -> owner.wireValue == snapshot.owner.wireValue },
    )
    else -> RootOperationResult.Failure(RootFailureKind.InternalFailure)
}

internal sealed interface RootBootScriptResult {
    data object Success : RootBootScriptResult

    data object RootUnavailable : RootBootScriptResult

    data class Failed(val error: Throwable) : RootBootScriptResult
}

private const val RootBootLogTag = "RootBootScript"
