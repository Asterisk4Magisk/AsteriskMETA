// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import app.R
import engine.mihomo.binding.MihomoBridge as Clash
import engine.mihomo.binding.model.ConfigurationOverride
import engine.mihomo.MihomoCoreLogSubscriber
import features.logs.AndroidAppLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.ConnectException
import java.net.InetSocketAddress
import kotlin.time.Duration.Companion.milliseconds

internal object AndroidMihomoRuntime {
    @Volatile
    private var loaded = false

    @Volatile
    private var running = false

    @Volatile
    var serviceStartedElapsedMillis = 0L
        private set

    @Volatile
    private var nativeTunRunning = false

    @Volatile
    private var tunContextRunning = false

    @Volatile
    private var activeProfile: LoadedMihomoProfile? = null

    @Volatile
    private var owner = MihomoRuntimeOwner.None

    private var coreLogSubscriber: MihomoCoreLogSubscriber? = null

    // App-process fence until the service answers a dispatched START. The
    // session lock takes over once the service has acquired native ownership.
    private var pendingVpnStart: Any? = null

    @Synchronized
    fun dispatchServiceCall(isStart: Boolean, dispatch: (onReply: () -> Unit) -> Unit) {
        val previous = pendingVpnStart
        val token = if (isStart) Any() else previous
        if (isStart) pendingVpnStart = token
        try {
            if (isStart) releaseStandby()
            dispatch {
                synchronized(this) {
                    // A late reply must not release a newer START's fence.
                    if (pendingVpnStart === token) pendingVpnStart = null
                }
            }
        } catch (error: Throwable) {
            if (pendingVpnStart === token) pendingVpnStart = previous
            throw error
        }
    }

    @Synchronized
    fun ensureLoaded(
        context: Context,
        config: VpnServiceStartConfig,
    ): Boolean {
        if (pendingVpnStart != null || VpnSession.isRunning(context)) {
            throw ConnectException("VPN session owns the Mihomo runtime")
        }
        val previousProfile = activeProfile
        ensureLoadedLocked(context, config, tunCallbacks = null, subscribeCoreLogs = true)
        if (!running) owner = MihomoRuntimeOwner.Standby
        return activeProfile !== previousProfile
    }

    @Synchronized
    fun prepareTunnel(
        context: Context,
        config: VpnServiceStartConfig,
        markSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int,
        subscribeCoreLogs: Boolean,
    ) {
        ensureLoadedLocked(context, config, TunCallbacks(markSocket, querySocketUid), subscribeCoreLogs)
    }

    @Synchronized
    fun startNativeTun(
        config: VpnServiceStartConfig,
        tunFileDescriptor: ParcelFileDescriptor,
        markSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int,
    ) {
        runCatching {
            tunFileDescriptor.use {
                Clash.startTun(
                    fd = it.fd,
                    stack = config.mihomoTunStack,
                    congestionController = config.mihomoTunCongestionController,
                    gateway = config.tunGatewayAddresses(),
                    portal = "",
                    dns = if (config.enableLocalDns) VpnDefaults.IPV4_DNS_HIJACK_ALL else "",
                    markSocket = markSocket,
                    querySocketUid = querySocketUid,
                )
            }
        }.onFailure { error ->
            coreLogSubscriber?.stop()
            coreLogSubscriber = null
            throw error
        }
        markProxyRunning(nativeTun = true)
        AndroidAppLogger.info(LogTag, "Started mihomo VPN runtime with profile ${config.mihomoProfilePath}")
    }

    @Synchronized
    fun startPreparedLocalProxy(
        config: VpnServiceStartConfig,
    ) {
        markProxyRunning(nativeTun = false)
        AndroidAppLogger.info(LogTag, "Started mihomo VPN runtime without native TUN using profile ${config.mihomoProfilePath}")
    }

    @Synchronized
    fun stop(resetCore: Boolean = true) {
        if (!loaded) {
            serviceStartedElapsedMillis = 0L
            running = false
            nativeTunRunning = false
            tunContextRunning = false
            activeProfile = null
            owner = MihomoRuntimeOwner.None
            runCatching { Clash.setAgeSecretKey(null) }
                .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to clear mihomo age secret key", error) }
            coreLogSubscriber?.stop()
            coreLogSubscriber = null
            return
        }
        val shouldStopTun = nativeTunRunning
        val shouldStopTunContext = tunContextRunning
        serviceStartedElapsedMillis = 0L
        running = false
        nativeTunRunning = false
        tunContextRunning = false
        owner = MihomoRuntimeOwner.None
        if (shouldStopTun) {
            runCatching { Clash.stopTun() }
                .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to stop mihomo TUN runtime", error) }
        }
        if (shouldStopTunContext) {
            runCatching { Clash.stopTunContext() }
                .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to stop mihomo TUN context", error) }
        }
        if (resetCore) {
            runCatching { Clash.reset() }
                .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to reset mihomo runtime", error) }
            runCatching { Clash.clearOverride(Clash.OverrideSlot.Session) }
                .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to clear mihomo session override", error) }
            runCatching { Clash.setAgeSecretKey(null) }
                .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to clear mihomo age secret key", error) }
            coreLogSubscriber?.stop()
            coreLogSubscriber = null
            loaded = false
            activeProfile = null
        }
    }

    fun isRunning(): Boolean {
        return running
    }

    fun isLoaded(): Boolean {
        return loaded
    }

    @Synchronized
    fun releaseStandby(): Boolean {
        if (!owner.canLifecycleRelease()) return false
        stop(resetCore = true)
        return true
    }

    suspend fun reloadProfile() {
        val profile = activeProfile ?: error("mihomo profile is not loaded")
        setRuntimeAgeSecretKey(profile.signature.ageSecretKey)
        withTimeout(DefaultLoadTimeoutMillis.milliseconds) {
            loadProfile(profile.directory, profile.content)
        }
    }

    @Synchronized
    fun updateAgeSecretKey(ageSecretKey: String?) {
        val profile = checkNotNull(activeProfile) { "Mihomo profile is not loaded" }
        val key = ageSecretKey.orEmpty()
        setRuntimeAgeSecretKey(key)
        activeProfile = LoadedMihomoProfile(
            directory = profile.directory,
            content = profile.content,
            signature = profile.signature.copy(ageSecretKey = key),
        )
    }

    private fun ensureLoadedLocked(
        context: Context,
        config: VpnServiceStartConfig,
        tunCallbacks: TunCallbacks?,
        subscribeCoreLogs: Boolean,
    ) {
        val signature = config.runtimeConfigSignature()
        if (loaded && activeProfile?.signature == signature) {
            syncCoreLogSubscriber(subscribeCoreLogs)
            tunCallbacks?.let(::installTunCallbacks)
            return
        }
        stop(resetCore = true)

        val dataDir = config.dataDir.ifBlank {
            error(context.getString(R.string.error_mihomo_data_dir_missing))
        }
        val profileDir = File(dataDir).apply { mkdirs() }
        val profileFile = File(config.mihomoProfilePath)
        val profileContent = config.standbyProfileContent?.copyOf()
        if (profileContent?.isEmpty() == true ||
            (profileContent == null && (!profileFile.isFile || profileFile.length() <= 0L))
        ) {
            error("mihomo profile file is unavailable")
        }

        syncCoreLogSubscriber(subscribeCoreLogs)
        runCatching {
            runBlocking {
                withTimeout(DefaultLoadTimeoutMillis.milliseconds) {
                    // Both facades retain this context across reset/load.
                    tunCallbacks?.let(::installTunCallbacks)
                    Clash.reset()
                    Clash.clearOverride(Clash.OverrideSlot.Session)
                    config.sessionOverride?.let { Clash.patchOverride(Clash.OverrideSlot.Session, it) }
                    setRuntimeAgeSecretKey(config.ageSecretKey)
                    loadProfile(profileDir, profileContent)
                }
            }
        }.onFailure { error ->
            coreLogSubscriber?.stop()
            coreLogSubscriber = null
            loaded = false
            activeProfile = null
            running = false
            nativeTunRunning = false
            tunContextRunning = false
            runCatching { Clash.stopTunContext() }
            throw error
        }
        loaded = true
        serviceStartedElapsedMillis = 0L
        running = false
        nativeTunRunning = false
        tunContextRunning = tunCallbacks != null
        activeProfile = LoadedMihomoProfile(profileDir, profileContent, signature)
        val source = if (profileContent == null) "file" else "memory"
        AndroidAppLogger.info(LogTag, "Loaded mihomo runtime profile source=$source")
    }

    private fun syncCoreLogSubscriber(subscribe: Boolean) {
        if (subscribe) {
            if (coreLogSubscriber == null) {
                coreLogSubscriber = MihomoCoreLogSubscriber().also { it.start() }
            }
        } else {
            coreLogSubscriber?.stop()
            coreLogSubscriber = null
        }
    }

    private fun installTunCallbacks(callbacks: TunCallbacks) {
        Clash.startTunContext(
            markSocket = callbacks.markSocket,
            querySocketUid = callbacks.querySocketUid,
        )
        tunContextRunning = true
    }

    private fun markProxyRunning(nativeTun: Boolean) {
        serviceStartedElapsedMillis = SystemClock.elapsedRealtime()
        running = true
        nativeTunRunning = nativeTun
        tunContextRunning = true
        owner = MihomoRuntimeOwner.ProxyService
    }

    private suspend fun loadProfile(profileDir: File, content: ByteArray?) {
        if (content == null) Clash.load(profileDir).await() else Clash.load(profileDir, content).await()
    }

    private fun VpnServiceStartConfig.tunGatewayAddresses(): String {
        return buildList {
            add("$ipv4Address/$ipv4PrefixLength")
            if (enableIpv6 && ipv6Address != null) {
                add("$ipv6Address/$ipv6PrefixLength")
            }
        }.joinToString(",")
    }

    private const val LogTag = "AndroidMihomoRuntime"
    private const val DefaultLoadTimeoutMillis = 60_000L
}

private fun setRuntimeAgeSecretKey(ageSecretKey: String) {
    Clash.setAgeSecretKey(ageSecretKey.trim().takeIf(String::isNotBlank))
}

private data class TunCallbacks(
    val markSocket: (Int) -> Boolean,
    val querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int,
)

private data class MihomoRuntimeConfigSignature(
    val dataDir: String,
    val profilePath: String,
    val profileSignature: String,
    val ageSecretKey: String,
    val sessionOverride: ConfigurationOverride?,
    val inMemoryProfile: Boolean,
)

private class LoadedMihomoProfile(
    val directory: File,
    val content: ByteArray?,
    val signature: MihomoRuntimeConfigSignature,
)

private fun VpnServiceStartConfig.runtimeConfigSignature(): MihomoRuntimeConfigSignature {
    return MihomoRuntimeConfigSignature(
        dataDir = dataDir,
        profilePath = mihomoProfilePath,
        profileSignature = mihomoProfileSignature,
        ageSecretKey = ageSecretKey,
        sessionOverride = sessionOverride,
        inMemoryProfile = standbyProfileContent != null,
    )
}
