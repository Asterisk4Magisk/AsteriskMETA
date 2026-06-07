// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package utils

internal fun Iterable<String>.toTrimmedNonEmptyList(): List<String> {
    return mapNotNull { value -> value.trim().takeIf(String::isNotEmpty) }
}

internal fun Iterable<String>.toTrimmedNonEmptyDistinctList(): List<String> {
    return toTrimmedNonEmptyList().distinct()
}

internal fun String.toIntInRangeOrNull(range: IntRange): Int? {
    return trim().toIntOrNull()?.takeIf { value -> value in range }
}

internal fun String.toIntInRangeOrDefault(range: IntRange, default: Int): Int {
    return toIntInRangeOrNull(range) ?: default
}

