// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.AppState
import app.AutoSwitchSettings
import app.LocalAppServices
import app.R
import engine.mihomo.runtime.MihomoProxyGroup
import engine.mihomo.runtime.runMihomoRuntimeCatching
import features.logs.AutoSwitchLogDialog
import features.settings.ArrowPreference
import features.settings.AutoSwitchRegionLabels
import features.settings.SettingsSectionCard
import features.settings.SmallTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ui.components.StringListEditor
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun AutoSwitchSettingsBottomSheet(
    show: Boolean,
    appState: AppState,
    draft: AutoSwitchSettings,
    onDraftChange: (AutoSwitchSettings) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (AutoSwitchSettings) -> Unit,
) {
    if (!show) return
    key(appState.selectedMihomoProfileId) {
        val runtime = LocalAppServices.current.mihomoRuntime
        var showLog by rememberSaveable { mutableStateOf(false) }
        var groups by remember { mutableStateOf(emptyList<MihomoProxyGroup>()) }
        var loading by remember { mutableStateOf(true) }
        var loadFailed by remember { mutableStateOf(false) }
        var failures by remember { mutableStateOf(draft.failureThreshold.toString()) }
        var interval by remember { mutableStateOf(draft.intervalSeconds.toString()) }
        var pendingKeywords by remember { mutableStateOf(false) }
        LaunchedEffect(runtime, appState.selectedMihomoProfileId) {
            val result = withContext(Dispatchers.IO) {
                runMihomoRuntimeCatching {
                    if (appState.proxyRunning) {
                        runtime.autoSwitchSnapshot(appState)
                    } else {
                        runtime.refreshProxies(appState).getOrThrow()
                        runtime.state.value.proxies
                    }
                }
            }
            loadFailed = result.isFailure
            groups = result.getOrNull()?.groups.orEmpty()
                .filter { it.type.equals("Selector", ignoreCase = true) }
            loading = false
        }
        val failureCount = failures.toIntOrNull()?.takeIf { it in 1..10 }
        val seconds = interval.toIntOrNull()?.takeIf { it in 10..3600 }
        val profileMatches = draft.profileId == appState.selectedMihomoProfileId
        val groupsValid = !draft.enabled || (!loading && !loadFailed && draft.groups.isNotEmpty() &&
                draft.groups.all { name -> groups.any { it.name == name } })
        SettingsModalBottomSheet(
            show = true,
            title = stringResource(R.string.settings_auto_switch),
            startAction = {
                TextButton(
                    stringResource(R.string.common_cancel),
                    Icons.Rounded.Close,
                    onDismissRequest
                )
            },
            endAction = {
                TextButton(
                    text = stringResource(R.string.common_save),
                    icon = Icons.Rounded.Save,
                    enabled = failureCount != null && seconds != null && groupsValid && profileMatches && !pendingKeywords,
                    onClick = {
                        onSave(
                            draft.copy(
                                failureThreshold = failureCount!!,
                                intervalSeconds = seconds!!
                            )
                        )
                    },
                )
            },
            onDismissRequest = onDismissRequest,
        ) {
            features.settings.SettingsSearchProvider("") {
                SettingsSheetContent {
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
                    Text(stringResource(R.string.auto_switch_policy), Modifier.padding(16.dp))
                    SmallTitle(stringResource(R.string.auto_switch_groups))
                    Text(
                        stringResource(R.string.auto_switch_groups_summary),
                        Modifier.padding(16.dp)
                    )
                    when {
                        loading -> Text(
                            stringResource(R.string.mihomo_proxies_loading),
                            Modifier.padding(16.dp)
                        )

                        loadFailed -> Text(
                            stringResource(R.string.auto_switch_groups_failed),
                            Modifier.padding(16.dp)
                        )

                        groups.isEmpty() -> Text(
                            stringResource(R.string.auto_switch_groups_empty),
                            Modifier.padding(16.dp)
                        )

                        else -> SettingsSectionCard {
                            (groups.map { it.name } + draft.groups).distinct().forEach { name ->
                                SwitchPreference(
                                    title = name,
                                    icon = Icons.Rounded.Tune,
                                    checked = name in draft.groups,
                                    onCheckedChange = { checked ->
                                        onDraftChange(
                                            draft.copy(
                                                groups = draft.groups.toggle(
                                                    name,
                                                    checked
                                                )
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                    SmallTitle(stringResource(R.string.auto_switch_regions))
                    SettingsSectionCard {
                        AutoSwitchRegionLabels.forEach { (code, label) ->
                            SwitchPreference(
                                title = stringResource(label),
                                icon = Icons.Rounded.Public,
                                checked = code in draft.preferredRegions,
                                onCheckedChange = { checked ->
                                    onDraftChange(
                                        draft.copy(
                                            preferredRegions = draft.preferredRegions.toggle(
                                                code,
                                                checked
                                            )
                                        )
                                    )
                                },
                            )
                        }
                    }
                    StringListEditor(
                        editorKey = appState.selectedMihomoProfileId,
                        title = stringResource(R.string.auto_switch_keywords),
                        values = draft.preferredKeywords,
                        onValuesChange = { onDraftChange(draft.copy(preferredKeywords = it)) },
                        emptyText = stringResource(R.string.auto_switch_keywords_empty),
                        onPendingChange = { pendingKeywords = it },
                    )
                    SmallTitle(stringResource(R.string.auto_switch_detection))
                    SettingsTextField(
                        value = failures,
                        onValueChange = { failures = it },
                        label = stringResource(R.string.auto_switch_failures),
                        errorText = if (failureCount == null) stringResource(R.string.auto_switch_failures_invalid) else null,
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
                }
            }
        }
        if (showLog) {
            AutoSwitchLogDialog(runtime.autoSwitchLog) { showLog = false }
        }
    }
}

private fun List<String>.toggle(value: String, checked: Boolean): List<String> =
    if (checked) (this + value).distinct() else filterNot { it == value }

