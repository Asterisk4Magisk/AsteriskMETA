// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import ui.isInDarkTheme
import ui.components.AsteriskSearchTopAppBar
import ui.components.AsteriskTopBarControls
import ui.components.AsteriskDropdownMenuItem
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import ui.components.AsteriskScaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.modes.MihomoProxyLayoutAuto
import app.modes.MihomoProxyLayoutDouble
import app.modes.MihomoProxyLayoutMultiple
import app.modes.MihomoProxyLayoutSingle
import app.modes.MihomoProxySortDefault
import app.modes.MihomoProxySortDelay
import app.modes.MihomoProxySortName
import ui.components.AsteriskCheckbox
import ui.components.AsteriskExpansionIndicator
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskFilterChip
import ui.components.AsteriskInfoChip
import ui.components.AsteriskModalBottomSheet
import ui.components.AsteriskPageCard
import ui.components.AsteriskSelectionCard
import ui.components.AsteriskTonalButton
import app.navigation.Route
import app.navigation.MainDestination
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.MihomoProviderMetadataCache
import engine.mihomo.hasUsableMihomoProfile
import engine.mihomo.selectedMihomoProfileOrNull
import engine.mihomo.sha256Hex
import engine.mihomo.runtime.MihomoProxiesState
import engine.mihomo.runtime.MihomoDelayTarget
import engine.mihomo.runtime.MihomoDelayStatus
import engine.mihomo.runtime.MihomoProxyGroup
import engine.mihomo.runtime.MihomoProxyNode
import engine.mihomo.runtime.MihomoRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
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
    val groupLayout = if (appState.mihomoProxyGroupLayout == MihomoProxyGroupLayoutExpandable) {
        MihomoProxyGroupLayoutExpandable
    } else {
        MihomoProxyGroupLayoutTabs
    }
    val proxyLayout = resolveMihomoProxyLayout(appState.mihomoProxyLayout, isWideScreen)
    val columns = resolveMihomoProxyColumns(proxyLayout)
    var pendingSelections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var locateRequestTrigger by remember { mutableIntStateOf(0) }
    var expandedGroupNames by rememberSaveable(appState.selectedMihomoProfileId) {
        mutableStateOf<Set<String>>(emptySet())
    }
    val keyword = searchQuery.trim()
    var searchCollapsedGroupNames by remember(appState.selectedMihomoProfileId, keyword) {
        mutableStateOf<Set<String>>(emptySet())
    }
    val resolvedSelectedGroupIndex = groupNames.indexOf(resolvedSelectedGroupName).coerceAtLeast(0)
    val groupPagerState = key(groupNames, groupLayout) {
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
        if (groupLayout == MihomoProxyGroupLayoutTabs && lastIndex >= 0 && groupPagerState.currentPage > lastIndex) {
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
    LaunchedEffect(resolvedSelectedGroupName, groupNames, groupLayout) {
        if (groupLayout != MihomoProxyGroupLayoutTabs) return@LaunchedEffect
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
        if (groupLayout != MihomoProxyGroupLayoutTabs) return@LaunchedEffect
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
                            onGroupLayoutChange = { layout ->
                                updateAppState { state -> state.copy(mihomoProxyGroupLayout = layout) }
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
                    AsteriskTopBarControls(alignEndToAppBarActions = true) {
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
        val expandable = groupLayout == MihomoProxyGroupLayoutExpandable &&
            hasProfiles && visibleProxies.groups.isNotEmpty()
        val listPadding = pageListPadding(contentPadding, bottomExtra = if (expandable) 12.dp else 104.dp)

        Box {
            if (expandable) {
                val sections = remember(visibleProxies, keyword, appState.mihomoProxySort) {
                    reduceMihomoProxyGroupSections(visibleProxies, keyword, appState.mihomoProxySort)
                }
                fun isExpanded(group: MihomoProxyGroup) = if (keyword.isNotEmpty()) {
                    group.name !in searchCollapsedGroupNames
                } else {
                    group.name in expandedGroupNames
                }
                val gridState = rememberLazyGridState()
                var previousKeyword by remember { mutableStateOf(keyword) }
                LaunchedEffect(keyword) {
                    if (previousKeyword != keyword) {
                        previousKeyword = keyword
                        gridState.scrollToItem(0)
                    }
                }
                var handledLocateRequest by remember { mutableIntStateOf(locateRequestTrigger) }
                LaunchedEffect(locateRequestTrigger) {
                    if (handledLocateRequest == locateRequestTrigger) return@LaunchedEffect
                    handledLocateRequest = locateRequestTrigger
                    var itemIndex = 0
                    for ((group, nodes) in sections) {
                        if (group.name == selectedGroupName) {
                            val currentName = pendingSelections[group.name] ?: group.now
                            val nodeIndex = nodes.indexOfFirst { it.name == currentName }
                            if (nodeIndex >= 0) {
                                gridState.animateScrollToItem(itemIndex + 1 + (nodeIndex / columns) * columns)
                            }
                            break
                        }
                        itemIndex += 1 + if (isExpanded(group)) nodes.size.coerceAtLeast(1) else 0
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listPadding,
                    verticalArrangement = Arrangement.spacedBy(MihomoProxyNodeGridSpacing),
                    horizontalArrangement = Arrangement.spacedBy(MihomoProxyNodeGridSpacing),
                ) {
                    if (sections.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            MihomoProxyEmptyCard()
                        }
                    }
                    sections.forEach { (group, nodes) ->
                        val expanded = isExpanded(group)
                        val currentName = pendingSelections[group.name] ?: group.now
                        item(
                            key = "group:${group.name}",
                            contentType = "group",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            MihomoProxyGroupCard(
                                group = group,
                                currentName = currentName,
                                expanded = expanded,
                                testing = testingTarget == MihomoDelayTarget.Group(group.name),
                                delayTestEnabled = runtimeAvailable && testingTarget == null,
                                canLocate = nodes.any { it.name == currentName },
                                onToggle = {
                                    selectedGroupName = group.name
                                    if (keyword.isNotEmpty()) {
                                        searchCollapsedGroupNames = if (expanded) searchCollapsedGroupNames + group.name
                                            else searchCollapsedGroupNames - group.name
                                    } else {
                                        expandedGroupNames = if (expanded) expandedGroupNames - group.name
                                            else expandedGroupNames + group.name
                                    }
                                },
                                onLocate = {
                                    selectedGroupName = group.name
                                    expandedGroupNames = expandedGroupNames + group.name
                                    searchCollapsedGroupNames = searchCollapsedGroupNames - group.name
                                    locateRequestTrigger++
                                },
                                onDelayTest = { testGroup(group) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = AsteriskMotion.contentFade(),
                                    placementSpec = AsteriskMotion.contentPlacement(),
                                    fadeOutSpec = AsteriskMotion.fastEffects(),
                                ),
                            )
                        }
                        if (expanded) {
                            if (nodes.isEmpty()) {
                                item(key = "empty:${group.name}", span = { GridItemSpan(maxLineSpan) }) {
                                    MihomoProxyEmptyCard(
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = AsteriskMotion.contentFade(),
                                            placementSpec = AsteriskMotion.contentPlacement(),
                                            fadeOutSpec = AsteriskMotion.fastEffects(),
                                        ),
                                    )
                                }
                            }
                            items(
                                items = nodes,
                                key = { "node:${group.name.length}:${group.name}:${it.toMihomoProxyLazyItemKey()}" },
                                contentType = { "node" },
                            ) { nodeId ->
                                val node = proxies.node(nodeId)
                                MihomoProxyNodeCard(
                                    node = node,
                                    selected = isMihomoProxyNodeCurrent(group, node.name, pendingSelections),
                                    selectionEnabled = isMihomoProxyGroupSelectable(group) && runtimeAvailable,
                                    delayTestEnabled = runtimeAvailable && testingTarget == null,
                                    compact = columns > 1,
                                    testing = testingTarget == MihomoDelayTarget.Node(node.id),
                                    onSelect = { selectProxy(group, node) },
                                    onDelayTest = { testProxy(node) },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = AsteriskMotion.contentFade(),
                                        placementSpec = AsteriskMotion.contentPlacement(),
                                        fadeOutSpec = AsteriskMotion.fastEffects(),
                                    ).fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            } else HorizontalPager(
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
            if (loadingPresentation == MihomoProxyLoadingPresentation.ContentRefresh) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = contentPadding.calculateTopPadding()),
                )
            }
            selectedGroup?.takeIf { !expandable }?.let { group ->
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

@Composable
private fun MihomoProxyGroupCard(
    group: MihomoProxyGroup,
    currentName: String,
    expanded: Boolean,
    testing: Boolean,
    delayTestEnabled: Boolean,
    canLocate: Boolean,
    onToggle: () -> Unit,
    onLocate: () -> Unit,
    onDelayTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expansionState = stringResource(
        if (expanded) R.string.mihomo_proxies_group_expanded else R.string.mihomo_proxies_group_collapsed,
    )
    AsteriskExpressiveCard(
        expanded = expanded,
        selected = expanded,
        onClick = onToggle,
        modifier = modifier.fillMaxWidth().semantics {
            role = Role.Button
            stateDescription = expansionState
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${group.type.displayMihomoProtocolName()} · ${group.all.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (currentName.isNotBlank()) {
                    Text(
                        text = currentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onLocate,
                    enabled = canLocate,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.MyLocation, stringResource(R.string.mihomo_proxies_locate_selected))
                }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onDelayTest, enabled = delayTestEnabled) {
                            Icon(Icons.Rounded.Speed, stringResource(R.string.mihomo_proxies_group_test))
                        }
                    }
                }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    AsteriskExpansionIndicator(expanded = expanded)
                }
            }
        }
    }
}

@Composable
private fun ProxyGroupTabs(
    groups: List<MihomoProxyGroup>,
    selectedGroupName: String,
    onSelectedGroupNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onReselectCurrentGroup: () -> Unit = {},
) {
    if (groups.isEmpty()) return
    val tabScrollState = rememberScrollState()
    var availableWidthPx by remember { mutableIntStateOf(0) }
    var contentWidthPx by remember { mutableIntStateOf(0) }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var tabBounds by remember { mutableStateOf<Map<String, ProxyGroupTabBounds>>(emptyMap()) }
    var showGroupPicker by remember { mutableStateOf(false) }
    // Compare with the full row, so reserving button space cannot keep it visible
    // after the groups fit again (for example, after rotating or filtering).
    val hasOverflow = availableWidthPx in 1..<contentWidthPx
    val selectedBounds = tabBounds[selectedGroupName]

    LaunchedEffect(hasOverflow) {
        if (!hasOverflow) showGroupPicker = false
    }

    fun selectGroup(group: MihomoProxyGroup) {
        showGroupPicker = false
        if (group.name == selectedGroupName) {
            onReselectCurrentGroup()
        } else {
            onSelectedGroupNameChange(group.name)
        }
    }

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size -> availableWidthPx = size.width },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { size -> viewportWidthPx = size.width }
                .horizontalScroll(tabScrollState),
        ) {
            Row(
                modifier = Modifier.onSizeChanged { size -> contentWidthPx = size.width },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { group ->
                    AsteriskFilterChip(
                        selected = group.name == selectedGroupName,
                        onClick = { selectGroup(group) },
                        label = group.name,
                        modifier = Modifier
                            .semantics { selected = group.name == selectedGroupName }
                            .onGloballyPositioned { coordinates ->
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
        if (hasOverflow) {
            IconButton(
                onClick = { showGroupPicker = true },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(R.string.mihomo_proxies_expand_groups),
                )
            }
        }
    }

    AsteriskModalBottomSheet(
        show = showGroupPicker && hasOverflow,
        onDismissRequest = { showGroupPicker = false },
        title = stringResource(R.string.mihomo_proxies_groups),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            groups.forEach { group ->
                AsteriskFilterChip(
                    selected = group.name == selectedGroupName,
                    onClick = { selectGroup(group) },
                    label = group.name,
                    modifier = Modifier.semantics { selected = group.name == selectedGroupName },
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

private data class ProxyGroupTabBounds(
    val leftPx: Int,
    val widthPx: Int,
)

@Composable
private fun MihomoProxyOptionsMenu(
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
    val groupLayoutLabel = stringResource(
        if (groupLayout == MihomoProxyGroupLayoutExpandable) {
            R.string.mihomo_proxies_option_group_layout_expandable
        } else {
            R.string.mihomo_proxies_option_group_layout_tabs
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
                        MihomoProxyGroupLayoutTabs to R.string.mihomo_proxies_option_group_layout_tabs,
                        MihomoProxyGroupLayoutExpandable to R.string.mihomo_proxies_option_group_layout_expandable,
                    ).forEach { (value, label) ->
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

private enum class ProxyOptionsLevel {
    Main,
    GroupLayout,
    Layout,
    Sort,
}

private val MihomoProxyOptionsMenuWidth = 224.dp

@Composable
private fun MihomoProxyNodeCard(
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
        .height(if (compact) MihomoProxyNodeCompactCardHeight else MihomoProxyNodeCardHeight)
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
private fun ProtocolDelayLine(
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
private fun MihomoProxyEmptyCard(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.common_empty),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MihomoProxyNoConfigurationCard(
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
private fun MihomoProxyLoadingCard() {
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
private fun delayColor(delay: Int?, status: MihomoDelayStatus?): Color {
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

private val MihomoProxyNodeCardHeight = 112.dp

// Compact (multi-column) cards fit a two-line title, a 2dp gap and the 28dp protocol/delay line.
private val MihomoProxyNodeCompactCardHeight = 90.dp
private val MihomoProxyNodeCardPadding = PaddingValues(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 10.dp)
private val MihomoProxyNodeGridSpacing = 12.dp
