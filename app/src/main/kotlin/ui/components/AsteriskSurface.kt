// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ui.layout.LocalAdaptiveWindow
import ui.layout.avoidFold
import ui.theme.AsteriskMotion
import ui.theme.AsteriskShapeTokens
import kotlin.math.abs
import kotlinx.coroutines.flow.first

@Composable
internal fun AsteriskPageCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(
        width = if (selected) 2.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
    )
    AsteriskExpressiveCard(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = border,
        content = content,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AsteriskModalBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissEnabled: Boolean = true,
    title: String? = null,
    startAction: @Composable () -> Unit = EmptySheetAction,
    endAction: @Composable () -> Unit = EmptySheetAction,
    content: @Composable ColumnScope.() -> Unit,
) {
    val window = LocalAdaptiveWindow.current
    val folded = window.hasSeparatingFold
    val foldedVisibility = remember(folded) { MutableTransitionState(false) }
    foldedVisibility.targetState = folded && show
    val currentShow by rememberUpdatedState(show)
    val currentContent by rememberUpdatedState(content)
    val sheetContent = remember {
        movableContentOf { Column(content = currentContent) }
    }
    val currentDismissEnabled by rememberUpdatedState(dismissEnabled)
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(
            SheetValue.Hidden,
            SheetValue.Expanded,
        ),
        confirmValueChange = { targetValue ->
            shouldAllowSheetStateChange(
                targetValue = targetValue,
                show = currentShow,
                dismissEnabled = currentDismissEnabled,
            )
        },
    )
    var renderSheet by remember { mutableStateOf(show) }
    val contentScrollConnection = remember {
        SheetContentNestedScrollConnection()
    }

    LaunchedEffect(show, folded) {
        if (show) {
            renderSheet = true
            if (!folded && sheetState.isVisible) sheetState.show()
        } else if (renderSheet) {
            if (folded) {
                snapshotFlow { foldedVisibility.isIdle && !foldedVisibility.currentState }.first { it }
            } else if (sheetState.isVisible) {
                sheetState.hide()
            }
            if (folded || !sheetState.isVisible) renderSheet = false
        }
    }

    if (!renderSheet) return
    val motionScheme = AsteriskMotion.sheetMotionScheme()
    if (folded) {
        if (!show && foldedVisibility.isIdle && !foldedVisibility.currentState) return
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = dismissEnabled,
                dismissOnClickOutside = false,
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().avoidFold(window).safeDrawingPadding().clipToBounds(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                AnimatedVisibility(
                    visibleState = foldedVisibility,
                    enter = slideInVertically(motionScheme.defaultSpatialSpec()) { height -> height } +
                        fadeIn(motionScheme.defaultEffectsSpec()),
                    exit = slideOutVertically(motionScheme.fastEffectsSpec()) { height -> height } +
                        fadeOut(motionScheme.fastEffectsSpec()),
                    label = "foldedBottomSheet",
                ) {
                    Surface(
                        modifier = modifier.widthIn(max = 640.dp).fillMaxWidth(),
                        shape = AsteriskShapeTokens.Sheet,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column {
                            AsteriskSheetHeader(title, startAction, endAction, compact = true)
                            Box(Modifier.weight(1f, fill = false)) { sheetContent() }
                        }
                    }
                }
            }
        }
        return
    }
    val contentMotionScheme = MaterialTheme.motionScheme
    MaterialTheme(motionScheme = motionScheme) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            // Keep the top inset independent of the sheet offset to avoid changing drag anchors.
            modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
            sheetState = sheetState,
            sheetGesturesEnabled = dismissEnabled,
            shape = AsteriskShapeTokens.Sheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            contentWindowInsets = {
                WindowInsets.safeDrawing
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .union(WindowInsets.ime)
            },
        ) {
            MaterialTheme(motionScheme = contentMotionScheme) {
                Column(
                    modifier = Modifier
                        .nestedScroll(contentScrollConnection)
                        .pointerInput(contentScrollConnection) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                contentScrollConnection.startGesture()
                                do {
                                    val event = awaitPointerEvent()
                                } while (event.changes.any { change -> change.pressed })
                            }
                        },
                ) {
                    AsteriskSheetHeader(title, startAction, endAction)
                    sheetContent()
                }
            }
        }
    }
}

@Composable
internal fun AsteriskSheetHeader(
    title: String?,
    startAction: @Composable () -> Unit,
    endAction: @Composable () -> Unit,
    compact: Boolean = false,
) {
    if (title == null && startAction === EmptySheetAction && endAction === EmptySheetAction) return
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = if (compact) Modifier else Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            startAction()
        }
        Box(
            modifier = Modifier.weight(1.4f).padding(horizontal = if (compact) 4.dp else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = if (compact) Modifier else Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            endAction()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun shouldAllowSheetStateChange(
    targetValue: SheetValue,
    show: Boolean,
    dismissEnabled: Boolean,
): Boolean =
    targetValue != SheetValue.Hidden || !show || dismissEnabled

internal class SheetGestureHandoffGuard {
    private var hasScrollDecision = false
    private var canDragSheet = false

    fun startGesture() {
        hasScrollDecision = false
        canDragSheet = false
    }

    fun recordScroll(consumedY: Float, availableY: Float) {
        if (hasScrollDecision) return
        // Scroll startup or an ancestor consuming the delta can leave no vertical movement.
        // Wait for movement before deciding who owns this gesture.
        if (consumedY == 0f && availableY == 0f) return
        hasScrollDecision = true
        // decide once, allowing subpixel consumption at the content boundary.
        canDragSheet = availableY > 0f && abs(consumedY) < 0.5f
    }

    fun shouldConsumeDownwardRemainder(remainderY: Float): Boolean =
        hasScrollDecision && !canDragSheet && remainderY > 0f

    fun endGesture() {
        startGesture()
    }
}

private class SheetContentNestedScrollConnection : NestedScrollConnection {
    private val handoffGuard = SheetGestureHandoffGuard()

    fun startGesture() {
        handoffGuard.startGesture()
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput) {
            handoffGuard.recordScroll(consumed.y, available.y)
        }
        return if (handoffGuard.shouldConsumeDownwardRemainder(available.y)) {
            Offset(x = 0f, y = available.y)
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val consumeRemainder = handoffGuard.shouldConsumeDownwardRemainder(available.y)
        handoffGuard.endGesture()
        return if (consumeRemainder) {
            Velocity(x = 0f, y = available.y)
        } else {
            Velocity.Zero
        }
    }
}

private val EmptySheetAction: @Composable () -> Unit = {}
