// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

internal fun String.displayMihomoProtocolName(compact: Boolean = false): String {
    val protocol = trim().ifBlank { return "Proxy" }
    val normalized = protocol.lowercase().replace("_", "-").replace(" ", "-")
    if (compact) {
        when (normalized) {
            "ss", "shadowsocks" -> return "SS"
            "ssr", "shadowsocksr", "shadowsocks-r" -> return "SSR"
            "hysteria2", "hy2" -> return "HY2"
            "wireguard", "wire-guard", "wg" -> return "WG"
            "tailscale" -> return "Tailscale"
            "trusttunnel", "trust-tunnel" -> return "TrustTun"
            "gostrelay", "gost-relay" -> return "GOST Relay"
            "compatible" -> return "Compat"
            "rejectdrop", "reject-drop" -> return "Reject Drop"
            "loadbalance", "load-balance" -> return "Balance"
        }
    }
    return when (normalized) {
        "vmess" -> "VMess"
        "vless" -> "VLESS"
        "ss", "shadowsocks" -> "Shadowsocks"
        "ssr", "shadowsocksr", "shadowsocks-r" -> "ShadowsocksR"
        "socks", "socks5" -> "SOCKS5"
        "http" -> "HTTP"
        "https" -> "HTTPS"
        "trojan" -> "Trojan"
        "hysteria" -> "Hysteria"
        "hysteria2", "hy2" -> "Hysteria2"
        "tuic" -> "TUIC"
        "wireguard", "wire-guard", "wg" -> "WireGuard"
        "snell" -> "Snell"
        "ssh" -> "SSH"
        "dns" -> "DNS"
        "mieru" -> "Mieru"
        "anytls", "any-tls" -> "AnyTLS"
        "masque" -> "MASQUE"
        "openvpn", "open-vpn" -> "OpenVPN"
        "tailscale" -> "Tailscale"
        "trusttunnel", "trust-tunnel" -> "TrustTunnel"
        "gostrelay", "gost-relay" -> "GostRelay"
        "direct" -> "Direct"
        "reject" -> "Reject"
        "rejectdrop", "reject-drop" -> "RejectDrop"
        "compatible" -> "Compatible"
        "pass" -> "Pass"
        "passrule", "pass-rule" -> "PassRule"
        "relay" -> "Relay"
        "select", "selector" -> "Selector"
        "fallback" -> "Fallback"
        "urltest", "url-test" -> "URLTest"
        "loadbalance", "load-balance" -> "LoadBalance"
        "built-in", "builtin" -> "Built-in"
        else -> protocol
    }
}
