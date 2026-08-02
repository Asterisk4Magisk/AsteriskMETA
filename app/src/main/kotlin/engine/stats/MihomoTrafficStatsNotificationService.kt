// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package engine.stats

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.AsteriskApplication
import app.R
import engine.mihomo.MihomoControlConfig
import engine.mihomo.runtime.MihomoControlClient
import engine.mihomo.runtime.MihomoProxiesState
import engine.mihomo.runtime.MihomoRuntimeRepository
import engine.mihomo.runtime.MihomoTrafficSample
import features.logs.AndroidAppLogger
import java.math.BigInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import utils.toReadableBytes
import kotlin.time.Duration.Companion.milliseconds

private const val ActionStart = "engine.stats.action.START"
private const val ActionStop = "engine.stats.action.STOP"
private const val ExtraHost = "host"
private const val ExtraPort = "port"
private const val ExtraSecret = "secret"
private const val ExtraUseBridge = "use_bridge"
private const val ExtraNodeName = "node_name"
private const val ChannelId = "mihomo_traffic_stats"
private const val NotificationId = 3001
private const val LogTag = "MihomoTrafficStats"
private const val StreamRestartDelayMillis = 1_000L
private const val NodeRefreshIntervalMillis = 60_000L
private const val MaxConsecutiveFailures = 5

class MihomoTrafficStatsNotificationService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val client = MihomoControlClient()
    private var monitorJob: Job? = null
    private var activeRuntime: MihomoTrafficStatsRuntime? = null
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val contentIntent by lazy {
        packageManager
            .getLaunchIntentForPackage(packageName)
            ?.let { intent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStop) {
            stopStats()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Runtime values are supplied by the owning proxy engine. Reconstructing them
        // from application settings would violate raw-config isolation after a sticky
        // service restart because the YAML controller and secret are intentionally not
        // persisted outside the profile content.
        val runtime = intent?.readRuntime()
        if (runtime == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (activeRuntime == runtime && monitorJob?.isActive == true) {
            return START_NOT_STICKY
        }

        activeRuntime = runtime
        startForegroundCompat(buildNotification(runtime.nodeName, TrafficStatsSnapshot()))
        startMonitor(runtime)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopStats()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun startMonitor(runtime: MihomoTrafficStatsRuntime) {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val repository = (application as AsteriskApplication).mihomoRuntime
            val accumulator = TrafficStatsAccumulator()
            var nodeName = runtime.nodeName.ifBlank { getString(R.string.proxy_traffic_stats_notification_title) }
            var lastNodeRefreshAt = 0L
            var consecutiveFailures = 0

            while (currentCoroutineContext().isActive) {
                val result = runCatching {
                    trafficSamples(runtime, repository).collect { sample ->
                        consecutiveFailures = 0
                        val now = System.currentTimeMillis()
                        val repositoryState = repository.state.value
                        repositoryState.proxies
                            .currentProxyNodeName(repositoryState.configs.mode)
                            ?.let { currentNode -> nodeName = currentNode }
                        if (!repositoryState.traffic.connected &&
                            now - lastNodeRefreshAt >= NodeRefreshIntervalMillis
                        ) {
                            nodeName = resolveCurrentNodeName(runtime, nodeName)
                            lastNodeRefreshAt = now
                        }
                        updateNotification(nodeName, accumulator.accept(sample))
                    }
                }

                result.onFailure { error ->
                    if (error is CancellationException) throw error
                    AndroidAppLogger.debug(LogTag, "Traffic stats notification stream failed: ${error.message}")
                }
                consecutiveFailures += 1
                if (consecutiveFailures >= MaxConsecutiveFailures) {
                    AndroidAppLogger.warn(LogTag, "Stopping traffic stats notification after repeated stream failures")
                    stopSelf()
                    return@launch
                }
                delay(StreamRestartDelayMillis.milliseconds)
            }
        }
    }

    private fun trafficSamples(
        runtime: MihomoTrafficStatsRuntime,
        repository: MihomoRuntimeRepository,
    ): Flow<MihomoTrafficSample> {
        return repository.state
            .map { state -> state.traffic.connected }
            .distinctUntilChanged()
            .flatMapLatest { repositoryConnected ->
                AndroidAppLogger.debug(
                    LogTag,
                    if (repositoryConnected) {
                        "Using foreground runtime traffic stream"
                    } else {
                        "Using notification-owned traffic stream"
                    },
                )
                if (repositoryConnected) {
                    repository.state
                        .map { state -> state.traffic }
                        .distinctUntilChanged()
                        .filter { traffic -> traffic.connected }
                        .map { traffic ->
                            traffic.latest.copy(
                                totalUp = traffic.totalUp,
                                totalDown = traffic.totalDown,
                            )
                        }
                } else {
                    client.traffic(runtime.control, runtime.useBridge)
                }
            }
    }

    private suspend fun resolveCurrentNodeName(
        runtime: MihomoTrafficStatsRuntime,
        fallback: String,
    ): String {
        return runCatching {
            val configs = client.getConfigs(runtime.control, runtime.useBridge)
            val proxies = client.getProxies(runtime.control, runtime.useBridge, configs.mode)
            proxies.currentProxyNodeName(configs.mode) ?: fallback
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            fallback
        }
    }

    private fun updateNotification(nodeName: String, snapshot: TrafficStatsSnapshot) {
        notificationManager.notify(NotificationId, buildNotification(nodeName, snapshot))
    }

    private fun buildNotification(
        nodeName: String,
        snapshot: TrafficStatsSnapshot,
    ): Notification {
        val title = nodeName.ifBlank { getString(R.string.proxy_traffic_stats_notification_title) }
        val speedLine = getString(
            R.string.proxy_traffic_stats_notification_speed,
            "${snapshot.upSpeed.toReadableBytes(keepTrailingZero = true)}/s",
            "${snapshot.downSpeed.toReadableBytes(keepTrailingZero = true)}/s",
        )
        val trafficLine = getString(
            R.string.proxy_traffic_stats_notification_traffic,
            snapshot.totalUp.toReadableBytes(keepTrailingZero = true),
            snapshot.totalDown.toReadableBytes(keepTrailingZero = true),
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, ChannelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(speedLine)
            .setStyle(Notification.BigTextStyle().bigText("$speedLine\n$trafficLine"))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    private fun stopStats() {
        monitorJob?.cancel()
        monitorJob = null
        activeRuntime = null
        stopForegroundCompat()
        notificationManager.cancel(NotificationId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                getString(R.string.proxy_traffic_stats_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationId, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        internal fun reconcile(context: Context, runtime: MihomoTrafficStatsRuntime?) {
            if (runtime == null) {
                stop(context)
            } else {
                start(context, runtime)
            }
        }

        internal fun start(context: Context, runtime: MihomoTrafficStatsRuntime) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, MihomoTrafficStatsNotificationService::class.java).apply {
                action = ActionStart
                putRuntime(runtime)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }

        internal fun stop(context: Context) {
            val appContext = context.applicationContext
            appContext.stopService(Intent(appContext, MihomoTrafficStatsNotificationService::class.java).apply {
                action = ActionStop
            })
            appContext.getSystemService(NotificationManager::class.java).cancel(NotificationId)
        }
    }
}

internal data class TrafficStatsSnapshot(
    val upSpeed: Long = 0L,
    val downSpeed: Long = 0L,
    val totalUp: Long = 0L,
    val totalDown: Long = 0L,
)

private data class TrafficSpeedSample(
    val up: Long,
    val down: Long,
)

internal class TrafficStatsAccumulator {
    private var totalUp = 0L
    private var totalDown = 0L
    private val speedSamples = ArrayDeque<TrafficSpeedSample>(TrafficSpeedWindowSamples)

    fun accept(sample: MihomoTrafficSample): TrafficStatsSnapshot {
        totalUp = sample.totalUp ?: (totalUp + sample.up)
        totalDown = sample.totalDown ?: (totalDown + sample.down)
        if (speedSamples.size == TrafficSpeedWindowSamples) speedSamples.removeFirst()
        speedSamples.addLast(TrafficSpeedSample(up = sample.up, down = sample.down))
        return TrafficStatsSnapshot(
            upSpeed = speedSamples.averageOf(TrafficSpeedSample::up),
            downSpeed = speedSamples.averageOf(TrafficSpeedSample::down),
            totalUp = totalUp,
            totalDown = totalDown,
        )
    }

    private fun ArrayDeque<TrafficSpeedSample>.averageOf(
        value: (TrafficSpeedSample) -> Long,
    ): Long {
        val sum = fold(BigInteger.ZERO) { total, sample ->
            total.add(BigInteger.valueOf(value(sample)))
        }
        return sum.divide(BigInteger.valueOf(size.toLong())).toLong()
    }
}

private const val TrafficSpeedWindowSamples = 3

private fun MihomoProxiesState.currentProxyNodeName(mode: String): String? {
    if (mode.equals("direct", ignoreCase = true)) {
        return "DIRECT"
    }
    return groups
        .firstOrNull { group -> group.name.equals("GLOBAL", ignoreCase = true) && group.now.isNotBlank() }
        ?.now
        ?: groups.firstOrNull { group -> group.now.isNotBlank() }?.now
}

private fun Intent.putRuntime(runtime: MihomoTrafficStatsRuntime) {
    putExtra(ExtraHost, runtime.control.host)
    putExtra(ExtraPort, runtime.control.port)
    putExtra(ExtraSecret, runtime.control.secret)
    putExtra(ExtraScheme, runtime.control.scheme)
    putExtra(ExtraUseBridge, runtime.useBridge)
    putExtra(ExtraNodeName, runtime.nodeName)
}

private fun Intent.readRuntime(): MihomoTrafficStatsRuntime? {
    val host = getStringExtra(ExtraHost)?.takeIf(String::isNotBlank) ?: return null
    val port = getIntExtra(ExtraPort, 0).takeIf { value -> value in 1..65535 } ?: return null
    return MihomoTrafficStatsRuntime(
        control = MihomoControlConfig(
            host = host,
            port = port,
            secret = getStringExtra(ExtraSecret).orEmpty(),
            scheme = getStringExtra(ExtraScheme).orEmpty().takeIf { it in setOf("http", "https") } ?: "http",
        ),
        useBridge = getBooleanExtra(ExtraUseBridge, true),
        nodeName = getStringExtra(ExtraNodeName).orEmpty(),
    )
}

private const val ExtraScheme = "mihomo_control_scheme"
