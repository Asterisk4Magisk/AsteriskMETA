// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.window.layout.WindowLayoutInfo
import ui.layout.separatingWindowPanes
import kotlin.math.roundToInt

internal const val OverlayButtonAnchorSizeDp = 48
internal const val FloatingIconSizeDp = 32
internal const val OverlayChartHeightDp = 34
internal const val OverlayCloseNudgeDp = 2

internal class ConnectionOverlayWindowGeometry(
    private val context: Context,
    private val windows: WindowManager,
    private val view: View,
    type: Int,
    title: String,
    buttonTitle: String,
    allowScreenshots: Boolean,
    savedButtonPosition: Offset?,
) {
    private var overviewHeight = 0
    private var measuredPanelHeight = 0
    private var customSize by mutableStateOf<DpSize?>(null)
    private var panelOffset = IntOffset.Zero
    private var alignFoldedButtonToPanel = true
    private var fittedViewport = viewport()
    private var closeOnRight = view.layoutDirection != View.LAYOUT_DIRECTION_RTL
    var windowLayoutInfo: WindowLayoutInfo? = null
    val hasCustomSize get() = customSize != null
    var buttonPosition = savedButtonPosition ?: defaultButtonPosition()
        private set
    var buttonOffset by mutableStateOf(IntOffset.Zero)
        private set
    var minimized by mutableStateOf(false)
        private set
    var panelVisible by mutableStateOf(true)
        private set
    var panelHeightLimit by mutableStateOf((automaticHeightLimit(fittedViewport.safeBounds.height) / density).dp)
        private set

    val layout = windowLayout(type, title, allowScreenshots)
    val buttonLayout = windowLayout(type, buttonTitle, allowScreenshots)

    init {
        fit(allowScreenshots, fittedViewport)
    }

    fun toggleMinimized(allowScreenshots: Boolean): Boolean {
        if (minimized) panelVisible = true else alignFoldedButtonToPanel = true
        minimized = !minimized
        // Visibility changes reuse the geometry that positioned the visible close button.
        return fit(allowScreenshots, fittedViewport)
    }

    fun finishMinimizing(allowScreenshots: Boolean): Boolean {
        if (minimized) panelVisible = false
        return fit(allowScreenshots, fittedViewport)
    }

    fun updateCloseSide(onRight: Boolean): Boolean {
        if (closeOnRight == onRight) return false
        closeOnRight = onRight
        panelOffset = IntOffset.Zero
        return true
    }

    fun fit(allowScreenshots: Boolean): Boolean {
        fittedViewport = viewport()
        return fit(allowScreenshots, fittedViewport)
    }

    private fun fit(allowScreenshots: Boolean, viewport: OverlayViewport): Boolean {
        val width = viewport.safeBounds.width
        val height = viewport.safeBounds.height
        val anchorSize = buttonAnchorSize(width, height)
        val desiredButton = buttonOrigin(width, height, anchorSize)
        val pane = placementPane(viewport, desiredButton, anchorSize)
        val button = pane.clampOrigin(desiredButton, anchorSize, anchorSize)
        val requestedSize = customSize
        val availableWidth = availableSpace(pane.width)
        val panelWidth = (requestedSize?.let { (it.width.value * density).roundToInt() }
            ?: dp(CompactOverlayWidthDp)).coerceIn(minimumPanelWidth(availableWidth), availableWidth)
        val availableHeight = availableSpace(pane.height)
        val preferredHeight = requestedSize?.let { (it.height.value * density).roundToInt() }
            ?.coerceIn(minimumChartHeight(availableHeight), availableHeight)
            ?: automaticHeightLimit(pane.height)
        val measuredHeight = if (requestedSize != null) preferredHeight else
            measuredPanelHeight.coerceIn(minOf(anchorSize, preferredHeight), preferredHeight)
        val preferredPosition = button - closeOffset(panelWidth, anchorSize) + panelOffset
        val panel = pane.clampOrigin(preferredPosition, panelWidth, measuredHeight)
        val heightAtTop = minOf(availableHeight, (pane.bottom - panel.y).coerceAtLeast(1))
        val maximumHeight = if (requestedSize == null) preferredHeight else
            preferredHeight.coerceIn(minimumPanelHeight(heightAtTop), heightAtTop)
        panelHeightLimit = (maximumHeight / density).dp
        val panelHeight = if (requestedSize == null) WindowManager.LayoutParams.WRAP_CONTENT else maximumHeight
        // Keep the transparent panel laid out so the first expanding frame is ready immediately.
        val panelChanged = layout.updateFrame(
            panel, panelWidth, panelHeight, windowFlags(allowScreenshots, panelVisible),
            alpha = if (panelVisible) 1f else 0f,
        )
        val floatingSize = minOf(anchorSize, dp(FloatingIconSizeDp))
        val inset = floatingInset(anchorSize)
        val restingButton = if (alignFoldedButtonToPanel && (pane.width < width || pane.height < height)) {
            panel + closeOffset(panelWidth, anchorSize)
        } else button
        val buttonFrame = pane.clampOrigin(restingButton + inset, floatingSize, floatingSize)
        val buttonChanged = buttonLayout.updateFrame(
            buttonFrame, floatingSize, floatingSize, windowFlags(allowScreenshots, !panelVisible),
        )
        updateButtonOffset(buttonFrame - inset, anchorSize)
        return panelChanged || buttonChanged
    }

    fun drag(dx: Float, dy: Float): Boolean {
        val viewport = viewport()
        val width = viewport.safeBounds.width
        val height = viewport.safeBounds.height
        val anchorSize = buttonAnchorSize(width, height)
        val desiredButton = buttonOrigin(width, height, anchorSize)
        val pane = placementPane(viewport, desiredButton, anchorSize)
        val current = when {
            !panelVisible && alignFoldedButtonToPanel && (pane.width < width || pane.height < height) ->
                IntOffset(buttonLayout.x, buttonLayout.y) - floatingInset(anchorSize)
            !panelVisible -> desiredButton
            pane.width < width || pane.height < height ->
                desiredButton - closeOffset(layout.width, anchorSize) + panelOffset
            else -> IntOffset(layout.x, layout.y)
        }
        val activeWidth = if (panelVisible) layout.width else anchorSize
        val activeHeight = if (panelVisible) view.height else anchorSize
        val direction = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) 1 else -1
        val x = (current.x + direction * dx.roundToInt()).coerceIn(0, (width - activeWidth).coerceAtLeast(0))
        val y = (current.y + dy.roundToInt()).coerceIn(0, (height - activeHeight).coerceAtLeast(0))
        val position = IntOffset(x, y)
        if (position == current) return false
        if (panelVisible) {
            placePanel(position, layout.width, width, height, anchorSize)
        } else {
            alignFoldedButtonToPanel = false
            buttonPosition = positionFraction(position, width, height, anchorSize)
            panelOffset = IntOffset.Zero
        }
        return true
    }

    fun resize(fromLeft: Boolean, dx: Float, dy: Float): Boolean {
        val viewport = viewport()
        val width = viewport.safeBounds.width
        val height = viewport.safeBounds.height
        val anchorSize = buttonAnchorSize(width, height)
        val pane = placementPane(viewport, buttonOrigin(width, height, anchorSize), anchorSize)
        val movesEndGap = fromLeft == (view.layoutDirection == View.LAYOUT_DIRECTION_RTL)
        val widthDelta = if (fromLeft) -dx else dx
        val widthAtAnchor = if (movesEndGap) layout.x + layout.width - pane.left else pane.right - layout.x
        val maxWidth = minOf(dp(MaxExpandedOverlayWidthDp), availableSpace(pane.width), widthAtAnchor).coerceAtLeast(1)
        val nextWidth = (layout.width + widthDelta.roundToInt()).coerceIn(minimumPanelWidth(maxWidth), maxWidth)
        val maxHeight = minOf(availableSpace(pane.height), (pane.bottom - layout.y).coerceAtLeast(1))
        val currentHeight = if (layout.height > 0) layout.height else view.height
        val nextHeight = (currentHeight + dy.roundToInt()).coerceIn(minimumPanelHeight(maxHeight), maxHeight)
        if (nextWidth == layout.width && nextHeight == currentHeight) return false
        val x = if (movesEndGap) layout.x + layout.width - nextWidth else layout.x
        placePanel(IntOffset(x, layout.y), nextWidth, width, height, anchorSize)
        customSize = DpSize((nextWidth / density).dp, (nextHeight / density).dp)
        return true
    }

    fun updateOverviewHeight(height: Int): Boolean {
        if (height == overviewHeight) return false
        overviewHeight = height
        return hasCustomSize
    }

    fun updatePanelHeight(height: Int): Boolean {
        if (!panelVisible || layout.height > 0 || height <= 0 || measuredPanelHeight == height) return false
        measuredPanelHeight = height
        return true
    }

    private fun defaultButtonPosition(): Offset {
        val bounds = fittedViewport.safeBounds
        val size = buttonAnchorSize(bounds.width, bounds.height)
        return positionFraction(IntOffset(dp(OverlayMarginDp), dp(96)), bounds.width, bounds.height, size)
    }

    private fun floatingInset(anchorSize: Int): IntOffset {
        val inset = ((anchorSize - minOf(anchorSize, dp(FloatingIconSizeDp))) / 2f).roundToInt()
        return IntOffset(inset, inset)
    }

    private fun buttonAnchorSize(width: Int, height: Int): Int =
        minOf(dp(OverlayButtonAnchorSizeDp), availableSpace(width), availableSpace(height))

    private fun buttonOrigin(width: Int, height: Int, size: Int): IntOffset = IntOffset(
        ((width - size).coerceAtLeast(0) * buttonPosition.x).roundToInt(),
        ((height - size).coerceAtLeast(0) * buttonPosition.y).roundToInt(),
    )

    private fun positionFraction(position: IntOffset, width: Int, height: Int, size: Int): Offset = Offset(
        (position.x.toFloat() / (width - size).coerceAtLeast(1)).coerceIn(0f, 1f),
        (position.y.toFloat() / (height - size).coerceAtLeast(1)).coerceIn(0f, 1f),
    )

    private fun closeOffset(panelWidth: Int, anchorSize: Int): IntOffset {
        val nudge = dp(OverlayCloseNudgeDp)
        val endIsRight = view.layoutDirection != View.LAYOUT_DIRECTION_RTL
        val x = if (closeOnRight == endIsRight) -nudge else panelWidth - anchorSize + nudge
        return IntOffset(x, -nudge)
    }

    private fun placePanel(position: IntOffset, panelWidth: Int, width: Int, height: Int, anchorSize: Int) {
        val close = position + closeOffset(panelWidth, anchorSize)
        buttonPosition = positionFraction(close, width, height, anchorSize)
        // At an edge the floating touch target must stay on-screen, while the panel's fixed edges stay put.
        panelOffset = close - buttonOrigin(width, height, anchorSize)
    }

    private fun updateButtonOffset(button: IntOffset, size: Int) {
        // Window coordinates use Gravity.END. Compose uses physical offsets for the animation.
        val end = button.x - layout.x
        val left = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) end else layout.width - size - end
        buttonOffset = IntOffset(left, button.y - layout.y)
    }

    private val density: Float get() = context.resources.displayMetrics.density

    private fun dp(value: Int) = (value * density).roundToInt()

    private fun viewport(): OverlayViewport {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val metrics = context.resources.displayMetrics
            val size = IntSize(metrics.widthPixels, metrics.heightPixels)
            return OverlayViewport(size, IntRect(0, 0, size.width, size.height))
        }
        val metrics = windows.currentWindowMetrics
        val size = IntSize(metrics.bounds.width(), metrics.bounds.height())
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        return OverlayViewport(size, IntRect(insets.left, insets.top, size.width - insets.right, size.height - insets.bottom))
    }

    private fun placementPane(viewport: OverlayViewport, button: IntOffset, anchorSize: Int): IntRect {
        val safe = viewport.safeBounds
        val full = IntRect(0, 0, safe.width, safe.height)
        val panes = separatingWindowPanes(windowLayoutInfo, viewport.size) ?: return full
        val rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
        // WindowManager positions are relative to the safe area's top and end edges.
        val centerX = if (rtl) safe.left + button.x + anchorSize / 2 else safe.right - button.x - anchorSize / 2
        val centerY = safe.top + button.y + anchorSize / 2
        val preferFirst = if (panes.vertical) {
            centerX < (panes.first.right + panes.second.left) / 2
        } else centerY < (panes.first.bottom + panes.second.top) / 2
        val preferred = if (preferFirst) panes.first else panes.second
        val clipped = preferred.intersect(safe).takeIf { it.width >= anchorSize && it.height >= anchorSize }
            ?: panes.other(preferred).intersect(safe).takeIf { it.width >= anchorSize && it.height >= anchorSize }
            ?: return full
        val end = if (rtl) clipped.left - safe.left else safe.right - clipped.right
        val top = clipped.top - safe.top
        return IntRect(end, top, end + clipped.width, top + clipped.height)
    }

    private fun IntRect.clampOrigin(position: IntOffset, width: Int, height: Int): IntOffset = IntOffset(
        position.x.coerceIn(left, (right - width).coerceAtLeast(left)),
        position.y.coerceIn(top, (bottom - height).coerceAtLeast(top)),
    )

    private fun minimumPanelWidth(availableWidth: Int): Int {
        val extraFontScale = (context.resources.configuration.fontScale - 1f).coerceAtLeast(0f)
        val extraWidth = (OverlayResourceTextWidthDp * extraFontScale).roundToInt()
        return dp(MinExpandedOverlayWidthDp + extraWidth).coerceAtMost(availableWidth)
    }

    private fun minimumChartHeight(availableHeight: Int): Int =
        dp(OverlayButtonAnchorSizeDp + OverlayChartHeightDp).coerceAtMost(availableHeight)

    private fun minimumPanelHeight(availableHeight: Int): Int =
        maxOf(overviewHeight, minimumChartHeight(availableHeight)).coerceAtMost(availableHeight)

    private fun availableSpace(size: Int): Int = (size - 2 * dp(OverlayMarginDp)).coerceAtLeast(1)

    private fun automaticHeightLimit(height: Int): Int = (height * 0.78f).roundToInt()
        .coerceIn(dp(350), dp(500))
        .coerceAtMost(availableSpace(height))

    private fun windowLayout(type: Int, title: String, allowScreenshots: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        type,
        windowFlags(allowScreenshots, touchable = false),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        this.title = title
    }

    private fun WindowManager.LayoutParams.updateFrame(
        position: IntOffset,
        width: Int,
        height: Int,
        flags: Int,
        alpha: Float = 1f,
    ): Boolean {
        val changed = this.width != width || this.height != height || x != position.x || y != position.y ||
            this.flags != flags || this.alpha != alpha
        this.width = width
        this.height = height
        x = position.x
        y = position.y
        this.flags = flags
        this.alpha = alpha
        return changed
    }

    private fun windowFlags(allowScreenshots: Boolean, touchable: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!allowScreenshots) flags = flags or WindowManager.LayoutParams.FLAG_SECURE
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return flags
    }
}

private data class OverlayViewport(val size: IntSize, val safeBounds: IntRect)

private const val CompactOverlayWidthDp = 244
private const val MinExpandedOverlayWidthDp = 216
private const val OverlayResourceTextWidthDp = 108
private const val MaxExpandedOverlayWidthDp = 560
private const val OverlayMarginDp = 12
