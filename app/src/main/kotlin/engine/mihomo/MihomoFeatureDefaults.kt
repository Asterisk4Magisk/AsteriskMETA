// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

const val MihomoDnsModeNormal = 0
const val MihomoDnsModeFakeIp = 1
const val MihomoDnsModeRedirHost = 2
const val MihomoDnsModeHosts = 3
val MihomoDnsModeValues = listOf("normal", "fake-ip", "redir-host", "hosts")

const val MihomoGeodataLoaderStandard = 0
const val MihomoGeodataLoaderMemconservative = 1
val MihomoGeodataLoaderValues = listOf("standard", "memconservative")

const val MihomoSnifferProtocolOverrideFollowGlobal = 0
const val MihomoSnifferProtocolOverrideEnabled = 1
const val MihomoSnifferProtocolOverrideDisabled = 2

const val DefaultMihomoDnsFakeIpRange = "198.18.0.1/16"
val DefaultMihomoDnsDefaultNameserver = listOf("223.5.5.5")
val DefaultMihomoDnsNameserver = listOf("https://dns.alidns.com/dns-query")
val DefaultMihomoDnsNameserverPolicy = listOf(
    "www.baidu.com=>114.114.114.114",
    "+.internal.crop.com=>10.0.0.1",
    "geosite:cn=>https://dns.alidns.com/dns-query",
)
val DefaultMihomoDnsProxyServerNameserver = listOf("https://doh.pub/dns-query")
val DefaultMihomoDnsFallback = listOf("tls://8.8.8.8#RULES")
val DefaultMihomoDnsFakeIpFilter = listOf("*.lan", "localhost.ptlogin2.qq.com")
val DefaultMihomoDnsFallbackFilterIpcidr = listOf("240.0.0.0/4")
val DefaultMihomoDnsFallbackFilterDomain = listOf("+.google.com", "+.facebook.com", "+.youtube.com")

val DefaultMihomoSnifferHttpPorts = listOf("80", "8080-8880")
val DefaultMihomoSnifferTlsPorts = listOf("443")
val DefaultMihomoSnifferQuicPorts = listOf("443")
