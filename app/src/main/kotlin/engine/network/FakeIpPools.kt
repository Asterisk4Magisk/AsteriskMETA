// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.network

const val DefaultFakeIpIpv4Pool = "198.18.0.0/15"

/** Preserve saved IPv4 ranges; only unsupported IPv6 pools are reset. */
fun String.withoutIpv6FakeIpPool(): String =
    if (':' in this) DefaultFakeIpIpv4Pool else this

/** The existing ROOT contract accepts IPv4 pools with prefixes /1 through /30. */
fun isValidFakeIpIpv4Pool(value: String): Boolean {
    val cidr = parseCidrAddressOrNull(value) ?: return false
    return isIpv4Address(cidr.address) && cidr.prefixLength in 1..30
}
