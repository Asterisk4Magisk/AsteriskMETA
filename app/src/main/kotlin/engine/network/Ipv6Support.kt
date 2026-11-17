// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.network

import java.net.Inet6Address
import java.net.NetworkInterface

/**
 * Whether the device itself has an address it could reach the outside world with over IPv6.
 *
 * The check answers one question for the card: is there any point in asking for an IPv6 address
 * at all. A device on a network without IPv6 has no route for it, so every IPv6 request would
 * only wait for a timeout to say so.
 */
internal fun deviceHasIpv6(): Boolean {
    val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return false
    for (networkInterface in interfaces) {
        val usable = runCatching {
            networkInterface.isUp &&
                !networkInterface.isLoopback &&
                networkInterface.inetAddresses.asSequence().any { address ->
                    address is Inet6Address &&
                        !address.isLoopbackAddress &&
                        !address.isLinkLocalAddress &&
                        !address.isAnyLocalAddress
                }
        }.getOrDefault(false)
        if (usable) return true
    }
    return false
}
