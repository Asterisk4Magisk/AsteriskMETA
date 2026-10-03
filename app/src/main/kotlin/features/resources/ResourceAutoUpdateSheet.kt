// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.R
import ui.components.AsteriskActionButton
import ui.components.AsteriskModalBottomSheet
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun ResourceAutoUpdateSheet(
    show: Boolean,
    enabled: Boolean,
    interval: String,
    onDismissRequest: () -> Unit,
    onSave: (Boolean, String) -> Unit,
) {
    var enabledDraft by rememberSaveable(show) { mutableStateOf(enabled) }
    var intervalDraft by rememberSaveable(show) { mutableStateOf(interval) }
    val valid = resourceAutoUpdateIntervalMillis(true, intervalDraft) != null
    AsteriskModalBottomSheet(
        show = show,
        title = stringResource(R.string.settings_title),
        onDismissRequest = onDismissRequest,
        startAction = {
            AsteriskActionButton(
                text = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
        endAction = {
            AsteriskActionButton(
                text = stringResource(R.string.common_save),
                icon = Icons.Rounded.Save,
                enabled = valid,
                onClick = { onSave(enabledDraft, intervalDraft.trim()) },
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .toggleable(enabledDraft, role = Role.Switch, onValueChange = { enabledDraft = it })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_resource_files_auto_update_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_resource_files_auto_update_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabledDraft, onCheckedChange = null)
            }
            OutlinedTextField(
                value = intervalDraft,
                onValueChange = { intervalDraft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_resource_files_auto_update_interval)) },
                supportingText = if (!valid) {
                    { Text(stringResource(R.string.common_error_update_interval)) }
                } else {
                    null
                },
                isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
    }
}
