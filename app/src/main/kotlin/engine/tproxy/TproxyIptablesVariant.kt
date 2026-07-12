// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.tproxy

import engine.root.RootIp6Command
import engine.root.RootIp6tablesCommand
import engine.root.RootIpCommand
import engine.root.RootIptablesConfig
import engine.root.RootIptablesCommand

internal fun RootIptablesConfig.ipv4IptablesVariant(): TproxyIptablesVariant {
    return TproxyIptablesVariant(
        command = RootIptablesCommand,
        ipCommand = RootIpCommand,
        routeTable = ipv4Table,
        routeDestination = "default",
        preroutingChain = TproxyPreroutingChain,
        outputChain = TproxyOutputChain,
        tproxyOnIp = "0.0.0.0",
        proxyPrivateCidrs = proxyPrivateIpv4Cidrs,
        bypassPrivateCidrs = bypassPrivateIpv4Cidrs,
    )
}

internal fun RootIptablesConfig.ipv6IptablesVariant(useDummyInterface: Boolean): TproxyIptablesVariant {
    return TproxyIptablesVariant(
        command = RootIp6tablesCommand,
        ipCommand = RootIp6Command,
        routeTable = ipv6Table,
        routeDestination = "default",
        preroutingChain = TproxyPrerouting6Chain,
        outputChain = TproxyOutput6Chain,
        tproxyOnIp = "::",
        proxyPrivateCidrs = proxyPrivateIpv6Cidrs,
        bypassPrivateCidrs = bypassPrivateIpv6Cidrs,
        dummyInterface = DummyInterfaceConfig.takeIf { useDummyInterface },
    )
}

internal fun buildGlobalIpv6AddressCheckCommand(): String {
    return "$RootIp6Command addr show scope global 2>/dev/null | grep -q 'inet6 '"
}

internal data class TproxyIptablesVariant(
    val command: String,
    val ipCommand: String,
    val routeTable: String,
    val routeDestination: String,
    val preroutingChain: String,
    val outputChain: String,
    val tproxyOnIp: String,
    val proxyPrivateCidrs: List<String>,
    val bypassPrivateCidrs: List<String>,
    val dummyInterface: TproxyDummyInterfaceConfig? = null,
)

internal data class TproxyDummyInterfaceConfig(
    val device: String,
    val address: String,
    val mark: String,
    val routeTable: String,
    val outputChain: String,
    val preroutingChain: String,
)

private val DummyInterfaceConfig = TproxyDummyInterfaceConfig(
    device = TproxyDummyDevice,
    address = TproxyDummyAddress,
    mark = TproxyDummyFwmark,
    routeTable = TproxyDummyRouteTable,
    outputChain = "ASTERISK_TPROXY6_DUMMY",
    preroutingChain = "ASTERISK_TPROXY6_DUMMY_PRE",
)
