// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import ui.icons.AsteriskIcons as Icons
import ui.theme.AsteriskMotion

private const val DisabledCheckboxAlpha = 0.38f

@Composable
internal fun AsteriskCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val enabledAlpha = if (enabled) 1f else DisabledCheckboxAlpha
    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = enabledAlpha)
        } else {
            Color.Transparent
        },
        animationSpec = AsteriskMotion.effects(),
        label = "checkbox-container",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = enabledAlpha)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = enabledAlpha)
        },
        animationSpec = AsteriskMotion.effects(),
        label = "checkbox-border",
    )
    val checkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = AsteriskMotion.fastSpatial(),
        label = "checkbox-check",
    )
    val interactionModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier.semantics {
            role = Role.Checkbox
            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            if (!enabled) disabled()
        }
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .then(interactionModifier),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = containerColor,
            border = BorderStroke(2.dp, borderColor),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary.copy(
                        alpha = checkProgress * enabledAlpha,
                    ),
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer {
                            val scale = 0.72f + (0.28f * checkProgress)
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }
    }
}
