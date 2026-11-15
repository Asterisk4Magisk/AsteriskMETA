// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.LocalAppServices
import app.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.theme.AsteriskMotion
import ui.theme.AsteriskShapeTokens
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import ui.icons.AsteriskIcons as Icons

/** Shares a draggable text-find panel with a Sora editor and optional floating editor tools. */
@Composable
internal fun AsteriskCodeSearchHost(
    state: AsteriskCodeSearchState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showSearchButton: Boolean = enabled,
    onSearchClosed: (() -> Unit)? = null,
    editorTools: (@Composable RowScope.(searchButton: @Composable () -> Unit) -> Unit)? = null,
    content: @Composable (searchButton: @Composable () -> Unit, editorModifier: Modifier, backdrop: HazeState) -> Unit,
) {
    val backdrop = rememberHazeState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var editorBounds by remember { mutableStateOf(Rect.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var wasActive by remember { mutableStateOf(state.active) }
    val currentOnSearchClosed by rememberUpdatedState(onSearchClosed)
    LaunchedEffect(state.active) {
        if (wasActive && !state.active) {
            currentOnSearchClosed?.invoke() ?: run {
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }
        wasActive = state.active
    }
    LaunchedEffect(enabled) { if (!enabled) state.close() }
    LaunchedEffect(state.active, state.query, state.matchCase, state.useRegex, enabled) {
        state.clearResults()
        state.pending = state.active && state.query.isNotEmpty() && enabled
        if (!state.pending) return@LaunchedEffect
        delay(SearchDebounceMillis.milliseconds)
        state.search(state.query, state.matchCase, state.useRegex)
        state.pending = false
    }
    if (state.active) {
        val backState = rememberNavigationEventState(NavigationEventInfo.None)
        NavigationBackHandler(state = backState, isBackEnabled = true, onBackCompleted = state::close)
    }
    val searchButton: @Composable () -> Unit = {
        if (showSearchButton) {
            AsteriskCodeActionButton(
                icon = Icons.Rounded.Search,
                label = stringResource(if (state.active) R.string.code_editor_search_close else R.string.common_search),
                onClick = { if (state.active) state.close() else state.open() },
                enabled = enabled,
                modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow() },
            )
        }
    }
    Box(modifier = modifier.onGloballyPositioned { origin = it.positionInWindow() }) {
        content(
            searchButton,
            Modifier.onGloballyPositioned { editorBounds = it.boundsInWindow() },
            backdrop,
        )
        CompositionLocalProvider(LocalChromeBackdrop provides backdrop) {
            if (editorTools != null && enabled) {
                CodeEditorToolsOverlay(editorBounds, origin) { editorTools(searchButton) }
            }
            CodeSearchOverlay(state.active, anchor) { dragHandle ->
                CodeSearchPanel(
                    searchActive = state.active,
                    matchCase = state.matchCase,
                    onToggleMatchCase = { if (state.active) state.matchCase = !state.matchCase },
                    useRegex = state.useRegex,
                    onToggleRegex = { if (state.active) state.useRegex = !state.useRegex },
                    searchState = state,
                    searchPending = state.pending,
                    onSearch = { keyboard?.hide() },
                    onClose = state::close,
                    dragHandle = dragHandle,
                )
            }
        }
    }
}

@Composable
private fun CodeEditorToolsOverlay(
    editorBounds: Rect,
    origin: Offset,
    content: @Composable RowScope.() -> Unit,
) {
    if (editorBounds.isEmpty) return
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .absoluteOffset {
                IntOffset((editorBounds.left - origin.x).roundToInt(), (editorBounds.top - origin.y).roundToInt())
            }
            .size(with(density) { editorBounds.width.toDp() }, with(density) { editorBounds.height.toDp() }),
    ) {
        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
            Box(Modifier.matchParentSize().floatingChrome(AsteriskShapeTokens.SmallContainer))
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}

@Composable
private fun CodeSearchOverlay(
    searchActive: Boolean,
    anchor: Rect,
    content: @Composable (dragHandle: @Composable () -> Unit) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)),
    ) {
        val density = LocalDensity.current
        var origin by remember { mutableStateOf(Offset.Zero) }
        var panelSize by remember { mutableStateOf(IntSize.Zero) }
        var draggedPosition by remember { mutableStateOf<Offset?>(null) }
        val panelWidth = (maxWidth - 32.dp).coerceIn(0.dp, 340.dp)
        val width = with(density) { panelWidth.toPx() }
        val gutter = with(density) { 16.dp.toPx() }
        val gap = with(density) { 8.dp.toPx() }
        val maxX = (constraints.maxWidth - width - gutter).coerceAtLeast(gutter)
        val maxY = (constraints.maxHeight - panelSize.height - gutter).coerceAtLeast(gutter)
        val below = anchor.bottom - origin.y + gap
        val above = anchor.top - origin.y - gap - panelSize.height
        val desired = draggedPosition ?: Offset(
            anchor.right - origin.x - width,
            if (below <= maxY) below else above,
        )
        val position = Offset(desired.x.coerceIn(gutter, maxX), desired.y.coerceIn(gutter, maxY))

        Box(
            modifier = Modifier.matchParentSize().onGloballyPositioned { origin = it.positionInWindow() },
        )
        AnimatedVisibility(
            visible = searchActive,
            modifier = Modifier
                .absoluteOffset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .width(panelWidth),
            enter = fadeIn(AsteriskMotion.fastEffects()) +
                slideInVertically(AsteriskMotion.fastSpatial()) { -it / 8 },
            exit = fadeOut(AsteriskMotion.fastEffects()) +
                slideOutVertically(AsteriskMotion.fastSpatial()) { -it / 8 },
        ) {
            DisposableEffect(Unit) {
                onDispose {
                    draggedPosition = null
                    panelSize = IntSize.Zero
                }
            }
            Box(modifier = Modifier.onSizeChanged { panelSize = it }) {
                content {
                    CodeSearchDragHandle(
                        onDragStarted = {
                            draggedPosition = position
                        },
                        onDrag = { delta ->
                            val next = (draggedPosition ?: position) + delta
                            draggedPosition = Offset(
                                next.x.coerceIn(gutter, maxX),
                                next.y.coerceIn(gutter, maxY),
                            )
                        },
                        onReset = { draggedPosition = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeSearchDragHandle(
    onDragStarted: () -> Unit,
    onDrag: (Offset) -> Unit,
    onReset: () -> Unit,
) {
    val currentOnDragStarted by rememberUpdatedState(onDragStarted)
    val currentOnDrag by rememberUpdatedState(onDrag)
    IconButton(
        onClick = onReset,
        modifier = Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { currentOnDragStarted() },
            ) { change, delta ->
                change.consume()
                currentOnDrag(delta)
            }
        },
    ) {
        Icon(
            imageVector = Icons.Rounded.DragIndicator,
            contentDescription = stringResource(R.string.code_editor_search_move),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CodeSearchPanel(
    searchActive: Boolean,
    matchCase: Boolean,
    onToggleMatchCase: () -> Unit,
    useRegex: Boolean,
    onToggleRegex: () -> Unit,
    searchState: AsteriskCodeSearchState,
    searchPending: Boolean,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    Box(modifier = Modifier.pointerInput(Unit) {}) {
        val focusRequester = remember { FocusRequester() }
        val scope = rememberCoroutineScope()
        val tipNotifier = LocalAppServices.current.tipNotifier
        val contentChanged = stringResource(R.string.code_editor_replace_changed)
        LaunchedEffect(searchActive) {
            if (searchActive) focusRequester.requestFocus()
        }
        Box(Modifier.matchParentSize().floatingChrome(AsteriskShapeTokens.SmallContainer))
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                CodeSearchField(
                    state = searchState.queryInput,
                    focusRequester = focusRequester,
                    onSearch = onSearch,
                    onClose = onClose,
                    dragHandle = dragHandle,
                )
                AnimatedVisibility(
                    visible = searchState.editable && searchState.replacementExpanded,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                ) {
                    CodeReplaceControls(
                        state = searchState,
                        onReplaceAll = {
                            scope.launch {
                                if (!searchState.replaceAll() && searchState.active) tipNotifier.show(contentChanged)
                            }
                        },
                    )
                }
                CodeSearchControls(
                    searchState = searchState,
                    hasQuery = searchState.query.isNotEmpty(),
                    searchPending = searchPending,
                    matchCase = matchCase,
                    onToggleMatchCase = onToggleMatchCase,
                    useRegex = useRegex,
                    onToggleRegex = onToggleRegex,
                    onPrevious = searchState::previousMatch,
                    onNext = searchState::nextMatch,
                    onToggleReplacement = {
                        searchState.replacementExpanded = !searchState.replacementExpanded
                        if (!searchState.replacementExpanded) focusRequester.requestFocus()
                    },
                )
            }
        }
    }
}

@Composable
private fun CodeSearchControls(
    searchState: AsteriskCodeSearchState,
    hasQuery: Boolean,
    searchPending: Boolean,
    matchCase: Boolean,
    onToggleMatchCase: () -> Unit,
    useRegex: Boolean,
    onToggleRegex: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleReplacement: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val counter = when {
        !hasQuery || searchPending || searchState.searching -> ""
        searchState.patternInvalid -> stringResource(R.string.code_editor_search_invalid)
        searchState.matchCount == 0 -> stringResource(R.string.code_editor_search_empty)
        else -> stringResource(
            R.string.code_editor_search_count,
            searchState.currentMatchIndex + 1,
            searchState.matchCount,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchState.editable) {
            IconToggleButton(
                checked = searchState.replacementExpanded,
                onCheckedChange = { onToggleReplacement() },
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = stringResource(
                        if (searchState.replacementExpanded) R.string.code_editor_replace_hide else R.string.code_editor_replace_show,
                    ),
                    tint = if (searchState.replacementExpanded) colorScheme.primary else colorScheme.onSurfaceVariant,
                )
            }
        }
        CodeSearchToggle(
            text = SearchMatchCaseLabel,
            active = matchCase,
            description = stringResource(R.string.code_editor_search_case),
            onClick = onToggleMatchCase,
        )
        CodeSearchToggle(
            text = SearchRegexLabel,
            active = useRegex,
            description = stringResource(R.string.code_editor_search_regex),
            onClick = onToggleRegex,
        )
        Text(
            text = counter,
            style = MaterialTheme.typography.labelLarge,
            color = if (searchState.patternInvalid) colorScheme.error else colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        IconButton(onClick = onPrevious, enabled = searchState.matchCount > 0 && !searchState.searching) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.code_editor_search_previous),
            )
        }
        IconButton(onClick = onNext, enabled = searchState.matchCount > 0 && !searchState.searching) {
            Icon(
                imageVector = Icons.Rounded.ArrowDownward,
                contentDescription = stringResource(R.string.code_editor_search_next),
            )
        }
    }
}

@Composable
private fun CodeReplaceControls(
    state: AsteriskCodeSearchState,
    onReplaceAll: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            state = state.replacementInput,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).focusRequester(focusRequester),
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            onKeyboardAction = KeyboardActionHandler { state.replaceCurrent() },
            decorator = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (state.replacement.isEmpty()) {
                        Text(
                            text = stringResource(R.string.code_editor_replace_hint),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        AsteriskCodeActionButton(
            icon = Icons.Rounded.EditNote,
            label = stringResource(R.string.code_editor_replace),
            onClick = state::replaceCurrent,
            enabled = state.canReplace,
        )
        if (state.replacing) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        } else {
            AsteriskCodeActionButton(
                icon = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                label = stringResource(R.string.code_editor_replace_all),
                onClick = onReplaceAll,
                enabled = state.canReplace,
            )
        }
    }
}

@Composable
private fun CodeSearchField(
    state: TextFieldState,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dragHandle()
        BasicTextField(
            state = state,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).focusRequester(focusRequester),
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
            cursorBrush = SolidColor(colorScheme.primary),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            onKeyboardAction = KeyboardActionHandler { onSearch() },
            decorator = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.code_editor_search_hint),
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (state.text.isNotEmpty()) {
                IconButton(onClick = { state.setTextAndPlaceCursorAtEnd("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.common_clear),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.code_editor_search_close),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CodeSearchToggle(
    text: String,
    active: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    IconToggleButton(
        checked = active,
        onCheckedChange = { onClick() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = colorScheme.primaryContainer,
            checkedContentColor = colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private const val SearchDebounceMillis = 120L
private const val SearchMatchCaseLabel = "Aa"
private const val SearchRegexLabel = ".*"
