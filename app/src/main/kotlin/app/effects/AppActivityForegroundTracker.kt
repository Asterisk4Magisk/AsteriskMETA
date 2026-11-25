// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AppActivityForegroundTracker(
    private val coordinator: MihomoRuntimeLifecycleCoordinator,
) : Application.ActivityLifecycleCallbacks {
    private var startedActivityCount = 0
    private var overlayActive = false
    private var overlayConnectionsVisible = false
    private val mutableStatusWatchRequired = MutableStateFlow(false)
    val statusWatchRequired = mutableStatusWatchRequired.asStateFlow()

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount += 1
        updateObservation()
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        updateObservation()
    }

    fun setOverlayObservation(active: Boolean, connectionsVisible: Boolean) {
        overlayActive = active
        overlayConnectionsVisible = connectionsVisible
        updateObservation()
    }

    private fun updateObservation() {
        val observation = when {
            startedActivityCount > 0 -> RuntimeObservation.Foreground
            overlayActive && overlayConnectionsVisible -> RuntimeObservation.OverlayConnections
            overlayActive -> RuntimeObservation.Overlay
            else -> null
        }
        mutableStatusWatchRequired.value = observation != null
        coordinator.updateObservation(observation)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

internal enum class RuntimeObservation {
    Foreground,
    Overlay,
    OverlayConnections,
}
