// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import android.os.ParcelFileDescriptor
import app.R
import engine.mihomo.binding.MihomoBridge as Clash
import engine.mihomo.MihomoCoreLogSubscriber
import features.logs.AndroidAppLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetSocketAddress
import kotlin.time.Duration.Companion.milliseconds

internal object AndroidMihomoRuntime {
    @Volatile
    private var loaded = false

    @Volatile
    private var running = false

    @Volatile
    private var nativeTunRunning = false

    @Volatile
    private var tunContextRunning = false

    @Volatile
    private var activeProfile: LoadedMihomoProfile? = null

    @Volatile
    private var owner = MihomoRuntimeOwner.None

    private var coreLogSubscriber: MihomoCoreLogSubscriber? = null

    @Synchronized
    fun ensureLoaded(
        context: Context,
        config: VpnServiceStartConfig,
        preserveActiveTun: Boolean = false,
    ) {
        if (preserveActiveTun && running) {
            return
        }
        ensureLoadedLocked(context, config)
        if (!running) owner = MihomoRuntimeOwner.Standby
    }

    @Synchronized
    fun start(
        context: Context,
        config: VpnServiceStartConfig,
        tunFileDescriptor: ParcelFileDescriptor,
        markSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int,
    ) {
        ensureLoadedLocked(context, config)

        runCatching {
            tunFileDescriptor.use {
                Clash.startTun(
                    fd = it.fd,
                    stack = config.mihomoTunStack,
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
        running = true
        nativeTunRunning = true
        tunContextRunning = true
        owner = MihomoRuntimeOwner.ProxyService
        AndroidAppLogger.info(LogTag, "Started mihomo VPN runtime with profile ${config.mihomoProfilePath}")
    }

    @Synchronized
    fun startLocalProxy(
        context: Context,
        config: VpnServiceStartConfig,
        markSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int,
    ) {
        ensureLoadedLocked(context, config)
        runCatching {
            Clash.startTunContext(
                markSocket = markSocket,
                querySocketUid = querySocketUid,
            )
        }.onFailure { error ->
            coreLogSubscriber?.stop()
            coreLogSubscriber = null
            throw error
        }
        running = true
        nativeTunRunning = false
        tunContextRunning = true
        owner = MihomoRuntimeOwner.ProxyService
        AndroidAppLogger.info(LogTag, "Started mihomo VPN runtime without native TUN using profile ${config.mihomoProfilePath}")
    }

    @Synchronized
    fun stop(resetCore: Boolean = true) {
        if (!loaded) {
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
        val shouldStopTunContext = !nativeTunRunning && tunContextRunning
        running = false
        nativeTunRunning = false
        tunContextRunning = false
        owner = MihomoRuntimeOwner.None
        if (shouldStopTun) {
            runCatching { Clash.stopTun() }
                .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to stop mihomo TUN runtime", error) }
        } else if (shouldStopTunContext) {
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

    private fun ensureLoadedLocked(
        context: Context,
        config: VpnServiceStartConfig,
    ) {
        val signature = config.runtimeConfigSignature()
        if (loaded && activeProfile?.signature == signature) {
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

        coreLogSubscriber?.stop()
        coreLogSubscriber = MihomoCoreLogSubscriber().also { subscriber -> subscriber.start() }
        runCatching {
            runBlocking {
                withTimeout(DefaultLoadTimeoutMillis.milliseconds) {
                    Clash.reset()
                    Clash.clearOverride(Clash.OverrideSlot.Session)
                    setRuntimeAgeSecretKey(config.ageSecretKey)
                    loadProfile(profileDir, profileContent)
                }
            }
        }.onFailure { error ->
            coreLogSubscriber?.stop()
            coreLogSubscriber = null
            loaded = false
            activeProfile = null
            throw error
        }
        loaded = true
        running = false
        nativeTunRunning = false
        tunContextRunning = false
        activeProfile = LoadedMihomoProfile(profileDir, profileContent, signature)
        val source = if (profileContent == null) "file" else "memory"
        AndroidAppLogger.info(LogTag, "Loaded mihomo runtime profile source=$source")
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

private data class MihomoRuntimeConfigSignature(
    val dataDir: String,
    val profilePath: String,
    val profileSignature: String,
    val ageSecretKey: String,
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
        inMemoryProfile = standbyProfileContent != null,
    )
}
