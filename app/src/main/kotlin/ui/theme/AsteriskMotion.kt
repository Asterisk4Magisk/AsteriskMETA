// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.navigationevent.NavigationEvent

internal val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Smooth, non-bouncing spatial motion for content that changes the surrounding layout.
 */
internal object AsteriskContentMotionScheme {
    private val defaultSpatialSpec: FiniteAnimationSpec<Any> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    @Suppress("UNCHECKED_CAST")
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        defaultSpatialSpec as FiniteAnimationSpec<T>
}

/**
 * The only feature-facing motion gateway. Spatial changes and visual effects intentionally use
 * different Material motion families, while the system animator preference can disable both.
 */
internal object AsteriskMotion {
    internal data class HorizontalSlideOffsets(
        val incoming: Int,
        val outgoing: Int,
    )

    internal fun predictiveBackSlideOffsets(
        width: Int,
        swipeEdge: Int,
    ): HorizontalSlideOffsets {
        val direction = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
        return HorizontalSlideOffsets(
            incoming = -direction * width / 3,
            outgoing = direction * width,
        )
    }

    fun <T> effects(reducedMotion: Boolean): FiniteAnimationSpec<T> = if (reducedMotion) {
        snap()
    } else {
        MotionScheme.expressive().defaultEffectsSpec()
    }

    @Composable
    fun <T> effects(): FiniteAnimationSpec<T> = if (LocalReduceMotion.current) {
        snap()
    } else {
        MaterialTheme.motionScheme.defaultEffectsSpec()
    }

    @Composable
    fun <T> fastEffects(): FiniteAnimationSpec<T> = if (LocalReduceMotion.current) {
        snap()
    } else {
        MaterialTheme.motionScheme.fastEffectsSpec()
    }

    @Composable
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = if (LocalReduceMotion.current) {
        snap()
    } else {
        MaterialTheme.motionScheme.fastSpatialSpec()
    }

    @Composable
    fun <T> spatial(): FiniteAnimationSpec<T> = if (LocalReduceMotion.current) {
        snap()
    } else {
        MaterialTheme.motionScheme.defaultSpatialSpec()
    }

    @Composable
    fun <T> contentSpatial(): FiniteAnimationSpec<T> = if (LocalReduceMotion.current) {
        snap()
    } else {
        AsteriskContentMotionScheme.defaultSpatialSpec()
    }

    @Composable
    fun contentEnter(): EnterTransition =
        fadeIn(animationSpec = contentSpatial()) +
            expandVertically(animationSpec = contentSpatial())

    @Composable
    fun contentExit(): ExitTransition =
        shrinkVertically(animationSpec = contentSpatial()) +
            fadeOut(animationSpec = contentSpatial())

    @Composable
    fun <S> navigationForward(): AnimatedContentTransitionScope<S>.() -> ContentTransform {
        val spatialSpec = spatial<IntOffset>()
        return {
            slideInHorizontally(
                animationSpec = spatialSpec,
                initialOffsetX = { width -> width },
            ).togetherWith(
                slideOutHorizontally(
                    animationSpec = spatialSpec,
                    targetOffsetX = { width -> -width / 3 },
                ),
            )
        }
    }

    @Composable
    fun <S> navigationBack(): AnimatedContentTransitionScope<S>.() -> ContentTransform {
        val spatialSpec = spatial<IntOffset>()
        return {
            slideInHorizontally(
                animationSpec = spatialSpec,
                initialOffsetX = { width -> -width / 3 },
            ).togetherWith(
                slideOutHorizontally(
                    animationSpec = spatialSpec,
                    targetOffsetX = { width -> width },
                ),
            )
        }
    }

    @Composable
    fun <S> predictiveNavigationBack():
        AnimatedContentTransitionScope<S>.(Int) -> ContentTransform {
        val spatialSpec = spatial<IntOffset>()
        return { swipeEdge ->
            slideInHorizontally(
                animationSpec = spatialSpec,
                initialOffsetX = { width ->
                    predictiveBackSlideOffsets(width, swipeEdge).incoming
                },
            ).togetherWith(
                slideOutHorizontally(
                    animationSpec = spatialSpec,
                    targetOffsetX = { width ->
                        predictiveBackSlideOffsets(width, swipeEdge).outgoing
                    },
                ),
            )
        }
    }

    @Composable
    fun <S> destinationChange(
        indexOf: (S) -> Int,
    ): AnimatedContentTransitionScope<S>.() -> ContentTransform {
        val spatialSpec = spatial<IntOffset>()
        val effectsSpec = fastEffects<Float>()
        return horizontalSlideFade(
            spatialSpec = spatialSpec,
            effectsSpec = effectsSpec,
            distanceDivisor = 8,
        ) {
            indexOf(targetState).compareTo(indexOf(initialState))
        }
    }

    fun <S> horizontalSlideFade(
        spatialSpec: FiniteAnimationSpec<IntOffset>,
        effectsSpec: FiniteAnimationSpec<Float>,
        sizeSpec: FiniteAnimationSpec<IntSize>? = null,
        distanceDivisor: Int = 5,
        direction: AnimatedContentTransitionScope<S>.() -> Int,
    ): AnimatedContentTransitionScope<S>.() -> ContentTransform {
        require(distanceDivisor > 0) { "distanceDivisor must be positive" }
        return {
            val slideDirection = direction()
            val transform = (
                slideInHorizontally(
                    animationSpec = spatialSpec,
                    initialOffsetX = { width -> slideDirection * width / distanceDivisor },
                ) + fadeIn(animationSpec = effectsSpec)
                ).togetherWith(
                slideOutHorizontally(
                    animationSpec = spatialSpec,
                    targetOffsetX = { width -> -slideDirection * width / distanceDivisor },
                ) + fadeOut(animationSpec = effectsSpec),
            )
            transform.withSizeSpec(sizeSpec)
        }
    }

    fun <S> fadeThrough(
        effectsSpec: FiniteAnimationSpec<Float>,
        sizeSpec: FiniteAnimationSpec<IntSize>? = null,
    ): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (fadeIn(animationSpec = effectsSpec) togetherWith fadeOut(animationSpec = effectsSpec))
            .withSizeSpec(sizeSpec)
    }

    fun fadeEnter(spec: FiniteAnimationSpec<Float>): EnterTransition =
        fadeIn(animationSpec = spec)

    fun fadeExit(spec: FiniteAnimationSpec<Float>): ExitTransition =
        fadeOut(animationSpec = spec)

    fun scaleFadeEnter(
        effectsSpec: FiniteAnimationSpec<Float>,
        spatialSpec: FiniteAnimationSpec<Float>,
    ): EnterTransition =
        fadeIn(animationSpec = effectsSpec) + scaleIn(animationSpec = spatialSpec)

    fun scaleFadeExit(
        effectsSpec: FiniteAnimationSpec<Float>,
        spatialSpec: FiniteAnimationSpec<Float>,
    ): ExitTransition =
        fadeOut(animationSpec = effectsSpec) + scaleOut(animationSpec = spatialSpec)

    fun <S> scaleSwap(
        spatialSpec: FiniteAnimationSpec<Float>,
        scale: Float = 0.92f,
    ): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        scaleIn(
            initialScale = scale,
            animationSpec = spatialSpec,
        ).togetherWith(
            scaleOut(
                targetScale = scale,
                animationSpec = spatialSpec,
            ),
        )
    }

    private fun ContentTransform.withSizeSpec(
        sizeSpec: FiniteAnimationSpec<IntSize>?,
    ): ContentTransform = if (sizeSpec == null) {
        this
    } else {
        ContentTransform(
            targetContentEnter = targetContentEnter,
            initialContentExit = initialContentExit,
            targetContentZIndex = targetContentZIndex,
            sizeTransform = SizeTransform(sizeAnimationSpec = { _, _ -> sizeSpec }),
        )
    }
}
