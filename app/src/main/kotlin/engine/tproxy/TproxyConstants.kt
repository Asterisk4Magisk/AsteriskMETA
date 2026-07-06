// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.tproxy

import engine.network.NetworkLimits
import engine.root.RootProxyAuxFwmark
import engine.root.RootProxyFwmark

const val DefaultTproxyPort = NetworkLimits.PORT_MAX
const val TproxyFwmark = RootProxyFwmark
const val TproxyRouteTable = "160"
const val TproxyDummyDevice = "xdummy"
const val TproxyDummyAddress = "fd01:5ca1:ab1e:8d97:497f:8b48:b9aa:85cd/128"
const val TproxyDummyFwmark = RootProxyAuxFwmark
const val TproxyDummyRouteTable = "164"
const val TproxyPreroutingChain = "ASTERISK_TPROXY_PREROUTING"
const val TproxyOutputChain = "ASTERISK_TPROXY_OUTPUT"
const val TproxyPrerouting6Chain = "ASTERISK_TPROXY6_PREROUTING"
const val TproxyOutput6Chain = "ASTERISK_TPROXY6_OUTPUT"

