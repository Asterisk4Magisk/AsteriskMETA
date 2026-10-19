// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.AppState
import app.AutoSwitchExpectedDelayMillisRange
import app.AutoSwitchFailureThresholdRange
import app.AutoSwitchIntervalSecondsRange
import app.AutoSwitchSettings
import app.LocalAppServices
import app.R
import engine.mihomo.runtime.MihomoProxyGroup
import features.logs.AutoSwitchLogDialog
import features.settings.ArrowPreference
import features.settings.AutoSwitchRegionLabels
import features.settings.SettingsSearchProvider
import kotlinx.coroutines.ensureActive
import ui.components.AsteriskActionButton
import ui.components.StringListEditor
import ui.icons.AsteriskIcons as Icons

/** Edits a draft; only the explicit save action publishes settings. */
@Composable
internal fun AutoSwitchSettingsBottomSheet(
    show: Boolean,
    appState: AppState,
    overridesEnabled: Boolean,
    draft: AutoSwitchSettings,
    onDraftChange: (AutoSwitchSettings) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (AutoSwitchSettings) -> Unit,
) {
    // Keep the sheet composed while its host animates show=false to Hidden.
    key(appState.selectedMihomoProfileId) {
        val runtime = LocalAppServices.current.mihomoRuntime
        var showLog by rememberSaveable { mutableStateOf(false) }
        var info by rememberSaveable { mutableStateOf<AutoSwitchInfo?>(null) }
        var groups by remember { mutableStateOf(emptyList<MihomoProxyGroup>()) }
        var loading by remember { mutableStateOf(true) }
        var loadFailed by remember { mutableStateOf(false) }
        var failures by remember { mutableStateOf(draft.failureThreshold.toString()) }
        var interval by remember { mutableStateOf(draft.intervalSeconds.toString()) }
        var expectedDelay by remember { mutableStateOf(draft.expectedDelayMillis.toString()) }
        var pendingKeywords by remember { mutableStateOf(false) }
        var editorSession by remember { mutableIntStateOf(0) }
        LaunchedEffect(show, runtime, appState.selectedMihomoProfileId) {
            if (!show) return@LaunchedEffect
            // Reset only on opening so the exit animation keeps the current content.
            failures = draft.failureThreshold.toString()
            interval = draft.intervalSeconds.toString()
            expectedDelay = draft.expectedDelayMillis.toString()
            pendingKeywords = false
            showLog = false
            info = null
            editorSession++
            loading = true
            loadFailed = false
            val result = runtime.refreshProxies(appState)
            coroutineContext.ensureActive()
            loadFailed = result.isFailure
            groups = runtime.state.value.proxies.groups.filter { it.type.equals("Selector", ignoreCase = true) }
            loading = false
        }
        val failureCount = failures.toIntOrNull()?.takeIf { it in AutoSwitchFailureThresholdRange }
        val seconds = interval.toIntOrNull()?.takeIf { it in AutoSwitchIntervalSecondsRange }
        val delayMillis = expectedDelay.toIntOrNull()?.takeIf { it in AutoSwitchExpectedDelayMillisRange }
        val groupsValid = !draft.enabled || (!loading && !loadFailed && draft.groups.isNotEmpty() &&
            draft.groups.all { name -> groups.any { it.name == name } })
        val canSave = failureCount != null && seconds != null && delayMillis != null && groupsValid &&
            !pendingKeywords
        SettingsModalBottomSheet(
            show = show,
            title = stringResource(R.string.settings_auto_switch),
            startAction = {
                TextButton(stringResource(R.string.common_cancel), Icons.Rounded.Close, onDismissRequest)
            },
            endAction = {
                TextButton(
                    text = stringResource(R.string.common_save),
                    icon = Icons.Rounded.Save,
                    enabled = canSave,
                    onClick = {
                        if (failureCount != null && seconds != null && delayMillis != null) {
                            onSave(
                                draft.copy(
                                    failureThreshold = failureCount,
                                    intervalSeconds = seconds,
                                    expectedDelayMillis = delayMillis,
                                ),
                            )
                        }
                    },
                )
            },
            onDismissRequest = onDismissRequest,
        ) {
            key(editorSession) {
                // Keep the page search query from highlighting rows inside the sheet.
                SettingsSearchProvider("") {
                    SettingsSheetContent {
                        AutoSwitchSectionTitle(stringResource(R.string.common_basic)) { info = AutoSwitchInfo.Policy }
                        SwitchPreference(
                            title = stringResource(R.string.auto_switch_enabled),
                            icon = Icons.Rounded.Sync,
                            checked = draft.enabled,
                            onCheckedChange = { onDraftChange(draft.copy(enabled = it)) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.auto_switch_log_title),
                            summary = stringResource(R.string.auto_switch_log_summary),
                            icon = Icons.Rounded.History,
                            onClick = { showLog = true },
                        )
                        // Only the raw-profile state warning stays inline; explanations open on demand.
                        if (!overridesEnabled) {
                            Text(
                                text = stringResource(R.string.auto_switch_policy_raw),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        AutoSwitchSectionTitle(stringResource(R.string.auto_switch_groups)) { info = AutoSwitchInfo.Groups }
                        when {
                            loading -> Text(
                                stringResource(R.string.mihomo_proxies_loading),
                                Modifier.padding(16.dp),
                            )

                            loadFailed -> Text(
                                stringResource(R.string.auto_switch_groups_failed),
                                Modifier.padding(16.dp),
                            )

                            groups.isEmpty() -> Text(
                                stringResource(R.string.auto_switch_groups_empty),
                                Modifier.padding(16.dp),
                            )

                            else -> Column {
                                // Saved names missing from this profile stay visible so they can be cleared.
                                (groups.map { it.name } + draft.groups).distinct().forEach { name ->
                                    SwitchPreference(
                                        title = name,
                                        icon = Icons.Rounded.Tune,
                                        checked = name in draft.groups,
                                        onCheckedChange = { checked ->
                                            onDraftChange(draft.copy(groups = draft.groups.toggle(name, checked)))
                                        },
                                    )
                                }
                            }
                        }
                        AutoSwitchSectionTitle(stringResource(R.string.auto_switch_regions))
                        AutoSwitchRegionLabels.forEach { (code, label) ->
                            SwitchPreference(
                                title = stringResource(label),
                                icon = Icons.Rounded.Public,
                                checked = code in draft.preferredRegions,
                                onCheckedChange = { checked ->
                                    onDraftChange(
                                        draft.copy(preferredRegions = draft.preferredRegions.toggle(code, checked)),
                                    )
                                },
                            )
                        }
                        StringListEditor(
                            editorKey = appState.selectedMihomoProfileId,
                            title = stringResource(R.string.auto_switch_keywords),
                            values = draft.preferredKeywords,
                            onValuesChange = { onDraftChange(draft.copy(preferredKeywords = it)) },
                            emptyText = stringResource(R.string.auto_switch_keywords_empty),
                            onPendingChange = { pendingKeywords = it },
                            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                        )
                        AutoSwitchSectionTitle(stringResource(R.string.auto_switch_detection))
                        SettingsTextField(
                            value = failures,
                            onValueChange = { failures = it },
                            label = stringResource(R.string.auto_switch_failures),
                            errorText = if (failureCount == null) {
                                stringResource(R.string.auto_switch_failures_invalid)
                            } else {
                                null
                            },
                            keyboardOptions = fiveDigitKeyboardOptions(),
                            sanitizeInput = ::sanitizeFiveDigitInput,
                        )
                        SettingsTextField(
                            value = interval,
                            onValueChange = { interval = it },
                            label = stringResource(R.string.auto_switch_interval),
                            errorText = if (seconds == null) stringResource(R.string.auto_switch_interval_invalid) else null,
                            keyboardOptions = fiveDigitKeyboardOptions(),
                            sanitizeInput = ::sanitizeFiveDigitInput,
                        )
                        SettingsTextField(
                            value = expectedDelay,
                            onValueChange = { expectedDelay = it },
                            label = stringResource(R.string.auto_switch_expected_delay),
                            errorText = if (delayMillis == null) {
                                stringResource(R.string.auto_switch_expected_delay_invalid)
                            } else {
                                null
                            },
                            keyboardOptions = fiveDigitKeyboardOptions(),
                            sanitizeInput = ::sanitizeFiveDigitInput,
                        )
                    }
                }
            }
        }
        if (show && showLog) {
            AutoSwitchLogDialog(appState = appState, onDismissRequest = { showLog = false })
        }
        info?.takeIf { show }?.let { shown ->
            AutoSwitchInfoDialog(
                title = stringResource(shown.title),
                text = stringResource(shown.text),
                onDismissRequest = { info = null },
            )
        }
    }
}

private enum class AutoSwitchInfo(@StringRes val title: Int, @StringRes val text: Int) {
    Groups(R.string.auto_switch_groups, R.string.auto_switch_groups_summary),
    Policy(R.string.auto_switch_policy_title, R.string.auto_switch_policy),
}

// Match the DNS/sniffer sheet headings while keeping explanations available on demand.
@Composable
private fun AutoSwitchSectionTitle(title: String, onInfoClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
        )
        if (onInfoClick != null) {
            // Match the compact info action on BOX outbound group cards.
            val infoIconSize = with(LocalDensity.current) {
                MaterialTheme.typography.bodySmall.fontSize.toDp()
            }
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(width = 24.dp, height = 20.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = stringResource(R.string.auto_switch_info),
                    modifier = Modifier.size(infoIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AutoSwitchInfoDialog(title: String, text: String, onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Text(
                text = text,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            AsteriskActionButton(
                text = stringResource(R.string.common_close),
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
    )
}

private fun List<String>.toggle(value: String, checked: Boolean): List<String> =
    if (checked) (this + value).distinct() else filterNot { it == value }
