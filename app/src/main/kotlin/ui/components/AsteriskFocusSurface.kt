// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.theme.ExpressiveInteractionState
import ui.theme.FocusDensity
import ui.theme.FocusTone
import ui.theme.focusShapeRole

private const val PrimaryActionStackFontScale = 1.3f

internal fun shouldStackPrimaryAction(
    fontScale: Float,
    keepPrimaryActionInline: Boolean,
): Boolean = !keepPrimaryActionInline && fontScale >= PrimaryActionStackFontScale

@Composable
internal fun focusAccentColor(tone: FocusTone): Color = when (tone) {
    FocusTone.Primary -> MaterialTheme.colorScheme.primary
    FocusTone.Inactive -> MaterialTheme.colorScheme.onSurfaceVariant
    FocusTone.Warning -> MaterialTheme.colorScheme.tertiary
    FocusTone.Error -> MaterialTheme.colorScheme.error
    FocusTone.ReadOnly -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun RowScope.FocusTitleAndSummary(
    title: String,
    summary: String?,
    contentColor: Color,
    summaryStyle: TextStyle,
    summaryContentDescription: String? = null,
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (summary != null) {
            Text(
                text = summary,
                modifier = if (summaryContentDescription != null) {
                    Modifier.semantics { contentDescription = summaryContentDescription }
                } else {
                    Modifier
                },
                style = summaryStyle,
                color = contentColor.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AsteriskFocusSurface(
    title: String,
    modifier: Modifier = Modifier,
    density: FocusDensity = FocusDensity.Medium,
    tone: FocusTone = FocusTone.Primary,
    summary: String? = null,
    summaryStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    summaryContentDescription: String? = null,
    stateIcon: ImageVector? = null,
    shapeOverride: Shape? = null,
    metrics: (@Composable RowScope.() -> Unit)? = null,
    primaryAction: (@Composable BoxScope.() -> Unit)? = null,
    keepPrimaryActionInline: Boolean = false,
    forcePrimaryActionStacked: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    stateIconTint: Color? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val shape = shapeOverride ?: rememberExpressiveShape(
        role = focusShapeRole(density),
        state = ExpressiveInteractionState.Rest,
    )
    val accentColor = focusAccentColor(tone)
    val stackPrimaryAction = forcePrimaryActionStacked || shouldStackPrimaryAction(
        fontScale = LocalDensity.current.fontScale,
        keepPrimaryActionInline = keepPrimaryActionInline,
    )
    val horizontalPadding = when (density) {
        FocusDensity.Large -> 24.dp
        FocusDensity.Medium -> 20.dp
        FocusDensity.Compact -> 16.dp
    }
    // Tighter top/bottom padding leaves room for the runtime line under the
    // status title without growing the card.
    val verticalPadding = when (density) {
        FocusDensity.Large -> 10.dp
        FocusDensity.Medium -> 16.dp
        FocusDensity.Compact -> 12.dp
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    // Centre-aligned: the trailing action is a single chip, so
                    // its centre matches the state icon's centre regardless of
                    // how many lines the title column has.
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (stateIcon != null) {
                        Icon(
                            imageVector = stateIcon,
                            contentDescription = null,
                            modifier = Modifier.size(if (density == FocusDensity.Large) 32.dp else 28.dp),
                            tint = stateIconTint ?: accentColor,
                        )
                    }
                    FocusTitleAndSummary(
                        title = title,
                        summary = summary,
                        contentColor = contentColor,
                        summaryStyle = summaryStyle,
                        summaryContentDescription = summaryContentDescription,
                    )
                    if (!stackPrimaryAction && primaryAction != null) {
                        Box(content = primaryAction)
                    }
                }
                if (stackPrimaryAction && primaryAction != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd,
                        content = primaryAction,
                    )
                }
            }
            if (metrics != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = metrics,
                )
            }
            if (content != null) {
                content()
            }
        }
    }
}
