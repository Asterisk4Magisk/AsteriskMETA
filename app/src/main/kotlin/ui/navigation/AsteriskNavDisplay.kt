/*
 * Copyright 2025 The Android Open Source Project
 * Copyright 2025 compose-miuix-ui contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on AndroidX Navigation3 1.1.7's NavDisplay and the interruption handling in
 * compose-miuix-ui/miuix 0.9.3. This single-pane host uses only public AndroidX APIs.
 * Licensed under the Apache License, Version 2.0:
 * https://www.apache.org/licenses/LICENSE-2.0
 */

package ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import ui.theme.AsteriskMotion
import ui.theme.LocalReduceMotion

/**
 * The apps use one entry per scene; sheets and dialogs live inside those entries.
 * Keep AndroidX entry decoration, lifecycle and back dispatch, while owning the
 * transition so an interrupted entry cannot change stacking order or restart at zero.
 */
@Composable
internal fun <T : Any> AsteriskNavDisplay(
    entries: List<NavEntry<T>>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform,
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform,
    predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform,
) {
    require(entries.isNotEmpty()) { "Navigation entries cannot be empty" }
    val strategies = remember { listOf(SinglePaneSceneStrategy<T>()) }
    val sceneState = rememberSceneState(
        entries = entries,
        sceneStrategies = strategies,
        onBack = onBack,
    )
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

    val transitionState = remember { SeekableTransitionState(scene) }
    val transition = rememberTransition(transitionState, label = "navigation-scene")
    val inPredictiveBack by remember(previousScene != null) {
        derivedStateOf { previousScene != null && gestureState.transitionState is InProgress }
    }
    val contentKeys = sceneState.entries.map { it.contentKey }
    val history = remember { NavigationHistory(contentKeys) }
    history.update(contentKeys)
    val isPop = history.isPop

    // A scene keeps its assigned layer until the whole transition has settled.
    // In particular, starting back while A -> B is entering must not put B below A.
    val layers = remember { mutableMapOf<Any, Float>() }
    val initialKey = transition.currentState.key
    val targetKey = transition.targetState.key
    val initialLayer = layers.getOrPut(initialKey) { 0f }
    val interruptedEntry = inPredictiveBack && transition.currentState == previousScene
    val targetLayer = when {
        initialKey == targetKey -> initialLayer
        targetKey in layers -> layers.getValue(targetKey)
        (isPop || inPredictiveBack) && !interruptedEntry -> initialLayer - 1f
        else -> initialLayer + 1f
    }
    layers[targetKey] = targetLayer

    val reducedMotion = LocalReduceMotion.current
    if (inPredictiveBack && previousScene != null) {
        LaunchedEffect(scene, previousScene) {
            val entryInterruptionFraction = transitionState.fraction
            snapshotFlow { gestureState.transitionState }.collectLatest { gesture ->
                if (gesture is InProgress) {
                    if (transition.currentState == previousScene) {
                        // Reverse the partially completed A -> B transition, rather than
                        // retargeting it as though B had already finished entering.
                        transitionState.seekTo(
                            AsteriskMotion.interruptedNavigationFraction(
                                entryFraction = entryInterruptionFraction,
                                backProgress = gesture.latestEvent.progress,
                            ),
                            scene,
                        )
                    } else {
                        transitionState.seekTo(gesture.latestEvent.progress, previousScene)
                    }
                }
            }
        }
    } else {
        LaunchedEffect(scene) {
            if (transitionState.currentState != scene) {
                if (transitionState.targetState == scene) {
                    transitionState.animateTo(
                        scene,
                        animationSpec = AsteriskMotion.navigation(reducedMotion),
                    )
                } else {
                    transitionState.animateTo(scene)
                }
            } else {
                // Commit/cancel can return to the initial state while the entry is still
                // visible. Finish that same transition before disposing its old content.
                val duration = transition.totalDurationNanos / 1_000_000
                val completed = transition.targetState == scene
                val finalFraction = if (completed) 1f else 0f
                val remaining = if (completed) 1f - transitionState.fraction else transitionState.fraction
                animate(
                    transitionState.fraction,
                    finalFraction,
                    animationSpec = AsteriskMotion.navigation(
                        reducedMotion = reducedMotion,
                        durationMillis = (remaining * duration).toInt(),
                    ),
                ) { value, _ ->
                    this@LaunchedEffect.launch {
                        if (value == finalFraction) transitionState.snapTo(scene)
                        else transitionState.seekTo(value)
                    }
                }
            }
        }
    }

    transition.AnimatedContent(
        modifier = modifier,
        contentKey = { it.key },
        transitionSpec = {
            val gesture = gestureState.transitionState
            val transform = when {
                inPredictiveBack && gesture is InProgress ->
                    predictivePopTransitionSpec(gesture.latestEvent.swipeEdge)
                isPop -> popTransitionSpec()
                else -> transitionSpec()
            }
            ContentTransform(
                targetContentEnter = transform.targetContentEnter,
                initialContentExit = transform.initialContentExit,
                targetContentZIndex = targetLayer,
                sizeTransform = null,
            )
        },
    ) { targetScene ->
        val settled = transition.currentState == transition.targetState
        val lifecycleOwner = rememberLifecycleOwner(
            maxLifecycle = if (settled) Lifecycle.State.RESUMED else Lifecycle.State.STARTED,
        )
        CompositionLocalProvider(
            LocalLifecycleOwner provides lifecycleOwner,
            LocalNavAnimatedContentScope provides this,
        ) {
            targetScene.content()
        }
    }

    LaunchedEffect(transition, inPredictiveBack) {
        if (!inPredictiveBack) {
            snapshotFlow {
                transition.currentState == transition.targetState && !transition.isRunning
            }.filter { it }.collect {
                val settledKey = transition.targetState.key
                layers.keys.retainAll(setOf(settledKey))
            }
        }
    }
}

private class NavigationHistory(private var keys: List<Any>) {
    var isPop: Boolean = false
        private set

    fun update(next: List<Any>) {
        if (next == keys) return
        isPop = next.size < keys.size && keys.take(next.size) == next
        keys = next
    }
}
