// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.AppState
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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver

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

/**
 * Reports screen on/off changes so traffic stats services can pause sampling while the
 * screen is off. Registration and callbacks run on the service main thread.
 */
internal class ScreenInteractiveObserver(
    private val context: Context,
    private val onChange: (interactive: Boolean) -> Unit,
) {
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> onChange(true)
                Intent.ACTION_SCREEN_OFF -> onChange(false)
            }
        }
    }

    val isInteractive: Boolean
        get() = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
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

/**
 * Serializes start and stop requests for a traffic stats foreground service, which runs in the
 * `:service` process.
 *
 * After startForegroundService(), the service must call startForeground() before it is stopped;
 * a stopService() issued earlier makes the system crash the service process with
 * ForegroundServiceDidNotStartInTimeException. Each start request therefore carries an
 * acknowledgement that the service sends once it is in the foreground, and a stop requested
 * while a start is unacknowledged waits for it. A start whose acknowledgement never comes,
 * because the service process died, stops counting after [StartAcknowledgementTimeoutMillis].
 */
internal class TrafficStatsServiceRequests {
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingStarts = mutableSetOf<Any>()
    private var deferredStop: (() -> Unit)? = null
    private var stopped = false

    fun start(requestStart: (acknowledgement: ResultReceiver) -> Boolean) {
        synchronized(lock) {
            deferredStop = null
            stopped = false
            val token = Any()
            val acknowledgement = object : ResultReceiver(null) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) = acknowledge(token)
            }
            if (!requestStart(acknowledgement)) return
            pendingStarts += token
            mainHandler.postDelayed({ acknowledge(token) }, StartAcknowledgementTimeoutMillis)
        }
    }

    /**
     * Returns false, without stopping, when this process requested no start since its last stop:
     * status reads stop the service on every stopped result. The first stop in a process always
     * runs, since the service outlives the process that started it.
     */
    fun stop(stopService: () -> Unit): Boolean {
        synchronized(lock) {
            if (stopped) return false
            stopped = true
            if (pendingStarts.isEmpty()) stopService() else deferredStop = stopService
        }
        return true
    }

    private fun acknowledge(token: Any) {
        val stop = synchronized(lock) {
            if (!pendingStarts.remove(token) || pendingStarts.isNotEmpty()) return
            deferredStop.also { deferredStop = null }
        }
        stop?.invoke()
    }
}

internal fun Intent.putTrafficStatsStartAcknowledgement(acknowledgement: ResultReceiver): Intent {
    return putExtra(ExtraStartAcknowledgement, acknowledgement)
}

/** Sent by the service for each start request once it reached startForeground(). */
internal fun Intent.acknowledgeTrafficStatsStart() {
    @Suppress("DEPRECATION")
    getParcelableExtra<ResultReceiver>(ExtraStartAcknowledgement)?.send(0, null)
}

private const val ExtraStartAcknowledgement = "start_acknowledgement"
private const val StartAcknowledgementTimeoutMillis = 15_000L

internal data class MihomoTrafficStatsRuntime(
    val control: MihomoControlConfig,
    val useBridge: Boolean,
    val nodeName: String = "",
)

internal fun AppState.toMihomoTrafficStatsRuntime(
    context: Context,
    runMode: Int = this.runMode,
    rawConfig: MihomoRawConfigSnapshot? = null,
): MihomoTrafficStatsRuntime? {
    if (!enableTrafficStatsNotification) return null
    val usesRawConfig = usesRawMihomoConfig()
    val control = if (usesRawConfig) {
        rawConfig?.api?.value?.control ?: return null
    } else {
        mihomoControlConfig(context, runMode)
    }
    return MihomoTrafficStatsRuntime(
        control = control,
        useBridge = false,
        nodeName = selectedMihomoProfileOrNull()?.name.orEmpty(),
    )
}
