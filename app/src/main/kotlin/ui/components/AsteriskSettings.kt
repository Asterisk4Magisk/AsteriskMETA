// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.theme.AsteriskMotion

@Immutable
internal data class AsteriskSegmentItem<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    /** Dims the segment without disabling it, for modes that only show a tip when tapped. */
    val muted: Boolean = false,
)

/**
 * Connected segmented switch: equal-width segments 2dp apart whose corners round only on the
 * group's outer edges, the selected one turning into a full pill filled with
 * `checkedContainerColor` and a bold label. Corners and label weight transition
 * with the colors so selecting a segment never snaps its outline or text.
 *
 * Built from plain selectable surfaces rather than the expressive `ButtonGroup`/`ToggleButton`:
 * those expose checkbox semantics (a screen reader would announce a one-of-many choice as
 * independent toggles) and measure through the group's overflow machinery on every pass. Each
 * segment here is a radio button inside a selectable group and keeps the 48dp touch target.
 */
@Composable
internal fun <T> AsteriskSegmentedControl(
    items: List<AsteriskSegmentItem<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = if (LocalTopBarContent.current) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    },
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    checkedContainerColor: Color = if (LocalTopBarContent.current) {
        asteriskPillSelectionColor()
    } else {
        MaterialTheme.colorScheme.primaryContainer
    },
    checkedContentColor: Color = if (LocalTopBarContent.current) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    },
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    minHeight: Dp = SegmentMinHeight,
) {
    if (items.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SegmentSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = item.value == selectedValue
            val selectionProgress by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = AsteriskMotion.effects(),
                label = "segment-selection-progress",
            )
            val selectionFraction = selectionProgress.coerceIn(0f, 1f)
            val targetContainer = if (selected) checkedContainerColor else containerColor
            val targetContent = if (selected) checkedContentColor else contentColor
            val segmentContainer by animateColorAsState(
                targetValue = if (enabled) targetContainer else targetContainer.copy(alpha = targetContainer.alpha * 0.3f),
                animationSpec = AsteriskMotion.effects(),
                label = "segment-container-color",
            )
            val segmentContent by animateColorAsState(
                targetValue = if (enabled) targetContent else targetContent.copy(alpha = 0.38f),
                animationSpec = AsteriskMotion.effects(),
                label = "segment-content-color",
            )
            Surface(
                selected = selected,
                onClick = { onSelected(item.value) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = minHeight)
                    .alpha(if (item.muted) 0.38f else 1f)
                    .semantics { role = Role.RadioButton },
                shape = segmentShape(selectionFraction, index, items.lastIndex),
                color = segmentContainer,
                contentColor = segmentContent,
            ) {
                Row(
                    modifier = Modifier.padding(contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.icon != null) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = item.label,
                        style = textStyle,
                        fontWeight = lerp(FontWeight.Normal, FontWeight.Bold, selectionFraction),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun segmentShape(selectionFraction: Float, index: Int, lastIndex: Int): Shape {
    if (lastIndex == 0) return CircleShape
    val innerCorner = SegmentInnerCornerSize(selectionFraction)
    return RoundedCornerShape(
        topStart = if (index == 0) SegmentOuterCorner else innerCorner,
        topEnd = if (index == lastIndex) SegmentOuterCorner else innerCorner,
        bottomEnd = if (index == lastIndex) SegmentOuterCorner else innerCorner,
        bottomStart = if (index == 0) SegmentOuterCorner else innerCorner,
    )
}

@Immutable
private data class SegmentInnerCornerSize(val selectionFraction: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val restingRadius = with(density) { SegmentInnerCorner.toPx() }
        val selectedRadius = shapeSize.minDimension / 2f
        return restingRadius + (selectedRadius - restingRadius) * selectionFraction
    }
}

private val SegmentSpacing = 2.dp
private val SegmentMinHeight = 40.dp
private val SegmentOuterCorner = CornerSize(50)
private val SegmentInnerCorner = 8.dp
