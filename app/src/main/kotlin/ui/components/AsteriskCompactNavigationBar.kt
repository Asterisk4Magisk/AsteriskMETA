// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ui.theme.AsteriskMotion

internal data class AsteriskCompactNavigationTab(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * Compact bottom navigation: icon-only tabs inside one
 * floating circle of `surfaceContainer`, with a `secondaryContainer` circle
 * that travels to the selected tab through `animateBounds`, and an optional
 * trailing action sitting beside the pill.
 *
 * The pill and the action form one centred group — the action hugs the pill
 * instead of being pinned to the screen edge.
 */
@Composable
internal fun AsteriskCompactNavigationBar(
    tabs: List<AsteriskCompactNavigationTab>,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val boundsSpec = AsteriskMotion.spatial<Rect>()
    val boundsTransform = remember(boundsSpec) { BoundsTransform { _, _ -> boundsSpec } }
    // The pill's movable content is remembered once, so it has to read the
    // latest transform (Reduce Motion can swap it after the first frame).
    val currentBoundsTransform by rememberUpdatedState(boundsTransform)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // `maxWidth` is already the content width (outer padding removed), so
        // compare against a content threshold, not a device width. 320dp of
        // content means a ~360dp device keeps the roomy spacing.
        val dense = maxWidth < 320.dp
        // 24dp icon + 2 × 12dp keeps the 48dp minimum touch target.
        val tabHorizontalPadding = if (dense) 12.dp else 16.dp
        val groupSpacing = if (dense) 8.dp else 12.dp

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(groupSpacing),
        ) {
            Box(
                modifier = Modifier
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(4.dp),
            ) {
                LookaheadScope {
                    // One shared pill, moved between tabs so it slides instead of
                    // fading out and in at the destination.
                    val selectionPill = remember {
                        movableContentOf<BoxScope> { scope ->
                            with(scope) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .animateBounds(
                                            lookaheadScope = this@LookaheadScope,
                                            boundsTransform = currentBoundsTransform,
                                        )
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.selectableGroup(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        tabs.forEach { tab ->
                            Box(
                                modifier = Modifier.zIndex(if (tab.selected) 0f else 1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (tab.selected) {
                                    selectionPill(this)
                                }
                                val contentColor by animateColorAsState(
                                    targetValue = if (tab.selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    animationSpec = AsteriskMotion.fastEffects(),
                                    label = "compact-navigation-item-color",
                                )
                                Column(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .selectable(
                                            selected = tab.selected,
                                            onClick = tab.onClick,
                                            role = Role.Tab,
                                            indication = ripple(),
                                            interactionSource = remember { MutableInteractionSource() },
                                        )
                                        .padding(horizontal = tabHorizontalPadding, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(24.dp),
                                        tint = contentColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            trailingAction?.invoke()
        }
    }
}
