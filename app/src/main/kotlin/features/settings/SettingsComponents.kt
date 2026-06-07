// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.R
import app.modes.RunModeTun2Socks
import app.modes.RunModeTproxy
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.Card
import ui.text.formatTemplate

internal val SettingsLogLevelOptions = listOf("debug", "info", "warning", "error", "silent")

@Composable
internal fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = bottomPadding),
    ) {
        content()
    }
}

@Composable
internal fun localProxySettingsSummary(
    runMode: Int,
    port: String,
    listenAllInterfaces: Boolean,
    transparentProxyPort: String,
    socks5ProxyPort: String,
): String {
    val summary = if (listenAllInterfaces) {
        stringResource(R.string.settings_local_proxy_summary_all_interfaces)
    } else {
        stringResource(R.string.settings_local_proxy_summary_fixed)
    }
    val localProxySummary = summary.formatTemplate("port" to port)
    val inboundProxySummary = when (runMode) {
        RunModeTproxy -> stringResource(R.string.settings_local_proxy_summary_tproxy)
            .formatTemplate("port" to transparentProxyPort)

        RunModeTun2Socks -> stringResource(R.string.settings_local_proxy_summary_tun2socks)
            .formatTemplate("port" to socks5ProxyPort)

        else -> ""
    }
    return listOf(inboundProxySummary, localProxySummary)
        .filter(String::isNotBlank)
        .joinToString(separator = "，")
}
