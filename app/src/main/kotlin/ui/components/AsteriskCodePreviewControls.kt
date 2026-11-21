// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.theme.AsteriskMotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AsteriskCodeActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier.semantics { contentDescription = label },
            enabled = enabled && !loading,
        ) {
            val loadingMotion = AsteriskMotion.fastEffects<Float>()
            AnimatedContent(
                targetState = loading,
                transitionSpec = AsteriskMotion.fadeThrough(loadingMotion),
                label = "code-action-loading",
            ) { isLoading ->
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(icon, contentDescription = null)
                }
            }
        }
    }
}

@Composable
internal fun AsteriskCodePreviewTabs(
    showOutput: Boolean,
    onShowOutputChange: (Boolean) -> Unit,
    consoleLabel: String,
    outputLabel: String,
) {
    PrimaryTabRow(
        selectedTabIndex = if (showOutput) 1 else 0,
        containerColor = Color.Transparent,
    ) {
        Tab(
            selected = !showOutput,
            onClick = { onShowOutputChange(false) },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            text = { Text(consoleLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
        Tab(
            selected = showOutput,
            onClick = { onShowOutputChange(true) },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            text = { Text(outputLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}
