// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState
import app.modes.RunModeVpnService
import engine.mihomo.requireUsableMihomoProfile
import engine.root.runtime.RootFailureKind
import engine.root.runtime.RootOperationBlockedException
import engine.root.runtime.RootOperationLogRecord
import engine.root.runtime.RootOperationResult
import engine.root.runtime.RootRequestedAction
import engine.root.runtime.RootToggleDecision
import engine.root.runtime.classifyForeignRootConflict
import engine.root.runtime.decideRootSafeToggle
import engine.root.runtime.toSanitizedLogRecord
import engine.root.runtime.model.RootRuntimeOwner
import engine.root.runtime.RootRuntimeBusyException
import engine.root.runtime.RootRuntimeConflictException
import features.logs.AndroidAppLogger
import kotlin.coroutines.cancellation.CancellationException

internal class ProxyServiceUseCase(
    private val proxyEngine: AndroidProxyEngine,
    private val requestVpnPermission: VpnPermissionRequester = DenyVpnPermission,
) {
    suspend fun toggle(state: AppState): ProxyServiceResult {
        val live = try {
            proxyEngine.status(state.runMode, state)
        } catch (error: Throwable) {
            return error.toProxyServiceFailure(RootRequestedAction.Toggle)
        }
        return when (val decision = decideRootSafeToggle(LocalRootOwner, live)) {
            RootToggleDecision.OrdinaryStart -> start(state)
            RootToggleDecision.StopOwn -> stop(state.runMode)
            is RootToggleDecision.Blocked -> decision.result.toProxyServiceFailure(RootRequestedAction.Toggle)
        }
    }

    suspend fun restart(state: AppState): ProxyServiceResult {
        val live = try {
            proxyEngine.status(state.runMode, state)
        } catch (error: Throwable) {
            return error.toProxyServiceFailure(RootRequestedAction.RestartSameOwner)
        }
        classifyForeignRootConflict(
            LocalRootOwner,
            live,
        )?.let { conflict -> return conflict.toProxyServiceFailure(RootRequestedAction.RestartSameOwner) }
        return runCatching {
            state.requireUsableMihomoProfile()
            proxyEngine.restart(ProxyEngineStartRequest(state), requestVpnPermission)
        }.fold(
            onSuccess = { status -> ProxyServiceResult.Success(proxyRunning = status.running, appState = status.appState) },
            onFailure = { error -> error.toProxyServiceFailure(RootRequestedAction.RestartSameOwner) },
        )
    }

    /**
     * Applies a changed profile to the running core in place, in VPN and ROOT modes alike (see
     * [AndroidProxyEngine.applyProfile]); a stopped core is left stopped. A profile the core
     * cannot take fails with a [ProfileApplyException] and never restarts the service.
     *
     * The VPN branch reads no ROOT status: the foreign-owner check belongs to ROOT modes only.
     */
    suspend fun applyConfiguration(state: AppState): ProxyServiceResult {
        if (state.runMode != RunModeVpnService) {
            val live = try {
                proxyEngine.status(state.runMode, state)
            } catch (error: Throwable) {
                return error.toProxyServiceFailure(RootRequestedAction.RestartSameOwner)
            }
            classifyForeignRootConflict(LocalRootOwner, live)?.let { conflict ->
                return conflict.toProxyServiceFailure(RootRequestedAction.RestartSameOwner)
            }
        }
        return runCatching {
            state.requireUsableMihomoProfile()
            proxyEngine.applyProfile(ProxyEngineStartRequest(state), requestVpnPermission)
        }.fold(
            onSuccess = { status -> ProxyServiceResult.Success(proxyRunning = status.running, appState = status.appState) },
            onFailure = { error ->
                when {
                    error is CancellationException -> throw error
                    error is ProfileApplyException -> {
                        // The cause may quote the configuration, so only its kind is logged.
                        AndroidAppLogger.error(
                            LogTag,
                            "Profile apply failed: ${error.failure} (${error.cause?.javaClass?.simpleName})",
                        )
                        ProxyServiceResult.Failed(error)
                    }
                    state.runMode == RunModeVpnService -> ProxyServiceResult.Failed(error)
                    else -> error.toProxyServiceFailure(RootRequestedAction.RestartSameOwner)
                }
            },
        )
    }

    /** The live running state for [state]'s run mode, or `null` when it cannot be read. */
    suspend fun isRunning(state: AppState): Boolean? = try {
        proxyEngine.status(state.runMode, state).running
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        AndroidAppLogger.warn(LogTag, "Failed to read proxy status", error)
        null
    }

    private suspend fun start(state: AppState): ProxyServiceResult {
        return runCatching {
            state.requireUsableMihomoProfile()
            proxyEngine.start(ProxyEngineStartRequest(state), requestVpnPermission)
        }.fold(
            onSuccess = { status -> ProxyServiceResult.Success(proxyRunning = status.running, appState = status.appState) },
            onFailure = { error -> error.toProxyServiceFailure(RootRequestedAction.OrdinaryStart) },
        )
    }

    suspend fun stop(runMode: Int): ProxyServiceResult {
        val live = try {
            proxyEngine.status(runMode)
        } catch (error: Throwable) {
            return error.toProxyServiceFailure(RootRequestedAction.StopOwn)
        }
        classifyForeignRootConflict(
            LocalRootOwner,
            live,
        )?.let { conflict -> return conflict.toProxyServiceFailure(RootRequestedAction.StopOwn) }
        return runCatching { proxyEngine.stop(runMode) }.fold(
            onSuccess = { status -> ProxyServiceResult.Success(proxyRunning = status.running, appState = status.appState) },
            onFailure = { error -> error.toProxyServiceFailure(RootRequestedAction.StopOwn) },
        )
    }

    suspend fun shutdown(runMode: Int): ProxyServiceResult {
        val live = try {
            proxyEngine.status(runMode)
        } catch (error: Throwable) {
            return error.toProxyServiceFailure(RootRequestedAction.StopOwn)
        }
        classifyForeignRootConflict(LocalRootOwner, live)?.let { conflict ->
            return conflict.toProxyServiceFailure(RootRequestedAction.StopOwn)
        }
        return runCatching { proxyEngine.shutdownCurrentRunMode(runMode) }.fold(
            onSuccess = { status -> ProxyServiceResult.Success(status.running, status.appState) },
            onFailure = { error -> error.toProxyServiceFailure(RootRequestedAction.StopOwn) },
        )
    }

    private fun RootOperationResult.toProxyServiceFailure(action: RootRequestedAction): ProxyServiceResult.Failed {
        logRootResult(toSanitizedLogRecord(action))
        return ProxyServiceResult.Failed(RootOperationBlockedException(this))
    }

    private fun Throwable.toProxyServiceFailure(action: RootRequestedAction): ProxyServiceResult.Failed {
        if (this is CancellationException) throw this
        val rootResult = when (this) {
            is RootRuntimeConflictException -> RootOperationResult.ForeignOwnerConflict(
                owner = RootRuntimeOwner.entries.single { owner -> owner.wireValue == snapshot.owner.wireValue },
            )
            is RootRuntimeBusyException -> RootOperationResult.Busy(
                RootRuntimeOwner.entries.single { owner -> owner.wireValue == snapshot.owner.wireValue },
            )
            else -> RootOperationResult.Failure(RootFailureKind.StartFailure)
        }
        logRootResult(rootResult.toSanitizedLogRecord(action))
        return if (this is RootRuntimeConflictException || this is RootRuntimeBusyException) {
            ProxyServiceResult.Failed(RootOperationBlockedException(rootResult))
        } else {
            ProxyServiceResult.Failed(this)
        }
    }

    private fun logRootResult(record: RootOperationLogRecord) {
        AndroidAppLogger.error(LogTag, record.asLogMessage())
    }
}

internal sealed interface ProxyServiceResult {
    data class Success(
        val proxyRunning: Boolean,
        val appState: AppState? = null,
    ) : ProxyServiceResult

    data class Failed(val error: Throwable) : ProxyServiceResult
}

private val LocalRootOwner = RootRuntimeOwner.AsteriskMeta
private const val LogTag = "ProxyServiceUseCase"
