// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data

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
