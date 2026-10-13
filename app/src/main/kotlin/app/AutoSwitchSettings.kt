// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

data class AutoSwitchSettings(
    val enabled: Boolean = false,
    val profileId: Int = 0,
    val groups: List<String> = emptyList(),
    val preferredRegions: List<String> = emptyList(),
    val preferredKeywords: List<String> = emptyList(),
    val failureThreshold: Int = 3,
    val intervalSeconds: Int = 30,
) {
    fun normalized(): AutoSwitchSettings = copy(
        groups = groups.map(String::trim).filter(String::isNotEmpty).distinct(),
        preferredRegions = preferredRegions.distinct(),
        preferredKeywords = preferredKeywords.map(String::trim).filter(String::isNotEmpty)
            .distinct(),
        failureThreshold = failureThreshold.coerceIn(1, 10),
        intervalSeconds = intervalSeconds.coerceIn(10, 3600),
    )
}
