// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * `checkedContainerColor` and a bold label.
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
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    checkedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    checkedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    if (items.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SegmentSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = item.value == selectedValue
            val targetContainer = if (selected) checkedContainerColor else containerColor
            val targetContent = if (selected) checkedContentColor else contentColor
            val segmentContainer by animateColorAsState(
                targetValue = if (enabled) targetContainer else targetContainer.copy(alpha = 0.3f),
                animationSpec = AsteriskMotion.fastEffects(),
                label = "segment-container-color",
            )
            val segmentContent by animateColorAsState(
                targetValue = if (enabled) targetContent else targetContent.copy(alpha = 0.38f),
                animationSpec = AsteriskMotion.fastEffects(),
                label = "segment-content-color",
            )
            Surface(
                selected = selected,
                onClick = { onSelected(item.value) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SegmentMinHeight)
                    .alpha(if (item.muted) 0.38f else 1f)
                    .semantics { role = Role.RadioButton },
                shape = segmentShape(selected, index, items.lastIndex),
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
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun segmentShape(selected: Boolean, index: Int, lastIndex: Int): Shape = when {
    selected || lastIndex == 0 -> CircleShape
    index == 0 -> SegmentLeadingShape
    index == lastIndex -> SegmentTrailingShape
    else -> SegmentMiddleShape
}

private val SegmentSpacing = 2.dp
private val SegmentMinHeight = 40.dp
private val SegmentOuterCorner = CornerSize(50)
private val SegmentInnerCorner = CornerSize(8.dp)
private val SegmentLeadingShape = RoundedCornerShape(
    topStart = SegmentOuterCorner,
    topEnd = SegmentInnerCorner,
    bottomEnd = SegmentInnerCorner,
    bottomStart = SegmentOuterCorner,
)
private val SegmentMiddleShape = RoundedCornerShape(SegmentInnerCorner)
private val SegmentTrailingShape = RoundedCornerShape(
    topStart = SegmentInnerCorner,
    topEnd = SegmentOuterCorner,
    bottomEnd = SegmentOuterCorner,
    bottomStart = SegmentInnerCorner,
)
