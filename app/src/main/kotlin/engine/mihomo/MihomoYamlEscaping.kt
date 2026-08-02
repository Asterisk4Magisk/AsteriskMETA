// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import org.snakeyaml.engine.v2.api.LoadSettings

internal val MihomoYamlLoadSettings: LoadSettings = LoadSettings.builder()
    .setMaxAliasesForCollections(MihomoYamlMaxCollectionAliases)
    .build()

private const val MihomoYamlMaxCollectionAliases = 256

internal data class EscapedYamlContent(
    val value: String,
    val replacements: Map<String, String>,
) {
    fun restore(text: String): String {
        if (replacements.isEmpty()) return text
        return replacements.entries.fold(text) { result, (token, original) ->
            result.replace(token, original)
        }
    }

    fun restoreParsedValue(value: Any?): Any? {
        if (replacements.isEmpty()) return value
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
    val firstSupplementaryIndex = findFirstSupplementaryIndex()
    if (firstSupplementaryIndex < 0) {
        return EscapedYamlContent(this, emptyMap())
    }

    val replacements = linkedMapOf<String, String>()
    val escaped = buildString(length) {
        append(this@escapeSupplementaryYamlCodePoints, 0, firstSupplementaryIndex)
        var index = firstSupplementaryIndex
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

private fun String.findFirstSupplementaryIndex(): Int {
    for (index in 0 until lastIndex) {
        if (this[index].isHighSurrogate() && this[index + 1].isLowSurrogate()) {
            return index
        }
    }
    return -1
}
