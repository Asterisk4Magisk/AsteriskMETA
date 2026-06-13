// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import engine.proxy.LocalProxyOptions
import engine.mihomo.MihomoCoreLogPaths
import engine.mihomo.logDirectoryPath
import java.io.File

internal data class RootStartConfig(
    val mihomoProfileYaml: String,
    val setuidgidPath: String,
    val runtimeLayout: RootRuntimeLayout,
    val enableIpv6: Boolean,
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
}

internal val RootStartConfig.startupScriptPath: String
    get() = runtimeLayout.startupScriptPath

internal val RootStartConfig.bootLogDirPath: String
    get() = coreLogPaths.logDirectoryPath()

internal val RootStartConfig.bootLogPath: String
    get() = File(bootLogDirPath, RootBootLogFileName).absolutePath

internal val RootRuntimeLayout.startupScriptPath: String
    get() = File(dataDir, RootStartupScriptFileName).absolutePath
