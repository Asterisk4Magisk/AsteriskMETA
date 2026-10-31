// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.R
import engine.mihomo.runtime.MihomoDelayStatus
import engine.mihomo.runtime.MihomoProxyGroup
import engine.mihomo.runtime.MihomoProxyNode
import kotlin.math.roundToInt
import ui.components.AsteriskExpansionIndicator
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskFilterChip
import ui.components.AsteriskInfoChip
import ui.components.AsteriskPageCard
import ui.components.AsteriskSelectionCard
import ui.components.AsteriskTonalButton
import ui.icons.AsteriskIcons as Icons
import ui.isInDarkTheme

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
    compact: Boolean = false,
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
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).padding(2.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = onDelayTest,
                                enabled = delayTestEnabled,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = stringResource(R.string.mihomo_proxies_group_test),
                                    modifier = Modifier.size(18.dp),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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
        } else {
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            ProtocolDelayLine(
                protocol = node.type.displayMihomoProtocolName(),
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
    AsteriskSelectionCard(
        selected = selected,
        onClick = onSelect,
        enabled = selectionEnabled,
        modifier = modifier.height(MihomoProxyNodeCardHeight),
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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                color = when {
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                },
                contentColor = when {
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text = protocol,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Surface(
                onClick = onClick,
                enabled = enabled,
                color = Color.Transparent,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = enabled, onClick = onClick),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = delayText,
                        style = if (compact) {
                            MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        } else {
                            MaterialTheme.typography.labelSmall
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
