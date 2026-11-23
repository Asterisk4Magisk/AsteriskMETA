// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.LocalAppServices
import app.R
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.clipboard.setPlainText
import ui.layout.LocalAdaptiveWindow
import ui.layout.avoidFold
import ui.layout.pageListPadding
import ui.layout.rememberPageGutter
import ui.layout.CodeEditorFocusState
import ui.theme.AsteriskMotion
import ui.icons.AsteriskIcons as Icons

/** Full-screen preview chrome; loading, diagnostics and the document language stay with the caller. */
@Composable
internal fun AsteriskCodePreviewDialog(
    title: String,
    onDismissRequest: () -> Unit,
    searchAvailable: Boolean = true,
    keepSearchButtonVisible: Boolean = false,
    focusState: CodeEditorFocusState? = null,
    embedded: Boolean = false,
    showBackButton: Boolean = true,
    contentHorizontalPadding: Dp? = null,
    actions: @Composable RowScope.() -> Unit = {},
    topBarContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable (searchState: AsteriskCodeSearchState, editorModifier: Modifier) -> Unit,
) {
    val searchState = rememberAsteriskCodeSearchState()
    val onBack = {
        when {
            searchState.active -> searchState.close()
            focusState?.expanded == true -> focusState.exit()
            else -> onDismissRequest()
        }
    }
    val window = LocalAdaptiveWindow.current
    val preview: @Composable () -> Unit = {
        AsteriskCodeSearchHost(
            state = searchState,
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .then(if (embedded) Modifier else Modifier.avoidFold(window))
                .clipToBounds(),
            enabled = searchAvailable,
            showSearchButton = keepSearchButtonVisible || searchAvailable,
        ) { searchButton, editorModifier, backdrop ->
            AsteriskScaffold(
                backdrop = backdrop,
                contentWindowInsets = if (embedded) WindowInsets(0, 0, 0, 0)
                else WindowInsets.systemBars.union(WindowInsets.displayCutout),
                topBar = {
                    Column {
                        AsteriskTopAppBar(
                            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            navigationIcon = {
                                if (showBackButton || searchState.active) {
                                    IconButton(onClick = onBack) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                                    }
                                }
                            },
                            actions = { searchButton(); actions() },
                        )
                        AnimatedVisibility(
                            visible = focusState?.expanded != true,
                            enter = AsteriskMotion.contentEnter(),
                            exit = AsteriskMotion.contentExit(),
                        ) {
                            Column(content = topBarContent)
                        }
                    }
                },
            ) { innerPadding ->
                val contentPadding = pageListPadding(
                    innerPadding,
                    horizontalExtra = contentHorizontalPadding ?: rememberPageGutter(),
                )
                Box(
                    Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(innerPadding).imePadding(),
                ) {
                    content(searchState, editorModifier)
                }
            }
        }
    }
    if (embedded) {
        BackHandler(enabled = searchState.active, onBack = onBack)
        preview()
    } else {
        Dialog(
            onDismissRequest = onBack,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
            ),
            content = preview,
        )
    }
}

@Composable
internal fun AsteriskCodeCopyButton(text: () -> String) {
    val clipboard = LocalClipboard.current
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.common_copied)
    IconButton(onClick = {
        val snapshot = text()
        scope.launch {
            clipboard.setPlainText(snapshot)
            services.tipNotifier.show(copied)
        }
    }) {
        Icon(Icons.Rounded.ContentCopy, stringResource(R.string.common_copy))
    }
}

/** Prepare large preview documents away from the UI thread before binding them to Sora. */
@Composable
internal fun rememberCodePreviewDocument(text: String): Content? {
    val document by produceState<Content?>(null, text) {
        value = null
        value = withContext(Dispatchers.Default) { Content(text) }
    }
    return document
}

@Composable
internal fun AsteriskCodePreviewLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
