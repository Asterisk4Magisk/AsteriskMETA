// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.raw

import app.modes.RunModeBpf2Socks
import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import app.modes.RunModeTproxy
import app.modes.RunModeVpnService

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
    val requiresSocksInbound = runMode == RunModeTun2Socks ||
        runMode == RunModeBpf2Socks ||
        runMode == RunModeVpnService && vpnUsesHev
    when (runMode) {
        RunModeTproxy -> if (tproxyPort.value == null) {
            issue(RawConfigReadiness.Blocked, tproxyPort.path, tproxyPort.problem ?: "TPROXY requires tproxy-port")
        }
        RunModeTun -> if (tunInbound.value == null) {
            issue(RawConfigReadiness.Blocked, tunInbound.path, tunInbound.problem ?: "Root TUN requires one compatible TUN inbound")
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
    if (dnsHijackRequested && dnsHijack.value?.proven != true) {
        issue(
            RawConfigReadiness.Degraded,
            "rules",
            "DNS hijack is not installed because YAML does not prove an enabled DNS outbound rule for port 53",
        )
    }
    val readiness = when {
        issues.any { it.readiness == RawConfigReadiness.Blocked } -> RawConfigReadiness.Blocked
        issues.isNotEmpty() -> RawConfigReadiness.Degraded
        else -> RawConfigReadiness.Ready
    }
    return MihomoRawConfigCheckResult(readiness, issues)
}
