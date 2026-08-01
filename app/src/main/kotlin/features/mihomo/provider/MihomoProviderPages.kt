// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.mihomo.provider

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
import app.navigation.Route
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.MihomoProviderDeclaration
import engine.mihomo.MihomoProviderRawContent
import engine.mihomo.hasUsableMihomoProfile
import engine.mihomo.parseMihomoProxyProviderDeclarations
import engine.mihomo.runtime.MihomoProviderNode
import engine.mihomo.runtime.MihomoProviderSubscriptionInfo
import engine.mihomo.runtime.MihomoProxyProviderRuntimeDetail
import engine.mihomo.selectedMihomoProfileOrNull
import features.mihomo.displayMihomoProtocolName
import features.mihomo.MihomoCodeEditorState
import features.mihomo.YamlCodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.clipboard.setPlainText
import ui.components.AsteriskFilterChip
import ui.components.AsteriskStatusCard
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskInfoChip
import ui.components.AsteriskPinnedSearchArea
import ui.theme.ExpressiveShapeRole
import ui.layout.pageHorizontalPadding
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.text.formatTemplate
import ui.theme.AsteriskMotion
import utils.ReadableByteUnit
import utils.toReadableBytes
import utils.toReadableDateOrDash
import utils.toReadableDateTimeOrDash
import java.io.File

@Composable
fun MihomoProviderListPage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val appState by LocalAppStateStore.current.collectAppState()
    val services = LocalAppServices.current
    val appContext = LocalContext.current.applicationContext
    val loader = remember { MihomoProviderRawContentLoader() }
    val scope = rememberCoroutineScope()
    val tipNotifier = services.tipNotifier
    val refreshedMessage = stringResource(R.string.mihomo_providers_refresh_done)
    val refreshFailedMessage = stringResource(R.string.mihomo_providers_refresh_failed)
    val refreshAllMessage = stringResource(R.string.mihomo_providers_refresh_all_done)
    val previewFailedMessage = stringResource(R.string.mihomo_configuration_preview_failed)
    val providerFileUnavailableMessage = stringResource(R.string.mihomo_provider_file_missing)
    var providersState by remember { mutableStateOf(ProviderDeclarationsState(loading = true)) }
    var providerRuntimeDetails by remember { mutableStateOf<Map<String, MihomoProxyProviderRuntimeDetail>>(emptyMap()) }
    var refreshingNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refreshingAll by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var previewProviderName by rememberSaveable { mutableStateOf("") }
    var previewRawContent by remember { mutableStateOf<MihomoProviderRawContent?>(null) }
    val providerNames = providersState.providers.map(MihomoProviderDeclaration::name)
    val profileAgeSecretKey = appState.selectedMihomoProfileOrNull()?.ageSecretKey.orEmpty()

    if (previewRawContent != null) {
        MihomoProviderRawContentPage(
            padding = padding,
            providerName = previewProviderName,
            rawContent = previewRawContent!!,
            onBack = {
                previewRawContent = null
                previewProviderName = ""
            },
        )
        return
    }

    LaunchedEffect(appState, reloadToken) {
        providersState = ProviderDeclarationsState(loading = true)
        providersState = loadProviderDeclarations(
            context = appContext,
            appState = appState,
            dataDir = appContext.mihomoProviderDataDir(),
        )
    }

    suspend fun loadProviderRuntimeDetail(name: String) {
        services.mihomoRuntime.getProxyProviderDetail(appState, name)
            .onSuccess { detail ->
                providerRuntimeDetails = providerRuntimeDetails + (name to detail)
            }
    }

    LaunchedEffect(appState, providerNames) {
        providerRuntimeDetails = emptyMap()
        providerNames.forEach { name ->
            launch {
                loadProviderRuntimeDetail(name)
            }
        }
    }

    fun refreshProvider(name: String) {
        if (refreshingAll || name in refreshingNames) return
        refreshingNames = refreshingNames + name
        scope.launch {
            services.mihomoRuntime.refreshProxyProvider(appState, name)
                .onSuccess {
                    loadProviderRuntimeDetail(name)
                    tipNotifier.show(refreshedMessage)
                }
                .onFailure { error -> tipNotifier.showError(error, refreshFailedMessage) }
            refreshingNames = refreshingNames - name
        }
    }

    fun previewProvider(provider: MihomoProviderDeclaration) {
        scope.launch {
            runCatching {
                loader.load(provider, profileAgeSecretKey, providerFileUnavailableMessage)
            }.onSuccess { content ->
                previewProviderName = provider.name
                previewRawContent = content
            }.onFailure { error ->
                tipNotifier.showError(error, previewFailedMessage)
            }
        }
    }

    fun refreshAllProviders() {
        if (refreshingAll) return
        val providers = providersState.providers
        if (providers.isEmpty()) return
        refreshingAll = true
        refreshingNames = providers.map(MihomoProviderDeclaration::name).toSet()
        scope.launch {
            var successCount = 0
            var failedCount = 0
            providers.forEach { provider ->
                services.mihomoRuntime.refreshProxyProvider(appState, provider.name)
                    .onSuccess {
                        successCount += 1
                        loadProviderRuntimeDetail(provider.name)
                    }
                    .onFailure { error ->
                        failedCount += 1
                        if (failedCount == 1) {
                            tipNotifier.showError(error, refreshFailedMessage)
                        }
                    }
            }
            tipNotifier.show(refreshAllMessage.format(successCount, failedCount))
            refreshingNames = emptySet()
            refreshingAll = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mihomo_providers_title)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (refreshingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(14.dp).size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = ::refreshAllProviders) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.mihomo_providers_refresh_all),
                            )
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
        val listPadding = pageListPadding(contentPadding)
        LazyColumn(
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "provider_status") {
                ProviderStatusCard(
                    state = reduceProviderFocusState(
                        providerCount = providersState.providers.size,
                        runtimeDetails = providerRuntimeDetails.values,
                        loading = providersState.loading,
                        error = providersState.error,
                    ),
                )
            }
            when {
                providersState.loading -> item(key = "loading") {
                    ProviderMessageCard(text = stringResource(R.string.mihomo_dashboard_network_detection_checking))
                }

                providersState.error.isNotBlank() -> item(key = "error") {
                    ProviderMessageCard(
                        text = providersState.error,
                        actionText = stringResource(R.string.common_retry),
                        onAction = { reloadToken += 1 },
                    )
                }

                providersState.providers.isEmpty() -> item(key = "empty") {
                    ProviderMessageCard(
                        text = if (appState.hasUsableMihomoProfile()) {
                            stringResource(R.string.mihomo_providers_empty)
                        } else {
                            stringResource(R.string.mihomo_proxies_no_configuration_summary)
                        },
                    )
                }

                else -> {
                    items(
                        items = providersState.providers,
                        key = { provider -> provider.name },
                    ) { provider ->
                        MihomoProviderCard(
                            modifier = Modifier.animateItem(
                                fadeInSpec = AsteriskMotion.effects(),
                                placementSpec = AsteriskMotion.spatial(),
                                fadeOutSpec = AsteriskMotion.effects(),
                            ),
                            provider = provider,
                            runtimeDetail = providerRuntimeDetails[provider.name],
                            refreshing = provider.name in refreshingNames,
                            onClick = {
                                navigator.push(Route.MihomoProviderDetail(provider.name))
                            },
                            onAction = { action ->
                                when (action) {
                                    MihomoProviderAction.Preview -> previewProvider(provider)
                                    MihomoProviderAction.Sync -> refreshProvider(provider.name)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MihomoProviderDetailPage(
    padding: PaddingValues,
    providerName: String,
) {
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val appState by LocalAppStateStore.current.collectAppState()
    val services = LocalAppServices.current
    val appContext = LocalContext.current.applicationContext
    var declarationsState by remember { mutableStateOf(ProviderDeclarationsState(loading = true)) }
    var runtimeDetail by remember { mutableStateOf<MihomoProxyProviderRuntimeDetail?>(null) }
    var runtimeLoading by remember { mutableStateOf(false) }
    var runtimeError by remember { mutableStateOf("") }
    var runtimeReloadToken by remember { mutableIntStateOf(0) }
    var nodeSearchQuery by rememberSaveable { mutableStateOf("") }
    var nodeFilter by remember { mutableStateOf(ProviderNodeFilter.All) }

    val provider = declarationsState.providers.firstOrNull { declaration -> declaration.name == providerName }

    LaunchedEffect(appState) {
        declarationsState = ProviderDeclarationsState(loading = true)
        declarationsState = loadProviderDeclarations(
            context = appContext,
            appState = appState,
            dataDir = appContext.mihomoProviderDataDir(),
        )
    }

    LaunchedEffect(provider, appState, runtimeReloadToken) {
        runtimeDetail = null
        runtimeError = ""
        if (provider == null) {
            runtimeLoading = false
            return@LaunchedEffect
        }

        runtimeLoading = true
        services.mihomoRuntime.getProxyProviderDetail(appState, provider.name)
            .onSuccess { detail -> runtimeDetail = detail }
            .onFailure { error -> runtimeError = error.message.orEmpty() }
        runtimeLoading = false
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
                    ProviderNodeFilterChips(
                        selected = nodeFilter,
                        onSelected = { nodeFilter = it },
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
        val listPadding = pageListPadding(contentPadding)
        LazyColumn(
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
                        filter = nodeFilter,
                        onRetry = { runtimeReloadToken += 1 },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderStatusCard(
    state: ProviderFocusState,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(
        when (state.readiness) {
            ProviderReadiness.Loading -> R.string.mihomo_provider_focus_loading
            ProviderReadiness.Ready -> R.string.mihomo_provider_focus_ready
            ProviderReadiness.Empty -> R.string.mihomo_provider_focus_empty
            ProviderReadiness.Error -> R.string.mihomo_provider_focus_error
        },
    )
    val updated = if (state.updatedAtMillis > 0L) {
        stringResource(
            R.string.mihomo_provider_focus_updated,
            state.updatedAtMillis.toReadableDateTimeOrDash(),
        )
    } else {
        null
    }
    AsteriskStatusCard(
        modifier = modifier,
        status = updated,
        controls = {
            AsteriskInfoChip(
                text = pluralStringResource(
                    R.plurals.mihomo_provider_focus_progress,
                    state.readyCount,
                    state.readyCount,
                    state.providerCount,
                ),
            )
            Text(
                text = stringResource(R.string.mihomo_provider_nodes_count)
                    .formatTemplate("count" to state.nodeCount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudSync,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.providerNodeItems(
    detail: MihomoProxyProviderRuntimeDetail?,
    loading: Boolean,
    error: String,
    searchQuery: String,
    filter: ProviderNodeFilter,
    onRetry: () -> Unit,
) {
    val allNodes = detail?.nodes.orEmpty()
    val nodes = reduceMihomoProviderNodes(allNodes, searchQuery, filter)
    when {
        loading -> item(key = "node_loading") {
            ProviderMessageCard(text = stringResource(R.string.mihomo_dashboard_network_detection_checking))
        }

        error.isNotBlank() -> item(key = "node_error") {
            ProviderMessageCard(
                text = "${stringResource(R.string.mihomo_provider_runtime_unavailable)}: $error",
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
private fun MihomoProviderCard(
    modifier: Modifier = Modifier,
    provider: MihomoProviderDeclaration,
    runtimeDetail: MihomoProxyProviderRuntimeDetail?,
    refreshing: Boolean,
    onClick: () -> Unit,
    onAction: (MihomoProviderAction) -> Unit,
) {
    val vehicleText = runtimeDetail?.vehicleType?.ifBlank { null } ?: provider.vehicleType
    var menuExpanded by remember { mutableStateOf(false) }
    AsteriskExpressiveCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        role = ExpressiveShapeRole.ContentCard,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = provider.sourceSummary.ifBlank { vehicleText },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                runtimeDetail?.subscriptionInfo
                    ?.takeIf { info -> info.hasProviderTrafficInfo() }
                    ?.let { info -> MihomoProviderTrafficInfo(info = info) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = provider.runtimeSummaryText(runtimeDetail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    AsteriskInfoChip(text = vehicleText)
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.mihomo_configuration_actions),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mihomo_configuration_preview)) },
                        onClick = {
                            menuExpanded = false
                            onAction(MihomoProviderAction.Preview)
                        },
                        leadingIcon = { Icon(Icons.Rounded.Visibility, contentDescription = null) },
                    )
                    if (!refreshing) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mihomo_configuration_sync)) },
                            onClick = {
                                menuExpanded = false
                                onAction(MihomoProviderAction.Sync)
                            },
                            leadingIcon = { Icon(Icons.Rounded.Sync, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}

private enum class MihomoProviderAction {
    Preview,
    Sync,
}

@Composable
private fun MihomoProviderRawContentPage(
    padding: PaddingValues,
    providerName: String,
    rawContent: MihomoProviderRawContent,
    onBack: () -> Unit,
) {
    val isWideScreen = LocalIsWideScreen.current
    val clipboard = LocalClipboard.current
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val content = rawContent.content
    val previewEditorState = remember(content) {
        MihomoCodeEditorState(content).also { state ->
            state.replaceText(content, placeCursorAtEnd = false)
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val matchCount = remember(content, query) {
        if (query.isBlank()) {
            0
        } else {
            content.windowed(query.length, 1, partialWindows = false)
                .count { value -> value.equals(query, ignoreCase = true) }
        }
    }
    val copiedMessage = stringResource(R.string.logs_copied_to_clipboard)
    val sourceLabel = stringResource(
        if (rawContent.declarationOnly) {
            R.string.mihomo_provider_raw_declaration
        } else {
            R.string.mihomo_provider_raw_read_only
        },
    )
    val metadata = stringResource(
        R.string.mihomo_provider_raw_metadata,
        sourceLabel,
        content.toByteArray().size.toLong().toReadableBytes(),
    )

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text(providerName.ifBlank { "-" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setPlainText(content)
                                    services.tipNotifier.show(copiedMessage)
                                }
                            },
                            enabled = content.isNotBlank(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.common_copy),
                            )
                        }
                    },
                )
                AsteriskPinnedSearchArea(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.common_search),
                    clearContentDescription = stringResource(R.string.common_clear),
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .pageHorizontalPadding()
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = if (query.isBlank()) {
                    metadata
                } else {
                    "$metadata · ${pluralStringResource(R.plurals.mihomo_provider_raw_matches, matchCount, matchCount)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            if (rawContent.lastError.isNotBlank() && content.isBlank()) {
                ProviderMessageCard(text = rawContent.lastError)
            } else {
                YamlCodeEditor(
                    label = stringResource(R.string.mihomo_provider_file_content),
                    state = previewEditorState,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProviderNodeFilterChips(
    selected: ProviderNodeFilter,
    onSelected: (ProviderNodeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProviderNodeFilter.entries.forEach { filter ->
            AsteriskFilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = stringResource(
                    when (filter) {
                        ProviderNodeFilter.All -> R.string.common_all
                        ProviderNodeFilter.Available -> R.string.mihomo_provider_filter_available
                        ProviderNodeFilter.Timeout -> R.string.mihomo_provider_filter_timeout
                    },
                ),
            )
        }
    }
}

@Composable
private fun ProviderNodeCard(
    node: MihomoProviderNode,
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
            Text(
                text = node.delay.delayText(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = node.delay.providerDelayColor(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(88.dp),
            )
        }
    }
}

@Composable
private fun ProviderEmptyState(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.common_empty),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProviderMessageCard(
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AsteriskExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        role = ExpressiveShapeRole.ContentCard,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = text.ifBlank { stringResource(R.string.common_empty) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionText != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                ) {
                    Text(actionText)
                }
            }
        }
    }
}

private suspend fun loadProviderDeclarations(
    context: Context,
    appState: app.AppState,
    dataDir: File,
): ProviderDeclarationsState {
    if (!appState.hasUsableMihomoProfile()) {
        return ProviderDeclarationsState()
    }
    return withContext(Dispatchers.IO) {
        runCatching {
            val profile = MihomoProfileFactory.buildProfile(context, appState)
            ProviderDeclarationsState(
                providers = profile.parseMihomoProxyProviderDeclarations(dataDir),
            )
        }.getOrElse { error ->
            ProviderDeclarationsState(error = error.message.orEmpty())
        }
    }
}

private fun MihomoProviderNode.protocolText(): String {
    return type.ifBlank { subtitle.ifBlank { "Proxy" } }
}

private fun MihomoProviderSubscriptionInfo.hasProviderTrafficInfo(): Boolean {
    return upload > 0L || download > 0L || total > 0L || expire > 0L
}

@Composable
private fun Int?.providerDelayColor(): Color {
    val darkTheme = isSystemInDarkTheme()
    return when {
        this == null -> MaterialTheme.colorScheme.onSurfaceVariant
        this < 0 -> MaterialTheme.colorScheme.error
        this < 100 -> if (darkTheme) Color(0xFF6BD58A) else Color(0xFF128A3C)
        this < 200 -> if (darkTheme) Color(0xFFFFC857) else Color(0xFFD18A00)
        this < 400 -> if (darkTheme) Color(0xFFFF9B63) else Color(0xFFE06400)
        else -> MaterialTheme.colorScheme.error
    }
}

@Composable
private fun MihomoProviderTrafficInfo(
    info: MihomoProviderSubscriptionInfo,
) {
    val usedBytes = info.upload + info.download
    val progress = if (info.total > 0L) {
        (usedBytes.toDouble() / info.total.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Text(
            text = stringResource(R.string.mihomo_configuration_traffic_summary)
                .formatTemplate(
                    "used" to usedBytes.toReadableBytes(maxUnit = ReadableByteUnit.GiB),
                    "total" to info.total.toProviderTrafficTotalText(),
                    "expire" to info.expire.toProviderExpireText(),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun MihomoProviderDeclaration.runtimeSummaryText(
    runtimeDetail: MihomoProxyProviderRuntimeDetail?,
): String {
    val nodeText = runtimeDetail?.nodes?.size?.let { count ->
        stringResource(R.string.mihomo_provider_nodes_count).formatTemplate("count" to count)
    }
    val updatedText = runtimeDetail?.updatedAtMillis
        ?.takeIf { timestamp -> timestamp > 0L }
        ?.toReadableDateTimeOrDash()
    return listOfNotNull(nodeText, updatedText)
        .joinToString(" · ")
        .ifBlank { providerType.name }
}

@Composable
private fun Long.toProviderTrafficTotalText(): String {
    return if (this > 0L) {
        toReadableBytes(maxUnit = ReadableByteUnit.GiB)
    } else {
        stringResource(R.string.mihomo_provider_traffic_unlimited)
    }
}

@Composable
private fun Long.toProviderExpireText(): String {
    return if (this > 0L) {
        (this * 1_000L).toReadableDateOrDash()
    } else {
        stringResource(R.string.mihomo_configuration_expire_unlimited)
    }
}

@Composable
private fun Int?.delayText(): String {
    return when {
        this == null -> stringResource(R.string.mihomo_proxies_delay_not_tested)
        this < 0 -> stringResource(R.string.mihomo_provider_delay_timeout)
        else -> "$this ms"
    }
}

private data class ProviderDeclarationsState(
    val loading: Boolean = false,
    val providers: List<MihomoProviderDeclaration> = emptyList(),
    val error: String = "",
)
