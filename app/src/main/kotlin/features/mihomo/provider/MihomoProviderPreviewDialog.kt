// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.LocalAppServices
import app.R
import engine.mihomo.MihomoProviderRawContent
import features.mihomo.MihomoCodeEditorState
import features.mihomo.YamlCodeEditor
import kotlinx.coroutines.launch
import ui.clipboard.setPlainText
import ui.components.AsteriskActionButton
import ui.components.AsteriskStatusCard
import ui.text.formatTemplate
import utils.toReadableBytes

@Composable
internal fun MihomoProviderPreviewDialog(
    providerName: String,
    rawContent: MihomoProviderRawContent,
    onDismissRequest: () -> Unit,
) {
    val textContent = rawContent as? MihomoProviderRawContent.Text
    val content = textContent?.content.orEmpty()
    val clipboard = LocalClipboard.current
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.logs_copied_to_clipboard)
    val copyTextContent = {
        scope.launch {
            clipboard.setPlainText(content)
            services.tipNotifier.show(copiedMessage)
        }
        Unit
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 880.dp)
                .padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.mihomo_configuration_preview_title)
                            .formatTemplate("name" to providerName.ifBlank { "-" }),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (content.isNotBlank()) {
                        IconButton(onClick = copyTextContent) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.common_copy),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                when (rawContent) {
                    is MihomoProviderRawContent.Text -> ProviderTextPreviewContent(rawContent)
                    is MihomoProviderRawContent.Binary -> ProviderBinaryPreviewContent(rawContent)
                }
                AsteriskActionButton(
                    text = stringResource(R.string.common_complete),
                    icon = Icons.Rounded.Check,
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ProviderTextPreviewContent(
    rawContent: MihomoProviderRawContent.Text,
) {
    val content = rawContent.content
    val sourceLabel = stringResource(
        if (rawContent.declarationOnly) {
            R.string.mihomo_provider_raw_declaration
        } else {
            R.string.mihomo_provider_raw_read_only
        },
    )
    val metadata = stringResource(
        R.string.mihomo_provider_raw_metadata,
        sourceLabel,
        content.toByteArray().size.toLong().toReadableBytes(),
    )
    val previewEditorState = remember(content) {
        MihomoCodeEditorState(content).also { state ->
            state.replaceText(content, placeCursorAtEnd = false)
        }
    }

    Text(
        text = metadata,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    if (rawContent.lastError.isNotBlank()) {
        ProviderMessageCard(text = rawContent.lastError)
        if (content.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
    if (content.isNotBlank() || rawContent.lastError.isBlank()) {
        YamlCodeEditor(
            label = stringResource(R.string.mihomo_provider_file_content),
            state = previewEditorState,
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 560.dp),
        )
    }
}

@Composable
private fun ProviderBinaryPreviewContent(
    rawContent: MihomoProviderRawContent.Binary,
) {
    val sourceLabel = stringResource(R.string.mihomo_rule_provider_binary_preview)
    val formatMetadata = stringResource(
        R.string.mihomo_provider_raw_metadata,
        sourceLabel,
        rawContent.format.ifBlank { "mrs" }.uppercase(),
    )
    val sizeMetadata = stringResource(
        R.string.mihomo_provider_raw_metadata,
        formatMetadata,
        rawContent.byteSize.toReadableBytes(),
    )
    val metadata = rawContent.ruleCount?.let { count ->
        stringResource(
            R.string.mihomo_provider_raw_metadata,
            sizeMetadata,
            pluralStringResource(R.plurals.mihomo_rule_provider_rules_count, count, count),
        )
    } ?: sizeMetadata

    AsteriskStatusCard(status = metadata) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.mihomo_rule_provider_binary_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (rawContent.lastError.isNotBlank()) {
                Text(
                    text = rawContent.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
