// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.AppServices
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalMainDestinationState
import app.LocalNavigator
import app.LocalUpdateAppState
import app.MihomoOverrideScriptState
import app.R
import app.collectAppState
import app.modes.MihomoProxyGroupLayoutExpandable
import app.modes.MihomoProxyGroupLayoutTabs
import app.navigation.MainDestination
import app.navigation.Route
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.MihomoProviderMetadataCache
import engine.mihomo.hasUsableMihomoProfile
import engine.mihomo.runtime.MihomoDelayTarget
import engine.mihomo.runtime.MihomoProxiesState
import engine.mihomo.runtime.MihomoProxyGroup
import engine.mihomo.runtime.MihomoProxyNode
import engine.mihomo.runtime.MihomoRuntimeState
import engine.mihomo.selectedMihomoProfileOrNull
import engine.mihomo.sha256Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.components.AsteriskScaffold
import ui.components.AsteriskSearchTopAppBar
import ui.components.AsteriskTopBarControls
import ui.icons.AsteriskIcons as Icons
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.theme.AsteriskMotion

private data class MihomoProxyPageRuntimeState(
    val proxies: MihomoProxiesState,
    val delayTestingTarget: MihomoDelayTarget?,
    val lastError: String,
)

private fun MihomoRuntimeState.toProxyPageRuntimeState() = MihomoProxyPageRuntimeState(
    proxies = proxies,
    delayTestingTarget = delayTestingTarget,
    lastError = lastError,
)

private fun AppServices.mihomoProxyPageRuntimeSnapshot() =
    mihomoRuntime.state.value.toProxyPageRuntimeState()

private data class MihomoProviderDetectionSignature(
    val profileId: Int,
    val contentSha256: String,
    val contentPath: String,
    val contentSizeBytes: Long,
    val disableOverrides: Boolean,
    val overrideScripts: List<MihomoOverrideScriptState>,
    val runMode: Int,
    val mihomoMode: Int,
)

@Composable
fun MihomoProxyPage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val appContext = LocalContext.current.applicationContext
    val navigator = LocalNavigator.current
    val mainDestinationState = LocalMainDestinationState.current
    val services = LocalAppServices.current
    val runtimeState by remember(services.mihomoRuntime) {
        services.mihomoRuntime.state
            .map(MihomoRuntimeState::toProxyPageRuntimeState)
            .distinctUntilChanged()
    }.collectAsState(initial = services.mihomoProxyPageRuntimeSnapshot())
    val scope = rememberCoroutineScope()
    val tipNotifier = services.tipNotifier
    val runtimeUnavailableMessage = stringResource(R.string.mihomo_proxies_runtime_unavailable)
    val selectFailedMessage = stringResource(R.string.mihomo_proxies_select_failed)
    val closeConnectionsFailedMessage = stringResource(R.string.mihomo_proxies_interrupt_connections_failed)
    val delayFailedMessage = stringResource(R.string.mihomo_proxies_delay_failed)
    val delayDoneMessage = stringResource(R.string.mihomo_proxies_delay_done)

    val hasProfiles = appState.mihomoProfiles.isNotEmpty()
    val hasUsableProfile = appState.hasUsableMihomoProfile()
    val selectedProfile = appState.selectedMihomoProfileOrNull()
    var pageRefreshInProgress by remember { mutableStateOf(false) }
    val pageRefreshOwnership = remember { MihomoProxyPageRefreshOwnership() }
    LaunchedEffect(
        services.mihomoRuntime,
        hasUsableProfile,
        appState.selectedMihomoProfileId,
        selectedProfile?.contentSha256,
        selectedProfile?.contentPath,
        selectedProfile?.contentSizeBytes,
        selectedProfile?.disableOverrides,
        appState.proxyRunning,
        appState.runMode,
        appState.mihomoMode,
    ) {
        val refreshId = pageRefreshOwnership.beginRefresh()
        if (hasUsableProfile) {
            pageRefreshInProgress = true
            try {
                services.mihomoRuntime.refreshProxies(appState)
            } finally {
                if (pageRefreshOwnership.isCurrent(refreshId)) {
                    pageRefreshInProgress = false
                }
            }
        } else {
            pageRefreshInProgress = false
        }
    }
    var hasProviders by remember { mutableStateOf(false) }
    val providerDetectionSignature = remember(
        appState.selectedMihomoProfileId,
        selectedProfile?.contentSha256,
        selectedProfile?.disableOverrides,
        appState.mihomoOverrideScripts,
        appState.runMode,
        appState.mihomoMode,
    ) {
        MihomoProviderDetectionSignature(
            profileId = appState.selectedMihomoProfileId,
            contentSha256 = selectedProfile?.contentSha256.orEmpty(),
            contentPath = selectedProfile?.contentPath.orEmpty(),
            contentSizeBytes = selectedProfile?.contentSizeBytes ?: 0L,
            disableOverrides = selectedProfile?.disableOverrides ?: false,
            overrideScripts = appState.mihomoOverrideScripts,
            runMode = appState.runMode,
            mihomoMode = appState.mihomoMode,
        )
    }
    LaunchedEffect(hasUsableProfile, providerDetectionSignature) {
        hasProviders = hasUsableProfile && withContext(Dispatchers.IO) {
            runCatching {
                val runtimeProfile = MihomoProfileFactory.buildProfile(appContext, appState)
                MihomoProviderMetadataCache.hasProviders(
                    key = "runtime:${runtimeProfile.sha256Hex()}",
                ) {
                    runtimeProfile
                }
            }.getOrDefault(false)
        }
    }
    val runtimeProxies = runtimeState.proxies
    val runtimeHasProxySnapshot = runtimeProxies.groups.isNotEmpty()
    val loadingPresentation = resolveMihomoProxyLoadingPresentation(
        pageRefreshInProgress = pageRefreshInProgress,
        hasProxySnapshot = runtimeHasProxySnapshot,
    )
    val proxies = when {
        !hasProfiles -> MihomoProxiesState()
        else -> runtimeProxies
    }
    val visibleProxies = remember(proxies, appState.mihomoProxyExcludeNotSelectable) {
        filterMihomoProxyGroups(proxies, excludeNotSelectable = appState.mihomoProxyExcludeNotSelectable)
    }
    val runtimeAvailable = hasUsableProfile && runtimeHasProxySnapshot
    val groupNames = visibleProxies.groups.map(MihomoProxyGroup::name)
    var selectedGroupName by rememberSaveable { mutableStateOf(groupNames.firstOrNull().orEmpty()) }
    val resolvedSelectedGroupName = selectedGroupName.takeIf { groupName -> groupName in groupNames }
        ?: groupNames.firstOrNull().orEmpty()
    val testingTarget = runtimeState.delayTestingTarget
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedGroup = visibleProxies.groups.firstOrNull { group -> group.name == resolvedSelectedGroupName }
    val groupLayout = resolveMihomoProxyGroupLayout(appState.mihomoProxyGroupLayout)
    val proxyLayout = resolveMihomoProxyLayout(appState.mihomoProxyLayout, isWideScreen)
    val columns = resolveMihomoProxyColumns(proxyLayout)
    var pendingSelections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var locateRequestTrigger by remember { mutableIntStateOf(0) }
    var expandedGroupNames by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val resolvedSelectedGroupIndex = groupNames.indexOf(resolvedSelectedGroupName).coerceAtLeast(0)
    val groupPagerState = key(groupNames) {
        rememberPagerState(
            initialPage = resolvedSelectedGroupIndex,
            pageCount = { groupNames.size.coerceAtLeast(1) },
        )
    }

    LaunchedEffect(groupNames) {
        pendingSelections = pendingSelections.filterKeys { groupName -> groupName in groupNames }
        expandedGroupNames = expandedGroupNames.filter { groupName -> groupName in groupNames }.toSet()
        if (selectedGroupName !in groupNames) {
            selectedGroupName = groupNames.firstOrNull().orEmpty()
        }
        val lastIndex = groupNames.lastIndex
        if (lastIndex >= 0 && groupPagerState.currentPage > lastIndex) {
            groupPagerState.scrollToPage(lastIndex)
        }
    }

    LaunchedEffect(visibleProxies.groups) {
        if (pendingSelections.isEmpty()) return@LaunchedEffect
        pendingSelections = pendingSelections.filter { (groupName, proxyName) ->
            val group = visibleProxies.groups.firstOrNull { item -> item.name == groupName } ?: return@filter false
            group.now != proxyName && group.all.any { nodeId -> nodeId.name == proxyName }
        }
    }

    val pagerSpatialMotion = AsteriskMotion.spatial<Float>()
    LaunchedEffect(resolvedSelectedGroupName, groupNames) {
        val selectedIndex = groupNames.indexOf(resolvedSelectedGroupName)
        if (
            selectedIndex >= 0 &&
            !groupPagerState.isScrollInProgress &&
            groupPagerState.currentPage != selectedIndex
        ) {
            groupPagerState.animateScrollToPage(
                page = selectedIndex,
                animationSpec = pagerSpatialMotion,
            )
        }
    }

    LaunchedEffect(groupPagerState, groupNames) {
        snapshotFlow { groupPagerState.targetPage }
            .collect { page ->
                groupNames.getOrNull(page)?.let { groupName ->
                    if (selectedGroupName != groupName) {
                        selectedGroupName = groupName
                    }
                }
            }
    }

    fun requireRuntime(): Boolean {
        if (!runtimeAvailable) {
            val message = runtimeState.lastError.takeIf(String::isNotBlank)
                ?.let { error -> "$runtimeUnavailableMessage: $error" }
                ?: runtimeUnavailableMessage
            scope.launch { tipNotifier.show(message) }
            return false
        }
        return true
    }

    fun selectProxy(group: MihomoProxyGroup, node: MihomoProxyNode) {
        if (!isMihomoProxyGroupSelectable(group)) return
        if (!requireRuntime()) return
        val pendingProxyName = pendingSelections[group.name]
        if (pendingProxyName == node.name || (pendingProxyName == null && group.now == node.name)) return
        pendingSelections = pendingSelections + (group.name to node.name)
        // Finish the DNS transaction even if this page leaves composition.
        services.appScope.launch(Dispatchers.Main.immediate) {
            services.mihomoRuntime.selectProxy(appState, group.name, node.name)
                .onSuccess {
                    if (appState.mihomoProxyInterruptConnections && appState.proxyRunning) {
                        services.mihomoRuntime.closeConnectionsForGroup(appState, group.name)
                            .onFailure { error ->
                                tipNotifier.showError(error, closeConnectionsFailedMessage)
                            }
                    }
                }
                .onFailure { error ->
                    if (pendingSelections[group.name] == node.name) {
                        pendingSelections = pendingSelections - group.name
                    }
                    tipNotifier.showError(error, selectFailedMessage)
                }
        }
    }

    fun testProxy(node: MihomoProxyNode) {
        if (!requireRuntime() || testingTarget != null) return
        scope.launch {
            services.mihomoRuntime.testProxyDelay(appState, node.id)
                .onSuccess { tipNotifier.show(delayDoneMessage) }
                .onFailure { error -> tipNotifier.showError(error, delayFailedMessage) }
        }
    }

    fun testGroup(group: MihomoProxyGroup) {
        if (!requireRuntime() || testingTarget != null) return
        scope.launch {
            services.mihomoRuntime.testGroupDelay(appState, group.name, group.testUrl)
                .onSuccess { tipNotifier.show(delayDoneMessage) }
                .onFailure { error -> tipNotifier.showError(error, delayFailedMessage) }
        }
    }

    AsteriskScaffold(
        topBar = {
            Column {
                AsteriskSearchTopAppBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.mihomo_proxies_search),
                    searchAvailable = hasProfiles,
                    title = { Text(stringResource(R.string.mihomo_proxies_title)) },
                    actions = {
                        if (hasProviders) {
                            IconButton(
                                onClick = { navigator.push(Route.MihomoProviderManagement) },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = stringResource(R.string.mihomo_provider_management_title),
                                )
                            }
                        }
                        MihomoProxyOptionsMenu(
                            interruptConnections = appState.mihomoProxyInterruptConnections,
                            excludeNotSelectable = appState.mihomoProxyExcludeNotSelectable,
                            groupLayout = groupLayout,
                            layout = appState.mihomoProxyLayout,
                            sort = resolveMihomoProxySort(appState.mihomoProxySort),
                            onInterruptConnectionsChange = { enabled ->
                                updateAppState { state ->
                                    state.copy(mihomoProxyInterruptConnections = enabled)
                                }
                            },
                            onExcludeNotSelectableChange = { enabled ->
                                updateAppState { state ->
                                    state.copy(mihomoProxyExcludeNotSelectable = enabled)
                                }
                            },
                            onGroupLayoutChange = { nextGroupLayout ->
                                updateAppState { state ->
                                    state.copy(mihomoProxyGroupLayout = nextGroupLayout)
                                }
                            },
                            onLayoutChange = { layout ->
                                updateAppState { state ->
                                    state.copy(mihomoProxyLayout = layout)
                                }
                            },
                            onSortChange = { sort ->
                                updateAppState { state ->
                                    state.copy(mihomoProxySort = sort)
                                }
                            },
                        )
                    },
                )
                if (groupLayout == MihomoProxyGroupLayoutTabs && hasProfiles && visibleProxies.groups.size > 1) {
                    AsteriskTopBarControls {
                        ProxyGroupTabs(
                            groups = visibleProxies.groups,
                            selectedGroupName = resolvedSelectedGroupName,
                            onSelectedGroupNameChange = { selectedGroupName = it },
                            onReselectCurrentGroup = { locateRequestTrigger++ },
                        )
                    }
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

        Box {
            if (!hasProfiles || visibleProxies.groups.isEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listPadding,
                    verticalArrangement = Arrangement.spacedBy(MihomoProxyNodeGridSpacing),
                    horizontalArrangement = Arrangement.spacedBy(MihomoProxyNodeGridSpacing),
                ) {
                    item(
                        key = "empty",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        if (
                            loadingPresentation == MihomoProxyLoadingPresentation.Initial &&
                            hasUsableProfile
                        ) {
                            MihomoProxyLoadingCard()
                        } else if (hasProfiles) {
                            MihomoProxyEmptyCard()
                        } else {
                            MihomoProxyNoConfigurationCard(
                                onAddConfiguration = {
                                    mainDestinationState?.select(MainDestination.Configurations)
                                        ?: navigator.push(Route.MihomoProfileList)
                                },
                            )
                        }
                    }
                }
            } else if (groupLayout == MihomoProxyGroupLayoutExpandable) {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listPadding,
                    verticalArrangement = Arrangement.spacedBy(MihomoProxyNodeGridSpacing),
                    horizontalArrangement = Arrangement.spacedBy(MihomoProxyNodeGridSpacing),
                ) {
                    visibleProxies.groups.forEach { group ->
                        val pageNodes = reduceMihomoProxyNodeNames(
                            group = group,
                            proxies = visibleProxies,
                            query = searchQuery,
                            sort = resolveMihomoProxySort(appState.mihomoProxySort),
                        )
                        val matchesSearch = searchQuery.isBlank() ||
                            group.name.contains(searchQuery.trim(), ignoreCase = true) ||
                            pageNodes.isNotEmpty()
                        if (matchesSearch) {
                            val isExpanded = group.name in expandedGroupNames ||
                                (searchQuery.isNotBlank() && pageNodes.isNotEmpty())
                            val currentSelectedName = (pendingSelections[group.name] ?: group.now)
                                .takeIf(String::isNotBlank).orEmpty()

                            item(
                                key = "group_header:${group.name}",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                MihomoProxyGroupCard(
                                    group = group,
                                    currentNodeName = currentSelectedName,
                                    nodeCount = group.all.size,
                                    expanded = isExpanded,
                                    testing = testingTarget == MihomoDelayTarget.Group(group.name),
                                    delayTestEnabled = runtimeAvailable && testingTarget == null,
                                    onToggleExpand = {
                                        selectedGroupName = group.name
                                        expandedGroupNames = if (group.name in expandedGroupNames) {
                                            expandedGroupNames - group.name
                                        } else {
                                            expandedGroupNames + group.name
                                        }
                                    },
                                    onDelayTest = { testGroup(group) },
                                    modifier = Modifier
                                        .animateItem(
                                            fadeInSpec = AsteriskMotion.effects(),
                                            placementSpec = AsteriskMotion.spatial(),
                                            fadeOutSpec = AsteriskMotion.effects(),
                                        )
                                        .fillMaxWidth(),
                                )
                            }
                            if (isExpanded) {
                                if (pageNodes.isEmpty()) {
                                    item(
                                        key = "group_empty:${group.name}",
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        MihomoProxyEmptyCard()
                                    }
                                } else {
                                    items(
                                        items = pageNodes,
                                        key = { nodeId -> "group_node:${group.name}:${nodeId.toMihomoProxyLazyItemKey()}" },
                                        span = { GridItemSpan(1) },
                                    ) { nodeId ->
                                        val node = proxies.node(nodeId)
                                        val selectionEnabled = isMihomoProxyGroupSelectable(group) && runtimeAvailable
                                        MihomoProxyNodeCard(
                                            modifier = Modifier
                                                .animateItem(
                                                    fadeInSpec = AsteriskMotion.effects(),
                                                    placementSpec = AsteriskMotion.spatial(),
                                                    fadeOutSpec = AsteriskMotion.effects(),
                                                )
                                                .fillMaxWidth(),
                                            node = node,
                                            selected = isMihomoProxyNodeCurrent(
                                                group = group,
                                                nodeName = node.name,
                                                pendingSelections = pendingSelections,
                                            ),
                                            selectionEnabled = selectionEnabled,
                                            delayTestEnabled = runtimeAvailable && testingTarget == null,
                                            compact = columns > 1,
                                            testing = testingTarget == MihomoDelayTarget.Node(node.id),
                                            onSelect = { selectProxy(group, node) },
                                            onDelayTest = { testProxy(node) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                HorizontalPager(
                    state = groupPagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    val group = visibleProxies.groups.getOrNull(page)
                    val pageNodes = remember(group, visibleProxies, searchQuery, appState.mihomoProxySort) {
                        reduceMihomoProxyNodeNames(
                            group = group,
                            proxies = visibleProxies,
                            query = searchQuery,
                            sort = resolveMihomoProxySort(appState.mihomoProxySort),
                        )
                    }
                    val pageGridState = rememberLazyGridState()
                    val selectedNodeName = (pendingSelections[group?.name] ?: group?.now)
                        .orEmpty().takeIf(String::isNotBlank)
                    var handledLocateRequest by remember { mutableIntStateOf(locateRequestTrigger) }

                    LaunchedEffect(locateRequestTrigger) {
                        if (handledLocateRequest == locateRequestTrigger) return@LaunchedEffect
                        handledLocateRequest = locateRequestTrigger
                        if (groupPagerState.currentPage != page || selectedNodeName == null) return@LaunchedEffect
                        val selectedIndex = pageNodes.indexOfFirst { nodeId -> nodeId.name == selectedNodeName }
                        if (selectedIndex < 0) return@LaunchedEffect
                        val targetItemIndex = (selectedIndex / columns) * columns
                        val firstVisible = pageGridState.firstVisibleItemIndex
                        if (kotlin.math.abs(firstVisible - targetItemIndex) > 12) {
                            val preIndex = if (targetItemIndex > firstVisible) {
                                (targetItemIndex - columns).coerceAtLeast(0)
                            } else {
                                (targetItemIndex + columns).coerceAtMost(pageNodes.lastIndex)
                            }
                            pageGridState.scrollToItem(preIndex)
                        }
                        pageGridState.animateScrollToItem(targetItemIndex)
                    }

                    Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = pageGridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = listPadding,
                            verticalArrangement = Arrangement.spacedBy(MihomoProxyNodeGridSpacing),
                            horizontalArrangement = Arrangement.spacedBy(MihomoProxyNodeGridSpacing),
                        ) {
                            if (group == null) {
                                item(
                                    key = "empty",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    if (
                                        loadingPresentation == MihomoProxyLoadingPresentation.Initial &&
                                        hasUsableProfile
                                    ) {
                                        MihomoProxyLoadingCard()
                                    } else if (hasProfiles) {
                                        MihomoProxyEmptyCard()
                                    } else {
                                        MihomoProxyNoConfigurationCard(
                                            onAddConfiguration = {
                                                mainDestinationState?.select(MainDestination.Configurations)
                                                    ?: navigator.push(Route.MihomoProfileList)
                                            },
                                        )
                                    }
                                }
                            } else if (pageNodes.isEmpty()) {
                                item(
                                    key = "group_empty:${group.name}",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    MihomoProxyEmptyCard()
                                }
                            } else {
                                items(
                                    items = pageNodes,
                                    key = { nodeId -> nodeId.toMihomoProxyLazyItemKey() },
                                ) { nodeId ->
                                    val node = proxies.node(nodeId)
                                    val selectionEnabled = isMihomoProxyGroupSelectable(group) && runtimeAvailable
                                    MihomoProxyNodeCard(
                                        modifier = Modifier
                                            .animateItem(
                                                fadeInSpec = AsteriskMotion.effects(),
                                                placementSpec = AsteriskMotion.spatial(),
                                                fadeOutSpec = AsteriskMotion.effects(),
                                            )
                                            .fillMaxWidth(),
                                        node = node,
                                        selected = isMihomoProxyNodeCurrent(
                                            group = group,
                                            nodeName = node.name,
                                            pendingSelections = pendingSelections,
                                        ),
                                        selectionEnabled = selectionEnabled,
                                        delayTestEnabled = runtimeAvailable && testingTarget == null,
                                        compact = columns > 1,
                                        testing = testingTarget == MihomoDelayTarget.Node(node.id),
                                        onSelect = { selectProxy(group, node) },
                                        onDelayTest = { testProxy(node) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (loadingPresentation == MihomoProxyLoadingPresentation.ContentRefresh) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = contentPadding.calculateTopPadding()),
                )
            }
            if (groupLayout == MihomoProxyGroupLayoutTabs) {
                selectedGroup?.let { group ->
                    MihomoDelayToolbar(
                        enabled = runtimeAvailable && testingTarget == null,
                        testing = testingTarget == MihomoDelayTarget.Group(group.name),
                        canLocate = (pendingSelections[group.name] ?: group.now).isNotBlank(),
                        onLocate = { locateRequestTrigger++ },
                        onDelayTest = { testGroup(group) },
                        bottomPadding = contentPadding.calculateBottomPadding(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd),
                    )
                }
            }
        }
    }
}
