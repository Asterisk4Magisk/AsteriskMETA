// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.daemon.config

internal object AsteriskdConfigValidator {
    fun validate(config: AsteriskdConfig) = with(config) {
        require(owner == AsteriskdOwner.AsteriskMeta && coreType == AsteriskdCoreType.Mihomo)
        AsteriskdMetaConfigFactory.requireRunnableMode(mode)
        require(!(network.enableIpv6 && network.disableSystemIpv6))
        require(network.enableFakeDns == (network.fakeDnsIpv4Pool != null))
        require(network.appPolicy.uids == network.appPolicy.uids.distinct().sorted())
        require(network.appPolicy.bypassUids == network.appPolicy.bypassUids.distinct().sorted())
        require((network.appPolicy.directCidrPathV4 == null) == (network.appPolicy.directCidrPathV6 == null))
        when (mode) {
            AsteriskdMode.Tproxy -> {
                require(modeOptions.transparentPort != null && modeOptions.tunnelName == null)
                require(helper == null)
            }
            AsteriskdMode.Tun2Socks -> {
                require(modeOptions.transparentPort == null && modeOptions.tunnelName == null)
                require(helper is AsteriskdHevSocks5TunnelHelper)
            }
            AsteriskdMode.Bpf2Socks -> {
                require(modeOptions.transparentPort == null && modeOptions.tunnelName == null)
                require(helper is AsteriskdBpf2SocksHelper && matcher == null)
            }
            AsteriskdMode.Tun -> {
                require(modeOptions.transparentPort == null && !modeOptions.tunnelName.isNullOrBlank())
                require(helper == null)
            }
            AsteriskdMode.Ebpf -> {
                require(modeOptions.transparentPort == null && modeOptions.tunnelName == null)
                require(helper == null && matcher == null)
                require(!network.enableLocalDns && !network.enableFakeDns && network.fakeDnsIpv4Pool == null)
                require(network.ignoredInterfaces.isEmpty())
                require(network.virtualInterfaces.isEmpty())
                require(network.hotspotInterfacePrefixes.isEmpty())
                require(network.proxyPrivateCidrs.isEmpty() && network.bypassPrivateCidrs.isEmpty())
                require(network.appPolicy == AsteriskdAppPolicy(
                    mode = AsteriskdAppPolicyMode.Global,
                    uids = emptyList(),
                    bypassUids = emptyList(),
                    directCidrPathV4 = null,
                    directCidrPathV6 = null,
                ))
            }
        }
        if (matcher != null) require(AsteriskdMetaConfigFactory.isMatcherAllowed(mode))
        network.appPolicy.directCidrPathV4?.let { pathV4 ->
            require(pathV4.isNotBlank() && !network.appPolicy.directCidrPathV6.isNullOrBlank())
            require((matcher != null) xor (mode == AsteriskdMode.Bpf2Socks))
        }
    }
}

internal object AsteriskdMetaConfigFactory {
    fun requireRunnableMode(mode: AsteriskdMode) {
        require(mode != AsteriskdMode.Ebpf)
    }

    fun isMatcherAllowed(mode: AsteriskdMode): Boolean =
        mode == AsteriskdMode.Tproxy || mode == AsteriskdMode.Tun || mode == AsteriskdMode.Tun2Socks
}
