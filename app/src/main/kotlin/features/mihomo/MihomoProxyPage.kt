// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.mihomo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalMainDestinationState
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.modes.MihomoProxyLayoutAuto
import app.modes.MihomoProxyLayoutDouble
import app.modes.MihomoProxyLayoutMultiple
import app.modes.MihomoProxyLayoutSingle
import app.modes.MihomoProxySortDefault
import app.modes.MihomoProxySortDelay
import app.modes.MihomoProxySortName
import ui.components.AsteriskCheckbox
import ui.components.AsteriskFilterChip
import ui.components.AsteriskInfoChip
import ui.components.AsteriskPageCard
import ui.components.AsteriskPinnedSearchArea
import ui.components.AsteriskSelectionCard
import ui.components.AsteriskTonalButton
import app.navigation.Route
import app.navigation.MainDestination
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.hasMihomoProxyProviders
import engine.mihomo.hasUsableMihomoProfile
import engine.mihomo.selectedMihomoProfileOrNull
import engine.mihomo.runtime.MihomoProxiesState
import engine.mihomo.runtime.MihomoProxyGroup
import engine.mihomo.runtime.MihomoProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.theme.AsteriskMotion

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
    val runtimeState by services.mihomoRuntime.state.collectAsState()
    val scope = rememberCoroutineScope()
    val tipNotifier = services.tipNotifier
    val runtimeUnavailableMessage = stringResource(R.string.mihomo_proxies_runtime_unavailable)
    val selectFailedMessage = stringResource(R.string.mihomo_proxies_select_failed)
    val delayFailedMessage = stringResource(R.string.mihomo_proxies_delay_failed)
    val delayDoneMessage = stringResource(R.string.mihomo_proxies_delay_done)

    val hasProfiles = appState.mihomoProfiles.isNotEmpty()
    val hasUsableProfile = appState.hasUsableMihomoProfile()
    val selectedProfile = appState.selectedMihomoProfileOrNull()
    LaunchedEffect(
        services.mihomoRuntime,
        hasUsableProfile,
        appState.selectedMihomoProfileId,
        selectedProfile?.contentSha256,
        selectedProfile?.disableOverrides,
        appState.proxyRunning,
        appState.runMode,
        appState.mihomoMode,
    ) {
        if (hasUsableProfile) {
            services.mihomoRuntime.refreshProxies(appState)
        }
    }
    var hasProxyProviders by remember { mutableStateOf(false) }
    LaunchedEffect(appState) {
        hasProxyProviders = if (hasUsableProfile) {
            withContext(Dispatchers.IO) {
                runCatching {
                    MihomoProfileFactory.buildProfile(appContext, appState)
                        .hasMihomoProxyProviders()
                }.getOrDefault(false)
            }
        } else {
            false
        }
    }
    val runtimeProxies = runtimeState.proxies
    val runtimeHasProxySnapshot = runtimeProxies.groups.isNotEmpty()
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
    val proxyLayout = resolveMihomoProxyLayout(appState.mihomoProxyLayout, isWideScreen)
    val columns = resolveMihomoProxyColumns(proxyLayout)
    var pendingSelections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val resolvedSelectedGroupIndex = groupNames.indexOf(resolvedSelectedGroupName).coerceAtLeast(0)
    val groupPagerState = key(groupNames) {
        rememberPagerState(
            initialPage = resolvedSelectedGroupIndex,
            pageCount = { groupNames.size.coerceAtLeast(1) },
        )
    }

    LaunchedEffect(groupNames) {
        pendingSelections = pendingSelections.filterKeys { groupName -> groupName in groupNames }
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
            group.now != proxyName && proxyName in group.all
        }
    }

    LaunchedEffect(resolvedSelectedGroupName, groupNames) {
        val selectedIndex = groupNames.indexOf(resolvedSelectedGroupName)
        if (
            selectedIndex >= 0 &&
            !groupPagerState.isScrollInProgress &&
            groupPagerState.currentPage != selectedIndex
        ) {
            groupPagerState.animateScrollToPage(
                page = selectedIndex,
                animationSpec = tween(easing = LinearEasing),
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
        scope.launch {
            services.mihomoRuntime.selectProxy(appState, group.name, node.name)
                .onSuccess { }
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
            services.mihomoRuntime.testProxyDelay(appState, node.name)
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

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text(stringResource(R.string.mihomo_proxies_title)) },
                    actions = {
                        if (hasProxyProviders) {
                            IconButton(
                                onClick = { navigator.push(Route.MihomoProviders) },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = stringResource(R.string.mihomo_providers_title),
                                )
                            }
                        }
                        MihomoProxyOptionsMenu(
                            excludeNotSelectable = appState.mihomoProxyExcludeNotSelectable,
                            layout = appState.mihomoProxyLayout,
                            sort = resolveMihomoProxySort(appState.mihomoProxySort),
                            onExcludeNotSelectableChange = { enabled ->
                                updateAppState { state ->
                                    state.copy(mihomoProxyExcludeNotSelectable = enabled)
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
                if (hasProfiles) {
                    AsteriskPinnedSearchArea(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = stringResource(R.string.mihomo_proxies_search),
                        clearContentDescription = stringResource(R.string.common_clear),
                    ) {
                        if (visibleProxies.groups.size > 1) {
                            ProxyGroupTabs(
                                groups = visibleProxies.groups,
                                selectedGroupName = resolvedSelectedGroupName,
                                onSelectedGroupNameChange = { selectedGroupName = it },
                            )
                        }
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
        val layoutDirection = LocalLayoutDirection.current
        val pageListContentPadding = PaddingValues(
            start = listPadding.calculateStartPadding(layoutDirection),
            end = listPadding.calculateEndPadding(layoutDirection),
            bottom = listPadding.calculateBottomPadding(),
        )

        Box {
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

                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = pageGridState,
                        modifier = Modifier.padding(top = listPadding.calculateTopPadding()),
                        contentPadding = pageListContentPadding,
                        verticalArrangement = Arrangement.spacedBy(MihomoProxyNodeGridSpacing),
                        horizontalArrangement = Arrangement.spacedBy(MihomoProxyNodeGridSpacing),
                    ) {
                        if (group == null) {
                            item(
                                key = "empty",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                if (runtimeState.proxiesRefreshing && hasUsableProfile) {
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
                                key = { nodeName -> "${group.name}:$nodeName" },
                            ) { nodeName ->
                                val node = proxies.node(nodeName)
                                val selectionEnabled = isMihomoProxyGroupSelectable(group) && runtimeAvailable
                                MihomoProxyNodeCard(
                                    modifier = Modifier
                                        .animateItem()
                                        .fillMaxWidth(),
                                    node = node,
                                    selected = isMihomoProxyNodeCurrent(
                                        group = group,
                                        nodeName = node.name,
                                        pendingSelections = pendingSelections,
                                    ),
                                    selectionEnabled = selectionEnabled,
                                    runtimeAvailable = runtimeAvailable,
                                    compact = columns > 1,
                                    testing = testingTarget == node.name,
                                    onSelect = { selectProxy(group, node) },
                                    onDelayTest = { testProxy(node) },
                                )
                            }
                        }
                    }
                }
            }
            selectedGroup?.let { group ->
                ProxyDelayToolbar(
                    enabled = runtimeAvailable && testingTarget == null,
                    testing = testingTarget == group.name,
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
private fun ProxyGroupTabs(
    groups: List<MihomoProxyGroup>,
    selectedGroupName: String,
    onSelectedGroupNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
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
                    onClick = { onSelectedGroupNameChange(group.name) },
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

private data class ProxyGroupTabBounds(
    val leftPx: Int,
    val widthPx: Int,
)

@Composable
private fun MihomoProxyOptionsMenu(
    excludeNotSelectable: Boolean,
    layout: Int,
    sort: Int,
    onExcludeNotSelectableChange: (Boolean) -> Unit,
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
                transitionSpec = {
                    val direction = if (targetState == ProxyOptionsLevel.Main) -1 else 1
                    (
                        slideInHorizontally(
                            animationSpec = menuSpatialMotion,
                            initialOffsetX = { width -> direction * width / 5 },
                        ) + fadeIn(animationSpec = menuEffectsMotion)
                        ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = menuSpatialMotion,
                            targetOffsetX = { width -> -direction * width / 5 },
                        ) + fadeOut(animationSpec = menuEffectsMotion),
                    ).using(
                        SizeTransform(sizeAnimationSpec = { _, _ -> menuSizeMotion }),
                    )
                },
                contentAlignment = Alignment.TopStart,
                label = "proxy-options-level",
            ) { currentLevel ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (currentLevel) {
                ProxyOptionsLevel.Main -> {
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
                    ).forEach { (value, label, icon) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                dismissMenu()
                                onLayoutChange(value)
                            },
                            leadingIcon = { Icon(icon, contentDescription = null) },
                            trailingIcon = { RadioButton(selected = layout == value, onClick = null) },
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
                    ).forEach { (value, label, icon) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                dismissMenu()
                                onSortChange(value)
                            },
                            leadingIcon = { Icon(icon, contentDescription = null) },
                            trailingIcon = { RadioButton(selected = sort == value, onClick = null) },
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
    Layout,
    Sort,
}

private val MihomoProxyOptionsMenuWidth = 224.dp

@Composable
private fun MihomoProxyNodeCard(
    node: MihomoProxyNode,
    selected: Boolean,
    selectionEnabled: Boolean,
    runtimeAvailable: Boolean,
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
                    text = node.name,
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
                delay = node.delay,
                selected = selected,
                testing = testing,
                enabled = runtimeAvailable && !testing,
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
private fun ProtocolDelayLine(
    protocol: String,
    delay: Int?,
    selected: Boolean,
    testing: Boolean,
    enabled: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val delayText = when {
        testing -> ""
        delay == null -> stringResource(R.string.mihomo_proxies_delay_not_tested)
        delay < 0 -> stringResource(R.string.mihomo_proxies_delay_timeout)
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
                        color = delayColor(delay),
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
private fun ProxyDelayToolbar(
    enabled: Boolean,
    testing: Boolean,
    onDelayTest: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(
            end = 20.dp,
            bottom = bottomPadding + MihomoFloatingToolbarBottomSpacing,
        ),
    ) {
        ExtendedFloatingActionButton(
            onClick = { if (enabled) onDelayTest() },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                alpha = if (enabled && !testing) 1f else 0.45f,
            ),
            icon = {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    DelayToolbarGlyph()
                }
            },
            text = { Text(stringResource(R.string.mihomo_proxies_group_test)) },
        )
    }
}

@Composable
private fun DelayToolbarGlyph(
) {
    Icon(
        imageVector = Icons.Rounded.Speed,
        contentDescription = stringResource(R.string.mihomo_proxies_group_test),
    )
}

@Composable
private fun MihomoProxyEmptyCard() {
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
                text = stringResource(R.string.mihomo_proxies_no_configuration_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.mihomo_proxies_no_configuration_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AsteriskTonalButton(
                text = stringResource(R.string.mihomo_configuration_add),
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
private fun delayColor(delay: Int?): Color {
    return when {
        delay == null -> MaterialTheme.colorScheme.onSurfaceVariant
        delay < 0 -> MaterialTheme.colorScheme.error
        delay < 300 -> MaterialTheme.colorScheme.primary
        delay < 500 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
}

private val MihomoProxyNodeCardHeight = 112.dp
private val MihomoProxyNodeCardPadding = PaddingValues(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 10.dp)
private val MihomoProxyNodeGridSpacing = 12.dp
private val MihomoFloatingToolbarBottomSpacing = 16.dp
