// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

internal data class EscapedYamlContent(
    val value: String,
    val replacements: Map<String, String>,
) {
    fun restore(text: String): String {
        return replacements.entries.fold(text) { result, (token, original) ->
            result.replace(token, original)
        }
    }

    fun restoreParsedValue(value: Any?): Any? {
        return when (value) {
            is Map<*, *> -> linkedMapOf<Any?, Any?>().apply {
                value.forEach { (key, childValue) ->
                    put(restoreParsedValue(key), restoreParsedValue(childValue))
                }
            }
            is List<*> -> value.map(::restoreParsedValue)
            is String -> restore(value)
            else -> value
        }
    }
}

internal fun String.escapeSupplementaryYamlCodePoints(): EscapedYamlContent {
    val replacements = linkedMapOf<String, String>()
    val escaped = buildString(length) {
        var index = 0
        while (index < this@escapeSupplementaryYamlCodePoints.length) {
            val char = this@escapeSupplementaryYamlCodePoints[index]
            if (
                char.isHighSurrogate() &&
                index + 1 < this@escapeSupplementaryYamlCodePoints.length &&
                this@escapeSupplementaryYamlCodePoints[index + 1].isLowSurrogate()
            ) {
                val codePoint = Character.toCodePoint(
                    char,
                    this@escapeSupplementaryYamlCodePoints[index + 1],
                )
                val original = String(Character.toChars(codePoint))
                val token = "__ASTERISKMETA_SUPPLEMENTARY_${replacements.size}_${codePoint.toString(16)}__"
                replacements[token] = original
                append(token)
                index += 2
            } else {
                append(char)
                index += 1
            }
        }
    }
    return EscapedYamlContent(escaped, replacements)
}
