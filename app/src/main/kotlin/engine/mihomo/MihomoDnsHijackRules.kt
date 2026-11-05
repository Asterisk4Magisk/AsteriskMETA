// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import app.modes.RunModeTproxy
import app.modes.RunModeTun

internal fun managedMihomoDnsRules(value: List<Any?>, runMode: Int): List<Any?> {
    val original = value.ifEmpty { listOf("MATCH,DIRECT") }
    // Core DNS queries using #RULES must not be redirected back to the DNS outbound.
    val inbound = when (runMode) {
        RunModeTproxy -> "IN-NAME,$MihomoTproxyInboundName"
        RunModeTun -> "IN-NAME,$MihomoTunInboundName"
        // bpf2socks and hev-socks5-tunnel use SOCKS5. Meow only accepts SOCKS5 here and
        // skips rules naming SOCKS with just a warning; Mihomo accepts both spellings.
        else -> "IN-TYPE,SOCKS5"
    }
    val udpRule = "AND,(($inbound),(NETWORK,UDP),(DST-PORT,53)),dns-out"
    val tcpRule = "AND,(($inbound),(NETWORK,TCP),(DST-PORT,53)),dns-out"
    val udpRules = if (udpRule in original) original else listOf(udpRule) + original
    return if (tcpRule !in udpRules) listOf(tcpRule) + udpRules else udpRules
}
