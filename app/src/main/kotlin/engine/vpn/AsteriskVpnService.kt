// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import app.R
import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import androidx.core.app.NotificationCompat
import engine.network.NetworkDefaults
import engine.proxy.LocalProxyLoopbackAddress
import engine.vpn.hevtun.HevTunRuntime
import features.boot.VpnResumeReceiver
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import system.AndroidLauncherIcon
import system.getInstalledApplicationsCompat
import utils.toTrimmedNonEmptyDistinctList
import java.io.Closeable
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

@SuppressLint("VpnServicePolicy")
class AsteriskVpnService : VpnService() {
    private var tunFileDescriptor: ParcelFileDescriptor? = null
    private var hevTunRuntime: HevTunRuntime? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var foregroundStarted = false

    private var resumeWatchdog: Runnable? = null

    @Volatile
    private var running = false

    /** Whether this instance received a lifecycle intent; a stats query alone must not keep it started. */
    private var lifecycleRequested = false
    private var session: Closeable? = null
    private val connectivityManager: ConnectivityManager by lazy {
        getSystemService(ConnectivityManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reply = intent?.serviceReply()
        if (intent?.action == AsteriskVpnServiceIntents.ACTION_QUERY_PROCESS_STATS) {
            val isRunning = running
            reply?.sendProcessStats(if (isRunning) VpnProcessStats.sampleCurrentProcess() else null)
            if (!isRunning && !lifecycleRequested) stopSelf(startId)
            return START_NOT_STICKY
        }
        lifecycleRequested = true
        when (intent?.action) {
            AsteriskVpnServiceIntents.ACTION_STOP -> {
                cancelResumeWatchdog()
                // Queue on the mutex in intent order, then run native work on IO.
                serviceScope.launch(Dispatchers.Main.immediate) {
                    val result = runCatching {
                        operationMutex.withLock {
                            withContext(Dispatchers.IO) { stopVpn() }
                        }
                    }.onFailure { error ->
                        AndroidAppLogger.warn(LogTag, "Failed to stop VPN Service", error)
                    }
                    reply?.sendServiceResult(result)
                    stopSelfOnMain(startId)
                }
            }

            AsteriskVpnServiceIntents.ACTION_FINISH_RESUME -> {
                cancelResumeWatchdog()
                serviceScope.launch(Dispatchers.Main.immediate) {
                    operationMutex.withLock {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                if (running) stopForegroundNotification() else stopVpn()
                            }
                        }.onFailure { error ->
                            AndroidAppLogger.warn(LogTag, "Failed to release the idle VPN Service", error)
                        }
                        reply?.sendServiceResult(result)
                        if (!running) stopSelfOnMain(startId)
                    }
                }
            }

            AsteriskVpnServiceIntents.ACTION_UPDATE_AGE_SECRET_KEY -> {
                val ageSecretKey = intent.readVpnServiceAgeSecretKey()
                serviceScope.launch(Dispatchers.Main.immediate) {
                    operationMutex.withLock {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                check(running) { "VPN Service is not running" }
                                AndroidMihomoRuntime.updateAgeSecretKey(ageSecretKey)
                            }
                        }
                        reply?.sendServiceResult(result)
                        if (!running) stopSelfOnMain(startId)
                    }
                }
            }

            AsteriskVpnServiceIntents.ACTION_START -> {
                cancelResumeWatchdog()
                val config = intent.readVpnServiceStartConfig()
                if (config == null) {
                    reply?.sendServiceResult(
                        Result.failure(IllegalStateException(getString(R.string.error_vpn_start_config_missing))),
                    )
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                serviceScope.launch(Dispatchers.Main.immediate) {
                    operationMutex.withLock {
                        withContext(Dispatchers.IO) {
                            val result = runCatching {
                                startVpn(config)
                                // A resumed start kept the service in the foreground until now;
                                // the system's VPN binding keeps the process alive from here on.
                                stopForegroundNotification()
                            }.onFailure { error ->
                                AndroidAppLogger.error(LogTag, "Failed to start VPN Service", error)
                                stopVpn()
                                stopSelfOnMain(startId)
                            }
                            reply?.sendServiceResult(result)
                        }
                    }
                }
            }

            // The boot receiver and the system's always-on VPN start carry no configuration.
            AsteriskVpnServiceIntents.ACTION_RESUME,
            SERVICE_INTERFACE,
            -> resumeVpn(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * Handles a start that only names the service: the boot receiver's resume request and the
     * system's always-on VPN start.
     *
     * The service must already be a foreground service before the start continues: a
     * `startForegroundService()` caller has five seconds to reach `startForeground()`, and the
     * app process may only start this service while it runs in the foreground. The app state
     * and the configuration compiler belong to the app process, so the start is handed back to
     * [VpnResumeReceiver], which starts the tunnel exactly like a manual start; the
     * [ACTION_START][AsteriskVpnServiceIntents.ACTION_START] that follows removes the start-up
     * notification again. A start that never arrives is released by the watchdog.
     */
    private fun resumeVpn(startId: Int) {
        // Without consent the foreground-service type is not allowed and the tunnel would fail.
        if (prepare(this) != null) {
            AndroidAppLogger.warn(LogTag, "Refused to resume VPN Service without VPN permission")
            stopSelfOnMain(startId)
            return
        }
        promoteToForeground()
        if (!foregroundStarted) {
            // The foreground-service type is not allowed for this app right now; a background
            // start would be killed by the system anyway, so fail cleanly.
            stopSelfOnMain(startId)
            return
        }
        if (running) {
            stopForegroundNotification()
            return
        }
        sendBroadcast(Intent(this, VpnResumeReceiver::class.java))
        cancelResumeWatchdog()
        val watchdog = Runnable {
            resumeWatchdog = null
            serviceScope.launch {
                operationMutex.withLock {
                    if (running) return@withLock
                    AndroidAppLogger.warn(LogTag, "Released the VPN Service because the resume was never completed")
                    stopVpn()
                    // stopSelf(id) leaves the service alone when a newer start arrived meanwhile.
                    stopSelfOnMain(startId)
                }
            }
        }
        resumeWatchdog = watchdog
        mainHandler.postDelayed(watchdog, ResumeWatchdogMillis)
    }

    private fun cancelResumeWatchdog() {
        resumeWatchdog?.let(mainHandler::removeCallbacks)
        resumeWatchdog = null
    }

    private fun promoteToForeground(): Boolean {
        if (foregroundStarted) return true
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                ForegroundChannelId,
                getString(R.string.settings_run_mode_vpn_service),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = NotificationCompat.Builder(this, ForegroundChannelId)
            .setSmallIcon(R.drawable.ic_qs_proxy)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.quick_settings_tile_running))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    AndroidLauncherIcon.launchIntent(this),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val promoted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    ForegroundNotificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                )
            } else {
                startForeground(ForegroundNotificationId, notification)
            }
        }.onFailure { error ->
            AndroidAppLogger.error(LogTag, "Failed to promote VPN Service to the foreground", error)
        }.isSuccess
        if (promoted) foregroundStarted = true
        return promoted
    }

    private fun stopForegroundNotification() {
        if (!foregroundStarted) return
        foregroundStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        cancelResumeWatchdog()
        runCatching {
            stopVpn()
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to stop VPN Service while destroying service", error)
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        runCatching {
            stopVpn()
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to stop VPN Service while revoking", error)
        }
        super.onRevoke()
    }

    private fun stopSelfOnMain(startId: Int) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            stopSelf(startId)
        } else {
            mainHandler.post { stopSelf(startId) }
        }
    }

    private fun startVpn(config: VpnServiceStartConfig) {
        // Keep the foreground promotion from resumeVpn() while clearing a previous session.
        stopVpn(removeForeground = false)
        session = runCatching {
            VpnSession.open(this, config.localProxyOptions)
        }.getOrElse { error ->
            throw IllegalStateException(getString(R.string.error_vpn_session_unavailable), error)
        }
        AndroidMihomoRuntime.prepareTunnel(
            context = this,
            config = config,
            markSocket = this::protect,
            querySocketUid = this::querySocketUid,
            subscribeCoreLogs = false,
        )
        val tunDescriptor = establishTun(config)
        tunFileDescriptor = tunDescriptor
        val hevConfig = config.hevSocks5TunnelConfig
        if (hevConfig == null) {
            AndroidMihomoRuntime.startNativeTun(
                config = config,
                tunFileDescriptor = tunDescriptor,
                markSocket = this::protect,
                querySocketUid = this::querySocketUid,
            )
            tunFileDescriptor = null
        } else {
            val tunFd = tunFileDescriptor?.fd ?: error(getString(R.string.error_vpn_tun_fd_unavailable))
            AndroidMihomoRuntime.startPreparedLocalProxy(
                config = config,
            )
            val runtime = hevTunRuntime ?: HevTunRuntime().also { hevTunRuntime = it }
            runtime.start(hevConfig, tunFd)
            AndroidAppLogger.info(LogTag, "Started Hev TUN with VPN file descriptor")
        }
        running = true
    }

    private fun establishTun(config: VpnServiceStartConfig): ParcelFileDescriptor {
        val builder = Builder()
            .setSession(config.sessionName)
            .setMtu(config.mtu)
            .addAddress(config.ipv4Address, config.ipv4PrefixLength)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        if (config.enableIpv6 && config.ipv6Address != null) {
            builder
                .addAddress(config.ipv6Address, config.ipv6PrefixLength)
        }

        builder.applyVpnRoutes(config)

        if (config.enableLocalDns) {
            config.dnsServers.forEach { dnsServer ->
                builder.addDnsServer(dnsServer)
            }
        }

        builder.applyApplicationPolicy(config)
        builder.applyAppendHttpProxy(config)

        return builder.establish() ?: error(getString(R.string.error_vpn_tunnel_establish_failed))
    }

    private fun Builder.applyVpnRoutes(config: VpnServiceStartConfig): Builder {
        addRoute(NetworkDefaults.IPV4_ANY_ADDRESS, 0)
        if (config.enableIpv6 && config.ipv6Address != null) {
            addRoute(NetworkDefaults.IPV6_ANY_ADDRESS, 0)
        }
        return this
    }

    private fun Builder.applyApplicationPolicy(config: VpnServiceStartConfig): Builder {
        val policy = config.applicationPolicy
        val selfPackageName = packageName
        when (policy.mode) {
            ProxyAppListModeWhitelist -> {
                val allowedCount = addAllowedApplications(policy.packageNames.filterNot { it.trim() == selfPackageName })
                if (allowedCount == 0) {
                    // An empty allowed list means "all apps" to Android, so use a full deny list instead.
                    addDisallowedApplications(installedPackageNames())
                }
            }

            ProxyAppListModeBlacklist -> {
                addDisallowedApplications(policy.packageNames + selfPackageName)
            }

            ProxyAppListModeGlobal -> {
                addDisallowedApplications(listOf(selfPackageName))
            }

            else -> Unit
        }
        AndroidAppLogger.info(LogTag, "Excluded self package from VPN routing: $selfPackageName")
        return this
    }

    private fun Builder.applyAppendHttpProxy(config: VpnServiceStartConfig): Builder {
        if (config.appendHttpProxyOptions.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setHttpProxy(ProxyInfo.buildDirectProxy(LocalProxyLoopbackAddress, config.appendHttpProxyOptions.port))
            AndroidAppLogger.info(
                LogTag,
                "Appended VPN HTTP proxy: $LocalProxyLoopbackAddress:${config.appendHttpProxyOptions.port}",
            )
        }
        return this
    }

    private fun Builder.addAllowedApplications(packageNames: List<String>): Int {
        return packageNames.toTrimmedNonEmptyDistinctList().count { packageName ->
            addApplicationIfInstalled(packageName) {
                addAllowedApplication(packageName)
            }
        }
    }

    private fun Builder.addDisallowedApplications(packageNames: List<String>): Int {
        return packageNames.toTrimmedNonEmptyDistinctList().count { packageName ->
            addApplicationIfInstalled(packageName) {
                addDisallowedApplication(packageName)
            }
        }
    }

    private fun addApplicationIfInstalled(packageName: String, addApplication: () -> Unit): Boolean {
        return runCatching {
            addApplication()
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                if (error is PackageManager.NameNotFoundException) {
                    false
                } else {
                    throw error
                }
            },
        )
    }

    private fun installedPackageNames(): List<String> {
        return packageManager.getInstalledApplicationsCompat()
            .map { applicationInfo -> applicationInfo.packageName }
            .distinct()
    }

    private fun stopVpn(removeForeground: Boolean = true) {
        stopNativeRuntimes()
        runCatching {
            tunFileDescriptor?.close()
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to close VPN TUN file descriptor", error)
        }
        tunFileDescriptor = null
        running = false
        runCatching {
            session?.close()
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to close the VPN session", error)
        }
        session = null
        if (removeForeground) stopForegroundNotification()
    }

    private fun stopNativeRuntimes() {
        val tasks = buildList {
            add("Hev TUN" to { hevTunRuntime?.stop() })
            add("mihomo" to { AndroidMihomoRuntime.stop(resetCore = true) })
        }
        val completion = CountDownLatch(tasks.size)
        val threads = tasks.map { (name, action) ->
            Thread({
                runCatching {
                    action()
                }.onFailure { error ->
                    AndroidAppLogger.warn(LogTag, "Failed to stop $name while stopping VPN Service", error)
                }.also {
                    completion.countDown()
                }
            }, "$LogTag-$name").apply {
                isDaemon = true
            }.also { thread ->
                thread.start()
            }
        }

        if (!completion.await(RuntimeShutdownTimeoutMillis, TimeUnit.MILLISECONDS)) {
            threads.filter(Thread::isAlive).forEach { thread ->
                AndroidAppLogger.warn(
                    LogTag,
                    "Timed out stopping VPN runtime after ${RuntimeShutdownTimeoutMillis}ms: ${thread.name}",
                )
            }
            // Reclaim the native resources and session lock together if shutdown stalls.
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(0)
        }
    }

    private fun querySocketUid(
        protocol: Int,
        source: InetSocketAddress,
        target: InetSocketAddress,
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return InvalidUid
        }
        return runCatching {
            connectivityManager.getConnectionOwnerUid(protocol, source, target)
        }.getOrElse {
            InvalidUid
        }
    }

    companion object {
        private const val LogTag = "AsteriskVpnService"
        private const val InvalidUid = -1
        private const val RuntimeShutdownTimeoutMillis = 1_000L
        private const val ForegroundChannelId = "vpn-service"
        private const val ForegroundNotificationId = 4

        private const val StartTimeoutMillis = 10_000L
        private const val StopTimeoutMillis = 5_000L
        private const val ProcessStatsTimeoutMillis = 2_000L
        private const val ResumeWatchdogMillis = 60_000L

        internal suspend fun start(context: Context, config: VpnServiceStartConfig) {
            awaitServiceCall(context, AsteriskVpnServiceIntents.startIntent(context, config), StartTimeoutMillis)
        }

        internal suspend fun stop(context: Context) {
            awaitServiceCall(context, AsteriskVpnServiceIntents.stopIntent(context), StopTimeoutMillis)
        }

        internal suspend fun finishResume(context: Context) {
            awaitServiceCall(context, AsteriskVpnServiceIntents.finishResumeIntent(context), StopTimeoutMillis)
        }

        internal suspend fun updateAgeSecretKey(context: Context, ageSecretKey: String?) {
            awaitServiceCall(
                context,
                AsteriskVpnServiceIntents.updateAgeSecretKeyIntent(context, ageSecretKey),
                StopTimeoutMillis,
            )
        }

        internal fun isRunning(context: Context): Boolean = VpnSession.isRunning(context)

        /** Samples the service process; probing the session first keeps a query from starting an idle service. */
        internal suspend fun queryProcessStats(context: Context): VpnProcessStats? {
            if (!isRunning(context)) return null
            return queryServiceProcessStats(context, ProcessStatsTimeoutMillis)
        }

        /** Must match `android:process` of this service in the manifest. */
        internal const val ServiceProcessSuffix = ":vpn"

        /** Whether [application] runs in this service's own process, which hosts the core only. */
        internal fun isServiceProcess(application: Application): Boolean {
            return currentProcessName(application) == application.packageName + ServiceProcessSuffix
        }

        private fun currentProcessName(application: Application): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()?.takeIf(String::isNotEmpty)?.let { return it }
            }
            // argv is NUL separated; its first entry is the process name.
            runCatching { File("/proc/self/cmdline").readBytes() }.getOrNull()
                ?.toString(Charsets.UTF_8)
                ?.substringBefore('\u0000')
                ?.takeIf(String::isNotEmpty)
                ?.let { return it }
            AndroidAppLogger.platformWarn(LogTag, "Unable to resolve the process name; assuming the app process")
            return application.packageName
        }
    }
}
