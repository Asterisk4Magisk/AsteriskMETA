// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

internal enum class LogLevelFilter {
    All,
    Debug,
    Info,
    Warning,
    Error,
}

internal fun reduceLogEntries(
    entries: List<CoreLogEntry>,
    query: String,
    filter: LogLevelFilter = LogLevelFilter.All,
): List<CoreLogEntry> {
    val normalizedQuery = query.trim()
    return entries.filter { entry ->
        val matchesQuery = normalizedQuery.isEmpty() || listOf(
            formatCoreLogTime(entry.timestampMillis),
            entry.level,
            entry.message,
        ).any { value -> value.contains(normalizedQuery, ignoreCase = true) }
        val level = entry.level.trim().lowercase()
        val matchesFilter = when (filter) {
            LogLevelFilter.All -> true
            LogLevelFilter.Debug -> level == "debug"
            LogLevelFilter.Info -> level == "info"
            LogLevelFilter.Warning -> level == "warning" || level == "warn"
            LogLevelFilter.Error -> level == "error"
        }
        matchesQuery && matchesFilter
    }
}

internal fun logEntriesForExport(entries: List<CoreLogEntry>): List<CoreLogEntry> = entries
