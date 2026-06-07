// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import android.os.Process
import app.effectiveLocalDnsEnabled
import engine.mihomo.MihomoProfileFactory
import engine.proxy.LocalProxyOptions
import engine.proxy.toLocalProxyOptions
import engine.mihomo.MihomoCoreLogPaths
import engine.mihomo.prepareMihomoCoreLogPaths
import features.resources.runtime.prepareMihomoResourceFilePaths
import system.toAndroidUserId
import engine.proxy.ProxyEngineStartRequest
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
    val mihomoProfileYaml: String,
    val mihomoProfilePath: String,
    val mihomoTunStack: String,
    val applicationPolicy: VpnApplicationPolicy,
    val localProxyOptions: LocalProxyOptions,
    val appendHttpProxyOptions: VpnAppendHttpProxyOptions,
    val coreLogPaths: MihomoCoreLogPaths,
    val dataDir: String = "",
)

internal object VpnMihomoConfigFactory {
    fun create(
        context: Context,
        request: ProxyEngineStartRequest,
        exposePorts: Boolean = true,
    ): VpnServiceStartConfig {
        val appState = request.appState
        val coreLogPaths = context.prepareMihomoCoreLogPaths()
        val resourceFilePaths = context.prepareMihomoResourceFilePaths()
        val tunOptions = appState.toTunOptions()
        val localProxyOptions = appState.toLocalProxyOptions()
        val appendHttpProxyOptions = if (exposePorts) {
            appState.toVpnAppendHttpProxyOptions(localProxyOptions)
        } else {
            VpnAppendHttpProxyOptions.Disabled
        }
        val profilePath = File(resourceFilePaths.dataDir, "config.yaml").absolutePath

        return VpnServiceStartConfig(
            sessionName = "AsteriskMETA",
            mtu = tunOptions.mtu,
            ipv4Address = tunOptions.ipv4Address.address,
            ipv4PrefixLength = tunOptions.ipv4Address.prefixLength,
            enableIpv6 = appState.enableIpv6,
            ipv6Address = if (appState.enableIpv6) tunOptions.ipv6Address.address else null,
            ipv6PrefixLength = tunOptions.ipv6Address.prefixLength,
            enableLocalDns = appState.effectiveLocalDnsEnabled,
            dnsServers = tunOptions.dnsServers,
            mihomoProfileYaml = MihomoProfileFactory.buildProfile(appState, exposePorts = exposePorts),
            mihomoProfilePath = profilePath,
            mihomoTunStack = MihomoProfileFactory.tunStack(appState),
            applicationPolicy = appState.toVpnApplicationPolicy(Process.myUid().toAndroidUserId()),
            localProxyOptions = localProxyOptions,
            appendHttpProxyOptions = appendHttpProxyOptions,
            coreLogPaths = coreLogPaths,
            dataDir = resourceFilePaths.dataDir,
        )
    }
}
