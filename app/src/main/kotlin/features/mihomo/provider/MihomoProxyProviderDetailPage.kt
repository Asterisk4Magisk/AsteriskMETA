// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.mihomo.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import app.collectAppState
import engine.mihomo.runtime.MihomoProxyProviderNode
import engine.mihomo.runtime.MihomoProxyProviderRuntimeDetail
import engine.mihomo.runtime.MihomoDelayResult
import engine.mihomo.runtime.MihomoDelayStatus
import engine.mihomo.runtime.MihomoDelayTarget
import engine.mihomo.runtime.MihomoProxyNodeId
import engine.mihomo.runtime.withDelayResult
import engine.mihomo.DefaultMihomoDelayTestUrl
import features.mihomo.MihomoDelayToolbar
import features.mihomo.displayMihomoProtocolName
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskFilterChip
import ui.components.AsteriskInfoChip
import ui.components.AsteriskPinnedSearchArea
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.theme.AsteriskMotion
import ui.theme.ExpressiveShapeRole
import ui.icons.AsteriskIcons as Icons

@Composable
fun MihomoProxyProviderDetailPage(
    padding: PaddingValues,
    providerName: String,
) {
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val appState by LocalAppStateStore.current.collectAppState()
    val services = LocalAppServices.current
    val delayTestingTarget by remember(services.mihomoRuntime) {
        services.mihomoRuntime.state
            .map { state -> state.delayTestingTarget }
            .distinctUntilChanged()
    }.collectAsState(initial = null)
    val appContext = LocalContext.current.applicationContext
    val tipNotifier = services.tipNotifier
    val delayFailedMessage = stringResource(R.string.mihomo_proxies_delay_failed)
    val delayDoneMessage = stringResource(R.string.mihomo_proxies_delay_done)
    var declarationsState by remember { mutableStateOf(ProviderDeclarationsState(loading = true)) }
    var runtimeDetail by remember { mutableStateOf<MihomoProxyProviderRuntimeDetail?>(null) }
    var runtimeLoading by remember { mutableStateOf(false) }
    var runtimeError by remember { mutableStateOf("") }
    var runtimeReloadToken by remember { mutableIntStateOf(0) }
    var latestDelayResult by remember(providerName) { mutableStateOf<MihomoDelayResult?>(null) }
    var nodeSearchQuery by rememberSaveable { mutableStateOf("") }
    var nodeFilter by remember { mutableStateOf(ProxyProviderNodeFilter.All) }
    val nodeFilterPagerState = rememberPagerState(
        initialPage = ProxyProviderNodeFilter.All.ordinal,
        pageCount = { ProxyProviderNodeFilter.entries.size },
    )

    val provider = declarationsState.providers.firstOrNull { declaration -> declaration.name == providerName }

    val pagerSpatialMotion = AsteriskMotion.spatial<Float>()
    val nodeFilterPagerScope = rememberCoroutineScope()
    val selectNodeFilter: (ProxyProviderNodeFilter) -> Unit = { filter ->
        nodeFilter = filter
        if (nodeFilterPagerState.targetPage != filter.ordinal) {
            nodeFilterPagerScope.launch {
                nodeFilterPagerState.animateScrollToPage(
                    page = filter.ordinal,
                    animationSpec = pagerSpatialMotion,
                )
            }
        }
    }

    LaunchedEffect(nodeFilterPagerState) {
        snapshotFlow { nodeFilterPagerState.targetPage }
            .collect { page ->
                val filter = proxyProviderNodeFilterForPage(page)
                if (nodeFilter != filter) {
                    nodeFilter = filter
                }
            }
    }

    LaunchedEffect(appState) {
        declarationsState = ProviderDeclarationsState(loading = true)
        declarationsState = loadProxyProviderDeclarations(
            context = appContext,
            appState = appState,
            dataDir = appContext.mihomoProviderDataDir(),
        )
    }

    LaunchedEffect(provider, appState) {
        latestDelayResult = null
        runtimeDetail = null
    }

    LaunchedEffect(provider, appState, runtimeReloadToken) {
        runtimeError = ""
        if (provider == null) {
            runtimeLoading = false
            return@LaunchedEffect
        }

        runtimeLoading = true
        services.mihomoRuntime.getProxyProviderDetail(appState, provider.name)
            .onSuccess { detail ->
                runtimeDetail = mergeMihomoProviderRuntimeRefresh(detail, latestDelayResult)
            }
            .onFailure { error -> runtimeError = error.message.orEmpty() }
        runtimeLoading = false
    }

    fun testNodeDelay(node: MihomoProxyProviderNode) {
        val detail = runtimeDetail ?: return
        if (delayTestingTarget != null) return
        nodeFilterPagerScope.launch {
            services.mihomoRuntime.testProxyDelay(
                appState = appState,
                proxyId = MihomoProxyNodeId(node.name, detail.name),
                testUrl = detail.testUrl.ifBlank { DefaultMihomoDelayTestUrl },
                expectedStatus = detail.expectedStatus,
            ).onSuccess { result ->
                val mergedResult = mergeMihomoProviderDelayResults(latestDelayResult, result)
                latestDelayResult = mergedResult
                runtimeDetail = runtimeDetail?.withDelayResult(mergedResult)
                tipNotifier.show(delayDoneMessage)
                runtimeReloadToken += 1
            }.onFailure { error ->
                tipNotifier.showError(error, delayFailedMessage)
            }
        }
    }

    fun testProviderDelay() {
        val detail = runtimeDetail ?: return
        if (delayTestingTarget != null) return
        nodeFilterPagerScope.launch {
            services.mihomoRuntime.testProviderDelay(
                appState = appState,
                providerName = detail.name,
                testUrl = detail.testUrl.ifBlank { DefaultMihomoDelayTestUrl },
                expectedStatus = detail.expectedStatus,
            ).onSuccess { result ->
                val mergedResult = mergeMihomoProviderDelayResults(latestDelayResult, result)
                latestDelayResult = mergedResult
                runtimeDetail = runtimeDetail?.withDelayResult(mergedResult)
                tipNotifier.show(delayDoneMessage)
                runtimeReloadToken += 1
            }.onFailure { error ->
                tipNotifier.showError(error, delayFailedMessage)
            }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text(provider?.name ?: providerName, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                )
                AsteriskPinnedSearchArea(
                    query = nodeSearchQuery,
                    onQueryChange = { nodeSearchQuery = it },
                    placeholder = stringResource(R.string.mihomo_provider_nodes_search),
                    clearContentDescription = stringResource(R.string.common_clear),
                ) {
                    ProxyProviderNodeFilterChips(
                        selected = nodeFilter,
                        onSelected = selectNodeFilter,
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val listPadding = pageListPadding(contentPadding, bottomExtra = 104.dp)
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = nodeFilterPagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                val pageFilter = proxyProviderNodeFilterForPage(page)
                val pageListState = rememberLazyListState()
                LazyColumn(
                    state = pageListState,
                    contentPadding = listPadding,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when {
                        declarationsState.loading || provider == null -> {
                            item(key = if (declarationsState.loading) "loading" else "missing") {
                                ProviderMessageCard(
                                    text = if (declarationsState.loading) {
                                        stringResource(R.string.mihomo_dashboard_network_detection_checking)
                                    } else {
                                        stringResource(R.string.mihomo_provider_missing)
                                    },
                                )
                            }
                        }

                        else -> {
                            providerNodeItems(
                                detail = runtimeDetail,
                                loading = runtimeLoading,
                                error = runtimeError,
                                searchQuery = nodeSearchQuery,
                                filter = pageFilter,
                                delayTestingTarget = delayTestingTarget,
                                delayTestingEnabled = delayTestingTarget == null,
                                onDelayTest = ::testNodeDelay,
                                onRetry = { runtimeReloadToken += 1 },
                            )
                        }
                    }
                }
            }
            runtimeDetail?.let { detail ->
                MihomoDelayToolbar(
                    enabled = !runtimeLoading && delayTestingTarget == null && detail.nodes.isNotEmpty(),
                    testing = delayTestingTarget == MihomoDelayTarget.Provider(detail.name),
                    onDelayTest = ::testProviderDelay,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.providerNodeItems(
    detail: MihomoProxyProviderRuntimeDetail?,
    loading: Boolean,
    error: String,
    searchQuery: String,
    filter: ProxyProviderNodeFilter,
    delayTestingTarget: MihomoDelayTarget?,
    delayTestingEnabled: Boolean,
    onDelayTest: (MihomoProxyProviderNode) -> Unit,
    onRetry: () -> Unit,
) {
    val allNodes = detail?.nodes.orEmpty()
    val nodes = reduceMihomoProxyProviderNodes(allNodes, searchQuery, filter)
    when {
        loading -> item(key = "node_loading") {
            ProviderMessageCard(text = stringResource(R.string.mihomo_dashboard_network_detection_checking))
        }

        error.isNotBlank() -> item(key = "node_error") {
            ProviderMessageCard(
                text = stringResource(
                    R.string.mihomo_provider_runtime_error,
                    stringResource(R.string.mihomo_provider_runtime_unavailable),
                    error,
                ),
                actionText = stringResource(R.string.common_retry),
                onAction = onRetry,
            )
        }

        allNodes.isEmpty() -> item(key = "node_empty") {
            ProviderEmptyState(text = stringResource(R.string.mihomo_provider_nodes_empty))
        }

        nodes.isEmpty() -> item(key = "node_search_empty") {
            ProviderEmptyState()
        }

        else -> items(
            items = nodes,
            key = { node -> node.name },
        ) { node ->
            ProviderNodeCard(
                node = node,
                testing = delayTestingTarget == MihomoDelayTarget.Node(
                    MihomoProxyNodeId(node.name, detail?.name),
                ),
                delayTestingEnabled = delayTestingEnabled,
                onDelayTest = { onDelayTest(node) },
                modifier = Modifier.animateItem(
                    fadeInSpec = AsteriskMotion.effects(),
                    placementSpec = AsteriskMotion.spatial(),
                    fadeOutSpec = AsteriskMotion.effects(),
                ),
            )
        }
    }
}

@Composable
private fun ProxyProviderNodeFilterChips(
    selected: ProxyProviderNodeFilter,
    onSelected: (ProxyProviderNodeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProxyProviderNodeFilter.entries.forEach { filter ->
            AsteriskFilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = stringResource(
                    when (filter) {
                        ProxyProviderNodeFilter.All -> R.string.common_all
                        ProxyProviderNodeFilter.Available -> R.string.mihomo_provider_filter_available
                        ProxyProviderNodeFilter.Timeout -> R.string.mihomo_provider_filter_timeout
                    },
                ),
            )
        }
    }
}

@Composable
private fun ProviderNodeCard(
    node: MihomoProxyProviderNode,
    testing: Boolean,
    delayTestingEnabled: Boolean,
    onDelayTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = node.subtitle.takeUnless { value ->
        value.isBlank() || value.equals(node.type, ignoreCase = true)
    }.orEmpty()
    AsteriskExpressiveCard(
        modifier = modifier.fillMaxWidth().heightIn(min = 76.dp),
        role = ExpressiveShapeRole.ContentCard,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.title.ifBlank { node.name },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsteriskInfoChip(
                        text = node.protocolText().displayMihomoProtocolName(),
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .height(40.dp)
                    .clip(ui.theme.AsteriskShapeTokens.Pill)
                    .clickable(enabled = delayTestingEnabled && !testing, onClick = onDelayTest),
                contentAlignment = Alignment.Center,
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Text(
                        text = node.delayText(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = node.providerDelayColor(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun MihomoProxyProviderNode.protocolText(): String {
    return type.ifBlank { subtitle.ifBlank { "Proxy" } }
}

@Composable
private fun MihomoProxyProviderNode.providerDelayColor(): Color {
    val darkTheme = isSystemInDarkTheme()
    return when {
        delayStatus == MihomoDelayStatus.Timeout || delayStatus == MihomoDelayStatus.Failed -> {
            MaterialTheme.colorScheme.error
        }
        delayStatus == null || delay == null -> MaterialTheme.colorScheme.onSurfaceVariant
        delay < 100 -> if (darkTheme) Color(0xFF6BD58A) else Color(0xFF128A3C)
        delay < 200 -> if (darkTheme) Color(0xFFFFC857) else Color(0xFFD18A00)
        delay < 400 -> if (darkTheme) Color(0xFFFF9B63) else Color(0xFFE06400)
        else -> MaterialTheme.colorScheme.error
    }
}

@Composable
private fun MihomoProxyProviderNode.delayText(): String {
    return when {
        delayStatus == MihomoDelayStatus.Timeout -> stringResource(R.string.mihomo_provider_delay_timeout)
        delayStatus == MihomoDelayStatus.Failed -> stringResource(R.string.mihomo_proxies_delay_failed)
        delayStatus == null || delay == null -> stringResource(R.string.mihomo_proxies_delay_not_tested)
        else -> stringResource(R.string.mihomo_provider_delay_value, delay)
    }
}
