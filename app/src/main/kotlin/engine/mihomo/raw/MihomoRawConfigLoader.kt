// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.raw

import android.content.Context
import app.AppState
import engine.mihomo.mihomoProfileContentStore
import engine.mihomo.selectedMihomoProfileOrNull

internal fun AppState.usesRawMihomoConfig(): Boolean =
    selectedMihomoProfileOrNull()?.disableOverrides == true

internal fun Context.loadSelectedRawConfig(appState: AppState): MihomoRawConfigParseResult? {
    val profile = appState.selectedMihomoProfileOrNull()?.takeIf { it.disableOverrides } ?: return null
    return MihomoRawConfigParser.parse(mihomoProfileContentStore().readBytes(profile))
}

internal class MihomoRawConfigStartupException(message: String) : IllegalStateException(message)

internal fun Context.requireStartableRawConfig(
    appState: AppState,
): Pair<MihomoRawConfigSnapshot, MihomoRawConfigCheckResult>? {
    val parsed = loadSelectedRawConfig(appState) ?: return null
    val snapshot = parsed.snapshot ?: throw MihomoRawConfigStartupException(
        "${appState.selectedMihomoProfileOrNull()?.name.orEmpty()}: ${parsed.error.orEmpty()}",
    )
    val check = snapshot.check(
        runMode = appState.runMode,
        vpnUsesHev = appState.enableVpnHevTun,
        dnsHijackRequested = appState.enableLocalDns,
    )
    if (!check.canStart) {
        val details = check.issues
            .filter { it.readiness == RawConfigReadiness.Blocked }
            .joinToString("; ") { issue -> "${issue.fieldPath}: ${issue.reason}" }
        throw MihomoRawConfigStartupException(
            "${appState.selectedMihomoProfileOrNull()?.name.orEmpty()} (${appState.runMode}): $details",
        )
    }
    return snapshot to check
}
