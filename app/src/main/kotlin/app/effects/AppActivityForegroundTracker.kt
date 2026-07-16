// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import android.app.Activity
import android.app.Application
import android.os.Bundle

internal class AppActivityForegroundTracker(
    private val coordinator: MihomoRuntimeLifecycleCoordinator,
) : Application.ActivityLifecycleCallbacks {
    private var startedActivityCount = 0

    override fun onActivityStarted(activity: Activity) {
        val becameForeground = synchronized(this) {
            startedActivityCount += 1
            startedActivityCount == 1
        }
        if (becameForeground) coordinator.onForeground()
    }

    override fun onActivityStopped(activity: Activity) {
        val becameBackground = synchronized(this) {
            startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            startedActivityCount == 0
        }
        if (becameBackground) coordinator.onBackground()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
