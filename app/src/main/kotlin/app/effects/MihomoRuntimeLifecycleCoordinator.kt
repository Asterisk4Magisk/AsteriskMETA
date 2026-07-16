// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import app.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal interface MihomoRuntimeLifecycleTarget {
    fun resumeForForeground(appState: AppState)
    fun pauseForBackground(appState: AppState, releaseStandby: Boolean)
}

internal class MihomoRuntimeLifecycleCoordinator(
    private val appScope: CoroutineScope,
    private val target: MihomoRuntimeLifecycleTarget,
    private val backgroundGraceMillis: Long = DefaultBackgroundGraceMillis,
) {
    private val lock = Any()
    private var stage = RuntimeLifecycleStage.BackgroundPaused
    private var latestAppState: AppState? = null
    private var backgroundJob: Job? = null

    fun updateAppState(appState: AppState) {
        val shouldResume = synchronized(lock) {
            latestAppState = appState
            stage == RuntimeLifecycleStage.Foreground
        }
        if (shouldResume) target.resumeForForeground(appState)
    }

    fun onForeground() {
        val state = synchronized(lock) {
            backgroundJob?.cancel()
            backgroundJob = null
            stage = RuntimeLifecycleStage.Foreground
            latestAppState
        }
        state?.let(target::resumeForForeground)
    }

    fun onBackground() {
        synchronized(lock) {
            if (stage != RuntimeLifecycleStage.Foreground) return
            stage = RuntimeLifecycleStage.BackgroundGrace
            backgroundJob?.cancel()
            backgroundJob = appScope.launch {
                delay(backgroundGraceMillis.milliseconds)
                val state = synchronized(lock) {
                    if (stage != RuntimeLifecycleStage.BackgroundGrace) return@launch
                    stage = RuntimeLifecycleStage.BackgroundPaused
                    backgroundJob = null
                    latestAppState
                } ?: return@launch
                target.pauseForBackground(
                    appState = state,
                    releaseStandby = !state.proxyRunning,
                )
            }
        }
    }

    fun close() {
        synchronized(lock) {
            backgroundJob?.cancel()
            backgroundJob = null
            stage = RuntimeLifecycleStage.BackgroundPaused
        }
    }
}

private enum class RuntimeLifecycleStage {
    Foreground,
    BackgroundGrace,
    BackgroundPaused,
}

internal const val DefaultBackgroundGraceMillis = 5_000L
