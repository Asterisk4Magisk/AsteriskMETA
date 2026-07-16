// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

internal data class MihomoCodeEditorBehavior(
    val readOnly: Boolean,
) {
    val editable: Boolean = !readOnly
    val enabled: Boolean = true
    val selectionEnabled: Boolean = true
}

internal enum class MihomoCodeLanguage {
    JavaScript,
    Yaml,
}

internal enum class CodeLexState {
    Normal,
    JavaScriptBlockComment,
    JavaScriptTemplateString,
}

internal enum class CodeTokenKind {
    Normal,
    Keyword,
    Key,
    String,
    Number,
    Literal,
    Function,
    Comment,
    Operator,
}

internal data class CodeToken(
    val start: Int,
    val end: Int,
    val kind: CodeTokenKind,
)

internal data class CodeLineTokens(
    val line: String,
    val state: CodeLexState,
    val tokens: List<CodeToken>,
)

internal fun tokenizeCodeLine(
    line: CharSequence,
    language: MihomoCodeLanguage,
    state: CodeLexState = CodeLexState.Normal,
): CodeLineTokens {
    val text = line.toString()
    return when (language) {
        MihomoCodeLanguage.JavaScript -> tokenizeJavaScriptLine(text, state)
        MihomoCodeLanguage.Yaml -> tokenizeYamlLine(text)
    }
}

private fun tokenizeJavaScriptLine(
    line: String,
    initialState: CodeLexState,
): CodeLineTokens {
    val tokens = mutableListOf<CodeToken>()
    var state = initialState
    var index = 0

    if (state == CodeLexState.JavaScriptBlockComment) {
        val end = line.indexOf("*/")
        if (end < 0) {
            tokens += CodeToken(0, line.length, CodeTokenKind.Comment)
            return CodeLineTokens(line, state, tokens)
        }
        index = end + 2
        tokens += CodeToken(0, index, CodeTokenKind.Comment)
        state = CodeLexState.Normal
    } else if (state == CodeLexState.JavaScriptTemplateString) {
        val end = line.findUnescaped('`')
        if (end < 0) {
            tokens += CodeToken(0, line.length, CodeTokenKind.String)
            return CodeLineTokens(line, state, tokens)
        }
        index = end + 1
        tokens += CodeToken(0, index, CodeTokenKind.String)
        state = CodeLexState.Normal
    }

    while (index < line.length) {
        val start = index
        when {
            line.startsWith("//", index) -> {
                tokens += CodeToken(index, line.length, CodeTokenKind.Comment)
                index = line.length
            }

            line.startsWith("/*", index) -> {
                val end = line.indexOf("*/", startIndex = index + 2)
                if (end < 0) {
                    tokens += CodeToken(index, line.length, CodeTokenKind.Comment)
                    state = CodeLexState.JavaScriptBlockComment
                    index = line.length
                } else {
                    index = end + 2
                    tokens += CodeToken(start, index, CodeTokenKind.Comment)
                }
            }

            line[index] == '`' -> {
                val end = line.findUnescaped('`', index + 1)
                if (end < 0) {
                    tokens += CodeToken(index, line.length, CodeTokenKind.String)
                    state = CodeLexState.JavaScriptTemplateString
                    index = line.length
                } else {
                    index = end + 1
                    tokens += CodeToken(start, index, CodeTokenKind.String)
                }
            }

            line[index] == '\'' || line[index] == '"' -> {
                index = line.quotedTokenEnd(index, line[index])
                tokens += CodeToken(start, index, CodeTokenKind.String)
            }

            line[index].isDigit() ||
                (line[index] == '.' && line.getOrNull(index + 1)?.isDigit() == true) -> {
                index = line.javaScriptNumberEnd(index)
                tokens += CodeToken(start, index, CodeTokenKind.Number)
            }

            line[index].isJavaScriptIdentifierStart() -> {
                index += 1
                while (index < line.length && line[index].isJavaScriptIdentifierPart()) {
                    index += 1
                }
                val identifier = line.substring(start, index)
                val kind = when {
                    identifier in JavaScriptKeywords -> CodeTokenKind.Keyword
                    identifier in JavaScriptLiterals -> CodeTokenKind.Literal
                    line.nextNonWhitespace(index) == '(' -> CodeTokenKind.Function
                    else -> CodeTokenKind.Normal
                }
                tokens += CodeToken(start, index, kind)
            }

            line[index] in JavaScriptOperators -> {
                index += 1
                while (index < line.length && line[index] in JavaScriptOperators) {
                    index += 1
                }
                tokens += CodeToken(start, index, CodeTokenKind.Operator)
            }

            else -> {
                index += 1
                while (
                    index < line.length &&
                    !line.isJavaScriptTokenStart(index)
                ) {
                    index += 1
                }
                tokens += CodeToken(start, index, CodeTokenKind.Normal)
            }
        }
    }
    if (tokens.isEmpty()) {
        tokens += CodeToken(0, 0, CodeTokenKind.Normal)
    }
    return CodeLineTokens(line, state, tokens)
}

private fun tokenizeYamlLine(line: String): CodeLineTokens {
    val tokens = mutableListOf<CodeToken>()
    var index = 0
    while (index < line.length) {
        val start = index
        when {
            line[index] == '#' -> {
                tokens += CodeToken(index, line.length, CodeTokenKind.Comment)
                index = line.length
            }

            line[index] == '\'' || line[index] == '"' -> {
                index = line.quotedTokenEnd(index, line[index])
                tokens += CodeToken(start, index, CodeTokenKind.String)
            }

            line[index].isDigit() ||
                (line[index] in setOf('+', '-') && line.getOrNull(index + 1)?.isDigit() == true) -> {
                index = line.yamlNumberEnd(index)
                tokens += CodeToken(start, index, CodeTokenKind.Number)
            }

            line[index].isYamlWordStart() -> {
                index += 1
                while (index < line.length && line[index].isYamlWordPart()) {
                    index += 1
                }
                val word = line.substring(start, index)
                val kind = when {
                    line.nextNonWhitespace(index) == ':' -> CodeTokenKind.Key
                    word.lowercase() in YamlLiterals -> CodeTokenKind.Literal
                    else -> CodeTokenKind.Normal
                }
                tokens += CodeToken(start, index, kind)
            }

            line[index] in YamlOperators -> {
                index += 1
                tokens += CodeToken(start, index, CodeTokenKind.Operator)
            }

            else -> {
                index += 1
                while (index < line.length && !line.isYamlTokenStart(index)) {
                    index += 1
                }
                tokens += CodeToken(start, index, CodeTokenKind.Normal)
            }
        }
    }
    if (tokens.isEmpty()) {
        tokens += CodeToken(0, 0, CodeTokenKind.Normal)
    }
    return CodeLineTokens(line, CodeLexState.Normal, tokens)
}

private fun String.quotedTokenEnd(start: Int, quote: Char): Int {
    var index = start + 1
    var escaped = false
    while (index < length) {
        val char = this[index]
        when {
            escaped -> escaped = false
            char == '\\' -> escaped = true
            char == quote -> return index + 1
        }
        index += 1
    }
    return length
}

private fun String.findUnescaped(char: Char, startIndex: Int = 0): Int {
    var index = startIndex
    var escaped = false
    while (index < length) {
        val current = this[index]
        when {
            escaped -> escaped = false
            current == '\\' -> escaped = true
            current == char -> return index
        }
        index += 1
    }
    return -1
}

private fun String.javaScriptNumberEnd(start: Int): Int {
    var index = start + 1
    while (index < length && this[index] in JavaScriptNumberCharacters) {
        index += 1
    }
    return index
}

private fun String.yamlNumberEnd(start: Int): Int {
    var index = start + 1
    while (index < length && this[index] in YamlNumberCharacters) {
        index += 1
    }
    return index
}

private fun String.nextNonWhitespace(start: Int): Char? {
    var index = start
    while (index < length && this[index].isWhitespace()) {
        index += 1
    }
    return getOrNull(index)
}

private fun String.isJavaScriptTokenStart(index: Int): Boolean {
    val char = this[index]
    return startsWith("//", index) ||
        startsWith("/*", index) ||
        char == '`' ||
        char == '\'' ||
        char == '"' ||
        char.isDigit() ||
        char.isJavaScriptIdentifierStart() ||
        char in JavaScriptOperators
}

private fun String.isYamlTokenStart(index: Int): Boolean {
    val char = this[index]
    return char == '#' ||
        char == '\'' ||
        char == '"' ||
        char.isDigit() ||
        char.isYamlWordStart() ||
        char in YamlOperators
}

private fun Char.isJavaScriptIdentifierStart(): Boolean = isLetter() || this == '_' || this == '$'

private fun Char.isJavaScriptIdentifierPart(): Boolean = isLetterOrDigit() || this == '_' || this == '$'

private fun Char.isYamlWordStart(): Boolean = isLetter() || this == '_' || this == '-'

private fun Char.isYamlWordPart(): Boolean = isLetterOrDigit() || this == '_' || this == '-'

private val JavaScriptOperators = setOf(
    '{', '}', '[', ']', '(', ')', ':', ',', '.', ';', '+', '-', '*', '/', '%', '=', '!', '<', '>', '?', '&', '|', '~', '^',
)

private val JavaScriptNumberCharacters = setOf(
    '-', '+', '.', '_', 'e', 'E', 'x', 'X', 'b', 'B', 'o', 'O',
) + ('0'..'9') + ('a'..'f') + ('A'..'F')

private val JavaScriptLiterals = setOf(
    "true",
    "false",
    "null",
    "undefined",
    "NaN",
    "Infinity",
)

private val JavaScriptKeywords = setOf(
    "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
    "default", "delete", "do", "else", "export", "extends", "finally", "for", "from", "function",
    "get", "if", "import", "in", "instanceof", "let", "new", "of", "return", "set", "static",
    "super", "switch", "this", "throw", "try", "typeof", "var", "void", "while", "with", "yield",
)

private val YamlOperators = setOf('[', ']', '{', '}', ',', ':', '&', '*', '!', '|', '>', '-', '?')

private val YamlNumberCharacters = setOf('+', '-', '.', '_', 'e', 'E', 'x', 'X', 'o', 'O') +
    ('0'..'9') + ('a'..'f') + ('A'..'F')

private val YamlLiterals = setOf(
    "true", "false", "null", "yes", "no", "on", "off", "~",
)
