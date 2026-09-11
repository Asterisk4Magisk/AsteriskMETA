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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ui.theme.AsteriskMotion
import ui.theme.LocalReduceMotion

/** Full-width sliding highlight for the service mode switch. */
@Composable
internal fun Modifier.asteriskPillIndicator(
    selectedIndex: Int,
    itemCount: Int,
    enabled: Boolean = true,
): Modifier {
    val position = animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0)).toFloat(),
        animationSpec = AsteriskMotion.fastSpatial(),
        label = "pill-indicator-position",
    )
    val color = animateColorAsState(
        targetValue = asteriskPillSelectionColor(enabled),
        animationSpec = AsteriskMotion.fastEffects(),
        label = "pill-indicator-color",
    )
    val layoutDirection = LocalLayoutDirection.current
    return drawBehind {
        if (itemCount <= 0) return@drawBehind
        val segmentWidth = size.width / itemCount
        val logicalPosition = position.value.coerceIn(0f, (itemCount - 1).toFloat())
        val physicalPosition = if (layoutDirection == LayoutDirection.Rtl) {
            itemCount - 1 - logicalPosition
        } else {
            logicalPosition
        }
        drawRoundRect(
            color = color.value,
            topLeft = Offset(physicalPosition * segmentWidth, 0f),
            size = Size(segmentWidth, size.height),
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}

/** Keep the navigation and mode-control tint identical while their motion differs. */
@Composable
private fun asteriskPillSelectionColor(enabled: Boolean = true): Color {
    val colors = MaterialTheme.colorScheme
    val tintAlpha = if (colors.surface.luminance() < 0.5f) 0.18f else 0.12f
    return colors.primary.copy(alpha = tintAlpha * if (enabled) 1f else 0.55f)
}

/** Fade and expand at this item's center, never travel across other destinations. */
@Composable
internal fun Modifier.asteriskNavigationSelection(selected: Boolean): Modifier {
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
            cornerRadius = CornerRadius(height / 2f),
        )
    }
}
