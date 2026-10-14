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
        else -> "IN-TYPE,SOCKS"
    }
    val udpRule = "AND,(($inbound),(NETWORK,UDP),(DST-PORT,53)),dns-out"
    val tcpRule = "AND,(($inbound),(NETWORK,TCP),(DST-PORT,53)),dns-out"
    val udpRules = if (udpRule in original) original else listOf(udpRule) + original
    return if (tcpRule !in udpRules) listOf(tcpRule) + udpRules else udpRules
}
