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

        var searchIndex = 0
        var copyIndex = 0
        var restored: StringBuilder? = null
        while (searchIndex < text.length) {
            val tokenStart = text.indexOf(SupplementaryTokenPrefix, searchIndex)
            if (tokenStart < 0) break

            val tokenEnd = text.indexOf("__", tokenStart + SupplementaryTokenPrefix.length)
            if (tokenEnd < 0) break

            val endExclusive = tokenEnd + 2
            val original = replacements[text.substring(tokenStart, endExclusive)]
            if (original == null) {
                searchIndex = tokenStart + SupplementaryTokenPrefix.length
                continue
            }

            val output = restored ?: StringBuilder(text.length).also { restored = it }
            output.append(text, copyIndex, tokenStart)
            output.append(original)
            copyIndex = endExclusive
            searchIndex = endExclusive
        }
        return restored?.append(text, copyIndex, text.length)?.toString() ?: text
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
    val tokenByOriginal = linkedMapOf<String, String>()
    val literalTokens = SupplementaryTokenPattern.findAll(this)
        .mapTo(hashSetOf()) { it.value }
    var nextTokenIndex = 0
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
                val token = tokenByOriginal.getOrPut(original) {
                    var generated: String
                    do {
                        generated = "$SupplementaryTokenPrefix${nextTokenIndex}_${codePoint.toString(16)}__"
                        nextTokenIndex += 1
                    } while (generated in literalTokens)
                    replacements[generated] = original
                    generated
                }
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

private const val SupplementaryTokenPrefix = "__ASTERISKMETA_SUPPLEMENTARY_"

private val SupplementaryTokenPattern = Regex("$SupplementaryTokenPrefix[0-9]+_[0-9a-f]+__")
