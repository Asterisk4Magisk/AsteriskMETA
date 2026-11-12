// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import ui.theme.AsteriskMotion
import ui.theme.LocalReduceMotion

/** Shared tint for navigation items, top-bar options and service controls. */
@Composable
internal fun asteriskPillSelectionColor(): Color {
    val colors = MaterialTheme.colorScheme
    val tintAlpha = if (colors.surface.luminance() < 0.5f) 0.18f else 0.12f
    return colors.primary.copy(alpha = tintAlpha)
}

/** Fade and expand at this item's center, never travel across other destinations. */
@Composable
internal fun Modifier.asteriskNavigationSelection(selected: Boolean, cornerRadius: Dp? = null): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val opacity = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 220),
        label = "navigation-selection-opacity",
    )
    val scale = animateFloatAsState(
        targetValue = if (selected) 1f else 0.84f,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.82f, stiffness = 600f)
        },
        label = "navigation-selection-scale",
    )
    val color = animateColorAsState(
        targetValue = asteriskPillSelectionColor(),
        animationSpec = AsteriskMotion.fastEffects(),
        label = "navigation-selection-color",
    )
    return drawBehind {
        val alpha = opacity.value.coerceIn(0f, 1f)
        if (alpha <= 0f) return@drawBehind
        val indicatorScale = scale.value
        val width = size.width * indicatorScale
        val height = size.height * indicatorScale
        drawRoundRect(
            color = color.value.copy(alpha = color.value.alpha * alpha),
            topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f),
            size = Size(width, height),
            cornerRadius = CornerRadius(
                cornerRadius?.toPx()?.times(indicatorScale) ?: (height / 2f)
            ),
        )
    }
}
