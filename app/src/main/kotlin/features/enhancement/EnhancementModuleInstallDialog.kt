// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.enhancement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.R
import ui.components.AsteriskActionButton

@Composable
internal fun EnhancementModuleInstallDialog(
    progress: EnhancementModuleInstallProgress,
    rebooting: Boolean,
    onReboot: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(progress.output, scroll.maxValue) {
        scroll.scrollTo(scroll.maxValue)
    }
    Dialog(
        onDismissRequest = { if (!progress.running && !rebooting) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !progress.running && !rebooting,
            dismissOnClickOutside = !progress.running && !rebooting,
        ),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.enhancement_module_install_title),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = enhancementModuleSummary(
                            progress.result ?: EnhancementModuleState(EnhancementModulePhase.Installing),
                        ),
                        modifier = Modifier.weight(1f).heightIn(max = 72.dp)
                            .verticalScroll(rememberScrollState()),
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (progress.result?.phase == EnhancementModulePhase.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (progress.running) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 360.dp, max = 560.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f)),
                ) {
                    Text(
                        text = progress.output.ifEmpty {
                            stringResource(R.string.enhancement_module_install_output_waiting)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (!progress.running && progress.result?.phase == EnhancementModulePhase.PendingReboot) {
                        AsteriskActionButton(
                            text = stringResource(R.string.enhancement_module_reboot_action),
                            icon = Icons.Rounded.RestartAlt,
                            onClick = onReboot,
                            enabled = !rebooting,
                        )
                    }
                    AsteriskActionButton(
                        text = stringResource(R.string.common_close),
                        icon = Icons.Rounded.Check,
                        onClick = onDismiss,
                        enabled = !progress.running && !rebooting,
                    )
                }
            }
        }
    }
}
