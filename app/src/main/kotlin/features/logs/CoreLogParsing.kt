// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.TimeZone

internal data class CoreLogFile(
    val path: String,
    val defaultLevel: String,
)

internal data class ParsedCoreLogLine(
    val timestampMillis: Long?,
    val level: String,
    val message: String,
)

private const val PersistedCoreLogTimestampPattern =
    """(?:\d+|—|\d{4}[-/]\d{2}[-/]\d{2}\s+\d{2}:\d{2}:\d{2})"""

private val MihomoLogrusLineRegex =
    Regex("""^time=(?:"([^"]+)"|(\S+))\s+level=([A-Za-z]+)\s+msg=(.*)$""")
private val MihomoLogLineRegex =
    Regex("""^($PersistedCoreLogTimestampPattern)\s+\[([A-Za-z]+)]\s*(.*)$""")
private val MihomoLogLineWithoutLevelRegex =
    Regex("""^($PersistedCoreLogTimestampPattern)\s+(.*)$""")

internal fun parseCoreLogLine(
    line: String,
    defaultLevel: String,
    timeZone: TimeZone = TimeZone.getDefault(),
): ParsedCoreLogLine? {
    val trimmedLine = line.trim()
    if (trimmedLine.isEmpty()) {
        return null
    }

    parseAsteriskdJsonLogLine(trimmedLine)?.let { parsed -> return parsed }

    MihomoLogrusLineRegex.matchEntire(trimmedLine)?.let { match ->
        val timestamp = match.groupValues[1].ifEmpty { match.groupValues[2] }
        return ParsedCoreLogLine(
            timestampMillis = parseCoreLogRfc3339Timestamp(timestamp),
            level = match.groupValues[3],
            message = decodeLogrusValue(match.groupValues[4]),
        )
    }

    MihomoLogLineRegex.matchEntire(trimmedLine)?.let { match ->
        val (time, level, message) = match.destructured
        return ParsedCoreLogLine(
            timestampMillis = parseCoreLogTimestamp(time, timeZone),
            level = level,
            message = message,
        )
    }

    MihomoLogLineWithoutLevelRegex.matchEntire(trimmedLine)?.let { match ->
        val (time, message) = match.destructured
        return ParsedCoreLogLine(
            timestampMillis = parseCoreLogTimestamp(time, timeZone),
            level = defaultLevel,
            message = message,
        )
    }

    return ParsedCoreLogLine(
        timestampMillis = null,
        level = defaultLevel,
        message = trimmedLine,
    )
}

private fun parseAsteriskdJsonLogLine(line: String): ParsedCoreLogLine? {
    if (!line.startsWith('{')) return null
    return runCatching {
        val value = LogJson.parseToJsonElement(line) as? JsonObject ?: return@runCatching null
        if (value.keys != AsteriskdLogKeys) return@runCatching null
        val timestamp = value.getValue("timestamp").jsonPrimitive.content
        val level = value.getValue("level").jsonPrimitive.content
        value.getValue("component").jsonPrimitive.content
        value.getValue("event").jsonPrimitive.content
        value.getValue("stream").jsonPrimitive.contentOrNull
        val message = value.getValue("message").jsonPrimitive.content
        value.getValue("truncated").jsonPrimitive.booleanOrNull ?: return@runCatching null
        ParsedCoreLogLine(
            timestampMillis = parseCoreLogRfc3339Timestamp(timestamp),
            level = level,
            message = message,
        )
    }.getOrNull()
}

private val AsteriskdLogKeys = setOf(
    "timestamp", "level", "component", "event", "stream", "message", "truncated",
)

private val LogJson = Json { isLenient = false }

internal fun restoredCoreLogEntries(
    lines: List<String>,
    defaultLevel: String,
    timeZone: TimeZone = TimeZone.getDefault(),
): List<CoreLogEntry> {
    return lines
        .mapNotNull { line -> parseCoreLogLine(line, defaultLevel, timeZone) }
        .mapIndexed { index, parsedLine ->
            CoreLogEntry(
                id = index + 1L,
                timestampMillis = parsedLine.timestampMillis,
                level = parsedLine.level,
                message = parsedLine.message,
            )
        }
}

private fun decodeLogrusValue(value: String): String {
    val trimmedValue = value.trim()
    if (trimmedValue.length < 2 || trimmedValue.first() != '"' || trimmedValue.last() != '"') {
        return trimmedValue
    }

    return buildString(trimmedValue.length - 2) {
        var escaped = false
        trimmedValue.substring(1, trimmedValue.lastIndex).forEach { char ->
            if (!escaped) {
                if (char == '\\') {
                    escaped = true
                } else {
                    append(char)
                }
                return@forEach
            }

            when (char) {
                '\\' -> append('\\')
                '"' -> append('"')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                else -> {
                    append('\\')
                    append(char)
                }
            }
            escaped = false
        }
        if (escaped) {
            append('\\')
        }
    }
}
