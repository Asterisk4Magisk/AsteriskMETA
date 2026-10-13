// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import app.AppState
import app.modes.RunModeVpnService
import engine.mihomo.MihomoControlConfig
import engine.mihomo.mihomoControlConfig
import engine.mihomo.raw.MihomoRawConfigSnapshot
import engine.mihomo.raw.usesRawMihomoConfig
import engine.mihomo.selectedMihomoProfileOrNull
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns one traffic sampling session. Lifecycle methods are called on the service
 * main thread; workers can publish notification changes only through their session.
 */
internal class TrafficStatsMonitor(
    private val scope: CoroutineScope,
    private val lifecycleDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private var session: Session? = null
    private var job: Job? = null

    val isActive: Boolean
        get() = job?.isActive == true

    fun start(block: suspend Session.() -> Unit) {
        cancel()
        val next = Session()
        session = next
        job = scope.launch { next.block() }
    }

    fun cancel() {
        session = null
        job?.cancel()
        job = null
    }

    inner class Session internal constructor() {
        suspend fun publish(action: () -> Unit) {
            withContext(lifecycleDispatcher) {
                currentCoroutineContext().ensureActive()
                if (session === this@Session) action()
            }
        }
    }
}

internal fun tryStartTrafficStatsService(
    start: () -> Unit,
    onFailure: (Exception) -> Unit,
): Boolean {
    return try {
        start()
        true
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        onFailure(error)
        false
    }
}

internal data class MihomoTrafficStatsRuntime(
    val control: MihomoControlConfig,
    val useBridge: Boolean,
    val nodeName: String = "",
)

internal fun AppState.toMihomoTrafficStatsRuntime(
    runMode: Int = this.runMode,
    rawConfig: MihomoRawConfigSnapshot? = null,
): MihomoTrafficStatsRuntime? {
    if (!enableTrafficStatsNotification) return null
    val usesRawConfig = usesRawMihomoConfig()
    val control = if (usesRawConfig) {
        rawConfig?.api?.value?.control ?: return null
    } else {
        mihomoControlConfig()
    }
    return MihomoTrafficStatsRuntime(
        control = control,
        useBridge = !usesRawConfig && runMode == RunModeVpnService,
        nodeName = selectedMihomoProfileOrNull()?.name.orEmpty(),
    )
}
