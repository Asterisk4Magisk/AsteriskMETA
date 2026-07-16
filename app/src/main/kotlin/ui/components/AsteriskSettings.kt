// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ui.theme.AsteriskMotion
import ui.theme.AsteriskShapeTokens

@Immutable
internal data class AsteriskSegmentItem<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
)

@Composable
internal fun <T> AsteriskSegmentedControl(
    items: List<AsteriskSegmentItem<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AsteriskShapeTokens.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            if (items.isEmpty()) return@BoxWithConstraints

            val selectedIndex = items.indexOfFirst { item -> item.value == selectedValue }
                .coerceAtLeast(0)
            val segmentWidth = maxWidth / items.size.toFloat()
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex.toFloat(),
                animationSpec = AsteriskMotion.fastSpatial(),
                label = "segment-indicator-offset",
            )
            val indicatorColor by animateColorAsState(
                targetValue = MaterialTheme.colorScheme.primaryContainer.copy(
                    alpha = if (enabled) 1f else 0.55f,
                ),
                animationSpec = AsteriskMotion.effects(),
                label = "segment-indicator-color",
            )

            Surface(
                modifier = Modifier
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .width(segmentWidth)
                    .heightIn(min = 48.dp),
                shape = AsteriskShapeTokens.Pill,
                color = indicatorColor,
                content = {},
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val selected = item.value == selectedValue
                    val targetContentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val contentColor by animateColorAsState(
                        targetValue = targetContentColor.copy(alpha = if (enabled) 1f else 0.38f),
                        animationSpec = AsteriskMotion.effects(),
                        label = "segment-content-color",
                    )

                    Surface(
                        onClick = { onSelected(item.value) },
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics {
                                role = Role.RadioButton
                                this.selected = selected
                            },
                        shape = AsteriskShapeTokens.Pill,
                        color = Color.Transparent,
                        contentColor = contentColor,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (item.icon != null) Icon(item.icon, contentDescription = null)
                            Text(
                                text = item.label,
                                modifier = Modifier.padding(start = if (item.icon == null) 0.dp else 6.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
