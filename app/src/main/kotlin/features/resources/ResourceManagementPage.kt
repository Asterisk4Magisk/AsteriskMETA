// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.AppState
import app.CustomResourceFileState
import app.CustomResourceFileStatus
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.ResourceFileKind
import app.ResourceFilesStatus
import app.collectAppState
import app.customResourceFileNameOrNull
import app.nextAvailableCustomResourceFileId
import app.resourceFileUpdateSource
import app.statusOf
import engine.network.toPortOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import ui.components.AsteriskExtendedFab
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.text.formatTemplate
import kotlin.coroutines.cancellation.CancellationException
import ui.icons.AsteriskIcons as Icons

@Composable
fun ResourceManagementPage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val resourceFileUseCase = services.resourceFileUseCase
    val resourceFileUpdateCoordinator = services.resourceFileUpdateCoordinator
    val updateQueueState by resourceFileUpdateCoordinator.state.collectAsState()
    val sourceOptions = settingsResourceFileSourceOptions()
    val tipNotifier = services.tipNotifier
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(ResourceFilesStatus()) }
    var resourceActionRunning by remember { mutableStateOf(false) }
    val showCustomResourceFileDialog = remember { mutableStateOf(false) }
    var editingCustomResourceFile by remember { mutableStateOf<CustomResourceFileState?>(null) }
    val customResourceFileNameState = rememberTextFieldState()
    val customResourceFileUrlState = rememberTextFieldState()
    val editCustomResourceFileNameState = rememberTextFieldState()
    val editCustomResourceFileUrlState = rememberTextFieldState()
    var showCustomSourceEditor by remember { mutableStateOf(false) }
    val sourceGeoIpUrlState = rememberTextFieldState()
    val sourceGeoSiteUrlState = rememberTextFieldState()
    val sourceMmdbUrlState = rememberTextFieldState()
    val sourceAsnUrlState = rememberTextFieldState()
    val sourceDirectCidrIpv4UrlState = rememberTextFieldState()
    val sourceDirectCidrIpv6UrlState = rememberTextFieldState()
    val updatedMessage = stringResource(R.string.settings_resource_files_updated)
    val updatedOneMessage = stringResource(R.string.settings_resource_file_updated)
    val replacedMessage = stringResource(R.string.settings_resource_files_replaced)
    val restoredMessage = stringResource(R.string.settings_resource_files_restored)
    val deletedMessage = stringResource(R.string.settings_resource_files_deleted)
    val customResourceFileNameInvalidMessage = stringResource(
        R.string.settings_resource_files_custom_name_invalid,
    )
    val customResourceFileNameDuplicateMessage = stringResource(
        R.string.settings_resource_files_custom_name_duplicate,
    )

    fun runResourceFileAction(
        action: suspend () -> ResourceFilesStatus?,
        successMessage: String?,
    ) {
        if (resourceActionRunning) return
        resourceActionRunning = true
        val result = CompletableDeferred<ResourceFilesStatus?>()
        services.appScope.launch {
            try {
                val nextStatus = action()
                if (nextStatus != null) {
                    successMessage?.let { message -> tipNotifier.show(message) }
                }
                result.complete(nextStatus)
            } catch (error: CancellationException) {
                result.completeExceptionally(error)
                throw error
            } catch (error: Throwable) {
                tipNotifier.showError(error)
                result.complete(null)
            }
        }
        scope.launch {
            try {
                result.await()?.let {
                    status = it
                }
            } finally {
                resourceActionRunning = false
            }
        }
    }

    fun showResourceFileEditorError(message: String) {
        services.appScope.launch {
            tipNotifier.show(message)
        }
    }

    fun updateResourceFile(kind: ResourceFileKind) {
        resourceFileUpdateCoordinator.enqueue(
            ResourceFileUpdateRequest.BuiltIn(
                kind = kind,
                source = appState.resourceFileUpdateSource(),
                options = appState.resourceFileUpdateOptions(),
                customResourceFiles = appState.customResourceFiles.toList(),
            ),
        )
    }

    fun updateCustomResourceFile(file: CustomResourceFileState) {
        resourceFileUpdateCoordinator.enqueue(
            ResourceFileUpdateRequest.Custom(
                file = file,
                options = appState.resourceFileUpdateOptions(),
                customResourceFiles = appState.customResourceFiles.toList(),
            ),
        )
    }

    fun customResourceFileReservedNames(editingFileId: Int? = null): Set<String> {
        return ResourceFileKind.entries.map { kind -> kind.fileName }.toSet() +
            appState.customResourceFiles
                .filterNot { file -> file.id == editingFileId }
                .map { file -> file.name }
    }

    fun validatedCustomResourceFileName(name: String, reservedNames: Set<String>): String? {
        val fileName = customResourceFileNameOrNull(name)
        if (fileName == null) {
            showResourceFileEditorError(customResourceFileNameInvalidMessage)
            return null
        }
        if (fileName in reservedNames) {
            showResourceFileEditorError(customResourceFileNameDuplicateMessage)
            return null
        }
        return fileName
    }

    fun addCustomResourceFile(name: String, url: String): Boolean {
        val fileName = validatedCustomResourceFileName(
            name = name,
            reservedNames = customResourceFileReservedNames(),
        ) ?: return false
        var addedFile: CustomResourceFileState? = null
        var nextCustomResourceFiles = appState.customResourceFiles
        updateAppState { state ->
            val updateUrl = url.trim()
            val fileId = state.nextAvailableCustomResourceFileId()
            val nextCustomFile = CustomResourceFileState(
                id = fileId,
                name = fileName,
                url = updateUrl,
            )
            addedFile = nextCustomFile
            nextCustomResourceFiles = state.customResourceFiles + nextCustomFile
            state.copy(
                customResourceFiles = nextCustomResourceFiles,
                nextCustomResourceFileId = fileId + 1,
            )
        }
        addedFile?.takeIf { file -> file.url.isBlank() }?.let { file ->
            runResourceFileAction(
                action = {
                    resourceFileUseCase.replaceCustom(
                        customFile = file,
                        customResourceFiles = nextCustomResourceFiles,
                    )
                },
                successMessage = replacedMessage.formatTemplate("name" to file.name),
            )
        }
        return true
    }

    fun editCustomResourceFile(file: CustomResourceFileState, name: String, url: String): Boolean {
        val fileName = validatedCustomResourceFileName(
            name = name,
            reservedNames = customResourceFileReservedNames(editingFileId = file.id),
        ) ?: return false
        var editedFile: CustomResourceFileState? = null
        var nextCustomResourceFiles = appState.customResourceFiles
        updateAppState { state ->
            val updateUrl = url.trim()
            val nextCustomFile = file.copy(
                name = fileName,
                url = updateUrl,
            )
            editedFile = nextCustomFile
            nextCustomResourceFiles = state.customResourceFiles.map { customFile ->
                if (customFile.id == file.id) nextCustomFile else customFile
            }
            state.copy(customResourceFiles = nextCustomResourceFiles)
        }
        editedFile?.let { nextFile ->
            runResourceFileAction(
                action = {
                    resourceFileUseCase.renameCustom(
                        previousFile = file,
                        customFile = nextFile,
                        customResourceFiles = nextCustomResourceFiles,
                    )
                },
                successMessage = null,
            )
        }
        return true
    }

    fun openCustomSourceEditor() {
        val source = appState.resourceFileUpdateSource()
        sourceGeoIpUrlState.setTextAndPlaceCursorAtEnd(appState.customResourceFileGeoIpUrl.ifBlank { source.geoIpUrl })
        sourceGeoSiteUrlState.setTextAndPlaceCursorAtEnd(
            appState.customResourceFileGeoSiteUrl.ifBlank { source.geoSiteUrl },
        )
        sourceMmdbUrlState.setTextAndPlaceCursorAtEnd(appState.customResourceFileMmdbUrl.ifBlank { source.mmdbUrl })
        sourceAsnUrlState.setTextAndPlaceCursorAtEnd(appState.customResourceFileAsnUrl.ifBlank { source.asnUrl })
        sourceDirectCidrIpv4UrlState.setTextAndPlaceCursorAtEnd(
            appState.customResourceFileDirectCidrIpv4Url.ifBlank { source.directCidrIpv4Url },
        )
        sourceDirectCidrIpv6UrlState.setTextAndPlaceCursorAtEnd(
            appState.customResourceFileDirectCidrIpv6Url.ifBlank { source.directCidrIpv6Url },
        )
        showCustomSourceEditor = true
    }

    LaunchedEffect(appState.customResourceFiles, updateQueueState.completionRevision) {
        status = resourceFileUseCase.status(appState.customResourceFiles)
    }
    LaunchedEffect(resourceFileUpdateCoordinator, updatedMessage, updatedOneMessage) {
        resourceFileUpdateCoordinator.results.collect { result ->
            when (result) {
                is ResourceFileUpdateResult.Success -> {
                    val message = when (val request = result.request) {
                        is ResourceFileUpdateRequest.All -> updatedMessage
                        is ResourceFileUpdateRequest.BuiltIn -> updatedOneMessage.formatTemplate(
                            "name" to request.kind.displayName,
                        )
                        is ResourceFileUpdateRequest.Custom -> updatedOneMessage.formatTemplate(
                            "name" to request.file.name,
                        )
                    }
                    tipNotifier.show(message)
                }
                is ResourceFileUpdateResult.Failure -> tipNotifier.showError(result.error)
                is ResourceFileUpdateResult.Cancelled -> Unit
            }
        }
    }

    val overview = reduceResourceOverview(status, appState.customResourceFiles)
    val lastUpdatedAtMillis = (
        ResourceFileKind.entries.map { kind -> status.statusOf(kind).updatedAtMillis } +
            status.customResourceFiles.map { file -> file.status.updatedAtMillis }
        ).maxOrNull() ?: 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_resource_management)) },
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
                onClick = {
                    customResourceFileNameState.clearText()
                    customResourceFileUrlState.clearText()
                    showCustomResourceFileDialog.value = true
                },
                icon = Icons.Rounded.Add,
                text = stringResource(R.string.settings_resource_files_add_custom),
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val listPadding = pageListPadding(contentPadding, bottomExtra = 88.dp)

        LazyColumn(
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "resource_overview") {
                ResourceOverviewCard(
                    overview = overview,
                    sourceOptions = sourceOptions,
                    selectedSource = appState.resourceFileSource,
                    lastUpdatedAtMillis = lastUpdatedAtMillis,
                    updating = updateQueueState.isBusy,
                    actionsEnabled = !resourceActionRunning,
                    onSourceChange = { index ->
                        if (index == ResourceFileSourceCustom) {
                            openCustomSourceEditor()
                        } else {
                            updateAppState { state -> state.copy(resourceFileSource = index) }
                        }
                    },
                    onUpdate = {
                        resourceFileUpdateCoordinator.enqueue(
                            ResourceFileUpdateRequest.All(
                                source = appState.resourceFileUpdateSource(),
                                options = appState.resourceFileUpdateOptions(),
                                customResourceFiles = appState.customResourceFiles.toList(),
                            ),
                        )
                    },
                    onCancel = resourceFileUpdateCoordinator::cancelAll,
                )
            }
            item(key = "resource_core_section") {
                ResourceSectionTitle(stringResource(R.string.settings_resource_files_core_files))
            }
            item(key = ResourceFileKind.MihomoCore.fileName) {
                val kind = ResourceFileKind.MihomoCore
                ResourceFileCard(
                    fileName = kind.displayName,
                    status = status.statusOf(kind),
                    actionsEnabled = !resourceActionRunning,
                    description = stringResource(R.string.settings_resource_files_root_only),
                    onReplace = {
                        runResourceFileAction(
                            action = { resourceFileUseCase.replace(kind, appState.customResourceFiles) },
                            successMessage = replacedMessage.formatTemplate("name" to kind.displayName),
                        )
                    },
                    onRestore = {
                        runResourceFileAction(
                            action = { resourceFileUseCase.restoreBundled(kind, appState.customResourceFiles) },
                            successMessage = restoredMessage.formatTemplate("name" to kind.displayName),
                        )
                    },
                )
            }
            item(key = "resource_rules_section") {
                ResourceSectionTitle(stringResource(R.string.settings_resource_files_files))
            }
            ResourceFileKind.entries.filterNot { it == ResourceFileKind.MihomoCore }.forEach { kind ->
                item(key = kind.fileName) {
                    ResourceFileCard(
                        fileName = kind.displayName,
                        status = status.statusOf(kind),
                        updateState = updateQueueState.displayStateOf(
                            ResourceFileUpdateTarget.BuiltIn(kind),
                        ),
                        actionsEnabled = !resourceActionRunning,
                        onUpdate = { updateResourceFile(kind) },
                        onReplace = {
                            runResourceFileAction(
                                action = { resourceFileUseCase.replace(kind, appState.customResourceFiles) },
                                successMessage = replacedMessage.formatTemplate("name" to kind.displayName),
                            )
                        },
                        onRestore = {
                            runResourceFileAction(
                                action = { resourceFileUseCase.restoreBundled(kind, appState.customResourceFiles) },
                                successMessage = restoredMessage.formatTemplate("name" to kind.displayName),
                            )
                        },
                    )
                }
            }
            if (appState.customResourceFiles.isNotEmpty()) {
                item(key = "resource_custom_section") {
                    ResourceSectionTitle(stringResource(R.string.settings_resource_files_custom_section))
                }
            }
            appState.customResourceFiles.forEach { customFile ->
                item(key = "custom_resource_file_${customFile.id}") {
                    CustomResourceFileCard(
                        fileStatus = status.statusOf(customFile),
                        updateState = updateQueueState.displayStateOf(
                            ResourceFileUpdateTarget.Custom(customFile.id),
                        ),
                        actionsEnabled = !resourceActionRunning,
                        onUpdate = ::updateCustomResourceFile,
                        onReplace = { file ->
                            runResourceFileAction(
                                action = {
                                    resourceFileUseCase.replaceCustom(file, appState.customResourceFiles)
                                },
                                successMessage = replacedMessage.formatTemplate("name" to file.name),
                            )
                        },
                        onEdit = { file ->
                            editCustomResourceFileNameState.setTextAndPlaceCursorAtEnd(file.name)
                            editCustomResourceFileUrlState.setTextAndPlaceCursorAtEnd(file.url)
                            editingCustomResourceFile = file
                        },
                        onDelete = { file ->
                            runResourceFileAction(
                                action = {
                                    var remaining = emptyList<CustomResourceFileState>()
                                    updateAppState { state ->
                                        remaining = state.customResourceFiles.filterNot { it.id == file.id }
                                        state.copy(customResourceFiles = remaining)
                                    }
                                    resourceFileUseCase.deleteCustom(file, remaining)
                                },
                                successMessage = deletedMessage.formatTemplate("name" to file.name),
                            )
                        },
                    )
                }
            }
        }
        CustomResourceFileEditorSheet(
            show = showCustomResourceFileDialog.value,
            nameState = customResourceFileNameState,
            urlState = customResourceFileUrlState,
            reservedNames = customResourceFileReservedNames(),
            onDismissRequest = { showCustomResourceFileDialog.value = false },
            onSave = ::addCustomResourceFile,
        )
        CustomResourceFileEditorSheet(
            show = editingCustomResourceFile != null,
            nameState = editCustomResourceFileNameState,
            urlState = editCustomResourceFileUrlState,
            reservedNames = customResourceFileReservedNames(editingCustomResourceFile?.id),
            onDismissRequest = { editingCustomResourceFile = null },
            onSave = { name, url ->
                editingCustomResourceFile?.let { file -> editCustomResourceFile(file, name, url) } ?: false
            },
        )
        CustomResourceSourceEditorSheet(
            show = showCustomSourceEditor,
            geoIpUrlState = sourceGeoIpUrlState,
            geoSiteUrlState = sourceGeoSiteUrlState,
            mmdbUrlState = sourceMmdbUrlState,
            asnUrlState = sourceAsnUrlState,
            directCidrIpv4UrlState = sourceDirectCidrIpv4UrlState,
            directCidrIpv6UrlState = sourceDirectCidrIpv6UrlState,
            onDismissRequest = { showCustomSourceEditor = false },
            onSave = {
                updateAppState { state ->
                    state.copy(
                        resourceFileSource = ResourceFileSourceCustom,
                        customResourceFileGeoIpUrl = sourceGeoIpUrlState.text.toString().trim(),
                        customResourceFileGeoSiteUrl = sourceGeoSiteUrlState.text.toString().trim(),
                        customResourceFileMmdbUrl = sourceMmdbUrlState.text.toString().trim(),
                        customResourceFileAsnUrl = sourceAsnUrlState.text.toString().trim(),
                        customResourceFileDirectCidrIpv4Url = sourceDirectCidrIpv4UrlState.text.toString().trim(),
                        customResourceFileDirectCidrIpv6Url = sourceDirectCidrIpv6UrlState.text.toString().trim(),
                    )
                }
                showCustomSourceEditor = false
            },
        )
    }
}

@Composable
private fun ResourceSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
    )
}

private fun AppState.resourceFileUpdateOptions(): ResourceFileUpdateOptions {
    return ResourceFileUpdateOptions(
        useRunningProxy = proxyRunning,
        fallbackProxyPort = localProxyPort.toPortOrNull(),
        fallbackProxyUsername = localProxyUsername,
        fallbackProxyPassword = localProxyPassword,
    )
}

private fun ResourceFilesStatus.statusOf(customFile: CustomResourceFileState): CustomResourceFileStatus {
    return customResourceFiles.firstOrNull { fileStatus -> fileStatus.file.id == customFile.id }
        ?: CustomResourceFileStatus(file = customFile)
}
