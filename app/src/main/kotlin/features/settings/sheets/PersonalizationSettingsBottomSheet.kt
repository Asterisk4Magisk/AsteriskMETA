// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
import app.R
import features.settings.LocalSettingsSearchQuery
import features.settings.PersonalizationSettingsContent
import ui.components.AsteriskModalBottomSheet

/**
 * Popup opened by the Appearance section's 个性化 entry.
 *
 * Every control applies on tap (no Cancel / Save): theme mode, accent colour,
 * network activity chart style, navigation bar style and the auto-hide
 * navigation bar switch all go straight through the host's
 * `updateAppState { ... .copy(...) }` path, which is why
 * [AsteriskModalBottomSheet] is used directly instead of the draft-and-commit
 * [SettingsModalBottomSheet] shape.
 *
 * The controls are rendered with an empty search query so the popup always
 * shows every option, even when the Settings page was searched into.
 */
@Composable
internal fun PersonalizationSettingsBottomSheet(
    show: Boolean,
    colorModeOptions: List<String>,
    colorMode: Int,
    keyColorOptions: List<String>,
    seedIndex: Int,
    chartStyleOptions: List<String>,
    networkActivityStyle: Int,
    navigationBarStyle: Int,
    autoHideNavigationBar: Boolean,
    onColorModeChange: (Int) -> Unit,
    onSeedIndexChange: (Int) -> Unit,
    onNetworkActivityStyleChange: (Int) -> Unit,
    onNavigationBarStyleChange: (Int) -> Unit,
    onAutoHideNavigationBarChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AsteriskModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.settings_personalization),
    ) {
        CompositionLocalProvider(LocalSettingsSearchQuery provides "") {
            SettingsSheetContent {
                PersonalizationSettingsContent(
                    colorModeOptions = colorModeOptions,
                    colorMode = colorMode,
                    keyColorOptions = keyColorOptions,
                    seedIndex = seedIndex,
                    chartStyleOptions = chartStyleOptions,
                    networkActivityStyle = networkActivityStyle,
                    navigationBarStyle = navigationBarStyle,
                    autoHideNavigationBar = autoHideNavigationBar,
                    onColorModeChange = onColorModeChange,
                    onSeedIndexChange = onSeedIndexChange,
                    onNetworkActivityStyleChange = onNetworkActivityStyleChange,
                    onNavigationBarStyleChange = onNavigationBarStyleChange,
                    onAutoHideNavigationBarChange = onAutoHideNavigationBarChange,
                )
            }
        }
    }
}
