// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import android.content.Context
import app.AppState
import engine.network.toPortOrNull
import engine.proxy.ProxyEngineStartRequest
import engine.tun2socks.DefaultTun2SocksProxyPort
import engine.mihomo.MihomoCoreLogPaths
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.prepareMihomoCoreLogPaths
import features.resources.runtime.MihomoResourceFilePaths
import features.resources.runtime.prepareMihomoResourceFilePaths

internal class RootConfigBuildContext(
    private val androidContext: Context,
    val appState: AppState,
    private val resourceFilePaths: MihomoResourceFilePaths,
    private val coreLogPaths: MihomoCoreLogPaths,
) {
    fun buildRootStartConfig(): RootStartConfig {
        return appState.toRootStartConfig(
            mihomoProfileYaml = MihomoProfileFactory.buildProfile(appState),
            resourceFilePaths = resourceFilePaths,
            runtimeLayout = resourceFilePaths.toRootRuntimeLayout(),
            coreLogPaths = coreLogPaths,
        )
    }

    fun buildRootIptablesConfig(
        base: RootIptablesConfig,
        ignoredLocalInterfaceNames: Set<String>,
    ): RootIptablesConfig {
        return base.withAppSettings(
            context = androidContext,
            appState = appState,
            ignoredLocalInterfaceNames = ignoredLocalInterfaceNames,
        )
    }

}

internal fun Context.prepareRootConfigBuildContext(request: ProxyEngineStartRequest): RootConfigBuildContext {
    val appState = request.appState
    val resourceFilePaths = prepareMihomoResourceFilePaths()
    val coreLogPaths = prepareMihomoCoreLogPaths()
    return RootConfigBuildContext(
        androidContext = applicationContext,
        appState = appState,
        resourceFilePaths = resourceFilePaths,
        coreLogPaths = coreLogPaths,
    )
}

private fun AppState.toRootStartConfig(
    mihomoProfileYaml: String,
    resourceFilePaths: MihomoResourceFilePaths,
    runtimeLayout: RootRuntimeLayout,
    coreLogPaths: MihomoCoreLogPaths,
): RootStartConfig {
    return RootStartConfig(
        mihomoProfileYaml = mihomoProfileYaml,
        setuidgidPath = resourceFilePaths.setuidgidPath,
        runtimeLayout = runtimeLayout,
        enableIpv6 = enableIpv6,
        coreLogPaths = coreLogPaths,
    )
}

internal fun AppState.tun2SocksInternalProxyPortValue(): Int {
    return socks5ProxyPort.toPortOrNull() ?: DefaultTun2SocksProxyPort
}
