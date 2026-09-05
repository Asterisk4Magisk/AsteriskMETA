// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import engine.mihomo.isMihomoTunSharedInterface
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.R
import ui.components.StringListEditor
import ui.icons.AsteriskIcons as Icons
import ui.text.formatTemplate
import utils.toTrimmedNonEmptyDistinctList

internal fun List<String>.sanitizeTunSharedNetworkInterfaces(): List<String> {
    return toTrimmedNonEmptyDistinctList().filterNot { it == "lo" }
}

@Composable
internal fun tunSharedNetworkInterfacesSummary(interfaces: List<String>): String {
    val values = interfaces.sanitizeTunSharedNetworkInterfaces()
    if (values.isEmpty()) {
        return stringResource(R.string.settings_tun_shared_network_none)
    }
    return stringResource(R.string.settings_tun_shared_network_selected)
        .formatTemplate("count" to values.size)
}

@Composable
internal fun TunSharedNetworkBottomSheet(
    show: Boolean,
    interfaces: List<String>,
    onInterfacesChange: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var editorPending by remember(show) { mutableStateOf(false) }
    val invalidMessage = stringResource(R.string.settings_tun_shared_network_invalid)
    val normalizedInterfaces = interfaces.sanitizeTunSharedNetworkInterfaces()
    SettingsModalBottomSheet(
        show = show,
        title = stringResource(R.string.settings_tun_shared_network),
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
                onClick = { onSave(normalizedInterfaces) },
                enabled = !editorPending && normalizedInterfaces.all(::isMihomoTunSharedInterface),
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            item {
                StringListEditor(
                    editorKey = "tun-shared-network:$show",
                    title = stringResource(R.string.settings_tun_shared_network_input),
                    description = stringResource(R.string.settings_tun_shared_network_description),
                    values = normalizedInterfaces,
                    onValuesChange = { values ->
                        onInterfacesChange(values.sanitizeTunSharedNetworkInterfaces())
                    },
                    emptyText = stringResource(R.string.settings_tun_shared_network_empty),
                    validateInput = { value -> if (isMihomoTunSharedInterface(value)) null else invalidMessage },
                    onPendingChange = { editorPending = it },
                )
            }
        }
    }
}
