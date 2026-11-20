// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import app.ResourceFileKind
import features.mihomo.MihomoCodeEditorState
import features.mihomo.PlainTextCodeEditor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ui.components.AsteriskCodeSearchHost
import ui.components.AsteriskSaveButton
import ui.components.AsteriskScaffold
import ui.components.AsteriskTopAppBar
import ui.components.rememberAsteriskCodeSearchState
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.rememberCodeEditorFocusState
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun ResourceCidrEditorPage(padding: PaddingValues, kind: ResourceFileKind) {
    val services = LocalAppServices.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val scope = rememberCoroutineScope()
    val editorState = remember(kind) { MihomoCodeEditorState() }
    val codeSearch = rememberAsteriskCodeSearchState()
    var original by remember(kind) { mutableStateOf<String?>(null) }
    var loading by remember(kind) { mutableStateOf(true) }
    var saving by remember(kind) { mutableStateOf(false) }
    val unavailableMessage = stringResource(R.string.settings_resource_cidr_editor_unavailable)
    val invalidLineMessage = stringResource(R.string.settings_resource_cidr_editor_invalid_line)
    val emptyMessage = stringResource(R.string.settings_resource_cidr_editor_empty)
    val changedMessage = stringResource(R.string.settings_resource_cidr_editor_changed)
    val saveFailedMessage = stringResource(R.string.settings_resource_cidr_editor_save_failed)
    val savedMessage = stringResource(R.string.settings_resource_cidr_editor_saved, kind.fileName)

    LaunchedEffect(kind) {
        try {
            val content = services.resourceFileUseCase.readCidrText(kind)
            editorState.replaceText(content, placeCursorAtEnd = false)
            original = content
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            services.tipNotifier.showError(error, fallbackMessage = unavailableMessage)
        } finally {
            loading = false
        }
    }

    val editorFocus = rememberCodeEditorFocusState(
        editorFocused = editorState.isFocused,
        clearEditorFocus = editorState::clearFocus,
        searchActive = codeSearch.active,
        backEnabled = !saving,
    )
    BackHandler(enabled = saving) {}

    fun save() {
        val loaded = original ?: return
        if (saving) return
        val content = editorState.snapshotText()
        if (content == loaded) {
            navigator.pop()
            return
        }
        saving = true
        scope.launch {
            try {
                services.resourceFileUseCase.saveCidrText(kind, original = loaded, content = content)
                services.tipNotifier.show(savedMessage)
                navigator.pop()
            } catch (error: CancellationException) {
                throw error
            } catch (error: InvalidCidrResourceException) {
                services.tipNotifier.show(invalidLineMessage.format(error.lineNumber))
            } catch (_: EmptyCidrResourceException) {
                services.tipNotifier.show(emptyMessage)
            } catch (_: CidrResourceChangedException) {
                services.tipNotifier.show(changedMessage)
            } catch (error: Exception) {
                services.tipNotifier.showError(error, fallbackMessage = saveFailedMessage)
            } finally {
                saving = false
            }
        }
    }

    AsteriskCodeSearchHost(
        state = codeSearch,
        modifier = Modifier.fillMaxSize(),
        enabled = original != null && !saving,
        editorTools = { searchButton -> searchButton() },
    ) { _, editorModifier, backdrop ->
        AsteriskScaffold(
            backdrop = backdrop,
            topBar = {
                AsteriskTopAppBar(
                    title = { Text(kind.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                when {
                                    codeSearch.active -> codeSearch.close()
                                    editorFocus.expanded -> editorFocus.exit()
                                    else -> navigator.pop()
                                }
                            },
                            enabled = !saving,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        AsteriskSaveButton(
                            onClick = ::save,
                            enabled = original != null && !saving,
                            loading = saving,
                        )
                    },
                )
            },
        ) { innerPadding ->
            val contentPadding = pageListPadding(
                pageContentPaddingWithCutout(innerPadding, padding, isWideScreen),
            )
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> CircularProgressIndicator()
                    original == null -> Text(unavailableMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Column(Modifier.fillMaxSize()) {
                        if (!editorFocus.expanded) {
                            Text(
                                stringResource(R.string.settings_resource_cidr_editor_hint),
                                modifier = Modifier.padding(bottom = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PlainTextCodeEditor(
                            state = editorState,
                            modifier = Modifier.fillMaxWidth().weight(1f).then(editorModifier),
                            readOnly = saving,
                            searchState = codeSearch,
                        )
                    }
                }
            }
        }
    }
}
