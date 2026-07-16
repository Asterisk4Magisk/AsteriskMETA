// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.R
import ui.clipboard.ClipboardImportMode
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun ImportModeDialog(
    show: Boolean,
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    onModeSelected: (ClipboardImportMode) -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsteriskActionButton(
                    text = stringResource(R.string.common_merge_import),
                    icon = Icons.Rounded.Add,
                    onClick = { onModeSelected(ClipboardImportMode.Merge) },
                )
                AsteriskActionButton(
                    text = stringResource(R.string.common_replace_existing),
                    icon = Icons.Rounded.Sync,
                    onClick = { onModeSelected(ClipboardImportMode.Replace) },
                )
            }
        },
        dismissButton = {
            AsteriskActionButton(
                text = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
    )
}
