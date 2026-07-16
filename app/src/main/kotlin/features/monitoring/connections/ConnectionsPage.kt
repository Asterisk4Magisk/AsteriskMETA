// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.monitoring.connections

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.R
import engine.mihomo.runtime.MihomoConnection
import features.monitoring.ConnectionMonitorStatus
import features.monitoring.ConnectionRouteFilter
import features.monitoring.ConnectionSort
import features.monitoring.MonitoringConnectionsFocusState
import features.monitoring.MonitoringIntent
import features.monitoring.MonitoringScaffold
import features.monitoring.MonitoringStatusHeader
import features.monitoring.MonitoringValueRow
import features.monitoring.ObserveMonitoring
import features.monitoring.buildMonitoringConnectionsFocusState
import features.monitoring.clearDisplayedConnections
import features.monitoring.discardDisplayedConnection
import features.monitoring.reduceConnections
import features.monitoring.resolveDisplayedConnections
import kotlinx.coroutines.launch
import ui.components.AsteriskActionButton
import ui.components.AsteriskFilterChip
import ui.components.AsteriskPinnedSearchArea
import ui.layout.rememberPageGutter
import ui.theme.AsteriskMotion
import utils.toReadableBytes
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun ConnectionsMonitorPage(padding: PaddingValues) {
    val horizontalPadding = rememberPageGutter()
    val services = LocalAppServices.current
    val monitoring by services.monitoring.state.collectAsState()
    val connections = monitoring.connections
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var route by rememberSaveable { mutableStateOf(ConnectionRouteFilter.All) }
    var sort by rememberSaveable { mutableStateOf(ConnectionSort.Traffic) }
    var selected by remember { mutableStateOf<MihomoConnection?>(null) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showCloseAllConfirmation by rememberSaveable { mutableStateOf(false) }
    var operationInProgress by rememberSaveable { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var frozenConnections by remember { mutableStateOf(connections) }
    val closeFailed = stringResource(R.string.monitor_connections_close_failed)
    val closeAllFailed = stringResource(R.string.monitor_connections_close_all_failed)
    ObserveMonitoring(MonitoringIntent.Connections)

    val displayedConnections = resolveDisplayedConnections(
        latest = connections,
        frozen = frozenConnections,
        paused = paused,
    )
    val visibleConnections = remember(displayedConnections.snapshot.connections, query, route, sort) {
        reduceConnections(
            connections = displayedConnections.snapshot.connections,
            query = query,
            route = route,
            sort = sort,
        )
    }

    fun closeConnection(connection: MihomoConnection) {
        if (operationInProgress) return
        operationInProgress = true
        scope.launch {
            services.monitoring.closeConnection(connection.id)
                .onSuccess {
                    frozenConnections = discardDisplayedConnection(frozenConnections, connection.id)
                }
                .onFailure { error -> services.tipNotifier.showError(error, closeFailed) }
            operationInProgress = false
        }
    }

    MonitoringScaffold(
        title = stringResource(R.string.monitor_connections_title),
        outerPadding = padding,
        actions = {
            IconButton(
                onClick = {
                    if (!paused) frozenConnections = connections
                    paused = !paused
                },
            ) {
                Icon(
                    imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = stringResource(
                        if (paused) R.string.monitor_connections_resume else R.string.monitor_connections_pause,
                    ),
                )
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    enabled = displayedConnections.activeCount?.let { it > 0 } == true && !operationInProgress,
                ) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.home_more_actions))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.monitor_connections_close_all)) },
                        leadingIcon = { Icon(Icons.Rounded.LinkOff, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            showCloseAllConfirmation = true
                        },
                    )
                }
            }
        },
        toolbar = {
            AsteriskPinnedSearchArea(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.monitor_connections_search),
                clearContentDescription = stringResource(R.string.common_clear),
            ) {
                ConnectionControls(
                    route = route,
                    onRouteChange = { route = it },
                    sort = sort,
                    onSortChange = { sort = it },
                )
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                top = contentPadding.calculateTopPadding() + 8.dp,
                end = horizontalPadding,
                bottom = contentPadding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item("status") {
                ConnectionsMonitorStatus(
                    state = buildMonitoringConnectionsFocusState(displayedConnections),
                    paused = paused,
                )
            }
            when {
                displayedConnections.status == ConnectionMonitorStatus.ServiceStopped -> item("stopped") {
                    StatusCard(stringResource(R.string.monitor_service_not_enabled))
                }
                displayedConnections.status == ConnectionMonitorStatus.Loading -> item("loading") {
                    StatusCard(stringResource(R.string.monitor_loading), loading = true)
                }
                displayedConnections.status == ConnectionMonitorStatus.Error &&
                    displayedConnections.snapshot.updatedAtMillis == 0L -> item("error") {
                    StatusCard(displayedConnections.error.ifBlank { stringResource(R.string.monitor_data_unavailable) })
                }
                visibleConnections.isEmpty() -> item("empty") {
                    StatusCard(
                        if (query.isBlank() && route == ConnectionRouteFilter.All) {
                            stringResource(R.string.monitor_connections_empty)
                        } else {
                            stringResource(R.string.monitor_connections_no_match)
                        },
                    )
                }
                else -> {
                    if (displayedConnections.stale) item("stale") {
                        StatusCard(stringResource(R.string.monitor_data_stale))
                    }
                    items(visibleConnections, key = MihomoConnection::id) { connection ->
                        ConnectionCard(
                            connection = connection,
                            expanded = selected?.id == connection.id,
                            onOpen = {
                                selected = if (selected?.id == connection.id) null else connection
                            },
                            onClose = {
                                if (selected?.id == connection.id) selected = null
                                closeConnection(connection)
                            },
                            closeEnabled = !operationInProgress,
                        )
                    }
                }
            }
        }
    }

    if (showCloseAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showCloseAllConfirmation = false },
            title = { Text(stringResource(R.string.monitor_connections_close_all)) },
            text = {
                val activeCount = displayedConnections.activeCount ?: 0
                Text(
                    pluralStringResource(
                        R.plurals.monitor_connections_close_all_message,
                        activeCount,
                        activeCount,
                    ),
                )
            },
            confirmButton = {
                AsteriskActionButton(
                    text = stringResource(R.string.monitor_connections_close_all_confirm),
                    icon = Icons.Rounded.LinkOff,
                    onClick = {
                        showCloseAllConfirmation = false
                        operationInProgress = true
                        scope.launch {
                            services.monitoring.closeAllConnections()
                                .onSuccess {
                                    frozenConnections = clearDisplayedConnections(frozenConnections)
                                }
                                .onFailure { error -> services.tipNotifier.showError(error, closeAllFailed) }
                            operationInProgress = false
                        }
                    },
                )
            },
            dismissButton = {
                AsteriskActionButton(
                    text = stringResource(R.string.common_cancel),
                    icon = Icons.Rounded.Close,
                    onClick = { showCloseAllConfirmation = false },
                )
            },
        )
    }
}

@Composable
private fun ConnectionsMonitorStatus(
    state: MonitoringConnectionsFocusState,
    paused: Boolean,
) {
    val summary = when (state.status) {
        ConnectionMonitorStatus.ServiceStopped -> stringResource(R.string.monitor_service_not_enabled)
        ConnectionMonitorStatus.Loading -> stringResource(R.string.monitor_loading)
        ConnectionMonitorStatus.Error -> state.error.ifBlank { stringResource(R.string.monitor_data_unavailable) }
        ConnectionMonitorStatus.Available -> {
            val routeSummary = stringResource(
                R.string.monitor_connections_focus_routes,
                state.proxyCount,
                state.directCount,
            )
            stringResource(
                R.string.monitor_connections_status_summary,
                routeSummary,
                stringResource(
                    if (paused) R.string.monitor_connections_paused else R.string.monitor_connections_live,
                ),
            )
        }
    }
    MonitoringStatusHeader(
        title = stringResource(R.string.monitor_connections_active),
        value = state.activeCount?.toString() ?: "—",
        summary = summary,
        modifier = ContentWidthModifier,
    )
}

@Composable
private fun ConnectionControls(
    route: ConnectionRouteFilter,
    onRouteChange: (ConnectionRouteFilter) -> Unit,
    sort: ConnectionSort,
    onSortChange: (ConnectionSort) -> Unit,
) {
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = ContentWidthModifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectionRouteFilter.entries.forEach { option ->
            AsteriskFilterChip(
                selected = route == option,
                onClick = { onRouteChange(option) },
                label = connectionRouteLabel(option),
            )
        }
        Box {
            AsteriskFilterChip(
                selected = false,
                onClick = { showSortMenu = true },
                label = connectionSortLabel(sort),
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null) },
            )
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                ConnectionSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(connectionSortLabel(option)) },
                        onClick = {
                            onSortChange(option)
                            showSortMenu = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    connection: MihomoConnection,
    expanded: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    closeEnabled: Boolean,
) {
    Card(
        onClick = onOpen,
        modifier = ContentWidthModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = connection.destinationAddress.ifBlank { stringResource(R.string.monitor_value_unknown) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(
                        connection.process.ifBlank { stringResource(R.string.monitor_connections_unknown_source) },
                        connection.network.uppercase().ifBlank { stringResource(R.string.monitor_value_unknown) },
                        formatDuration(connection.startedAtMillis),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val routing = (connection.chains + connection.providerChains).joinToString(" → ")
                    .ifBlank { listOf(connection.rule, connection.rulePayload).filter(String::isNotBlank).joinToString(" · ") }
                if (routing.isNotBlank()) {
                    Text(
                        text = routing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = formatConnectionRate(connection),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(R.string.monitor_connections_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onClose, enabled = closeEnabled) {
                Icon(Icons.Rounded.LinkOff, stringResource(R.string.monitor_connections_close_one))
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = AsteriskMotion.expandEnter(),
            exit = AsteriskMotion.expandExit(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            ) {
                DetailRow(stringResource(R.string.monitor_connections_source), connection.sourceAddress)
                DetailRow(stringResource(R.string.monitor_connections_target), connection.destinationAddress)
                DetailRow(stringResource(R.string.monitor_connections_process), connection.process)
                DetailRow(stringResource(R.string.monitor_connections_network), connection.network.uppercase())
                DetailRow(
                    stringResource(R.string.monitor_connections_rule),
                    listOf(connection.rule, connection.rulePayload).filter(String::isNotBlank).joinToString(" · "),
                )
                DetailRow(
                    stringResource(R.string.monitor_connections_chain),
                    (connection.chains + connection.providerChains).joinToString(" → "),
                )
                DetailRow(stringResource(R.string.monitor_connections_download), connection.downloadBytes.toReadableBytes())
                DetailRow(stringResource(R.string.monitor_connections_upload), connection.uploadBytes.toReadableBytes())
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    MonitoringValueRow(label, value)
}

@Composable
private fun StatusCard(message: String, loading: Boolean = false) {
    Card(
        modifier = ContentWidthModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun connectionRouteLabel(route: ConnectionRouteFilter): String = stringResource(
    when (route) {
        ConnectionRouteFilter.All -> R.string.monitor_filter_all
        ConnectionRouteFilter.Proxy -> R.string.monitor_filter_proxy
        ConnectionRouteFilter.Direct -> R.string.monitor_filter_direct
    },
)

@Composable
private fun connectionSortLabel(sort: ConnectionSort): String = stringResource(
    when (sort) {
        ConnectionSort.Traffic -> R.string.monitor_sort_rate
        ConnectionSort.StartedAt -> R.string.monitor_sort_started
        ConnectionSort.Target -> R.string.monitor_sort_target
    },
)

private fun formatConnectionRate(connection: MihomoConnection): String {
    val down = connection.downloadBytesPerSecond
    val up = connection.uploadBytesPerSecond
    return formatRatePair(down, up)
}

private fun formatRatePair(download: Long?, upload: Long?): String {
    if (download == null && upload == null) return "—"
    return "↓ ${(download ?: 0L).toReadableBytes()}/s · ↑ ${(upload ?: 0L).toReadableBytes()}/s"
}

private fun formatDuration(startedAtMillis: Long?): String {
    val start = startedAtMillis ?: return "—"
    val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(0L)
    return DateUtils.formatElapsedTime(elapsed / 1_000L)
}

private val ContentWidthModifier = Modifier.fillMaxWidth().widthIn(max = 840.dp)
