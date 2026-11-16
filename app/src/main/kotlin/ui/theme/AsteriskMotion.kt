// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

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
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal val LocalReduceMotion = staticCompositionLocalOf { false }

private const val NavigationTransitionDurationMillis = 500
private const val NavigationSpringStiffness = 146f
private const val SheetMotionSpeed = 0.8f

// Mirrors the default transition curve used by AsteriskNG's Miuix NavDisplay.
private val NavigationTransitionEasing = DampedNavigationEasing(
    response = 0.8f,
    damping = 0.95f,
)

private class DampedNavigationEasing(
    response: Float,
    damping: Float,
) : Easing {
    private val decayRate: Float
    private val dampedFrequency: Float
    private val phaseCoefficient: Float

    init {
        val angularFrequency = 2.0 * PI / response
        val stiffness = angularFrequency * angularFrequency
        val dampingCoefficient = damping * 4.0 * PI / response
        val dampedFrequencySquared =
            4.0 * stiffness - dampingCoefficient * dampingCoefficient

        dampedFrequency = (sqrt(dampedFrequencySquared) / 2.0).toFloat()
        decayRate = (-dampingCoefficient / 2.0).toFloat()
        phaseCoefficient = decayRate / dampedFrequency
    }

    override fun transform(fraction: Float): Float {
        val time = fraction.toDouble()
        val decay = exp(decayRate * time)
        return (
            decay * (
                -cos(dampedFrequency * time) +
                    phaseCoefficient * sin(dampedFrequency * time)
            ) + 1.0
        ).toFloat()
    }
}

/**
 * The only feature-facing motion gateway. Spatial changes and visual effects intentionally use
 * different Material motion families, while the system animator preference can disable both.
 */
internal object AsteriskMotion {
    fun <T> navigation(
        reducedMotion: Boolean,
        durationMillis: Int = NavigationTransitionDurationMillis,
    ): FiniteAnimationSpec<T> = if (reducedMotion) {
        snap()
    } else {
        tween(
            durationMillis = durationMillis,
            easing = NavigationTransitionEasing,
        )
    }

    fun navigationSettle(reducedMotion: Boolean): FiniteAnimationSpec<Float> = if (reducedMotion) {
        snap()
    } else {
        spring(dampingRatio = 1f, stiffness = NavigationSpringStiffness, visibilityThreshold = 0.0025f)
    }

    internal fun navigationGestureProgress(anchor: Float, progress: Float): Float =
        (anchor + progress).coerceIn(anchor.coerceAtMost(0f), 0.999f)

    internal fun navigationCommitVelocity(remainingDistance: Float, velocity: Float): Float =
        max(velocity, -sqrt(NavigationSpringStiffness) * remainingDistance.coerceAtLeast(0f))

    @Composable
    fun sheetMotionScheme(): MotionScheme {
        val base = MaterialTheme.motionScheme
        val reducedMotion = LocalReduceMotion.current
        return remember(base, reducedMotion) {
            object : MotionScheme by base {
                override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
                    sheet(base.defaultSpatialSpec(), reducedMotion)

                override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
                    sheet(base.defaultEffectsSpec(), reducedMotion)

                // Material uses fast effects for the sheet's hide translation as well.
                override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
                    sheet(base.fastEffectsSpec(), reducedMotion)
            }
        }
    }

    private fun <T> sheet(
        base: FiniteAnimationSpec<T>,
        reducedMotion: Boolean,
    ): FiniteAnimationSpec<T> = when {
        reducedMotion -> snap()
        base is SpringSpec<T> -> spring(
            dampingRatio = base.dampingRatio,
            // Spring speed scales with the square root of stiffness.
            stiffness = base.stiffness * SheetMotionSpeed * SheetMotionSpeed,
            visibilityThreshold = base.visibilityThreshold,
        )
        else -> base
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

    fun contentFade(reducedMotion: Boolean): FiniteAnimationSpec<Float> = if (reducedMotion) {
        snap()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }

    @Composable
    fun contentFade(): FiniteAnimationSpec<Float> =
        contentFade(reducedMotion = LocalReduceMotion.current)

    fun contentSize(reducedMotion: Boolean): FiniteAnimationSpec<IntSize> = if (reducedMotion) {
        snap()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntSize.VisibilityThreshold,
        )
    }

    @Composable
    fun contentSize(): FiniteAnimationSpec<IntSize> =
        contentSize(reducedMotion = LocalReduceMotion.current)

    fun contentPlacement(reducedMotion: Boolean): FiniteAnimationSpec<IntOffset> = if (reducedMotion) {
        snap()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntOffset.VisibilityThreshold,
        )
    }

    @Composable
    fun contentPlacement(): FiniteAnimationSpec<IntOffset> =
        contentPlacement(reducedMotion = LocalReduceMotion.current)

    @Composable
    fun contentEnter(): EnterTransition =
        fadeIn(animationSpec = contentFade()) +
            expandVertically(animationSpec = contentSize())

    @Composable
    fun contentExit(): ExitTransition =
        shrinkVertically(animationSpec = contentSize()) +
            fadeOut(animationSpec = contentFade())

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
