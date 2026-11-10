// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.R
import engine.mihomo.MihomoFindProcessModeValues
import engine.mihomo.MihomoGeodataLoaderValues
import engine.network.toPortOrNull
import features.settings.OtherSettingsDraft
import java.util.UUID
import ui.components.AsteriskCancelButton
import ui.components.AsteriskSaveButton
import ui.components.StringListEditor
import ui.icons.AsteriskIcons as Icons
import ui.theme.AsteriskMotion
import utils.toTrimmedNonEmptyDistinctList

@Composable
internal fun OtherSettingsBottomSheet(
    show: Boolean,
    draft: OtherSettingsDraft,
    reservedPorts: Set<Int>,
    showExternalControl: Boolean,
    onDraftChange: (OtherSettingsDraft) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (OtherSettingsDraft) -> Unit,
) {
    val portError = when {
        !showExternalControl || !draft.mihomoControlPortFixed -> null
        draft.mihomoControlPort.toPortOrNull() == null -> stringResource(R.string.settings_local_proxy_port_invalid)
        draft.mihomoControlPort.toPortOrNull() in reservedPorts ->
            stringResource(R.string.settings_other_controller_port_conflict)
        else -> null
    }
    val secretError = if (showExternalControl && draft.mihomoControlSecret.isBlank()) {
        stringResource(R.string.settings_other_controller_secret_required)
    } else {
        null
    }
    val originInvalidMessage = stringResource(R.string.settings_other_controller_cors_origin_invalid)
    val findProcessModeOptions = listOf(stringResource(R.string.settings_other_follow_profile)) +
        MihomoFindProcessModeValues.drop(1)
    val canSave = portError == null && secretError == null
    // The text fields keep their own state; a new key reloads them after a programmatic change.
    var secretRevision by remember(show) { mutableIntStateOf(0) }

    SettingsModalBottomSheet(
        show = show,
        title = stringResource(R.string.settings_other),
        startAction = {
            AsteriskCancelButton(onClick = onDismissRequest)
        },
        endAction = {
            AsteriskSaveButton(
                enabled = canSave,
                onClick = { if (canSave) onSave(draft.sanitized()) },
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        key(show) {
            SettingsSheetContent {
                SettingsSheetSection(title = stringResource(R.string.settings_other_section_geodata)) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_geodata_mode),
                        icon = Icons.Rounded.Public,
                        summary = stringResource(R.string.settings_geodata_mode_summary),
                        checked = draft.enableGeodataMode,
                        onCheckedChange = { onDraftChange(draft.copy(enableGeodataMode = it)) },
                    )
                    WindowDropdownPreference(
                        title = stringResource(R.string.settings_geodata_loader),
                        icon = Icons.Rounded.Storage,
                        summary = stringResource(R.string.settings_geodata_loader_summary),
                        items = MihomoGeodataLoaderValues,
                        selectedIndex = draft.mihomoGeodataLoader.coerceIn(MihomoGeodataLoaderValues.indices),
                        onSelectedIndexChange = { onDraftChange(draft.copy(mihomoGeodataLoader = it)) },
                    )
                }

                SettingsSheetSection(title = stringResource(R.string.settings_other_section_process)) {
                    WindowDropdownPreference(
                        title = stringResource(R.string.settings_other_find_process_mode),
                        icon = Icons.Rounded.Apps,
                        summary = stringResource(R.string.settings_other_find_process_mode_summary),
                        items = findProcessModeOptions,
                        selectedIndex = draft.mihomoFindProcessMode.coerceIn(findProcessModeOptions.indices),
                        onSelectedIndexChange = { onDraftChange(draft.copy(mihomoFindProcessMode = it)) },
                    )
                }

                // The VPN core only serves the app's controller socket, so these keys would have no effect.
                if (showExternalControl) SettingsSheetSection(title = stringResource(R.string.settings_other_section_external_control)) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_other_controller_port_fixed),
                        icon = Icons.Rounded.Lock,
                        summary = stringResource(R.string.settings_other_controller_port_fixed_summary),
                        checked = draft.mihomoControlPortFixed,
                        onCheckedChange = { onDraftChange(draft.copy(mihomoControlPortFixed = it)) },
                    )
                    key(draft.mihomoControlPortFixed) {
                        if (draft.mihomoControlPortFixed) {
                            SettingsTextField(
                                value = draft.mihomoControlPort,
                                onValueChange = { onDraftChange(draft.copy(mihomoControlPort = it)) },
                                label = stringResource(R.string.settings_other_controller_port),
                                errorText = portError,
                                keyboardOptions = fiveDigitKeyboardOptions(),
                                sanitizeInput = ::sanitizeFiveDigitInput,
                            )
                        } else {
                            // An unfixed port may still move on the next start, so it is only shown.
                            SheetTextField(
                                value = draft.mihomoControlPort,
                                onValueChange = {},
                                label = stringResource(R.string.settings_other_controller_port),
                                enabled = false,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            )
                        }
                    }
                    key(secretRevision) {
                        SettingsTextField(
                            value = draft.mihomoControlSecret,
                            onValueChange = { onDraftChange(draft.copy(mihomoControlSecret = it)) },
                            label = stringResource(R.string.settings_other_controller_secret),
                            errorText = secretError,
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        TextButton(
                            text = stringResource(R.string.settings_other_controller_secret_regenerate),
                            icon = Icons.Rounded.Refresh,
                            onClick = {
                                onDraftChange(draft.copy(mihomoControlSecret = UUID.randomUUID().toString()))
                                secretRevision += 1
                            },
                        )
                    }
                    SwitchPreference(
                        title = stringResource(R.string.settings_other_controller_listen_all_interfaces),
                        icon = Icons.Rounded.Lan,
                        summary = stringResource(R.string.settings_other_controller_listen_all_interfaces_summary),
                        checked = draft.mihomoControlListenAllInterfaces,
                        onCheckedChange = { onDraftChange(draft.copy(mihomoControlListenAllInterfaces = it)) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_other_controller_cors_allow_private_network),
                        icon = Icons.Rounded.Security,
                        summary = stringResource(R.string.settings_other_controller_cors_allow_private_network_summary),
                        checked = draft.mihomoControlCorsAllowPrivateNetwork,
                        onCheckedChange = { onDraftChange(draft.copy(mihomoControlCorsAllowPrivateNetwork = it)) },
                    )
                    StringListEditor(
                        editorKey = "controller-cors-allow-origins:$show",
                        title = stringResource(R.string.settings_other_controller_cors_allow_origins),
                        description = stringResource(R.string.settings_other_controller_cors_allow_origins_description),
                        values = draft.mihomoControlCorsAllowOrigins.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = {
                            onDraftChange(draft.copy(mihomoControlCorsAllowOrigins = it.toTrimmedNonEmptyDistinctList()))
                        },
                        emptyText = stringResource(R.string.settings_sniffer_list_empty),
                        validateInput = { if (isControllerCorsOrigin(it)) null else originInvalidMessage },
                    )
                    Spacer(Modifier.height(8.dp))
                    SwitchPreference(
                        title = stringResource(R.string.settings_other_external_ui_override),
                        icon = Icons.Rounded.Language,
                        summary = stringResource(R.string.settings_other_external_ui_override_summary),
                        checked = draft.overrideMihomoExternalUi,
                        onCheckedChange = { onDraftChange(draft.copy(overrideMihomoExternalUi = it)) },
                    )
                    AnimatedVisibility(
                        visible = draft.overrideMihomoExternalUi,
                        enter = AsteriskMotion.contentEnter(),
                        exit = AsteriskMotion.contentExit(),
                    ) {
                        Column {
                            SettingsTextField(
                                value = draft.mihomoExternalUi,
                                onValueChange = { onDraftChange(draft.copy(mihomoExternalUi = it)) },
                                label = stringResource(R.string.settings_other_external_ui),
                                errorText = null,
                            )
                            SettingsTextField(
                                value = draft.mihomoExternalUiUrl,
                                onValueChange = { onDraftChange(draft.copy(mihomoExternalUiUrl = it)) },
                                label = stringResource(R.string.settings_other_external_ui_url),
                                errorText = null,
                            )
                            SettingsTextField(
                                value = draft.mihomoExternalUiName,
                                onValueChange = { onDraftChange(draft.copy(mihomoExternalUiName = it)) },
                                label = stringResource(R.string.settings_other_external_ui_name),
                                errorText = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun isControllerCorsOrigin(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed == "*") return true
    if (trimmed.any(Char::isWhitespace)) return false
    val scheme = trimmed.substringBefore("://", missingDelimiterValue = "").lowercase()
    return scheme in setOf("http", "https") && trimmed.substringAfter("://").isNotBlank()
}

internal fun OtherSettingsDraft.sanitized(): OtherSettingsDraft {
    return copy(
        mihomoControlPort = mihomoControlPort.trim(),
        mihomoControlSecret = mihomoControlSecret.trim(),
        mihomoControlCorsAllowOrigins = mihomoControlCorsAllowOrigins.toTrimmedNonEmptyDistinctList()
            .filter(::isControllerCorsOrigin),
        mihomoExternalUi = mihomoExternalUi.trim(),
        mihomoExternalUiUrl = mihomoExternalUiUrl.trim(),
        mihomoExternalUiName = mihomoExternalUiName.trim(),
    )
}
