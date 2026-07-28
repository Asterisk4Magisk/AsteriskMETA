// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun formatCoreLogTime(
    timestampMillis: Long?,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val timestamp = timestampMillis?.takeIf { it >= 0L } ?: return UnknownCoreLogTime
    return runCatching {
        coreLogDisplayFormat(timeZone).format(Date(timestamp))
    }.getOrDefault(UnknownCoreLogTime)
}

internal fun parseCoreLogTimestamp(
    value: String,
    timeZone: TimeZone = TimeZone.getDefault(),
): Long? {
    val normalized = value.trim().replace('/', '-')
    if (normalized.isEmpty()) {
        return null
    }
    normalized.toLongOrNull()?.let { timestamp ->
        return timestamp.takeIf { it >= 0L }
    }

    return runCatching {
        val position = ParsePosition(0)
        val parsed = coreLogDisplayFormat(timeZone).parse(normalized, position)
        parsed?.time?.takeIf { position.index == normalized.length }
    }.getOrNull()
}

internal fun parseCoreLogRfc3339Timestamp(value: String): Long? {
    val match = CoreLogRfc3339Regex.matchEntire(value.trim()) ?: return null
    val base = match.groupValues[1]
    val fraction = match.groupValues[2]
    val zone = match.groupValues[3]
    val milliseconds = fraction.padEnd(3, '0').take(3).ifEmpty { "000" }
    val normalized = "$base.$milliseconds$zone"

    return runCatching {
        val position = ParsePosition(0)
        val parsed = SimpleDateFormat(CoreLogRfc3339Pattern, Locale.US).apply {
            isLenient = false
        }.parse(normalized, position)
        parsed?.time?.takeIf { position.index == normalized.length }
    }.getOrNull()
}

internal fun decodePersistedLogTimestamp(
    value: String,
    timeZone: TimeZone = TimeZone.getDefault(),
): Long? = parseCoreLogTimestamp(value, timeZone)

internal fun coreLogEntryText(
    entry: CoreLogEntry,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    return "${formatCoreLogTime(entry.timestampMillis, timeZone)}  " +
        "${entry.level.uppercase()}  ${entry.message}"
}

private fun coreLogDisplayFormat(timeZone: TimeZone): SimpleDateFormat {
    return SimpleDateFormat(CoreLogDisplayPattern, Locale.US).apply {
        isLenient = false
        this.timeZone = timeZone
    }
}

private const val CoreLogDisplayPattern = "yyyy-MM-dd HH:mm:ss"
private const val CoreLogRfc3339Pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
private const val UnknownCoreLogTime = "—"

private val CoreLogRfc3339Regex = Regex(
    """^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:\d{2})$""",
)
