// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import engine.network.NetworkCidrAddress
import engine.network.isIpv4Address
import engine.network.parseCidrAddressOrNull
import utils.toIntInRangeOrDefault

internal val defaultIpv4TunAddress = VpnDefaults.IPV4_CIDR.toNetworkCidrAddress()
internal val defaultIpv6TunAddress = VpnDefaults.IPV6_CIDR.toNetworkCidrAddress()

internal data class TunOptions(
    val mtu: Int,
    val ipv4Address: NetworkCidrAddress,
    val ipv6Address: NetworkCidrAddress,
    val dnsServers: List<String>,
)

internal fun AppState.toTunOptions(): TunOptions {
    return TunOptions(
        mtu = tunMtuValue(),
        ipv4Address = tunIpv4Address(),
        ipv6Address = tunIpv6Address(),
        dnsServers = listOf(
            tunVpnDns.trim()
                .takeIf(::isIpv4Address)
                ?: VpnDefaults.IPV4_DNS,
        ),
    )
}

private fun AppState.tunMtuValue(): Int {
    return tunMtu.toIntInRangeOrDefault(VpnDefaults.MTU_MIN..VpnDefaults.MTU_MAX, default = VpnDefaults.MTU)
}

private fun AppState.tunIpv4Address(): NetworkCidrAddress {
    return parseCidrAddressOrNull(tunIpv4Cidr)
        ?.takeIf { address -> !address.address.contains(":") }
        ?: defaultIpv4TunAddress
}

private fun AppState.tunIpv6Address(): NetworkCidrAddress {
    return parseCidrAddressOrNull(tunIpv6Cidr)
        ?.takeIf { address -> address.address.contains(":") }
        ?: defaultIpv6TunAddress
}

private fun String.toNetworkCidrAddress(): NetworkCidrAddress {
    return parseCidrAddressOrNull(this) ?: error("Invalid VPN CIDR: $this")
}

