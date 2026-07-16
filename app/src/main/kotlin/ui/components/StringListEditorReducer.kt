// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

internal fun stringListItemKey(index: Int): String = "string-list-item-$index"

internal data class StringListEditResult(
    val values: List<String>,
    val error: String? = null,
)

internal data class StringListBatchResult(
    val values: List<String>? = null,
    val errorLine: Int? = null,
    val error: String? = null,
)

internal fun addStringListValue(
    source: List<String>,
    input: String,
    validate: (String) -> String?,
): StringListEditResult {
    val value = input.trim()
    val error = validate(value)
    return if (error == null) StringListEditResult(source + value) else StringListEditResult(source, error)
}

internal fun editStringListValue(
    source: List<String>,
    index: Int,
    input: String,
    validate: (String) -> String?,
): StringListEditResult {
    val value = input.trim()
    val error = validate(value)
    if (error != null || index !in source.indices) return StringListEditResult(source, error)
    return StringListEditResult(source.toMutableList().apply { this[index] = value })
}

internal fun parseStringListBatch(
    input: String,
    validate: (String) -> String?,
): StringListBatchResult {
    val values = mutableListOf<String>()
    input.lineSequence().forEachIndexed { index, line ->
        val value = line.trim()
        if (value.isEmpty()) return@forEachIndexed
        val error = validate(value)
        if (error != null) {
            return StringListBatchResult(errorLine = index + 1, error = error)
        }
        values += value
    }
    return StringListBatchResult(values = values)
}
