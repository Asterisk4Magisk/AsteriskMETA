// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ConnectionTestConcurrentBounds
import app.ConnectionTestTimeoutBounds
import app.ConnectionTestTimeoutStepMillis
import app.R
import features.settings.ConnectionTestSettingsDraft
import app.isValidConnectionTestUrl
import kotlin.math.roundToInt
import ui.icons.AsteriskIcons as Icons

/** Edits a draft; only the explicit save action publishes settings. */
@Composable
internal fun ConnectionTestSettingsBottomSheet(
    show: Boolean,
    overridesEnabled: Boolean,
    draft: ConnectionTestSettingsDraft,
    onDraftChange: (ConnectionTestSettingsDraft) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (ConnectionTestSettingsDraft) -> Unit,
) {
    val urlError = if (isValidConnectionTestUrl(draft.url)) {
        null
    } else {
        stringResource(R.string.connection_test_url_invalid)
    }
    SettingsModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.connection_test_settings_title),
        startAction = {
            TextButton(
                text = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
        endAction = {
            TextButton(
                text = stringResource(R.string.common_save),
                icon = Icons.Rounded.Save,
                enabled = urlError == null,
                onClick = { if (urlError == null) onSave(draft.copy(url = draft.url.trim())) },
            )
        },
    ) {
        key(show) {
            SettingsSheetContent {
                Text(
                    text = stringResource(R.string.connection_test_scope_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                SettingsTextField(
                    value = draft.url,
                    onValueChange = { onDraftChange(draft.copy(url = it)) },
                    label = stringResource(R.string.connection_test_url),
                    errorText = urlError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                )
                IntSliderPreference(
                    title = stringResource(R.string.connection_test_concurrent),
                    value = draft.concurrent,
                    onValueChange = { onDraftChange(draft.copy(concurrent = it)) },
                    valueRange = ConnectionTestConcurrentBounds.min..ConnectionTestConcurrentBounds.max,
                    // One stop per integer across 1..32.
                    steps = ConnectionTestConcurrentBounds.max - ConnectionTestConcurrentBounds.min - 1,
                )

                IntSliderPreference(
                    title = stringResource(R.string.connection_test_timeout),
                    value = draft.timeoutMillis,
                    onValueChange = { onDraftChange(draft.copy(timeoutMillis = it)) },
                    valueRange = ConnectionTestTimeoutBounds.min..ConnectionTestTimeoutBounds.max,
                    steps = (ConnectionTestTimeoutBounds.max - ConnectionTestTimeoutBounds.min) /
                        ConnectionTestTimeoutStepMillis - 1,
                )
                SwitchPreference(
                    title = stringResource(R.string.connection_test_unified_delay),
                    icon = Icons.Rounded.Speed,
                    summary = stringResource(
                        if (overridesEnabled) R.string.connection_test_unified_delay_summary
                        else R.string.connection_test_unified_delay_raw_summary,
                    ),
                    checked = draft.unifiedDelay,
                    enabled = overridesEnabled,
                    onCheckedChange = { onDraftChange(draft.copy(unifiedDelay = it)) },
                )
            }
        }
    }
}

// Keep the slider controlled by the draft so saving always uses the displayed value.
@Suppress("DEPRECATION")
@Composable
private fun IntSliderPreference(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    steps: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(valueRange)) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = steps.coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
