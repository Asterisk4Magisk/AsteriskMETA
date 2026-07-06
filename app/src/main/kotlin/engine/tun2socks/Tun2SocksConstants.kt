// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.tun2socks

import engine.root.RootProxyFwmark

const val Tun2SocksListenAddress = "127.0.0.1"
const val DefaultTun2SocksProxyPort = 65534
const val Tun2SocksFwmark = RootProxyFwmark
const val Tun2SocksRouteTable = "168"
const val Tun2SocksPreroutingChain = "ASTERISK_TUN_PREROUTING"
const val Tun2SocksOutputChain = "ASTERISK_TUN_OUTPUT"
const val Tun2SocksForwardChain = "ASTERISK_TUN_FORWARD"
const val Tun2SocksPrerouting6Chain = "ASTERISK_TUN6_PREROUTING"
const val Tun2SocksOutput6Chain = "ASTERISK_TUN6_OUTPUT"
const val Tun2SocksForward6Chain = "ASTERISK_TUN6_FORWARD"
