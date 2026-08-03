// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.mihomo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import ui.components.AsteriskModalBottomSheet
import ui.components.AsteriskActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.DefaultMihomoOverrideScriptId
import app.DefaultMihomoProfileId
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.MihomoOverrideScriptState
import app.MihomoProfileState
import app.MihomoProfileType
import app.R
import app.collectAppState
import app.navigation.Route
import app.nextAvailableMihomoProfileId
import app.hasRuntimeRelevantChanges
import app.withMihomoRestartApplied
import app.withMihomoRestartRequired
import engine.mihomo.MihomoProviderMetadataCache
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.parseMihomoProxyProviderNames
import engine.mihomo.raw.usesRawMihomoConfig
import engine.proxy.ProxyServiceResult
import features.mihomo.provider.MihomoProviderNamesMetadata
import features.mihomo.provider.MihomoProviderUsageLoadState
import features.mihomo.provider.MihomoProviderUsageSection
import features.mihomo.provider.providerMetadataContentKey
import features.mihomo.provider.refreshMihomoProviderUsageAfterSync
import features.mihomo.provider.resolveSharedMihomoProviderUsageState
import features.mihomo.provider.selectedMihomoProviderUsageLoadKeyOrNull
import features.subscription.SubscriptionInstallConfig
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import features.subscription.toSubscriptionInstallConfigOrNull
import features.subscription.usecase.MihomoProfileSyncStage
import features.subscription.usecase.launchMihomoProfileSubscriptionUpdate
import features.subscription.usecase.subscriptionUpdateRequestCount
import features.subscription.usecase.subscriptionUpdateMessage
import features.subscription.usecase.toSubscriptionFetchOptions
import features.subscription.usecase.tryUpdateSubscriptionsSequentially
import features.subscription.usecase.withUpdatedMihomoProfiles
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.components.AsteriskInfoChip
import ui.components.AsteriskChipTone
import ui.components.AsteriskExtendedFab
import ui.components.AsteriskSelectionCard
import ui.theme.AsteriskShapeTokens
import ui.text.formatTemplate
import utils.ReadableByteUnit
import utils.toReadableBytes
import utils.toReadableDateOrDash
import utils.toReadableDateTimeOrDash
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

@Composable
fun MihomoProfileListPage(
    padding: PaddingValues,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isWideScreen = LocalIsWideScreen.current
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingActionButtonBottomPadding = (
        padding.calculateBottomPadding() - navigationBarBottomPadding
    ).coerceAtLeast(0.dp)
    val navigator = LocalNavigator.current
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val activeSubscriptionUpdateRequests by subscriptionUpdateRequestCount.collectAsState()
    val profileSaveUseCase = remember(
        services.mihomoProfilePreparer,
        services.mihomoProfileContentStore,
    ) {
        MihomoProfileSaveUseCase.create(
            profilePreparer = services.mihomoProfilePreparer,
            contentStore = services.mihomoProfileContentStore,
        )
    }
    var showImportDialog by remember { mutableStateOf(false) }
    var previewProfileName by remember { mutableStateOf("") }
    var previewProfileContent by remember { mutableStateOf<String?>(null) }
    var syncingProfileIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var proxyProviderNamesByProfileId by remember {
        mutableStateOf<Map<Int, MihomoProviderNamesMetadata>>(emptyMap())
    }
    var providerUsageExpandedProfileId by remember { mutableStateOf<Int?>(null) }
    var providerUsageWaitingForProfileStop by remember { mutableStateOf(false) }
    var providerUsageProfileStopFailed by remember { mutableStateOf(false) }
    var showRestartRequired by remember { mutableStateOf(false) }
    var restartInProgress by remember { mutableStateOf(false) }
    var importSyncStage by remember { mutableStateOf<MihomoProfileSyncStage?>(null) }
    var importSaveJob by remember { mutableStateOf<Job?>(null) }
    var batchSyncJob by remember { mutableStateOf<Job?>(null) }
    var batchSyncProgress by remember { mutableStateOf<MihomoProfileBatchSyncProgress?>(null) }
    var failedImport by remember { mutableStateOf<FailedMihomoProfileImport?>(null) }
    val remoteSubscriptionProfiles = appState.mihomoProfiles.filter { profile ->
        profile.type == MihomoProfileType.Url && profile.url.isNotBlank()
    }
    val syncSuccessMessage = stringResource(R.string.subscription_update_result)
    val syncFailedMessage = stringResource(R.string.subscription_update_result_with_failed)
    val syncCancelledMessage = stringResource(R.string.mihomo_configuration_sync_all_cancelled)
    val syncInterruptedMessage = stringResource(R.string.mihomo_configuration_sync_all_interrupted)
    val syncBusyMessage = stringResource(R.string.mihomo_configuration_sync_all_busy)
    val providerSyncResultMessage = stringResource(R.string.mihomo_configuration_provider_sync_result)
    val providerSyncEmptyMessage = stringResource(R.string.mihomo_configuration_provider_sync_empty)
    val providerSyncFailedMessage = stringResource(R.string.mihomo_configuration_provider_sync_failed)
    val providerSyncUsageReloadFailedMessage = stringResource(
        R.string.mihomo_configuration_provider_sync_usage_reload_failed,
    )
    val previewFailedMessage = stringResource(R.string.mihomo_configuration_preview_failed)
    val importedMessage = stringResource(R.string.mihomo_configuration_imported)
    val importFileFailedMessage = stringResource(R.string.mihomo_configuration_import_file_failed)
    val syncFailedSavedMessage = stringResource(R.string.mihomo_configuration_save_sync_failed_saved)
    val importQrFailedMessage = stringResource(R.string.mihomo_configuration_import_qr_failed)
    val invalidQrMessage = stringResource(R.string.mihomo_configuration_invalid_qr_content)
    val serviceStoppedMessage = stringResource(R.string.proxy_service_stopped)
    val stopFailedMessage = stringResource(R.string.mihomo_dashboard_stop_failed)
    val restartFailedMessage = stringResource(R.string.mihomo_configuration_restart_failed)

    val profileContentSignatures = appState.mihomoProfiles.map { profile ->
        MihomoProfileContentSignature(
            id = profile.id,
            contentPath = profile.contentPath,
            contentSha256 = profile.contentSha256,
            contentSizeBytes = profile.contentSizeBytes,
        )
    }
    LaunchedEffect(profileContentSignatures) {
        val profiles = appState.mihomoProfiles
        proxyProviderNamesByProfileId = emptyMap()
        proxyProviderNamesByProfileId = withContext(Dispatchers.IO) {
            buildMap {
                profiles.forEach { profile ->
                    if (!profile.hasContent) return@forEach
                    val cacheKey = profile.providerMetadataContentKey()
                    val names = try {
                        MihomoProviderMetadataCache.getProxyProviderNames(cacheKey) {
                            services.mihomoProfileContentStore.useReader(profile) { reader ->
                                reader.parseMihomoProxyProviderNames()
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        emptyList()
                    }
                    put(
                        profile.id,
                        MihomoProviderNamesMetadata(contentKey = cacheKey, names = names),
                    )
                }
            }
        }
    }

    val keyedProviderUsageState by services.mihomoProviderUsage.state.collectAsState()
    val providerUsageLoadKey = appState.selectedMihomoProviderUsageLoadKeyOrNull()

    LaunchedEffect(appState.proxyRunning) {
        if (!appState.proxyRunning) {
            providerUsageWaitingForProfileStop = false
            providerUsageProfileStopFailed = false
        }
    }
    fun saveProfile(profile: MihomoProfileState, isNew: Boolean): MihomoProfileState {
        var savedProfile = profile
        updateAppState { state ->
            if (isNew) {
                val profileId = state.nextAvailableMihomoProfileId()
                savedProfile = profile.copy(id = profileId)
                val shouldSelectProfile = state.mihomoProfiles.isEmpty()
                state.copy(
                    mihomoProfiles = state.mihomoProfiles + savedProfile,
                    nextMihomoProfileId = profileId + 1,
                    selectedMihomoProfileId = if (shouldSelectProfile) {
                        savedProfile.id
                    } else {
                        state.selectedMihomoProfileId
                    },
                )
            } else {
                val previousProfile = state.mihomoProfiles.firstOrNull { it.id == profile.id }
                state.copy(
                    mihomoProfiles = state.mihomoProfiles.map { item ->
                        if (item.id == profile.id) profile else item
                    },
                ).withMihomoRestartRequired(
                    profileId = profile.id,
                    contentChanged = previousProfile?.hasRuntimeRelevantChanges(profile) == true,
                )
            }
        }
        return savedProfile
    }

    fun observeProviderUsageProfileStop(stopJob: Job) {
        scope.launch {
            stopJob.join()
            val currentState = stateStore.state.value
            val stopFailed = currentState.proxyRunning
            providerUsageWaitingForProfileStop = stopFailed
            providerUsageProfileStopFailed = stopFailed
        }
    }

    fun retryProviderUsage() {
        if (!providerUsageWaitingForProfileStop) {
            services.mihomoProviderUsage.refresh(stateStore.state.value)
            return
        }
        val currentState = stateStore.state.value
        if (!currentState.proxyRunning) {
            providerUsageWaitingForProfileStop = false
            providerUsageProfileStopFailed = false
            services.mihomoProviderUsage.refresh(currentState)
            return
        }
        providerUsageProfileStopFailed = false
        val stopJob = services.appScope.launch {
            stopProxyServiceAfterProfileChange(
                appState = currentState,
                services = services,
                updateAppState = updateAppState,
                stoppedMessage = serviceStoppedMessage,
                stopFailedMessage = stopFailedMessage,
            )
        }
        observeProviderUsageProfileStop(stopJob)
    }

    fun deleteProfile(profile: MihomoProfileState) {
        if (profile.builtIn) return
        val previousState = appState
        val selectedProfileDeleted = profile.id == appState.selectedMihomoProfileId
        if (selectedProfileDeleted) {
            providerUsageExpandedProfileId = null
        }
        if (selectedProfileDeleted && previousState.proxyRunning) {
            providerUsageWaitingForProfileStop = true
            providerUsageProfileStopFailed = false
        }
        updateAppState { state ->
            val nextProfiles = state.mihomoProfiles.filterNot { it.id == profile.id }
            state.copy(
                mihomoProfiles = nextProfiles,
                selectedMihomoProfileId = if (nextProfiles.any { it.id == state.selectedMihomoProfileId }) {
                    state.selectedMihomoProfileId
                } else {
                    nextProfiles.firstOrNull()?.id ?: DefaultMihomoProfileId
                },
            )
        }
        services.mihomoProfileContentStore.delete(profile)
        if (selectedProfileDeleted) {
            val stopJob = services.appScope.launch {
                stopProxyServiceAfterProfileChange(
                    appState = previousState,
                    services = services,
                    updateAppState = updateAppState,
                    stoppedMessage = serviceStoppedMessage,
                    stopFailedMessage = stopFailedMessage,
                )
            }
            if (previousState.proxyRunning) {
                observeProviderUsageProfileStop(stopJob)
            }
        }
    }

    fun selectProfile(profile: MihomoProfileState) {
        if (profile.id == appState.selectedMihomoProfileId) return
        val previousState = appState
        providerUsageExpandedProfileId = null
        if (previousState.proxyRunning) {
            providerUsageWaitingForProfileStop = true
            providerUsageProfileStopFailed = false
        }
        updateAppState { state ->
            if (profile.id == state.selectedMihomoProfileId) {
                state
            } else {
                state.copy(selectedMihomoProfileId = profile.id)
            }
        }
        val stopJob = services.appScope.launch {
            stopProxyServiceAfterProfileChange(
                appState = previousState,
                services = services,
                updateAppState = updateAppState,
                stoppedMessage = serviceStoppedMessage,
                stopFailedMessage = stopFailedMessage,
            )
        }
        if (previousState.proxyRunning) {
            observeProviderUsageProfileStop(stopJob)
        }
    }

    fun previewProfile(profile: MihomoProfileState) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    MihomoProfileFactory.buildProfile(context, appState.copy(selectedMihomoProfileId = profile.id))
                }
            }.onSuccess { content ->
                previewProfileName = profile.name
                previewProfileContent = content
            }.onFailure { error ->
                services.tipNotifier.showError(error, previewFailedMessage)
            }
        }
    }

    fun launchProfileSubscriptionUpdate(profile: MihomoProfileState) =
        services.appScope.launchMihomoProfileSubscriptionUpdate(
            profiles = listOf(profile),
            appStateSnapshot = appState,
            profilePreparer = services.mihomoProfilePreparer,
            contentStore = services.mihomoProfileContentStore,
            updateAppState = updateAppState,
            onResult = { result ->
                services.tipNotifier.show(
                    subscriptionUpdateMessage(
                        result = result,
                        successTemplate = syncSuccessMessage,
                        failedTemplate = syncFailedMessage,
                    ),
                )
            },
            onFailure = { error ->
                services.tipNotifier.showError(error, syncFailedMessage)
            },
        )

    fun syncProfile(profile: MihomoProfileState) {
        if (profile.type != MihomoProfileType.Url || profile.url.isBlank() || profile.id in syncingProfileIds) return
        syncingProfileIds = syncingProfileIds + profile.id
        val syncJob = launchProfileSubscriptionUpdate(profile)
        scope.launch {
            try {
                syncJob.join()
                if (stateStore.state.value.pendingMihomoRestartProfileId == profile.id) {
                    showRestartRequired = true
                }
            } finally {
                syncingProfileIds = syncingProfileIds - profile.id
            }
        }
    }

    fun syncAllSubscriptions() {
        if (
            batchSyncJob?.isActive == true ||
            syncingProfileIds.isNotEmpty() ||
            subscriptionUpdateRequestCount.value > 0
        ) {
            return
        }
        val profiles = remoteSubscriptionProfiles
        if (profiles.isEmpty()) return
        val ownedProfileIds = profiles.mapTo(mutableSetOf()) { profile -> profile.id }

        var updatedCount = 0
        var failedCount = 0
        var processedCount = 0
        var reserved = false
        val updatedProfileIds = mutableSetOf<Int>()
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = tryUpdateSubscriptionsSequentially(
                    profiles = profiles,
                    profilePreparer = services.mihomoProfilePreparer,
                    contentStore = services.mihomoProfileContentStore,
                    fetchOptions = { item -> stateStore.state.value.toSubscriptionFetchOptions(item) },
                    onReserved = {
                        reserved = true
                        syncingProfileIds = syncingProfileIds + ownedProfileIds
                    },
                    onProfileStarted = { profile, currentIndex, totalCount ->
                        batchSyncProgress = MihomoProfileBatchSyncProgress(
                            profileId = profile.id,
                            profileName = profile.name.ifBlank { profile.url },
                            currentIndex = currentIndex,
                            totalCount = totalCount,
                            completedCount = processedCount,
                            stage = MihomoProfileSyncStage.Downloading,
                        )
                    },
                    onStage = { item, stage ->
                        batchSyncProgress = batchSyncProgress
                            ?.takeIf { progress -> progress.profileId == item.id }
                            ?.copy(stage = stage)
                    },
                    onProfileCompleted = { item, profileResult, completedAtMillis ->
                        profileResult
                            .onSuccess { update ->
                                updateAppState { state ->
                                    state.withUpdatedMihomoProfiles(
                                        updates = listOf(update),
                                        updatedAtMillis = completedAtMillis,
                                    )
                                }
                                updatedProfileIds += update.profileId
                                updatedCount += 1
                            }
                            .onFailure { failedCount += 1 }
                        processedCount += 1
                        batchSyncProgress = batchSyncProgress
                            ?.takeIf { progress -> progress.profileId == item.id }
                            ?.copy(completedCount = processedCount)
                    },
                )
                if (result == null) {
                    services.tipNotifier.show(syncBusyMessage)
                    return@launch
                }
                val template = if (failedCount > 0) syncFailedMessage else syncSuccessMessage
                services.tipNotifier.show(
                    template.formatTemplate(
                        "profileCount" to updatedCount,
                        "failedCount" to failedCount,
                    ),
                )
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    services.tipNotifier.show(
                        syncCancelledMessage.formatTemplate(
                            "profileCount" to updatedCount,
                            "failedCount" to failedCount,
                            "skippedCount" to profiles.size - processedCount,
                        ),
                    )
                }
                throw error
            } catch (_: Throwable) {
                services.tipNotifier.show(
                    syncInterruptedMessage.formatTemplate(
                        "profileCount" to updatedCount,
                        "failedCount" to failedCount,
                        "skippedCount" to profiles.size - processedCount,
                    ),
                )
            } finally {
                batchSyncProgress = null
                if (reserved) syncingProfileIds = syncingProfileIds - ownedProfileIds
                if (batchSyncJob === job) batchSyncJob = null
                if (stateStore.state.value.pendingMihomoRestartProfileId in updatedProfileIds) {
                    showRestartRequired = true
                }
            }
        }
        batchSyncJob = job
        job.start()
    }

    fun restartWithUpdatedSubscription() {
        if (restartInProgress) return
        restartInProgress = true
        val completed = CompletableDeferred<ProxyServiceResult>()
        services.appScope.launch {
            completed.complete(services.proxyServiceUseCase.restart(stateStore.state.value))
        }
        scope.launch {
            try {
                when (val result = completed.await()) {
                    is ProxyServiceResult.Success -> {
                        updateAppState { state ->
                            state.copy(
                                proxyRunning = result.proxyRunning,
                                localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                                mihomoControlPort = result.appState?.mihomoControlPort ?: state.mihomoControlPort,
                            ).withMihomoRestartApplied()
                        }
                        showRestartRequired = false
                    }

                    is ProxyServiceResult.Failed -> {
                        services.tipNotifier.showError(result.error, restartFailedMessage)
                    }
                }
            } finally {
                restartInProgress = false
            }
        }
    }

    fun syncProfileProviders(profile: MihomoProfileState) {
        if (!profile.hasContent || profile.id in syncingProfileIds) {
            return
        }
        syncingProfileIds = syncingProfileIds + profile.id
        val syncJob = services.appScope.launch {
            var reloadUsage = false
            try {
                val content = withContext(Dispatchers.IO) {
                    services.mihomoProfileContentStore.read(profile)
                }
                val result = services.mihomoProviderFetcher.refreshProxyProviders(
                    profileContent = content,
                    sourceUrl = profile.url,
                    ageSecretKey = profile.ageSecretKey,
                    fetchOptions = stateStore.state.value.toSubscriptionFetchOptions(profile),
                )
                val currentState = stateStore.state.value
                if (
                    result.successCount > 0 &&
                    currentState.selectedMihomoProfileId == profile.id &&
                    !(currentState.usesRawMihomoConfig() && !currentState.proxyRunning)
                ) {
                    val reloadFailure = services.mihomoRuntime
                        .reloadInteractiveProfileFromDisk(currentState)
                        .exceptionOrNull()
                    if (reloadFailure is CancellationException) throw reloadFailure
                    if (reloadFailure != null) {
                        services.tipNotifier.showError(
                            reloadFailure,
                            providerSyncUsageReloadFailedMessage.format(
                                result.totalCount,
                                result.successCount,
                                result.failedCount,
                            ),
                        )
                    } else {
                        reloadUsage = true
                    }
                } else if (result.successCount > 0) {
                    reloadUsage = true
                }
                if (result.failedCount == 0) {
                    updateAppState { state ->
                        state.copy(
                            mihomoProfiles = state.mihomoProfiles.map { item ->
                                if (item.id == profile.id) {
                                    item.withProviderSyncResult(result.failedCount)
                                } else {
                                    item
                                }
                            },
                        )
                    }
                }
                if (!reloadUsage && result.successCount > 0) {
                    return@launch
                }
                services.tipNotifier.show(
                    if (result.totalCount == 0) {
                        providerSyncEmptyMessage
                    } else {
                        providerSyncResultMessage.format(
                            result.totalCount,
                            result.successCount,
                            result.failedCount,
                        )
                    },
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                services.tipNotifier.showError(error, providerSyncFailedMessage)
            } finally {
                refreshMihomoProviderUsageAfterSync(
                    refreshRequired = reloadUsage,
                    syncedProfileId = profile.id,
                    appState = stateStore.state.value,
                    refresh = services.mihomoProviderUsage::refresh,
                )
            }
        }
        scope.launch {
            try {
                syncJob.join()
            } finally {
                syncingProfileIds = syncingProfileIds - profile.id
            }
        }
    }

    fun saveSubscriptionProfile(config: SubscriptionInstallConfig): MihomoProfileState {
        return saveProfile(
            profile = MihomoProfileState(
                id = DefaultMihomoProfileId,
                name = config.name,
                type = MihomoProfileType.Url,
                url = config.url,
                userAgent = config.userAgent,
                updateInterval = config.updateInterval,
                updateViaProxy = config.updateViaProxy,
            ),
            isNew = true,
        )
    }

    fun launchImportedProfileSave(draft: MihomoProfileSaveDraft) {
        if (importSaveJob?.isActive == true) return
        failedImport = null
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val preparation = profileSaveUseCase.prepare(
                    draft = draft,
                    fetchOptions = AndroidSubscriptionFetchOptions(),
                    onStage = { stage -> importSyncStage = stage },
                )
                when (preparation) {
                    is MihomoProfileSavePreparation.Success -> {
                        importSyncStage = null
                        val committed = profileSaveUseCase.commit(draft, preparation).copy(
                            lastUpdatedAtMillis = draft.desiredProfile.lastUpdatedAtMillis,
                        )
                        saveProfile(committed, isNew = true)
                        services.tipNotifier.show(importedMessage)
                    }

                    is MihomoProfileSavePreparation.Failure -> {
                        failedImport = FailedMihomoProfileImport(draft, preparation)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                services.tipNotifier.showError(error, importFileFailedMessage)
            } finally {
                if (importSaveJob === job) {
                    importSaveJob = null
                }
                importSyncStage = null
            }
        }
        importSaveJob = job
        job.start()
    }

    fun saveFailedImport() {
        val failed = failedImport ?: return
        failedImport = null
        scope.launch {
            try {
                val committed = profileSaveUseCase.commit(failed.draft, failed.failure).copy(
                    lastUpdatedAtMillis = failed.draft.desiredProfile.lastUpdatedAtMillis,
                )
                saveProfile(committed, isNew = true)
                services.tipNotifier.show(syncFailedSavedMessage)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failedImport = failed
                services.tipNotifier.showError(error, importFileFailedMessage)
            }
        }
    }

    fun importFile() {
        scope.launch {
            runCatching {
                val uri = services.mihomoProfileFilePicker() ?: return@launch
                val imported = withContext(Dispatchers.IO) {
                    context.readMihomoProfileFile(uri)
                }
                launchImportedProfileSave(
                    MihomoProfileSaveDraft(
                        desiredProfile = MihomoProfileState(
                            id = DefaultMihomoProfileId,
                            name = imported.name,
                            type = MihomoProfileType.File,
                            lastUpdatedAtMillis = imported.modifiedAtMillis,
                        ),
                        originalProfile = null,
                        localContent = imported.content,
                        contentChanged = true,
                    ),
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                services.tipNotifier.showError(error, importFileFailedMessage)
            }
        }
    }

    fun importQrCode() {
        services.appScope.launch {
            runCatching {
                val text = services.qrCodeScanner()?.trim().orEmpty()
                if (text.isBlank()) return@launch
                val config = text.toSubscriptionInstallConfigOrNull()
                    ?: error(invalidQrMessage)
                val savedProfile = saveSubscriptionProfile(config)
                launchProfileSubscriptionUpdate(savedProfile)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                services.tipNotifier.showError(error, importQrFailedMessage)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.mihomo_configurations_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                actions = {
                    IconButton(
                        onClick = ::syncAllSubscriptions,
                        enabled = remoteSubscriptionProfiles.isNotEmpty() &&
                            syncingProfileIds.isEmpty() &&
                            batchSyncJob?.isActive != true &&
                            activeSubscriptionUpdateRequests == 0,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = stringResource(R.string.mihomo_configuration_sync_all),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AsteriskExtendedFab(
                onClick = { showImportDialog = true },
                icon = Icons.Rounded.Add,
                text = stringResource(R.string.mihomo_configuration_add),
                modifier = Modifier.padding(bottom = floatingActionButtonBottomPadding),
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val listPadding = pageListPadding(contentPadding, bottomExtra = 88.dp)
        val layoutDirection = LocalLayoutDirection.current
        val pageListContentPadding = PaddingValues(
            start = listPadding.calculateStartPadding(layoutDirection),
            end = listPadding.calculateEndPadding(layoutDirection),
            bottom = listPadding.calculateBottomPadding(),
        )

        LazyColumn(
            modifier = Modifier.padding(top = listPadding.calculateTopPadding()),
            contentPadding = pageListContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (appState.mihomoProfiles.isEmpty()) {
                item(key = "profile_empty", contentType = "empty") {
                    MihomoProfileListEmptyState()
                }
            } else {
                items(
                    items = appState.mihomoProfiles,
                    key = { profile -> profile.id },
                ) { profile ->
                    val selected = profile.id == appState.selectedMihomoProfileId
                    MihomoProfileCard(
                        profile = profile,
                        overrideScripts = appState.mihomoOverrideScripts,
                        selected = selected,
                        restartRequired = profile.id == appState.pendingMihomoRestartProfileId,
                        syncing = profile.id in syncingProfileIds,
                        hasProxyProviders = proxyProviderNamesByProfileId[profile.id]
                            ?.takeIf { metadata ->
                                metadata.contentKey == profile.providerMetadataContentKey()
                            }
                            ?.names
                            ?.isNotEmpty() == true,
                        providerUsageState = if (selected) {
                            resolveSharedMihomoProviderUsageState(
                                keyedState = keyedProviderUsageState,
                                expectedKey = providerUsageLoadKey,
                                waitingForStop = providerUsageWaitingForProfileStop,
                                stopFailed = providerUsageProfileStopFailed,
                            )
                        } else {
                            MihomoProviderUsageLoadState.Hidden
                        },
                        providerUsageExpanded = providerUsageExpandedProfileId == profile.id,
                        onProviderUsageExpandedChange = { expanded ->
                            providerUsageExpandedProfileId = if (expanded) profile.id else null
                        },
                        onProviderUsageRetry = ::retryProviderUsage,
                        onOpenProviderDetails = { navigator.push(Route.MihomoProviderManagement) },
                        onSelect = { selectProfile(profile) },
                        onAction = { action ->
                            when (action) {
                                MihomoProfileAction.Edit -> {
                                    navigator.push(
                                        Route.MihomoProfileEdit(
                                            profileId = profile.id,
                                            type = profile.type.storageValue,
                                        ),
                                    )
                                }
                                MihomoProfileAction.Preview -> previewProfile(profile)
                                MihomoProfileAction.Sync -> syncProfile(profile)
                                MihomoProfileAction.SyncProviders -> syncProfileProviders(profile)
                                MihomoProfileAction.Delete -> deleteProfile(profile)
                            }
                        },
                    )
                }
            }
        }
        MihomoProfileImportDialog(
            show = showImportDialog,
            onDismissRequest = { showImportDialog = false },
            onAction = { action ->
                showImportDialog = false
                when (action) {
                    MihomoProfileImportAction.QrCode -> importQrCode()
                    MihomoProfileImportAction.File -> importFile()
                    MihomoProfileImportAction.Url -> navigator.push(
                        Route.MihomoProfileEdit(
                            profileId = 0,
                            type = MihomoProfileType.Url.storageValue,
                            draftId = System.nanoTime(),
                        ),
                    )
                }
            },
        )
        MihomoProfileSyncProgressDialog(
            stage = importSyncStage.takeIf { importSaveJob?.isActive == true },
            onCancel = { importSaveJob?.cancel() },
        )
        batchSyncProgress?.let { progress ->
            MihomoProfileSyncProgressDialog(
                stage = progress.stage,
                onCancel = { batchSyncJob?.cancel() },
                title = stringResource(R.string.mihomo_configuration_sync_all_in_progress_title),
                profileName = progress.profileName,
                progress = progress.fraction,
                progressLabel = stringResource(
                    R.string.mihomo_configuration_sync_all_progress,
                    progress.currentIndex,
                    progress.totalCount,
                ),
            )
        }
        failedImport?.let { failed ->
            MihomoProfileSyncFailureDialog(
                failure = failed.failure,
                onRetry = { launchImportedProfileSave(failed.draft) },
                onSaveAnyway = ::saveFailedImport,
                onCancel = { failedImport = null },
            )
        }
        MihomoProfilePreviewDialog(
            show = previewProfileContent != null,
            profileName = previewProfileName,
            content = previewProfileContent.orEmpty(),
            onDismissRequest = { previewProfileContent = null },
        )
        RestartRequiredDialog(
            show = showRestartRequired,
            restarting = restartInProgress,
            onRestartNow = ::restartWithUpdatedSubscription,
            onLater = { showRestartRequired = false },
        )
    }
}

@Composable
private fun MihomoProfileListEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = AsteriskShapeTokens.InnerContainer,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(18.dp).size(32.dp),
            )
        }
        Text(
            text = stringResource(R.string.common_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class MihomoProfileContentSignature(
    val id: Int,
    val contentPath: String,
    val contentSha256: String,
    val contentSizeBytes: Long,
)

private data class MihomoProfileBatchSyncProgress(
    val profileId: Int,
    val profileName: String,
    val currentIndex: Int,
    val totalCount: Int,
    val completedCount: Int,
    val stage: MihomoProfileSyncStage,
) {
    val fraction: Float
        get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
}

@Composable
private fun MihomoProfileCard(
    profile: MihomoProfileState,
    overrideScripts: List<MihomoOverrideScriptState>,
    selected: Boolean,
    restartRequired: Boolean,
    syncing: Boolean,
    hasProxyProviders: Boolean,
    providerUsageState: MihomoProviderUsageLoadState,
    providerUsageExpanded: Boolean,
    onProviderUsageExpandedChange: (Boolean) -> Unit,
    onProviderUsageRetry: () -> Unit,
    onOpenProviderDetails: () -> Unit,
    onSelect: () -> Unit,
    onAction: (MihomoProfileAction) -> Unit,
) {
    val displayState = reduceMihomoProfileDisplay(profile)
    val overrideScriptName = profile.overrideScriptName(overrideScripts)
    var menuExpanded by remember { mutableStateOf(false) }

    AsteriskSelectionCard(
        selected = selected,
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected },
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = profile.summaryText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
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
                        MihomoProfileMenuItem(
                            text = stringResource(R.string.common_edit),
                            action = MihomoProfileAction.Edit,
                            onAction = onAction,
                            onDismiss = { menuExpanded = false },
                        )
                        MihomoProfileMenuItem(
                            text = stringResource(R.string.mihomo_configuration_preview),
                            action = MihomoProfileAction.Preview,
                            onAction = onAction,
                            onDismiss = { menuExpanded = false },
                        )
                        if (displayState.showSync && !syncing) {
                            MihomoProfileMenuItem(
                                text = stringResource(R.string.mihomo_configuration_sync),
                                action = MihomoProfileAction.Sync,
                                onAction = onAction,
                                onDismiss = { menuExpanded = false },
                            )
                        }
                        if (hasProxyProviders && !syncing) {
                            MihomoProfileMenuItem(
                                text = stringResource(R.string.mihomo_configuration_sync_providers),
                                action = MihomoProfileAction.SyncProviders,
                                onAction = onAction,
                                onDismiss = { menuExpanded = false },
                            )
                        }
                        if (!profile.builtIn) {
                            MihomoProfileMenuItem(
                                text = stringResource(R.string.common_delete),
                                action = MihomoProfileAction.Delete,
                                onAction = onAction,
                                onDismiss = { menuExpanded = false },
                            )
                        }
                    }
                }
            }

            MihomoProfileSubscriptionInfo(profile = profile)
            MihomoProviderUsageSection(
                state = providerUsageState,
                expanded = providerUsageExpanded,
                onExpandedChange = onProviderUsageExpandedChange,
                onRetry = onProviderUsageRetry,
                onOpenDetails = onOpenProviderDetails,
            )
            Text(
                text = profile.lastUpdatedText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp, end = 8.dp),
            )
            FlowRow(
                modifier = Modifier.padding(top = 12.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (displayState.syncFailed) {
                    AsteriskInfoChip(
                        text = stringResource(R.string.mihomo_configuration_sync_failed_chip),
                        tone = AsteriskChipTone.Error,
                    )
                }
                AsteriskInfoChip(
                    text = stringResource(
                        when (displayState.kind) {
                            MihomoProfileDisplayKind.RemoteSubscription -> {
                                R.string.mihomo_configuration_chip_remote_subscription
                            }
                            MihomoProfileDisplayKind.LocalFile -> R.string.mihomo_configuration_chip_local_file
                        },
                    ),
                    emphasized = selected,
                )
                if (displayState.rawConfiguration) {
                    AsteriskInfoChip(
                        text = stringResource(R.string.mihomo_configuration_raw_chip),
                        emphasized = selected,
                    )
                }
                if (restartRequired) {
                    AsteriskInfoChip(
                        text = stringResource(R.string.mihomo_configuration_restart_required),
                        emphasized = selected,
                    )
                }
                if (overrideScriptName != null) {
                    AsteriskInfoChip(
                        text = if (displayState.rawConfiguration) {
                            stringResource(R.string.mihomo_configuration_override_script_stopped)
                        } else {
                            stringResource(R.string.mihomo_configuration_override_script_applied)
                                .formatTemplate("name" to overrideScriptName)
                        },
                        emphasized = selected,
                    )
                }
            }
        }
    }
}

@Composable
private fun MihomoProfileMenuItem(
    text: String,
    action: MihomoProfileAction,
    onAction: (MihomoProfileAction) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(action.icon(), contentDescription = null) },
        onClick = {
            onDismiss()
            onAction(action)
        },
    )
}

private fun MihomoProfileAction.icon(): ImageVector {
    return when (this) {
        MihomoProfileAction.Edit -> Icons.Rounded.Edit
        MihomoProfileAction.Preview -> Icons.Rounded.Visibility
        MihomoProfileAction.Sync -> Icons.Rounded.Sync
        MihomoProfileAction.SyncProviders -> Icons.Rounded.CloudSync
        MihomoProfileAction.Delete -> Icons.Rounded.Delete
    }
}

private enum class MihomoProfileAction {
    Edit,
    Preview,
    Sync,
    SyncProviders,
    Delete,
}

private enum class MihomoProfileImportAction {
    QrCode,
    File,
    Url,
}

@Composable
private fun MihomoProfileImportDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onAction: (MihomoProfileImportAction) -> Unit,
) {
    AsteriskModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.mihomo_configuration_add_method_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            MihomoProfileImportOption(
                title = stringResource(R.string.mihomo_configuration_add_qr),
                summary = stringResource(R.string.mihomo_configuration_add_qr_summary),
                icon = { Icon(imageVector = Icons.Rounded.QrCodeScanner, contentDescription = null) },
                onClick = { onAction(MihomoProfileImportAction.QrCode) },
            )
            MihomoProfileImportOption(
                title = stringResource(R.string.mihomo_configuration_add_file),
                summary = stringResource(R.string.mihomo_configuration_add_file_summary),
                icon = { Icon(imageVector = Icons.Rounded.FolderOpen, contentDescription = null) },
                onClick = { onAction(MihomoProfileImportAction.File) },
            )
            MihomoProfileImportOption(
                title = stringResource(R.string.mihomo_configuration_add_url),
                summary = stringResource(R.string.mihomo_configuration_add_url_summary),
                icon = { Icon(imageVector = Icons.Rounded.Link, contentDescription = null) },
                onClick = { onAction(MihomoProfileImportAction.Url) },
            )
        }
    }
}

@Composable
private fun MihomoProfileImportOption(
    title: String,
    summary: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
        shape = AsteriskShapeTokens.InnerContainer,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = AsteriskShapeTokens.SmallContainer,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(
                    modifier = Modifier.padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun MihomoProfileSubscriptionInfo(
    profile: MihomoProfileState,
) {
    val info = profile.subscriptionInfo
    if (!info.hasTraffic) return
    val progress = (info.usedBytes.toDouble() / info.totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).padding(end = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )
        Text(
            text = stringResource(R.string.mihomo_configuration_traffic_summary)
                .formatTemplate(
                    "used" to info.usedBytes.toReadableBytes(maxUnit = ReadableByteUnit.GiB),
                    "total" to info.totalBytes.toReadableBytes(maxUnit = ReadableByteUnit.GiB),
                    "expire" to info.expireText(),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, end = 8.dp),
        )
    }
}

@Composable
private fun MihomoProfileState.summaryText(): String {
    if (disableOverrides) return stringResource(R.string.mihomo_configuration_raw_summary)
    return when (type) {
        MihomoProfileType.Url -> url.ifBlank { stringResource(R.string.mihomo_configuration_type_url) }
        MihomoProfileType.File -> stringResource(R.string.mihomo_configuration_type_file)
    }
}

private fun MihomoProfileState.overrideScriptName(
    scripts: List<MihomoOverrideScriptState>,
): String? {
    if (overrideScriptId == DefaultMihomoOverrideScriptId) return null
    return scripts.firstOrNull { script -> script.id == overrideScriptId }?.name
}

@Composable
private fun MihomoProfilePreviewDialog(
    show: Boolean,
    profileName: String,
    content: String,
    onDismissRequest: () -> Unit,
) {
    if (!show) return
    val previewEditorState = remember(content) {
        MihomoCodeEditorState(content).also { state ->
            state.replaceText(content, placeCursorAtEnd = false)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 880.dp)
                .padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.mihomo_configuration_preview_title)
                        .formatTemplate("name" to profileName.ifBlank { "-" }),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                YamlCodeEditor(
                    label = stringResource(R.string.mihomo_configuration_content),
                    state = previewEditorState,
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 360.dp, max = 560.dp),
                )
                AsteriskActionButton(
                    text = stringResource(R.string.common_complete),
                    icon = Icons.Rounded.Check,
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MihomoProfileState.lastUpdatedText(): String {
    val value = lastUpdatedAtMillis.toReadableDateTimeOrDash()
    val label = when (type) {
        MihomoProfileType.File -> R.string.mihomo_configuration_last_modified
        MihomoProfileType.Url -> R.string.mihomo_configuration_last_sync
    }
    return stringResource(label).formatTemplate("time" to value)
}

@Composable
private fun app.MihomoSubscriptionInfo.expireText(): String {
    if (expireAtSeconds <= 0L) return stringResource(R.string.mihomo_configuration_expire_unlimited)
    return (expireAtSeconds * 1000L).toReadableDateOrDash()
}

private data class ImportedMihomoProfileFile(
    val name: String,
    val content: String,
    val modifiedAtMillis: Long,
)

private data class FailedMihomoProfileImport(
    val draft: MihomoProfileSaveDraft,
    val failure: MihomoProfileSavePreparation.Failure,
)

private fun Context.readMihomoProfileFile(
    uri: Uri,
): ImportedMihomoProfileFile {
    val content = contentResolver.openInputStream(uri)?.use { input ->
        input.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
    } ?: error("Unable to open configuration file")
    if (content.isBlank()) {
        error("Configuration file is empty")
    }
    val displayName = queryDisplayName(uri)
    val name = displayName
        ?.substringBeforeLast('.', displayName)
        ?.takeIf(String::isNotBlank)
        ?: "Profile"
    return ImportedMihomoProfileFile(
        name = name,
        content = content,
        modifiedAtMillis = queryLastModified(uri) ?: System.currentTimeMillis(),
    )
}

private fun Context.queryLastModified(uri: Uri): Long? {
    return runCatching {
        contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (index < 0) return@use null
                cursor.getLong(index).takeIf { value -> value > 0L }
            }
    }.getOrNull()
}

private fun Context.queryDisplayName(uri: Uri): String? {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index < 0) return@use null
        cursor.getString(index)
    } ?: uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf(String::isNotBlank)
}
