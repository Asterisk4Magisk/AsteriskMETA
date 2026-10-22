// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

const val DefaultAutoSwitchFailureThreshold = 1
const val DefaultAutoSwitchIntervalSeconds = 30
const val DefaultAutoSwitchExpectedDelayMillis = 300
val AutoSwitchFailureThresholdRange = 1..10
val AutoSwitchIntervalSecondsRange = 10..3600
val AutoSwitchExpectedDelayMillisRange = 0..99999

/** Per-profile settings; groups only apply while [profileId] is the selected profile. */
data class AutoSwitchSettings(
    val enabled: Boolean = false,
    /** Switch back to the user's selection once it passes consecutive health checks. */
    val restore: Boolean = false,
    val profileId: Int = 0,
    val groups: List<String> = emptyList(),
    val preferredRegions: List<String> = emptyList(),
    val preferredKeywords: List<String> = emptyList(),
    val failureThreshold: Int = DefaultAutoSwitchFailureThreshold,
    val intervalSeconds: Int = DefaultAutoSwitchIntervalSeconds,
    val expectedDelayMillis: Int = DefaultAutoSwitchExpectedDelayMillis,
) {
    fun normalized(): AutoSwitchSettings = copy(
        groups = groups.map(String::trim).filter(String::isNotEmpty).distinct(),
        preferredRegions = preferredRegions.distinct(),
        preferredKeywords = preferredKeywords.map(String::trim).filter(String::isNotEmpty).distinct(),
        failureThreshold = failureThreshold.coerceIn(AutoSwitchFailureThresholdRange),
        intervalSeconds = intervalSeconds.coerceIn(AutoSwitchIntervalSecondsRange),
        expectedDelayMillis = expectedDelayMillis.coerceIn(AutoSwitchExpectedDelayMillisRange),
    )
}
