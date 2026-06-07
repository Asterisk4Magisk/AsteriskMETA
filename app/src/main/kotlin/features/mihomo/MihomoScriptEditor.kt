// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.isInDarkTheme

@Composable
internal fun JavaScriptCodeEditor(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    readOnly: Boolean = false,
) {
    val editorColors = rememberScriptEditorColors()
    val highlighting = rememberJavaScriptSyntaxHighlightTransformation(editorColors)
    ScriptCodeTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        visualTransformation = highlighting,
        textStyle = TextStyle(
            color = editorColors.foreground,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(editorColors.accent),
        editorColors = editorColors,
        readOnly = readOnly,
        modifier = modifier,
    )
}

@Composable
internal fun YamlCodeEditor(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    readOnly: Boolean = false,
) {
    val editorColors = rememberScriptEditorColors()
    ScriptCodeTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        visualTransformation = VisualTransformation.None,
        textStyle = TextStyle(
            color = editorColors.foreground,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(editorColors.accent),
        editorColors = editorColors,
        readOnly = readOnly,
        modifier = modifier,
    )
}

@Composable
private fun rememberScriptEditorColors(): ScriptEditorColors {
    val colorScheme = MiuixTheme.colorScheme
    val primary = colorScheme.primary
    val background = colorScheme.secondaryContainer
    val foreground = colorScheme.onSurface
    val darkTheme = isInDarkTheme()
    val onSurfaceVariantSummary = colorScheme.onSurfaceVariantSummary
    val onSecondaryContainer = colorScheme.onSecondaryContainer
    return remember(primary, background, foreground, darkTheme, onSurfaceVariantSummary, onSecondaryContainer) {
        val primaryHue = primary.hue()
        ScriptEditorColors(
            accent = primary,
            foreground = foreground,
            background = background,
            gutter = enhancedThemeColor(primaryHue, darkTheme),
            separator = primary.copy(alpha = if (darkTheme) 0.24f else 0.18f),
            border = primary.copy(alpha = if (darkTheme) 0.20f else 0.14f),
            lineNumber = onSurfaceVariantSummary.copy(alpha = if (darkTheme) 0.78f else 0.68f),
            placeholder = onSecondaryContainer.copy(alpha = if (darkTheme) 0.70f else 0.58f),
            syntax = ScriptSyntaxColors(
                keyword = vividThemeColor(primaryHue, hueOffset = 0f, darkTheme = darkTheme),
                string = vividThemeColor(primaryHue, hueOffset = 88f, darkTheme = darkTheme),
                number = vividThemeColor(primaryHue, hueOffset = -52f, darkTheme = darkTheme),
                literal = vividThemeColor(primaryHue, hueOffset = 176f, darkTheme = darkTheme),
                function = vividThemeColor(primaryHue, hueOffset = 36f, darkTheme = darkTheme),
                comment = onSurfaceVariantSummary.copy(alpha = if (darkTheme) 0.72f else 0.62f),
                punctuation = onSurfaceVariantSummary,
            ),
        )
    }
}

@Composable
private fun rememberJavaScriptSyntaxHighlightTransformation(
    colors: ScriptEditorColors,
): VisualTransformation {
    val syntaxColors = colors.syntax
    return remember(syntaxColors) {
        JavaScriptSyntaxHighlightTransformation(syntaxColors)
    }
}

private data class ScriptEditorColors(
    val accent: Color,
    val foreground: Color,
    val background: Color,
    val gutter: Color,
    val separator: Color,
    val border: Color,
    val lineNumber: Color,
    val placeholder: Color,
    val syntax: ScriptSyntaxColors,
)

private data class ScriptSyntaxColors(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val literal: Color,
    val function: Color,
    val comment: Color,
    val punctuation: Color,
)

private class JavaScriptSyntaxHighlightTransformation(
    private val colors: ScriptSyntaxColors,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(highlightJavaScript(text.text, colors), OffsetMapping.Identity)
    }
}

@Composable
private fun ScriptCodeTextField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    visualTransformation: VisualTransformation,
    textStyle: TextStyle,
    keyboardOptions: KeyboardOptions,
    cursorBrush: SolidColor,
    editorColors: ScriptEditorColors,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val density = LocalDensity.current
    var textLayoutResult by remember {
        mutableStateOf<TextLayoutResult?>(null)
    }
    var editorViewportHeight by remember {
        mutableIntStateOf(0)
    }
    var textViewportWidth by remember {
        mutableIntStateOf(0)
    }
    val lineCount = remember(value.text) {
        value.text.count { char -> char == '\n' } + 1
    }
    val lineNumbers = remember(lineCount) {
        (1..lineCount).joinToString(separator = "\n")
    }
    val gutterWidth = ((lineCount.toString().length.coerceAtLeast(ScriptEditorMinLineNumberDigits) * 8) + 10).dp
    val shape = RoundedCornerShape(TextFieldDefaults.CornerRadius)
    val borderWidth by animateDpAsState(if (isFocused) ScriptEditorBorderWidth else 0.dp)
    val borderColor by animateColorAsState(
        if (isFocused) editorColors.accent else editorColors.border,
    )
    val verticalScrollMaxValue = scrollState.maxValue
    val horizontalScrollMaxValue = horizontalScrollState.maxValue

    LaunchedEffect(
        value.selection,
        value.text,
        textLayoutResult,
        editorViewportHeight,
        textViewportWidth,
        verticalScrollMaxValue,
        horizontalScrollMaxValue,
    ) {
        val layoutResult = textLayoutResult ?: return@LaunchedEffect
        if (editorViewportHeight <= 0 || textViewportWidth <= 0) {
            return@LaunchedEffect
        }

        val cursorOffset = value.selection.end.coerceIn(0, value.text.length)
        val cursorRect = layoutResult.getCursorRect(cursorOffset)
        val verticalPaddingPx = with(density) { ScriptEditorVerticalPadding.toPx() }
        val cursorPaddingPx = with(density) { ScriptEditorCursorScrollPadding.toPx() }
        val nextVerticalScroll = scrollToVisible(
            current = scrollState.value,
            viewportSize = editorViewportHeight,
            targetStart = cursorRect.top + verticalPaddingPx - cursorPaddingPx,
            targetEnd = cursorRect.bottom + verticalPaddingPx + cursorPaddingPx,
            maxValue = verticalScrollMaxValue,
        )
        if (nextVerticalScroll != scrollState.value) {
            scrollState.scrollTo(nextVerticalScroll)
        }

        val nextHorizontalScroll = scrollToVisible(
            current = horizontalScrollState.value,
            viewportSize = textViewportWidth,
            targetStart = cursorRect.left - cursorPaddingPx,
            targetEnd = cursorRect.right + cursorPaddingPx,
            maxValue = horizontalScrollMaxValue,
        )
        if (nextHorizontalScroll != horizontalScrollState.value) {
            horizontalScrollState.scrollTo(nextHorizontalScroll)
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        readOnly = readOnly,
        singleLine = false,
        maxLines = Int.MAX_VALUE,
        minLines = 1,
        visualTransformation = visualTransformation,
        onTextLayout = { result ->
            textLayoutResult = result
        },
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(editorColors.background)
                    .border(borderWidth, borderColor, shape),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            editorViewportHeight = size.height
                        },
                ) {
                    val contentMinHeight = (maxHeight - ScriptEditorVerticalPadding * 2f)
                        .coerceAtLeast(0.dp)

                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .width(gutterWidth)
                                .fillMaxHeight()
                                .background(editorColors.gutter),
                        )
                        Spacer(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(editorColors.separator),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .heightIn(min = contentMinHeight)
                            .padding(vertical = ScriptEditorVerticalPadding),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(gutterWidth)
                                .heightIn(min = contentMinHeight)
                                .padding(
                                    start = 2.dp,
                                    end = 4.dp,
                                ),
                        ) {
                            BasicText(
                                text = lineNumbers,
                                style = textStyle.copy(
                                    color = editorColors.lineNumber,
                                    textAlign = TextAlign.End,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(
                            modifier = Modifier
                                .width(1.dp)
                                .heightIn(min = contentMinHeight),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = contentMinHeight)
                                .padding(
                                    start = 0.dp,
                                    end = ScriptEditorHorizontalPadding,
                                )
                                .onSizeChanged { size ->
                                    textViewportWidth = size.width
                                }
                                .horizontalScroll(horizontalScrollState),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            if (value.text.isEmpty()) {
                                BasicText(
                                    text = label,
                                    style = textStyle.copy(color = editorColors.placeholder),
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            }
        },
    )
}

private fun highlightJavaScript(
    text: String,
    colors: ScriptSyntaxColors,
): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    var index = 0
    while (index < text.length) {
        when (val char = text[index]) {
            '/', '-' -> {
                val commentEnd = text.commentTokenEnd(index)
                if (commentEnd > index) {
                    builder.addStyle(SpanStyle(color = colors.comment), index, commentEnd)
                    index = commentEnd
                } else if (char == '-') {
                    val end = text.numberTokenEnd(index)
                    builder.addStyle(SpanStyle(color = colors.number), index, end)
                    index = end
                } else {
                    builder.addStyle(SpanStyle(color = colors.punctuation), index, index + 1)
                    index += 1
                }
            }

            '\'', '"', '`' -> {
                val end = text.stringTokenEnd(index, char)
                builder.addStyle(SpanStyle(color = colors.string), index, end)
                index = end
            }

            in '0'..'9' -> {
                val end = text.numberTokenEnd(index)
                builder.addStyle(SpanStyle(color = colors.number), index, end)
                index = end
            }

            in 'A'..'Z', in 'a'..'z', '_', '$' -> {
                val end = text.identifierTokenEnd(index)
                val token = text.substring(index, end)
                val tokenColor = when {
                    token in JavaScriptKeywords -> colors.keyword
                    token in JavaScriptLiterals -> colors.literal
                    text.isFunctionCall(end) -> colors.function
                    else -> null
                }
                if (tokenColor != null) {
                    builder.addStyle(SpanStyle(color = tokenColor), index, end)
                }
                index = end
            }

            '{', '}', '[', ']', '(', ')', ':', ',', '.', ';', '+', '*', '%', '=', '!', '<', '>', '?', '&', '|' -> {
                builder.addStyle(SpanStyle(color = colors.punctuation), index, index + 1)
                index += 1
            }

            else -> index += 1
        }
    }
    return builder.toAnnotatedString()
}

private fun String.commentTokenEnd(start: Int): Int {
    if (start + 1 >= length || this[start] != '/') return start
    return when (this[start + 1]) {
        '/' -> {
            var index = start + 2
            while (index < length && this[index] != '\n') {
                index += 1
            }
            index
        }

        '*' -> {
            var index = start + 2
            while (index + 1 < length) {
                if (this[index] == '*' && this[index + 1] == '/') {
                    return index + 2
                }
                index += 1
            }
            length
        }

        else -> start
    }
}

private fun String.stringTokenEnd(start: Int, quote: Char): Int {
    var index = start + 1
    var escaped = false
    while (index < length) {
        val char = this[index]
        if (escaped) {
            escaped = false
        } else if (char == '\\') {
            escaped = true
        } else if (char == quote) {
            return index + 1
        } else if (quote != '`' && char == '\n') {
            return index
        }
        index += 1
    }
    return length
}

private fun String.numberTokenEnd(start: Int): Int {
    var index = start
    while (index < length && this[index] in JavaScriptNumberTokenChars) {
        index += 1
    }
    return index
}

private fun String.identifierTokenEnd(start: Int): Int {
    var index = start
    while (index < length && this[index].isJavaScriptIdentifierChar()) {
        index += 1
    }
    return index
}

private fun String.isFunctionCall(identifierEnd: Int): Boolean {
    var index = identifierEnd
    while (index < length && this[index].isWhitespace()) {
        index += 1
    }
    return index < length && this[index] == '('
}

private fun Char.isJavaScriptIdentifierChar(): Boolean {
    return isLetterOrDigit() || this == '_' || this == '$'
}

private fun Color.hue(): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return hsv[0]
}

private fun vividThemeColor(
    baseHue: Float,
    hueOffset: Float,
    darkTheme: Boolean,
): Color {
    val hue = (baseHue + hueOffset).floorMod(360f)
    val saturation = if (darkTheme) 0.78f else 0.86f
    val value = if (darkTheme) 0.96f else 0.70f
    return Color.hsv(hue = hue, saturation = saturation, value = value)
}

private fun enhancedThemeColor(
    baseHue: Float,
    darkTheme: Boolean,
): Color {
    val saturation = if (darkTheme) 0.24f else 0.20f
    val value = if (darkTheme) 0.26f else 0.96f
    return Color.hsv(hue = (baseHue + 10f).floorMod(360f), saturation = saturation, value = value)
}

private fun Float.floorMod(modulus: Float): Float {
    val result = this % modulus
    return if (result < 0f) result + modulus else result
}

private fun scrollToVisible(
    current: Int,
    viewportSize: Int,
    targetStart: Float,
    targetEnd: Float,
    maxValue: Int,
): Int {
    if (viewportSize <= 0 || maxValue <= 0) {
        return current
    }

    val next = when {
        targetStart < current -> targetStart.toInt()
        targetEnd > current + viewportSize -> (targetEnd - viewportSize + 1f).toInt()
        else -> current
    }
    return next.coerceIn(0, maxValue)
}

private val ScriptEditorBorderWidth = 2.dp
private val ScriptEditorHorizontalPadding = 12.dp
private val ScriptEditorCursorScrollPadding = 24.dp
private val ScriptEditorVerticalPadding = 10.dp
private const val ScriptEditorMinLineNumberDigits = 2

private val JavaScriptNumberTokenChars = setOf('-', '+', '.', 'e', 'E', 'x', 'X', 'b', 'B', 'o', 'O') + ('0'..'9')

private val JavaScriptLiterals = setOf(
    "true",
    "false",
    "null",
    "undefined",
    "NaN",
    "Infinity",
)

private val JavaScriptKeywords = setOf(
    "async",
    "await",
    "break",
    "case",
    "catch",
    "class",
    "const",
    "continue",
    "default",
    "delete",
    "do",
    "else",
    "export",
    "extends",
    "finally",
    "for",
    "from",
    "function",
    "get",
    "if",
    "import",
    "in",
    "instanceof",
    "let",
    "new",
    "of",
    "return",
    "set",
    "static",
    "super",
    "switch",
    "this",
    "throw",
    "try",
    "typeof",
    "var",
    "void",
    "while",
    "with",
    "yield",
)
