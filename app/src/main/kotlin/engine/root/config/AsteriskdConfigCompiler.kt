// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.config

import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import engine.root.daemon.config.AsteriskdAppPolicy
import engine.root.daemon.config.AsteriskdAppPolicyMode
import engine.root.daemon.config.AsteriskdConfig
import engine.root.daemon.config.AsteriskdCoreConfig
import engine.root.daemon.config.AsteriskdCoreType
import engine.root.daemon.config.AsteriskdHelper
import engine.root.daemon.config.AsteriskdMatcher
import engine.root.daemon.config.AsteriskdMetaConfigFactory
import engine.root.daemon.config.AsteriskdMode
import engine.root.daemon.config.AsteriskdModeOptions
import engine.root.daemon.config.AsteriskdNetworkConfig
import engine.root.daemon.config.AsteriskdOwner

internal val RootStartConfig.disableSystemIpv6: Boolean
    get() = !enableIpv6 && enableRootIpv6Disabler

internal fun RootStartConfig.buildAsteriskdConfig(
    mode: AsteriskdMode,
    iptablesConfig: RootIptablesConfig,
    virtualInterfaces: List<String>,
    modeOptions: AsteriskdModeOptions,
    helper: AsteriskdHelper? = null,
): AsteriskdConfig {
    AsteriskdMetaConfigFactory.requireRunnableMode(mode)
    val matcher = if (iptablesConfig.enableEbpfRules && AsteriskdMetaConfigFactory.isMatcherAllowed(mode)) {
        AsteriskdMatcher(runtimePaths.matcherExecutablePath)
    } else {
        null
    }
    val useDirectCidrs =
        mode != AsteriskdMode.Ebpf &&
        iptablesConfig.enableEbpfDirectCidrBypass &&
        (matcher != null || mode == AsteriskdMode.Bpf2Socks)
    return AsteriskdConfig(
        owner = AsteriskdOwner.AsteriskMeta,
        coreType = AsteriskdCoreType.Mihomo,
        coreExecutablePath = runtimePaths.coreExecutablePath,
        coreConfigPath = runtimePaths.coreConfigPath,
        statePath = runtimePaths.statePath,
        logPath = runtimePaths.logPath,
        mode = mode,
        core = AsteriskdCoreConfig(
            workingDirectory = runtimePaths.workingDirectory,
            readinessTimeoutMilliseconds = 5000,
            ageSecretKey = ageSecretKey,
        ),
        network = if (mode == AsteriskdMode.Ebpf) {
            AsteriskdNetworkConfig(
                enableIpv6 = enableIpv6,
                disableSystemIpv6 = disableSystemIpv6,
                enableLocalDns = false,
                enableFakeDns = false,
                fakeDnsIpv4Pool = null,
                ignoredInterfaces = emptyList(),
                virtualInterfaces = emptyList(),
                hotspotInterfacePrefixes = emptyList(),
                proxyPrivateCidrs = emptyList(),
                bypassPrivateCidrs = emptyList(),
                appPolicy = AsteriskdAppPolicy(
                    mode = AsteriskdAppPolicyMode.Global,
                    uids = emptyList(),
                    bypassUids = emptyList(),
                    directCidrPathV4 = null,
                    directCidrPathV6 = null,
                ),
            )
        } else {
            AsteriskdNetworkConfig(
                enableIpv6 = enableIpv6,
                disableSystemIpv6 = disableSystemIpv6,
                enableLocalDns = enableLocalDns,
                enableFakeDns = enableFakeIp,
                fakeDnsIpv4Pool = fakeIpIpv4Pool.takeIf { enableFakeIp },
                ignoredInterfaces = iptablesConfig.ignoredInterfaces.distinct(),
                virtualInterfaces = virtualInterfaces.distinct(),
                hotspotInterfacePrefixes = iptablesConfig.externalInterfacePrefixes.distinct(),
                proxyPrivateCidrs = (
                    iptablesConfig.proxyPrivateIpv4Cidrs +
                        iptablesConfig.proxyPrivateIpv6Cidrs.takeIf { enableIpv6 }.orEmpty()
                    ).distinct(),
                bypassPrivateCidrs = (
                    iptablesConfig.bypassPrivateIpv4Cidrs +
                        iptablesConfig.bypassPrivateIpv6Cidrs.takeIf { enableIpv6 }.orEmpty()
                    ).distinct(),
                appPolicy = AsteriskdAppPolicy(
                    mode = when (iptablesConfig.proxyAppListMode) {
                        ProxyAppListModeBlacklist -> AsteriskdAppPolicyMode.Blacklist
                        ProxyAppListModeWhitelist -> AsteriskdAppPolicyMode.Whitelist
                        ProxyAppListModeGlobal -> AsteriskdAppPolicyMode.Global
                        else -> error("Unsupported application policy mode")
                    },
                    uids = iptablesConfig.proxyApplicationUids.distinct().sorted()
                        .takeUnless { iptablesConfig.proxyAppListMode == ProxyAppListModeGlobal }
                        .orEmpty(),
                    bypassUids = iptablesConfig.forcedBypassUids.distinct().sorted(),
                    directCidrPathV4 = directCidrIpv4Path.takeIf { useDirectCidrs },
                    directCidrPathV6 = directCidrIpv6Path.takeIf { useDirectCidrs },
                ),
            )
        },
        modeOptions = modeOptions,
        matcher = matcher,
        helper = helper,
    )
}
