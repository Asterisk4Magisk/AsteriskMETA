// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import app.AppState
import app.AutoSwitchSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Top-level key read by AndroidLibClashLite; upstream Mihomo ignores it. */
internal const val MihomoAutoSwitchConfigKey = "asterisk-auto-switch"

internal data class MihomoAutoSwitchRegion(val code: String, val aliases: List<String>)

internal val MihomoAutoSwitchRegions = listOf(
    MihomoAutoSwitchRegion("HK", listOf("🇭🇰", "香港", "Hong Kong", "HongKong")),
    MihomoAutoSwitchRegion("TW", listOf("🇹🇼", "台湾", "台灣", "Taiwan")),
    MihomoAutoSwitchRegion("JP", listOf("🇯🇵", "日本", "东京", "大阪", "Japan", "Tokyo", "Osaka")),
    MihomoAutoSwitchRegion("SG", listOf("🇸🇬", "新加坡", "狮城", "Singapore")),
    MihomoAutoSwitchRegion("US", listOf("🇺🇸", "美国", "美國", "United States", "USA", "Los Angeles")),
    MihomoAutoSwitchRegion("KR", listOf("🇰🇷", "韩国", "韓國", "Korea", "Seoul")),
    MihomoAutoSwitchRegion("GB", listOf("🇬🇧", "英国", "英國", "United Kingdom", "UK", "Britain", "London")),
    MihomoAutoSwitchRegion("DE", listOf("🇩🇪", "德国", "德國", "Germany", "Frankfurt")),
    MihomoAutoSwitchRegion("FR", listOf("🇫🇷", "法国", "法國", "France", "Paris")),
    MihomoAutoSwitchRegion("CA", listOf("🇨🇦", "加拿大", "Canada")),
    MihomoAutoSwitchRegion("AU", listOf("🇦🇺", "澳大利亚", "澳洲", "Australia", "Sydney")),
    MihomoAutoSwitchRegion("NL", listOf("🇳🇱", "荷兰", "荷蘭", "Netherlands", "Amsterdam")),
)

/** The core schema, shared by the YAML key, the Clash API and the embedded bridge. */
@Serializable
internal data class MihomoAutoSwitchOptions(
    val enable: Boolean = false,
    // The core reports null before its first configuration apply.
    val groups: List<String>? = null,
    @SerialName("preferred-filter") val preferredFilter: String = "",
    @SerialName("failure-threshold") val failureThreshold: Int = 0,
    val interval: Int = 0,
    val url: String = "",
    val timeout: Int = 0,
    @SerialName("expected-status") val expectedStatus: String = "",
    val concurrency: Int = 0,
) {
    fun toYamlMap(): Map<String, Any?> = linkedMapOf(
        "enable" to enable,
        "groups" to groups.orEmpty(),
        "preferred-filter" to preferredFilter,
        "failure-threshold" to failureThreshold,
        "interval" to interval,
        "url" to url,
        "timeout" to timeout,
        "expected-status" to expectedStatus,
        "concurrency" to concurrency,
    )

    /**
     * Compares with the core's normalized copy, which reports omitted groups as null. Two disabled
     * values are equivalent whatever their other fields.
     */
    fun sameAs(other: MihomoAutoSwitchOptions): Boolean =
        (!enable && !other.enable) ||
            copy(groups = groups.orEmpty()) == other.copy(groups = other.groups.orEmpty())
}

/**
 * Core settings for the selected profile. They are disabled for standby cores and for settings
 * saved against another profile, whose group names may not exist here.
 */
internal fun AppState.toMihomoAutoSwitchOptions(active: Boolean = true): MihomoAutoSwitchOptions {
    val settings = autoSwitch.normalized()
    val enabled = active && settings.enabled && settings.profileId == selectedMihomoProfileId &&
        settings.groups.isNotEmpty()
    return MihomoAutoSwitchOptions(
        enable = enabled,
        groups = if (enabled) settings.groups else emptyList(),
        preferredFilter = settings.toPreferredFilter(),
        failureThreshold = settings.failureThreshold,
        interval = settings.intervalSeconds,
        url = connectionTestUrl.trim(),
        timeout = connectionTestTimeoutMillis,
        concurrency = connectionTestConcurrent,
    )
}

/**
 * Builds a regexp2 pattern, the syntax Mihomo uses for group filters. Latin aliases and region
 * codes must stand alone, so "UK" does not match "Ukraine"; flags, CJK names and custom keywords
 * match anywhere.
 */
internal fun AutoSwitchSettings.toPreferredFilter(): String {
    val alternatives = MihomoAutoSwitchRegions
        .filter { it.code in preferredRegions }
        .flatMap { region -> (region.aliases + region.code).map(::regionAliasPattern) } +
        preferredKeywords.map(::escapeRegexp2)
    return if (alternatives.isEmpty()) "" else alternatives.distinct().joinToString("|", "(?i)(?:", ")")
}

private fun regionAliasPattern(alias: String): String {
    val escaped = escapeRegexp2(alias)
    return if (alias.all { it in 'A'..'Z' || it in 'a'..'z' || it == ' ' }) {
        "(?<![A-Za-z])$escaped(?![A-Za-z])"
    } else {
        escaped
    }
}

// regexp2 follows .NET syntax, which has no \Q...\E quoting.
private fun escapeRegexp2(value: String): String = buildString(value.length) {
    value.forEach { char ->
        if (char in Regexp2MetaCharacters) append('\\')
        append(char)
    }
}

private const val Regexp2MetaCharacters = "\\*+?|{}[]()^$.#"
