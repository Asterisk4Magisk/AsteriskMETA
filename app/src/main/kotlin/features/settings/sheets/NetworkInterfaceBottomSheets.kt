// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.R
import ui.icons.AsteriskIcons as Icons
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import ui.text.formatTemplate
import utils.toTrimmedNonEmptyDistinctList

private data class ExternalInterfaceGroup(
    val key: String,
    val prefixes: List<String>,
)

private val ExternalInterfaceGroups = listOf(
    ExternalInterfaceGroup("wifi", listOf("wlan+", "ap+", "softap+")),
    ExternalInterfaceGroup("usb", listOf("rndis+", "usb+")),
    ExternalInterfaceGroup("bluetooth", listOf("bnep+", "bt-pan+")),
    ExternalInterfaceGroup("ethernet", listOf("eth+")),
)

@Composable
internal fun externalInterfacesSummary(interfaces: List<String>): String {
    val selected = interfaces.sanitizeExternalInterfaces()
    if (selected.isEmpty()) {
        return stringResource(R.string.settings_external_interfaces_none)
    }
    val selectedGroups = ExternalInterfaceGroups
        .filter { group -> group.prefixes.any { it in selected } }
        .map { group -> externalInterfaceGroupTitle(group) }
    return stringResource(R.string.settings_external_interfaces_selected)
        .formatTemplate("interfaces" to selectedGroups.joinToString())
}

internal fun List<String>.sanitizeExternalInterfaces(): List<String> {
    val selectedPrefixes = toTrimmedNonEmptyDistinctList().toSet()
    return ExternalInterfaceGroups.flatMap { group ->
        if (group.prefixes.any { it in selectedPrefixes }) group.prefixes else emptyList()
    }
}

@Composable
internal fun ignoredInterfacesSummary(interfaces: List<String>): String {
    if (interfaces.isEmpty()) {
        return stringResource(R.string.settings_ignored_interfaces_none)
    }
    return stringResource(R.string.settings_ignored_interfaces_selected)
        .formatTemplate("interfaces" to interfaces.joinToString())
}

@Composable
internal fun ExternalInterfacesBottomSheet(
    show: Boolean,
    selectedInterfaces: List<String>,
    onSelectedInterfacesChange: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    SettingsModalBottomSheet(
        show = show,
        title = stringResource(R.string.settings_external_interfaces),
        startAction = {
            TextButton(
                text = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
        endAction = {
            TextButton(
                text = stringResource(R.string.common_save),
                icon = Icons.Rounded.Save,
                onClick = { onSave(selectedInterfaces.sanitizeExternalInterfaces()) },
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        SettingsSheetContent {
            SheetStatusText(stringResource(R.string.settings_external_interfaces_summary))
            ExternalInterfaceGroups.forEach { group ->
                val sanitizedSelection = selectedInterfaces.sanitizeExternalInterfaces()
                SwitchPreference(
                    title = externalInterfaceGroupTitle(group),
                    icon = externalInterfaceGroupIcon(group),
                    summary = group.prefixes.joinToString(),
                    checked = group.prefixes.all { it in sanitizedSelection },
                    onCheckedChange = { enabled ->
                        val next = if (enabled) {
                            sanitizedSelection + group.prefixes
                        } else {
                            sanitizedSelection.filterNot { it in group.prefixes }
                        }
                        onSelectedInterfacesChange(next.sanitizeExternalInterfaces())
                    },
                )
            }
        }
    }
}

@Composable
private fun externalInterfaceGroupTitle(group: ExternalInterfaceGroup): String {
    return when (group.key) {
        "wifi" -> stringResource(R.string.settings_external_interfaces_wifi)
        "usb" -> stringResource(R.string.settings_external_interfaces_usb)
        "bluetooth" -> stringResource(R.string.settings_external_interfaces_bluetooth)
        "ethernet" -> stringResource(R.string.settings_external_interfaces_ethernet)
        else -> group.key
    }
}

private fun externalInterfaceGroupIcon(group: ExternalInterfaceGroup): ImageVector {
    return when (group.key) {
        "wifi" -> Icons.Rounded.Wifi
        "usb" -> Icons.Rounded.Usb
        "bluetooth" -> Icons.Rounded.Bluetooth
        "ethernet" -> Icons.Rounded.SettingsEthernet
        else -> Icons.Rounded.SettingsInputComponent
    }
}

@Composable
private fun SheetStatusText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
