// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import android.content.Context
import app.AppState
import app.effectiveFakeIpEnabled
import app.effectiveLocalDnsEnabled
import engine.mihomo.DefaultMihomoDnsFakeIpRange
import engine.mihomo.MihomoCoreLogPaths
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.prepareMihomoCoreLogPaths
import engine.mihomo.selectedMihomoProfileOrNull
import engine.network.parseCidrAddressOrNull
import engine.network.toPortOrNull
import engine.proxy.ProxyEngineStartRequest
import engine.tun2socks.DefaultTun2SocksProxyPort
import features.resources.runtime.MihomoResourceFilePaths
import features.resources.runtime.prepareMihomoResourceFilePaths

internal class RootConfigBuildContext(
    private val androidContext: Context,
    val appState: AppState,
    val resourceFilePaths: MihomoResourceFilePaths,
    private val coreLogPaths: MihomoCoreLogPaths,
) {
    fun buildRootStartConfig(): RootStartConfig {
        return appState.toRootStartConfig(
            mihomoProfileYaml = MihomoProfileFactory.buildProfile(androidContext, appState),
            resourceFilePaths = resourceFilePaths,
            runtimeLayout = resourceFilePaths.toRootRuntimeLayout(),
            coreLogPaths = coreLogPaths,
        )
    }

    fun buildRootIptablesConfig(base: RootIptablesConfig): RootIptablesConfig {
        return base.withAppSettings(
            context = androidContext,
            appState = appState,
        )
    }

    fun buildRootEbpfRuntimeConfig(iptablesConfig: RootIptablesConfig): RootEbpfRuntimeConfig? {
        if (!iptablesConfig.enableEbpfRules) return null
        val runtimeLayout = resourceFilePaths.toRootRuntimeLayout()
        return RootEbpfRuntimeConfig(
            matcherPath = runtimeLayout.bpfMatcherPath,
            bpfPolicyPath = runtimeLayout.bpfPolicyPath,
            directCidrPathV4 = runtimeLayout.rootEbpfDirectCidrPathV4,
            directCidrPathV6 = runtimeLayout.rootEbpfDirectCidrPathV6,
            directCidrSourcePathsV4 = listOf(resourceFilePaths.directCidrIpv4Path),
            directCidrSourcePathsV6 = listOf(resourceFilePaths.directCidrIpv6Path),
            policy = iptablesConfig.toRootEbpfPolicy(
                enableIpv6 = appState.enableIpv6,
                directCidrPathV4 = runtimeLayout.rootEbpfDirectCidrPathV4,
                directCidrPathV6 = runtimeLayout.rootEbpfDirectCidrPathV6,
                xtOutputV4ProgramPath = RootEbpfXtOutputV4ProgramPath,
                xtOutputV6ProgramPath = RootEbpfXtOutputV6ProgramPath,
                xtPreroutingV4ProgramPath = RootEbpfXtPreroutingV4ProgramPath,
                xtPreroutingV6ProgramPath = RootEbpfXtPreroutingV6ProgramPath,
            ),
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
        ageSecretKey = selectedMihomoProfileOrNull()?.ageSecretKey.orEmpty(),
        setuidgidPath = resourceFilePaths.setuidgidPath,
        runtimeLayout = runtimeLayout,
        enableIpv6 = enableIpv6,
        enableRootIpv6Disabler = enableRootIpv6Disabler,
        enableLocalDns = effectiveLocalDnsEnabled,
        enableFakeIp = effectiveFakeIpEnabled,
        fakeIpIpv4Pool = rootFakeIpIpv4Pool(),
        coreLogPaths = coreLogPaths,
    )
}

internal fun AppState.tun2SocksInternalProxyPortValue(): Int {
    return socks5ProxyPort.toPortOrNull() ?: DefaultTun2SocksProxyPort
}

internal fun AppState.bpf2SocksBridgePortValue(): Int {
    return bpf2SocksBridgePort.toPortOrNull() ?: RootBpf2SocksDefaultBridgePort
}

private fun AppState.rootFakeIpIpv4Pool(): String {
    return dnsFakeIpRange.normalizedIpv4CidrOrNull()
        ?: DefaultMihomoDnsFakeIpRange.normalizedIpv4CidrOrNull()
        ?: "198.18.0.0/16"
}

private fun String.normalizedIpv4CidrOrNull(): String? {
    val cidr = parseCidrAddressOrNull(this) ?: return null
    if (":" in cidr.address) return null
    val octets = cidr.address.split(".")
    if (octets.size != 4) return null
    var addressValue = 0L
    octets.forEach { octet ->
        val value = octet.toIntOrNull() ?: return null
        addressValue = (addressValue shl 8) or value.toLong()
    }
    val mask = if (cidr.prefixLength == 0) {
        0L
    } else {
        (0xffffffffL shl (32 - cidr.prefixLength)) and 0xffffffffL
    }
    val network = addressValue and mask
    val address = listOf(
        (network shr 24) and 0xff,
        (network shr 16) and 0xff,
        (network shr 8) and 0xff,
        network and 0xff,
    ).joinToString(".")
    return "$address/${cidr.prefixLength}"
}
