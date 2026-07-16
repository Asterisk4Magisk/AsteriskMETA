// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import engine.mihomo.raw.MihomoRawConfigSnapshot

internal enum class SettingsSectionId {
    Theme,
    Configurations,
    Core,
    Advanced,
    Vpn,
    Tproxy,
    Tun,
    Tun2Socks,
    Bpf2Socks,
    Logs,
    About,
}

internal data class SettingsSearchItem(
    val section: SettingsSectionId,
    val title: String,
    val summary: String = "",
    val value: String = "",
    val optionText: List<String> = emptyList(),
)

internal enum class SettingsSearchFocusStatus {
    Idle,
    Matches,
    NoResults,
}

internal data class SettingsSearchFocusState(
    val status: SettingsSearchFocusStatus,
    val matchCount: Int,
)

internal fun reduceSettingsSearchFocusState(
    query: String,
    matchCount: Int,
): SettingsSearchFocusState = when {
    query.isBlank() -> SettingsSearchFocusState(SettingsSearchFocusStatus.Idle, 0)
    matchCount > 0 -> SettingsSearchFocusState(SettingsSearchFocusStatus.Matches, matchCount)
    else -> SettingsSearchFocusState(SettingsSearchFocusStatus.NoResults, 0)
}

internal data class SettingsRawConfigState(
    val enabled: Boolean,
    val snapshot: MihomoRawConfigSnapshot? = null,
    val parseError: String? = null,
) {
    val showsReadOnlyYamlValues: Boolean
        get() = enabled

    val unavailableReason: String?
        get() = parseError?.takeIf { enabled && snapshot == null }
}

internal fun SettingsSearchItem.matchesSettingsQuery(query: String): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty()) return true
    return sequenceOf(title, summary, value).plus(optionText.asSequence()).any { candidate ->
        candidate.contains(normalized, ignoreCase = true)
    }
}

internal fun filterSettingsItems(
    items: List<SettingsSearchItem>,
    query: String,
): List<SettingsSearchItem> = items.filter { item -> item.matchesSettingsQuery(query) }
