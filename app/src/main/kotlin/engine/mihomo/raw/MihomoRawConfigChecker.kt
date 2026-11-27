// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.raw

import app.modes.RunModeBpf2Socks
import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import app.modes.RunModeTproxy
import app.modes.RunModeVpnService
import engine.proxy.FlowMetadataInboundTag
import engine.proxy.usesFlowMetadata

internal fun MihomoRawConfigSnapshot.provesDnsHijack(runMode: Int, vpnUsesHev: Boolean = false): Boolean {
    val hijack = dnsHijack.value ?: return false
    return if (usesFlowMetadata(runMode, vpnUsesHev)) hijack.bridgeProven else hijack.proven
}

internal fun MihomoRawConfigParseResult.check(
    runMode: Int,
    vpnUsesHev: Boolean = false,
    dnsHijackRequested: Boolean = false,
): MihomoRawConfigCheckResult {
    val parsedSnapshot = snapshot
    if (parsedSnapshot != null) {
        return parsedSnapshot.check(runMode, vpnUsesHev, dnsHijackRequested)
    }
    return MihomoRawConfigCheckResult(
        readiness = RawConfigReadiness.Blocked,
        issues = listOf(
            RawConfigIssue(
                readiness = RawConfigReadiness.Blocked,
                fieldPath = "yaml",
                reason = error ?: "Configuration is invalid",
            ),
        ),
    )
}

internal fun MihomoRawConfigSnapshot.check(
    runMode: Int,
    vpnUsesHev: Boolean = false,
    dnsHijackRequested: Boolean = false,
): MihomoRawConfigCheckResult {
    val issues = mutableListOf<RawConfigIssue>()
    fun issue(readiness: RawConfigReadiness, path: String, reason: String) {
        issues += RawConfigIssue(readiness, path, reason)
    }
    if (api.value == null) {
        issue(
            RawConfigReadiness.Degraded,
            api.path,
            api.problem ?: "Mihomo API is not configured; dependent features are unavailable",
        )
    }
    val requiresSocksInbound = usesFlowMetadata(runMode, vpnUsesHev)
    when (runMode) {
        RunModeTproxy -> if (tproxyPort.value == null) {
            issue(RawConfigReadiness.Blocked, tproxyPort.path, tproxyPort.problem ?: "TPROXY requires tproxy-port")
        }
        RunModeTun -> if (tunInbound.value == null) {
            issue(RawConfigReadiness.Blocked, tunInbound.path, tunInbound.problem ?: "ROOT TUN requires one compatible TUN inbound")
        }
        RunModeTun2Socks, RunModeBpf2Socks -> if (socksInbound.value == null) {
            issue(RawConfigReadiness.Blocked, socksInbound.path, socksInbound.problem ?: "This run mode requires a SOCKS or Mixed inbound")
        }
        RunModeVpnService -> if (vpnUsesHev && socksInbound.value == null) {
            issue(RawConfigReadiness.Blocked, socksInbound.path, socksInbound.problem ?: "HEV VPN requires a SOCKS or Mixed inbound")
        }
    }
    if (!requiresSocksInbound && socksInbound.value == null) {
        issue(
            RawConfigReadiness.Degraded,
            socksInbound.path,
            socksInbound.problem ?: "Local proxy features are unavailable because YAML has no SOCKS or Mixed inbound",
        )
    }
    if (dnsHijackRequested && !provesDnsHijack(runMode, vpnUsesHev)) {
        issue(
            RawConfigReadiness.Degraded,
            "rules",
            if (requiresSocksInbound) {
                "DNS hijack requires enabled DNS outbound rules covering TCP and UDP port 53 on $FlowMetadataInboundTag"
            } else {
                "DNS hijack is not installed because YAML does not prove enabled DNS outbound rules for both TCP and UDP port 53"
            },
        )
    }
    val readiness = when {
        issues.any { it.readiness == RawConfigReadiness.Blocked } -> RawConfigReadiness.Blocked
        issues.isNotEmpty() -> RawConfigReadiness.Degraded
        else -> RawConfigReadiness.Ready
    }
    return MihomoRawConfigCheckResult(readiness, issues)
}
