// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.mihomo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.DefaultMihomoOverrideScript
import app.DefaultMihomoOverrideScriptId
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.MihomoOverrideScriptState
import app.R
import app.collectAppState
import app.navigation.Route
import app.nextAvailableMihomoOverrideScriptId
import engine.mihomo.MihomoProfileEmptyErrorMessage
import engine.mihomo.MihomoProfileMissingErrorMessage
import engine.mihomo.MihomoProfileScriptDebugResult
import engine.mihomo.debugMihomoProfileScriptOverride
import engine.mihomo.selectedMihomoProfileOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import ui.clipboard.setPlainText
import ui.components.BackNavigationIcon
import ui.components.NavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import ui.text.formatTemplate

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
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val deletedMessage = stringResource(R.string.mihomo_override_script_deleted)

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

    Scaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(R.string.mihomo_override_scripts_title),
                subtitle = stringResource(R.string.mihomo_override_scripts_count)
                    .formatTemplate("count" to appState.mihomoOverrideScripts.size),
                isWideScreen = isWideScreen,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackNavigationIcon(onClick = { navigator.pop() })
                },
                actions = {
                    NavigationIcon(
                        onClick = { navigator.push(Route.MihomoOverrideScriptEdit(draftId = System.nanoTime())) },
                        imageVector = MiuixIcons.Add,
                        contentDescription = stringResource(R.string.mihomo_override_script_add),
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
        val layoutDirection = LocalLayoutDirection.current
        val pageListContentPadding = PaddingValues(
            start = listPadding.calculateStartPadding(layoutDirection),
            end = listPadding.calculateEndPadding(layoutDirection),
            bottom = listPadding.calculateBottomPadding(),
        )

        Box {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .padding(top = listPadding.calculateTopPadding())
                    .pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = pageListContentPadding,
            ) {
                if (appState.mihomoOverrideScripts.isEmpty()) {
                    item(key = "script_empty", contentType = "empty") {
                        MihomoOverrideScriptEmptyState()
                    }
                } else {
                    item("scripts_title") {
                        SmallTitle(text = stringResource(R.string.mihomo_override_scripts_title))
                    }
                    items(
                        items = appState.mihomoOverrideScripts,
                        key = { script -> script.id },
                    ) { script ->
                        MihomoOverrideScriptCard(
                            script = script,
                            onEdit = { navigator.push(Route.MihomoOverrideScriptEdit(script.id)) },
                            onDelete = { deleteScript(script) },
                        )
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
}

@Composable
private fun MihomoOverrideScriptEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.common_empty),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MihomoOverrideScriptCard(
    script: MihomoOverrideScriptState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = script.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = MiuixIcons.Edit,
                        contentDescription = stringResource(R.string.common_edit),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MiuixTheme.colorScheme.onSurface,
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
    val topAppBarScrollBehavior = MiuixScrollBehavior()
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
    var scriptValue by remember(targetScript?.id, isNew) {
        val content = targetScript?.content ?: DefaultMihomoOverrideScript
        mutableStateOf(
            TextFieldValue(
                text = content,
                selection = TextRange(content.length),
            ),
        )
    }
    var debugRunning by remember { mutableStateOf(false) }
    var debugResult by remember { mutableStateOf<MihomoProfileScriptDebugResult?>(null) }
    val copiedMessage = stringResource(R.string.logs_copied_to_clipboard)

    fun saveScript() {
        val cleanName = nameState.text.toString().trim()
        if (cleanName.isBlank()) {
            scope.launch { services.tipNotifier.show(nameRequiredMessage) }
            return
        }
        val canSave = isNew || targetScript != null
        if (!canSave) return
        updateAppState { state ->
            if (isNew) {
                val scriptId = state.nextAvailableMihomoOverrideScriptId()
                val savedScript = MihomoOverrideScriptState(
                    id = scriptId,
                    name = cleanName,
                    content = scriptValue.text,
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
                                content = scriptValue.text,
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
        val profile = appState.selectedMihomoProfileOrNull()
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
            try {
                debugResult = withContext(Dispatchers.Default) {
                    val rawProfile = services.mihomoProfileContentStore.read(profile)
                    debugMihomoProfileScriptOverride(
                        rawProfileContent = rawProfile,
                        scriptContent = scriptValue.text,
                    )
                }
            } finally {
                debugRunning = false
            }
        }
    }

    Scaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = title,
                isWideScreen = isWideScreen,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackNavigationIcon(onClick = { navigator.pop() })
                },
                actions = {
                    NavigationIcon(
                        imageVector = MiuixIcons.Play,
                        contentDescription = if (debugRunning) {
                            stringResource(R.string.mihomo_override_script_debug_running)
                        } else {
                            stringResource(R.string.mihomo_override_script_debug_run)
                        },
                        onClick = ::runScriptDebug,
                    )
                    NavigationIcon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = stringResource(R.string.common_save),
                        onClick = ::saveScript,
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

        if (!isNew && targetScript == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.mihomo_configuration_missing),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            key(targetScript?.id, isNew) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    TextField(
                        state = nameState,
                        label = stringResource(R.string.mihomo_override_script_name),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                    JavaScriptCodeEditor(
                        label = stringResource(R.string.mihomo_configuration_override_script_content),
                        value = scriptValue,
                        onValueChange = { scriptValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
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
private fun MihomoOverrideScriptDebugDialog(
    result: MihomoProfileScriptDebugResult?,
    onDismissRequest: () -> Unit,
    onCopy: (MihomoProfileScriptDebugResult) -> Unit,
) {
    if (result == null) return

    val scrollState = rememberScrollState()

    WindowDialog(
        show = true,
        title = stringResource(R.string.mihomo_override_script_debug_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(scrollState)
                    .padding(bottom = 12.dp),
            ) {
                result.error?.takeIf(String::isNotBlank)?.let { error ->
                    DebugSection(
                        title = stringResource(R.string.mihomo_override_script_debug_error),
                        body = error,
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
                    )
                }
                DebugSection(
                    title = stringResource(R.string.mihomo_override_script_debug_logs),
                    body = result.logs.takeIf(List<*>::isNotEmpty)
                        ?.joinToString(separator = "\n") { log -> "[${log.level}] ${log.message}" }
                        ?: stringResource(R.string.mihomo_override_script_debug_no_logs),
                )
                result.outputYaml?.takeIf(String::isNotBlank)?.let { output ->
                    DebugSection(
                        title = stringResource(R.string.mihomo_override_script_debug_output),
                        body = output,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.mihomo_override_script_debug_copy),
                    onClick = { onCopy(result) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.common_complete),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    body: String,
) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
    Text(
        text = body,
        style = MiuixTheme.textStyles.body2.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        ),
        color = MiuixTheme.colorScheme.onSurface,
    )
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
