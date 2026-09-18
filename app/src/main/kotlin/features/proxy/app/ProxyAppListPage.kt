// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.proxy.app

import android.content.pm.PackageManager
import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import features.proxy.app.model.name
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.modes.RunModeVpnService
import features.proxy.app.model.ProxyAppListItem
import features.proxy.app.model.ProxyAppListUserSpaceTabUi
import features.proxy.app.usecase.ProxyAppListClipboardData
import features.proxy.app.usecase.applyProxyAppListClipboardImport
import features.proxy.app.usecase.decodeProxyAppListFromClipboard
import features.proxy.app.usecase.encodeProxyAppListForClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import system.ANDROID_APP_ICON_SIZE_DP
import ui.clipboard.ClipboardImportException
import ui.clipboard.ClipboardImportFailure
import ui.clipboard.ClipboardImportMode
import ui.clipboard.getPlainText
import ui.clipboard.setPlainText
import ui.components.AsteriskPullToRefreshBox
import ui.components.AsteriskScaffold
import ui.components.AsteriskSearchField
import ui.components.AsteriskTopAppBar
import ui.components.ImportModeDialog
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ui.layout.cutoutHorizontalPadding
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageHorizontalPadding
import ui.layout.pageListPadding
import ui.text.formatTemplate
import kotlin.time.Duration.Companion.milliseconds
import ui.icons.AsteriskIcons as Icons

private const val ProxyAppListAutomaticLoadingMinVisibleMillis = 500L

@Composable
fun ProxyAppListPage(
    padding: PaddingValues,
    onBack: (() -> Unit)? = null,
) {
    val pageState = rememberProxyAppListPageState()
    val appState by LocalAppStateStore.current.collectAppState()
    val selfPackageName = LocalContext.current.applicationContext.packageName
    val packageManager: PackageManager = LocalContext.current.applicationContext.packageManager
    val updateAppState = LocalUpdateAppState.current
    val isWideScreen = LocalIsWideScreen.current
    val services = LocalAppServices.current
    val packageCatalog = services.packageCatalog
    val userSpaces = services.userSpaces
    val tipNotifier = services.tipNotifier
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.common_copied)
    val clipboardEmptyMessage = stringResource(R.string.common_clipboard_empty)
    val unsupportedClipboardMessage = stringResource(R.string.common_clipboard_unsupported_format)
    val importTitle = stringResource(R.string.proxy_app_list_import_clipboard_title)
    val importMessageTemplate = stringResource(R.string.proxy_app_list_import_clipboard_message)
    val importedTemplate = stringResource(R.string.proxy_app_list_imported)
    val noValidAppsMessage = stringResource(R.string.proxy_app_list_import_no_valid_apps)
    val invalidEntryMessage = stringResource(R.string.proxy_app_list_import_invalid_entry)
    val invalidUserIdMessage = stringResource(R.string.proxy_app_list_import_invalid_user)
    val unsupportedModeMessage = stringResource(R.string.proxy_app_list_import_unsupported_mode)
    var pendingAppListImport by remember { mutableStateOf<ProxyAppListClipboardData?>(null) }

    val scanSkippedGlobalMessage = stringResource(R.string.proxy_app_list_scan_china_skipped_global)
    val scanDoneTemplate = stringResource(R.string.proxy_app_list_scan_china_done)
    val scanNoMatchTemplate = stringResource(R.string.proxy_app_list_scan_china_no_match)
    val invertDoneMessage = stringResource(R.string.proxy_app_list_invert_done)
    val clearDoneMessage = stringResource(R.string.proxy_app_list_clear_done)
    var pendingScanJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }

    val appSelectionKeyGroups = remember(pageState.appPackages) {
        pageState.appPackages.groupBy { entry ->
            val userId = entry.userId ?: 0
            entry.uid?.let { "$userId:uid:$it" } ?: "$userId:package:${entry.packageName}"
        }.values.map { entries ->
            entries.map { "${it.userId ?: 0}:${it.packageName}" }
        }
    }

    val proxyAppListModes = proxyAppListModeLabels()
    val modeIndex = appState.proxyAppListMode.coerceIn(proxyAppListModes.indices)
    val isVpnServiceMode = appState.runMode == RunModeVpnService
    val selectedAppKeys = remember(appState.proxyAppListSelectedApps) {
        appState.proxyAppListSelectedApps.toSet()
    }
    val vpnServiceUserId = if (isVpnServiceMode) {
        pageState.userSpaces.firstOrNull()?.id
    } else {
        null
    }
    val userTabIds = remember(pageState.userTabs) {
        pageState.userTabs.map { tab -> tab.id }
    }
    val selectedUserId = pageState.selectedUserId
        ?.takeIf { userId -> userId in userTabIds }
        ?: userTabIds.firstOrNull()
    val selectedUserIndex = remember(userTabIds, selectedUserId) {
        userTabIds.indexOf(selectedUserId)
            .coerceAtLeast(0)
    }
    val userPagerState = key(userTabIds) {
        rememberPagerState(
            initialPage = selectedUserIndex,
            pageCount = { userTabIds.size.coerceAtLeast(1) },
        )
    }
    val iconSizePx = with(LocalDensity.current) {
        ANDROID_APP_ICON_SIZE_DP.dp.roundToPx()
    }

    ProxyAppListPageEffects(
        pageState = pageState,
        selectedApps = appState.proxyAppListSelectedApps,
        selectedAppKeys = selectedAppKeys,
        isVpnServiceMode = isVpnServiceMode,
        vpnServiceUserId = vpnServiceUserId,
        selfPackageName = selfPackageName,
        selectedUserIndex = selectedUserIndex,
        userTabIds = userTabIds,
        userPagerState = userPagerState,
        packageCatalog = packageCatalog,
        userSpaces = userSpaces,
        tipNotifier = tipNotifier,
        onSelectedAppsPruned = { previousSelection, prunedSelection ->
            updateAppState { state ->
                if (state.proxyAppListSelectedApps == previousSelection) {
                    state.copy(proxyAppListSelectedApps = prunedSelection)
                } else {
                    state
                }
            }
        },
    )

    AsteriskScaffold(
        topBar = {
            Column {
                ProxyAppListTopBar(
                    onBack = onBack,
                    searchValue = pageState.searchValue,
                    searchActive = searchActive,
                    showSystemApps = pageState.showSystemApps,
                    onSearchValueChange = { value -> pageState.searchValue = value },
                    onSearchActiveChange = { active ->
                        searchActive = active
                        if (!active) pageState.searchValue = ""
                    },
                    onMoreAction = { action ->
                        when (action) {
                            ProxyAppListMoreAction.ToggleSystemApps -> {
                                pageState.showSystemApps = !pageState.showSystemApps
                            }

                            ProxyAppListMoreAction.ImportClipboard -> {
                                scope.launch {
                                    runCatching {
                                        decodeProxyAppListFromClipboard(
                                            text = clipboard.getPlainText().orEmpty(),
                                            currentUserId = selectedUserId ?: 0,
                                            selfPackageName = selfPackageName,
                                        )
                                    }.onSuccess { imported ->
                                        pendingAppListImport = imported
                                    }.onFailure { error ->
                                        tipNotifier.show(
                                            error.proxyAppListClipboardImportMessage(
                                                emptyClipboard = clipboardEmptyMessage,
                                                unsupportedFormat = unsupportedClipboardMessage,
                                                noValidApps = noValidAppsMessage,
                                                invalidEntry = invalidEntryMessage,
                                                invalidUserId = invalidUserIdMessage,
                                                unsupportedMode = unsupportedModeMessage,
                                            ),
                                        )
                                    }
                                }
                            }

                            ProxyAppListMoreAction.ExportClipboard -> {
                                scope.launch {
                                    clipboard.setPlainText(
                                        encodeProxyAppListForClipboard(
                                            selectedApps = appState.proxyAppListSelectedApps,
                                            mode = appState.proxyAppListMode,
                                        ),
                                    )
                                    tipNotifier.show(copiedMessage)
                                }
                            }

                            ProxyAppListMoreAction.Help -> {
                                showHelpDialog = true
                            }

                            ProxyAppListMoreAction.InvertSelection -> {
                                val snapshot = pageState.appPackages
                                if (snapshot.isNotEmpty()) {
                                    updateAppState { state ->
                                        state.copy(proxyAppListSelectedApps = invertSelectionForScan(
                                            matched = expandSelectionToSharedUids(state.proxyAppListSelectedApps, appSelectionKeyGroups),
                                            allKeys = snapshot.map { "${it.userId ?: 0}:${it.packageName}" },
                                        ))
                                    }
                                    scope.launch { tipNotifier.show(invertDoneMessage) }
                                }
                            }

                            ProxyAppListMoreAction.ClearSelection -> {
                                updateAppState { state ->
                                    state.copy(proxyAppListSelectedApps = emptyList())
                                }
                                scope.launch { tipNotifier.show(clearDoneMessage) }
                            }

                            ProxyAppListMoreAction.ScanChinaApps -> {
                                val currentMode = appState.proxyAppListMode
                                if (currentMode == ProxyAppListModeGlobal) {
                                    scope.launch {
                                        tipNotifier.show(scanSkippedGlobalMessage)
                                    }
                                } else {
                                    val snapshot = pageState.appPackages.toList()
                                    if (snapshot.isEmpty()) {
                                        scope.launch {
                                            tipNotifier.show(scanNoMatchTemplate.formatTemplate("scanned" to 0))
                                        }
                                    } else {
                                        val snapshotMode = currentMode
                                        pageState.scanProgress = ScanProgressState(
                                            total = snapshot.size,
                                            scanned = 0,
                                            matched = emptyList(),
                                        )
                                        pendingScanJob?.cancel()
                                        pendingScanJob = scope.launch {
                                            try {
                                                val matchedEntries = LinkedHashMap<String, MatchedApp>()
                                                withContext(Dispatchers.IO) {
                                                    snapshot.forEachIndexed { index, entry ->
                                                        ensureActive()
                                                        if (AppScanner.isChinaApp(entry.packageName, packageManager)) {
                                                            val label = entry.name
                                                            val key = "${entry.userId ?: 0}:${entry.packageName}"
                                                            synchronized(matchedEntries) {
                                                                matchedEntries[key] = MatchedApp(key, entry.packageName, label)
                                                            }
                                                        }
                                                        val currentScanned = index + 1
                                                        val currentMatched = synchronized(matchedEntries) {
                                                            matchedEntries.values.toList()
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            pageState.scanProgress = ScanProgressState(
                                                                total = snapshot.size,
                                                                scanned = currentScanned,
                                                                matched = currentMatched,
                                                            )
                                                        }
                                                    }
                                                }
                                                val finalMatched = matchedEntries.values.toList()
                                                val finalKeys = expandSelectionToSharedUids(finalMatched.map { it.key }, appSelectionKeyGroups)
                                                val allKeys = snapshot.map { entry ->
                                                    "${entry.userId ?: 0}:${entry.packageName}"
                                                }
                                                updateAppState { state ->
                                                    if (state.proxyAppListMode != snapshotMode) return@updateAppState state
                                                    val nextSelection = when (snapshotMode) {
                                                        ProxyAppListModeBlacklist -> mergeSelectedAppsForScan(
                                                            current = state.proxyAppListSelectedApps,
                                                            matched = finalKeys,
                                                        )
                                                        ProxyAppListModeWhitelist -> invertSelectionForScan(
                                                            matched = finalKeys,
                                                            allKeys = allKeys,
                                                        )
                                                        else -> state.proxyAppListSelectedApps
                                                    }
                                                    state.copy(proxyAppListSelectedApps = nextSelection)
                                                }
                                                if (finalMatched.isNotEmpty()) {
                                                    tipNotifier.show(
                                                        String.format(scanDoneTemplate, snapshot.size, finalMatched.size),
                                                    )
                                                } else {
                                                    tipNotifier.show(
                                                        scanNoMatchTemplate.formatTemplate("scanned" to snapshot.size),
                                                    )
                                                }
                                            } finally {
                                                if (pendingScanJob === coroutineContext[kotlinx.coroutines.Job]) {
                                                    pageState.scanProgress = null
                                                    pendingScanJob = null
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    },
                )

                ProxyAppListModeSegmentedRow(
                    modes = proxyAppListModes,
                    selectedIndex = modeIndex,
                    onSelectedIndexChange = { index ->
                        updateAppState { state -> state.copy(proxyAppListMode = index) }
                    },
                    modifier = Modifier
                        .cutoutHorizontalPadding()
                        .pageHorizontalPadding()
                        .padding(top = 8.dp, bottom = 12.dp),
                )

                if (pageState.userTabs.size > 1) {
                    ProxyAppListUserSpaceTabs(
                        tabs = pageState.userTabs,
                        selectedUserId = selectedUserId,
                        onSelectedUserIdChange = { userId -> pageState.selectedUserId = userId },
                        modifier = Modifier
                            .cutoutHorizontalPadding()
                            .pageHorizontalPadding()
                            .padding(bottom = 8.dp),
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
        // Keep the viewport behind the glass header; inset only the list content.
        val listPadding = pageListPadding(contentPadding)

        ProxyAppListContent(
            pageState = pageState,
            selectedAppKeys = selectedAppKeys,
            modeIndex = modeIndex,
            iconSizePx = iconSizePx,
            listPadding = listPadding,
            userPagerState = userPagerState,
            onAppCheckedChange = { item, isChecked ->
                updateAppState { state ->
                    state.copy(
                        proxyAppListSelectedApps = updateProxyAppListSelection(
                            selectedApps = state.proxyAppListSelectedApps,
                            item = item,
                            isChecked = isChecked,
                        ),
                    )
                }
            },
        )
    }

    val appListImport = pendingAppListImport
    ImportModeDialog(
        show = appListImport != null,
        title = importTitle,
        message = importMessageTemplate.formatTemplate("count" to appListImport?.selectedApps.orEmpty().size),
        onDismissRequest = { pendingAppListImport = null },
        onModeSelected = { importMode ->
            val imported = pendingAppListImport ?: return@ImportModeDialog
            var importedCount = 0
            updateAppState { state ->
                val result = applyProxyAppListClipboardImport(
                    currentMode = state.proxyAppListMode,
                    currentSelectedApps = state.proxyAppListSelectedApps,
                    imported = imported,
                    mode = importMode,
                )
                importedCount = when (importMode) {
                    ClipboardImportMode.Replace -> result.selectedApps.size
                    ClipboardImportMode.Merge ->
                        (result.selectedApps.size - state.proxyAppListSelectedApps.size).coerceAtLeast(0)
                }
                state.copy(
                    proxyAppListMode = result.mode,
                    proxyAppListSelectedApps = result.selectedApps,
                )
            }
            pendingAppListImport = null
            scope.launch {
                tipNotifier.show(importedTemplate.formatTemplate("count" to importedCount))
            }
        },
    )
    ScanChinaAppsDialog(
        progress = pageState.scanProgress,
        onCancel = {
            pendingScanJob?.cancel()
            pendingScanJob = null
            pageState.scanProgress = null
        },
    )

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(stringResource(R.string.proxy_app_list_help_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.proxy_app_list_help_blacklist))
                    Text(stringResource(R.string.proxy_app_list_help_global))
                    Text(stringResource(R.string.proxy_app_list_help_whitelist))
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }
}

@Composable
private fun ProxyAppListTopBar(
    onBack: (() -> Unit)?,
    searchValue: String,
    searchActive: Boolean,
    showSystemApps: Boolean,
    onSearchValueChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onMoreAction: (ProxyAppListMoreAction) -> Unit,
) {
    if (searchActive) {
        // Intercept the system back action so it exits search mode first
        // instead of navigating away from the page. Without this, dismissing
        // the IME with back would still pop the destination when the user
        // taps back a second time.
        val searchBackEventState = rememberNavigationEventState(NavigationEventInfo.None)
        NavigationBackHandler(
            state = searchBackEventState,
            isBackEnabled = true,
            onBackCompleted = { onSearchActiveChange(false) },
        )
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        AsteriskTopAppBar(
            navigationIcon = {
                IconButton(onClick = { onSearchActiveChange(false) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
            },
            title = {
                AsteriskSearchField(
                    query = searchValue,
                    onQueryChange = onSearchValueChange,
                    placeholder = stringResource(R.string.proxy_app_list_search_label),
                    clearContentDescription = stringResource(R.string.common_clear),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            },
        )
    } else {
        AsteriskTopAppBar(
            navigationIcon = {
                onBack?.let { navigateBack ->
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                }
            },
            title = {
                Text(stringResource(R.string.proxy_app_list_title))
            },
            actions = {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.common_search),
                    )
                }
                ProxyAppListMoreActionsMenu(
                    showSystemApps = showSystemApps,
                    onAction = onMoreAction,
                )
            },
        )
    }
}

@Composable
private fun ProxyAppListContent(
    pageState: ProxyAppListPageState,
    selectedAppKeys: Set<String>,
    modeIndex: Int,
    iconSizePx: Int,
    listPadding: PaddingValues,
    userPagerState: PagerState,
    onAppCheckedChange: (ProxyAppListItem, Boolean) -> Unit,
) {
    var showAutomaticLoading by remember { mutableStateOf(false) }
    var automaticLoadingStartedAtMillis by remember { mutableStateOf<Long?>(null) }
    val automaticLoading = pageState.loadingApps && !pageState.refreshingApps
    val layoutDirection = LocalLayoutDirection.current
    val pagerListPadding = PaddingValues(
        start = listPadding.calculateStartPadding(layoutDirection),
        top = listPadding.calculateTopPadding(),
        end = listPadding.calculateEndPadding(layoutDirection),
        bottom = listPadding.calculateBottomPadding(),
    )

    LaunchedEffect(pageState.loadingApps, pageState.refreshingApps) {
        when {
            automaticLoading -> {
                automaticLoadingStartedAtMillis = SystemClock.elapsedRealtime()
                showAutomaticLoading = true
            }

            pageState.refreshingApps -> {
                automaticLoadingStartedAtMillis = null
                showAutomaticLoading = false
            }

            showAutomaticLoading -> {
                val startedAt = automaticLoadingStartedAtMillis
                val elapsed = if (startedAt == null) {
                    ProxyAppListAutomaticLoadingMinVisibleMillis
                } else {
                    SystemClock.elapsedRealtime() - startedAt
                }
                val remaining = ProxyAppListAutomaticLoadingMinVisibleMillis - elapsed
                if (remaining > 0L) {
                    delay(remaining.milliseconds)
                }
                automaticLoadingStartedAtMillis = null
                showAutomaticLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        AsteriskPullToRefreshBox(
            isRefreshing = pageState.refreshingApps,
            onRefresh = pageState::requestRefresh,
            indicatorTopPadding = listPadding.calculateTopPadding(),
            modifier = Modifier.fillMaxSize(),
        ) {
            HorizontalPager(
                state = userPagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
                verticalAlignment = Alignment.Top,
            ) { page ->
                ProxyAppListUserPage(
                    tab = pageState.userTabs.getOrNull(page),
                    pageState = pageState,
                    selectedAppKeys = selectedAppKeys,
                    modeIndex = modeIndex,
                    iconSizePx = iconSizePx,
                    listPadding = pagerListPadding,
                    onAppCheckedChange = onAppCheckedChange,
                )
            }
        }
        if (showAutomaticLoading) {
            ProxyAppListLoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(listPadding),
            )
        }
    }
}

@Composable
private fun ProxyAppListLoadingState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.5.dp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.proxy_app_list_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProxyAppListUserPage(
    tab: ProxyAppListUserSpaceTabUi?,
    pageState: ProxyAppListPageState,
    selectedAppKeys: Set<String>,
    modeIndex: Int,
    iconSizePx: Int,
    listPadding: PaddingValues,
    onAppCheckedChange: (ProxyAppListItem, Boolean) -> Unit,
) {
    val userId = tab?.id
    val visibleApps = userId?.let { id ->
        pageState.preparedAppListData.visibleItemsByUser[id]
    }.orEmpty()
    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = listPadding,
    ) {
        when {
            visibleApps.isEmpty() -> item(key = "app_empty", contentType = "empty") {
                ProxyAppListEmptyState()
            }

            else -> items(
                items = visibleApps,
                key = { item -> item.key },
                contentType = { "app" },
            ) { item ->
                val checked = remember(item.selectionKeys, selectedAppKeys) {
                    item.selectionKeys.any { key -> key in selectedAppKeys }
                }
                ProxyAppListItemCard(
                    app = item.app,
                    checked = checked,
                    enabled = modeIndex != ProxyAppListGlobalModeIndex,
                    sharedUid = item.sharedUid,
                    iconSizePx = iconSizePx,
                    onCheckedChange = { isChecked ->
                        onAppCheckedChange(item, isChecked)
                    },
                )
            }
        }
    }
}

@Composable
private fun proxyAppListModeLabels(): List<String> {
    return listOf(
        stringResource(R.string.proxy_app_list_mode_blacklist),
        stringResource(R.string.proxy_app_list_mode_whitelist),
        stringResource(R.string.proxy_app_list_mode_global),
    )
}

private fun Throwable.proxyAppListClipboardImportMessage(
    emptyClipboard: String,
    unsupportedFormat: String,
    noValidApps: String,
    invalidEntry: String,
    invalidUserId: String,
    unsupportedMode: String,
): String {
    return when ((this as? ClipboardImportException)?.failure) {
        ClipboardImportFailure.EmptyClipboard -> emptyClipboard
        ClipboardImportFailure.NoValidApps -> noValidApps
        ClipboardImportFailure.InvalidAppEntry -> invalidEntry
        ClipboardImportFailure.InvalidAppUserId -> invalidUserId
        ClipboardImportFailure.UnsupportedAppMode -> unsupportedMode
        ClipboardImportFailure.UnsupportedFormat -> unsupportedFormat
        else -> unsupportedFormat
    }
}
