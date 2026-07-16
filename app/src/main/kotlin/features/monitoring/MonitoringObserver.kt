// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.LocalAppServices

internal enum class MonitoringIntent {
    Home,
    Connections,
    Resource,
    Traffic,
    Network,
}

internal data class MonitoringPlan(
    val runtimeSummaryIntervalMillis: Long? = null,
    val connectionIntervalMillis: Long? = null,
    val resourceIntervalMillis: Long? = null,
    val trafficIntervalMillis: Long? = null,
    val observeLocalNetwork: Boolean = false,
    val networkPageVisible: Boolean = false,
)

internal fun resolveMonitoringPlan(counts: Map<MonitoringIntent, Int>): MonitoringPlan {
    fun observes(intent: MonitoringIntent): Boolean = counts[intent]?.let { count -> count > 0 } == true

    val home = observes(MonitoringIntent.Home)
    val connections = observes(MonitoringIntent.Connections)
    val resource = observes(MonitoringIntent.Resource)
    val traffic = observes(MonitoringIntent.Traffic)
    val network = observes(MonitoringIntent.Network)
    val any = home || connections || resource || traffic || network

    return MonitoringPlan(
        runtimeSummaryIntervalMillis = when {
            !any -> null
            home && !connections && !resource && !traffic && !network -> HomeMonitoringIntervalMillis
            else -> DetailMonitoringIntervalMillis
        },
        connectionIntervalMillis = when {
            connections -> DetailMonitoringIntervalMillis
            home -> HomeMonitoringIntervalMillis
            else -> null
        },
        resourceIntervalMillis = when {
            resource -> DetailMonitoringIntervalMillis
            home -> HomeMonitoringIntervalMillis
            else -> null
        },
        trafficIntervalMillis = when {
            traffic -> DetailMonitoringIntervalMillis
            home -> HomeMonitoringIntervalMillis
            else -> null
        },
        observeLocalNetwork = home && !network,
        networkPageVisible = network,
    )
}

internal class MonitoringObserverRegistry(
    private val onChanged: (Map<MonitoringIntent, Int>) -> Unit = {},
) {
    private val counts = mutableMapOf<MonitoringIntent, Int>()

    @Synchronized
    fun acquire(intent: MonitoringIntent) {
        counts[intent] = counts.getOrDefault(intent, 0) + 1
        onChanged(counts.toMap())
    }

    @Synchronized
    fun release(intent: MonitoringIntent) {
        val current = counts[intent] ?: return
        if (current <= 1) {
            counts.remove(intent)
        } else {
            counts[intent] = current - 1
        }
        onChanged(counts.toMap())
    }

    @Synchronized
    fun snapshot(): Map<MonitoringIntent, Int> = counts.toMap()
}

internal class MonitoringObservation(
    private val onClose: () -> Unit,
) : AutoCloseable {
    private var closed = false

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        onClose()
    }
}

@Composable
internal fun ObserveMonitoring(intent: MonitoringIntent, pageSessionId: String? = null) {
    val repository = LocalAppServices.current.monitoring
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(repository, intent, pageSessionId, lifecycleOwner) {
        var observation: MonitoringObservation? = null
        fun synchronizeObservation() {
            val visible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (visible && observation == null) {
                observation = repository.observe(intent, pageSessionId)
            } else if (!visible) {
                observation?.close()
                observation = null
            }
        }
        val observer = LifecycleEventObserver { _, _ -> synchronizeObservation() }
        lifecycleOwner.lifecycle.addObserver(observer)
        synchronizeObservation()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            observation?.close()
        }
    }
}

private const val DetailMonitoringIntervalMillis = 1_000L
private const val HomeMonitoringIntervalMillis = 3_000L
