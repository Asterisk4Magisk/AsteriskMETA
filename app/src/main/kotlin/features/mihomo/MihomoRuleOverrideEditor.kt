// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.LocalIsWideScreen
import app.MihomoOverrideRuleState
import app.MihomoRuleOverrideState
import app.MihomoSourceRuleEditAction
import app.MihomoSourceRuleEditState
import app.R
import app.nextEntryId
import app.normalized
import engine.mihomo.MihomoProfileCompilation
import engine.mihomo.MihomoRuleSourceInfo
import engine.mihomo.isMihomoMatchRule
import engine.mihomo.loadMihomoProfileObject
import engine.mihomo.toMihomoRuleSourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import ui.components.AsteriskActionButton
import ui.components.AsteriskCancelButton
import ui.components.AsteriskScaffold
import ui.components.AsteriskTopAppBar
import ui.components.SectionCardColor
import ui.components.SectionCardEntry
import ui.components.SectionCardFooter
import ui.components.SectionCardHeaderAction
import ui.components.SectionCardHeader
import ui.components.SectionCardItem
import ui.components.SectionCardPart
import ui.components.lazySectionCards
import ui.components.AsteriskInfoChip
import ui.components.AsteriskChipTone
import ui.components.animateDraggedCardLift
import ui.components.draggedCardLift
import ui.components.longPressReorderDragHandle
import ui.components.rememberAsteriskReorderableLazyListState
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.icons.AsteriskIcons as Icons
import ui.theme.AsteriskShapeTokens

/**
 * Edits a copy of a profile's rule override over the profile editor. Nothing is stored here:
 * Complete hands the copy back to the profile editor, whose own Save persists it.
 * Going back asks before discarding changes.
 *
 * [sourceContent] is the configuration the override applies to, possibly an unsaved draft; it
 * may be blank, in which case rules can still be written but source rules cannot be listed.
 */
@Composable
internal fun MihomoRuleOverrideEditorDialog(
    initial: MihomoRuleOverrideState,
    sourceContent: String,
    sourceIsCachedSubscription: Boolean,
    ruleModeActive: Boolean,
    compilePreview: suspend (MihomoRuleOverrideState) -> MihomoProfileCompilation,
    onConfirm: (MihomoRuleOverrideState) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val isWideScreen = LocalIsWideScreen.current
    var draft by remember { mutableStateOf(initial) }
    var editTarget by remember { mutableStateOf<RuleEditTarget?>(null) }
    var editSession by remember { mutableIntStateOf(0) }
    var importRegion by remember { mutableStateOf<RuleRegion?>(null) }
    var showSourceRules by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val source by produceState(RuleSource(RuleSourceStatus.Loading), sourceContent) {
        value = withContext(Dispatchers.Default) { sourceContent.toRuleSource() }
    }

    fun edit(target: RuleEditTarget) {
        editSession += 1
        editTarget = target
    }

    fun requestDismiss() {
        if (draft == initial) onDismissRequest() else confirmDiscard = true
    }

    val rows = remember(draft) { draft.toEditorRows() }
    val cardParts = remember(rows) { rows.mapNotNull { row -> row.cardPart?.let { part -> row.key to part } }.toMap() }
    val listState = rememberLazyListState()
    val reorderState = rememberAsteriskReorderableLazyListState(
        lazyListState = listState,
        itemCount = rows.size,
    ) { fromIndex, toIndex ->
        val from = rows[fromIndex] as? EditorRow.Rule ?: return@rememberAsteriskReorderableLazyListState
        val to = rows[toIndex] as? EditorRow.Rule ?: return@rememberAsteriskReorderableLazyListState
        // A rule is only reordered inside its own section.
        if (from.region == to.region) draft = draft.moveRule(from.region, from.rule.id, to.rule.id)
    }

    Dialog(
        onDismissRequest = ::requestDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        AsteriskScaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                AsteriskTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.mihomo_rule_override),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = ::requestDismiss) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        AsteriskActionButton(
                            text = stringResource(R.string.mihomo_rule_override_preview),
                            icon = Icons.Rounded.Visibility,
                            onClick = { showPreview = true },
                            enabled = source.status == RuleSourceStatus.Ready,
                        )
                        AsteriskActionButton(
                            text = stringResource(R.string.common_complete),
                            icon = Icons.Rounded.Check,
                            onClick = { onConfirm(draft.normalized()) },
                        )
                    },
                )
            },
        ) { innerPadding ->
            val contentPadding = pageListPadding(
                pageContentPaddingWithCutout(
                    innerPadding = innerPadding,
                    outerPadding = PaddingValues(),
                    isWideScreen = isWideScreen,
                ),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazySectionCards(listState, contentPadding, SectionCardColor) { key -> cardParts[key] },
                contentPadding = contentPadding,
            ) {
                // No spacing between items: the segments of one section card must touch. The list
                // paints the cards, so the segments below do not paint their own background.
                items(rows, key = EditorRow::key) { row ->
                    when (row) {
                        EditorRow.Master -> MasterSwitchCard(
                            enabled = draft.enabled,
                            ruleModeActive = ruleModeActive,
                            onEnabledChange = { enabled -> draft = draft.copy(enabled = enabled) },
                        )

                        is EditorRow.RegionHeader -> {
                            val earlyMatch = row.region == RuleRegion.Prepend && draft.hasEarlyMatch()
                            SectionCardHeader(
                                title = stringResource(row.region.titleRes),
                                count = draft.rulesOf(row.region).size,
                                description = stringResource(
                                    if (earlyMatch) R.string.mihomo_rule_override_early_match else row.region.summaryRes,
                                ),
                                descriptionIsWarning = earlyMatch,
                                paintBackground = false,
                            ) {
                                SectionCardHeaderAction(
                                    icon = Icons.Rounded.EditNote,
                                    contentDescription = stringResource(R.string.mihomo_rule_import_short),
                                    onClick = { importRegion = row.region },
                                )
                                SectionCardHeaderAction(
                                    icon = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.common_add),
                                    onClick = { edit(RuleEditTarget.New(row.region)) },
                                )
                            }
                        }

                        is EditorRow.Rule -> ReorderableItem(reorderState.reorderableState, key = row.key) { dragging ->
                            val lift = animateDraggedCardLift(dragging, label = "rule-override")
                            SectionCardItem(paintBackground = false) {
                                RuleCard(
                                    rule = row.rule,
                                    canMoveUp = row.index > 0,
                                    canMoveDown = row.index < row.count - 1,
                                    onClick = { edit(RuleEditTarget.Existing(row.region, row.rule)) },
                                    onEnabledChange = { enabled ->
                                        draft = draft.updateRule(row.region, row.rule.copy(enabled = enabled))
                                    },
                                    onMove = { delta -> draft = draft.shiftRule(row.region, row.rule.id, delta) },
                                    onDelete = { draft = draft.deleteRule(row.region, row.rule.id) },
                                    modifier = Modifier
                                        .draggedCardLift(lift, AsteriskShapeTokens.InnerContainerRadius)
                                        .longPressReorderDragHandle(
                                            this,
                                            enabled = row.count > 1,
                                            state = reorderState,
                                        ),
                                )
                            }
                        }

                        is EditorRow.Footer -> SectionCardFooter(
                            text = row.emptyRes?.let { stringResource(it) },
                            paintBackground = false,
                        )

                        EditorRow.SourceHeader -> SectionCardHeader(
                            title = stringResource(R.string.mihomo_rule_override_source_edits),
                            count = draft.sourceEdits.size,
                            description = source.statusText(sourceIsCachedSubscription),
                            paintBackground = false,
                        ) {
                            SectionCardHeaderAction(
                                icon = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.mihomo_rule_override_source_rules),
                                onClick = { showSourceRules = true },
                                enabled = source.status == RuleSourceStatus.Ready && source.info.rules.isNotEmpty(),
                            )
                        }

                        is EditorRow.SourceEdit -> SectionCardItem(paintBackground = false) {
                            SourceEditCard(
                                edit = row.edit,
                                matchCount = source.matchCountOf(row.edit),
                                onClick = { edit(RuleEditTarget.Replace(row.edit.sourceRule, row.edit)) },
                                onEnabledChange = { enabled ->
                                    draft = draft.copy(
                                        sourceEdits = draft.sourceEdits.map { edit ->
                                            if (edit.id == row.edit.id) edit.copy(enabled = enabled) else edit
                                        },
                                    )
                                },
                                onRestore = { draft = draft.restoreSourceRule(row.edit.sourceRule) },
                            )
                        }

                        EditorRow.Fallback -> FallbackSection(
                            matchTarget = draft.matchTarget.orEmpty(),
                            source = source.info,
                            onMatchTargetChange = { target -> draft = draft.copy(matchTarget = target) },
                        )
                    }
                }
            }
        }

        val target = editTarget
        MihomoRuleEditSheet(
            request = target?.let { current ->
                MihomoRuleEditRequest(
                    session = editSession,
                    title = stringResource(current.titleRes),
                    rawRule = current.rawRule,
                    remark = current.remark,
                )
            },
            sourceInfo = source.info,
            onConfirm = { rawRule, remark ->
                if (target != null) draft = draft.applyEdit(target, rawRule, remark)
                editTarget = null
            },
            onDismissRequest = { editTarget = null },
        )
        MihomoRuleImportSheet(
            show = importRegion != null,
            onConfirm = { rules ->
                importRegion?.let { region -> draft = draft.addRules(region, rules) }
                importRegion = null
            },
            onDismissRequest = { importRegion = null },
        )
        if (showSourceRules) {
            MihomoSourceRulePickerDialog(
                rules = source.info.rules,
                edits = draft.sourceEdits,
                onModify = { sourceRule ->
                    edit(RuleEditTarget.Replace(sourceRule, draft.sourceEditOf(sourceRule)))
                },
                onBlock = { sourceRule -> draft = draft.blockSourceRule(sourceRule) },
                onRestore = { sourceRule -> draft = draft.restoreSourceRule(sourceRule) },
                onDismissRequest = { showSourceRules = false },
            )
        }
        if (showPreview) {
            MihomoRuleOverridePreviewDialog(
                compile = { compilePreview(draft.normalized()) },
                sourceIsCachedSubscription = sourceIsCachedSubscription,
                onDismissRequest = { showPreview = false },
            )
        }
        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text(stringResource(R.string.mihomo_rule_override_discard_title)) },
                text = { Text(stringResource(R.string.mihomo_rule_override_discard_message)) },
                dismissButton = { AsteriskCancelButton(onClick = { confirmDiscard = false }) },
                confirmButton = {
                    AsteriskActionButton(
                        text = stringResource(R.string.mihomo_rule_override_discard_confirm),
                        icon = Icons.Rounded.Delete,
                        onClick = onDismissRequest,
                    )
                },
            )
        }
    }
}

@Composable
private fun MasterSwitchCard(
    enabled: Boolean,
    ruleModeActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(
        onClick = { onEnabledChange(!enabled) },
        modifier = Modifier.fillMaxWidth(),
        shape = AsteriskShapeTokens.ListCard,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.mihomo_rule_override_enable),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.mihomo_rule_override_order_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!ruleModeActive) {
                    Text(
                        text = stringResource(R.string.mihomo_rule_override_mode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun RuleCard(
    rule: MihomoOverrideRuleState,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onMove: (delta: Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val moveUpLabel = stringResource(R.string.mihomo_rule_move_up)
    val moveDownLabel = stringResource(R.string.mihomo_rule_move_down)
    val deleteLabel = stringResource(R.string.common_delete)
    SectionCardEntry(
        onClick = onClick,
        modifier = modifier.semantics {
            // Dragging is a gesture only; the same reordering is offered as actions.
            customActions = buildList {
                if (canMoveUp) add(CustomAccessibilityAction(moveUpLabel) { onMove(-1); true })
                if (canMoveDown) add(CustomAccessibilityAction(moveDownLabel) { onMove(1); true })
                add(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
            }
        },
    ) {
        RuleText(
            rule = rule.rawRule,
            remark = rule.remark,
            modifier = Modifier.weight(1f).alpha(if (rule.enabled) 1f else DisabledRuleAlpha),
        )
        Switch(
            checked = rule.enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.padding(start = 8.dp),
        )
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.mihomo_rule_actions))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                RuleMenuItem(moveUpLabel, Icons.Rounded.ArrowUpward, enabled = canMoveUp) {
                    menuExpanded = false
                    onMove(-1)
                }
                RuleMenuItem(moveDownLabel, Icons.Rounded.ArrowDownward, enabled = canMoveDown) {
                    menuExpanded = false
                    onMove(1)
                }
                RuleMenuItem(deleteLabel, Icons.Rounded.Delete) {
                    menuExpanded = false
                    onDelete()
                }
            }
        }
    }
}

@Composable
private fun RuleMenuItem(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun RuleText(
    rule: String,
    remark: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Text(
            text = rule,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (remark.isNotBlank()) {
            Text(
                text = remark,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SourceEditCard(
    edit: MihomoSourceRuleEditState,
    matchCount: Int?,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRestore: () -> Unit,
) {
    SectionCardEntry(onClick = onClick) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
                .alpha(if (edit.enabled) 1f else DisabledRuleAlpha),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceEditChip(edit.action)
                when (matchCount) {
                    null -> Unit
                    0 -> AsteriskInfoChip(
                        text = stringResource(R.string.mihomo_rule_override_edit_unmatched),
                        tone = AsteriskChipTone.Error,
                    )
                    1 -> Unit
                    else -> AsteriskInfoChip(stringResource(R.string.mihomo_rule_override_edit_affects, matchCount))
                }
            }
            RuleText(rule = edit.sourceRule, remark = "")
            if (edit.action == MihomoSourceRuleEditAction.Replace) {
                RuleText(rule = "→ ${edit.replacementRule}", remark = edit.remark)
            }
        }
        Switch(
            checked = edit.enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.padding(start = 8.dp),
        )
        IconButton(onClick = onRestore) {
            Icon(Icons.Rounded.Refresh, stringResource(R.string.mihomo_rule_override_restore))
        }
    }
}

@Composable
internal fun SourceEditChip(action: MihomoSourceRuleEditAction) {
    when (action) {
        MihomoSourceRuleEditAction.Remove -> AsteriskInfoChip(
            text = stringResource(R.string.mihomo_rule_override_blocked),
            tone = AsteriskChipTone.Tertiary,
        )
        MihomoSourceRuleEditAction.Replace -> AsteriskInfoChip(
            text = stringResource(R.string.mihomo_rule_override_modified),
            tone = AsteriskChipTone.Primary,
        )
    }
}

/** A section card of its own: it is never long, so it is one list item. */
@Composable
private fun FallbackSection(
    matchTarget: String,
    source: MihomoRuleSourceInfo,
    onMatchTargetChange: (String) -> Unit,
) {
    Column {
        SectionCardHeader(
            title = stringResource(R.string.mihomo_rule_override_fallback),
            description = stringResource(R.string.mihomo_rule_override_fallback_summary),
        )
        SectionCardItem {
            SuggestionTextField(
                value = matchTarget,
                onValueChange = onMatchTargetChange,
                label = stringResource(R.string.mihomo_rule_override_fallback_target),
                suggestions = source.targets,
                supportingText = source.matchTarget
                    ?.let { target -> stringResource(R.string.mihomo_rule_override_fallback_keep, target) }
                    ?: stringResource(R.string.mihomo_rule_override_fallback_none),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SectionCardFooter()
    }
}

internal enum class RuleRegion(val titleRes: Int, val summaryRes: Int, val emptyRes: Int) {
    Prepend(
        R.string.mihomo_rule_override_prepend,
        R.string.mihomo_rule_override_prepend_summary,
        R.string.mihomo_rule_override_prepend_empty,
    ),
    BeforeMatch(
        R.string.mihomo_rule_override_before_match,
        R.string.mihomo_rule_override_before_match_summary,
        R.string.mihomo_rule_override_before_match_empty,
    ),
}

private sealed interface RuleEditTarget {
    val titleRes: Int
    val rawRule: String
    val remark: String

    data class New(val region: RuleRegion) : RuleEditTarget {
        override val titleRes = R.string.mihomo_rule_add
        override val rawRule = ""
        override val remark = ""
    }

    data class Existing(val region: RuleRegion, val rule: MihomoOverrideRuleState) : RuleEditTarget {
        override val titleRes = R.string.mihomo_rule_edit
        override val rawRule = rule.rawRule
        override val remark = rule.remark
    }

    /** Replaces every source rule that reads [sourceRule]; [edit] is its current edit, if any. */
    data class Replace(val sourceRule: String, val edit: MihomoSourceRuleEditState?) : RuleEditTarget {
        override val titleRes = R.string.mihomo_rule_override_modify_source
        override val rawRule = edit?.replacementRule?.takeIf(String::isNotBlank) ?: sourceRule
        override val remark = edit?.remark.orEmpty()
    }
}

private sealed interface EditorRow {
    val key: String

    /** The section card this row is part of, painted by the list; `null` outside the cards. */
    val cardPart: Pair<String, SectionCardPart>? get() = null

    data object Master : EditorRow {
        override val key = "master"
    }

    data class RegionHeader(val region: RuleRegion) : EditorRow {
        override val key = "header:${region.name}"
        override val cardPart = region.name to SectionCardPart.Header
    }

    data class Rule(val region: RuleRegion, val rule: MihomoOverrideRuleState, val index: Int, val count: Int) : EditorRow {
        override val key = "rule:${rule.id}"
        override val cardPart = region.name to SectionCardPart.Item
    }

    /** The bottom of a section card; [emptyRes] is shown when the section has no entries. */
    data class Footer(val owner: String, val emptyRes: Int?) : EditorRow {
        override val key = "footer:$owner"
        override val cardPart = owner to SectionCardPart.Footer
    }

    data object SourceHeader : EditorRow {
        override val key = "header:source"
        override val cardPart = SourceCard to SectionCardPart.Header
    }

    data class SourceEdit(val edit: MihomoSourceRuleEditState) : EditorRow {
        override val key = "edit:${edit.id}"
        override val cardPart = SourceCard to SectionCardPart.Item
    }

    data object Fallback : EditorRow {
        override val key = "fallback"
    }
}

/** The editor follows the order the rules take in the compiled configuration. */
private fun MihomoRuleOverrideState.toEditorRows(): List<EditorRow> = buildList {
    add(EditorRow.Master)
    addRegion(RuleRegion.Prepend, prependRules)
    add(EditorRow.SourceHeader)
    sourceEdits.forEach { edit -> add(EditorRow.SourceEdit(edit)) }
    add(EditorRow.Footer(SourceCard, R.string.mihomo_rule_override_source_edits_empty.takeIf { sourceEdits.isEmpty() }))
    addRegion(RuleRegion.BeforeMatch, beforeMatchRules)
    add(EditorRow.Fallback)
}

private fun MutableList<EditorRow>.addRegion(region: RuleRegion, rules: List<MihomoOverrideRuleState>) {
    add(EditorRow.RegionHeader(region))
    rules.forEachIndexed { index, rule -> add(EditorRow.Rule(region, rule, index, rules.size)) }
    add(EditorRow.Footer(region.name, region.emptyRes.takeIf { rules.isEmpty() }))
}

internal enum class RuleSourceStatus { Loading, Missing, Invalid, Ready }

internal data class RuleSource(
    val status: RuleSourceStatus,
    val info: MihomoRuleSourceInfo = MihomoRuleSourceInfo(),
) {
    private val ruleCounts: Map<String, Int> by lazy { info.rules.groupingBy(String::trim).eachCount() }

    /** How many source rules [edit] changes, or `null` while the source is not known. */
    fun matchCountOf(edit: MihomoSourceRuleEditState): Int? =
        if (status == RuleSourceStatus.Ready) ruleCounts[edit.sourceRule.trim()] ?: 0 else null
}

private fun String.toRuleSource(): RuleSource {
    if (isBlank()) return RuleSource(RuleSourceStatus.Missing)
    val profile = runCatching { loadMihomoProfileObject() }.getOrNull()
        ?: return RuleSource(RuleSourceStatus.Invalid)
    return RuleSource(RuleSourceStatus.Ready, profile.toMihomoRuleSourceInfo())
}

@Composable
private fun RuleSource.statusText(cachedSubscription: Boolean): String = when (status) {
    RuleSourceStatus.Loading -> ""
    RuleSourceStatus.Missing -> stringResource(R.string.mihomo_rule_override_source_waiting)
    RuleSourceStatus.Invalid -> stringResource(R.string.mihomo_rule_override_source_invalid)
    RuleSourceStatus.Ready -> stringResource(
        if (cachedSubscription) {
            R.string.mihomo_rule_override_source_count_cached
        } else {
            R.string.mihomo_rule_override_source_count
        },
        info.rules.size,
    )
}

private fun MihomoRuleOverrideState.rulesOf(region: RuleRegion): List<MihomoOverrideRuleState> = when (region) {
    RuleRegion.Prepend -> prependRules
    RuleRegion.BeforeMatch -> beforeMatchRules
}

private fun MihomoRuleOverrideState.withRules(
    region: RuleRegion,
    rules: List<MihomoOverrideRuleState>,
): MihomoRuleOverrideState = when (region) {
    RuleRegion.Prepend -> copy(prependRules = rules)
    RuleRegion.BeforeMatch -> copy(beforeMatchRules = rules)
}

internal fun MihomoRuleOverrideState.addRules(region: RuleRegion, rawRules: List<String>): MihomoRuleOverrideState {
    var next = this
    rawRules.forEach { rawRule ->
        next = next.withRules(region, next.rulesOf(region) + MihomoOverrideRuleState(next.nextEntryId(), rawRule = rawRule))
    }
    return next
}

private fun MihomoRuleOverrideState.updateRule(region: RuleRegion, rule: MihomoOverrideRuleState) =
    withRules(region, rulesOf(region).map { item -> if (item.id == rule.id) rule else item })

private fun MihomoRuleOverrideState.deleteRule(region: RuleRegion, id: Int) =
    withRules(region, rulesOf(region).filterNot { item -> item.id == id })

/** Moves the rule [id] to the position of the rule [targetId] of the same region. */
internal fun MihomoRuleOverrideState.moveRule(region: RuleRegion, id: Int, targetId: Int): MihomoRuleOverrideState {
    val rules = rulesOf(region).toMutableList()
    val from = rules.indexOfFirst { item -> item.id == id }
    val to = rules.indexOfFirst { item -> item.id == targetId }
    if (from < 0 || to < 0 || from == to) return this
    rules.add(to, rules.removeAt(from))
    return withRules(region, rules)
}

private fun MihomoRuleOverrideState.shiftRule(region: RuleRegion, id: Int, delta: Int): MihomoRuleOverrideState {
    val rules = rulesOf(region)
    val target = rules.getOrNull(rules.indexOfFirst { item -> item.id == id } + delta) ?: return this
    return moveRule(region, id, target.id)
}

internal fun MihomoRuleOverrideState.sourceEditOf(sourceRule: String): MihomoSourceRuleEditState? =
    sourceEdits.firstOrNull { edit -> edit.sourceRule.trim() == sourceRule.trim() }

/** One source rule has at most one edit: a later edit replaces the earlier one. */
private fun MihomoRuleOverrideState.putSourceEdit(
    sourceRule: String,
    action: MihomoSourceRuleEditAction,
    replacementRule: String = "",
    remark: String = "",
): MihomoRuleOverrideState {
    val existing = sourceEditOf(sourceRule)
    val edit = MihomoSourceRuleEditState(
        id = existing?.id ?: nextEntryId(),
        enabled = true,
        sourceRule = sourceRule.trim(),
        action = action,
        replacementRule = replacementRule,
        remark = remark,
    )
    return copy(
        sourceEdits = if (existing == null) sourceEdits + edit else sourceEdits.map { item ->
            if (item.id == existing.id) edit else item
        },
    )
}

internal fun MihomoRuleOverrideState.blockSourceRule(sourceRule: String) =
    putSourceEdit(sourceRule, MihomoSourceRuleEditAction.Remove)

internal fun MihomoRuleOverrideState.restoreSourceRule(sourceRule: String) =
    copy(sourceEdits = sourceEdits.filterNot { edit -> edit.sourceRule.trim() == sourceRule.trim() })

private fun MihomoRuleOverrideState.applyEdit(
    target: RuleEditTarget,
    rawRule: String,
    remark: String,
): MihomoRuleOverrideState = when (target) {
    is RuleEditTarget.New -> withRules(
        target.region,
        rulesOf(target.region) + MihomoOverrideRuleState(nextEntryId(), rawRule = rawRule, remark = remark),
    )
    is RuleEditTarget.Existing -> updateRule(target.region, target.rule.copy(rawRule = rawRule, remark = remark))
    // Writing the source rule back unchanged is not an edit.
    is RuleEditTarget.Replace -> if (rawRule == target.sourceRule.trim()) {
        restoreSourceRule(target.sourceRule)
    } else {
        putSourceEdit(target.sourceRule, MihomoSourceRuleEditAction.Replace, rawRule, remark)
    }
}

/** Whether the first rule a user wrote is a `MATCH`, which hides every rule after it. */
internal fun MihomoRuleOverrideState.hasEarlyMatch(): Boolean =
    prependRules.any { rule -> rule.enabled && rule.rawRule.isMihomoMatchRule() }

private const val DisabledRuleAlpha = 0.5f

private const val SourceCard = "source"
