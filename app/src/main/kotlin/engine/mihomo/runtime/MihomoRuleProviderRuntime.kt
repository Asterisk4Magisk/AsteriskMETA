// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal fun JsonObject.toMihomoRuleProviderRuntimeSummaries(): Map<String, MihomoRuleProviderRuntimeSummary> {
    val providers = this["providers"] as? JsonObject ?: return emptyMap()
    return providers.mapNotNull { (name, value) ->
        val provider = runCatching { value.jsonObject }.getOrNull() ?: return@mapNotNull null
        val summary = provider.toMihomoRuleProviderRuntimeSummary(name)
        summary.name to summary
    }.toMap()
}

internal fun JsonObject.toMihomoRuleProviderRuntimeSummary(
    fallbackName: String,
): MihomoRuleProviderRuntimeSummary {
    return MihomoRuleProviderRuntimeSummary(
        name = stringValue("name") ?: fallbackName,
        behavior = stringValue("behavior")?.lowercase().orEmpty(),
        format = stringValue("format")?.lowercase().orEmpty(),
        ruleCount = intValue("ruleCount") ?: 0,
        type = stringValue("type").orEmpty(),
        vehicleType = stringValue("vehicleType").orEmpty(),
        updatedAtMillis = longValue("updatedAt")
            ?: stringValue("updatedAt")?.toMihomoProviderTimestampMillis()
            ?: 0L,
    )
}

internal fun String.toMihomoProviderTimestampMillis(): Long {
    val normalizedTimestamp = replace(Rfc3339FractionPattern) { match ->
        "." + match.groupValues[1].padEnd(3, '0').take(3)
    }
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ssX",
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.ROOT).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(normalizedTimestamp)?.time
        }.getOrNull()
    } ?: 0L
}

private val Rfc3339FractionPattern = Regex("""\.(\d+)(?=Z|[+-]\d{2}:?\d{2}$)""")

private fun JsonObject.stringValue(name: String): String? {
    return runCatching { this[name]?.jsonPrimitive?.contentOrNull }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
}

private fun JsonObject.intValue(name: String): Int? {
    return runCatching { this[name]?.jsonPrimitive?.intOrNull }.getOrNull()
}

private fun JsonObject.longValue(name: String): Long? {
    return runCatching { this[name]?.jsonPrimitive?.longOrNull }.getOrNull()
}
