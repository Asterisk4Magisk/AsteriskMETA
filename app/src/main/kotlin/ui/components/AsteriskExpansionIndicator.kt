// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import ui.icons.AsteriskIcons as Icons
import ui.theme.AsteriskMotion

@Composable
internal fun AsteriskExpansionIndicator(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = AsteriskMotion.fastEffects(),
        label = "asterisk-expansion-indicator",
    )
    Icon(
        imageVector = Icons.Rounded.ExpandMore,
        contentDescription = contentDescription,
        modifier = modifier.rotate(rotation),
        tint = tint,
    )
}
