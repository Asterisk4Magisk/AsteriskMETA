// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import data.AndroidAppStateStore
import engine.stats.MihomoTrafficStatsNotificationService
import engine.stats.MihomoTrafficStatsRuntime
import engine.stats.toMihomoTrafficStatsRuntime

@Composable
internal fun TrafficStatsNotificationSynchronizer(
    stateStore: AndroidAppStateStore,
) {
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(appContext, stateStore) {
        var activeRuntime: MihomoTrafficStatsRuntime? = null
        stateStore.state.collect { appState ->
            val runtime = if (appState.proxyRunning) {
                appState.toMihomoTrafficStatsRuntime()
            } else {
                null
            }
            if (runtime == activeRuntime) {
                return@collect
            }
            activeRuntime = runtime
            MihomoTrafficStatsNotificationService.reconcile(appContext, runtime)
        }
    }
}
