// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalFoundationApi::class, ExperimentalScrollBarApi::class)

package features.mihomo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import app.AppState
import app.collectAppState
import app.navigation.Route
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.escapeSupplementaryYamlCodePoints
import engine.mihomo.hasUsableMihomoProfile
import engine.mihomo.mihomoProxyProviderFileCandidates
import engine.mihomo.selectedMihomoProfileOrNull
import engine.mihomo.runtime.MihomoProxiesState
import engine.mihomo.runtime.MihomoProxyGroup
import engine.mihomo.runtime.MihomoProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import features.resources.runtime.prepareMihomoResourceFilePaths
import java.io.File
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import ui.isInDarkTheme
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun MihomoProxyPage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val appState by LocalAppStateStore.current.collectAppState()
    val appContext = LocalContext.current.applicationContext
    val navigator = LocalNavigator.current
    val services = LocalAppServices.current
    val runtimeState by services.mihomoRuntime.state.collectAsState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()
    val tipNotifier = services.tipNotifier
    val runtimeUnavailableMessage = stringResource(R.string.mihomo_proxies_runtime_unavailable)
    val selectFailedMessage = stringResource(R.string.mihomo_proxies_select_failed)
    val delayFailedMessage = stringResource(R.string.mihomo_proxies_delay_failed)
    val delayDoneMessage = stringResource(R.string.mihomo_proxies_delay_done)

    val hasProfiles = appState.mihomoProfiles.isNotEmpty()
    val hasUsableProfile = appState.hasUsableMihomoProfile()
    var fallbackProxies by remember { mutableStateOf(MihomoProxiesState()) }
    LaunchedEffect(
        hasUsableProfile,
        appState.selectedMihomoProfileId,
        appState.selectedMihomoProfileContentHash(),
        appState.selectedMihomoProfileOverrideHash(),
        appState.runMode,
    ) {
        if (!hasUsableProfile) {
            fallbackProxies = MihomoProxiesState()
            return@LaunchedEffect
        }
        val snapshot = appState
        val dataDir = withContext(Dispatchers.IO) {
            File(appContext.prepareMihomoResourceFilePaths().dataDir)
        }
        fallbackProxies = withContext(Dispatchers.IO) {
            runCatching { parseMihomoProxyState(MihomoProfileFactory.buildProfile(snapshot), dataDir) }
                .getOrDefault(MihomoProxiesState())
        }
    }
    val proxies = when {
        !hasProfiles -> MihomoProxiesState()
        runtimeState.proxies.groups.isNotEmpty() -> runtimeState.proxies
        else -> fallbackProxies
    }
    val runtimeAvailable = hasUsableProfile && proxies.groups.isNotEmpty()
    val groupNames = proxies.groups.map(MihomoProxyGroup::name)
    var selectedGroupName by rememberSaveable { mutableStateOf(groupNames.firstOrNull().orEmpty()) }
    val testingTarget = runtimeState.delayTestingTarget
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedGroup = proxies.groups.firstOrNull { group -> group.name == selectedGroupName }
    val columns = if (isWideScreen) 3 else 2
    var pendingSelections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val groupPagerState = rememberPagerState(
        initialPage = groupNames.indexOf(selectedGroupName).coerceAtLeast(0),
        pageCount = { groupNames.size.coerceAtLeast(1) },
    )

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

    LaunchedEffect(proxies.groups) {
        if (pendingSelections.isEmpty()) return@LaunchedEffect
        pendingSelections = pendingSelections.filter { (groupName, proxyName) ->
            val group = proxies.groups.firstOrNull { item -> item.name == groupName } ?: return@filter false
            group.now != proxyName && proxyName in group.all
        }
    }

    LaunchedEffect(selectedGroupName, groupNames) {
        val selectedIndex = groupNames.indexOf(selectedGroupName)
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
            AdaptiveTopAppBar(
                title = stringResource(R.string.mihomo_proxies_title),
                isWideScreen = isWideScreen,
                scrollBehavior = topAppBarScrollBehavior,
                bottomContent = {
                    if (hasProfiles) {
                        Column {
                            MihomoProxySearchBar(
                                searchValue = searchQuery,
                                onSearchValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp),
                            )
                            if (proxies.groups.size > 1) {
                                val pagerOffsetFraction by remember(groupPagerState) {
                                    derivedStateOf { groupPagerState.currentPageOffsetFraction }
                                }
                                ProxyGroupTabs(
                                    groups = proxies.groups,
                                    selectedGroupName = selectedGroupName,
                                    pagerPage = groupPagerState.currentPage,
                                    pagerOffsetFraction = pagerOffsetFraction,
                                    onSelectedGroupNameChange = { selectedGroupName = it },
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }
                        }
                    }
                },
            )
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
                val group = proxies.groups.getOrNull(page)
                val pageNodes = remember(group, proxies, searchQuery) {
                    filteredProxyNodeNames(group, proxies, searchQuery)
                }
                val pageListState = rememberLazyListState()

                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = pageListState,
                        modifier = Modifier
                            .padding(top = listPadding.calculateTopPadding())
                            .pageScrollModifiers(topAppBarScrollBehavior),
                        contentPadding = pageListContentPadding,
                        verticalArrangement = Arrangement.spacedBy(MihomoProxyNodeGridSpacing),
                    ) {
                        if (group == null) {
                            item("empty") {
                                if (hasProfiles) {
                                    MihomoProxyEmptyCard()
                                } else {
                                    MihomoProxyNoConfigurationCard(
                                        onAddConfiguration = { navigator.push(Route.MihomoProfileList) },
                                    )
                                }
                            }
                        } else if (pageNodes.isEmpty()) {
                            item("group_empty:${group.name}") {
                                MihomoProxyEmptyCard()
                            }
                        } else {
                            items(
                                items = pageNodes.chunked(columns),
                                key = { row -> "${group.name}:${row.joinToString("|")}" },
                            ) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(MihomoProxyNodeGridSpacing),
                                ) {
                                    row.forEach { nodeName ->
                                        val node = proxies.node(nodeName)
                                        val selectedNodeName = pendingSelections[group.name] ?: group.now
                                        MihomoProxyNodeCard(
                                            modifier = Modifier.weight(1f),
                                            node = node,
                                            selected = selectedNodeName == node.name,
                                            runtimeAvailable = runtimeAvailable,
                                            testing = testingTarget == node.name,
                                            onSelect = { selectProxy(group, node) },
                                            onDelayTest = { testProxy(node) },
                                        )
                                    }
                                    repeat(columns - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollBar(
                        adapter = rememberScrollBarAdapter(pageListState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        trackPadding = contentPadding,
                    )
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
    pagerPage: Int,
    pagerOffsetFraction: Float,
    onSelectedGroupNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return
    val density = LocalDensity.current
    val tabScrollState = rememberScrollState()
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var tabBounds by remember { mutableStateOf<Map<String, ProxyGroupTabBounds>>(emptyMap()) }
    val selectedIndex = groups.indexOfFirst { group -> group.name == selectedGroupName }
    val selectedBounds = tabBounds[selectedGroupName]
    val pagerPosition = (pagerPage + pagerOffsetFraction)
        .coerceIn(0f, groups.lastIndex.toFloat())
    val startIndex = floor(pagerPosition).toInt().coerceIn(0, groups.lastIndex)
    val endIndex = (startIndex + 1).coerceAtMost(groups.lastIndex)
    val indicatorStartBounds = tabBounds[groups[startIndex].name]
    val indicatorEndBounds = tabBounds[groups[endIndex].name] ?: indicatorStartBounds
    val indicatorFraction = pagerPosition - startIndex
    val indicatorLeftPx = if (indicatorStartBounds != null && indicatorEndBounds != null) {
        linearInterpolate(
            start = indicatorStartBounds.leftPx,
            end = indicatorEndBounds.leftPx,
            fraction = indicatorFraction,
        )
    } else {
        selectedBounds?.leftPx
    }
    val indicatorWidthPx = if (indicatorStartBounds != null && indicatorEndBounds != null) {
        linearInterpolate(
            start = indicatorStartBounds.widthPx,
            end = indicatorEndBounds.widthPx,
            fraction = indicatorFraction,
        )
    } else {
        selectedBounds?.widthPx
    }
    val indicatorOffset = with(density) { (indicatorLeftPx ?: 0).toDp() }
    val indicatorWidth = with(density) { (indicatorWidthPx ?: 0).toDp() }

    LaunchedEffect(selectedIndex, selectedBounds, viewportWidthPx) {
        if (selectedIndex < 0 || selectedBounds == null || viewportWidthPx <= 0) return@LaunchedEffect
        val visibleStart = tabScrollState.value
        val visibleEnd = visibleStart + viewportWidthPx
        val tabStart = selectedBounds.leftPx
        val tabEnd = selectedBounds.leftPx + selectedBounds.widthPx
        val targetScroll = when {
            tabStart < visibleStart -> tabStart
            tabEnd > visibleEnd -> tabEnd - viewportWidthPx
            else -> visibleStart
        }.coerceIn(0, tabScrollState.maxValue)
        if (targetScroll != visibleStart) {
            tabScrollState.animateScrollTo(targetScroll)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .onSizeChanged { size -> viewportWidthPx = size.width }
            .horizontalScroll(tabScrollState),
    ) {
        if (indicatorWidthPx != null && indicatorWidthPx > 0) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .height(MihomoProxyGroupTabHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(MihomoProxyGroupTabSpacing),
        ) {
            groups.forEach { group ->
                val selected = group.name == selectedGroupName
                val interactionSource = remember(group.name) { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .height(MihomoProxyGroupTabHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) {
                            onSelectedGroupNameChange(group.name)
                        }
                        .onGloballyPositioned { coordinates ->
                            val leftPx = coordinates.positionInParent().x.roundToInt()
                            val bounds = ProxyGroupTabBounds(
                                leftPx = leftPx,
                                widthPx = coordinates.size.width,
                            )
                            if (tabBounds[group.name] != bounds) {
                                tabBounds = tabBounds + (group.name to bounds)
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = group.name,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MihomoProxySearchBar(
    searchValue: String,
    onSearchValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchBar(
        modifier = modifier,
        inputField = {
            InputField(
                query = searchValue,
                onQueryChange = onSearchValueChange,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                label = stringResource(R.string.mihomo_proxies_search),
            )
        },
        expanded = false,
        onExpandedChange = {},
    ) {}
}

private fun filteredProxyNodeNames(
    group: MihomoProxyGroup?,
    proxies: MihomoProxiesState,
    searchQuery: String,
): List<String> {
    val keyword = searchQuery.trim()
    return group?.all
        ?.filter { nodeName ->
            val node = proxies.node(nodeName)
            val displayType = node.type.displayMihomoProtocolName()
            keyword.isEmpty() ||
                node.name.contains(keyword, ignoreCase = true) ||
                node.type.contains(keyword, ignoreCase = true) ||
                displayType.contains(keyword, ignoreCase = true)
        }
        .orEmpty()
}

@Composable
private fun MihomoProxyNodeCard(
    node: MihomoProxyNode,
    selected: Boolean,
    runtimeAvailable: Boolean,
    testing: Boolean,
    onSelect: () -> Unit,
    onDelayTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(MihomoProxyNodeCardHeight),
        colors = CardDefaults.defaultColors(color = mihomoProxyNodeCardColor(selected)),
        insideMargin = PaddingValues(MihomoProxyNodeCardPadding),
        onClick = onSelect,
        showIndication = false,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = node.name,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ProtocolDelayLine(
                protocol = node.type,
                delay = node.delay,
                selected = selected,
                testing = testing,
                enabled = runtimeAvailable && !testing,
                onClick = onDelayTest,
            )
        }
    }
}

@Composable
private fun ProtocolDelayLine(
    protocol: String,
    delay: Int?,
    selected: Boolean,
    testing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val delayText = when {
        testing -> ""
        delay == null -> ""
        delay < 0 -> stringResource(R.string.mihomo_proxies_delay_timeout)
        else -> "$delay ms"
    }.trim()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProtocolChip(
            text = protocol.displayMihomoProtocolName(),
            selected = selected,
            enabled = enabled,
            onClick = onClick,
        )
        if (delayText.isNotEmpty()) {
            Text(
                text = delayText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = delayColor(delayText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        FloatingToolbar(
            color = MiuixTheme.colorScheme.primary,
            cornerRadius = 32.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = MihomoFloatingToolbarVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    modifier = Modifier.size(MihomoFloatingToolbarButtonSize),
                    onClick = {
                        if (enabled) {
                            onDelayTest()
                        }
                    },
                ) {
                    DelayToolbarGlyph(
                        color = MiuixTheme.colorScheme.onPrimary.copy(
                            alpha = if (enabled && !testing) 1f else 0.45f,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtocolChip(
    text: String,
    selected: Boolean = false,
    enabled: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (enabled && onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(clickModifier)
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                },
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) {
                MiuixTheme.colorScheme.onPrimary
            } else {
                MiuixTheme.colorScheme.primary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DelayToolbarGlyph(
    color: Color,
) {
    Icon(
        modifier = Modifier.size(26.dp),
        imageVector = MiuixIcons.Stopwatch,
        contentDescription = stringResource(R.string.mihomo_proxies_delay_test),
        tint = color,
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
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun MihomoProxyNoConfigurationCard(
    onAddConfiguration: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 16.dp),
        colors = CardDefaults.defaultColors(color = mihomoProxyCardColor()),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.mihomo_proxies_no_configuration_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.mihomo_proxies_no_configuration_summary),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            TextButton(
                text = stringResource(R.string.mihomo_configuration_add),
                onClick = onAddConfiguration,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun mihomoProxyNodeCardColor(selected: Boolean): Color {
    return if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MiuixTheme.colorScheme.surface
    }
}

@Composable
private fun mihomoProxyCardColor(selected: Boolean = false): Color {
    return MiuixTheme.colorScheme.primary.copy(alpha = if (selected) 0.18f else 0.12f)
}

private fun parseMihomoProxyState(profile: String, dataDir: File): MihomoProxiesState {
    val escapedProfile = profile.escapeSupplementaryYamlCodePoints()
    val root = runCatching {
        val parsed = Load(LoadSettings.builder().build()).loadFromString(escapedProfile.value)
        escapedProfile.restoreParsedValue(parsed) as? Map<*, *>
    }.getOrNull().orEmpty()
    val nodes = linkedMapOf<String, MihomoProxyNode>()
    root["proxies"].asMapList().forEach { item -> item.putProxyNode(nodes) }
    val providerNodesByName = linkedMapOf<String, List<String>>()
    root["proxy-providers"].asMap().forEach { (providerName, providerValue) ->
        val name = providerName.asTextOrNull() ?: return@forEach
        val provider = providerValue as? Map<*, *> ?: return@forEach
        val providerNodes = provider.proxyProviderNodeNames(nodes, dataDir)
        if (providerNodes.isNotEmpty()) {
            providerNodesByName[name] = providerNodes
        }
    }
    val groups = root["proxy-groups"].asMapList().mapNotNull { item ->
        val name = item["name"].asTextOrNull() ?: return@mapNotNull null
        val all = buildList {
            addAll(item["proxies"].asTextList())
            item["use"].asTextList().forEach { providerName ->
                addAll(providerNodesByName[providerName].orEmpty())
            }
        }.distinct()
        all.forEach { proxyName ->
            nodes.putIfAbsent(proxyName, MihomoProxyNode(name = proxyName, type = "Built-in"))
        }
        MihomoProxyGroup(
            name = name,
            type = item["type"].asTextOrNull().orEmpty().ifBlank { "Selector" },
            now = all.firstOrNull().orEmpty(),
            all = all,
            testUrl = item["url"].asTextOrNull().orEmpty(),
            hidden = item["hidden"].asBooleanOrFalse(),
        )
    }.filterNot(MihomoProxyGroup::hidden)

    return MihomoProxiesState(
        groups = groups,
        nodes = nodes.values.toList(),
        nodeByName = nodes,
        updatedAtMillis = System.currentTimeMillis(),
    )
}

private fun AppState.selectedMihomoProfileContentHash(): Int {
    return selectedMihomoProfileOrNull()?.content?.hashCode() ?: 0
}

private fun AppState.selectedMihomoProfileOverrideHash(): Int {
    val profile = selectedMihomoProfileOrNull() ?: return 0
    val script = mihomoOverrideScripts.firstOrNull { item -> item.id == profile.overrideScriptId }
    return 31 * profile.overrideScriptId + (script?.content?.hashCode() ?: 0)
}

private fun Map<*, *>.putProxyNode(nodes: MutableMap<String, MihomoProxyNode>): String? {
    val name = this["name"].asTextOrNull() ?: return null
    nodes[name] = MihomoProxyNode(
        name = name,
        type = this["type"].asTextOrNull().orEmpty().ifBlank { "Proxy" },
        udp = this["udp"].asBooleanOrFalse(),
    )
    return name
}

private fun Map<*, *>.proxyProviderNodeNames(
    nodes: MutableMap<String, MihomoProxyNode>,
    dataDir: File,
): List<String> {
    val payload = this["payload"].asMapList()
        .ifEmpty { this["proxies"].asMapList() }
        .ifEmpty { proxyProviderFilePayload(dataDir) }
    return payload.mapNotNull { item -> item.putProxyNode(nodes) }
}

private fun Map<*, *>.proxyProviderFilePayload(dataDir: File): List<Map<*, *>> {
    val file = mihomoProxyProviderFileCandidates(dataDir, this)
        .firstOrNull { candidate -> candidate.exists() && candidate.length() > 0 }
        ?: return emptyList()
    val escaped = runCatching { file.readText().escapeSupplementaryYamlCodePoints() }.getOrNull()
        ?: return emptyList()
    val parsed = runCatching {
        val value = Load(LoadSettings.builder().build()).loadFromString(escaped.value)
        escaped.restoreParsedValue(value)
    }.getOrNull()
    return when (parsed) {
        is Map<*, *> -> parsed["proxies"].asMapList().ifEmpty { parsed["payload"].asMapList() }
        is List<*> -> parsed.asMapList()
        else -> emptyList()
    }
}

private fun Any?.asMap(): Map<*, *> {
    return this as? Map<*, *> ?: emptyMap<Any?, Any?>()
}

private fun Any?.asMapList(): List<Map<*, *>> {
    return (this as? List<*>)?.mapNotNull { item -> item as? Map<*, *> }.orEmpty()
}

private fun Any?.asTextList(): List<String> {
    return (this as? List<*>)?.mapNotNull { item -> item.asTextOrNull() }.orEmpty()
}

private fun Any?.asTextOrNull(): String? {
    return when (this) {
        null -> null
        is String -> this
        else -> toString()
    }?.trim()?.takeIf(String::isNotEmpty)
}

private fun Any?.asBooleanOrFalse(): Boolean {
    return when (this) {
        is Boolean -> this
        is String -> this.toBooleanStrictOrNull() ?: false
        else -> false
    }
}

private fun String.displayMihomoProtocolName(): String {
    val protocol = trim().ifBlank { return "Proxy" }
    return when (protocol.lowercase().replace("_", "-").replace(" ", "-")) {
        "vmess" -> "VMess"
        "vless" -> "VLESS"
        "ss", "shadowsocks" -> "Shadowsocks"
        "ssr", "shadowsocksr", "shadowsocks-r" -> "ShadowsocksR"
        "socks", "socks5" -> "SOCKS5"
        "http" -> "HTTP"
        "https" -> "HTTPS"
        "trojan" -> "Trojan"
        "hysteria" -> "Hysteria"
        "hysteria2", "hy2" -> "Hysteria2"
        "tuic" -> "TUIC"
        "wireguard", "wire-guard", "wg" -> "WireGuard"
        "snell" -> "Snell"
        "ssh" -> "SSH"
        "dns" -> "DNS"
        "mieru" -> "Mieru"
        "anytls", "any-tls" -> "AnyTLS"
        "masque" -> "MASQUE"
        "openvpn", "open-vpn" -> "OpenVPN"
        "tailscale" -> "Tailscale"
        "trusttunnel", "trust-tunnel" -> "TrustTunnel"
        "gostrelay", "gost-relay" -> "GostRelay"
        "direct" -> "Direct"
        "reject" -> "Reject"
        "rejectdrop", "reject-drop" -> "RejectDrop"
        "compatible" -> "Compatible"
        "pass" -> "Pass"
        "passrule", "pass-rule" -> "PassRule"
        "relay" -> "Relay"
        "select", "selector" -> "Selector"
        "fallback" -> "Fallback"
        "urltest", "url-test" -> "URLTest"
        "loadbalance", "load-balance" -> "LoadBalance"
        "built-in", "builtin" -> "Built-in"
        else -> protocol
    }
}

@Composable
private fun delayColor(text: String): Color {
    val delay = ProxyDelayNumberRegex.find(text)?.value?.toIntOrNull()
    val darkTheme = isInDarkTheme()
    return when {
        delay == null -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        delay < 300 -> if (darkTheme) Color(0xFF6BD58A) else Color(0xFF128A3C)
        delay < 500 -> if (darkTheme) Color(0xFFFFC857) else Color(0xFFD18A00)
        delay < 700 -> if (darkTheme) Color(0xFFFF9B63) else Color(0xFFE06400)
        else -> MiuixTheme.colorScheme.error
    }
}

private val ProxyDelayNumberRegex = Regex("""\d+""")

private data class ProxyGroupTabBounds(
    val leftPx: Int,
    val widthPx: Int,
)

private fun linearInterpolate(start: Int, end: Int, fraction: Float): Int {
    return (start + (end - start) * fraction).roundToInt()
}

private val MihomoProxyGroupTabHeight = 36.dp
private val MihomoProxyGroupTabSpacing = 8.dp
private val MihomoProxyNodeCardHeight = 96.dp
private val MihomoProxyNodeCardPadding = 10.dp
private val MihomoProxyNodeGridSpacing = 12.dp
private val MihomoFloatingToolbarButtonSize = 52.dp
private val MihomoFloatingToolbarVerticalPadding = 8.dp
private val MihomoFloatingToolbarBottomSpacing = 16.dp
