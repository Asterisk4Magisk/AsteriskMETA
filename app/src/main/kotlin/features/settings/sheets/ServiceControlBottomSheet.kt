// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ServiceControlSettings
import app.ServiceControlWifiRule
import app.ServiceControlWifiRuleKind
import features.settings.servicecontrol.ServiceCronParseResult
import features.settings.servicecontrol.canSaveServiceControlDraft
import features.settings.servicecontrol.isValidServiceSsid
import features.settings.servicecontrol.normalizeBssidOrNull
import features.settings.servicecontrol.parseServiceCron
import app.R
import ui.components.StringListEditor
import ui.icons.AsteriskIcons as Icons
import ui.theme.AsteriskMotion

@Composable
internal fun ServiceControlBottomSheet(
    show: Boolean,
    saving: Boolean,
    draft: ServiceControlSettings,
    runtimeError: String?,
    onDraftChange: (ServiceControlSettings) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (ServiceControlSettings) -> Unit,
) {
    var pendingEditors by remember(show) { mutableStateOf(emptySet<String>()) }
    val scheduleEffective = draft.enabled && draft.schedule.enabled
    val wifiEffective = draft.enabled && draft.wifi.enabled
    val startCronInvalid = scheduleEffective &&
        parseServiceCron(draft.schedule.startCron) is ServiceCronParseResult.Invalid
    val stopCronInvalid = scheduleEffective &&
        parseServiceCron(draft.schedule.stopCron) is ServiceCronParseResult.Invalid
    val canSave = canSaveServiceControlDraft(draft, pendingEditors.isNotEmpty())

    SettingsModalBottomSheet(
        show = show,
        title = stringResource(R.string.settings_service_control),
        startAction = {
            TextButton(
                text = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Close,
                enabled = !saving,
                onClick = onDismissRequest,
            )
        },
        endAction = {
            TextButton(
                text = if (saving) {
                    stringResource(R.string.settings_service_control_saving)
                } else {
                    stringResource(R.string.common_save)
                },
                icon = Icons.Rounded.Save,
                enabled = canSave && !saving,
                onClick = { if (canSave) onSave(draft) },
            )
        },
        onDismissRequest = { if (!saving) onDismissRequest() },
    ) {
        key(show) {
            SettingsSheetContent {
                ServiceControlSection(stringResource(R.string.settings_service_control_basic)) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_service_control_enable),
                        summary = stringResource(R.string.settings_service_control_enable_summary),
                        icon = Icons.Rounded.PowerSettingsNew,
                        checked = draft.enabled,
                        onCheckedChange = { onDraftChange(draft.copy(enabled = it)) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_service_control_schedule_enable),
                        icon = Icons.Rounded.Sync,
                        checked = draft.schedule.enabled,
                        enabled = draft.enabled,
                        onCheckedChange = {
                            onDraftChange(draft.copy(schedule = draft.schedule.copy(enabled = it)))
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_service_control_wifi_enable),
                        icon = Icons.Rounded.Wifi,
                        checked = draft.wifi.enabled,
                        enabled = draft.enabled,
                        onCheckedChange = {
                            onDraftChange(draft.copy(wifi = draft.wifi.copy(enabled = it)))
                        },
                    )
                }

                AnimatedVisibility(
                    visible = scheduleEffective,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                    label = "service-control-schedule",
                ) {
                    ServiceControlSection(stringResource(R.string.settings_service_control_schedule)) {
                        Text(
                            text = stringResource(R.string.settings_service_control_cron_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        SettingsTextField(
                            value = draft.schedule.startCron,
                            onValueChange = {
                                onDraftChange(draft.copy(schedule = draft.schedule.copy(startCron = it)))
                            },
                            label = stringResource(R.string.settings_service_control_start_cron),
                            errorText = stringResource(R.string.settings_service_control_cron_invalid)
                                .takeIf { startCronInvalid },
                        )
                        SettingsTextField(
                            value = draft.schedule.stopCron,
                            onValueChange = {
                                onDraftChange(draft.copy(schedule = draft.schedule.copy(stopCron = it)))
                            },
                            label = stringResource(R.string.settings_service_control_stop_cron),
                            errorText = stringResource(R.string.settings_service_control_cron_invalid)
                                .takeIf { stopCronInvalid },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = wifiEffective,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                    label = "service-control-wifi",
                ) {
                    ServiceControlSection(stringResource(R.string.settings_service_control_wifi)) {
                        ServiceControlWifiRuleKind.entries.forEach { kind ->
                            val rule = draft.rule(kind)
                            ServiceControlRuleEditor(
                                kind = kind,
                                rule = rule,
                                onRuleChange = { next ->
                                    onDraftChange(draft.withRule(kind, next))
                                },
                                onPendingChange = { key, pending ->
                                    pendingEditors = if (pending) pendingEditors + key else pendingEditors - key
                                },
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = runtimeError != null,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                    label = "service-control-runtime-error",
                ) {
                    Text(
                        text = runtimeError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_service_control_doze_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ServiceControlRuleEditor(
    kind: ServiceControlWifiRuleKind,
    rule: ServiceControlWifiRule,
    onRuleChange: (ServiceControlWifiRule) -> Unit,
    onPendingChange: (String, Boolean) -> Unit,
) {
    val title = stringResource(kind.titleResource())
    val invalidSsid = stringResource(R.string.settings_service_control_ssid_invalid)
    val invalidBssid = stringResource(R.string.settings_service_control_bssid_invalid)
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SwitchPreference(
            title = title,
            icon = Icons.Rounded.Wifi,
            checked = rule.enabled,
            onCheckedChange = { onRuleChange(rule.copy(enabled = it)) },
        )
        AnimatedVisibility(
            visible = rule.enabled,
            enter = AsteriskMotion.contentEnter(),
            exit = AsteriskMotion.contentExit(),
            label = "service-control-rule-${kind.name}",
        ) {
            Column {
                StringListEditor(
                    editorKey = "${kind.name}-ssid",
                    title = stringResource(R.string.settings_service_control_ssids),
                    values = rule.ssids,
                    onValuesChange = { onRuleChange(rule.copy(ssids = it.take(MaxWifiIdentifiers))) },
                    emptyText = stringResource(R.string.settings_service_control_ssids_empty),
                    description = stringResource(R.string.settings_service_control_match_note),
                    normalizeInput = { it },
                    validateInput = { value ->
                        invalidSsid.takeUnless { isValidServiceSsid(value) }
                    },
                    onPendingChange = { onPendingChange("${kind.name}-ssid", it) },
                )
                StringListEditor(
                    editorKey = "${kind.name}-bssid",
                    title = stringResource(R.string.settings_service_control_bssids),
                    values = rule.bssids,
                    onValuesChange = { onRuleChange(rule.copy(bssids = it.take(MaxWifiIdentifiers))) },
                    emptyText = stringResource(R.string.settings_service_control_bssids_empty),
                    normalizeInput = { value -> normalizeBssidOrNull(value) ?: value.trim().lowercase() },
                    validateInput = { value ->
                        invalidBssid.takeIf { normalizeBssidOrNull(value) == null }
                    },
                    onPendingChange = { onPendingChange("${kind.name}-bssid", it) },
                )
            }
        }
    }
}

private fun ServiceControlWifiRuleKind.titleResource(): Int = when (this) {
    ServiceControlWifiRuleKind.ConnectStart -> R.string.settings_service_control_connect_start
    ServiceControlWifiRuleKind.ConnectStop -> R.string.settings_service_control_connect_stop
    ServiceControlWifiRuleKind.DisconnectStart -> R.string.settings_service_control_disconnect_start
    ServiceControlWifiRuleKind.DisconnectStop -> R.string.settings_service_control_disconnect_stop
}

private fun ServiceControlSettings.rule(kind: ServiceControlWifiRuleKind): ServiceControlWifiRule = when (kind) {
    ServiceControlWifiRuleKind.ConnectStart -> wifi.connectStart
    ServiceControlWifiRuleKind.ConnectStop -> wifi.connectStop
    ServiceControlWifiRuleKind.DisconnectStart -> wifi.disconnectStart
    ServiceControlWifiRuleKind.DisconnectStop -> wifi.disconnectStop
}

private fun ServiceControlSettings.withRule(
    kind: ServiceControlWifiRuleKind,
    rule: ServiceControlWifiRule,
): ServiceControlSettings {
    val wifi = when (kind) {
        ServiceControlWifiRuleKind.ConnectStart -> wifi.copy(
            connectStart = rule,
            connectStop = wifi.connectStop.copy(enabled = wifi.connectStop.enabled && !rule.enabled),
        )
        ServiceControlWifiRuleKind.ConnectStop -> wifi.copy(
            connectStop = rule,
            connectStart = wifi.connectStart.copy(enabled = wifi.connectStart.enabled && !rule.enabled),
        )
        ServiceControlWifiRuleKind.DisconnectStart -> wifi.copy(
            disconnectStart = rule,
            disconnectStop = wifi.disconnectStop.copy(enabled = wifi.disconnectStop.enabled && !rule.enabled),
        )
        ServiceControlWifiRuleKind.DisconnectStop -> wifi.copy(
            disconnectStop = rule,
            disconnectStart = wifi.disconnectStart.copy(enabled = wifi.disconnectStart.enabled && !rule.enabled),
        )
    }
    return copy(wifi = wifi)
}

@Composable
private fun ServiceControlSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
    }
}

private const val MaxWifiIdentifiers = 64
