// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun WarningConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    dismissText: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    detailsMaxHeight: Dp = 240.dp,
    details: (@Composable ColumnScope.() -> Unit)? = null,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    text = summary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(14.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (details != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .heightIn(max = detailsMaxHeight)
                            .verticalScroll(rememberScrollState()),
                        content = details,
                    )
                }
            }
        },
        dismissButton = {
            AsteriskActionButton(
                text = dismissText,
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
        confirmButton = {
            AsteriskActionButton(
                text = confirmText,
                icon = Icons.Rounded.Check,
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            )
        },
    )
}
