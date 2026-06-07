// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

const val DefaultMuxConcurrency = "8"
const val DefaultMuxXudpConcurrency = "16"
const val DefaultMuxUdp443Mode = 0
const val MaxMuxConcurrency = 128
const val MaxMuxXudpConcurrency = 1024
val MuxUdp443Values = listOf("reject", "allow", "skip")

const val DefaultFragmentPackets = "tlshello"
const val DefaultFragmentLength = "100-200"
const val DefaultFragmentInterval = "10-20"
const val MaxFragmentInputLength = 21
val FragmentPacketsValues = listOf("tlshello", "1-2", "1-3", "1-5")

const val MihomoFakeDnsIpv4Pool = "198.18.0.0/15"
const val MihomoFakeDnsIpv6Pool = "fc00::/18"
const val MihomoFakeDnsIpv4OnlyPoolSize = 65_535
const val MihomoFakeDnsDualStackPoolSize = 32_768
const val MihomoLogDisabled = "silent"
val DefaultDirectDnsDomains = listOf("geosite:cn")

const val MihomoDnsModeNormal = 0
const val MihomoDnsModeFakeIp = 1
const val MihomoDnsModeRedirHost = 2
const val MihomoDnsModeHosts = 3
val MihomoDnsModeValues = listOf("normal", "fake-ip", "redir-host", "hosts")

const val DefaultMihomoDnsFakeIpRange = "198.18.0.1/16"
val DefaultMihomoDnsDefaultNameserver = listOf("223.5.5.5")
val DefaultMihomoDnsNameserver = listOf("https://dns.alidns.com/dns-query")
val DefaultMihomoDnsNameserverPolicy = listOf(
    "www.baidu.com=>114.114.114.114",
    "+.internal.crop.com=>10.0.0.1",
    "geosite:cn=>https://dns.alidns.com/dns-query",
)
val DefaultMihomoDnsProxyServerNameserver = listOf("https://doh.pub/dns-query")
val DefaultMihomoDnsFallback = listOf("tls://1.1.1.1#RULES")
val DefaultMihomoDnsFakeIpFilter = listOf("*.lan", "localhost.ptlogin2.qq.com")
val DefaultMihomoDnsFallbackFilterIpcidr = listOf("240.0.0.0/4")
val DefaultMihomoDnsFallbackFilterDomain = listOf("+.google.com", "+.facebook.com", "+.youtube.com")
