// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.mihomo.provider

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import features.mihomo.YamlCodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import ui.components.BackNavigationIcon
import ui.components.IconDropdownMenu
import ui.components.IconDropdownMenuEntry
import ui.components.NavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import ui.text.formatTemplate
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
    val topAppBarScrollBehavior = MiuixScrollBehavior()
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
    var previewProviderName by rememberSaveable { mutableStateOf("") }
    var previewRawContent by remember { mutableStateOf<MihomoProviderRawContent?>(null) }
    val providerNames = providersState.providers.map(MihomoProviderDeclaration::name)
    val profileAgeSecretKey = appState.selectedMihomoProfileOrNull()?.ageSecretKey.orEmpty()

    LaunchedEffect(appState) {
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
            AdaptiveTopAppBar(
                title = stringResource(R.string.mihomo_providers_title),
                subtitle = stringResource(R.string.mihomo_providers_count)
                    .formatTemplate("count" to providersState.providers.size),
                isWideScreen = isWideScreen,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = { navigator.pop() }) },
                actions = {
                    NavigationIcon(
                        onClick = ::refreshAllProviders,
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.mihomo_providers_refresh_all),
                    )
                },
            )
        },
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val listPadding = pageListPadding(contentPadding)
        Box {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = listPadding,
            ) {
                when {
                    providersState.loading -> item(key = "loading") {
                        ProviderMessageCard(text = stringResource(R.string.mihomo_dashboard_network_detection_checking))
                    }

                    providersState.error.isNotBlank() -> item(key = "error") {
                        ProviderMessageCard(text = providersState.error)
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
                        item(key = "providers_title") {
                            SmallTitle(text = stringResource(R.string.mihomo_providers_title))
                        }
                        items(
                            items = providersState.providers,
                            key = { provider -> provider.name },
                        ) { provider ->
                            MihomoProviderCard(
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
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
    MihomoProviderPreviewDialog(
        show = previewRawContent != null,
        providerName = previewProviderName,
        rawContent = previewRawContent,
        onDismissRequest = {
            previewRawContent = null
            previewProviderName = ""
        },
    )
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
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var declarationsState by remember { mutableStateOf(ProviderDeclarationsState(loading = true)) }
    var runtimeDetail by remember { mutableStateOf<MihomoProxyProviderRuntimeDetail?>(null) }
    var runtimeLoading by remember { mutableStateOf(false) }
    var runtimeError by remember { mutableStateOf("") }
    var nodeSearchQuery by rememberSaveable { mutableStateOf("") }

    val provider = declarationsState.providers.firstOrNull { declaration -> declaration.name == providerName }

    LaunchedEffect(appState) {
        declarationsState = ProviderDeclarationsState(loading = true)
        declarationsState = loadProviderDeclarations(
            context = appContext,
            appState = appState,
            dataDir = appContext.mihomoProviderDataDir(),
        )
    }

    LaunchedEffect(provider, appState) {
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
            AdaptiveTopAppBar(
                title = provider?.name ?: providerName,
                subtitle = stringResource(R.string.mihomo_provider_nodes),
                isWideScreen = isWideScreen,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = { navigator.pop() }) },
                bottomContent = {
                    ProviderNodeSearchBar(
                        searchValue = nodeSearchQuery,
                        onSearchValueChange = { nodeSearchQuery = it },
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    )
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
        Box {
            when {
                declarationsState.loading || provider == null -> {
                    val lazyListState = rememberLazyListState()
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.pageScrollModifiers(topAppBarScrollBehavior),
                        contentPadding = listPadding,
                    ) {
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
                    VerticalScrollBar(
                        adapter = rememberScrollBarAdapter(lazyListState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        trackPadding = contentPadding,
                    )
                }

                else -> {
                    val lazyListState = rememberLazyListState()
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.pageScrollModifiers(topAppBarScrollBehavior),
                        contentPadding = listPadding,
                    ) {
                        providerNodeItems(
                            detail = runtimeDetail,
                            loading = runtimeLoading,
                            error = runtimeError,
                            searchQuery = nodeSearchQuery,
                        )
                    }
                    VerticalScrollBar(
                        adapter = rememberScrollBarAdapter(lazyListState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        trackPadding = contentPadding,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.providerNodeItems(
    detail: MihomoProxyProviderRuntimeDetail?,
    loading: Boolean,
    error: String,
    searchQuery: String,
) {
    val allNodes = detail?.nodes.orEmpty()
    val nodes = allNodes.filterProviderNodes(searchQuery)
    when {
        loading -> item(key = "node_loading") {
            ProviderMessageCard(text = stringResource(R.string.mihomo_dashboard_network_detection_checking))
        }

        error.isNotBlank() -> item(key = "node_error") {
            ProviderMessageCard(text = "${stringResource(R.string.mihomo_provider_runtime_unavailable)}: $error")
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
            ProviderNodeCard(node = node)
        }
    }
}

@Composable
private fun MihomoProviderCard(
    provider: MihomoProviderDeclaration,
    runtimeDetail: MihomoProxyProviderRuntimeDetail?,
    refreshing: Boolean,
    onClick: () -> Unit,
    onAction: (MihomoProviderAction) -> Unit,
) {
    val vehicleText = runtimeDetail?.vehicleType?.ifBlank { null } ?: provider.vehicleType
    val interactionSource = remember { MutableInteractionSource() }
    val menuEntries = buildList {
        add(
            IconDropdownMenuEntry(
                key = MihomoProviderAction.Preview,
                title = stringResource(R.string.mihomo_configuration_preview),
                action = MihomoProviderAction.Preview,
            ),
        )
        if (!refreshing) {
            add(
                IconDropdownMenuEntry(
                    key = MihomoProviderAction.Sync,
                    title = stringResource(R.string.mihomo_configuration_sync),
                    action = MihomoProviderAction.Sync,
                ),
            )
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        colors = providerCardColors(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = provider.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = provider.sourceSummary.ifBlank { vehicleText },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ProviderChip(text = vehicleText)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconDropdownMenu(
                imageVector = MiuixIcons.More,
                contentDescription = stringResource(R.string.mihomo_configuration_actions),
                entries = menuEntries,
                onAction = onAction,
            )
        }
    }
}

private enum class MihomoProviderAction {
    Preview,
    Sync,
}

@Composable
private fun MihomoProviderPreviewDialog(
    show: Boolean,
    providerName: String,
    rawContent: MihomoProviderRawContent?,
    onDismissRequest: () -> Unit,
) {
    val content = rawContent?.content.orEmpty()
    val previewValue = remember(content) {
        TextFieldValue(
            text = content,
            selection = TextRange(0),
        )
    }
    val error = rawContent?.lastError.orEmpty()

    WindowDialog(
        show = show,
        title = stringResource(R.string.mihomo_configuration_preview_title)
            .formatTemplate("name" to providerName.ifBlank { "-" }),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (error.isNotBlank() && content.isBlank()) {
                ProviderMessageCard(text = error)
            } else {
                if (rawContent?.declarationOnly == true) {
                    SmallTitle(text = stringResource(R.string.mihomo_provider_raw_declaration))
                }
                YamlCodeEditor(
                    label = stringResource(R.string.mihomo_provider_file_content),
                    value = previewValue,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 360.dp, max = 520.dp)
                        .padding(bottom = 16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )
            }
            TextButton(
                text = stringResource(R.string.common_complete),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProviderNodeSearchBar(
    searchValue: String,
    onSearchValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchBar(
        modifier = modifier.fillMaxWidth(),
        inputField = {
            InputField(
                query = searchValue,
                onQueryChange = onSearchValueChange,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                label = stringResource(R.string.mihomo_provider_nodes_search),
            )
        },
        expanded = false,
        onExpandedChange = {},
    ) {}
}

@Composable
private fun ProviderNodeCard(
    node: MihomoProviderNode,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = providerCardColors(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = node.title.ifBlank { node.name },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProviderNodeProtocolChip(
                    text = node.protocolText().displayMihomoProtocolName(),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = node.delay.delayText(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = node.delay.providerDelayColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
            .padding(horizontal = 12.dp)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun ProviderMessageCard(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = providerCardColors(),
    ) {
        Text(
            text = text.ifBlank { stringResource(R.string.common_empty) },
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(16.dp),
        )
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

private fun List<MihomoProviderNode>.filterProviderNodes(searchQuery: String): List<MihomoProviderNode> {
    val keyword = searchQuery.trim()
    if (keyword.isEmpty()) return this
    return filter { node ->
        val displayType = node.protocolText().displayMihomoProtocolName()
        node.name.contains(keyword, ignoreCase = true) ||
            node.title.contains(keyword, ignoreCase = true) ||
            node.subtitle.contains(keyword, ignoreCase = true) ||
            node.type.contains(keyword, ignoreCase = true) ||
            displayType.contains(keyword, ignoreCase = true)
    }
}

private fun MihomoProviderNode.protocolText(): String {
    return type.ifBlank { subtitle.ifBlank { "Proxy" } }
}

private fun engine.mihomo.runtime.MihomoProviderSubscriptionInfo.hasProviderTrafficInfo(): Boolean {
    return upload > 0L || download > 0L || total > 0L || expire > 0L
}

@Composable
private fun ProviderNodeProtocolChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Int?.providerDelayColor(): Color {
    val darkTheme = isSystemInDarkTheme()
    return when {
        this == null -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        this < 0 -> MiuixTheme.colorScheme.error
        this < 100 -> if (darkTheme) Color(0xFF6BD58A) else Color(0xFF128A3C)
        this < 200 -> if (darkTheme) Color(0xFFFFC857) else Color(0xFFD18A00)
        this < 400 -> if (darkTheme) Color(0xFFFF9B63) else Color(0xFFE06400)
        else -> MiuixTheme.colorScheme.error
    }
}

@Composable
private fun providerCardColors() = CardDefaults.defaultColors(
    color = MiuixTheme.colorScheme.primary.copy(alpha = ProviderCardTintAlpha),
)

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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MiuixTheme.colorScheme.primary),
            )
        }
        Text(
            text = stringResource(R.string.mihomo_configuration_traffic_summary)
                .formatTemplate(
                    "used" to usedBytes.toReadableBytes(maxUnit = ReadableByteUnit.GiB),
                    "total" to info.total.toProviderTrafficTotalText(),
                    "expire" to info.expire.toProviderExpireText(),
                ),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
private fun ProviderChip(
    text: String,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.disabledOnSecondaryVariant.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
        this == null -> "-"
        this < 0 -> stringResource(R.string.mihomo_provider_delay_timeout)
        else -> "$this ms"
    }
}

private data class ProviderDeclarationsState(
    val loading: Boolean = false,
    val providers: List<MihomoProviderDeclaration> = emptyList(),
    val error: String = "",
)

private const val ProviderCardTintAlpha = 0.08f
