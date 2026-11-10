// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ui.theme.AsteriskMotion

internal fun Modifier.draggedCardShadow(
    alpha: Float,
    color: Color,
    cornerRadius: Dp,
): Modifier {
    if (alpha <= 0f) return this
    return drawBehind {
        val cardCornerRadiusPx = cornerRadius.toPx()
        val maxSpread = 12.dp.toPx()
        val steps = 12
        for (step in steps downTo 1) {
            val progress = step / steps.toFloat()
            val spread = maxSpread * progress
            val layerAlpha = alpha * 0.035f * (1f - (step - 1f) / steps)
            drawRoundRect(
                color = color.copy(alpha = layerAlpha),
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + spread * 2, size.height + spread * 2),
                cornerRadius = CornerRadius(
                    draggedCardShadowLayerCornerRadius(cardCornerRadiusPx, spread),
                ),
            )
        }
    }
}

internal fun draggedCardShadowLayerCornerRadius(
    cardCornerRadiusPx: Float,
    spreadPx: Float,
): Float = cardCornerRadiusPx + spreadPx

/** The animated lift of a reorderable card; apply it with [draggedCardLift]. */
@Stable
internal class DraggedCardLift(
    val isDragging: Boolean,
    private val scaleState: State<Float>,
    val shadowAlpha: Float,
    val shadowColor: Color,
) {
    val scale: Float
        get() = scaleState.value
}

/** Animate the scale and glow of a card while it is dragged; [label] prefixes both animations. */
@Composable
internal fun animateDraggedCardLift(isDragging: Boolean, label: String): DraggedCardLift {
    val scale = animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1f,
        animationSpec = AsteriskMotion.fastSpatial(),
        label = "$label-scale",
    )
    val shadowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = AsteriskMotion.fastEffects(),
        label = "$label-shadow",
    )
    return DraggedCardLift(
        isDragging = isDragging,
        scaleState = scale,
        shadowAlpha = shadowAlpha,
        shadowColor = MaterialTheme.colorScheme.primary,
    )
}

/** Draw a dragged card above its siblings, scaled, with the dragged glow behind it. */
internal fun Modifier.draggedCardLift(lift: DraggedCardLift, cornerRadius: Dp): Modifier =
    zIndex(if (lift.isDragging) 1f else 0f)
        .graphicsLayer {
            scaleX = lift.scale
            scaleY = lift.scale
        }
        .draggedCardShadow(
            alpha = lift.shadowAlpha,
            color = lift.shadowColor,
            cornerRadius = cornerRadius,
        )
