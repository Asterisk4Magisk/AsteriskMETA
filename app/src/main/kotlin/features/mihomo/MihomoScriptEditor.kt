// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager.LineTokenizeResult
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import ui.isInDarkTheme
import ui.theme.AsteriskShapeTokens

@Stable
internal class MihomoCodeEditorState(
    initialText: String = "",
) {
    private var editor: CodeEditor? = null
    private var retainedText = initialText
    private var moveCursorToEnd = true

    var documentVersion by mutableIntStateOf(0)
        private set

    var isEmpty by mutableStateOf(initialText.isEmpty())
        private set

    var isFocused by mutableStateOf(false)
        private set

    internal fun attach(editor: CodeEditor) {
        if (this.editor === editor) return
        this.editor = editor
        applyRetainedText(editor)
    }

    internal fun detach(editor: CodeEditor) {
        if (this.editor !== editor) return
        retainedText = editor.text.toString()
        isEmpty = editor.text.isEmpty()
        isFocused = false
        this.editor = null
    }

    internal fun onContentChanged(editor: CodeEditor, action: Int) {
        if (this.editor !== editor || action == ContentChangeEvent.ACTION_SET_NEW_TEXT) return
        isEmpty = editor.text.isEmpty()
        documentVersion += 1
    }

    internal fun onFocusChanged(editor: CodeEditor, focused: Boolean) {
        if (this.editor === editor) {
            isFocused = focused
        }
    }

    fun snapshotText(): String = editor?.text?.toString() ?: retainedText

    fun replaceText(text: String, placeCursorAtEnd: Boolean = true) {
        if (text == retainedText && editor?.text?.toString() == text) return
        retainedText = text
        moveCursorToEnd = placeCursorAtEnd
        isEmpty = text.isEmpty()
        editor?.let(::applyRetainedText)
        documentVersion += 1
    }

    private fun applyRetainedText(editor: CodeEditor) {
        editor.setText(retainedText)
        if (moveCursorToEnd) {
            val lastLine = editor.lineCount - 1
            editor.setSelection(lastLine, editor.text.getColumnCount(lastLine), false)
        } else {
            editor.setSelection(0, 0, false)
        }
    }
}

@Composable
internal fun JavaScriptCodeEditor(
    label: String,
    state: MihomoCodeEditorState,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    SoraCodeEditor(
        label = label,
        state = state,
        language = MihomoCodeLanguage.JavaScript,
        readOnly = readOnly,
        modifier = modifier,
    )
}

@Composable
internal fun YamlCodeEditor(
    label: String,
    state: MihomoCodeEditorState,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    SoraCodeEditor(
        label = label,
        state = state,
        language = MihomoCodeLanguage.Yaml,
        readOnly = readOnly,
        modifier = modifier,
    )
}

@Composable
private fun SoraCodeEditor(
    label: String,
    state: MihomoCodeEditorState,
    language: MihomoCodeLanguage,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCodeEditorColors()
    val colorScheme = remember(colors) { colors.toSoraColorScheme() }
    val borderWidth by animateDpAsState(if (state.isFocused) FocusedBorderWidth else 0.dp)
    val borderColor by animateColorAsState(
        if (state.isFocused) colors.accent else colors.border,
    )

    Surface(
        modifier = modifier,
        shape = AsteriskShapeTokens.SmallContainer,
        color = colors.background,
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    MihomoSoraEditor(context).also { editor ->
                        editor.configure(language, colors, colorScheme)
                        editor.bindState(state)
                    }
                },
                update = { editor ->
                    editor.bindState(state)
                    editor.updateLanguage(language)
                    if (editor.colorScheme !== colorScheme) {
                        editor.colorScheme = colorScheme
                    }
                    val behavior = MihomoCodeEditorBehavior(readOnly)
                    editor.isEnabled = behavior.enabled
                    editor.isFocusable = behavior.selectionEnabled
                    editor.isFocusableInTouchMode = behavior.selectionEnabled
                    editor.isEditable = behavior.editable
                },
                onRelease = { editor ->
                    editor.bindState(null)
                    editor.release()
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (state.isEmpty) {
                Text(
                    text = label,
                    color = colors.placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = PlaceholderStartPadding, top = PlaceholderTopPadding),
                )
            }
        }
    }
}

private class MihomoSoraEditor(context: Context) : CodeEditor(context) {
    private var boundState: MihomoCodeEditorState? = null
    private var language: MihomoCodeLanguage? = null

    init {
        subscribeAlways(ContentChangeEvent::class.java) { event ->
            boundState?.onContentChanged(this, event.action)
        }
        onFocusChangeListener = OnFocusChangeListener { _, focused ->
            boundState?.onFocusChanged(this, focused)
        }
    }

    fun bindState(state: MihomoCodeEditorState?) {
        if (boundState === state) return
        boundState?.detach(this)
        boundState = state
        state?.attach(this)
    }

    fun updateLanguage(language: MihomoCodeLanguage) {
        if (this.language == language) return
        this.language = language
        setEditorLanguage(MihomoSoraLanguage(language))
    }
}

private fun MihomoSoraEditor.configure(
    language: MihomoCodeLanguage,
    colors: CodeEditorColors,
    colorScheme: EditorColorScheme,
) {
    setTextSize(EditorTextSize)
    setTypefaceText(Typeface.MONOSPACE)
    setTypefaceLineNumber(Typeface.MONOSPACE)
    setLineSpacing(dp(EditorLineSpacing).toFloat(), 1f)
    setTabWidth(EditorTabWidth)
    setWordwrap(false)
    setPinLineNumber(true)
    setLineNumberEnabled(true)
    setDisplayLnPanel(false)
    setLineNumberMarginLeft(dp(LineNumberMargin).toFloat())
    setDividerWidth(dp(DividerWidth).toFloat())
    setHighlightCurrentBlock(false)
    setBlockLineEnabled(false)
    setRenderFunctionCharacters(false)
    setLigatureEnabled(false)
    setScalable(true)
    setCursorAnimationEnabled(true)
    setInputType(
        InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
    )
    setPadding(0, dp(EditorVerticalPadding), dp(EditorHorizontalPadding), dp(EditorVerticalPadding))
    setEdgeEffectColor(colors.accent.toArgb())
    this.colorScheme = colorScheme
    updateLanguage(language)
}

private fun CodeEditor.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

private class MihomoSoraLanguage(
    language: MihomoCodeLanguage,
) : EmptyLanguage() {
    private val analyzer = MihomoIncrementalAnalyzeManager(language)

    override fun getAnalyzeManager(): AnalyzeManager = analyzer
}

private class MihomoIncrementalAnalyzeManager(
    private val language: MihomoCodeLanguage,
) : AsyncIncrementalAnalyzeManager<CodeLexState, CodeToken>(true) {
    override fun getInitialState(): CodeLexState = CodeLexState.Normal

    override fun stateEquals(state: CodeLexState, another: CodeLexState): Boolean = state == another

    override fun tokenizeLine(
        line: CharSequence,
        state: CodeLexState,
        lineIndex: Int,
    ): LineTokenizeResult<CodeLexState, CodeToken> {
        val result = tokenizeCodeLine(line, language, state)
        return LineTokenizeResult(result.state, result.tokens)
    }

    override fun generateSpansForLine(
        tokens: LineTokenizeResult<CodeLexState, CodeToken>,
    ): List<Span> {
        val lineTokens = tokens.tokens.orEmpty()
        if (lineTokens.isEmpty()) {
            return listOf(SpanFactory.obtainNoExt(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
        }
        return lineTokens.map { token ->
            SpanFactory.obtainNoExt(token.start, TextStyle.makeStyle(token.kind.soraColorId()))
        }
    }

    override fun computeBlocks(
        text: Content,
        delegate: CodeBlockAnalyzeDelegate,
    ): List<CodeBlock> = emptyList()
}

private fun CodeTokenKind.soraColorId(): Int = when (this) {
    CodeTokenKind.Normal -> EditorColorScheme.TEXT_NORMAL
    CodeTokenKind.Keyword -> EditorColorScheme.KEYWORD
    CodeTokenKind.Key -> EditorColorScheme.ATTRIBUTE_NAME
    CodeTokenKind.String -> EditorColorScheme.LITERAL
    CodeTokenKind.Number -> SyntaxNumberColorId
    CodeTokenKind.Literal -> SyntaxLiteralColorId
    CodeTokenKind.Function -> EditorColorScheme.FUNCTION_NAME
    CodeTokenKind.Comment -> EditorColorScheme.COMMENT
    CodeTokenKind.Operator -> EditorColorScheme.OPERATOR
}

@Composable
private fun rememberCodeEditorColors(): CodeEditorColors {
    val colorScheme = MaterialTheme.colorScheme
    val darkTheme = isInDarkTheme()
    return remember(colorScheme, darkTheme) {
        CodeEditorColors(
            darkTheme = darkTheme,
            accent = colorScheme.primary,
            foreground = colorScheme.onSurface,
            background = colorScheme.surfaceContainerHigh,
            gutter = colorScheme.surfaceContainerHighest,
            separator = colorScheme.outlineVariant.copy(alpha = 0.72f),
            border = colorScheme.outlineVariant.copy(alpha = 0.48f),
            lineNumber = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            placeholder = colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            selection = colorScheme.primary.copy(alpha = if (darkTheme) 0.34f else 0.24f),
            currentLine = colorScheme.primary.copy(alpha = if (darkTheme) 0.10f else 0.06f),
            keyword = colorScheme.primary,
            key = colorScheme.primary,
            string = colorScheme.tertiary,
            number = colorScheme.secondary,
            literal = colorScheme.tertiary,
            function = colorScheme.secondary,
            comment = colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
            operator = colorScheme.onSurfaceVariant,
            actionBackground = colorScheme.inverseSurface,
            actionForeground = colorScheme.inverseOnSurface,
        )
    }
}

private data class CodeEditorColors(
    val darkTheme: Boolean,
    val accent: Color,
    val foreground: Color,
    val background: Color,
    val gutter: Color,
    val separator: Color,
    val border: Color,
    val lineNumber: Color,
    val placeholder: Color,
    val selection: Color,
    val currentLine: Color,
    val keyword: Color,
    val key: Color,
    val string: Color,
    val number: Color,
    val literal: Color,
    val function: Color,
    val comment: Color,
    val operator: Color,
    val actionBackground: Color,
    val actionForeground: Color,
)

private fun CodeEditorColors.toSoraColorScheme(): EditorColorScheme {
    return object : EditorColorScheme(darkTheme) {}.apply {
        setColor(EditorColorScheme.WHOLE_BACKGROUND, background.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, gutter.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER, lineNumber.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, accent.toArgb())
        setColor(EditorColorScheme.LINE_DIVIDER, separator.toArgb())
        setColor(EditorColorScheme.TEXT_NORMAL, foreground.toArgb())
        setColor(EditorColorScheme.SELECTION_INSERT, accent.toArgb())
        setColor(EditorColorScheme.SELECTION_HANDLE, accent.toArgb())
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selection.toArgb())
        setColor(EditorColorScheme.CURRENT_LINE, currentLine.toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_TRACK, border.toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB, lineNumber.toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, accent.toArgb())
        setColor(EditorColorScheme.KEYWORD, keyword.toArgb())
        setColor(EditorColorScheme.ATTRIBUTE_NAME, key.toArgb())
        setColor(EditorColorScheme.LITERAL, string.toArgb())
        setColor(SyntaxNumberColorId, number.toArgb())
        setColor(SyntaxLiteralColorId, literal.toArgb())
        setColor(EditorColorScheme.FUNCTION_NAME, function.toArgb())
        setColor(EditorColorScheme.COMMENT, comment.toArgb())
        setColor(EditorColorScheme.OPERATOR, operator.toArgb())
        setColor(EditorColorScheme.IDENTIFIER_NAME, foreground.toArgb())
        setColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, actionBackground.toArgb())
        setColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, actionForeground.toArgb())
    }
}

private const val SyntaxNumberColorId = 256
private const val SyntaxLiteralColorId = 257
private const val EditorTextSize = 14f
private const val EditorLineSpacing = 4f
private const val EditorVerticalPadding = 8f
private const val EditorHorizontalPadding = 10f
private const val EditorTabWidth = 2
private const val LineNumberMargin = 4f
private const val DividerWidth = 1f
private val FocusedBorderWidth = 2.dp
private val PlaceholderStartPadding = 50.dp
private val PlaceholderTopPadding = 9.dp
