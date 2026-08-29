// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.mihomo

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
import engine.mihomo.MihomoProfileScriptDebugResult
import engine.mihomo.debugMihomoProfileScriptOverride
import features.settings.SettingsDropdownRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.clipboard.setPlainText
import ui.components.AsteriskActionButton
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskExtendedFab
import ui.components.AsteriskFilterChip
import ui.components.AsteriskListRow
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.text.formatTemplate
import ui.theme.AsteriskMotion
import ui.theme.ExpressiveShapeRole
import ui.icons.AsteriskIcons as Icons

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

    Scaffold(
        topBar = {
            TopAppBar(
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
        val layoutDirection = LocalLayoutDirection.current
        val pageListContentPadding = PaddingValues(
            start = listPadding.calculateStartPadding(layoutDirection),
            end = listPadding.calculateEndPadding(layoutDirection),
            bottom = listPadding.calculateBottomPadding(),
        )

        LazyColumn(
            modifier = Modifier.padding(top = listPadding.calculateTopPadding()),
            contentPadding = pageListContentPadding,
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
                    AsteriskActionButton(
                        text = stringResource(R.string.common_cancel),
                        icon = Icons.Rounded.Close,
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
                            R.plurals.mihomo_override_script_configuration_count,
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
                        contentDescription = stringResource(R.string.mihomo_configuration_actions),
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
        scope.launch {
            debugRunning = true
            debugResult = null
            val scriptContent = scriptEditorState.snapshotText()
            try {
                debugResult = withContext(Dispatchers.Default) {
                    val rawProfile = services.mihomoProfileContentStore.read(profile)
                    debugMihomoProfileScriptOverride(
                        rawProfileContent = rawProfile,
                        scriptContent = scriptContent,
                    )
                }
            } finally {
                debugRunning = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (!isNew && targetScript == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.mihomo_configuration_missing),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                key(targetScript?.id, isNew) {
                    OutlinedTextField(
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
                    JavaScriptCodeEditor(
                        label = stringResource(R.string.mihomo_configuration_override_script_content),
                        state = scriptEditorState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .imePadding(),
                    )
                }
            }
        }
        MihomoOverrideScriptDebugDialog(
            result = debugResult,
            onDismissRequest = { debugResult = null },
            onCopy = { result ->
                scope.launch {
                    clipboard.setPlainText(result.toClipboardReport())
                    services.tipNotifier.show(copiedMessage)
                }
            },
        )
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
            summary = stringResource(R.string.mihomo_override_script_debug_profile_none),
            leadingIcon = Icons.Rounded.Description,
            enabled = false,
            modifier = modifier,
        )
        return
    }
    val selectedIndex = profiles.indexOfFirst { profile -> profile.id == selectedProfileId }.coerceAtLeast(0)
    SettingsDropdownRow(
        title = stringResource(R.string.mihomo_override_script_debug_profile),
        icon = Icons.Rounded.Description,
        items = profiles.map(MihomoProfileState::name),
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index -> onSelectedProfileIdChange(profiles[index].id) },
        modifier = modifier,
    )
}

@Composable
private fun MihomoOverrideScriptDebugDialog(
    result: MihomoProfileScriptDebugResult?,
    onDismissRequest: () -> Unit,
    onCopy: (MihomoProfileScriptDebugResult) -> Unit,
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
    val outputSizeMotion = AsteriskMotion.spatial<IntSize>()
    val outputEffectsMotion = AsteriskMotion.fastEffects<Float>()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.mihomo_override_script_debug_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (result.success) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ) {
                    Text(
                        text = stringResource(
                            if (result.success) {
                                R.string.mihomo_override_script_debug_success
                            } else {
                                R.string.mihomo_override_script_debug_failed
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        },
        text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                result.error?.takeIf(String::isNotBlank)?.let { error ->
                    DebugSection(
                        title = stringResource(R.string.mihomo_override_script_debug_error),
                        body = error,
                        error = true,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                result.summary?.let { summary ->
                    DebugSection(
                        title = stringResource(R.string.mihomo_override_script_debug_summary_title),
                        body = stringResource(
                            R.string.mihomo_override_script_debug_summary,
                            summary.inputProxyCount,
                            summary.outputProxyCount,
                            summary.inputProxyGroupCount,
                            summary.outputProxyGroupCount,
                            summary.inputRuleCount,
                            summary.outputRuleCount,
                        ),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsteriskFilterChip(
                    selected = !showOutput,
                    onClick = { showOutput = false },
                    label = stringResource(R.string.mihomo_override_script_debug_logs),
                    leadingIcon = { Icon(Icons.Rounded.Code, contentDescription = null) },
                )
                AsteriskFilterChip(
                    selected = showOutput,
                    onClick = { showOutput = true },
                    label = stringResource(R.string.mihomo_override_script_debug_output),
                    leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                )
            }
            AnimatedContent(
                targetState = showOutput,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                transitionSpec = AsteriskMotion.fadeThrough(
                    effectsSpec = outputEffectsMotion,
                    sizeSpec = outputSizeMotion,
                ),
                contentAlignment = Alignment.TopStart,
                label = "script-debug-output",
            ) { showingOutput ->
                if (showingOutput) {
                    YamlCodeEditor(
                        label = stringResource(R.string.mihomo_override_script_debug_output),
                        state = outputEditorState,
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 320.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        SelectionContainer {
                            Text(
                                text = consoleText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
        },
        confirmButton = {
            AsteriskActionButton(
                text = stringResource(R.string.common_complete),
                icon = Icons.Rounded.Check,
                onClick = onDismissRequest,
            )
        },
        dismissButton = {
            AsteriskActionButton(
                text = stringResource(R.string.mihomo_override_script_debug_copy),
                icon = Icons.Rounded.ContentCopy,
                onClick = { onCopy(result) },
            )
        },
    )
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

private fun MihomoProfileScriptDebugResult.toClipboardReport(): String {
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
        outputYaml?.takeIf(String::isNotBlank)?.let { output ->
            appendLine()
            appendLine("Output YAML:")
            appendLine(output)
        }
    }
}
