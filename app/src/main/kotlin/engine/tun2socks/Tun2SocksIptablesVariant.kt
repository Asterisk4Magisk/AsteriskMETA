// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.tun2socks

import engine.root.RootIp6Command
import engine.root.RootIp6tablesCommand
import engine.root.RootIpCommand
import engine.root.RootIptablesConfig
import engine.root.RootIptablesCommand

internal data class Tun2SocksIptablesVariant(
    val command: String,
    val ipCommand: String,
    val routeTable: String,
    val preroutingChain: String,
    val outputChain: String,
    val forwardChain: String,
    val proxyPrivateCidrs: List<String>,
    val bypassPrivateCidrs: List<String>,
    val ipv6: Boolean,
) {
    companion object {
        fun forIpv4(config: RootIptablesConfig): Tun2SocksIptablesVariant {
            return Tun2SocksIptablesVariant(
                command = RootIptablesCommand,
                ipCommand = RootIpCommand,
                routeTable = config.ipv4Table,
                preroutingChain = Tun2SocksPreroutingChain,
                outputChain = Tun2SocksOutputChain,
                forwardChain = Tun2SocksForwardChain,
                proxyPrivateCidrs = config.proxyPrivateIpv4Cidrs,
                bypassPrivateCidrs = config.bypassPrivateIpv4Cidrs,
                ipv6 = false,
            )
        }

        fun forIpv6(config: RootIptablesConfig): Tun2SocksIptablesVariant {
            return Tun2SocksIptablesVariant(
                command = RootIp6tablesCommand,
                ipCommand = RootIp6Command,
                routeTable = config.ipv6Table,
                preroutingChain = Tun2SocksPrerouting6Chain,
                outputChain = Tun2SocksOutput6Chain,
                forwardChain = Tun2SocksForward6Chain,
                proxyPrivateCidrs = config.proxyPrivateIpv6Cidrs,
                bypassPrivateCidrs = config.bypassPrivateIpv6Cidrs,
                ipv6 = true,
            )
        }
    }
}
