// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.connections

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalAppStateStore
import app.R
import app.collectAppState
import coil3.compose.AsyncImage
import engine.mihomo.runtime.ConnectionRouteKind
import engine.mihomo.runtime.MihomoConnection
import features.monitoring.ConnectionMonitorStatus
import features.monitoring.ConnectionSort
import features.monitoring.MonitoringConnectionsFocusState
import features.monitoring.overlay.ConnectionOverlayToggle
import features.monitoring.MonitoringDnsSummary
import features.monitoring.MonitoringIntent
import features.monitoring.MonitoringRulesSummary
import features.monitoring.MonitoringScaffold
import features.monitoring.MonitoringMenuGroupHeader
import features.monitoring.MonitoringPauseButton
import features.monitoring.MonitoringStatusHeader
import features.monitoring.MonitoringStatusMenu
import features.monitoring.MonitoringValueRow
import features.monitoring.formatMonitoringRatePair
import features.monitoring.ObserveMonitoring
import features.monitoring.buildMonitoringConnectionsFocusState
import features.monitoring.clearDisplayedConnections
import features.monitoring.discardDisplayedConnection
import features.monitoring.dns.DnsRecord
import features.monitoring.dns.DnsRecordFilter
import features.monitoring.dns.filterDnsRecords
import features.monitoring.reduceConnections
import features.monitoring.resolveDisplayedConnections
import features.proxy.app.model.ProxyAppIconRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import system.ANDROID_APP_ICON_SIZE_DP
import ui.components.AsteriskActionButton
import ui.components.AsteriskCancelButton
import ui.components.AsteriskDropdownMenuItem
import ui.components.AsteriskExpansionIndicator
import ui.components.AsteriskFilterChip
import ui.components.AsteriskSelectionCard
import ui.components.AsteriskTopBarControls
import ui.layout.LocalAdaptiveWindow
import ui.layout.rememberPageGutter
import ui.theme.AsteriskMotion
import ui.theme.AsteriskShapeTokens
import utils.toReadableBytes
import ui.icons.AsteriskIcons as Icons

private enum class ConnectionMonitorTab(val routeFilter: ConnectionRouteKind? = null) {
    App,
    Proxy(ConnectionRouteKind.Proxy),
    Direct(ConnectionRouteKind.Direct),
    Dns,
    Rules;

    val replacesConnections: Boolean
        get() = this == Dns || this == Rules
}

private val ConnectionAppLookupDispatcher = Dispatchers.IO.limitedParallelism(1)

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
    var rulesQuery by rememberSaveable { mutableStateOf("") }
    var dnsQuery by rememberSaveable { mutableStateOf("") }
    var dnsFilter by rememberSaveable { mutableStateOf<DnsRecordFilter?>(null) }

    // The upstream block the user tapped on a DNS record card. It narrows the
    // record list to the lookups that went through that server.
    var selectedDnsUpstream by rememberSaveable { mutableStateOf<String?>(null) }
    val dnsAvailable by services.monitoring.dnsAvailable.collectAsState(initial = null)
    val tabs = remember(dnsAvailable) {
        ConnectionMonitorTab.entries.filter { it != ConnectionMonitorTab.Dns || dnsAvailable == true }
    }
    var selectedTab by rememberSaveable { mutableStateOf(ConnectionMonitorTab.App) }
    val tab = selectedTab.takeIf { it in tabs } ?: ConnectionMonitorTab.App
    LaunchedEffect(dnsAvailable) {
        if (dnsAvailable == false && selectedTab == ConnectionMonitorTab.Dns) {
            selectedTab = ConnectionMonitorTab.App
        }
    }
    val showRules = tab == ConnectionMonitorTab.Rules
    val showDns = tab == ConnectionMonitorTab.Dns
    var sort by rememberSaveable { mutableStateOf(ConnectionSort.Traffic) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val rulesListState = rememberLazyListState()
    val dnsListState = rememberLazyListState()
    var expandedAppKeys by remember { mutableStateOf(emptySet<String>()) }
    var showCloseAllConfirmation by rememberSaveable { mutableStateOf(false) }
    var operationInProgress by rememberSaveable { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var frozenDns by remember { mutableStateOf<MonitoringDnsSummary?>(null) }
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
            paused = false
            frozenDns = null
            selectedId = null
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
    val liveIds = remember(pageSnapshot.active) { pageSnapshot.active.mapTo(HashSet(), MihomoConnection::id) }
    val closeFailed = stringResource(R.string.monitor_connections_close_failed)
    val closeAllFailed = stringResource(R.string.monitor_connections_close_all_failed)
    ObserveMonitoring(
        when {
            showRules -> MonitoringIntent.Rules
            showDns -> MonitoringIntent.Dns
            else -> MonitoringIntent.Connections
        },
    )

    val displayedConnections = resolveDisplayedConnections(
        latest = connections,
        frozen = frozenConnections,
        paused = paused,
    )
    val visibleConnections = remember(listedConnections, query, tab, sort) {
        if (tab.replacesConnections) emptyList() else reduceConnections(
            connections = listedConnections,
            query = query,
            route = tab.routeFilter,
            sort = sort,
        )
    }
    val appResolver = remember(context) { ConnectionAppResolver(context) }
    val appGroups = if (tab == ConnectionMonitorTab.App) {
        val groups by produceState(emptyList<ConnectionAppGroup>(), visibleConnections, appResolver) {
            value = withContext(ConnectionAppLookupDispatcher) {
                groupConnectionsByApp(visibleConnections, appResolver::resolve)
            }
        }
        groups
    } else emptyList()
    val appRows = remember(appGroups, expandedAppKeys) {
        buildConnectionAppRows(appGroups, expandedAppKeys)
    }
    val rulesState = when {
        !appState.proxyRunning -> MonitoringRulesSummary()
        monitoring.rules.status == ConnectionMonitorStatus.ServiceStopped ->
            MonitoringRulesSummary(status = ConnectionMonitorStatus.Loading)
        else -> monitoring.rules
    }
    val filteredRules = remember(rulesState.rules, rulesQuery) {
        filterRules(rulesState.rules, rulesQuery)
    }
    val dnsState = if (showDns && appState.proxyRunning) {
        val dns = frozenDns ?: services.monitoring.dns.collectAsState().value
        if (dns.status == ConnectionMonitorStatus.ServiceStopped) {
            MonitoringDnsSummary(status = ConnectionMonitorStatus.Loading)
        } else dns
    } else MonitoringDnsSummary()
    val filteredDnsRecords = when {
        !showDns -> emptyList()
        dnsFilter == null && dnsQuery.isBlank() && selectedDnsUpstream == null -> dnsState.records
        else -> {
            val filter = dnsFilter
            val query = dnsQuery
            val upstream = selectedDnsUpstream
            val records = dnsState.records
            key(filter, query, upstream) {
                val filtered by produceState<List<DnsRecord>?>(null, records) {
                    value = withContext(Dispatchers.Default) { filterDnsRecords(records, filter, query, upstream) }
                }
                filtered
            }
        }
    }

    fun closeConnection(connection: MihomoConnection) {
        if (operationInProgress || !appState.proxyRunning || connection.id !in liveIds) return
        if (selectedId == connection.id) selectedId = null
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

    @Composable
    fun ConnectionCardItem(
        connection: MihomoConnection,
        modifier: Modifier = Modifier,
    ) {
        val app = if (tab.routeFilter != null) {
            key(connection.uid, connection.process, connection.processPath) {
                val identity by produceState<ConnectionAppIdentity?>(null, appResolver) {
                    value = withContext(ConnectionAppLookupDispatcher) { appResolver.resolve(connection) }
                }
                identity
            }
        } else null
        ConnectionCard(
            connection = connection,
            app = app,
            iconSizePx = iconSizePx,
            selected = selectedId == connection.id,
            onOpen = {
                selectedId = if (selectedId == connection.id) null else connection.id
            },
            onClose = { closeConnection(connection) },
            closeEnabled = !showClosed && appState.proxyRunning &&
                connection.id in liveIds && !operationInProgress,
            detectedClosedAt = if (showClosed) displayedHistory.closed[connection.id]?.detectedAtMillis else null,
            modifier = modifier,
        )
    }

    MonitoringScaffold(
        searchQuery = when {
            showRules -> rulesQuery
            showDns -> dnsQuery
            else -> query
        },
        onSearchQueryChange = { value ->
            when {
                showRules -> rulesQuery = value
                showDns -> dnsQuery = value
                else -> query = value
            }
        },
        searchPlaceholder = stringResource(
            when {
                showRules -> R.string.monitor_rules_search
                showDns -> R.string.monitor_dns_search
                else -> R.string.monitor_connections_search
            },
        ),
        title = stringResource(R.string.monitor_connections_title),
        beforeSearch = { ConnectionOverlayToggle(appState.proxyRunning) },
        outerPadding = padding,
        toolbar = {
            AsteriskTopBarControls {
                ConnectionControls(
                    tabs = tabs,
                    tab = tab,
                    onTabChange = { next ->
                        selectedTab = next
                        if (next.replacesConnections) {
                            showCloseAllConfirmation = false
                        }
                    },
                )
            }
        },
    ) { contentPadding ->
        if (showRules) {
            RulesMonitorContent(
                state = rulesState,
                filteredRules = filteredRules,
                searching = rulesQuery.isNotBlank(),
                listState = rulesListState,
                contentPadding = contentPadding,
            )
            return@MonitoringScaffold
        }
        if (showDns) {
            DnsMonitorContent(
                status = dnsState.status,
                records = dnsState.records,
                filteredRecords = filteredDnsRecords,
                paused = frozenDns != null,
                onTogglePaused = { frozenDns = dnsState.takeIf { frozenDns == null } },
                error = if (dnsState.unsupported) stringResource(R.string.monitor_dns_unsupported) else "",
                hasActiveFilter = dnsFilter != null || dnsQuery.isNotBlank() || selectedDnsUpstream != null,
                filter = dnsFilter,
                onFilterChange = { dnsFilter = it },
                selectedUpstream = selectedDnsUpstream,
                onUpstreamSelect = { next -> selectedDnsUpstream = next },
                iconSizePx = iconSizePx,
                listState = dnsListState,
                contentPadding = contentPadding,
            )
            return@MonitoringScaffold
        }
        val selectedConnection = selectedId?.let { id -> visibleConnections.firstOrNull { it.id == id } }
        ConnectionMonitorLayout(
            contentPadding = contentPadding,
            selection = selectedConnection,
            title = stringResource(R.string.monitor_connections_details),
            selectionKey = MihomoConnection::id,
            onDismiss = { selectedId = null },
            list = { modifier ->
                LazyColumn(
                    state = listState,
                    modifier = modifier,
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
                            sort = sort,
                            onSortChange = { sort = it },
                            closeAllEnabled = appState.proxyRunning && liveIds.isNotEmpty() && !operationInProgress,
                            onCloseAll = { showCloseAllConfirmation = true },
                            paused = paused,
                            onTogglePaused = {
                                if (!paused) {
                                    frozenConnections = connections
                                    frozenPageSnapshot = pageSnapshot
                                }
                                paused = !paused
                            },
                            showClosed = showClosed,
                            onShowClosedChange = { closed ->
                                showClosed = closed
                                selectedId = null
                            },
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
                                if (query.isBlank() && tab.routeFilter == null) {
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
                            if (tab == ConnectionMonitorTab.App) {
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
                                        is ConnectionAppRow.Link -> ConnectionCardItem(
                                            connection = row.connection,
                                            modifier = modifier.then(NestedConnectionModifier),
                                        )
                                    }
                                }
                            } else {
                                items(visibleConnections, key = MihomoConnection::id) { connection ->
                                    ConnectionCardItem(connection)
                                }
                            }
                        }
                    }
                }
            },
            details = { connection ->
                DetailRow(
                    stringResource(R.string.monitor_connections_rate),
                    formatConnectionRate(connection),
                )
                ConnectionDetails(
                    connection = connection,
                    policyChain = connectionPolicyChain(connection),
                    detectedClosedAt = if (showClosed) {
                        displayedHistory.closed[connection.id]?.detectedAtMillis
                    } else null,
                )
            },
        )
    }

    if (!tab.replacesConnections && showCloseAllConfirmation) {
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
    sort: ConnectionSort,
    onSortChange: (ConnectionSort) -> Unit,
    closeAllEnabled: Boolean,
    onCloseAll: () -> Unit,
    paused: Boolean,
    onTogglePaused: () -> Unit,
    showClosed: Boolean,
    onShowClosedChange: (Boolean) -> Unit,
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    val activeLabel = stringResource(R.string.monitor_connections_active)
    val closedLabel = stringResource(R.string.monitor_connections_closed)
    val summary = when (state.status) {
        ConnectionMonitorStatus.ServiceStopped -> stringResource(R.string.monitor_service_not_enabled)
        ConnectionMonitorStatus.Loading -> stringResource(R.string.monitor_loading)
        ConnectionMonitorStatus.Error -> state.error.ifBlank { stringResource(R.string.monitor_data_unavailable) }
        ConnectionMonitorStatus.Available -> stringResource(
            R.string.monitor_connections_focus_routes,
            state.proxyCount,
            state.directCount,
        )
    }
    MonitoringStatusHeader(
        title = stringResource(R.string.monitor_connections_total),
        value = state.activeCount?.toString() ?: "—",
        summary = summary,
        modifier = ContentWidthModifier,
        statusRowOffset = 4.dp,
        titleActionWidth = 96.dp,
        titleAction = { actionsModifier ->
            Row(actionsModifier) {
                IconButton(onClick = onCloseAll, enabled = closeAllEnabled) {
                    Icon(
                        imageVector = Icons.Rounded.LinkOff,
                        contentDescription = stringResource(R.string.monitor_connections_close_all),
                        modifier = Modifier.size(20.dp),
                    )
                }
                MonitoringPauseButton(
                    paused = paused,
                    contentDescription = stringResource(
                        if (paused) R.string.monitor_connections_resume else R.string.monitor_connections_pause,
                    ),
                    onClick = onTogglePaused,
                )
            }
        },
        controls = {
            val stateLabel = if (showClosed) closedLabel else activeLabel
            MonitoringStatusMenu(
                label = "$stateLabel · ${connectionSortLabel(sort)}",
                expanded = showOptionsMenu,
                onExpandedChange = { showOptionsMenu = it },
            ) {
                MonitoringMenuGroupHeader(stringResource(R.string.monitor_connections_menu_state))
                listOf(false, true).forEach { closed ->
                    AsteriskDropdownMenuItem(
                        text = if (closed) closedLabel else activeLabel,
                        selected = closed == showClosed,
                        onClick = {
                            showOptionsMenu = false
                            onShowClosedChange(closed)
                        },
                    )
                }
                MonitoringMenuGroupHeader(stringResource(R.string.monitor_connections_menu_sort))
                ConnectionSort.entries.forEach { option ->
                    AsteriskDropdownMenuItem(
                        text = connectionSortLabel(option),
                        selected = sort == option,
                        onClick = {
                            showOptionsMenu = false
                            onSortChange(option)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun ConnectionControls(
    tabs: List<ConnectionMonitorTab>,
    tab: ConnectionMonitorTab,
    onTabChange: (ConnectionMonitorTab) -> Unit,
) {
    Row(
        modifier = ContentWidthModifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { option ->
            AsteriskFilterChip(
                selected = tab == option,
                onClick = { onTabChange(option) },
                label = connectionMonitorTabLabel(option),
            )
        }
    }
}

@Composable
private fun ConnectionAppGroupCard(
    group: ConnectionAppGroup,
    expanded: Boolean,
    iconSizePx: Int,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    val label = group.label.ifBlank { stringResource(R.string.monitor_connections_unknown_source) }
    val packageName = group.packageName
    val connectionCountDescription = pluralStringResource(
        R.plurals.monitor_connections_app_count,
        group.connectionCount,
        group.connectionCount,
    )
    Card(
        onClick = onToggle,
        modifier = ContentWidthModifier.then(modifier),
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
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
            Spacer(Modifier.width(8.dp))
            Text(
                text = group.connectionCount.toString(),
                modifier = Modifier.clearAndSetSemantics { contentDescription = connectionCountDescription },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            if (LocalAdaptiveWindow.current.supportsWideContent) Spacer(Modifier.width(6.dp))
            AsteriskExpansionIndicator(
                expanded = expanded,
                contentDescription = stringResource(R.string.monitor_connections_app_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ConnectionAppIcon(
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
    app: ConnectionAppIdentity?,
    iconSizePx: Int,
    selected: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    closeEnabled: Boolean,
    modifier: Modifier = Modifier,
    detectedClosedAt: Long? = null,
) {
    val policyChain = connectionPolicyChain(connection)
    AsteriskSelectionCard(
        selected = selected,
        onClick = onOpen,
        modifier = ContentWidthModifier.then(modifier).semantics { this.selected = selected },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app != null) {
                ConnectionAppIcon(
                    packageName = app.packageName,
                    label = app.label,
                    iconSizePx = iconSizePx,
                )
                Spacer(Modifier.width(12.dp))
            }
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
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.monitor_connections_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onClose, enabled = closeEnabled) {
                Icon(Icons.Rounded.LinkOff, stringResource(R.string.monitor_connections_close_one))
            }
        }
    }
}

@Composable
private fun ConnectionDetails(
    connection: MihomoConnection,
    policyChain: String,
    detectedClosedAt: Long?,
) {
    val providerSources = connection.providerChains.filter(String::isNotBlank).distinct().joinToString(" · ")
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

// The core reports the final outbound first, followed by its enclosing groups.
private fun connectionPolicyChain(connection: MihomoConnection): String =
    connection.chains.asReversed().joinToString(" → ")

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    MonitoringValueRow(label, value)
}

@Composable
internal fun StatusCard(message: String, loading: Boolean = false) {
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
private fun connectionMonitorTabLabel(tab: ConnectionMonitorTab): String = stringResource(
    when (tab) {
        ConnectionMonitorTab.App -> R.string.monitor_filter_app
        ConnectionMonitorTab.Proxy -> R.string.monitor_filter_proxy
        ConnectionMonitorTab.Direct -> R.string.monitor_filter_direct
        ConnectionMonitorTab.Rules -> R.string.monitor_filter_rules
        ConnectionMonitorTab.Dns -> R.string.monitor_filter_dns
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

@Composable
private fun formatConnectionRate(connection: MihomoConnection): String =
    formatMonitoringRatePair(connection.uploadBytesPerSecond, connection.downloadBytesPerSecond)

private fun formatDuration(startedAtMillis: Long?, endedAtMillis: Long? = null): String {
    val start = startedAtMillis ?: return "—"
    val elapsed = ((endedAtMillis ?: System.currentTimeMillis()) - start).coerceAtLeast(0L)
    return DateUtils.formatElapsedTime(elapsed / 1_000L)
}

private val ContentWidthModifier = Modifier.widthIn(max = ConnectionMonitorContentMaxWidth).fillMaxWidth()

/** Inset applied to the connections listed under their application. */
private val NestedConnectionModifier = Modifier.padding(start = 12.dp)

private fun Set<String>.toggleAppGroup(key: String): Set<String> =
    if (key in this) this - key else this + key
