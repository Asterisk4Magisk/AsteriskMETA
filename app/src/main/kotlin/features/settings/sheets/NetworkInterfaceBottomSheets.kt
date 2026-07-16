// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.R
import ui.icons.AsteriskIcons as Icons
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import ui.components.AsteriskCheckbox
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

// Android vendors use different mobile data interface names; keep this list permissive for outlet candidates.
private val IgnoredInterfaceAllowedPrefixes = listOf(
    "wlan",
    "rmnet_data",
    "rmnet",
    "ccmni",
    "cc2mni",
    "ccemni",
    "pdp",
    "ppp",
    "eth",
    "bond",
    "oem",
    "rev_rmnet",
)

// Exclude loopback, virtual, tunnel, and tethering-facing interfaces from outlet candidates.
private val IgnoredInterfaceBlockedPrefixes = listOf(
    "lo",
    "dummy",
    "tun",
    "tap",
    "ifb",
    "ip6tnl",
    "sit",
    "gre",
    "gretap",
    "erspan",
    "veth",
    "br",
    "docker",
    "clat",
    "v4-",
    "ip_vti",
    "rndis",
    "usb",
    "ap",
    "softap",
    "bnep",
    "bt-pan",
    "p2p",
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

internal fun outletInterfaceOptions(interfaces: List<String>): List<String> {
    return interfaces
        .toTrimmedNonEmptyDistinctList()
        .asSequence()
        .filter { interfaceName ->
            IgnoredInterfaceBlockedPrefixes.none(interfaceName::startsWith)
        }
        .sortedWith(
            compareBy<String> { interfaceName ->
                IgnoredInterfaceAllowedPrefixes.indexOfFirst(interfaceName::startsWith)
                    .takeIf { it >= 0 } ?: IgnoredInterfaceAllowedPrefixes.size
            }.thenBy { it },
        )
        .toList()
}

internal fun List<String>.orderedBy(options: List<String>): List<String> {
    return options.filter { it in this }
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
internal fun IgnoredInterfacesBottomSheet(
    show: Boolean,
    interfaces: List<String>,
    selectedInterfaces: List<String>,
    loading: Boolean,
    errorMessage: String?,
    onSelectedInterfacesChange: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val formattedErrorMessage = errorMessage?.let { message ->
        stringResource(R.string.settings_ignored_interfaces_error).formatTemplate("message" to message)
    }

    SettingsModalBottomSheet(
        show = show,
        title = stringResource(R.string.settings_ignored_interfaces),
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
                onClick = { onSave(selectedInterfaces) },
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        SettingsSheetContent {
            SheetStatusText(stringResource(R.string.settings_ignored_interfaces_summary))
            if (loading) {
                SheetStatusText(stringResource(R.string.settings_ignored_interfaces_loading))
            }
            formattedErrorMessage?.takeIf(String::isNotBlank)?.let { SheetStatusText(it) }
            if (!loading && formattedErrorMessage == null && interfaces.isEmpty()) {
                SheetStatusText(stringResource(R.string.settings_ignored_interfaces_empty))
            }
            InterfaceOptionGrid(
                interfaces = interfaces,
                selectedInterfaces = selectedInterfaces,
                onSelectedInterfacesChange = onSelectedInterfacesChange,
            )
        }
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

@Composable
private fun InterfaceOptionGrid(
    interfaces: List<String>,
    selectedInterfaces: List<String>,
    onSelectedInterfacesChange: (List<String>) -> Unit,
) {
    interfaces.chunked(2).forEach { rowItems ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        ) {
            rowItems.forEachIndexed { index, interfaceName ->
                InterfaceOptionCard(
                    interfaceName = interfaceName,
                    selected = interfaceName in selectedInterfaces,
                    onSelectedChange = { selected ->
                        val next = if (selected) {
                            selectedInterfaces + interfaceName
                        } else {
                            selectedInterfaces - interfaceName
                        }
                        onSelectedInterfacesChange(next)
                    },
                    modifier = Modifier.weight(1f),
                )
                if (index == 0) {
                    Spacer(Modifier.width(8.dp))
                }
            }
            if (rowItems.size == 1) {
                Spacer(Modifier.width(8.dp))
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InterfaceOptionCard(
    interfaceName: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggle = { onSelectedChange(!selected) }
    Card(
        modifier = modifier,
        onClick = toggle,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = interfaceName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            AsteriskCheckbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
            )
        }
    }
}
