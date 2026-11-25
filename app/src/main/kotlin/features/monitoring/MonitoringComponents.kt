// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.TextButton
import ui.components.AsteriskSearchTopAppBar
import ui.components.AsteriskScaffold
import ui.components.AsteriskTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.LocalNavigator
import app.R
import ui.components.AsteriskStatusCard
import ui.components.AsteriskDropdownAnchor
import ui.components.AsteriskStatusCardHeader
import ui.components.AsteriskValueRow
import utils.toReadableBytes
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun MonitoringScaffold(
    title: String,
    outerPadding: PaddingValues,
    actions: @Composable () -> Unit = {},
    toolbar: @Composable () -> Unit = {},
    searchQuery: String? = null,
    onSearchQueryChange: (String) -> Unit = {},
    searchPlaceholder: String = "",
    beforeSearch: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val navigator = LocalNavigator.current
    AsteriskScaffold(
        topBar = {
            Column {
                if (searchQuery != null) {
                    AsteriskSearchTopAppBar(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = searchPlaceholder,
                        beforeSearch = { beforeSearch() },
                        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = navigator::pop) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back),
                                )
                            }
                        },
                        actions = { actions() },
                    )
                } else {
                    AsteriskTopAppBar(
                        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = navigator::pop) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back),
                                )
                            }
                        },
                        actions = { actions() },
                    )
                }
                toolbar()
            }
        },
    ) { innerPadding ->
        content(
            PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + outerPadding.calculateBottomPadding(),
            ),
        )
    }
}

@Composable
internal fun MonitoringSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    headerContent: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                headerContent()
            }
            Box(modifier = Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

@Composable
internal fun MonitoringStatusHeader(
    title: String,
    value: String,
    summary: String?,
    modifier: Modifier = Modifier,
    compactStatus: Boolean = false,
    controls: @Composable RowScope.() -> Unit = {},
    metrics: @Composable RowScope.() -> Unit = {},
    titleAction: @Composable ((Modifier) -> Unit)? = null,
    titleActionWidth: Dp = 48.dp,
    statusRowOffset: Dp = 0.dp,
) {
    AsteriskStatusCard(
        modifier = modifier,
        status = summary,
        compactStatus = compactStatus,
        controls = controls,
        statusRowOffset = statusRowOffset,
    ) {
        AsteriskStatusCardHeader(action = titleAction, actionWidth = titleActionWidth) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            metrics()
        }
    }
}

@Composable
internal fun MonitoringValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    valueMaxLines: Int = 3,
    trailing: @Composable (() -> Unit)? = null,
) {
    AsteriskValueRow(
        title = label,
        value = value,
        modifier = modifier,
        titleMinWidth = 112.dp,
        valueMaxLines = valueMaxLines,
        verticalAlignment = verticalAlignment,
        trailing = trailing,
    )
}

@Composable
internal fun formatMonitoringRate(bytesPerSecond: Long?): String =
    if (bytesPerSecond == null) "—"
    else stringResource(R.string.monitor_speed_per_second, bytesPerSecond.toReadableBytes())

@Composable
internal fun formatMonitoringRatePair(upload: Long?, download: Long?): String {
    if (upload == null && download == null) return "—"
    return stringResource(R.string.monitor_rate_pair, formatMonitoringRate(upload), formatMonitoringRate(download))
}

@Composable
internal fun MonitoringPauseButton(
    paused: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun MonitoringStatusMenu(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
        TextButton(
            onClick = { onExpandedChange(true) },
            // Align the arrow with the header actions.
            modifier = Modifier.offset(x = 12.dp),
        ) {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(4.dp))
            AsteriskDropdownAnchor(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                content = content,
            )
        }
    }
}

@Composable
internal fun MonitoringMenuGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
