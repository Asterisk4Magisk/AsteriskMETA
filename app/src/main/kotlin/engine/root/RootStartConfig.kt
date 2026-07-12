// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import engine.proxy.LocalProxyOptions
import engine.mihomo.MihomoCoreLogPaths
import engine.mihomo.logDirectoryPath
import java.io.File

internal data class RootStartConfig(
    val mihomoProfileYaml: String,
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
}

internal interface RootModeStartConfig {
    val root: RootStartConfig
    val localProxyOptions: LocalProxyOptions
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
