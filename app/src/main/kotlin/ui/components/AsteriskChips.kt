// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.theme.AsteriskMotion
import ui.theme.AsteriskShapeTokens

@Composable
internal fun AsteriskFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    InteractiveChip(
        text = label,
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
    )
}

@Composable
internal fun AsteriskInfoChip(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    tone: AsteriskChipTone = if (emphasized) AsteriskChipTone.Primary else AsteriskChipTone.Neutral,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val containerColor = when (tone) {
        AsteriskChipTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
        AsteriskChipTone.Primary -> MaterialTheme.colorScheme.primaryContainer
        AsteriskChipTone.Secondary -> MaterialTheme.colorScheme.secondaryContainer
        AsteriskChipTone.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer
        AsteriskChipTone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (tone) {
        AsteriskChipTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        AsteriskChipTone.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
        AsteriskChipTone.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
        AsteriskChipTone.Tertiary -> MaterialTheme.colorScheme.onTertiaryContainer
        AsteriskChipTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        modifier = modifier.height(28.dp),
        shape = AsteriskShapeTokens.Pill,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InteractiveChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    leadingIcon: (@Composable () -> Unit)?,
) {
    val containerColor = animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = AsteriskMotion.effects(),
        label = "chip-container",
    )
    val contentColor = animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = AsteriskMotion.effects(),
        label = "chip-content",
    )
    Box(
        modifier = modifier.heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(32.dp),
            shape = AsteriskShapeTokens.Pill,
            color = containerColor.value,
            contentColor = contentColor.value,
            border = BorderStroke(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.let { icon ->
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        icon()
                    }
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal enum class AsteriskChipTone {
    Neutral,
    Primary,
    Secondary,
    Tertiary,
    Error,
}
