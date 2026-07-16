// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import engine.proxy.LocalProxyOptions
import engine.mihomo.MihomoCoreLogPaths
import engine.mihomo.logDirectoryPath
import java.io.File

internal data class RootStartConfig(
    val mihomoProfileBytes: ByteArray,
    val ageSecretKey: String = "",
    val setuidgidPath: String,
    val runtimeLayout: RootRuntimeLayout,
    val enableIpv6: Boolean,
    val enableRootIpv6Disabler: Boolean,
    val enableLocalDns: Boolean,
    val enableFakeIp: Boolean,
    val fakeIpIpv4Pool: String,
    val coreLogPaths: MihomoCoreLogPaths,
) {
    val configPath: String
        get() = runtimeLayout.configPath

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RootStartConfig

        if (enableIpv6 != other.enableIpv6) return false
        if (enableRootIpv6Disabler != other.enableRootIpv6Disabler) return false
        if (enableLocalDns != other.enableLocalDns) return false
        if (enableFakeIp != other.enableFakeIp) return false
        if (!mihomoProfileBytes.contentEquals(other.mihomoProfileBytes)) return false
        if (ageSecretKey != other.ageSecretKey) return false
        if (setuidgidPath != other.setuidgidPath) return false
        if (runtimeLayout != other.runtimeLayout) return false
        if (fakeIpIpv4Pool != other.fakeIpIpv4Pool) return false
        if (coreLogPaths != other.coreLogPaths) return false
        if (configPath != other.configPath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = enableIpv6.hashCode()
        result = 31 * result + enableRootIpv6Disabler.hashCode()
        result = 31 * result + enableLocalDns.hashCode()
        result = 31 * result + enableFakeIp.hashCode()
        result = 31 * result + mihomoProfileBytes.contentHashCode()
        result = 31 * result + ageSecretKey.hashCode()
        result = 31 * result + setuidgidPath.hashCode()
        result = 31 * result + runtimeLayout.hashCode()
        result = 31 * result + fakeIpIpv4Pool.hashCode()
        result = 31 * result + coreLogPaths.hashCode()
        result = 31 * result + configPath.hashCode()
        return result
    }
}

internal interface RootModeStartConfig {
    val root: RootStartConfig
    val localProxyOptions: LocalProxyOptions?
    val asteriskdConfig: AsteriskdConfig?
        get() = null
    val rootEbpfConfig: RootEbpfRuntimeConfig?
        get() = null
}

internal val RootStartConfig.startupScriptPath: String
    get() = runtimeLayout.startupScriptPath

internal val RootStartConfig.bootLogDirPath: String
    get() = coreLogPaths.logDirectoryPath()

internal val RootStartConfig.bootLogPath: String
    get() = File(bootLogDirPath, RootBootLogFileName).absolutePath

internal val RootRuntimeLayout.startupScriptPath: String
    get() = File(dataDir, RootStartupScriptFileName).absolutePath

internal val RootRuntimeLayout.asteriskdConfigPath: String
    get() = File(dataDir, RootAsteriskdConfigFileName).absolutePath

internal val RootRuntimeLayout.asteriskdPidPath: String
    get() = File(dataDir, RootAsteriskdPidFileName).absolutePath

internal val RootRuntimeLayout.asteriskdReadyPath: String
    get() = File(dataDir, RootAsteriskdReadyFileName).absolutePath

internal val RootRuntimeLayout.asteriskdStatePath: String
    get() = File(dataDir, RootAsteriskdStateFileName).absolutePath

internal val RootRuntimeLayout.stopScriptPath: String
    get() = File(dataDir, RootStopScriptFileName).absolutePath

internal val RootRuntimeLayout.bpfPolicyPath: String
    get() = File(dataDir, RootEbpfPolicyFileName).absolutePath

internal val RootRuntimeLayout.rootEbpfDirectCidrPathV4: String
    get() = File(dataDir, RootEbpfDirectCidrV4FileName).absolutePath

internal val RootRuntimeLayout.rootEbpfDirectCidrPathV6: String
    get() = File(dataDir, RootEbpfDirectCidrV6FileName).absolutePath

internal val RootRuntimeLayout.bpf2socksConfigPath: String
    get() = File(dataDir, RootBpf2SocksConfigFileName).absolutePath

internal val RootRuntimeLayout.bpf2socksPidPath: String
    get() = File(dataDir, RootBpf2SocksPidFileName).absolutePath

internal val RootRuntimeLayout.asteriskdLogPath: String
    get() = File(dataDir, "logs/$RootAsteriskdLogFileName").absolutePath
