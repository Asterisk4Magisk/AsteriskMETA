// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.ResourceFileKind
import app.ResourceFilesStatus
import app.collectAppState
import app.resourceFileUpdateSource
import app.statusOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import ui.components.AsteriskScaffold
import ui.components.AsteriskTopAppBar
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
    var showCustomSourceEditor by remember { mutableStateOf(false) }
    var showResourceAutoUpdateSheet by remember { mutableStateOf(false) }
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

    fun updateResourceFile(kind: ResourceFileKind) {
        resourceFileUpdateCoordinator.enqueue(
            ResourceFileUpdateRequest.BuiltIn(
                kind = kind,
                source = appState.resourceFileUpdateSource(),
                options = appState.resourceFileUpdateOptions(),
            ),
        )
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

    LaunchedEffect(updateQueueState.completionRevision) {
        status = resourceFileUseCase.status()
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
                    }
                    tipNotifier.show(message)
                }
                is ResourceFileUpdateResult.Failure -> tipNotifier.showError(result.error)
                is ResourceFileUpdateResult.Cancelled -> Unit
            }
        }
    }

    val overview = reduceResourceOverview(status)
    val lastUpdatedAtMillis = (
        ResourceFileKind.entries.map { kind -> status.statusOf(kind).updatedAtMillis }
        ).maxOrNull() ?: 0L

    AsteriskScaffold(
        topBar = {
            AsteriskTopAppBar(
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
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val listPadding = pageListPadding(contentPadding)

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
                            ),
                        )
                    },
                    onCancel = resourceFileUpdateCoordinator::cancelAll,
                    onSettings = { showResourceAutoUpdateSheet = true },
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
                            action = {
                                resourceFileUseCase.replace(kind)?.also {
                                    services.refreshMihomoCoreBoot(appState)
                                }
                            },
                            successMessage = replacedMessage.formatTemplate("name" to kind.displayName),
                        )
                    },
                    onRestore = {
                        runResourceFileAction(
                            action = {
                                services.restoreSharedMihomoCore(
                                    state = appState,
                                    onRootStopped = { updateAppState { it.copy(proxyRunning = false) } },
                                )
                            },
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
                                action = { resourceFileUseCase.replace(kind) },
                                successMessage = replacedMessage.formatTemplate("name" to kind.displayName),
                            )
                        },
                        onRestore = {
                            runResourceFileAction(
                                action = { resourceFileUseCase.restoreBundled(kind) },
                                successMessage = restoredMessage.formatTemplate("name" to kind.displayName),
                            )
                        },
                    )
                }
            }

        }
        ResourceAutoUpdateSheet(
            show = showResourceAutoUpdateSheet,
            enabled = appState.enableResourceAutoUpdate,
            interval = appState.resourceAutoUpdateInterval,
            onDismissRequest = { showResourceAutoUpdateSheet = false },
            onSave = { enabled, interval ->
                updateAppState { state ->
                    state.copy(enableResourceAutoUpdate = enabled, resourceAutoUpdateInterval = interval)
                }
                showResourceAutoUpdateSheet = false
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
