// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Enumeration

internal fun collectMihomoDeviceState(): MihomoDeviceState {
    return MihomoDeviceState(
        intranetIp = collectIntranetIp(),
        updatedAtMillis = System.currentTimeMillis(),
    )
}

private fun collectIntranetIp(): String {
    val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
        ?: return ""
    val candidates = interfaces.asSequence()
        .filter(NetworkInterface::isUsableNetworkInterface)
        .flatMap { networkInterface ->
            networkInterface.inetAddresses.asSequence()
                .mapNotNull { address -> address.toDisplayAddressCandidate(networkInterface.name.orEmpty()) }
        }
        .sortedWith(
            compareByDescending<DisplayAddressCandidate> { candidate -> candidate.score }
                .thenBy { candidate -> candidate.interfaceName }
                .thenBy { candidate -> candidate.address },
        )
        .toList()
    return listOfNotNull(
        candidates.firstOrNull { candidate -> candidate.type == DisplayAddressType.Ipv4 }?.address,
        candidates.firstOrNull { candidate -> candidate.type == DisplayAddressType.Ipv6 }?.address,
    ).joinToString(separator = "\n")
}

private fun NetworkInterface.isUsableNetworkInterface(): Boolean {
    val interfaceName = name.orEmpty().lowercase()
    if (interfaceName.isBlank()) return false
    if (interfaceName.isVirtualInterfaceName()) return false
    return runCatching {
        isUp && !isLoopback && !isVirtual
    }.getOrDefault(false)
}

private fun String.isVirtualInterfaceName(): Boolean {
    return this == "lo" ||
        startsWith("tun") ||
        startsWith("tap") ||
        startsWith("utun") ||
        startsWith("ipsec") ||
        startsWith("wg") ||
        startsWith("dummy") ||
        startsWith("ifb") ||
        startsWith("sit") ||
        startsWith("ip6tnl") ||
        startsWith("clat") ||
        startsWith("v4-") ||
        startsWith("mihomo")
}

private fun InetAddress.toDisplayAddressCandidate(interfaceName: String): DisplayAddressCandidate? {
    val type = when (this) {
        is Inet4Address -> DisplayAddressType.Ipv4
        is Inet6Address -> DisplayAddressType.Ipv6
        else -> return null
    }
    if (isAnyLocalAddress || isLoopbackAddress || isMulticastAddress || isLinkLocalAddress) return null
    val address = hostAddress?.substringBefore('%')?.takeIf(String::isNotBlank) ?: return null
    val normalizedName = interfaceName.lowercase()
    val score = 1_000 + when {
        isSiteLocalAddress || address.isUniqueLocalIpv6Address() -> 100
        else -> 0
    } + when {
        normalizedName.startsWith("wlan") -> 40
        normalizedName.startsWith("eth") -> 35
        normalizedName.startsWith("rmnet") -> 30
        normalizedName.startsWith("ccmni") -> 30
        normalizedName.startsWith("usb") -> 20
        else -> 0
    }
    return DisplayAddressCandidate(
        address = address,
        interfaceName = normalizedName,
        score = score,
        type = type,
    )
}

private fun String.isUniqueLocalIpv6Address(): Boolean {
    val normalized = lowercase()
    return normalized.startsWith("fc") || normalized.startsWith("fd")
}

private data class DisplayAddressCandidate(
    val address: String,
    val interfaceName: String,
    val score: Int,
    val type: DisplayAddressType,
)

private enum class DisplayAddressType {
    Ipv4,
    Ipv6,
}

private fun <T> Enumeration<T>.asSequence(): Sequence<T> {
    return sequence {
        while (hasMoreElements()) {
            yield(nextElement())
        }
    }
}
