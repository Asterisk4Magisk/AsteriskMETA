// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import android.os.Process
import app.effectiveLocalDnsEnabled
import engine.hevtun.HevSocks5TunnelConfig
import engine.hevtun.HevSocks5TunnelConfigFileName
import engine.hevtun.HevSocks5TunnelLogFileName
import engine.hevtun.hevSocks5TunnelLogFile
import engine.hevtun.hevSocks5TunnelSocksTargetAddress
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.sha256Hex
import engine.mihomo.selectedMihomoProfileOrNull
import engine.proxy.LocalProxyOptions
import engine.proxy.toLocalProxyOptions
import engine.proxy.toLocalProxyOptionsOrNull
import engine.proxy.LocalProxyLoopbackAddress
import engine.mihomo.MihomoCoreLogPaths
import engine.mihomo.prepareMihomoCoreLogPaths
import features.resources.runtime.prepareMihomoResourceFilePaths
import system.toAndroidUserId
import engine.proxy.ProxyEngineStartRequest
import utils.writeAtomically
import engine.mihomo.raw.runtimeIpv6Enabled
import java.io.File

internal data class VpnServiceStartConfig(
    val sessionName: String,
    val mtu: Int = VpnDefaults.MTU,
    val ipv4Address: String = defaultIpv4TunAddress.address,
    val ipv4PrefixLength: Int = defaultIpv4TunAddress.prefixLength,
    val ipv6Address: String? = null,
    val ipv6PrefixLength: Int = defaultIpv6TunAddress.prefixLength,
    val enableIpv6: Boolean = false,
    val enableLocalDns: Boolean = true,
    val dnsServers: List<String>,
    val mihomoProfilePath: String,
    val mihomoProfileSignature: String,
    val ageSecretKey: String = "",
    val mihomoTunStack: String,
    val applicationPolicy: VpnApplicationPolicy,
    val localProxyOptions: LocalProxyOptions,
    val appendHttpProxyOptions: VpnAppendHttpProxyOptions,
    val coreLogPaths: MihomoCoreLogPaths,
    val dataDir: String = "",
    val hevSocks5TunnelConfig: HevSocks5TunnelConfig? = null,
)

internal object VpnMihomoConfigFactory {
    fun create(
        context: Context,
        request: ProxyEngineStartRequest,
        exposePorts: Boolean = true,
    ): VpnServiceStartConfig {
        val appState = request.appState
        val rawConfig = request.rawConfig
        val coreLogPaths = context.prepareMihomoCoreLogPaths()
        val resourceFilePaths = context.prepareMihomoResourceFilePaths()
        val tunOptions = appState.toTunOptions()
        val localProxyOptions = rawConfig?.toLocalProxyOptionsOrNull()
            ?: appState.toLocalProxyOptions().takeIf { rawConfig == null }
            ?: LocalProxyOptions(LocalProxyLoopbackAddress, 0, "", "")
        val appendHttpProxyOptions = if (exposePorts && localProxyOptions.port > 0) {
            appState.toVpnAppendHttpProxyOptions(localProxyOptions)
        } else {
            VpnAppendHttpProxyOptions.Disabled
        }
        val profilePath = File(resourceFilePaths.dataDir, "config.yaml").absolutePath
        val profileBytes = MihomoProfileFactory.buildProfileBytes(context, appState, exposePorts = exposePorts)
        val profileSignature = profileBytes.sha256Hex()
        val ageSecretKey = appState.selectedMihomoProfileOrNull()?.ageSecretKey.orEmpty()
        val runtimeIpv6 = rawConfig.runtimeIpv6Enabled(appState.enableIpv6)
        writeAtomically(File(profilePath)) { output ->
            output.write(profileBytes)
        }

        return VpnServiceStartConfig(
            sessionName = "AsteriskMETA",
            mtu = tunOptions.mtu,
            ipv4Address = tunOptions.ipv4Address.address,
            ipv4PrefixLength = tunOptions.ipv4Address.prefixLength,
            enableIpv6 = runtimeIpv6,
            ipv6Address = if (runtimeIpv6) tunOptions.ipv6Address.address else null,
            ipv6PrefixLength = tunOptions.ipv6Address.prefixLength,
            enableLocalDns = if (rawConfig == null) {
                appState.effectiveLocalDnsEnabled
            } else {
                appState.enableLocalDns && rawConfig.dnsHijack.value?.proven == true
            },
            dnsServers = tunOptions.dnsServers,
            mihomoProfilePath = profilePath,
            mihomoProfileSignature = profileSignature,
            ageSecretKey = ageSecretKey,
            mihomoTunStack = rawConfig?.tunInbound?.value?.stack ?: MihomoProfileFactory.tunStack(appState),
            applicationPolicy = appState.toVpnApplicationPolicy(Process.myUid().toAndroidUserId()),
            localProxyOptions = localProxyOptions,
            appendHttpProxyOptions = appendHttpProxyOptions,
            coreLogPaths = coreLogPaths,
            dataDir = resourceFilePaths.dataDir,
            hevSocks5TunnelConfig = buildVpnHevSocks5TunnelConfig(
                dataDir = resourceFilePaths.dataDir,
                coreLogPaths = coreLogPaths,
                localProxyOptions = localProxyOptions,
                tunOptions = tunOptions,
                enableIpv6 = runtimeIpv6,
                useHevTun = appState.enableVpnHevTun,
            ),
        )
    }
}

internal fun buildVpnHevSocks5TunnelConfig(
    dataDir: String,
    coreLogPaths: MihomoCoreLogPaths,
    localProxyOptions: LocalProxyOptions,
    tunOptions: TunOptions,
    enableIpv6: Boolean,
    useHevTun: Boolean = true,
): HevSocks5TunnelConfig? {
    if (!useHevTun) return null
    return HevSocks5TunnelConfig(
        configPath = File(dataDir, HevSocks5TunnelConfigFileName).absolutePath,
        logPath = coreLogPaths.hevSocks5TunnelLogFile(HevSocks5TunnelLogFileName).absolutePath,
        socksAddress = hevSocks5TunnelSocksTargetAddress(localProxyOptions),
        socksPort = localProxyOptions.port,
        socksUsername = localProxyOptions.username,
        socksPassword = localProxyOptions.password,
        mtu = tunOptions.mtu,
        ipv4Address = tunOptions.ipv4Address.address,
        ipv6Address = tunOptions.ipv6Address.address.takeIf { enableIpv6 },
        tunnelName = "asterisk0",
        enableMultiQueue = true,
        enableTcpFastOpen = true,
    )
}
