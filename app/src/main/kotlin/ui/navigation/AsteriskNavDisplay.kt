/*
 * Copyright 2025 The Android Open Source Project
 * Copyright 2025-2026 compose-miuix-ui contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on AndroidX Navigation3's scene APIs and the default animation in
 * compose-miuix-ui/miuix 0.9.4. This single-pane host uses only public AndroidX APIs.
 * Licensed under the Apache License, Version 2.0:
 * https://www.apache.org/licenses/LICENSE-2.0
 */

package ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ui.theme.AsteriskMotion
import ui.theme.LocalReduceMotion
import kotlin.math.abs
import kotlin.math.roundToInt

/** Keep Navigation3 entry ownership while driving every layer from the same visual depth. */
@Composable
internal fun <T : Any> AsteriskNavDisplay(
    entries: List<NavEntry<T>>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(entries.isNotEmpty()) { "Navigation entries cannot be empty" }
    val strategies = remember { listOf(SinglePaneSceneStrategy<T>()) }
    val sceneState = rememberSceneState(entries = entries, sceneStrategies = strategies, onBack = onBack)
    val scene = sceneState.currentScene
    val previousScene = sceneState.previousScenes.lastOrNull()
    val gestureState = rememberNavigationEventState(
        currentInfo = SceneInfo(scene),
        backInfo = sceneState.previousScenes.map { SceneInfo(it) },
    )
    NavigationBackHandler(
        state = gestureState,
        isBackEnabled = scene.previousEntries.isNotEmpty(),
        onBackCompleted = {
            repeat((entries.size - scene.previousEntries.size).coerceAtLeast(0)) { onBack() }
        },
    )

    val currentScenes = sceneState.previousScenes + scene
    val topIndex = currentScenes.lastIndex
    val liveKeys by rememberUpdatedState(currentScenes.map { it.key }.toSet())
    val renderedScenes = remember { mutableStateMapOf<Any, Scene<T>>() }
    val indices = remember { mutableStateMapOf<Any, Int>() }
    // A replacement occupies the same slot; a popped entry keeps its slot until it leaves.
    val replacedKeys = renderedScenes.keys.filter { it !in liveKeys && indices.getValue(it) <= topIndex }
    for (sceneKey in replacedKeys) {
        renderedScenes.remove(sceneKey)
        indices.remove(sceneKey)
    }
    currentScenes.forEachIndexed { index, item ->
        renderedScenes[item.key] = item
        indices[item.key] = index
    }

    val position = remember { Animatable(topIndex.toFloat()) }
    val motion = remember { NavigationMotionState() }
    val inPredictiveBack by remember(previousScene != null) {
        derivedStateOf { previousScene != null && gestureState.transitionState is InProgress }
    }
    val reducedMotion = LocalReduceMotion.current
    LaunchedEffect(scene.key, topIndex, inPredictiveBack, reducedMotion) {
        if (inPredictiveBack) {
            position.stop()
            val anchor = topIndex - position.value
            motion.startGesture()
            snapshotFlow { gestureState.transitionState }.collect { gesture ->
                if (gesture is InProgress) {
                    motion.trackGesture(gesture.latestEvent)
                    position.snapTo(topIndex - AsteriskMotion.navigationGestureProgress(anchor, gesture.latestEvent.progress))
                }
            }
        } else {
            val target = topIndex.toFloat()
            val released = motion.fromGesture
            val initialVelocity = motion.velocity
            motion.fromGesture = false
            val distance = target - position.value
            if (distance == 0f) {
                motion.velocity = 0f
                renderedScenes.keys.retainAll(liveKeys)
                indices.keys.retainAll(liveKeys)
                return@LaunchedEffect
            }
            val cancel = released && distance >= 0f
            val settleVelocity = if (released && !cancel) {
                AsteriskMotion.navigationCommitVelocity(-distance, initialVelocity)
            } else {
                initialVelocity
            }
            val animation = if (!released && initialVelocity == 0f && abs(distance) >= 0.999f) {
                AsteriskMotion.navigation(reducedMotion)
            } else {
                AsteriskMotion.navigationSettle(reducedMotion)
            }
            if (cancel) position.updateBounds(upperBound = target)
            try {
                position.animateTo(target, animationSpec = animation, initialVelocity = settleVelocity) {
                    motion.velocity = velocity
                }
                motion.velocity = 0f
                renderedScenes.keys.retainAll(liveKeys)
                indices.keys.retainAll(liveKeys)
            } finally {
                if (cancel) position.updateBounds(lowerBound = null, upperBound = null)
            }
        }
    }

    val settled by remember(topIndex, inPredictiveBack) {
        derivedStateOf { !inPredictiveBack && !position.isRunning && position.value == topIndex.toFloat() }
    }
    val visibleKeys by remember(scene.key) {
        derivedStateOf {
            val top = position.value
            renderedScenes.keys.filter { sceneKey ->
                val depth = top - indices.getValue(sceneKey)
                sceneKey == scene.key || (depth > -1f && depth < 1f)
            }.sortedBy { indices.getValue(it) }
        }
    }
    val cornerRadius = rememberNavigationCornerRadius()
    val cornerShape = remember(cornerRadius) {
        RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius)
    }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Box(modifier) {
        visibleKeys.forEachIndexed { visibleIndex, sceneKey ->
            val index = indices.getValue(sceneKey)
            if (visibleIndex == visibleKeys.lastIndex && visibleIndex > 0) {
                Box(Modifier.fillMaxSize().drawBehind {
                    // The scrim covers the viewport, independently of the covered page's alpha.
                    drawRect(Color.Black, alpha = (position.value - index + 1f).coerceIn(0f, 1f) * 0.5f)
                })
            }
            key(sceneKey) {
                val lifecycleOwner = rememberLifecycleOwner(
                    maxLifecycle = if (settled && sceneKey == scene.key) Lifecycle.State.RESUMED else Lifecycle.State.STARTED,
                )
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                    Box(
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            val depth = position.value - index
                            val covered = depth.coerceIn(0f, 1f)
                            translationX = if (depth <= 0f) {
                                (direction * (-depth).coerceIn(0f, 1f) * size.width).roundToInt().toFloat()
                            } else {
                                -direction * covered * size.width * 0.25f
                            }
                            alpha = when {
                                depth <= -1f || depth > 1f -> 0f
                                depth > 0f -> 1f - covered * 0.1f
                                else -> 1f
                            }
                            shape = cornerShape
                            clip = depth > -1f && depth < 0f
                        }.then(if (sceneKey == scene.key) Modifier else Modifier.clearAndSetSemantics {})
                            .pointerInput(settled) {
                                if (!settled) awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                    }
                                }
                            },
                    ) {
                        renderedScenes.getValue(sceneKey).content()
                    }
                }
            }
        }
    }
}

private class NavigationMotionState {
    var velocity = 0f
    var fromGesture = false
    private var lastProgress = 0f
    private var lastTimeMillis = 0L

    fun startGesture() {
        velocity = 0f
        fromGesture = true
        lastProgress = 0f
        lastTimeMillis = 0L
    }

    fun trackGesture(event: NavigationEvent) {
        if (lastTimeMillis != 0L && event.frameTimeMillis > lastTimeMillis) {
            velocity = -(event.progress - lastProgress) * 1000f / (event.frameTimeMillis - lastTimeMillis)
        }
        if (event.frameTimeMillis != 0L) {
            lastProgress = event.progress
            lastTimeMillis = event.frameTimeMillis
        }
    }
}
