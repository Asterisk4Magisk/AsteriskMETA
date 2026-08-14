// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.config

import engine.proxy.LocalProxyOptions
import engine.network.NetworkLimits
import engine.root.daemon.config.AsteriskdConfig

const val RootBpf2SocksDefaultBridgePort = NetworkLimits.PORT_MAX - 3
internal const val DefaultRootTun2SocksProxyPort = NetworkLimits.PORT_MAX - 1

internal data class RootConfigRuntimePaths(
    val coreExecutablePath: String,
    val coreConfigPath: String,
    val matcherExecutablePath: String,
    val bpf2SocksExecutablePath: String,
    val hevSocks5TunnelExecutablePath: String,
    val workingDirectory: String,
    val statePath: String,
    val logPath: String,
)

internal class RootStartConfig(
    val mihomoProfileBytes: ByteArray,
    val ageSecretKey: String?,
    val publicationStagingDirectory: String,
    val runtimePaths: RootConfigRuntimePaths,
    val directCidrIpv4Path: String,
    val directCidrIpv6Path: String,
    val enableIpv6: Boolean,
    val enableRootIpv6Disabler: Boolean,
    val enableLocalDns: Boolean,
    val enableFakeIp: Boolean,
    val fakeIpIpv4Pool: String,
    val enableBoot: Boolean,
) {
    init {
        ageSecretKey?.let { key ->
            require(key.isNotEmpty() && key.toByteArray(Charsets.UTF_8).size in 1..4095)
            require(key.none { character -> character == '\u0000' || character == '\r' || character == '\n' })
        }
    }

    val configPath: String
        get() = runtimePaths.coreConfigPath

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RootStartConfig) return false
        return mihomoProfileBytes.contentEquals(other.mihomoProfileBytes) &&
            ageSecretKey == other.ageSecretKey &&
            publicationStagingDirectory == other.publicationStagingDirectory &&
            runtimePaths == other.runtimePaths &&
            directCidrIpv4Path == other.directCidrIpv4Path &&
            directCidrIpv6Path == other.directCidrIpv6Path &&
            enableIpv6 == other.enableIpv6 &&
            enableRootIpv6Disabler == other.enableRootIpv6Disabler &&
            enableLocalDns == other.enableLocalDns &&
            enableFakeIp == other.enableFakeIp &&
            fakeIpIpv4Pool == other.fakeIpIpv4Pool &&
            enableBoot == other.enableBoot
    }

    override fun hashCode(): Int {
        var result = mihomoProfileBytes.contentHashCode()
        result = 31 * result + ageSecretKey.hashCode()
        result = 31 * result + publicationStagingDirectory.hashCode()
        result = 31 * result + runtimePaths.hashCode()
        result = 31 * result + directCidrIpv4Path.hashCode()
        result = 31 * result + directCidrIpv6Path.hashCode()
        result = 31 * result + enableIpv6.hashCode()
        result = 31 * result + enableRootIpv6Disabler.hashCode()
        result = 31 * result + enableLocalDns.hashCode()
        result = 31 * result + enableFakeIp.hashCode()
        result = 31 * result + fakeIpIpv4Pool.hashCode()
        result = 31 * result + enableBoot.hashCode()
        return result
    }

    override fun toString(): String = "RootStartConfig(runtimePaths=$runtimePaths, ageSecretKey=<redacted>)"
}

internal data class RootModeStartConfig(
    val root: RootStartConfig,
    val localProxyOptions: LocalProxyOptions?,
    val asteriskdConfig: AsteriskdConfig,
)
