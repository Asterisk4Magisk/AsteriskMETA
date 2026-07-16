// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.proxy.app

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ui.components.AsteriskPinnedSearchArea
import ui.components.ImportModeDialog
import ui.layout.pageContentPaddingWithCutout
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

    Scaffold(
        topBar = {
            ProxyAppListTopBar(
                onBack = onBack,
                modes = proxyAppListModes,
                modeIndex = modeIndex,
                searchValue = pageState.searchValue,
                showSystemApps = pageState.showSystemApps,
                userTabs = pageState.userTabs,
                selectedUserId = selectedUserId,
                onModeChanged = { index ->
                    updateAppState { state -> state.copy(proxyAppListMode = index) }
                },
                onSearchValueChange = { value -> pageState.searchValue = value },
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
                    }
                },
                onSelectedUserIdChange = { userId -> pageState.selectedUserId = userId },
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
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
}

@Composable
private fun ProxyAppListTopBar(
    onBack: (() -> Unit)?,
    modes: List<String>,
    modeIndex: Int,
    searchValue: String,
    showSystemApps: Boolean,
    userTabs: List<ProxyAppListUserSpaceTabUi>,
    selectedUserId: Int?,
    onModeChanged: (Int) -> Unit,
    onSearchValueChange: (String) -> Unit,
    onMoreAction: (ProxyAppListMoreAction) -> Unit,
    onSelectedUserIdChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.proxy_app_list_title))
                    Text(
                        text = modes[modeIndex],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            },
            actions = {
                ProxyAppListModeMenu(
                    modes = modes,
                    selectedIndex = modeIndex,
                    onSelectedIndexChange = onModeChanged,
                )
                ProxyAppListMoreActionsMenu(
                    showSystemApps = showSystemApps,
                    onAction = onMoreAction,
                )
            },
        )
        AsteriskPinnedSearchArea(
            query = searchValue,
            onQueryChange = onSearchValueChange,
            placeholder = stringResource(R.string.proxy_app_list_search_label),
            clearContentDescription = stringResource(R.string.common_clear),
        ) {
            if (userTabs.size > 1) {
                ProxyAppListUserSpaceTabs(
                    tabs = userTabs,
                    selectedUserId = selectedUserId,
                    onSelectedUserIdChange = onSelectedUserIdChange,
                )
            }
        }
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
            .fillMaxSize()
            .padding(top = listPadding.calculateTopPadding()),
    ) {
        PullToRefreshBox(
            isRefreshing = pageState.refreshingApps,
            onRefresh = pageState::requestRefresh,
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
            ProxyAppListLoadingState(modifier = Modifier.fillMaxSize())
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
