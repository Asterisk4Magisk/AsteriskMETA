// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.PatternSyntaxException

/** Shared text-find state for read-only previews and editable Sora documents. */
@Stable
internal class AsteriskCodeSearchState(
    active: Boolean = false,
    query: String = "",
    matchCase: Boolean = false,
    useRegex: Boolean = false,
    replacementExpanded: Boolean = false,
    replacement: String = "",
) {
    var active by mutableStateOf(active)
        private set
    val queryInput = TextFieldState(query)
    val query: String get() = queryInput.text.toString()
    var matchCase by mutableStateOf(matchCase)
    var useRegex by mutableStateOf(useRegex)
    var replacementExpanded by mutableStateOf(replacementExpanded)
    val replacementInput = TextFieldState(replacement)
    val replacement: String get() = replacementInput.text.toString()
    var editable by mutableStateOf(false)
        private set
    var replacing by mutableStateOf(false)
        private set
    var pending by mutableStateOf(false)
        internal set
    private var editor: CodeEditor? = null
    private var request: SearchRequest? = null
    private var selectFirstMatch = false
    private var selectNextMatch = false
    private var contentVersion = 0

    var searching by mutableStateOf(false)
        private set
    var matchCount by mutableIntStateOf(0)
        private set
    var currentMatchIndex by mutableIntStateOf(-1)
        private set
    var patternInvalid by mutableStateOf(false)
        private set

    val canReplace: Boolean
        get() = active && editable && !pending && !searching && !replacing && !patternInvalid &&
            matchCount > 0 && request == SearchRequest(query, matchCase, useRegex)

    fun open() {
        active = true
    }

    fun close() {
        active = false
        queryInput.setTextAndPlaceCursorAtEnd("")
        replacementInput.setTextAndPlaceCursorAtEnd("")
        replacementExpanded = false
        pending = false
        clearResults()
    }

    fun hasEditorFocus(): Boolean = editor?.isFocused == true

    fun clearEditorFocus() {
        editor?.clearFocus()
    }

    internal fun attach(editor: CodeEditor) {
        this.editor = editor
        updateEditable(editor)
        request?.let { applySearch(editor, it) }
    }

    internal fun detach(editor: CodeEditor) {
        if (this.editor !== editor) return
        this.editor = null
        editable = false
        editor.searcher.stopSearch()
    }

    internal fun updateEditable(editor: CodeEditor) {
        // isEditable also becomes false during Sora's asynchronous layout/formatting work.
        if (this.editor === editor) editable = editor.editable
    }

    fun search(query: String, matchCase: Boolean, useRegex: Boolean) {
        if (query.isEmpty()) return clearResults()
        val next = SearchRequest(query, matchCase, useRegex)
        request = next
        matchCount = 0
        currentMatchIndex = -1
        patternInvalid = false
        searching = true
        selectFirstMatch = true
        editor?.let { applySearch(it, next) }
    }

    internal fun clearResults() {
        request = null
        selectNextMatch = false
        searching = false
        matchCount = 0
        currentMatchIndex = -1
        patternInvalid = false
        editor?.searcher?.stopSearch()
    }

    fun nextMatch() {
        if (!searching && matchCount > 0) editor?.let { moveMatch(it, forward = true) }
    }

    fun previousMatch() {
        if (!searching && matchCount > 0) editor?.let { moveMatch(it, forward = false) }
    }

    fun replaceCurrent() {
        if (!canReplace) return
        val attached = editor ?: return
        if (!attached.isEditable) return
        if (attached.searcher.currentMatchedPositionIndex < 0 && !moveMatch(attached, forward = true)) return
        val start = attached.cursor.left
        val end = attached.cursor.right
        if (attached.text.substring(start, end) == replacement) {
            moveMatch(attached, forward = true)
            return
        }
        selectNextMatch = true
        preserveEditorFocus(attached) {
            attached.text.replace(start, end, replacement)
            val position = attached.text.indexer.getCharPosition(start + replacement.length)
            attached.setSelection(position.line, position.column, false)
        }
    }

    suspend fun replaceAll(): Boolean {
        if (!canReplace) return false
        val attached = editor ?: return false
        val originalRequest = request ?: return false
        val version = contentVersion
        val source = attached.text.toString()
        val replacement = replacement
        replacing = true
        try {
            val updated = withContext(Dispatchers.Default) {
                replaceCodeSearchMatches(source, originalRequest.query, originalRequest.matchCase, originalRequest.useRegex, replacement)
            }
            // A document may still be edited while the replacement is prepared.
            if (editor !== attached || contentVersion != version || request !== originalRequest || !attached.isEditable) return false
            // Input can change before the debounced search updates the request.
            if (originalRequest != SearchRequest(query, matchCase, useRegex)) return false
            if (updated != source) {
                val line = attached.cursor.leftLine
                val column = attached.cursor.leftColumn
                preserveEditorFocus(attached) {
                    attached.text.beginBatchEdit()
                    try {
                        attached.text.replace(0, attached.text.length, updated)
                    } finally {
                        attached.text.endBatchEdit()
                    }
                    val restoredLine = line.coerceAtMost(attached.text.lineCount - 1)
                    attached.setSelection(restoredLine, column.coerceAtMost(attached.text.getColumnCount(restoredLine)), false)
                }
            }
            return true
        } finally {
            replacing = false
        }
    }

    internal fun onResultsPublished(editor: CodeEditor) {
        if (this.editor !== editor || !editor.searcher.hasQuery()) return
        val publishedRequest = request ?: return
        // Sora clears its running-thread marker after dispatching the publication event.
        editor.post {
            if (this.editor !== editor || request !== publishedRequest || !editor.searcher.hasQuery()) return@post
            val searcher = editor.searcher
            searching = false
            matchCount = searcher.matchedPositionCount
            if (selectFirstMatch && matchCount > 0) {
                selectFirstMatch = false
                editor.setSelection(0, 0, false)
                moveMatch(editor, forward = true)
            } else if (selectNextMatch && matchCount > 0) {
                moveMatch(editor, forward = true)
            }
            selectNextMatch = false
            currentMatchIndex = searcher.currentMatchedPositionIndex
        }
    }

    internal fun onSelectionChanged(editor: CodeEditor) {
        if (this.editor === editor && !searching && editor.searcher.hasQuery()) {
            currentMatchIndex = editor.searcher.currentMatchedPositionIndex
        }
    }

    internal fun onContentChanged(editor: CodeEditor) {
        if (this.editor === editor) contentVersion += 1
        if (this.editor === editor && request != null && editor.searcher.hasQuery()) {
            searching = true
            currentMatchIndex = -1
        }
    }

    private fun applySearch(editor: CodeEditor, request: SearchRequest) {
        searching = true
        patternInvalid = false
        matchCount = 0
        currentMatchIndex = -1
        selectFirstMatch = true
        editor.searcher.stopSearch()
        try {
            editor.searcher.search(request.query, SearchOptions(!request.matchCase, request.useRegex))
        } catch (_: PatternSyntaxException) {
            searching = false
            patternInvalid = true
        }
    }

    private fun moveMatch(editor: CodeEditor, forward: Boolean): Boolean {
        val moved = preserveEditorFocus(editor) {
            if (forward) editor.searcher.gotoNext() else editor.searcher.gotoPrevious()
        }
        currentMatchIndex = editor.searcher.currentMatchedPositionIndex
        return moved
    }

    private inline fun <T> preserveEditorFocus(editor: CodeEditor, action: () -> T): T {
        // Sora requests focus when selecting a result; keep the find field's existing focus.
        val preserveFocus = !editor.hasFocus()
        val focusable = editor.isFocusable
        val focusableInTouchMode = editor.isFocusableInTouchMode
        if (preserveFocus) editor.isFocusable = false
        try {
            return action()
        } finally {
            if (preserveFocus) {
                editor.isFocusable = focusable
                editor.isFocusableInTouchMode = focusableInTouchMode
            }
        }
    }

    private data class SearchRequest(val query: String, val matchCase: Boolean, val useRegex: Boolean)

    companion object {
        val Saver = listSaver<AsteriskCodeSearchState, Any>(
            save = { listOf(it.active, it.query, it.matchCase, it.useRegex, it.replacementExpanded, it.replacement) },
            restore = { AsteriskCodeSearchState(it[0] as Boolean, it[1] as String, it[2] as Boolean, it[3] as Boolean, it[4] as Boolean, it[5] as String) },
        )
    }
}

@Composable
internal fun rememberAsteriskCodeSearchState(): AsteriskCodeSearchState =
    rememberSaveable(saver = AsteriskCodeSearchState.Saver) { AsteriskCodeSearchState() }
