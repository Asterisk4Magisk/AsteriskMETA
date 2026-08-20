// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.config

import android.content.Context
import app.AppState
import app.effectiveFakeIpEnabled
import app.effectiveLocalDnsEnabled
import engine.mihomo.DefaultMihomoDnsFakeIpRange
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.prepareMihomoCoreLogPaths
import engine.mihomo.raw.MihomoRawConfigSnapshot
import engine.mihomo.raw.runtimeIpv6Enabled
import engine.mihomo.selectedMihomoProfileOrNull
import engine.network.parseCidrAddressOrNull
import engine.network.toPortOrNull
import engine.proxy.ProxyEngineStartRequest
import features.resources.runtime.MihomoResourceFilePaths
import features.resources.runtime.mihomoResourceFilePaths
import java.io.File

internal class RootConfigBuildContext(
    private val androidContext: Context,
    val appState: AppState,
    val resourceFilePaths: MihomoResourceFilePaths,
    val rawConfig: MihomoRawConfigSnapshot? = null,
    private val preparedMihomoProfileBytes: ByteArray? = null,
) {
    fun buildRootStartConfig(): RootStartConfig {
        return appState.toRootStartConfig(
            mihomoProfileBytes = preparedMihomoProfileBytes
                ?: MihomoProfileFactory.buildProfileBytes(androidContext, appState),
            publicationStagingDirectory = androidContext.cacheDir.absolutePath,
            resourceFilePaths = resourceFilePaths,
            rawConfig = rawConfig,
        )
    }

    fun buildRootIptablesConfig(): RootIptablesConfig {
        return RootIptablesConfig().withAppSettings(context = androidContext, appState = appState)
    }
}

internal fun Context.prepareRootConfigBuildContext(request: ProxyEngineStartRequest): RootConfigBuildContext {
    applicationContext.prepareMihomoCoreLogPaths()
    return RootConfigBuildContext(
        androidContext = applicationContext,
        appState = request.appState,
        resourceFilePaths = mihomoResourceFilePaths(),
        rawConfig = request.rawConfig,
        preparedMihomoProfileBytes = request.preparedMihomoProfileBytes,
    )
}

private fun AppState.toRootStartConfig(
    mihomoProfileBytes: ByteArray,
    publicationStagingDirectory: String,
    resourceFilePaths: MihomoResourceFilePaths,
    rawConfig: MihomoRawConfigSnapshot?,
): RootStartConfig {
    val dataDirectory = File(resourceFilePaths.dataDir)
    val rawDnsHijack = rawConfig?.dnsHijack?.value?.proven == true && enableLocalDns
    return RootStartConfig(
        mihomoProfileBytes = mihomoProfileBytes,
        ageSecretKey = selectedMihomoProfileOrNull()?.ageSecretKey?.takeIf(String::isNotEmpty),
        publicationStagingDirectory = publicationStagingDirectory,
        runtimePaths = RootConfigRuntimePaths(
            coreExecutablePath = resourceFilePaths.mihomoCorePath,
            coreConfigPath = File(dataDirectory, "config.yaml").absolutePath,
            matcherExecutablePath = resourceFilePaths.bpfMatcherPath,
            bpf2SocksExecutablePath = resourceFilePaths.bpf2socksPath,
            hevSocks5TunnelExecutablePath = resourceFilePaths.hevSocks5TunnelPath,
            workingDirectory = resourceFilePaths.dataDir,
            statePath = File(dataDirectory, "asteriskd.state").absolutePath,
            logPath = File(File(dataDirectory, "logs"), "asteriskd.log").absolutePath,
        ),
        directCidrIpv4Path = resourceFilePaths.directCidrIpv4Path,
        directCidrIpv6Path = resourceFilePaths.directCidrIpv6Path,
        enableIpv6 = rawConfig.runtimeIpv6Enabled(enableIpv6),
        enableRootIpv6Disabler = enableRootIpv6Disabler,
        enableLocalDns = if (rawConfig == null) effectiveLocalDnsEnabled else rawDnsHijack,
        enableFakeIp = if (rawConfig == null) effectiveFakeIpEnabled else false,
        fakeIpIpv4Pool = rootFakeIpIpv4Pool(),
        enableBoot = enableRootBootScript,
        serviceControl = serviceControl,
    )
}

internal fun AppState.tun2SocksInternalProxyPortValue(): Int {
    return socks5ProxyPort.toPortOrNull() ?: DefaultRootTun2SocksProxyPort
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
    val mask = if (cidr.prefixLength == 0) 0L else
        (0xffffffffL shl (32 - cidr.prefixLength)) and 0xffffffffL
    val network = addressValue and mask
    val address = listOf(
        (network shr 24) and 0xff,
        (network shr 16) and 0xff,
        (network shr 8) and 0xff,
        network and 0xff,
    ).joinToString(".")
    return "$address/${cidr.prefixLength}"
}
