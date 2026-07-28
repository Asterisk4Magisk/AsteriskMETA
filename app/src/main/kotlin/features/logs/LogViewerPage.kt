// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.logs

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.clipboard.setPlainText
import ui.components.AsteriskActionButton
import ui.components.AsteriskFilterChip
import ui.components.AsteriskPinnedSearchArea
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.icons.AsteriskIcons as Icons

@Composable
fun CoreLogsPage(
    padding: PaddingValues,
) {
    val services = LocalAppServices.current
    val context = LocalContext.current
    LogViewerPage(
        padding = padding,
        title = stringResource(R.string.core_logs_title),
        repository = services.coreLogRepository,
        onClear = { context.clearCoreLogFile(MihomoLogFile.Error) },
    )
}

@Composable
fun LogcatLogsPage(
    padding: PaddingValues,
) {
    val services = LocalAppServices.current
    LogViewerPage(
        padding = padding,
        title = stringResource(R.string.logcat_logs_title),
        repository = services.logcatRepository,
    )
}

@Composable
private fun LogViewerPage(
    padding: PaddingValues,
    title: String,
    repository: CoreLogRepository,
    onClear: suspend () -> Unit = {},
) {
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val services = LocalAppServices.current
    val tipNotifier = services.tipNotifier
    val logFileCreator = services.logFileCreator
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var refreshing by remember { mutableStateOf(false) }
    var logEntries by remember(repository) { mutableStateOf(repository.entries.value) }
    var query by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf(LogLevelFilter.All) }
    var paused by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val displayedLogEntries = remember(logEntries, query, levelFilter) {
        reduceLogEntries(logEntries.asReversed(), query, levelFilter)
    }
    val copiedMessage = stringResource(R.string.logs_copied_to_clipboard)
    val exportedMessage = stringResource(R.string.logs_exported)
    val exportFailedMessage = stringResource(R.string.logs_export_failed)

    LaunchedEffect(repository, paused) {
        repository.refresh()
        logEntries = repository.entries.value
        if (!paused) {
            repository.entries.collect { entries -> logEntries = entries }
        }
    }

    fun refresh() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            try {
                repository.refresh()
                logEntries = repository.entries.value
            } finally {
                refreshing = false
            }
        }
    }

    fun export() {
        val exportEntries = logEntriesForExport(logEntries.toList())
        scope.launch {
            val uri = logFileCreator(logExportFileName(title)) ?: return@launch
            runCatching {
                context.exportLogEntries(uri, exportEntries)
            }.onSuccess {
                tipNotifier.show(exportedMessage)
            }.onFailure { error ->
                tipNotifier.showError(error, exportFailedMessage)
            }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = stringResource(
                                    R.string.logs_page_subtitle,
                                    pluralStringResource(
                                        R.plurals.logs_total_count,
                                        logEntries.size,
                                        logEntries.size,
                                    ),
                                    stringResource(if (paused) R.string.logs_paused else R.string.logs_live),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { paused = !paused }) {
                            Icon(
                                imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = stringResource(
                                    if (paused) R.string.logs_resume else R.string.logs_pause,
                                ),
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.home_more_actions),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.logs_export)) },
                                    leadingIcon = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        export()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_clear)) },
                                    leadingIcon = { Icon(Icons.Rounded.Clear, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        confirmClear = true
                                    },
                                )
                            }
                        }
                    },
                )
                AsteriskPinnedSearchArea(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.common_search),
                    clearContentDescription = stringResource(R.string.common_clear),
                ) {
                    LogLevelFilterRow(
                        selected = levelFilter,
                        onSelected = { levelFilter = it },
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
        val layoutDirection = LocalLayoutDirection.current
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::refresh,
            modifier = Modifier.fillMaxSize().padding(top = listPadding.calculateTopPadding()),
        ) {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    start = listPadding.calculateStartPadding(layoutDirection),
                    end = listPadding.calculateEndPadding(layoutDirection),
                    bottom = listPadding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (displayedLogEntries.isEmpty()) {
                    item(key = "log_empty") { LogEmptyCard() }
                } else {
                    items(
                        items = displayedLogEntries,
                        key = CoreLogEntry::id,
                    ) { entry ->
                        LogEntryCard(
                            entry = entry,
                            onClick = {
                                scope.launch {
                                    clipboard.setPlainText(coreLogEntryText(entry))
                                    tipNotifier.show(copiedMessage)
                                }
                            },
                        )
                    }
                }
            }
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text(stringResource(R.string.logs_clear_title)) },
                text = { Text(stringResource(R.string.logs_clear_message)) },
                dismissButton = {
                    AsteriskActionButton(
                        text = stringResource(R.string.common_cancel),
                        icon = Icons.Rounded.Close,
                        onClick = { confirmClear = false },
                    )
                },
                confirmButton = {
                    AsteriskActionButton(
                        text = stringResource(R.string.common_clear),
                        icon = Icons.Rounded.Clear,
                        onClick = {
                            confirmClear = false
                            scope.launch {
                                onClear()
                                repository.clear()
                                logEntries = emptyList()
                            }
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun LogLevelFilterRow(
    selected: LogLevelFilter,
    onSelected: (LogLevelFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LogLevelFilter.entries.forEach { filter ->
            AsteriskFilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = stringResource(
                    when (filter) {
                        LogLevelFilter.All -> R.string.common_all
                        LogLevelFilter.Debug -> R.string.logs_level_debug
                        LogLevelFilter.Info -> R.string.logs_level_info
                        LogLevelFilter.Warning -> R.string.logs_level_warning
                        LogLevelFilter.Error -> R.string.logs_level_error
                    },
                ),
            )
        }
    }
}

private suspend fun Context.exportLogEntries(
    uri: Uri,
    entries: List<CoreLogEntry>,
) {
    withContext(Dispatchers.IO) {
        val outputStream = contentResolver.openOutputStream(uri) ?: throw IllegalStateException()
        outputStream.writer(Charsets.UTF_8).use { writer ->
            entries.forEachIndexed { index, entry ->
                if (index > 0) writer.write("\n")
                writer.write(coreLogEntryText(entry))
            }
        }
    }
}

private fun logExportFileName(title: String): String {
    val safeTitle = title
        .trim()
        .map { char -> if (char.isLetterOrDigit()) char else '-' }
        .joinToString("")
        .trim('-')
        .ifBlank { "logs" }
    return "asteriskmeta-$safeTitle-${System.currentTimeMillis()}.log"
}
