// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.R
import app.modes.MihomoProxyGroupColumnsDouble
import app.modes.MihomoProxyGroupColumnsFollow
import app.modes.MihomoProxyGroupColumnsMultiple
import app.modes.MihomoProxyGroupColumnsSingle
import app.modes.MihomoProxyGroupLayoutExpandable
import app.modes.MihomoProxyGroupLayoutTabs
import ui.components.IconAccent
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun SettingsAppSection(
    languageOptions: List<String>,
    languageMode: Int,
    colorModeOptions: List<String>,
    colorMode: Int,
    keyColorOptions: List<String>,
    seedIndex: Int,
    onColorModeChange: (Int) -> Unit,
    onSeedIndexChange: (Int) -> Unit,
    onLanguageModeChange: (Int) -> Unit,
    autoHideNavigationBar: Boolean,
    onAutoHideNavigationBarChange: (Boolean) -> Unit,
    proxyGroupLayout: Int,
    onProxyGroupLayoutChange: (Int) -> Unit,
    proxyGroupColumns: Int,
    onProxyGroupColumnsChange: (Int) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_app))
    SettingsSectionCard {
        OverlayDropdownPreference(
            title = stringResource(R.string.settings_language),
            icon = Icons.Rounded.Language,
            items = languageOptions,
            selectedIndex = languageMode,
            onSelectedIndexChange = onLanguageModeChange,
            accent = IconAccent.MaskBlue,
        )
    }
    ThemeSettingsContent(
        colorModeOptions = colorModeOptions,
        colorMode = colorMode,
        keyColorOptions = keyColorOptions,
        seedIndex = seedIndex,
        onColorModeChange = onColorModeChange,
        onSeedIndexChange = onSeedIndexChange,
    )
    val autoHideNavigationBarTitle = stringResource(R.string.settings_auto_hide_navigation_bar)
    val autoHideNavigationBarSummary = stringResource(R.string.settings_auto_hide_navigation_bar_summary)
    if (settingsRowMatchesQuery(autoHideNavigationBarTitle, autoHideNavigationBarSummary, autoHideNavigationBar.toString())) {
        SettingsSectionCard {
            SwitchPreference(
                title = autoHideNavigationBarTitle,
                icon = Icons.Rounded.ExpandMore,
                summary = autoHideNavigationBarSummary,
                checked = autoHideNavigationBar,
                onCheckedChange = onAutoHideNavigationBarChange,
                accent = IconAccent.MaskBlueVariant,
            )
        }
    }
    val proxyGroupLayoutTitle = stringResource(R.string.settings_proxy_group_layout)
    val proxyGroupLayoutSummary = stringResource(
        if (proxyGroupLayout == MihomoProxyGroupLayoutExpandable) {
            R.string.settings_proxy_group_layout_expandable_summary
        } else {
            R.string.settings_proxy_group_layout_tabs_summary
        }
    )
    val proxyGroupLayoutOptions = listOf(
        stringResource(R.string.settings_proxy_group_layout_expandable),
        stringResource(R.string.settings_proxy_group_layout_tabs),
    )
    if (settingsRowMatchesQuery(proxyGroupLayoutTitle, proxyGroupLayoutSummary, proxyGroupLayoutOptions.joinToString())) {
        SettingsSectionCard {
            OverlayDropdownPreference(
                title = proxyGroupLayoutTitle,
                icon = Icons.Rounded.ViewAgenda,
                summary = proxyGroupLayoutSummary,
                items = proxyGroupLayoutOptions,
                selectedIndex = if (proxyGroupLayout == MihomoProxyGroupLayoutTabs) 1 else 0,
                onSelectedIndexChange = { index ->
                    onProxyGroupLayoutChange(
                        if (index == 1) MihomoProxyGroupLayoutTabs else MihomoProxyGroupLayoutExpandable
                    )
                },
                accent = IconAccent.MaskPurple,
            )
        }
    }
    if (proxyGroupLayout == MihomoProxyGroupLayoutExpandable) {
        val proxyGroupColumnsTitle = stringResource(R.string.settings_proxy_group_columns)
        val proxyGroupColumnsSummary = stringResource(
            when (proxyGroupColumns) {
                MihomoProxyGroupColumnsSingle -> R.string.settings_proxy_group_columns_single
                MihomoProxyGroupColumnsDouble -> R.string.settings_proxy_group_columns_double
                MihomoProxyGroupColumnsMultiple -> R.string.settings_proxy_group_columns_multiple
                else -> R.string.settings_proxy_group_columns_follow_summary
            }
        )
        val proxyGroupColumnsOptions = listOf(
            stringResource(R.string.settings_proxy_group_columns_follow),
            stringResource(R.string.settings_proxy_group_columns_single),
            stringResource(R.string.settings_proxy_group_columns_double),
            stringResource(R.string.settings_proxy_group_columns_multiple),
        )
        if (settingsRowMatchesQuery(proxyGroupColumnsTitle, proxyGroupColumnsSummary, proxyGroupColumnsOptions.joinToString())) {
            SettingsSectionCard {
                OverlayDropdownPreference(
                    title = proxyGroupColumnsTitle,
                    icon = Icons.Rounded.ViewColumn,
                    summary = proxyGroupColumnsSummary,
                    items = proxyGroupColumnsOptions,
                    selectedIndex = when (proxyGroupColumns) {
                        MihomoProxyGroupColumnsSingle -> 1
                        MihomoProxyGroupColumnsDouble -> 2
                        MihomoProxyGroupColumnsMultiple -> 3
                        else -> 0
                    },
                    onSelectedIndexChange = { index ->
                        onProxyGroupColumnsChange(
                            when (index) {
                                1 -> MihomoProxyGroupColumnsSingle
                                2 -> MihomoProxyGroupColumnsDouble
                                3 -> MihomoProxyGroupColumnsMultiple
                                else -> MihomoProxyGroupColumnsFollow
                            }
                        )
                    },
                    accent = IconAccent.MaskPurple,
                )
            }
        }
    }
}
