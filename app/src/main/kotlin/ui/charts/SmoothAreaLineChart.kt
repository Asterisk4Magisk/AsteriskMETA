// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Draws [values] (`0f..1f`, `NaN` = no sample) as a smoothed line plus a decorative gradient area.
 *
 * The area is purely visual: it runs from the line down to the baseline and fades to transparent, so
 * it never changes what the line says or how the axis is laid out.
 */
fun DrawScope.drawSmoothAreaLine(
    values: FloatArray,
    color: Color,
    strokeWidth: Float,
    areaAlpha: Float = 0.34f,
) {
    if (values.size < 2 || size.width <= 0f || size.height <= 0f) return
    val step = size.width / (values.size - 1)
    fun pointAt(index: Int): Offset {
        val value = values[index].coerceIn(0f, 1f)
        return Offset(x = step * index, y = size.height - value * size.height)
    }
    var index = 0
    while (index < values.size) {
        if (values[index].isNaN()) {
            index += 1
            continue
        }
        var last = index
        while (last + 1 < values.size && !values[last + 1].isNaN()) last += 1
        if (last == index) {
            // A single sample has no line to draw; mark it with a dot so a lone reading is visible.
            drawCircle(color = color, radius = strokeWidth, center = pointAt(index))
        } else {
            val points = List(last - index + 1) { offset -> pointAt(index + offset) }
            val line = smoothedPath(points, size.height)
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            // Anchor the fade at the series' own crest so a low series still shows a fill.
            val fillBrush = Brush.verticalGradient(
                0f to color.copy(alpha = areaAlpha),
                1f to color.copy(alpha = 0f),
                startY = points.minOf { point -> point.y },
                endY = size.height,
            )
            drawPath(path = area, brush = fillBrush)
            drawPath(
                path = line,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        index = last + 1
    }
}

/**
 * Catmull-Rom through [points], expressed as cubic Béziers, so the polyline reads as a soft curve
 * instead of a staircase of peaks. Control points are clamped to the canvas so the curve cannot
 * bulge outside the plot.
 */
private fun smoothedPath(points: List<Offset>, bottomY: Float): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (index in 0 until points.size - 1) {
        val p0 = points[(index - 1).coerceAtLeast(0)]
        val p1 = points[index]
        val p2 = points[index + 1]
        val p3 = points[(index + 2).coerceAtMost(points.lastIndex)]
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, (p1.y + (p2.y - p0.y) / 6f).coerceIn(0f, bottomY))
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, (p2.y - (p3.y - p1.y) / 6f).coerceIn(0f, bottomY))
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    return path
}
