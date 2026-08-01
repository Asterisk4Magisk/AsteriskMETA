// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

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
}
