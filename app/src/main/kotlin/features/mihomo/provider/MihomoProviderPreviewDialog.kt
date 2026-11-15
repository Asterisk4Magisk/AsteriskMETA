// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.R
import engine.mihomo.MihomoProviderRawContent
import engine.mihomo.MihomoRuleSetMrs
import features.mihomo.MihomoCodeEditorState
import features.mihomo.YamlCodeEditor
import ui.components.AsteriskCodeCopyButton
import ui.components.AsteriskCodePreviewDialog
import ui.components.AsteriskCodeSearchState
import ui.components.AsteriskStatusCard
import ui.components.AsteriskTopBarControls
import ui.components.rememberCodePreviewDocument
import ui.layout.rememberCodeEditorFocusState
import ui.theme.AsteriskMotion
import ui.icons.AsteriskIcons as Icons

/** Provider-specific decoding and diagnostics use the shared full-screen code preview. */
@Composable
internal fun MihomoProviderPreviewDialog(
    providerName: String,
    loading: Boolean,
    rawContent: MihomoProviderRawContent?,
    onDismissRequest: () -> Unit,
) {
    val textContent = rawContent as? MihomoProviderRawContent.Text
    val content = textContent?.content.orEmpty()
    val document = rememberCodePreviewDocument(content)
    val editorState = remember(document) { document?.let { MihomoCodeEditorState(it, placeCursorAtEnd = false) } }
    val editorFocus = rememberCodeEditorFocusState(
        editorFocused = editorState?.isFocused == true,
        clearEditorFocus = { editorState?.clearFocus() },
    )
    val metadata = providerPreviewMetadata(rawContent)
    val searchAvailable = textContent != null && editorState != null
    AsteriskCodePreviewDialog(
        title = providerName.ifBlank { ProviderPreviewFallbackName },
        onDismissRequest = onDismissRequest,
        searchAvailable = searchAvailable,
        focusState = editorFocus,
        actions = { if (searchAvailable) AsteriskCodeCopyButton(editorState::snapshotText) },
        topBarContent = {
            if (metadata.isNotBlank()) {
                AsteriskTopBarControls {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) { searchState, editorModifier ->
        when {
            loading || document == null -> ProviderPreviewLoading()
            textContent != null && editorState != null -> ProviderPreviewTextContent(
                errorMessage = textContent.lastError,
                editorExpanded = editorFocus.expanded,
                state = editorState,
                searchState = searchState,
                modifier = editorModifier,
            )
            else -> ProviderPreviewBinaryContent(rawContent as? MihomoProviderRawContent.Binary)
        }
    }
}

@Composable
private fun ProviderPreviewLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            Text(
                text = stringResource(R.string.mihomo_provider_preview_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderPreviewBinaryContent(
    content: MihomoProviderRawContent.Binary?,
) {
    val metadata = providerPreviewMetadata(content)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        AsteriskStatusCard(status = metadata.takeIf { it.isNotBlank() }) {
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
                val lastError = content?.lastError.orEmpty()
                if (lastError.isNotBlank()) {
                    Text(
                        text = lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderPreviewTextContent(
    errorMessage: String,
    editorExpanded: Boolean,
    state: MihomoCodeEditorState,
    searchState: AsteriskCodeSearchState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = errorMessage.isNotBlank() && !editorExpanded,
            enter = AsteriskMotion.contentEnter(),
            exit = AsteriskMotion.contentExit(),
        ) {
            Column {
                ProviderMessageCard(text = errorMessage)
                Spacer(Modifier.height(8.dp))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            YamlCodeEditor(
                state = state,
                showFormatButton = false,
                searchState = searchState,
                modifier = Modifier.fillMaxSize().then(modifier),
            )
        }
    }
}

@Composable
private fun providerPreviewMetadata(
    rawContent: MihomoProviderRawContent?,
): String {
    return when (rawContent) {
        is MihomoProviderRawContent.Text -> when {
            // A decoded rule set is no longer the file the provider declares.
            rawContent.sourceFormat.equals(MihomoRuleSetMrs.Format, ignoreCase = true) ->
                stringResource(R.string.mihomo_provider_preview_decoded_mrs)

            rawContent.declarationOnly -> stringResource(R.string.mihomo_provider_raw_declaration)
            else -> ""
        }

        is MihomoProviderRawContent.Binary -> {
            var metadata = stringResource(
                R.string.mihomo_provider_raw_metadata,
                stringResource(R.string.mihomo_rule_provider_binary_preview),
                rawContent.format.ifBlank { MihomoRuleSetMrs.Format }.uppercase(),
            )
            rawContent.ruleCount?.let { count ->
                metadata = stringResource(
                    R.string.mihomo_provider_raw_metadata,
                    metadata,
                    pluralStringResource(R.plurals.mihomo_rule_provider_rules_count, count, count),
                )
            }
            metadata
        }

        null -> ""
    }
}

private const val ProviderPreviewFallbackName = "-"
