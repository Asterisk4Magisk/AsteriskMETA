// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.R
import app.modes.MihomoProxyGroupColumnsDouble
import app.modes.MihomoProxyGroupColumnsFollow
import app.modes.MihomoProxyGroupColumnsMultiple
import app.modes.MihomoProxyGroupColumnsSingle
import app.modes.MihomoProxyGroupLayoutExpandable
import app.modes.MihomoProxyGroupLayoutTabs
import app.modes.MihomoProxyLayoutAuto
import app.modes.MihomoProxyLayoutDouble
import app.modes.MihomoProxyLayoutMultiple
import app.modes.MihomoProxyLayoutSingle
import app.modes.MihomoProxySortDefault
import app.modes.MihomoProxySortDelay
import app.modes.MihomoProxySortName
import ui.components.AsteriskCheckbox
import ui.components.AsteriskDropdownMenuItem
import ui.icons.AsteriskIcons as Icons
import ui.theme.AsteriskMotion

internal enum class ProxyOptionsLevel {
    Main,
    GroupLayout,
    GroupColumns,
    Layout,
    Sort,
}

private val MihomoProxyOptionsMenuWidth = 224.dp

@Composable
internal fun MihomoProxyOptionsMenu(
    interruptConnections: Boolean,
    excludeNotSelectable: Boolean,
    groupLayout: Int,
    groupColumns: Int,
    layout: Int,
    sort: Int,
    onInterruptConnectionsChange: (Boolean) -> Unit,
    onExcludeNotSelectableChange: (Boolean) -> Unit,
    onGroupLayoutChange: (Int) -> Unit,
    onGroupColumnsChange: (Int) -> Unit,
    onLayoutChange: (Int) -> Unit,
    onSortChange: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var level by rememberSaveable { mutableStateOf(ProxyOptionsLevel.Main) }
    val dismissMenu = {
        expanded = false
        level = ProxyOptionsLevel.Main
    }
    val groupLayoutLabel = stringResource(
        when (groupLayout) {
            MihomoProxyGroupLayoutTabs -> R.string.mihomo_proxies_option_group_layout_tabs
            else -> R.string.mihomo_proxies_option_group_layout_expandable
        },
    )
    val groupColumnsLabel = stringResource(
        when (groupColumns) {
            MihomoProxyGroupColumnsSingle -> R.string.mihomo_proxies_option_group_columns_single
            MihomoProxyGroupColumnsDouble -> R.string.mihomo_proxies_option_group_columns_double
            MihomoProxyGroupColumnsMultiple -> R.string.mihomo_proxies_option_group_columns_multiple
            else -> R.string.mihomo_proxies_option_group_columns_follow
        },
    )
    val layoutLabel = stringResource(
        when (layout) {
            MihomoProxyLayoutSingle -> R.string.mihomo_proxies_option_layout_single
            MihomoProxyLayoutDouble -> R.string.mihomo_proxies_option_layout_double
            MihomoProxyLayoutMultiple -> R.string.mihomo_proxies_option_layout_multiple
            else -> R.string.mihomo_proxies_option_layout_auto
        },
    )
    val sortLabel = stringResource(
        when (sort) {
            MihomoProxySortName -> R.string.mihomo_proxies_option_sort_name
            MihomoProxySortDelay -> R.string.mihomo_proxies_option_sort_delay
            else -> R.string.mihomo_proxies_option_sort_default
        },
    )
    val menuSpatialMotion = AsteriskMotion.fastSpatial<IntOffset>()
    val menuSizeMotion = AsteriskMotion.fastSpatial<IntSize>()
    val menuEffectsMotion = AsteriskMotion.fastEffects<Float>()
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.mihomo_proxies_options))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = dismissMenu,
            modifier = Modifier.width(MihomoProxyOptionsMenuWidth),
        ) {
            AnimatedContent(
                targetState = level,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = AsteriskMotion.horizontalSlideFade(
                    spatialSpec = menuSpatialMotion,
                    effectsSpec = menuEffectsMotion,
                    sizeSpec = menuSizeMotion,
                ) {
                    if (targetState == ProxyOptionsLevel.Main) -1 else 1
                },
                contentAlignment = Alignment.TopStart,
                label = "proxy-options-level",
            ) { currentLevel ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (currentLevel) {
                        ProxyOptionsLevel.Main -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mihomo_proxies_option_interrupt_connections)) },
                                onClick = { onInterruptConnectionsChange(!interruptConnections) },
                                leadingIcon = { Icon(Icons.Rounded.LinkOff, contentDescription = null) },
                                trailingIcon = {
                                    AsteriskCheckbox(
                                        checked = interruptConnections,
                                        onCheckedChange = null,
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mihomo_proxies_option_filter_not_selectable)) },
                                onClick = { onExcludeNotSelectableChange(!excludeNotSelectable) },
                                leadingIcon = { Icon(Icons.Rounded.FilterAlt, contentDescription = null) },
                                trailingIcon = {
                                    AsteriskCheckbox(
                                        checked = excludeNotSelectable,
                                        onCheckedChange = null,
                                    )
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(stringResource(R.string.mihomo_proxies_option_group_layout))
                                        Text(
                                            groupLayoutLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = { level = ProxyOptionsLevel.GroupLayout },
                                leadingIcon = { Icon(Icons.Rounded.ViewAgenda, contentDescription = null) },
                                trailingIcon = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                            )
                            if (groupLayout == MihomoProxyGroupLayoutExpandable) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.mihomo_proxies_option_group_columns))
                                            Text(
                                                groupColumnsLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = { level = ProxyOptionsLevel.GroupColumns },
                                    leadingIcon = { Icon(Icons.Rounded.ViewColumn, contentDescription = null) },
                                    trailingIcon = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(stringResource(R.string.mihomo_proxies_option_layout))
                                        Text(
                                            layoutLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = { level = ProxyOptionsLevel.Layout },
                                leadingIcon = { Icon(Icons.Rounded.ViewModule, contentDescription = null) },
                                trailingIcon = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(stringResource(R.string.mihomo_proxies_option_sort))
                                        Text(
                                            sortLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = { level = ProxyOptionsLevel.Sort },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null) },
                                trailingIcon = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                            )
                        }

                        ProxyOptionsLevel.GroupLayout -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mihomo_proxies_option_group_layout)) },
                                onClick = { level = ProxyOptionsLevel.Main },
                                leadingIcon = { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) },
                            )
                            HorizontalDivider()
                            listOf(
                                Triple(
                                    MihomoProxyGroupLayoutExpandable,
                                    R.string.mihomo_proxies_option_group_layout_expandable,
                                    Icons.Rounded.ViewAgenda,
                                ),
                                Triple(
                                    MihomoProxyGroupLayoutTabs,
                                    R.string.mihomo_proxies_option_group_layout_tabs,
                                    Icons.Rounded.ViewColumn,
                                ),
                            ).forEach { (value, label, _) ->
                                AsteriskDropdownMenuItem(
                                    text = stringResource(label),
                                    selected = groupLayout == value,
                                    onClick = {
                                        dismissMenu()
                                        onGroupLayoutChange(value)
                                    },
                                )
                            }
                        }

                        ProxyOptionsLevel.GroupColumns -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mihomo_proxies_option_group_columns)) },
                                onClick = { level = ProxyOptionsLevel.Main },
                                leadingIcon = { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) },
                            )
                            HorizontalDivider()
                            listOf(
                                Triple(
                                    MihomoProxyGroupColumnsFollow,
                                    R.string.mihomo_proxies_option_group_columns_follow,
                                    Icons.Rounded.AutoAwesome,
                                ),
                                Triple(
                                    MihomoProxyGroupColumnsSingle,
                                    R.string.mihomo_proxies_option_group_columns_single,
                                    Icons.Rounded.ViewAgenda,
                                ),
                                Triple(
                                    MihomoProxyGroupColumnsDouble,
                                    R.string.mihomo_proxies_option_group_columns_double,
                                    Icons.Rounded.ViewColumn,
                                ),
                                Triple(
                                    MihomoProxyGroupColumnsMultiple,
                                    R.string.mihomo_proxies_option_group_columns_multiple,
                                    Icons.Rounded.GridView,
                                ),
                            ).forEach { (value, label, _) ->
                                AsteriskDropdownMenuItem(
                                    text = stringResource(label),
                                    selected = groupColumns == value,
                                    onClick = {
                                        dismissMenu()
                                        onGroupColumnsChange(value)
                                    },
                                )
                            }
                        }

                        ProxyOptionsLevel.Layout -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mihomo_proxies_option_layout)) },
                                onClick = { level = ProxyOptionsLevel.Main },
                                leadingIcon = { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) },
                            )
                            HorizontalDivider()
                            listOf(
                                Triple(
                                    MihomoProxyLayoutAuto,
                                    R.string.mihomo_proxies_option_layout_auto,
                                    Icons.Rounded.AutoAwesome,
                                ),
                                Triple(
                                    MihomoProxyLayoutSingle,
                                    R.string.mihomo_proxies_option_layout_single,
                                    Icons.Rounded.ViewAgenda,
                                ),
                                Triple(
                                    MihomoProxyLayoutDouble,
                                    R.string.mihomo_proxies_option_layout_double,
                                    Icons.Rounded.ViewColumn,
                                ),
                                Triple(
                                    MihomoProxyLayoutMultiple,
                                    R.string.mihomo_proxies_option_layout_multiple,
                                    Icons.Rounded.GridView,
                                ),
                            ).forEach { (value, label, _) ->
                                AsteriskDropdownMenuItem(
                                    text = stringResource(label),
                                    selected = layout == value,
                                    onClick = {
                                        dismissMenu()
                                        onLayoutChange(value)
                                    },
                                )
                            }
                        }

                        ProxyOptionsLevel.Sort -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mihomo_proxies_option_sort)) },
                                onClick = { level = ProxyOptionsLevel.Main },
                                leadingIcon = { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) },
                            )
                            HorizontalDivider()
                            listOf(
                                Triple(
                                    MihomoProxySortDefault,
                                    R.string.mihomo_proxies_option_sort_default,
                                    Icons.AutoMirrored.Rounded.Sort,
                                ),
                                Triple(
                                    MihomoProxySortName,
                                    R.string.mihomo_proxies_option_sort_name,
                                    Icons.Rounded.SortByAlpha,
                                ),
                                Triple(
                                    MihomoProxySortDelay,
                                    R.string.mihomo_proxies_option_sort_delay,
                                    Icons.Rounded.Speed,
                                ),
                            ).forEach { (value, label, _) ->
                                AsteriskDropdownMenuItem(
                                    text = stringResource(label),
                                    selected = sort == value,
                                    onClick = {
                                        dismissMenu()
                                        onSortChange(value)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
