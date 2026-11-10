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

/** Leaves the profile's find-process-mode untouched; the others are written as [MihomoFindProcessModeValues]. */
const val MihomoFindProcessModeFollowProfile = 0
const val MihomoFindProcessModeOff = 1
const val MihomoFindProcessModeStrict = 2
const val MihomoFindProcessModeAlways = 3
val MihomoFindProcessModeValues = listOf("", "off", "strict", "always")

const val MihomoSnifferProtocolOverrideFollowGlobal = 0
const val MihomoSnifferProtocolOverrideEnabled = 1
const val MihomoSnifferProtocolOverrideDisabled = 2

const val DefaultMihomoDnsFakeIpRange = engine.network.DefaultFakeIpIpv4Pool
val DefaultMihomoDnsDefaultNameserver = listOf("system")
val DefaultMihomoDnsNameserver = listOf("https://dns.alidns.com/dns-query")
val DefaultMihomoDnsNameserverPolicy = listOf(
    "www.baidu.com=>114.114.114.114",
    "+.internal.crop.com=>10.0.0.1",
    "geosite:cn=>system",
)
val DefaultMihomoDnsProxyServerNameserver = listOf("https://doh.pub/dns-query")
val DefaultMihomoDnsFallback = listOf("tls://8.8.8.8#RULES")
val DefaultMihomoDnsFakeIpFilter = listOf("*.lan", "localhost.ptlogin2.qq.com")
val DefaultMihomoDnsFallbackFilterIpcidr = listOf("240.0.0.0/4")
val DefaultMihomoDnsFallbackFilterDomain = listOf("+.google.com", "+.facebook.com", "+.youtube.com")

val DefaultMihomoSnifferHttpPorts = listOf("80", "8080-8880")
val DefaultMihomoSnifferTlsPorts = listOf("443")
val DefaultMihomoSnifferQuicPorts = listOf("443")
