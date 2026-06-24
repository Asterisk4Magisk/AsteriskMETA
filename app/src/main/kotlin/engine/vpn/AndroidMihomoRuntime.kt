// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import android.os.ParcelFileDescriptor
import app.R
import com.github.kr328.clash.core.Clash
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
    private var activeProfileDir: File? = null

    @Volatile
    private var activeConfigSignature: MihomoRuntimeConfigSignature? = null

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

        val tunFd = tunFileDescriptor.detachFd()
        runCatching {
            Clash.startTun(
                fd = tunFd,
                stack = config.mihomoTunStack,
                gateway = config.tunGatewayAddresses(),
                portal = "",
                dns = if (config.enableLocalDns) VpnDefaults.IPV4_DNS_HIJACK_ALL else "",
                markSocket = markSocket,
                querySocketUid = querySocketUid,
            )
        }.onFailure { error ->
            coreLogSubscriber?.stop()
            coreLogSubscriber = null
            runCatching { ParcelFileDescriptor.adoptFd(tunFd).close() }
                .onFailure { closeError ->
                    AndroidAppLogger.warn(LogTag, "Failed to close detached TUN fd after mihomo start failure", closeError)
                }
            throw error
        }
        running = true
        AndroidAppLogger.info(LogTag, "Started mihomo VPN runtime with profile ${config.mihomoProfilePath}")
    }

    @Synchronized
    fun stop(resetCore: Boolean = true) {
        if (!loaded) {
            running = false
            activeProfileDir = null
            activeConfigSignature = null
            runCatching { Clash.setAgeSecretKey(null) }
                .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to clear mihomo age secret key", error) }
            coreLogSubscriber?.stop()
            coreLogSubscriber = null
            return
        }
        val shouldStopTun = running
        running = false
        if (shouldStopTun) {
            runCatching { Clash.stopTun() }
                .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to stop mihomo TUN runtime", error) }
        }
        runCatching { Clash.stopHttp() }
            .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to stop mihomo HTTP runtime", error) }
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
            activeProfileDir = null
            activeConfigSignature = null
        }
    }

    fun isRunning(): Boolean {
        return running
    }

    fun isLoaded(): Boolean {
        return loaded
    }

    suspend fun reloadProfile() {
        val profileDir = activeProfileDir ?: error("mihomo profile directory is not loaded")
        setRuntimeAgeSecretKey(activeConfigSignature?.ageSecretKey.orEmpty())
        withTimeout(DefaultLoadTimeoutMillis.milliseconds) {
            Clash.load(profileDir).await()
        }
    }

    private fun ensureLoadedLocked(
        context: Context,
        config: VpnServiceStartConfig,
    ) {
        val signature = config.runtimeConfigSignature()
        if (loaded && activeConfigSignature == signature) {
            return
        }
        stop(resetCore = true)

        val dataDir = config.dataDir.ifBlank {
            error(context.getString(R.string.error_mihomo_data_dir_missing))
        }
        val profileDir = File(dataDir).apply { mkdirs() }
        val profileFile = File(config.mihomoProfilePath)
        if (!profileFile.isFile || profileFile.length() <= 0L) {
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
                    Clash.load(profileDir).await()
                }
            }
        }.onFailure { error ->
            coreLogSubscriber?.stop()
            coreLogSubscriber = null
            loaded = false
            activeProfileDir = null
            activeConfigSignature = null
            throw error
        }
        loaded = true
        running = false
        activeProfileDir = profileDir
        activeConfigSignature = signature
        AndroidAppLogger.info(LogTag, "Loaded mihomo runtime profile ${profileFile.absolutePath}")
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
)

private fun VpnServiceStartConfig.runtimeConfigSignature(): MihomoRuntimeConfigSignature {
    return MihomoRuntimeConfigSignature(
        dataDir = dataDir,
        profilePath = mihomoProfilePath,
        profileSignature = mihomoProfileSignature,
        ageSecretKey = ageSecretKey,
    )
}
