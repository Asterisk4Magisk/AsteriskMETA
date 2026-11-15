// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import ui.layout.rememberCodeEditorFocusState
import androidx.compose.foundation.layout.BoxWithConstraints
import ui.components.AsteriskCancelButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import ui.components.AsteriskScaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import ui.components.AsteriskTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.DefaultMihomoOverrideScript
import app.DefaultMihomoOverrideScriptId
import app.DefaultMihomoProfileId
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.MihomoOverrideScriptState
import app.MihomoProfileState
import app.R
import app.collectAppState
import app.navigation.Route
import app.nextAvailableMihomoOverrideScriptId
import engine.mihomo.MihomoProfileEmptyErrorMessage
import engine.mihomo.MihomoProfileMissingErrorMessage
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.MihomoProfileScriptDebugResult
import engine.mihomo.debugMihomoProfileScriptOverride
import engine.mihomo.effectiveRuleOverride
import features.settings.SettingsDropdownRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.clipboard.setPlainText
import ui.components.AsteriskCodeSearchHost
import ui.components.rememberAsteriskCodeSearchState
import ui.components.AsteriskCodePreviewDialog
import ui.components.AsteriskCodePreviewTabs
import ui.components.AsteriskCodeActionButton
import ui.components.AsteriskActionButton
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskExtendedFab
import ui.components.AsteriskListRow
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.text.formatTemplate
import ui.theme.AsteriskMotion
import ui.theme.ExpressiveShapeRole
import ui.icons.AsteriskIcons as Icons
import ui.theme.AsteriskShapeTokens

@Composable
fun MihomoOverrideScriptListPage(
    padding: PaddingValues,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.mihomo_override_script_deleted)
    val duplicateNameTemplate = stringResource(R.string.mihomo_override_script_duplicate_name)
    var pendingDeletion by remember { mutableStateOf<MihomoOverrideScriptState?>(null) }

    fun deleteScript(script: MihomoOverrideScriptState) {
        updateAppState { state ->
            state.copy(
                mihomoOverrideScripts = state.mihomoOverrideScripts.filterNot { item -> item.id == script.id },
                mihomoProfiles = state.mihomoProfiles.map { profile ->
                    if (profile.overrideScriptId == script.id) {
                        profile.copy(overrideScriptId = DefaultMihomoOverrideScriptId)
                    } else {
                        profile
                    }
                },
            )
        }
        scope.launch {
            services.tipNotifier.show(deletedMessage)
        }
    }

    fun duplicateScript(script: MihomoOverrideScriptState) {
        updateAppState { state ->
            val nextId = state.nextAvailableMihomoOverrideScriptId()
            state.copy(
                mihomoOverrideScripts = state.mihomoOverrideScripts + script.copy(
                    id = nextId,
                    name = duplicateNameTemplate.formatTemplate("name" to script.name),
                ),
                nextMihomoOverrideScriptId = nextId + 1,
            )
        }
    }

    AsteriskScaffold(
        topBar = {
            AsteriskTopAppBar(
                title = { Text(stringResource(R.string.mihomo_override_scripts_title)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AsteriskExtendedFab(
                onClick = { navigator.push(Route.MihomoOverrideScriptEdit(draftId = System.nanoTime())) },
                icon = Icons.Rounded.Add,
                text = stringResource(R.string.mihomo_override_script_add),
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (appState.mihomoOverrideScripts.isEmpty()) {
                item(key = "script_empty", contentType = "empty") {
                    MihomoOverrideScriptEmptyState()
                }
            } else {
                items(
                    items = appState.mihomoOverrideScripts,
                    key = { script -> script.id },
                ) { script ->
                    MihomoOverrideScriptCard(
                        script = script,
                        referenceCount = appState.mihomoProfiles.count { profile ->
                            profile.overrideScriptId == script.id
                        },
                        onEdit = { navigator.push(Route.MihomoOverrideScriptEdit(script.id)) },
                        onDuplicate = { duplicateScript(script) },
                        onDelete = { pendingDeletion = script },
                    )
                }
            }
        }
        pendingDeletion?.let { script ->
            val referenceCount = appState.mihomoProfiles.count { profile ->
                profile.overrideScriptId == script.id
            }
            AlertDialog(
                onDismissRequest = { pendingDeletion = null },
                title = { Text(stringResource(R.string.mihomo_override_script_delete_title)) },
                text = {
                    Text(
                        pluralStringResource(
                            R.plurals.mihomo_override_script_delete_message,
                            referenceCount,
                            script.name,
                            referenceCount,
                        ),
                    )
                },
                dismissButton = {
                    AsteriskCancelButton(
                        onClick = { pendingDeletion = null },
                    )
                },
                confirmButton = {
                    AsteriskActionButton(
                        text = stringResource(R.string.common_delete),
                        icon = Icons.Rounded.Delete,
                        onClick = {
                            pendingDeletion = null
                            deleteScript(script)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun MihomoOverrideScriptEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                imageVector = Icons.Rounded.Code,
                contentDescription = null,
                modifier = Modifier.padding(18.dp).size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.common_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.mihomo_override_script_empty_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MihomoOverrideScriptCard(
    script: MihomoOverrideScriptState,
    referenceCount: Int,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val lineCount = remember(script.content) { script.content.count { it == '\n' } + 1 }
    AsteriskExpressiveCard(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp),
        role = ExpressiveShapeRole.ContentCard,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Code,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = script.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(
                        pluralStringResource(
                            R.plurals.mihomo_override_script_profile_count,
                            referenceCount,
                            referenceCount,
                        ),
                        pluralStringResource(
                            R.plurals.mihomo_override_script_line_count,
                            lineCount,
                            lineCount,
                        ),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.mihomo_profile_actions),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_edit)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_copy)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun MihomoOverrideScriptEditPage(
    padding: PaddingValues,
    scriptId: Int,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = scriptId <= DefaultMihomoOverrideScriptId
    val targetScript = remember(appState.mihomoOverrideScripts, scriptId) {
        appState.mihomoOverrideScripts.firstOrNull { script -> script.id == scriptId }
    }
    val title = stringResource(
        if (isNew) {
            R.string.mihomo_override_script_add
        } else {
            R.string.mihomo_override_script_edit
        },
    )
    val nameRequiredMessage = stringResource(R.string.mihomo_override_script_name_required)
    val nameState = rememberTextFieldState(initialText = targetScript?.name.orEmpty())
    val scriptEditorState = remember(targetScript?.id, isNew) {
        MihomoCodeEditorState(targetScript?.content ?: DefaultMihomoOverrideScript)
    }
    var debugRunning by remember { mutableStateOf(false) }
    var debugResult by remember { mutableStateOf<MihomoProfileScriptDebugResult?>(null) }
    var debugProfileId by remember {
        mutableIntStateOf(
            appState.mihomoProfiles.firstOrNull { profile ->
                profile.id == appState.selectedMihomoProfileId
            }?.id ?: appState.mihomoProfiles.firstOrNull()?.id ?: DefaultMihomoProfileId,
        )
    }
    val copiedMessage = stringResource(R.string.logs_copied_to_clipboard)

    fun saveScript() {
        val cleanName = nameState.text.toString().trim()
        if (cleanName.isBlank()) {
            scope.launch { services.tipNotifier.show(nameRequiredMessage) }
            return
        }
        val canSave = isNew || targetScript != null
        if (!canSave) return
        val scriptContent = scriptEditorState.snapshotText()
        updateAppState { state ->
            if (isNew) {
                val scriptId = state.nextAvailableMihomoOverrideScriptId()
                val savedScript = MihomoOverrideScriptState(
                    id = scriptId,
                    name = cleanName,
                    content = scriptContent,
                )
                state.copy(
                    mihomoOverrideScripts = state.mihomoOverrideScripts + savedScript,
                    nextMihomoOverrideScriptId = scriptId + 1,
                )
            } else {
                state.copy(
                    mihomoOverrideScripts = state.mihomoOverrideScripts.map { script ->
                        if (script.id == scriptId) {
                            script.copy(
                                name = cleanName,
                                content = scriptContent,
                            )
                        } else {
                            script
                        }
                    },
                )
            }
        }
        navigator.pop()
    }
    val rawDebugProfileMessage = stringResource(R.string.mihomo_override_script_debug_raw_profile)
    fun runScriptDebug() {
        if (debugRunning) return
        val profile = appState.mihomoProfiles.firstOrNull { item -> item.id == debugProfileId }
        if (profile == null) {
            debugResult = MihomoProfileScriptDebugResult(error = MihomoProfileMissingErrorMessage)
            return
        }
        if (!profile.hasContent) {
            debugResult = MihomoProfileScriptDebugResult(error = MihomoProfileEmptyErrorMessage)
            return
        }
        if (profile.disableOverrides) {
            // A raw profile never runs a script, so a result here would describe nothing real.
            debugResult = MihomoProfileScriptDebugResult(error = rawDebugProfileMessage)
            return
        }
        scope.launch {
            debugRunning = true
            debugResult = null
            val scriptContent = scriptEditorState.snapshotText()
            val debugAppState = appState
            try {
                debugResult = withContext(Dispatchers.Default) {
                    val rawProfile = services.mihomoProfileContentStore.read(profile)
                    debugMihomoProfileScriptOverride(
                        rawProfileContent = rawProfile,
                        scriptContent = scriptContent,
                        // The script runs after the profile's saved rule override, as in a real compile.
                        ruleOverride = profile.effectiveRuleOverride(),
                        // The output shows what the core would receive in the current run mode.
                        managedStage = MihomoProfileFactory.managedStage(context.applicationContext, debugAppState),
                    )
                }
            } finally {
                debugRunning = false
            }
        }
    }

    val codeSearch = rememberAsteriskCodeSearchState()
    val editorFocus = rememberCodeEditorFocusState(
        editorFocused = scriptEditorState.isFocused,
        clearEditorFocus = codeSearch::clearEditorFocus,
        searchActive = codeSearch.active,
    )
    AsteriskCodeSearchHost(
        state = codeSearch,
        modifier = Modifier.fillMaxSize(),
        enabled = isNew || targetScript != null,
        editorTools = { searchButton ->
            searchButton()
            MihomoCodeFormatButton(scriptEditorState, MihomoCodeLanguage.JavaScript)
        },
    ) { _, editorModifier, backdrop ->
        AsteriskScaffold(
            backdrop = backdrop,
            topBar = {
                AsteriskTopAppBar(
                    title = { Text(title, maxLines = 1) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                when {
                                    codeSearch.active -> codeSearch.close()
                                    editorFocus.expanded -> editorFocus.exit()
                                    else -> navigator.pop()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = ::runScriptDebug,
                            enabled = !debugRunning,
                        ) {
                            if (debugRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Rounded.BugReport, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                stringResource(
                                    if (debugRunning) {
                                        R.string.mihomo_override_script_debug_running
                                    } else {
                                        R.string.mihomo_override_script_debug_run
                                    },
                                ),
                            )
                        }
                        TextButton(
                            onClick = ::saveScript,
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.common_save))
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
            ) {
                if (!isNew && targetScript == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.mihomo_profile_missing),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    key(targetScript?.id, isNew) {
                        AnimatedVisibility(
                            visible = !editorFocus.expanded,
                            enter = AsteriskMotion.contentEnter(),
                            exit = AsteriskMotion.contentExit(),
                        ) {
                            Column {
                                OutlinedTextField(
                                    shape = AsteriskShapeTokens.InnerContainer,
                                    state = nameState,
                                    label = { Text(stringResource(R.string.mihomo_override_script_name)) },
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                )
                                DebugProfileSelector(
                                    profiles = appState.mihomoProfiles,
                                    selectedProfileId = debugProfileId,
                                    onSelectedProfileIdChange = { debugProfileId = it },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                )
                            }
                        }
                        JavaScriptCodeEditor(
                            state = scriptEditorState,
                            showFormatButton = false,
                            searchState = codeSearch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .imePadding()
                                .then(editorModifier),
                        )
                    }
                }
            }
            MihomoOverrideScriptDebugDialog(
                result = debugResult,
                onDismissRequest = { debugResult = null },
                onCopy = { report ->
                    scope.launch {
                        clipboard.setPlainText(report)
                        services.tipNotifier.show(copiedMessage)
                    }
                },
            )
        }
    }
}

@Composable
private fun DebugProfileSelector(
    profiles: List<MihomoProfileState>,
    selectedProfileId: Int,
    onSelectedProfileIdChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (profiles.isEmpty()) {
        AsteriskListRow(
            title = stringResource(R.string.mihomo_override_script_debug_profile),
            horizontalPadding = 0.dp,
            summary = stringResource(R.string.mihomo_override_script_debug_profile_none),
            leadingIcon = Icons.Rounded.Description,
            enabled = false,
            modifier = modifier,
        )
        return
    }
    val selectedIndex = profiles.indexOfFirst { profile -> profile.id == selectedProfileId }.coerceAtLeast(0)
    val rawProfileLabel = stringResource(R.string.mihomo_profile_raw_chip)
    SettingsDropdownRow(
        title = stringResource(R.string.mihomo_override_script_debug_profile),
        horizontalPadding = 0.dp,
        icon = Icons.Rounded.Description,
        items = profiles.map { profile ->
            if (profile.disableOverrides) "${profile.name} · $rawProfileLabel" else profile.name
        },
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index -> onSelectedProfileIdChange(profiles[index].id) },
        modifier = modifier,
    )
}

@Composable
private fun MihomoOverrideScriptDebugDialog(
    result: MihomoProfileScriptDebugResult?,
    onDismissRequest: () -> Unit,
    onCopy: (String) -> Unit,
) {
    if (result == null) return
    var showOutput by remember(result) { mutableStateOf(false) }
    val consoleText = result.logs.takeIf(List<*>::isNotEmpty)
        ?.joinToString(separator = "\n") { log -> "[${log.level}] ${log.message}" }
        ?: stringResource(R.string.mihomo_override_script_debug_no_logs)
    val outputText = result.outputYaml?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.mihomo_override_script_debug_no_output)
    val outputEditorState = remember(result, outputText) {
        MihomoCodeEditorState().also { state ->
            state.replaceText(outputText, placeCursorAtEnd = false)
        }
    }
    val outputFocus = rememberCodeEditorFocusState(
        editorFocused = outputEditorState.isFocused,
        clearEditorFocus = outputEditorState::clearFocus,
    )
    val outputEffectsMotion = AsteriskMotion.fastEffects<Float>()
    val hasDiagnostics = !result.error.isNullOrBlank() || result.summary != null

    AsteriskCodePreviewDialog(
        title = stringResource(R.string.mihomo_override_script_debug_title),
        onDismissRequest = onDismissRequest,
        searchAvailable = showOutput,
        keepSearchButtonVisible = true,
        focusState = outputFocus,
        actions = {
            AsteriskCodeActionButton(
                icon = Icons.Rounded.ContentCopy,
                label = stringResource(R.string.mihomo_override_script_debug_copy),
                onClick = {
                    onCopy(
                        result.toClipboardReport(
                            outputText = outputEditorState.snapshotText()
                                .takeUnless { result.outputYaml.isNullOrBlank() && it == outputText }.orEmpty(),
                        ),
                    )
                },
            )
        },
        topBarContent = {
            AsteriskCodePreviewTabs(
                showOutput = showOutput,
                onShowOutputChange = { showOutput = it },
                consoleLabel = stringResource(R.string.mihomo_override_script_debug_logs),
                outputLabel = stringResource(R.string.mihomo_override_script_debug_output),
            )
        },
    ) { searchState, editorModifier ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val diagnosticsMaxHeight = maxHeight * 0.35f
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = !outputFocus.expanded,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(max = diagnosticsMaxHeight)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        result.error?.takeIf(String::isNotBlank)?.let { error ->
                            DebugSection(
                                title = stringResource(R.string.mihomo_override_script_debug_error),
                                body = error,
                                error = true,
                            )
                        }
                        result.summary?.let { summary ->
                            val title = stringResource(R.string.mihomo_override_script_debug_summary_title)
                            val details = stringResource(
                                R.string.mihomo_override_script_debug_summary,
                                summary.inputProxyCount,
                                summary.outputProxyCount,
                                summary.inputProxyGroupCount,
                                summary.outputProxyGroupCount,
                                summary.inputRuleCount,
                                summary.outputRuleCount,
                            )
                            Text(
                                text = "$title · $details",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = if (!result.error.isNullOrBlank()) 8.dp else 0.dp),
                            )
                        }
                    }
                }
                AnimatedContent(
                    targetState = showOutput,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = if (hasDiagnostics && !outputFocus.expanded) 8.dp else 0.dp),
                    transitionSpec = AsteriskMotion.fadeThrough(outputEffectsMotion),
                    contentAlignment = Alignment.TopStart,
                    label = "script-debug-output",
                ) { showingOutput ->
                    if (showingOutput) {
                        YamlCodeEditor(
                            state = outputEditorState,
                            showFormatButton = false,
                            searchState = searchState,
                            modifier = Modifier.fillMaxSize().then(editorModifier),
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            SelectionContainer {
                                Text(
                                    text = consoleText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (error) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (error) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun MihomoProfileScriptDebugResult.toClipboardReport(
    outputText: String = outputYaml.orEmpty(),
): String {
    return buildString {
        appendLine(if (success) "Script debug: success" else "Script debug: failed")
        error?.takeIf(String::isNotBlank)?.let { error ->
            appendLine()
            appendLine("Error:")
            appendLine(error)
        }
        summary?.let { summary ->
            appendLine()
            appendLine("Summary:")
            appendLine("proxies: ${summary.inputProxyCount} -> ${summary.outputProxyCount}")
            appendLine("proxy-groups: ${summary.inputProxyGroupCount} -> ${summary.outputProxyGroupCount}")
            appendLine("rules: ${summary.inputRuleCount} -> ${summary.outputRuleCount}")
        }
        appendLine()
        appendLine("Console:")
        if (logs.isEmpty()) {
            appendLine("(empty)")
        } else {
            logs.forEach { log -> appendLine("[${log.level}] ${log.message}") }
        }
        outputText.takeIf(String::isNotBlank)?.let { output ->
            appendLine()
            appendLine("Final YAML:")
            appendLine(output)
        }
    }
}
