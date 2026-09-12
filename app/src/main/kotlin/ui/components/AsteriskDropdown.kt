// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ui.icons.AsteriskIcons as Icons
import ui.theme.AsteriskMotion

/** Place inside the clickable trigger so the menu follows its arrow, not its label. */
@Composable
internal fun AsteriskDropdownAnchor(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    menuModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = AsteriskMotion.fastEffects(),
        label = "dropdown-arrow",
    )
    Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.rotate(rotation),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = menuModifier,
            content = content,
        )
    }
}

@Composable
internal fun AsteriskDropdownMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(text, color = contentColor.copy(alpha = if (enabled) 1f else 0.38f)) },
        modifier = Modifier.semantics { this.selected = selected },
        leadingIcon = {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = if (enabled) 1f else 0.38f),
                )
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        enabled = enabled,
        onClick = onClick,
    )
}
