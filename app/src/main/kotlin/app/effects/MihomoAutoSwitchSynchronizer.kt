// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.AppState
import data.AndroidAppStateStore
import engine.mihomo.raw.usesRawMihomoConfig
import engine.mihomo.runtime.MihomoRuntimeRepository
import engine.mihomo.toMihomoAutoSwitchOptions
import features.logs.AndroidAppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlin.time.Duration.Companion.seconds

/**
 * The service profile already carries these settings, so the core keeps checking without the app.
 * This only hot-applies changes made while it runs, and repairs a core started before them.
 */
@Composable
internal fun MihomoAutoSwitchSynchronizer(
    stateStore: AndroidAppStateStore,
    runtime: MihomoRuntimeRepository,
) {
    LaunchedEffect(stateStore, runtime) {
        stateStore.state
            .distinctUntilChangedBy(AppState::autoSwitchSyncKey)
            .collectLatest { appState ->
                if (!appState.proxyRunning || appState.usesRawMihomoConfig()) return@collectLatest
                // The control endpoint may still be starting right after the service reports running.
                repeat(AutoSwitchSyncAttempts) { attempt ->
                    val failure = runtime.syncAutoSwitch(appState).exceptionOrNull() ?: return@collectLatest
                    if (attempt == AutoSwitchSyncAttempts - 1) {
                        AndroidAppLogger.warn(
                            "AutoSwitch",
                            "Could not apply automatic switching settings (${failure.javaClass.simpleName})",
                        )
                    } else {
                        delay(AutoSwitchSyncRetryDelay)
                    }
                }
            }
    }
}

private fun AppState.autoSwitchSyncKey(): List<Any?> = listOf(
    proxyRunning,
    runMode,
    usesRawMihomoConfig(),
    toMihomoAutoSwitchOptions(),
)

private const val AutoSwitchSyncAttempts = 3
private val AutoSwitchSyncRetryDelay = 2.seconds
