// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** One match inside one rendered line of a provider preview. */
internal data class MihomoRuleTextMatch(
    val lineIndex: Int,
    val start: Int,
    val end: Int,
) {
    val range: IntRange get() = start until end
}

/** Matches of one query over the previewed lines, grouped for rendering. */
internal data class MihomoRuleTextMatches(
    val matches: List<MihomoRuleTextMatch> = emptyList(),
    val byLine: Map<Int, List<IntRange>> = emptyMap(),
    val truncated: Boolean = false,
    val patternInvalid: Boolean = false,
) {
    val hasMatches: Boolean get() = matches.isNotEmpty()
}

/**
 * Matches [query] against every previewed line.
 *
 * A literal query is compared case-insensitively unless [matchCase] keeps the case; a regex query
 * follows the same case rule. Matching stops at [limit] hits so a broad pattern over a large rule
 * set cannot allocate without bound — [MihomoRuleTextMatches.truncated] reports that cut-off, and an
 * unusable pattern is reported instead of thrown at the UI.
 */
internal fun searchMihomoRuleText(
    lines: List<String>,
    query: String,
    matchCase: Boolean,
    useRegex: Boolean,
    limit: Int = MaxMihomoRuleTextMatches,
): MihomoRuleTextMatches {
    if (query.isEmpty() || lines.isEmpty()) return MihomoRuleTextMatches()
    val collector = MihomoRuleTextMatchCollector(limit)
    if (useRegex) {
        val pattern = try {
            Pattern.compile(query, if (matchCase) 0 else Pattern.CASE_INSENSITIVE)
        } catch (_: PatternSyntaxException) {
            return MihomoRuleTextMatches(patternInvalid = true)
        }
        collectRegexMatches(lines, pattern, collector)
    } else {
        collectLiteralMatches(lines, query, matchCase, collector)
    }
    return collector.build()
}

private fun collectRegexMatches(
    lines: List<String>,
    pattern: Pattern,
    collector: MihomoRuleTextMatchCollector,
) {
    for (lineIndex in lines.indices) {
        val line = lines[lineIndex]
        val matcher = pattern.matcher(line)
        var from = 0
        while (from <= line.length && collector.hasRoom()) {
            if (!matcher.find(from)) break
            val start = matcher.start()
            val end = matcher.end()
            collector.add(lineIndex, start, end)
            // An empty match still has to advance, otherwise the scan never ends.
            from = if (end > start) end else start + 1
        }
        if (!collector.hasRoom()) return
    }
}

private fun collectLiteralMatches(
    lines: List<String>,
    query: String,
    matchCase: Boolean,
    collector: MihomoRuleTextMatchCollector,
) {
    for (lineIndex in lines.indices) {
        val line = lines[lineIndex]
        val lastLineStart = line.length - query.length
        var from = 0
        while (from <= lastLineStart && collector.hasRoom()) {
            val found = line.indexOf(query, from, matchCase)
            if (found < 0) break
            collector.add(lineIndex, found, found + query.length)
            from = found + query.length
        }
        if (!collector.hasRoom()) return
    }
}

/** Index of the first occurrence of [query] at or after [from], optionally ignoring case. */
private fun String.indexOf(query: String, from: Int, matchCase: Boolean): Int {
    if (matchCase) return indexOf(query, from)
    var index = from
    while (index <= length - query.length) {
        if (regionMatches(index, query, 0, query.length, ignoreCase = true)) return index
        index++
    }
    return -1
}

private class MihomoRuleTextMatchCollector(private val limit: Int) {
    private val matches = ArrayList<MihomoRuleTextMatch>()
    private val byLine = HashMap<Int, MutableList<IntRange>>()
    private var truncated = false

    fun hasRoom(): Boolean = !truncated

    fun add(lineIndex: Int, start: Int, end: Int) {
        if (start >= end) return
        if (matches.size >= limit) {
            // One entry past the limit is enough to know the scan was cut short.
            truncated = true
            return
        }
        matches.add(MihomoRuleTextMatch(lineIndex = lineIndex, start = start, end = end))
        byLine.getOrPut(lineIndex) { ArrayList() }.add(start until end)
    }

    fun build(): MihomoRuleTextMatches = MihomoRuleTextMatches(
        matches = matches,
        byLine = byLine,
        truncated = truncated,
    )
}

private const val MaxMihomoRuleTextMatches = 5_000
