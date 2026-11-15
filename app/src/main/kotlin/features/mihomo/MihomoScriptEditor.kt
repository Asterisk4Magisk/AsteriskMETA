// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.LocalAppServices
import app.R
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import ui.icons.AsteriskIcons as Icons
import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.ViewGroup
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
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
import ui.components.AsteriskCodeSearchState
import ui.isInDarkTheme
import ui.theme.AsteriskMotion
import ui.theme.AsteriskShapeTokens

@Stable
internal class MihomoCodeEditorState(
    initialText: CharSequence = "",
    placeCursorAtEnd: Boolean = true,
) {
    private var editor: CodeEditor? = null
    private var retainedText = initialText
    private var moveCursorToEnd = placeCursorAtEnd

    var documentVersion by mutableIntStateOf(0)
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
        isFocused = false
        this.editor = null
    }

    internal fun onContentChanged(editor: CodeEditor, action: Int) {
        if (this.editor !== editor || action == ContentChangeEvent.ACTION_SET_NEW_TEXT) return
        documentVersion += 1
    }

    internal fun onFocusChanged(editor: CodeEditor, focused: Boolean) {
        if (this.editor === editor) {
            isFocused = focused
        }
    }

    fun snapshotText(): String = editor?.text?.toString() ?: retainedText.toString()

    fun replaceText(text: String, placeCursorAtEnd: Boolean = true) {
        if (text == retainedText && editor?.text?.toString() == text) return
        retainedText = text
        moveCursorToEnd = placeCursorAtEnd
        editor?.let(::applyRetainedText)
        documentVersion += 1
    }

    fun replaceFormattedText(text: String) {
        val attached = editor ?: return replaceText(text)
        if (text == snapshotText()) return
        val line = attached.cursor.leftLine
        val column = attached.cursor.leftColumn
        val content = attached.text
        content.beginBatchEdit()
        try {
            content.replace(0, content.length, text)
        } finally {
            content.endBatchEdit()
        }
        retainedText = text
        val restoredLine = line.coerceAtMost(content.lineCount - 1)
        attached.setSelection(restoredLine, column.coerceAtMost(content.getColumnCount(restoredLine)), false)
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
    state: MihomoCodeEditorState,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    searchState: AsteriskCodeSearchState? = null,
    showFormatButton: Boolean = true,
) {
    SoraCodeEditor(
        state = state,
        language = MihomoCodeLanguage.JavaScript,
        searchState = searchState,
        readOnly = readOnly,
        showFormatButton = showFormatButton,
        modifier = modifier,
    )
}

@Composable
internal fun YamlCodeEditor(
    state: MihomoCodeEditorState,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    searchState: AsteriskCodeSearchState? = null,
    showFormatButton: Boolean = true,
) {
    SoraCodeEditor(
        state = state,
        language = MihomoCodeLanguage.Yaml,
        showFormatButton = showFormatButton,
        searchState = searchState,
        readOnly = readOnly,
        modifier = modifier,
    )
}

@Composable
private fun SoraCodeEditor(
    state: MihomoCodeEditorState,
    language: MihomoCodeLanguage,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
    searchState: AsteriskCodeSearchState? = null,
    showFormatButton: Boolean = true,
) {
    val colors = rememberCodeEditorColors()
    val colorScheme = remember(colors) { colors.toSoraColorScheme() }
    val borderWidth by animateDpAsState(
        targetValue = if (state.isFocused) FocusedBorderWidth else 0.dp,
        animationSpec = AsteriskMotion.fastSpatial(),
        label = "script-editor-border-width",
    )
    val borderColor by animateColorAsState(
        targetValue = if (state.isFocused) colors.accent else colors.border,
        animationSpec = AsteriskMotion.effects(),
        label = "script-editor-border-color",
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
                        if (searchState != null) {
                            // Sora ignores extra scroll space for WRAP_CONTENT; the reader fills this viewport.
                            editor.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                        editor.configure(language, colors, colorScheme)
                        editor.bindState(state)
                        editor.bindSearchState(searchState)
                    }
                },
                update = { editor ->
                    editor.bindState(state)
                    editor.bindSearchState(searchState)
                    editor.updateLanguage(language)
                    if (editor.colorScheme !== colorScheme) {
                        editor.colorScheme = colorScheme
                    }
                    val behavior = MihomoCodeEditorBehavior(readOnly)
                    editor.isEnabled = behavior.enabled
                    editor.isFocusable = behavior.selectionEnabled
                    editor.isFocusableInTouchMode = behavior.selectionEnabled
                    // Sora hides the IME whenever setEditable(false) is called, including unchanged updates.
                    if (editor.isEditable != behavior.editable) editor.isEditable = behavior.editable
                    searchState?.updateEditable(editor)
                },
                onRelease = { editor ->
                    editor.bindSearchState(null)
                    editor.bindState(null)
                    editor.release()
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (!readOnly && showFormatButton) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    shape = AsteriskShapeTokens.InnerContainer,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 3.dp,
                ) {
                    MihomoCodeFormatButton(state, language, readOnly)
                }
            }
        }
    }
}

@Composable
internal fun MihomoCodeFormatButton(
    state: MihomoCodeEditorState,
    language: MihomoCodeLanguage,
    readOnly: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val tipNotifier = LocalAppServices.current.tipNotifier
    val currentReadOnly by rememberUpdatedState(readOnly)
    var formatting by remember(state, language) { mutableStateOf(false) }
    val formatLabel = stringResource(
        when (language) {
            MihomoCodeLanguage.JavaScript -> R.string.code_editor_format_javascript
            MihomoCodeLanguage.Yaml -> R.string.code_editor_format_yaml
        },
    )
    val formatFailed = stringResource(R.string.code_editor_format_failed)
    val contentChanged = stringResource(R.string.code_editor_format_content_changed)
    IconButton(
        enabled = !formatting,
        onClick = {
            val source = state.snapshotText()
            formatting = true
            scope.launch {
                try {
                    val formatted = formatMihomoCode(context, source, language)
                    if (!currentReadOnly) {
                        if (state.snapshotText() == source) {
                            state.replaceFormattedText(formatted)
                        } else {
                            tipNotifier.show(contentChanged)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    tipNotifier.show(formatFailed)
                } finally {
                    formatting = false
                }
            }
        },
    ) {
        if (formatting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                Icons.Rounded.AutoFixHigh,
                contentDescription = formatLabel,
            )
        }
    }
}

private class MihomoSoraEditor(context: Context) : CodeEditor(context) {
    private var boundState: MihomoCodeEditorState? = null
    private var boundSearchState: AsteriskCodeSearchState? = null
    private var language: MihomoCodeLanguage? = null

    init {
        subscribeAlways(ContentChangeEvent::class.java) { event ->
            boundState?.onContentChanged(this, event.action)
            boundSearchState?.onContentChanged(this)
        }
        subscribeAlways(PublishSearchResultEvent::class.java) {
            boundSearchState?.onResultsPublished(this)
        }
        subscribeAlways(SelectionChangeEvent::class.java) {
            boundSearchState?.onSelectionChanged(this)
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

    fun bindSearchState(state: AsteriskCodeSearchState?) {
        if (boundSearchState === state) return
        boundSearchState?.detach(this)
        boundSearchState = state
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
    isWordwrap = false
    setPinLineNumber(true)
    setLineNumberEnabled(true)
    setDisplayLnPanel(false)
    setLineNumberMarginLeft(dp(LineNumberMargin).toFloat())
    setDividerWidth(dp(DividerWidth).toFloat())
    setHighlightCurrentBlock(false)
    setBlockLineEnabled(false)
    isRenderFunctionCharacters = false
    setLigatureEnabled(false)
    isScalable = true
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
            actionBackground = colorScheme.surfaceContainerHigh,
            actionForeground = colorScheme.onSurface,
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
        setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, selection.toArgb())
        setColor(EditorColorScheme.MATCHED_TEXT_BORDER, accent.toArgb())
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
