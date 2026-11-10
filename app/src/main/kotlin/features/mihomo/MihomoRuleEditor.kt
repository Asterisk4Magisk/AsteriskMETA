// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.R
import engine.mihomo.MihomoFormRule
import engine.mihomo.MihomoFormRuleType
import engine.mihomo.MihomoRuleInputError
import engine.mihomo.MihomoRuleNetworks
import engine.mihomo.MihomoRuleSourceInfo
import engine.mihomo.hasKnownMihomoRuleType
import engine.mihomo.inputError
import engine.mihomo.mihomoRawRuleInputError
import engine.mihomo.parseMihomoRuleImport
import engine.mihomo.toMihomoFormRuleOrNull
import features.settings.SettingsDropdownRow
import ui.components.AsteriskActionButton
import ui.components.AsteriskCancelButton
import ui.components.AsteriskDropdownAnchor
import ui.components.AsteriskDropdownMenuItem
import ui.components.AsteriskModalBottomSheet
import ui.components.AsteriskSegmentItem
import ui.components.AsteriskSegmentedControl
import ui.icons.AsteriskIcons as Icons
import ui.theme.AsteriskShapeTokens
import ui.theme.AsteriskMotion

/**
 * One rule handed to [MihomoRuleEditSheet]; [rawRule] is blank for a new rule. [session] differs
 * for every opening, so reopening the same rule does not bring back what was typed and cancelled.
 */
internal data class MihomoRuleEditRequest(
    val session: Int,
    val title: String,
    val rawRule: String = "",
    val remark: String = "",
)

/**
 * Edits one rule as a form or as raw text. The form only takes rules it can read back without
 * losing anything; every other rule stays in raw text, which the core validates when it loads.
 */
@Composable
internal fun MihomoRuleEditSheet(
    request: MihomoRuleEditRequest?,
    sourceInfo: MihomoRuleSourceInfo,
    onConfirm: (rawRule: String, remark: String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // The sheet keeps showing the last request while it animates out.
    var shown by remember { mutableStateOf(request) }
    if (request != null) shown = request
    val current = shown ?: return

    key(current) {
        val initialForm = remember { current.rawRule.toMihomoFormRuleOrNull() }
        var rawMode by remember { mutableStateOf(current.rawRule.isNotBlank() && initialForm == null) }
        var type by remember { mutableStateOf(initialForm?.type ?: MihomoFormRuleType.DomainSuffix) }
        var payload by remember { mutableStateOf(initialForm?.payload.orEmpty()) }
        var target by remember { mutableStateOf(initialForm?.target.orEmpty()) }
        var noResolve by remember { mutableStateOf(initialForm?.noResolve ?: false) }
        var src by remember { mutableStateOf(initialForm?.src ?: false) }
        var rawText by remember { mutableStateOf(current.rawRule) }
        var remark by remember { mutableStateOf(current.remark) }
        var error by remember { mutableStateOf<MihomoRuleInputError?>(null) }
        var formUnavailable by remember { mutableStateOf(false) }

        fun formRule() = MihomoFormRule(type, payload, target, noResolve, src)

        fun selectMode(raw: Boolean) {
            if (raw == rawMode) return
            error = null
            formUnavailable = false
            if (raw) {
                if (payload.isNotBlank() || target.isNotBlank()) rawText = formRule().toRawRule()
                rawMode = true
                return
            }
            val parsed = rawText.toMihomoFormRuleOrNull()
            if (parsed == null && rawText.isNotBlank()) {
                // Switching would drop what the form cannot hold.
                formUnavailable = true
                return
            }
            if (parsed != null) {
                type = parsed.type
                payload = parsed.payload
                target = parsed.target
                noResolve = parsed.noResolve
                src = parsed.src
            }
            rawMode = false
        }

        fun confirm() {
            val rule = if (rawMode) rawText.trim() else formRule().toRawRule()
            error = if (rawMode) rule.mihomoRawRuleInputError() else formRule().inputError()
            if (error == null) onConfirm(rule, remark.trim())
        }

        AsteriskModalBottomSheet(
            show = request != null,
            onDismissRequest = onDismissRequest,
            title = current.title,
            startAction = { AsteriskCancelButton(onClick = onDismissRequest) },
            endAction = {
                AsteriskActionButton(
                    text = stringResource(R.string.common_complete),
                    icon = Icons.Rounded.Check,
                    onClick = ::confirm,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsteriskSegmentedControl(
                    items = listOf(
                        AsteriskSegmentItem(false, stringResource(R.string.mihomo_rule_mode_form)),
                        AsteriskSegmentItem(true, stringResource(R.string.mihomo_rule_mode_raw)),
                    ),
                    selectedValue = rawMode,
                    onSelected = ::selectMode,
                )
                AnimatedVisibility(
                    visible = formUnavailable,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                ) {
                    Text(
                        text = stringResource(R.string.mihomo_rule_form_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Column {
                    AnimatedVisibility(
                        visible = rawMode,
                        enter = AsteriskMotion.contentEnter(),
                        exit = AsteriskMotion.contentExit(),
                    ) {
                        OutlinedTextField(
                            shape = AsteriskShapeTokens.InnerContainer,
                            value = rawText,
                            onValueChange = { value ->
                                rawText = value
                                error = null
                                formUnavailable = false
                            },
                            label = { Text(stringResource(R.string.mihomo_rule_raw)) },
                            placeholder = { Text("DOMAIN-SUFFIX,example.com,DIRECT") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            isError = error != null,
                            supportingText = {
                                val message = error?.let { ruleInputErrorText(it) }
                                    ?: stringResource(R.string.mihomo_rule_unknown_type)
                                        .takeIf { rawText.contains(',') && !rawText.hasKnownMihomoRuleType() }
                                    ?: stringResource(R.string.mihomo_rule_raw_hint)
                                Text(message)
                            },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AnimatedVisibility(
                        visible = !rawMode,
                        enter = AsteriskMotion.contentEnter(),
                        exit = AsteriskMotion.contentExit(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsDropdownRow(
                                title = stringResource(R.string.mihomo_rule_type),
                                icon = Icons.Rounded.FilterAlt,
                                items = MihomoFormRuleType.entries.map(MihomoFormRuleType::keyword),
                                selectedIndex = type.ordinal,
                                onSelectedIndexChange = { index ->
                                    type = MihomoFormRuleType.entries[index]
                                    error = null
                                    if (type == MihomoFormRuleType.Network && payload.uppercase() !in MihomoRuleNetworks) {
                                        payload = MihomoRuleNetworks.first()
                                    }
                                },
                                horizontalPadding = 0.dp,
                            )
                            val payloadError = error?.takeIf { it != MihomoRuleInputError.TargetRequired }
                            when (type) {
                                MihomoFormRuleType.Network -> SettingsDropdownRow(
                                    title = stringResource(R.string.mihomo_rule_payload),
                                    icon = Icons.Rounded.Lan,
                                    items = MihomoRuleNetworks,
                                    selectedIndex = MihomoRuleNetworks.indexOf(payload.uppercase()).coerceAtLeast(0),
                                    onSelectedIndexChange = { index -> payload = MihomoRuleNetworks[index] },
                                    horizontalPadding = 0.dp,
                                )

                                else -> SuggestionTextField(
                                    value = payload,
                                    onValueChange = { value ->
                                        payload = value
                                        error = null
                                    },
                                    label = stringResource(
                                        if (type == MihomoFormRuleType.RuleSet) R.string.mihomo_rule_rule_set
                                        else R.string.mihomo_rule_payload,
                                    ),
                                    suggestions = if (type == MihomoFormRuleType.RuleSet) sourceInfo.ruleProviders else emptyList(),
                                    errorText = payloadError?.let { ruleInputErrorText(it) },
                                )
                            }
                            SuggestionTextField(
                                value = target,
                                onValueChange = { value ->
                                    target = value
                                    error = null
                                },
                                label = stringResource(R.string.mihomo_rule_target),
                                suggestions = sourceInfo.targets,
                                errorText = error?.takeIf { it == MihomoRuleInputError.TargetRequired }?.let { ruleInputErrorText(it) },
                            )
                            AnimatedVisibility(
                                visible = type.supportsIpParams,
                                enter = AsteriskMotion.contentEnter(),
                                exit = AsteriskMotion.contentExit(),
                            ) {
                                Column {
                                    RuleParameterSwitch(
                                        title = "src",
                                        summary = stringResource(R.string.mihomo_rule_src_summary),
                                        checked = src,
                                        onCheckedChange = { src = !src },
                                    )
                                    RuleParameterSwitch(
                                        title = "no-resolve",
                                        summary = stringResource(R.string.mihomo_rule_no_resolve_summary),
                                        checked = noResolve || src,
                                        enabled = !src,
                                        onCheckedChange = { noResolve = !noResolve },
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    shape = AsteriskShapeTokens.InnerContainer,
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text(stringResource(R.string.mihomo_rule_remark)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RuleParameterSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Switch, onClick = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp).alpha(if (enabled) 1f else 0.38f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A free text field that also offers names found in the configuration. */
@Composable
internal fun SuggestionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    // Typing narrows the list; an exact pick shows it whole again so it can be changed.
    val visible = remember(value, suggestions) {
        val query = value.trim()
        val matches = if (query.isEmpty() || query in suggestions) suggestions
        else suggestions.filter { name -> name.contains(query, ignoreCase = true) }
        matches.take(MaxVisibleSuggestions)
    }
    OutlinedTextField(
        shape = AsteriskShapeTokens.InnerContainer,
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = errorText != null,
        supportingText = (errorText ?: supportingText)?.let { message -> { Text(message) } },
        trailingIcon = if (visible.isEmpty()) {
            null
        } else {
            {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            role = Role.DropdownList,
                            onClickLabel = stringResource(R.string.mihomo_rule_choose),
                        ) { expanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    AsteriskDropdownAnchor(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        menuModifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
                    ) {
                        visible.forEach { name ->
                            AsteriskDropdownMenuItem(
                                text = name,
                                selected = name == value.trim(),
                                onClick = {
                                    expanded = false
                                    onValueChange(name)
                                },
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Adds pasted rules, one bare rule per line, in one step. */
@Composable
internal fun MihomoRuleImportSheet(
    show: Boolean,
    onConfirm: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var text by remember(show) { mutableStateOf("") }
    val parsed = remember(text) { text.parseMihomoRuleImport() }
    AsteriskModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.mihomo_rule_import),
        startAction = { AsteriskCancelButton(onClick = onDismissRequest) },
        endAction = {
            AsteriskActionButton(
                text = stringResource(R.string.common_add),
                icon = Icons.Rounded.Check,
                onClick = { onConfirm(parsed.rules) },
                enabled = parsed.rules.isNotEmpty() && parsed.errors.isEmpty(),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                shape = AsteriskShapeTokens.InnerContainer,
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.mihomo_rule_import_label)) },
                placeholder = { Text("DOMAIN-SUFFIX,example.com,DIRECT\nIP-CIDR,10.0.0.0/8,DIRECT,no-resolve") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                isError = parsed.errors.isNotEmpty(),
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (parsed.errors.isEmpty()) {
                    stringResource(R.string.mihomo_rule_import_hint, parsed.rules.size)
                } else {
                    stringResource(R.string.mihomo_rule_import_errors, parsed.errors.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (parsed.errors.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            parsed.errors.take(MaxVisibleImportErrors).forEach { (line, error) ->
                Text(
                    text = stringResource(R.string.mihomo_rule_import_error_line, line, ruleInputErrorText(error)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ruleInputErrorText(error: MihomoRuleInputError): String = stringResource(
    when (error) {
        MihomoRuleInputError.Blank -> R.string.mihomo_rule_error_blank
        MihomoRuleInputError.MultipleLines -> R.string.mihomo_rule_error_multiple_lines
        MihomoRuleInputError.YamlListSyntax -> R.string.mihomo_rule_error_yaml
        MihomoRuleInputError.MissingFields -> R.string.mihomo_rule_error_missing_fields
        MihomoRuleInputError.PayloadRequired -> R.string.mihomo_rule_error_payload_required
        MihomoRuleInputError.TargetRequired -> R.string.mihomo_rule_error_target_required
        MihomoRuleInputError.InvalidCidr -> R.string.mihomo_rule_error_cidr
        MihomoRuleInputError.InvalidPort -> R.string.mihomo_rule_error_port
        MihomoRuleInputError.InvalidUid -> R.string.mihomo_rule_error_uid
        MihomoRuleInputError.InvalidDscp -> R.string.mihomo_rule_error_dscp
        MihomoRuleInputError.InvalidNetwork -> R.string.mihomo_rule_error_network
        MihomoRuleInputError.CommaInField -> R.string.mihomo_rule_error_comma
    },
)

private const val MaxVisibleSuggestions = 60
private const val MaxVisibleImportErrors = 5
