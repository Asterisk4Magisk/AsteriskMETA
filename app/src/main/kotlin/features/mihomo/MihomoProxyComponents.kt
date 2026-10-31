// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.R
import app.modes.MihomoProxyGroupLayoutExpandable
import app.modes.MihomoProxyGroupLayoutTabs
import app.modes.MihomoProxyLayoutAuto
import app.modes.MihomoProxyLayoutDouble
import app.modes.MihomoProxyLayoutMultiple
import app.modes.MihomoProxyLayoutSingle
import app.modes.MihomoProxySortDefault
import app.modes.MihomoProxySortDelay
import app.modes.MihomoProxySortName
import engine.mihomo.runtime.MihomoDelayStatus
import engine.mihomo.runtime.MihomoProxyGroup
import engine.mihomo.runtime.MihomoProxyNode
import kotlin.math.roundToInt
import ui.components.AsteriskCheckbox
import ui.components.AsteriskDropdownMenuItem
import ui.components.AsteriskExpansionIndicator
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskFilterChip
import ui.components.AsteriskInfoChip
import ui.components.AsteriskPageCard
import ui.components.AsteriskSelectionCard
import ui.components.AsteriskTonalButton
import ui.icons.AsteriskIcons as Icons
import ui.isInDarkTheme
import ui.theme.AsteriskMotion

internal val MihomoProxyNodeCardHeight = 112.dp
internal val MihomoProxyNodeCardPadding = PaddingValues(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 10.dp)
internal val MihomoProxyNodeGridSpacing = 12.dp

@Composable
internal fun MihomoProxyGroupCard(
    group: MihomoProxyGroup,
    currentNodeName: String,
    nodeCount: Int,
    expanded: Boolean,
    testing: Boolean,
    delayTestEnabled: Boolean,
    onToggleExpand: () -> Unit,
    onDelayTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AsteriskExpressiveCard(
        modifier = modifier,
        expanded = expanded,
        selected = expanded,
        onClick = onToggleExpand,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AsteriskInfoChip(
                        text = group.type.uppercase(),
                        emphasized = expanded,
                    )
                    Text(
                        text = "($nodeCount)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (currentNodeName.isNotBlank()) {
                    Text(
                        text = currentNodeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(2.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(
                        onClick = onDelayTest,
                        enabled = delayTestEnabled,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = stringResource(R.string.mihomo_proxies_group_test),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AsteriskExpansionIndicator(
                    expanded = expanded,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}

@Composable
internal fun MihomoProxyNodeCard(
    node: MihomoProxyNode,
    selected: Boolean,
    selectionEnabled: Boolean,
    delayTestEnabled: Boolean,
    compact: Boolean,
    testing: Boolean,
    onSelect: () -> Unit,
    onDelayTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxSize().padding(MihomoProxyNodeCardPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = node.title.ifBlank { node.name },
                    modifier = Modifier.weight(1f),
                    style = if (compact) {
                        MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                        )
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            ProtocolDelayLine(
                protocol = node.type,
                providerName = node.providerName.orEmpty(),
                delay = node.delay,
                delayStatus = node.delayStatus,
                selected = selected,
                testing = testing,
                enabled = delayTestEnabled,
                compact = compact,
                onClick = onDelayTest,
            )
        }
    }
    val cardModifier = modifier
        .height(MihomoProxyNodeCardHeight)
        .semantics { this.selected = selected }
    AsteriskSelectionCard(
        selected = selected,
        enabled = selectionEnabled,
        onClick = onSelect,
        modifier = cardModifier,
    ) {
        content()
    }
}

@Composable
internal fun ProtocolDelayLine(
    protocol: String,
    providerName: String,
    delay: Int?,
    delayStatus: MihomoDelayStatus?,
    selected: Boolean,
    testing: Boolean,
    enabled: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val delayText = when {
        testing -> ""
        delayStatus == MihomoDelayStatus.Timeout -> stringResource(R.string.mihomo_proxies_delay_timeout)
        delayStatus == MihomoDelayStatus.Failed -> stringResource(R.string.mihomo_proxies_delay_failed)
        delayStatus == null || delay == null -> stringResource(R.string.mihomo_proxies_delay_not_tested)
        else -> "$delay ms"
    }.trim()
    val viewConfiguration = LocalViewConfiguration.current
    val chipTouchTargetViewConfiguration = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val minimumTouchTargetSize = DpSize(28.dp, 28.dp)
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides chipTouchTargetViewConfiguration) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(ui.theme.AsteriskShapeTokens.Pill)
                .clickable(enabled = enabled, onClick = onClick),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsteriskInfoChip(
                    text = protocol.displayMihomoProtocolName(compact = compact),
                    modifier = Modifier.weight(1f, fill = false),
                    emphasized = selected,
                    textStyle = if (compact) {
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    } else {
                        MaterialTheme.typography.labelSmall
                    },
                )
                if (providerName.isNotBlank()) {
                    Text(
                        text = providerName,
                        modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier.padding(end = 8.dp).height(28.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Text(
                        text = delayText,
                        style = if (compact) {
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        } else {
                            MaterialTheme.typography.labelMedium
                        },
                        fontWeight = FontWeight.Medium,
                        color = delayColor(delay, delayStatus),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
internal fun MihomoProxyEmptyCard() {
    Text(
        text = stringResource(R.string.common_empty),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun MihomoProxyNoConfigurationCard(
    onAddConfiguration: () -> Unit,
) {
    AsteriskPageCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = ui.theme.AsteriskShapeTokens.SmallContainer,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                    )
                }
            }
            Text(
                text = stringResource(R.string.mihomo_proxies_no_profile_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.mihomo_proxies_no_profile_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AsteriskTonalButton(
                text = stringResource(R.string.mihomo_profile_add),
                icon = Icons.Rounded.Add,
                onClick = onAddConfiguration,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
internal fun MihomoProxyLoadingCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
        )
        Text(
            text = stringResource(R.string.mihomo_proxies_loading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ProxyGroupTabs(
    groups: List<MihomoProxyGroup>,
    selectedGroupName: String,
    onSelectedGroupNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onReselectCurrentGroup: () -> Unit = {},
) {
    if (groups.isEmpty()) return
    val tabScrollState = rememberScrollState()
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var tabBounds by remember { mutableStateOf<Map<String, ProxyGroupTabBounds>>(emptyMap()) }
    val selectedBounds = tabBounds[selectedGroupName]

    LaunchedEffect(selectedGroupName, selectedBounds, viewportWidthPx, tabScrollState.maxValue) {
        if (selectedBounds == null || viewportWidthPx <= 0) return@LaunchedEffect
        val targetScroll = resolveProxyTabScrollTarget(
            visibleStart = tabScrollState.value,
            viewportWidth = viewportWidthPx,
            tabStart = selectedBounds.leftPx,
            tabEnd = selectedBounds.leftPx + selectedBounds.widthPx,
            maxScroll = tabScrollState.maxValue,
        )
        if (targetScroll != tabScrollState.value) {
            tabScrollState.animateScrollTo(targetScroll)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size -> viewportWidthPx = size.width }
            .horizontalScroll(tabScrollState),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { group ->
                AsteriskFilterChip(
                    selected = group.name == selectedGroupName,
                    onClick = {
                        if (group.name == selectedGroupName) {
                            onReselectCurrentGroup()
                        } else {
                            onSelectedGroupNameChange(group.name)
                        }
                    },
                    label = group.name,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val bounds = ProxyGroupTabBounds(
                            leftPx = coordinates.positionInParent().x.roundToInt(),
                            widthPx = coordinates.size.width,
                        )
                        if (tabBounds[group.name] != bounds) {
                            tabBounds = tabBounds + (group.name to bounds)
                        }
                    },
                )
            }
        }
    }
}

internal fun resolveProxyTabScrollTarget(
    visibleStart: Int,
    viewportWidth: Int,
    tabStart: Int,
    tabEnd: Int,
    maxScroll: Int,
): Int {
    val visibleEnd = visibleStart + viewportWidth
    return when {
        tabStart < visibleStart -> tabStart
        tabEnd > visibleEnd -> tabEnd - viewportWidth
        else -> visibleStart
    }.coerceIn(0, maxScroll)
}

internal data class ProxyGroupTabBounds(
    val leftPx: Int,
    val widthPx: Int,
)

@Composable
internal fun MihomoProxyOptionsMenu(
    interruptConnections: Boolean,
    excludeNotSelectable: Boolean,
    groupLayout: Int,
    layout: Int,
    sort: Int,
    onInterruptConnectionsChange: (Boolean) -> Unit,
    onExcludeNotSelectableChange: (Boolean) -> Unit,
    onGroupLayoutChange: (Int) -> Unit,
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

internal enum class ProxyOptionsLevel {
    Main,
    GroupLayout,
    Layout,
    Sort,
}

private val MihomoProxyOptionsMenuWidth = 224.dp

@Composable
internal fun delayColor(delay: Int?, status: MihomoDelayStatus?): Color {
    val darkTheme = isInDarkTheme()
    return when {
        status == MihomoDelayStatus.Timeout || status == MihomoDelayStatus.Failed -> if (darkTheme) Color(0xFFF12522) else Color(0xFFE94634)
        status == null || delay == null -> MaterialTheme.colorScheme.onSurfaceVariant
        delay < 0 -> if (darkTheme) Color(0xFFF12522) else Color(0xFFE94634)
        delay < 100 -> if (darkTheme) Color(0xFF6BD58A) else Color(0xFF128A3C)
        delay < 200 -> if (darkTheme) Color(0xFFFFC857) else Color(0xFFD18A00)
        delay < 300 -> if (darkTheme) Color(0xFFFF9B63) else Color(0xFFE06400)
        else -> if (darkTheme) Color(0xFFF12522) else Color(0xFFE94634)
    }
}
