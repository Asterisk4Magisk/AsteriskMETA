// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

object VpnDefaults {
    const val LOCAL_PROXY_PORT = 10_809
    const val MTU = 1500
    const val MTU_MIN = 1280
    const val MTU_MAX = 65_535
    const val IPV4_DNS = "172.19.0.2"
    const val IPV4_DNS_HIJACK_ALL = "0.0.0.0"
    const val IPV4_CIDR = "172.19.0.1/30"
    const val IPV6_CIDR = "fdfe:dcba:9876::1/126"
}
