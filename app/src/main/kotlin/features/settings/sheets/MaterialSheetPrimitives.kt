// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import features.settings.SettingsDropdownRow
import features.settings.SettingsSwitchRow
import ui.components.AsteriskModalBottomSheet
import ui.components.AsteriskActionButton

@Composable
internal fun SettingsModalBottomSheet(
    show: Boolean,
    dismissEnabled: Boolean = true,
    title: String,
    startAction: @Composable () -> Unit,
    endAction: @Composable () -> Unit,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    AsteriskModalBottomSheet(
        show = show,
        dismissEnabled = dismissEnabled,
        onDismissRequest = onDismissRequest,
        title = title,
        startAction = { startAction() },
        endAction = { endAction() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun TextButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    AsteriskActionButton(
        text = text,
        icon = icon,
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
internal fun WindowDropdownPreference(
    title: String,
    icon: ImageVector,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    summary: String = "",
) {
    SettingsDropdownRow(
        title = title,
        summary = summary,
        icon = icon,
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

@Composable
internal fun SwitchPreference(
    title: String,
    icon: ImageVector,
    summary: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(
        title = title,
        summary = summary,
        icon = icon,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
internal fun SettingsSheetSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
    }
}
