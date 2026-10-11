// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import app.modes.RunModeBpf2Socks
import app.modes.RunModeTproxy
import app.modes.RunModeTun2Socks

internal const val UdpDnsHijackRule = "AND,((NETWORK,UDP),(DST-PORT,53)),dns-out"
internal const val TcpDnsHijackRule = "AND,((NETWORK,TCP),(DST-PORT,53)),dns-out"

internal fun managedMihomoDnsRules(value: List<Any?>, runMode: Int): List<Any?> {
    val original = value.ifEmpty { listOf("MATCH,DIRECT") }
    val udpRules = if (UdpDnsHijackRule in original) original else listOf(UdpDnsHijackRule) + original
    val includeTcp = runMode == RunModeTproxy || runMode == RunModeTun2Socks || runMode == RunModeBpf2Socks
    return if (includeTcp && TcpDnsHijackRule !in udpRules) listOf(TcpDnsHijackRule) + udpRules else udpRules
}
