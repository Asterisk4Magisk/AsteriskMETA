// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import app.AppState
import data.backup.AppBackupRestorePreview
import kotlin.coroutines.cancellation.CancellationException

internal sealed interface SettingsBackupCleanupResult {
    data object Success : SettingsBackupCleanupResult

    data object Unavailable : SettingsBackupCleanupResult

    data class Failed(val error: Throwable) : SettingsBackupCleanupResult
}

internal sealed interface SettingsBackupRestoreResult {
    data object Success : SettingsBackupRestoreResult

    data class ProxyStopFailed(val error: Throwable) : SettingsBackupRestoreResult

    data object RootUnavailable : SettingsBackupRestoreResult

    data class RootUninstallFailed(val error: Throwable) : SettingsBackupRestoreResult

    data class StateReplaceFailed(val error: Throwable) : SettingsBackupRestoreResult
}

/**
 * Mirrors AsteriskBOX's executor. It takes the whole preview rather than only
 * the restored state because META restores profile contents to files as part
 * of replacing the state.
 */
internal class SettingsBackupRestoreExecutor(
    private val stopProxy: suspend (runMode: Int) -> SettingsBackupCleanupResult,
    private val uninstallRootBootScript:
        suspend (finalizeRestore: suspend () -> Unit) -> SettingsBackupCleanupResult,
    private val replaceState: suspend (AppBackupRestorePreview) -> Unit,
) {
    suspend fun execute(
        currentState: AppState,
        preview: AppBackupRestorePreview,
    ): SettingsBackupRestoreResult {
        when (val stopResult = safelyCleanUp { stopProxy(currentState.runMode) }) {
            SettingsBackupCleanupResult.Success -> Unit
            SettingsBackupCleanupResult.Unavailable -> {
                return SettingsBackupRestoreResult.ProxyStopFailed(
                    IllegalStateException("Proxy service is unavailable"),
                )
            }
            is SettingsBackupCleanupResult.Failed -> {
                return SettingsBackupRestoreResult.ProxyStopFailed(stopResult.error)
            }
        }

        if (currentState.enableRootBootScript) {
            var replacementResult: SettingsBackupRestoreResult? = null
            val uninstallResult = safelyCleanUp {
                uninstallRootBootScript {
                    replacementResult = safelyReplaceState(preview)
                }
            }
            when (uninstallResult) {
                SettingsBackupCleanupResult.Success -> Unit
                SettingsBackupCleanupResult.Unavailable -> {
                    return SettingsBackupRestoreResult.RootUnavailable
                }
                is SettingsBackupCleanupResult.Failed -> {
                    return SettingsBackupRestoreResult.RootUninstallFailed(uninstallResult.error)
                }
            }
            return replacementResult ?: SettingsBackupRestoreResult.StateReplaceFailed(
                IllegalStateException("ROOT cleanup did not finalize restored state"),
            )
        }

        return safelyReplaceState(preview)
    }

    private suspend fun safelyReplaceState(preview: AppBackupRestorePreview): SettingsBackupRestoreResult =
        try {
            replaceState(preview)
            SettingsBackupRestoreResult.Success
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsBackupRestoreResult.StateReplaceFailed(error)
        }

    private suspend fun safelyCleanUp(
        action: suspend () -> SettingsBackupCleanupResult,
    ): SettingsBackupCleanupResult =
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsBackupCleanupResult.Failed(error)
        }
}
