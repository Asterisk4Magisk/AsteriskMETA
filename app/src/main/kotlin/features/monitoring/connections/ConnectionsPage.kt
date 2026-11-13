// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.connections

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalAppStateStore
import app.R
import app.collectAppState
import coil3.compose.AsyncImage
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
import features.monitoring.restrictsRoute
import features.proxy.app.model.ProxyAppIconRequest
import kotlinx.coroutines.launch
import system.ANDROID_APP_ICON_SIZE_DP
import ui.components.AsteriskCancelButton
import ui.components.AsteriskTopBarControls
import ui.components.AsteriskActionButton
import ui.components.AsteriskDropdownAnchor
import ui.components.AsteriskDropdownMenuItem
import ui.components.AsteriskExpansionIndicator
import ui.components.AsteriskFilterChip
import ui.layout.rememberPageGutter
import ui.theme.AsteriskMotion
import ui.theme.AsteriskShapeTokens
import utils.toReadableBytes
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun ConnectionsMonitorPage(padding: PaddingValues) {
    val horizontalPadding = rememberPageGutter()
    val services = LocalAppServices.current
    val appState by LocalAppStateStore.current.collectAppState()
    val monitoring by services.monitoring.state.collectAsState()
    val connections = monitoring.connections
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) { ANDROID_APP_ICON_SIZE_DP.dp.roundToPx() }
    var query by rememberSaveable { mutableStateOf("") }
    var route by rememberSaveable { mutableStateOf(ConnectionRouteFilter.All) }
    var sort by rememberSaveable { mutableStateOf(ConnectionSort.Traffic) }
    var selected by remember { mutableStateOf<MihomoConnection?>(null) }
    var expandedAppKeys by remember { mutableStateOf(emptySet<String>()) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showCloseAllConfirmation by rememberSaveable { mutableStateOf(false) }
    var operationInProgress by rememberSaveable { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var frozenConnections by remember { mutableStateOf(connections) }
    val history = remember { ConnectionPageHistory(MihomoConnection::id) }
    var pageSnapshot by remember { mutableStateOf(ConnectionPageSnapshot<MihomoConnection>()) }
    var frozenPageSnapshot by remember { mutableStateOf(pageSnapshot) }
    var showClosed by remember { mutableStateOf(false) }
    var previousServiceRunning by remember { mutableStateOf(appState.proxyRunning) }
    LaunchedEffect(appState.proxyRunning, connections) {
        val restarted = !previousServiceRunning && appState.proxyRunning
        previousServiceRunning = appState.proxyRunning
        pageSnapshot = history.update(
            running = appState.proxyRunning,
            successful = connections.status == ConnectionMonitorStatus.Available && !connections.stale,
            snapshotMillis = connections.snapshot.updatedAtMillis,
            connections = connections.snapshot.connections,
            nowMillis = System.currentTimeMillis(),
        )
        if (restarted || !appState.proxyRunning) {
            showCloseAllConfirmation = false
            showMenu = false
            paused = false
            selected = null
            frozenPageSnapshot = pageSnapshot
            frozenConnections = connections
        }
    }
    val displayedHistory = if (paused) frozenPageSnapshot else pageSnapshot
    val listedConnections = if (showClosed) {
        displayedHistory.closed.values.toList().asReversed().map {
            it.connection.copy(uploadBytesPerSecond = 0L, downloadBytesPerSecond = 0L)
        }
    } else displayedHistory.active
    val liveIds = pageSnapshot.active.mapTo(HashSet(), MihomoConnection::id)
    val closeFailed = stringResource(R.string.monitor_connections_close_failed)
    val closeAllFailed = stringResource(R.string.monitor_connections_close_all_failed)
    ObserveMonitoring(MonitoringIntent.Connections)

    val displayedConnections = resolveDisplayedConnections(
        latest = connections,
        frozen = frozenConnections,
        paused = paused,
    )
    val visibleConnections = remember(listedConnections, query, route, sort) {
        reduceConnections(
            connections = listedConnections,
            query = query,
            route = route,
            sort = sort,
        )
    }
    val appResolver = remember(context) { ConnectionAppResolver(context) }
    val appGroups: List<ConnectionAppGroup> = remember(visibleConnections, route) {
        if (route == ConnectionRouteFilter.App) {
            groupConnectionsByApp(visibleConnections, appResolver::resolve)
        } else {
            emptyList()
        }
    }
    val appRows = remember(appGroups, expandedAppKeys) {
        buildConnectionAppRows(appGroups, expandedAppKeys)
    }

    fun closeConnection(connection: MihomoConnection) {
        if (operationInProgress || !appState.proxyRunning || connection.id !in liveIds) return
        operationInProgress = true
        scope.launch {
            services.monitoring.closeConnection(connection.id)
                .onSuccess {
                    frozenConnections = discardDisplayedConnection(frozenConnections, connection.id)
                    frozenPageSnapshot = frozenPageSnapshot.copy(
                        active = frozenPageSnapshot.active.filterNot { it.id == connection.id },
                    )
                }
                .onFailure { error -> services.tipNotifier.showError(error, closeFailed) }
            operationInProgress = false
        }
    }

    // Shared by the flat list and the application grouping so both render identically.
    val connectionCardItem: @Composable (MihomoConnection, Modifier) -> Unit = { connection, modifier ->
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
            closeEnabled = !showClosed && appState.proxyRunning &&
                connection.id in liveIds && !operationInProgress,
            detectedClosedAt = if (showClosed) displayedHistory.closed[connection.id]?.detectedAtMillis else null,
            modifier = modifier,
        )
    }

    MonitoringScaffold(
        searchQuery = query,
        onSearchQueryChange = { query = it },
        searchPlaceholder = stringResource(R.string.monitor_connections_search),
        title = stringResource(R.string.monitor_connections_title),
        outerPadding = padding,
        actions = {
            IconButton(
                onClick = {
                    if (!paused) {
                        frozenConnections = connections
                        frozenPageSnapshot = pageSnapshot
                    }
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
                    enabled = appState.proxyRunning && liveIds.isNotEmpty() && !operationInProgress,
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
            AsteriskTopBarControls {
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
                    state = buildMonitoringConnectionsFocusState(
                        displayedConnections.copy(
                            status = if (showClosed) ConnectionMonitorStatus.Available else displayedConnections.status,
                            activeCount = if (showClosed) listedConnections.size else displayedConnections.activeCount,
                            snapshot = displayedConnections.snapshot.copy(connections = listedConnections),
                        ),
                    ),
                    paused = paused,
                    showClosed = showClosed,
                    onShowClosedChange = { showClosed = it; selected = null },
                )
            }
            when {
                !showClosed && displayedConnections.status == ConnectionMonitorStatus.ServiceStopped -> item("stopped") {
                    StatusCard(stringResource(R.string.monitor_service_not_enabled))
                }
                !showClosed && displayedConnections.status == ConnectionMonitorStatus.Loading -> item("loading") {
                    StatusCard(stringResource(R.string.monitor_loading), loading = true)
                }
                !showClosed && displayedConnections.status == ConnectionMonitorStatus.Error &&
                    displayedConnections.snapshot.updatedAtMillis == 0L -> item("error") {
                    StatusCard(displayedConnections.error.ifBlank { stringResource(R.string.monitor_data_unavailable) })
                }
                visibleConnections.isEmpty() -> item("empty") {
                    StatusCard(
                        if (query.isBlank() && !route.restrictsRoute) {
                            stringResource(
                                if (showClosed) R.string.monitor_connections_closed_empty
                                else R.string.monitor_connections_empty,
                            )
                        } else {
                            stringResource(R.string.monitor_connections_no_match)
                        },
                    )
                }
                else -> {
                    if (displayedConnections.stale) item("stale") {
                        StatusCard(stringResource(R.string.monitor_data_stale))
                    }
                    if (route == ConnectionRouteFilter.App) {
                        items(appRows, key = ConnectionAppRow::key) { row ->
                            val modifier = Modifier.animateItem(
                                fadeInSpec = AsteriskMotion.effects(),
                                placementSpec = AsteriskMotion.spatial(),
                                fadeOutSpec = AsteriskMotion.effects(),
                            )
                            when (row) {
                                is ConnectionAppRow.Group -> ConnectionAppGroupCard(
                                    group = row.group,
                                    expanded = row.group.key in expandedAppKeys,
                                    iconSizePx = iconSizePx,
                                    modifier = modifier,
                                    onToggle = {
                                        expandedAppKeys = expandedAppKeys.toggleAppGroup(row.group.key)
                                    },
                                )
                                is ConnectionAppRow.Link -> connectionCardItem(
                                    row.connection,
                                    modifier.then(NestedConnectionModifier),
                                )
                            }
                        }
                    } else {
                        items(visibleConnections, key = MihomoConnection::id) { connection ->
                            connectionCardItem(connection, ContentWidthModifier)
                        }
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
                    enabled = appState.proxyRunning && liveIds.isNotEmpty() && !operationInProgress,
                    onClick = {
                        showCloseAllConfirmation = false
                        operationInProgress = true
                        scope.launch {
                            services.monitoring.closeAllConnections()
                                .onSuccess {
                                    frozenConnections = clearDisplayedConnections(frozenConnections)
                                    frozenPageSnapshot = frozenPageSnapshot.copy(active = emptyList())
                                }
                                .onFailure { error -> services.tipNotifier.showError(error, closeAllFailed) }
                            operationInProgress = false
                        }
                    },
                )
            },
            dismissButton = {
                AsteriskCancelButton(
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
    showClosed: Boolean,
    onShowClosedChange: (Boolean) -> Unit,
) {
    var showStateMenu by remember { mutableStateOf(false) }
    val activeLabel = stringResource(R.string.monitor_connections_active)
    val closedLabel = stringResource(R.string.monitor_connections_closed)
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
            if (showClosed) routeSummary else stringResource(
                R.string.monitor_connections_status_summary,
                routeSummary,
                stringResource(
                    if (paused) R.string.monitor_connections_paused else R.string.monitor_connections_live,
                ),
            )
        }
    }
    MonitoringStatusHeader(
        title = stringResource(R.string.monitor_connections_total),
        value = state.activeCount?.toString() ?: "—",
        summary = summary,
        modifier = ContentWidthModifier,
        controls = {
            TextButton(
                onClick = { showStateMenu = true },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(text = if (showClosed) closedLabel else activeLabel, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                AsteriskDropdownAnchor(
                    expanded = showStateMenu,
                    onDismissRequest = { showStateMenu = false },
                ) {
                    listOf(false, true).forEach { closed ->
                        AsteriskDropdownMenuItem(
                            text = if (closed) closedLabel else activeLabel,
                            selected = closed == showClosed,
                            onClick = {
                                showStateMenu = false
                                onShowClosedChange(closed)
                            },
                        )
                    }
                }
            }
        },
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
                trailingIcon = {
                    AsteriskDropdownAnchor(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        ConnectionSort.entries.forEach { option ->
                            AsteriskDropdownMenuItem(
                                text = connectionSortLabel(option),
                                selected = sort == option,
                                onClick = {
                                    onSortChange(option)
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ConnectionAppGroupCard(
    group: ConnectionAppGroup,
    expanded: Boolean,
    iconSizePx: Int,
    modifier: Modifier = ContentWidthModifier,
    onToggle: () -> Unit,
) {
    val label = group.label.ifBlank { stringResource(R.string.monitor_connections_unknown_source) }
    val packageName = group.packageName
    Card(
        onClick = onToggle,
        modifier = modifier,
        // One step above the connection cards so a group header reads as their parent.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConnectionAppIcon(
                packageName = packageName,
                label = label,
                iconSizePx = iconSizePx,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.monitor_connections_app_count,
                            group.connectionCount,
                            group.connectionCount,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (packageName != null && packageName != label) {
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AsteriskExpansionIndicator(
                expanded = expanded,
                contentDescription = stringResource(R.string.monitor_connections_app_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectionAppIcon(
    packageName: String?,
    label: String,
    iconSizePx: Int,
) {
    Box(
        modifier = Modifier
            .size(ANDROID_APP_ICON_SIZE_DP.dp)
            .clip(AsteriskShapeTokens.SmallContainer)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (packageName == null) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = ProxyAppIconRequest(packageName = packageName, sizePx = iconSizePx),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
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
    modifier: Modifier = ContentWidthModifier,
    detectedClosedAt: Long? = null,
) {
    // The core reports the final outbound first, followed by its enclosing groups.
    val policyChain = connection.chains.asReversed().joinToString(" → ")
    val providerSources = connection.providerChains.filter(String::isNotBlank).distinct().joinToString(" · ")
    Card(
        onClick = onOpen,
        modifier = modifier,
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
                        formatDuration(connection.startedAtMillis, detectedClosedAt),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val routing = policyChain
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
            AsteriskExpansionIndicator(
                expanded = expanded,
                contentDescription = stringResource(R.string.monitor_connections_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onClose, enabled = closeEnabled) {
                Icon(Icons.Rounded.LinkOff, stringResource(R.string.monitor_connections_close_one))
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = AsteriskMotion.contentEnter(),
            exit = AsteriskMotion.contentExit(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            ) {
                detectedClosedAt?.let { time ->
                    DetailRow(
                        stringResource(R.string.monitor_connections_closed_time),
                        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time)),
                    )
                }
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
                    policyChain,
                )
                if (providerSources.isNotBlank()) {
                    DetailRow(
                        stringResource(R.string.monitor_connections_provider_sources),
                        providerSources,
                    )
                }
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
        ConnectionRouteFilter.All -> R.string.common_all
        ConnectionRouteFilter.Proxy -> R.string.monitor_filter_proxy
        ConnectionRouteFilter.Direct -> R.string.monitor_filter_direct
        ConnectionRouteFilter.App -> R.string.monitor_filter_app
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

private fun formatDuration(startedAtMillis: Long?, endedAtMillis: Long? = null): String {
    val start = startedAtMillis ?: return "—"
    val elapsed = ((endedAtMillis ?: System.currentTimeMillis()) - start).coerceAtLeast(0L)
    return DateUtils.formatElapsedTime(elapsed / 1_000L)
}

private val ContentWidthModifier = Modifier.fillMaxWidth().widthIn(max = 840.dp)

/** Inset applied to the connections listed under their application. */
private val NestedConnectionModifier = ContentWidthModifier.padding(start = 12.dp)

private fun Set<String>.toggleAppGroup(key: String): Set<String> =
    if (key in this) this - key else this + key
