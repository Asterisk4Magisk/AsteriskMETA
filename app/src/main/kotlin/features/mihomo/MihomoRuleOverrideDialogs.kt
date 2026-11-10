// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.MihomoSourceRuleEditState
import app.R
import engine.mihomo.MihomoProfileCompilation
import engine.mihomo.MihomoRuleReferenceIssue
import engine.mihomo.loadMihomoProfileObject
import engine.mihomo.mihomoRuleReferenceIssues
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ui.components.AsteriskActionButton
import ui.components.AsteriskChipTone
import ui.components.AsteriskFilterChip
import ui.components.AsteriskInfoChip
import ui.components.AsteriskSearchField
import ui.icons.AsteriskIcons as Icons

/**
 * Lists the rules of the source configuration so one can be modified, blocked or restored.
 * Rules with the same text are one entry: an edit always applies to all of them.
 */
@Composable
internal fun MihomoSourceRulePickerDialog(
    rules: List<String>,
    edits: List<MihomoSourceRuleEditState>,
    onModify: (String) -> Unit,
    onBlock: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val distinctRules = remember(rules) { rules.groupingBy(String::trim).eachCount().entries.toList() }
    val visibleRules = remember(distinctRules, query) {
        val needle = query.trim()
        if (needle.isEmpty()) distinctRules else distinctRules.filter { (rule, _) -> rule.contains(needle, ignoreCase = true) }
    }
    val editsBySource = remember(edits) { edits.associateBy { edit -> edit.sourceRule.trim() } }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Text(
                text = stringResource(R.string.mihomo_rule_override_source_rules),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            AsteriskSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.mihomo_rule_override_source_search),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                if (visibleRules.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.mihomo_rule_override_source_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
                items(visibleRules, key = { entry -> entry.key }) { (rule, count) ->
                    val edit = editsBySource[rule]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(onClickLabel = stringResource(R.string.mihomo_rule_override_modify)) { onModify(rule) }
                            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = rule,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (edit != null || count > 1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (edit != null) SourceEditChip(edit.action)
                                    if (count > 1) {
                                        AsteriskInfoChip(stringResource(R.string.mihomo_rule_override_edit_affects, count))
                                    }
                                }
                            }
                        }
                        if (edit == null) {
                            IconButton(onClick = { onBlock(rule) }) {
                                Icon(Icons.Rounded.Block, stringResource(R.string.mihomo_rule_override_block))
                            }
                        } else {
                            IconButton(onClick = { onRestore(rule) }) {
                                Icon(Icons.Rounded.Refresh, stringResource(R.string.mihomo_rule_override_restore))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                AsteriskActionButton(
                    text = stringResource(R.string.common_complete),
                    icon = Icons.Rounded.Check,
                    onClick = onDismissRequest,
                )
            }
        }
    }
}

private sealed interface RuleOverridePreview {
    data object Compiling : RuleOverridePreview

    class Compiled(
        val compilation: MihomoProfileCompilation,
        val referenceIssues: List<MihomoRuleReferenceIssue>,
    ) : RuleOverridePreview

    class Failed(val message: String) : RuleOverridePreview
}

/**
 * Compiles the draft once and shows both what the override script receives and the final
 * configuration. Nothing is written or loaded, so the result is not validated by a core.
 */
@Composable
internal fun MihomoRuleOverridePreviewDialog(
    compile: suspend () -> MihomoProfileCompilation,
    sourceIsCachedSubscription: Boolean,
    onDismissRequest: () -> Unit,
) {
    val preview by produceState<RuleOverridePreview>(RuleOverridePreview.Compiling) {
        value = withContext(Dispatchers.Default) {
            try {
                val compilation = compile()
                val issues = compilation.finalYaml.loadMihomoProfileObject()?.mihomoRuleReferenceIssues().orEmpty()
                RuleOverridePreview.Compiled(compilation, issues)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                RuleOverridePreview.Failed(error.message.orEmpty().ifBlank { error::class.java.simpleName })
            }
        }
    }
    var showFinal by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 880.dp)
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.mihomo_rule_override_preview),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    when (preview) {
                        RuleOverridePreview.Compiling -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        is RuleOverridePreview.Compiled -> AsteriskInfoChip(
                            text = stringResource(R.string.mihomo_override_script_debug_success),
                            tone = AsteriskChipTone.Primary,
                        )
                        is RuleOverridePreview.Failed -> AsteriskInfoChip(
                            text = stringResource(R.string.mihomo_override_script_debug_failed),
                            tone = AsteriskChipTone.Error,
                        )
                    }
                }
                when (val current = preview) {
                    RuleOverridePreview.Compiling -> Box(Modifier.weight(1f))

                    is RuleOverridePreview.Failed -> SelectionContainer(Modifier.weight(1f).padding(top = 12.dp)) {
                        Text(
                            text = current.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }

                    is RuleOverridePreview.Compiled -> {
                        PreviewNotes(current, sourceIsCachedSubscription)
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AsteriskFilterChip(
                                selected = !showFinal,
                                onClick = { showFinal = false },
                                label = stringResource(R.string.mihomo_rule_override_preview_after_override),
                            )
                            AsteriskFilterChip(
                                selected = showFinal,
                                onClick = { showFinal = true },
                                label = stringResource(R.string.mihomo_rule_override_preview_final),
                            )
                        }
                        val text = if (showFinal) current.compilation.finalYaml else current.compilation.scriptInputYaml
                        val editorState = remember(current, showFinal) {
                            MihomoCodeEditorState().also { state -> state.replaceText(text, placeCursorAtEnd = false) }
                        }
                        YamlCodeEditor(
                            state = editorState,
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AsteriskActionButton(
                        text = stringResource(R.string.common_complete),
                        icon = Icons.Rounded.Check,
                        onClick = onDismissRequest,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewNotes(
    preview: RuleOverridePreview.Compiled,
    sourceIsCachedSubscription: Boolean,
) {
    val diagnostics = preview.compilation.ruleDiagnostics
    val notes = buildList {
        if (sourceIsCachedSubscription) add(stringResource(R.string.mihomo_rule_override_preview_cached) to false)
        add(
            stringResource(
                R.string.mihomo_rule_override_preview_counts,
                diagnostics.sourceRuleCount,
                diagnostics.outputRuleCount,
            ) to false,
        )
        if (diagnostics.unmatchedSourceEditIds.isNotEmpty()) {
            add(
                stringResource(
                    R.string.mihomo_rule_override_preview_unmatched,
                    diagnostics.unmatchedSourceEditIds.size,
                ) to true,
            )
        }
        if (diagnostics.unreachableRuleCount > 0) {
            add(
                stringResource(R.string.mihomo_rule_override_preview_unreachable, diagnostics.unreachableRuleCount) to true,
            )
        }
        preview.referenceIssues.forEach { issue ->
            add(
                stringResource(
                    if (issue.isRuleProvider) {
                        R.string.mihomo_rule_override_preview_missing_provider
                    } else {
                        R.string.mihomo_rule_override_preview_missing_target
                    },
                    issue.missingName,
                    issue.rule,
                ) to true,
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 140.dp)
            .padding(top = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        notes.forEach { (text, warning) ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (warning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
