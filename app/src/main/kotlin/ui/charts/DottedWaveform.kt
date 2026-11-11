// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.isInDarkTheme
import kotlin.math.roundToInt

/** Radius of a single waveform dot; both the lattice pitch and the columns derive from it. */
val DottedWaveformDotRadius: Dp = 1.6.dp

private const val DottedWaveformRampSteps = 48
private const val LightLowToneWeight = 0.10f
private const val LightHighToneWeight = 0.18f
private const val DarkLowToneWeight = 0.25f
private const val DarkHighToneWeight = 0.30f

/**
 * Per-dot MD3 tone ladder.
 *
 * `position` runs from the dot next to the middle axis (`0f`) to the far edge of the half (`1f`).
 * Both ends stay saturated and opaque so even the shortest column is readable; the seam leans
 * slightly towards the card surface, while the far end is pushed with a plain black or white tonal
 * anchor instead of `on*Container`, because a dynamic scheme is free to hand back an `on*Container`
 * that is darker than its base role, which would invert the required direction.
 */
class DottedWaveformRamp(
    lowColor: Color,
    highColor: Color,
) {
    // Charts ask for one colour per dot, so the ladder is baked once: the draw loop then only does
    // an array lookup instead of a perceptual colour interpolation per dot.
    private val palette = IntArray(DottedWaveformRampSteps) { index ->
        lerp(lowColor, highColor, index / (DottedWaveformRampSteps - 1f)).toArgb()
    }

    fun colorFor(position: Float): Color {
        // coerceIn does not sanitize NaN, and roundToInt would throw on it.
        val sanitized = if (position.isFinite()) position.coerceIn(0f, 1f) else 0f
        val index = (sanitized * (DottedWaveformRampSteps - 1)).roundToInt()
        return Color(palette[index])
    }
}

@Composable
fun rememberDottedWaveformRamp(baseColor: Color): DottedWaveformRamp {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isInDarkTheme()
    return remember(baseColor, colorScheme, isDark) {
        DottedWaveformRamp(
            lowColor = lerp(
                baseColor,
                colorScheme.surfaceContainer,
                if (isDark) DarkLowToneWeight else LightLowToneWeight,
            ),
            highColor = lerp(
                baseColor,
                if (isDark) Color.White else Color.Black,
                if (isDark) DarkHighToneWeight else LightHighToneWeight,
            ),
        )
    }
}

/**
 * Draws [amplitudes] as one vertical column of dots per slot.
 *
 * The x axis is always [slotCount] columns wide: the newest sample sits in the rightmost slot, and a
 * column's dots use the column pitch as their vertical pitch, so the chart is a square lattice.
 * Colour follows the absolute distance from the axis, therefore two dots at the same height always
 * share the same tone. A zero sample still draws its base dot; a slot with no sample at all must be
 * passed as `NaN` (the convention shared with the other chart painters here) and stays empty.
 */
fun DrawScope.drawDottedWaveform(
    amplitudes: FloatArray,
    slotCount: Int,
    dotRadius: Float,
    direction: Float,
    ramp: DottedWaveformRamp,
    verticalDotPitch: Float = 0f,
) {
    if (amplitudes.isEmpty() || slotCount < 2) return
    val slotWidth = (size.width - dotRadius * 2f) / (slotCount - 1)
    if (slotWidth <= 0f) return
    val dotPitch = slotWidth
    // The lattice pitch doubles as the vertical step, which leaves a wide chart with only a handful
    // of rows. Callers on a wide, short plot pass a finer pitch to keep the columns readable.
    val verticalPitch = if (verticalDotPitch > 0f) verticalDotPitch else dotPitch
    // The first dot starts one pitch away from the anchor, which leaves an empty seam between two
    // opposing series (or above the baseline of a single one), and the last dot stays inside.
    val firstDotOffset = verticalPitch
    // Both halves meet at the vertical middle of the canvas.
    val anchorY = size.height / 2f
    // Callers pass -1f (grow up) or +1f (grow down); zero is treated as "up" on purpose.
    val extent = if (direction <= 0f) anchorY else size.height - anchorY
    val usableHeight = extent - dotRadius - firstDotOffset
    if (usableHeight < 0f) return
    val maxDots = (usableHeight / verticalPitch).toInt() + 1
    val tallestColumnOffset = firstDotOffset + (maxDots - 1) * verticalPitch
    val visible = minOf(amplitudes.size, slotCount)
    // The newest sample owns the rightmost slot, so the axis grows from the right edge.
    val firstSample = amplitudes.size - visible
    val initialSlot = slotCount - visible
    for (slot in 0 until visible) {
        val raw = amplitudes[firstSample + slot]
        if (raw.isNaN()) continue
        val amplitude = raw.coerceIn(0f, 1f)
        val columnDots = 1 + (amplitude * (maxDots - 1)).roundToInt()
        val x = dotRadius + slotWidth * (initialSlot + slot)
        for (dot in 0 until columnDots) {
            val offset = firstDotOffset + dot * verticalPitch
            drawCircle(
                color = ramp.colorFor(offset / tallestColumnOffset),
                radius = dotRadius,
                center = Offset(x = x, y = anchorY + direction * offset),
            )
        }
    }
}
