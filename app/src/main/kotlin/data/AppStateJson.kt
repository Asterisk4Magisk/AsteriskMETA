// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data

import app.MihomoRuleOverrideState
import app.structuralError
import features.logs.AndroidAppLogger
import kotlinx.serialization.json.Json

private val appStateJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

internal object StringListJson {
    fun encode(values: List<String>): String {
        return appStateJson.encodeToString(values)
    }

    fun decode(payload: String): List<String> {
        return runCatching {
            appStateJson.decodeFromString<List<String>>(payload)
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to decode persisted string list", error)
        }.getOrDefault(emptyList())
    }

    private const val LogTag = "AppStateJson"
}

internal object MihomoRuleOverrideJson {
    fun encode(value: MihomoRuleOverrideState): String = appStateJson.encodeToString(value)

    /**
     * A payload that cannot be used reads as no override: failing here would fail the whole
     * database load, which resets every setting. Only the profile and the kind of failure are
     * logged, never the rules.
     */
    fun decode(profileId: Int, payload: String): MihomoRuleOverrideState {
        return parse(payload).getOrElse { error ->
            val reason = error.takeIf { it is InvalidRuleOverride }?.message ?: error::class.java.simpleName
            AndroidAppLogger.warn(LogTag, "Ignored the rule override of profile $profileId: $reason")
            MihomoRuleOverrideState()
        }
    }

    /** The override stored as [payload], or why it cannot be used. */
    fun parse(payload: String): Result<MihomoRuleOverrideState> {
        val decoded = try {
            appStateJson.decodeFromString<MihomoRuleOverrideState>(payload)
        } catch (error: IllegalArgumentException) {
            return Result.failure(error)
        }
        val reason = decoded.structuralError() ?: return Result.success(decoded)
        return Result.failure(InvalidRuleOverride(reason))
    }

    private class InvalidRuleOverride(reason: String) : IllegalArgumentException(reason)

    private const val LogTag = "AppStateJson"
}
