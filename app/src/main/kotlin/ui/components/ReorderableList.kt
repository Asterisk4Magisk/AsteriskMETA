// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableLazyGridState
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

internal class AsteriskReorderableLazyGridState(
    val reorderableState: ReorderableLazyGridState,
    val hapticFeedback: HapticFeedback,
)

internal class AsteriskReorderableLazyListState(
    val reorderableState: ReorderableLazyListState,
    val hapticFeedback: HapticFeedback,
)

internal fun verticalReorderScrollThresholdPadding(
    contentPadding: PaddingValues,
    bottomPadding: Dp = contentPadding.calculateBottomPadding(),
): PaddingValues = PaddingValues(
    top = contentPadding.calculateTopPadding(),
    bottom = bottomPadding,
)

internal fun translateAsteriskReorderableLazyGridMove(
    fromIndex: Int,
    toIndex: Int,
    itemCount: Int,
    indexOffset: Int,
): Pair<Int, Int>? {
    val translatedFromIndex = fromIndex - indexOffset
    val translatedToIndex = toIndex - indexOffset
    if (
        translatedFromIndex == translatedToIndex ||
        translatedFromIndex !in 0 until itemCount ||
        translatedToIndex !in 0 until itemCount
    ) {
        return null
    }
    return translatedFromIndex to translatedToIndex
}

@Composable
internal fun rememberAsteriskReorderableLazyGridState(
    lazyGridState: LazyGridState,
    itemCount: Int,
    indexOffset: Int = 0,
    scrollThresholdPadding: PaddingValues = PaddingValues(),
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): AsteriskReorderableLazyGridState {
    val hapticFeedback = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyGridState(
        lazyGridState = lazyGridState,
        scrollThresholdPadding = scrollThresholdPadding,
    ) { from, to ->
        val (fromIndex, toIndex) = translateAsteriskReorderableLazyGridMove(
            fromIndex = from.index,
            toIndex = to.index,
            itemCount = itemCount,
            indexOffset = indexOffset,
        ) ?: return@rememberReorderableLazyGridState
        onMove(fromIndex, toIndex)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    return AsteriskReorderableLazyGridState(reorderableState, hapticFeedback)
}

@Composable
internal fun rememberAsteriskReorderableLazyListState(
    lazyListState: LazyListState,
    itemCount: Int,
    indexOffset: Int = 0,
    scrollThresholdPadding: PaddingValues = PaddingValues(),
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): AsteriskReorderableLazyListState {
    val hapticFeedback = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = scrollThresholdPadding,
    ) { from, to ->
        val (fromIndex, toIndex) = translateAsteriskReorderableLazyGridMove(
            fromIndex = from.index,
            toIndex = to.index,
            itemCount = itemCount,
            indexOffset = indexOffset,
        ) ?: return@rememberReorderableLazyListState
        onMove(fromIndex, toIndex)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    return AsteriskReorderableLazyListState(reorderableState, hapticFeedback)
}

@Composable
internal fun Modifier.longPressReorderDragHandle(
    scope: ReorderableCollectionItemScope,
    enabled: Boolean,
    state: AsteriskReorderableLazyGridState,
    onDragStarted: () -> Unit = {},
    onDragStopped: () -> Unit = {},
): Modifier = longPressReorderDragHandle(scope, enabled, state.hapticFeedback, onDragStarted, onDragStopped)

@Composable
internal fun Modifier.longPressReorderDragHandle(
    scope: ReorderableCollectionItemScope,
    enabled: Boolean,
    state: AsteriskReorderableLazyListState,
    onDragStarted: () -> Unit = {},
    onDragStopped: () -> Unit = {},
): Modifier = longPressReorderDragHandle(scope, enabled, state.hapticFeedback, onDragStarted, onDragStopped)

@Composable
private fun Modifier.longPressReorderDragHandle(
    scope: ReorderableCollectionItemScope,
    enabled: Boolean,
    hapticFeedback: HapticFeedback,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
): Modifier {
    val gesture = remember { ReorderGesture() }
    val currentStart by rememberUpdatedState(onDragStarted)
    val currentStop by rememberUpdatedState(onDragStopped)
    return with(scope) {
        this@longPressReorderDragHandle.consumeReorderRelease(gesture, enabled).longPressDraggableHandle(
            enabled = enabled,
            onDragStarted = {
                gesture.started = true
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                currentStart()
            },
            onDragStopped = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                currentStop()
            },
        )
    }
}

private class ReorderGesture {
    var started = false
}

private fun Modifier.consumeReorderRelease(gesture: ReorderGesture, enabled: Boolean): Modifier =
    pointerInput(gesture, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            gesture.started = false
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (gesture.started) {
                        // Material Card handles clicks inside the drag modifier. Consume the
                        // release before its Main pass, even when the item never moved.
                        event.changes.forEach { change ->
                            if (change.changedToUpIgnoreConsumed()) change.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })
            } finally {
                gesture.started = false
            }
        }
    }
