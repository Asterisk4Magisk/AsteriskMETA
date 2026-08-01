// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ui.theme.AsteriskShapeTokens

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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
    val currentShow by rememberUpdatedState(show)
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

    LaunchedEffect(show) {
        if (show) {
            renderSheet = true
            if (sheetState.isVisible) sheetState.show()
        } else if (renderSheet) {
            sheetState.hide()
            if (!sheetState.isVisible) renderSheet = false
        }
    }

    if (!renderSheet) return
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = dismissEnabled,
        shape = AsteriskShapeTokens.Sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = {
            WindowInsets.safeDrawing.union(WindowInsets.ime)
        },
    ) {
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
            if (title != null || startAction !== EmptySheetAction || endAction !== EmptySheetAction) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        startAction()
                    }
                    Box(modifier = Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
                        title?.let { sheetTitle ->
                            Text(
                                text = sheetTitle,
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        endAction()
                    }
                }
            }
            content()
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
    private var gestureActive = false
    private var contentConsumed = false

    fun startGesture() {
        gestureActive = true
        contentConsumed = false
    }

    fun ensureGestureStarted() {
        if (gestureActive) return
        startGesture()
    }

    fun recordContentConsumption(deltaY: Float) {
        if (gestureActive && deltaY != 0f) {
            contentConsumed = true
        }
    }

    fun shouldConsumeDownwardRemainder(remainderY: Float): Boolean =
        gestureActive && contentConsumed && remainderY > 0f

    fun endGesture() {
        gestureActive = false
        contentConsumed = false
    }
}

private class SheetContentNestedScrollConnection : NestedScrollConnection {
    private val handoffGuard = SheetGestureHandoffGuard()

    fun startGesture() {
        handoffGuard.startGesture()
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) {
            handoffGuard.ensureGestureStarted()
        }
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput) {
            handoffGuard.ensureGestureStarted()
            handoffGuard.recordContentConsumption(consumed.y)
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
