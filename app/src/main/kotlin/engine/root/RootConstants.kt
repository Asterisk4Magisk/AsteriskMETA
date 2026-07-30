// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import engine.network.NetworkLimits

const val RootMihomoUid = 0
const val RootMihomoGid = 3005
const val RootAsteriskdUid = 0
const val RootAsteriskdGid = 0
const val RootIptablesCommand = "iptables -w 100"
const val RootIp6tablesCommand = "ip6tables -w 100"
const val RootIpCommand = "ip"
const val RootIp6Command = "ip -6"
const val RootProxyRouteRulePriority = 14599
const val RootProxyFwmark = "0x20000000/0x60000000"
const val RootProxyAuxFwmark = "0x40000000/0x60000000"
const val RootFakeIpIcmpReplyChain = "ASTERISK_FAKE_IP_ICMP"
const val RootFakeIpIcmpReplyPreroutingChain = "ASTERISK_FAKE_IP_ICMP_PRE"
const val RootStartupScriptFileName = "startup.sh"
const val RootBootLogFileName = "boot.log"
const val RootEbpfPolicyFileName = "bpf-policy.json"
const val RootEbpfDirectCidrV4FileName = "direct-cidr-v4.txt"
const val RootEbpfDirectCidrV6FileName = "direct-cidr-v6.txt"
const val RootBpf2SocksConfigFileName = "bpf2socks.json"
const val RootBpf2SocksPidFileName = "bpf2socks.pid"
const val RootEbpfPinnedObjectDir = "/sys/fs/bpf/asteriskmeta"
const val RootBpf2SocksPinnedObjectDir = "/sys/fs/bpf/asteriskmeta/bpf2socks"
const val RootBpf2SocksCgroupPath = "/sys/fs/cgroup"
const val RootBpf2SocksTokenIpv4Prefix = "127.128.0.0/9"
const val RootBpf2SocksTcPreference = 1
const val RootBpf2SocksTcHandle = 1
const val RootBpf2SocksTokenIpv6Prefix = "fd7a:7374:6572:6973::/64"
const val RootBpf2SocksListenAddress = "0.0.0.0"
const val RootBpf2SocksSocksInboundAddress = "127.0.0.1"
const val RootBpf2SocksDefaultBridgePort = NetworkLimits.PORT_MAX - 3
const val RootEbpfXtOutputV4ProgramPath = "/sys/fs/bpf/asteriskmeta/xt_output_v4"
const val RootEbpfXtOutputV6ProgramPath = "/sys/fs/bpf/asteriskmeta/xt_output_v6"
const val RootEbpfXtPreroutingV4ProgramPath = "/sys/fs/bpf/asteriskmeta/xt_prerouting_v4"
const val RootEbpfXtPreroutingV6ProgramPath = "/sys/fs/bpf/asteriskmeta/xt_prerouting_v6"
const val RootEbpfProbeXtOutputV4ProgramPath = "/sys/fs/bpf/asteriskmeta/probe_xt_output_v4"
const val RootEbpfProbeXtOutputV6ProgramPath = "/sys/fs/bpf/asteriskmeta/probe_xt_output_v6"
const val RootEbpfProbeXtPreroutingV4ProgramPath = "/sys/fs/bpf/asteriskmeta/probe_xt_prerouting_v4"
const val RootEbpfProbeXtPreroutingV6ProgramPath = "/sys/fs/bpf/asteriskmeta/probe_xt_prerouting_v6"
const val RootConfigFileName = "config-root.yaml"
const val RootPidFileName = "mihomo-root.pid"
const val RootAsteriskdConfigFileName = "asteriskd.json"
const val RootAsteriskdPidFileName = "asteriskd.pid"
const val RootAsteriskdReadyFileName = "asteriskd.ready"
const val RootAsteriskdStateFileName = "asteriskd.state"
const val RootAsteriskdLogFileName = "asteriskd.log"
const val RootStopScriptFileName = "stop.sh"
const val RootAsteriskdBypass4Anchor = "ASTERISKD_LOCAL4"
const val RootAsteriskdBypass4SlotA = "ASTERISKD_LOCAL4_A"
const val RootAsteriskdBypass4SlotB = "ASTERISKD_LOCAL4_B"
const val RootAsteriskdBypass6Anchor = "ASTERISKD_LOCAL6"
const val RootAsteriskdBypass6SlotA = "ASTERISKD_LOCAL6_A"
const val RootAsteriskdBypass6SlotB = "ASTERISKD_LOCAL6_B"
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
