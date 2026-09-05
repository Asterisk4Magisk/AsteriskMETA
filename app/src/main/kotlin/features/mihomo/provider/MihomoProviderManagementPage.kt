// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.mihomo.provider

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import app.collectAppState
import app.navigation.Route
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.MihomoProviderDeclaration
import engine.mihomo.MihomoProviderRawContent
import engine.mihomo.MihomoProviderType
import engine.mihomo.hasUsableMihomoProfile
import engine.mihomo.parseMihomoProviderDeclarations
import engine.mihomo.runtime.MihomoProxyProviderRuntimeDetail
import engine.mihomo.runtime.MihomoRuleProviderRuntimeSummary
import engine.mihomo.runtime.runMihomoRuntimeCatching
import engine.mihomo.selectedMihomoProfileOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.layout.pageContentPaddingWithCutout
import ui.theme.AsteriskMotion
import java.io.File
import ui.icons.AsteriskIcons as Icons

@Composable
fun MihomoProviderManagementPage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val appState by LocalAppStateStore.current.collectAppState()
    val services = LocalAppServices.current
    val appContext = LocalContext.current.applicationContext
    val loader = remember { MihomoProviderRawContentLoader() }
    val actionScope = remember(appState) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    DisposableEffect(actionScope) {
        onDispose { actionScope.cancel() }
    }
    val tipNotifier = services.tipNotifier
    val proxyRefreshedMessage = stringResource(R.string.mihomo_proxy_providers_refresh_done)
    val proxyRefreshFailedMessage = stringResource(R.string.mihomo_proxy_providers_refresh_failed)
    val proxyRefreshAllMessage = stringResource(R.string.mihomo_proxy_providers_refresh_all_done)
    val ruleRefreshedMessage = stringResource(R.string.mihomo_rule_providers_refresh_done)
    val ruleRefreshFailedMessage = stringResource(R.string.mihomo_rule_providers_refresh_failed)
    val ruleRefreshAllMessage = stringResource(R.string.mihomo_rule_providers_refresh_all_done)
    val previewFailedMessage = stringResource(R.string.mihomo_configuration_preview_failed)
    val providerFileUnavailableMessage = stringResource(R.string.mihomo_provider_file_missing)
    var selectedTab by remember { mutableStateOf(defaultMihomoProviderManagementTab()) }
    val providerPagerState = rememberPagerState(
        initialPage = defaultMihomoProviderManagementTab().ordinal,
        pageCount = { MihomoProviderManagementTab.entries.size },
    )
    var providerStates by remember {
        mutableStateOf(
            MihomoProviderType.entries.associateWith { ProviderDeclarationsState(loading = true) },
        )
    }
    var proxyRuntimeDetails by remember {
        mutableStateOf<Map<String, MihomoProxyProviderRuntimeDetail>>(emptyMap())
    }
    var ruleRuntimeSummaries by remember {
        mutableStateOf<Map<String, MihomoRuleProviderRuntimeSummary>>(emptyMap())
    }
    var ruleRuntimeLoading by remember { mutableStateOf(false) }
    var ruleRuntimeError by remember { mutableStateOf("") }
    var ruleRuntimeLoaded by remember { mutableStateOf(false) }
    var nextProxyRuntimeRequestId by remember { mutableIntStateOf(0) }
    var proxyRuntimeRequestIds by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var nextRuleRuntimeRequestId by remember { mutableIntStateOf(0) }
    var activeRuleRuntimeRequestId by remember { mutableIntStateOf(0) }
    var refreshingNamesByType by remember {
        mutableStateOf<Map<MihomoProviderType, Set<String>>>(emptyMap())
    }
    var refreshingAllTypes by remember { mutableStateOf<Set<MihomoProviderType>>(emptySet()) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var ruleRuntimeReloadToken by remember { mutableIntStateOf(0) }
    var previewState by remember { mutableStateOf(MihomoProviderPreviewState()) }
    val proxyProviders = providerStates[MihomoProviderType.Proxy]?.providers.orEmpty()
    val ruleProviders = providerStates[MihomoProviderType.Rule]?.providers.orEmpty()
    val proxyProviderNames = proxyProviders.map(MihomoProviderDeclaration::name)
    val profileAgeSecretKey = appState.selectedMihomoProfileOrNull()?.ageSecretKey.orEmpty()
    val proxyListState = rememberLazyListState()
    val ruleListState = rememberLazyListState()

    val pagerSpatialMotion = AsteriskMotion.spatial<Float>()
    val providerPagerScope = rememberCoroutineScope()
    val selectProviderTab: (MihomoProviderManagementTab) -> Unit = { tab ->
        selectedTab = tab
        if (providerPagerState.targetPage != tab.ordinal) {
            providerPagerScope.launch {
                providerPagerState.animateScrollToPage(
                    page = tab.ordinal,
                    animationSpec = pagerSpatialMotion,
                )
            }
        }
    }

    LaunchedEffect(providerPagerState) {
        snapshotFlow { providerPagerState.targetPage }
            .collect { page ->
                val tab = mihomoProviderManagementTabForPage(page)
                if (selectedTab != tab) {
                    selectedTab = tab
                }
            }
    }

    LaunchedEffect(appState, reloadToken) {
        providerStates = MihomoProviderType.entries.associateWith {
            ProviderDeclarationsState(loading = true)
        }
        proxyRuntimeDetails = emptyMap()
        ruleRuntimeSummaries = emptyMap()
        ruleRuntimeError = ""
        ruleRuntimeLoaded = false
        proxyRuntimeRequestIds = emptyMap()
        activeRuleRuntimeRequestId = 0
        ruleRuntimeLoading = false
        refreshingNamesByType = emptyMap()
        refreshingAllTypes = emptySet()
        previewState = dismissMihomoProviderPreview(previewState)
        providerStates = loadProviderDeclarationsByType(
            context = appContext,
            appState = appState,
            dataDir = appContext.mihomoProviderDataDir(),
        )
    }

    suspend fun loadProxyProviderRuntimeDetail(name: String) {
        val requestId = ++nextProxyRuntimeRequestId
        proxyRuntimeRequestIds = proxyRuntimeRequestIds + (name to requestId)
        services.mihomoRuntime.getProxyProviderDetail(appState, name)
            .onSuccess { detail ->
                if (proxyRuntimeRequestIds[name] == requestId) {
                    proxyRuntimeDetails = proxyRuntimeDetails + (name to detail)
                }
            }
    }

    suspend fun loadRuleProviderRuntimeSummaries() {
        val requestId = ++nextRuleRuntimeRequestId
        activeRuleRuntimeRequestId = requestId
        ruleRuntimeLoading = true
        ruleRuntimeError = ""
        try {
            services.mihomoRuntime.getRuleProviderSummaries(appState)
                .onSuccess { summaries ->
                    if (activeRuleRuntimeRequestId == requestId) {
                        ruleRuntimeSummaries = summaries
                    }
                }
                .onFailure { error ->
                    if (activeRuleRuntimeRequestId == requestId) {
                        ruleRuntimeError = error.message.orEmpty()
                    }
                }
            if (activeRuleRuntimeRequestId == requestId) {
                ruleRuntimeLoaded = true
            }
        } finally {
            if (activeRuleRuntimeRequestId == requestId) {
                ruleRuntimeLoading = false
            }
        }
    }

    LaunchedEffect(appState, proxyProviderNames) {
        proxyRuntimeDetails = emptyMap()
        proxyProviderNames.forEach { name ->
            launch {
                loadProxyProviderRuntimeDetail(name)
            }
        }
    }

    LaunchedEffect(
        selectedTab,
        ruleProviders,
        ruleRuntimeReloadToken,
        appState,
    ) {
        val ruleState = providerStates[MihomoProviderType.Rule] ?: return@LaunchedEffect
        if (
            selectedTab == MihomoProviderManagementTab.Rule &&
            !ruleState.loading &&
            ruleState.error.isBlank() &&
            ruleProviders.isNotEmpty() &&
            !ruleRuntimeLoaded
        ) {
            loadRuleProviderRuntimeSummaries()
        }
    }

    fun setProviderRefreshing(type: MihomoProviderType, name: String, refreshing: Boolean) {
        val current = refreshingNamesByType[type].orEmpty()
        val updated = if (refreshing) current + name else current - name
        refreshingNamesByType = if (updated.isEmpty()) {
            refreshingNamesByType - type
        } else {
            refreshingNamesByType + (type to updated)
        }
    }

    fun refreshProvider(provider: MihomoProviderDeclaration) {
        val type = provider.providerType
        if (
            isProviderTypeBusy(
                type = type,
                refreshingNamesByType = refreshingNamesByType,
                refreshingAllTypes = refreshingAllTypes,
                ruleRuntimeLoading = ruleRuntimeLoading,
            )
        ) return
        setProviderRefreshing(type, provider.name, true)
        actionScope.launch {
            try {
                val result = when (type) {
                    MihomoProviderType.Proxy -> services.mihomoRuntime.refreshProxyProvider(appState, provider.name)
                    MihomoProviderType.Rule -> services.mihomoRuntime.refreshRuleProvider(appState, provider.name)
                }
                result
                    .onSuccess {
                        when (type) {
                            MihomoProviderType.Proxy -> loadProxyProviderRuntimeDetail(provider.name)
                            MihomoProviderType.Rule -> loadRuleProviderRuntimeSummaries()
                        }
                        tipNotifier.show(
                            if (type == MihomoProviderType.Proxy) {
                                proxyRefreshedMessage
                            } else {
                                ruleRefreshedMessage
                            },
                        )
                    }
                    .onFailure { error ->
                        tipNotifier.showError(
                            error,
                            if (type == MihomoProviderType.Proxy) {
                                proxyRefreshFailedMessage
                            } else {
                                ruleRefreshFailedMessage
                            },
                        )
                    }
            } finally {
                setProviderRefreshing(type, provider.name, false)
            }
        }
    }

    fun previewProvider(provider: MihomoProviderDeclaration) {
        val pendingState = beginMihomoProviderPreview(previewState, provider.name)
        previewState = pendingState
        val requestId = pendingState.requestId
        actionScope.launch {
            runMihomoRuntimeCatching {
                loader.load(provider, profileAgeSecretKey, providerFileUnavailableMessage)
            }.onSuccess { content ->
                val resolvedContent = if (
                    content is MihomoProviderRawContent.Binary &&
                    provider.providerType == MihomoProviderType.Rule
                ) {
                    content.copy(ruleCount = ruleRuntimeSummaries[provider.name]?.ruleCount)
                } else {
                    content
                }
                previewState = completeMihomoProviderPreview(
                    previewState,
                    requestId,
                    resolvedContent,
                )
            }.onFailure { error ->
                previewState = failMihomoProviderPreview(previewState, requestId)
                tipNotifier.showError(error, previewFailedMessage)
            }
        }
    }

    fun refreshAllProviders(type: MihomoProviderType) {
        if (
            isProviderTypeBusy(
                type = type,
                refreshingNamesByType = refreshingNamesByType,
                refreshingAllTypes = refreshingAllTypes,
                ruleRuntimeLoading = ruleRuntimeLoading,
            )
        ) return
        val providers = providerStates[type]?.providers.orEmpty()
        if (providers.isEmpty()) return
        refreshingAllTypes = refreshingAllTypes + type
        refreshingNamesByType = refreshingNamesByType + (
            type to providers.map(MihomoProviderDeclaration::name).toSet()
        )
        actionScope.launch {
            try {
                val results = providers.map { provider ->
                    when (type) {
                        MihomoProviderType.Proxy -> services.mihomoRuntime.refreshProxyProvider(appState, provider.name)
                        MihomoProviderType.Rule -> services.mihomoRuntime.refreshRuleProvider(appState, provider.name)
                    }
                }
                when (type) {
                    MihomoProviderType.Proxy -> providers.forEach { provider ->
                        loadProxyProviderRuntimeDetail(provider.name)
                    }
                    MihomoProviderType.Rule -> loadRuleProviderRuntimeSummaries()
                }
                val summary = reduceProviderRefreshResults(results)
                val message = if (type == MihomoProviderType.Proxy) {
                    proxyRefreshAllMessage
                } else {
                    ruleRefreshAllMessage
                }
                tipNotifier.show(message.format(summary.successCount, summary.failureCount))
            } finally {
                refreshingNamesByType = refreshingNamesByType - type
                refreshingAllTypes = refreshingAllTypes - type
            }
        }
    }

    val selectedType = selectedTab.providerType
    val selectedState = providerStates[selectedType] ?: ProviderDeclarationsState(loading = true)
    val selectedRefreshingAll = selectedType in refreshingAllTypes
    val selectedTypeBusy = isProviderTypeBusy(
        type = selectedType,
        refreshingNamesByType = refreshingNamesByType,
        refreshingAllTypes = refreshingAllTypes,
        ruleRuntimeLoading = ruleRuntimeLoading,
    )
    Scaffold(
            topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text(stringResource(R.string.mihomo_provider_management_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                    actions = {
                        if (selectedRefreshingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(14.dp).size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = { refreshAllProviders(selectedType) },
                                enabled = !selectedState.loading &&
                                    !selectedTypeBusy &&
                                    selectedState.providers.isNotEmpty(),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(
                                        R.string.mihomo_provider_management_refresh_current,
                                    ),
                                )
                            }
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    MihomoProviderManagementTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectProviderTab(tab) },
                            text = {
                                Text(
                                    stringResource(
                                        when (tab) {
                                            MihomoProviderManagementTab.Proxy -> R.string.mihomo_proxy_providers_tab
                                            MihomoProviderManagementTab.Rule -> R.string.mihomo_rule_providers_tab
                                        },
                                    ),
                                )
                            },
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
        HorizontalPager(
            state = providerPagerState,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            val tab = mihomoProviderManagementTabForPage(page)
            MihomoProviderManagementList(
                tab = tab,
                state = providerStates[tab.providerType] ?: ProviderDeclarationsState(loading = true),
                hasUsableProfile = appState.hasUsableMihomoProfile(),
                proxyRuntimeDetails = proxyRuntimeDetails,
                ruleRuntimeSummaries = ruleRuntimeSummaries,
                ruleRuntimeLoading = ruleRuntimeLoading,
                ruleRuntimeError = ruleRuntimeError,
                refreshingNames = refreshingNamesByType[tab.providerType].orEmpty(),
                refreshEnabled = !isProviderTypeBusy(
                    type = tab.providerType,
                    refreshingNamesByType = refreshingNamesByType,
                    refreshingAllTypes = refreshingAllTypes,
                    ruleRuntimeLoading = ruleRuntimeLoading,
                ),
                contentPadding = contentPadding,
                listState = if (tab == MihomoProviderManagementTab.Proxy) proxyListState else ruleListState,
                onRetryDeclarations = { reloadToken += 1 },
                onRetryRuleRuntime = {
                    ruleRuntimeLoaded = false
                    ruleRuntimeReloadToken += 1
                },
                onOpenProxy = { provider ->
                    navigator.push(Route.MihomoProxyProviderDetail(provider.name))
                },
                onPreview = ::previewProvider,
                onRefresh = ::refreshProvider,
            )
        }
    }
    previewState.rawContent?.let { rawContent ->
        MihomoProviderPreviewDialog(
            providerName = previewState.providerName,
            rawContent = rawContent,
            onDismissRequest = {
                previewState = dismissMihomoProviderPreview(previewState)
            },
        )
    }
}

internal suspend fun loadProxyProviderDeclarations(
    context: Context,
    appState: app.AppState,
    dataDir: File,
): ProviderDeclarationsState {
    return loadProviderDeclarationsByType(
        context = context,
        appState = appState,
        dataDir = dataDir,
    )[MihomoProviderType.Proxy] ?: ProviderDeclarationsState()
}

internal suspend fun loadProviderDeclarationsByType(
    context: Context,
    appState: app.AppState,
    dataDir: File,
): Map<MihomoProviderType, ProviderDeclarationsState> {
    if (!appState.hasUsableMihomoProfile()) {
        return MihomoProviderType.entries.associateWith { ProviderDeclarationsState() }
    }
    return withContext(Dispatchers.IO) {
        runMihomoRuntimeCatching {
            val profile = MihomoProfileFactory.buildProfile(context, appState)
            MihomoProviderType.entries.associateWith { type ->
                ProviderDeclarationsState(
                    providers = profile.parseMihomoProviderDeclarations(dataDir, type),
                )
            }
        }.getOrElse { error ->
            val detail = error.message.orEmpty()
            val message = if (detail.isBlank()) {
                context.getString(R.string.mihomo_provider_declarations_load_failed)
            } else {
                context.getString(R.string.mihomo_provider_declarations_load_failed_detail, detail)
            }
            MihomoProviderType.entries.associateWith {
                ProviderDeclarationsState(error = message)
            }
        }
    }
}
