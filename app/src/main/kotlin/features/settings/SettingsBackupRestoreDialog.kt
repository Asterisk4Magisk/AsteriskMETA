// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import data.backup.AppBackupRestorePreview
import data.backup.AppBackupWarning
import app.R
import ui.components.WarningConfirmDialog
import ui.text.formatTemplate
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SettingsRestoreConfirmDialog(
    preview: AppBackupRestorePreview?,
    busy: Boolean,
    onDismissRequest: () -> Unit,
    onRestore: () -> Unit,
) {
    val backup = preview?.backup
    val creationTime = backup?.createdAtMillis?.let(::formatBackupCreationTime).orEmpty()
    WarningConfirmDialog(
        show = preview != null,
        title = stringResource(R.string.settings_restore_confirm_title),
        summary = stringResource(R.string.settings_restore_confirm_summary),
        dismissText = stringResource(R.string.common_cancel),
        confirmText = stringResource(R.string.common_restore),
        onDismissRequest = onDismissRequest,
        onConfirm = onRestore,
        busy = busy,
    ) {
        if (preview == null || backup == null) return@WarningConfirmDialog
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_restore_backup_version).formatTemplate(
                    "version" to backup.version,
                    "appVersion" to backup.appVersionName,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_restore_backup_created_at).formatTemplate(
                    "time" to creationTime,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_restore_backup_counts).formatTemplate(
                    "profiles" to preview.mihomoProfileCount,
                    "overrideScripts" to preview.mihomoOverrideScriptCount,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            preview.warnings.forEach { warning ->
                Text(
                    text = warning.localizedMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun AppBackupWarning.localizedMessage(): String =
    when (this) {
        is AppBackupWarning.MissingOverrideScriptReferences -> {
            stringResource(R.string.settings_restore_warning_missing_override_script_references)
                .formatTemplate("count" to count)
        }
        is AppBackupWarning.EmptyProfileContents -> {
            stringResource(R.string.settings_restore_warning_empty_profile_contents)
                .formatTemplate("count" to count)
        }
    }

private fun formatBackupCreationTime(createdAtMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(createdAtMillis))
