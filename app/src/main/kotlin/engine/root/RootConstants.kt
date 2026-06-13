// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

const val RootMihomoUid = 0
const val RootMihomoGid = 3005
const val RootIptablesCommand = "iptables"
const val RootIp6tablesCommand = "ip6tables"
const val RootIpCommand = "ip"
const val RootIp6Command = "ip -6"
const val RootProxyRouteRulePriority = 14599
const val RootFakeIpIcmpReplyChain = "ASTERISK_FAKE_IP_ICMP"
const val RootFakeIpIcmpReplyPreroutingChain = "ASTERISK_FAKE_IP_ICMP_PRE"
const val RootStartupScriptFileName = "startup.sh"
const val RootBootLogFileName = "boot.log"
const val RootConfigFileName = "config.yaml"
const val RootPidFileName = "mihomo-root.pid"
const val BootScriptHeredocDelimiter = "ASTERISKMETA_BOOT_SCRIPT"
const val RootBootScriptDir = "/data/adb/service.d"
const val RootBootScriptPath = "$RootBootScriptDir/asteriskmeta_start.sh"

val RootProxyAppWhitelistSystemUids = listOf(0, 1052)

val RootDefaultBypassPrivateCidrs = listOf(
    "0.0.0.0/8",
    "10.0.0.0/8",
    "100.0.0.0/8",
    "127.0.0.0/8",
    "169.254.0.0/16",
    "192.0.0.0/24",
    "192.0.2.0/24",
    "192.88.99.0/24",
    "192.168.0.0/16",
    "198.51.100.0/24",
    "203.0.113.0/24",
    "224.0.0.0/4",
    "240.0.0.0/4",
    "255.255.255.255/32",
    "::/128",
    "::1/128",
    "::ffff:0:0/96",
    "100::/64",
    "64:ff9b::/96",
    "2001::/32",
    "2001:10::/28",
    "2001:20::/28",
    "2001:db8::/32",
    "2002::/16",
    "fe80::/10",
    "ff00::/8",
)
