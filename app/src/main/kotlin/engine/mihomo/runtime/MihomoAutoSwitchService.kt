// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

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
import androidx.core.content.ContextCompat
import app.AppState
import app.AsteriskApplication
import app.R
import features.logs.AndroidAppLogger
import features.logs.AutoSwitchLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Keeps health checks alive in ROOT mode as well as VPN mode without owning either runtime. */
class MihomoAutoSwitchService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var logRepository: AutoSwitchLogRepository? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                getString(R.string.settings_auto_switch),
                NotificationManager.IMPORTANCE_LOW
            ),
        )
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = Notification.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_qs_proxy)
            .setContentTitle(getString(R.string.settings_auto_switch))
            .setContentText(getString(R.string.auto_switch_notification))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NotificationId, notification)
            }
        } catch (_: RuntimeException) {
            AndroidAppLogger.warn(
                "AutoSwitch",
                "Could not start automatic node health check service"
            )
            stopSelf()
            return
        }
        val app = application as AsteriskApplication
        logRepository = app.mihomoRuntime.autoSwitchLog
        scope.launch {
            app.stateStore.state
                .distinctUntilChanged { previous, next -> previous.autoSwitchSessionKey() == next.autoSwitchSessionKey() }
                .collectLatest { state ->
                    if (!state.shouldMonitorAutoSwitch()) {
                        stopSelf()
                        return@collectLatest
                    }
                    monitorMihomoAutoSwitch(
                        this@MihomoAutoSwitchService,
                        app.mihomoRuntime,
                        state
                    ) {
                        app.stateStore.state.value
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        logRepository?.stopMonitoring()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        internal fun reconcile(context: Context, enabled: Boolean) {
            val intent = Intent(context, MihomoAutoSwitchService::class.java)
            if (!enabled) {
                context.stopService(intent)
                return
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: RuntimeException) {
                AndroidAppLogger.warn(
                    "AutoSwitch",
                    "Could not launch automatic node health check service"
                )
            }
        }
    }
}

internal fun AppState.shouldMonitorAutoSwitch(): Boolean = proxyRunning && autoSwitch.enabled &&
        autoSwitch.profileId == selectedMihomoProfileId && autoSwitch.groups.isNotEmpty()

private const val ChannelId = "mihomo_auto_switch"
private const val NotificationId = 3002
