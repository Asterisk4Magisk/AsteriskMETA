// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.mihomo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.DefaultMihomoOverrideScriptId
import app.DefaultMihomoProfileId
import app.DefaultMihomoProfileUpdateInterval
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.MihomoProfileState
import app.MihomoProfileType
import app.R
import app.collectAppState
import app.hasRuntimeRelevantChanges
import app.modes.RunModeBpf2Socks
import app.modes.RunModeTproxy
import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import app.nextAvailableMihomoProfileId
import app.withMihomoRestartApplied
import app.withMihomoRestartRequired
import com.github.kr328.clash.core.Clash
import engine.mihomo.raw.MihomoRawConfigParseResult
import engine.mihomo.raw.MihomoRawConfigParser
import engine.mihomo.raw.MihomoRawConfigSnapshot
import engine.mihomo.raw.RawConfigReadiness
import engine.mihomo.raw.check
import engine.mihomo.sha256Hex
import engine.proxy.ProxyServiceResult
import features.settings.SettingsDropdownRow
import features.subscription.isPlainHttpSubscriptionUrl
import features.subscription.isValidSubscriptionIntervalInput
import features.subscription.isValidManualSubscriptionUrl
import features.subscription.sanitizeSubscriptionIntervalInput
import features.subscription.usecase.MihomoProfileSyncStage
import features.subscription.usecase.toSubscriptionFetchOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.components.AsteriskActionButton
import ui.components.AsteriskExpansionIndicator
import ui.components.AsteriskListRow
import ui.components.AsteriskModalBottomSheet
import ui.layout.pageContentPaddingWithCutout
import ui.theme.AsteriskMotion
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import ui.icons.AsteriskIcons as Icons

@Composable
fun MihomoProfileEditPage(
    padding: PaddingValues,
    profileId: Int,
    type: Int,
) {
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val isNew = profileId <= 0
    val targetProfile = remember(appState.mihomoProfiles, profileId) {
        appState.mihomoProfiles.firstOrNull { profile -> profile.id == profileId }
    }
    val profileType = targetProfile?.type ?: MihomoProfileType.fromStorageValue(type)
    val title = when {
        isNew && profileType == MihomoProfileType.Url -> stringResource(R.string.mihomo_configuration_add_url)
        profileType == MihomoProfileType.Url -> stringResource(R.string.mihomo_configuration_edit_url)
        else -> stringResource(R.string.mihomo_configuration_edit_file)
    }
    val syncSuccessMessage = stringResource(R.string.mihomo_configuration_save_sync_success)
    val syncFailedSavedMessage = stringResource(R.string.mihomo_configuration_save_sync_failed_saved)
    val saveFailedMessage = stringResource(R.string.mihomo_configuration_save_failed)
    val restartFailedMessage = stringResource(R.string.mihomo_configuration_restart_failed)
    val profileSaveUseCase = remember(
        services.mihomoProfilePreparer,
        services.mihomoProfileContentStore,
    ) {
        MihomoProfileSaveUseCase.create(
            profilePreparer = services.mihomoProfilePreparer,
            contentStore = services.mihomoProfileContentStore,
        )
    }

    var saving by remember { mutableStateOf(false) }
    var syncStage by remember { mutableStateOf<MihomoProfileSyncStage?>(null) }
    var saveJob by remember { mutableStateOf<Job?>(null) }
    var failedSave by remember { mutableStateOf<FailedMihomoProfileSave?>(null) }
    var showRestartRequired by remember { mutableStateOf(false) }
    var restartInProgress by remember { mutableStateOf(false) }
    val nameState = rememberTextFieldState(initialText = targetProfile?.name.orEmpty())
    val urlState = rememberTextFieldState(initialText = targetProfile?.url ?: "")
    val userAgentState = rememberTextFieldState(
        initialText = targetProfile?.userAgent ?: app.DefaultMihomoProfileUserAgent,
    )
    val ageSecretKeyState = rememberTextFieldState(initialText = targetProfile?.ageSecretKey.orEmpty())
    val updateIntervalState = rememberTextFieldState(
        initialText = targetProfile?.updateInterval ?: DefaultMihomoProfileUpdateInterval,
    )
    val updateIntervalValid = isValidSubscriptionIntervalInput(updateIntervalState.text.toString())
    var updateViaProxy by remember(targetProfile?.id, isNew) {
        mutableStateOf(targetProfile?.updateViaProxy ?: false)
    }
    val contentEditorState = remember(targetProfile?.id, isNew) {
        MihomoCodeEditorState()
    }
    var overrideScriptId by remember(targetProfile?.id, isNew) {
        mutableIntStateOf(targetProfile?.overrideScriptId ?: DefaultMihomoOverrideScriptId)
    }
    var disableOverrides by remember(targetProfile?.id, isNew) {
        mutableStateOf(targetProfile?.disableOverrides ?: false)
    }
    var showRawModeConfirmation by remember { mutableStateOf(false) }
    var rawContentForCheck by remember(targetProfile?.id, isNew) { mutableStateOf("") }
    val selectedOverrideScript = appState.mihomoOverrideScripts.firstOrNull { script ->
        script.id == overrideScriptId
    }
    val overrideScriptOptions = listOf(stringResource(R.string.mihomo_configuration_override_script_none)) +
        appState.mihomoOverrideScripts.map { script -> script.name }
    val selectedOverrideScriptIndex = selectedOverrideScript
        ?.let { script -> appState.mihomoOverrideScripts.indexOfFirst { it.id == script.id } + 1 }
        ?: 0
    val nameRequiredMessage = stringResource(R.string.mihomo_configuration_name_required)
    val invalidUrlMessage = stringResource(R.string.mihomo_configuration_invalid_subscription_url)
    val invalidAgeSecretKeyMessage = stringResource(R.string.mihomo_configuration_invalid_age_secret_key)
    var showHttpSubscriptionWarning by remember { mutableStateOf(false) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var showFileProperties by remember { mutableStateOf(false) }
    var rawParseResult by remember(targetProfile?.id, isNew) {
        mutableStateOf<MihomoRawConfigParseResult?>(null)
    }

    BackHandler(enabled = saving) {}

    LaunchedEffect(targetProfile?.id, targetProfile?.contentPath, profileType) {
        if (targetProfile == null) {
            contentEditorState.replaceText("")
            rawContentForCheck = ""
            return@LaunchedEffect
        }
        val initialContent = withContext(Dispatchers.IO) {
            services.mihomoProfileContentStore.readOrEmpty(targetProfile)
        }
        rawContentForCheck = initialContent
        if (profileType == MihomoProfileType.File) {
            contentEditorState.replaceText(initialContent)
        }
    }

    LaunchedEffect(
        disableOverrides,
        contentEditorState.documentVersion,
        rawContentForCheck,
        profileType,
    ) {
        if (!disableOverrides) {
            rawParseResult = null
            return@LaunchedEffect
        }
        if (profileType == MihomoProfileType.File) {
            delay(RawConfigValidationDebounceMillis.milliseconds)
        }
        val content = if (profileType == MihomoProfileType.File) {
            contentEditorState.snapshotText()
        } else {
            rawContentForCheck
        }
        rawParseResult = withContext(Dispatchers.Default) {
            MihomoRawConfigParser.parse(content.toByteArray(Charsets.UTF_8))
        }
    }
    val rawCheck = remember(rawParseResult, appState.runMode, appState.enableVpnHevTun, appState.enableLocalDns) {
        rawParseResult?.check(
            runMode = appState.runMode,
            vpnUsesHev = appState.enableVpnHevTun,
            dnsHijackRequested = appState.enableLocalDns,
        )
    }

    fun saveProfile(profile: MihomoProfileState, isNewProfile: Boolean): MihomoProfileState {
        var savedProfile = profile
        updateAppState { state ->
            if (isNewProfile) {
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

    fun selectedOverrideScriptId(): Int {
        return if (
            overrideScriptId == DefaultMihomoOverrideScriptId ||
            appState.mihomoOverrideScripts.any { script -> script.id == overrideScriptId }
        ) {
            overrideScriptId
        } else {
            DefaultMihomoOverrideScriptId
        }
    }

    fun restartWithSavedConfiguration() {
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
                        navigator.pop()
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

    fun launchSave(draft: MihomoProfileSaveDraft) {
        if (saving) return
        failedSave = null
        saving = true
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val preparation = profileSaveUseCase.prepare(
                    draft = draft,
                    fetchOptions = stateStore.state.value.toSubscriptionFetchOptions(draft.desiredProfile),
                    onStage = { stage -> syncStage = stage },
                )
                when (preparation) {
                    is MihomoProfileSavePreparation.Success -> {
                        syncStage = null
                        val committed = profileSaveUseCase.commit(draft, preparation)
                        val saved = saveProfile(committed, draft.originalProfile == null)
                        if (preparation.synchronized) {
                            services.tipNotifier.show(syncSuccessMessage)
                        }
                        if (stateStore.state.value.pendingMihomoRestartProfileId == saved.id) {
                            showRestartRequired = true
                        } else {
                            navigator.pop()
                        }
                    }

                    is MihomoProfileSavePreparation.Failure -> {
                        failedSave = FailedMihomoProfileSave(draft, preparation)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                services.tipNotifier.showError(error, saveFailedMessage)
            } finally {
                if (saveJob === job) {
                    saveJob = null
                }
                syncStage = null
                saving = false
            }
        }
        saveJob = job
        job.start()
    }

    fun saveFailedDraft() {
        val failed = failedSave ?: return
        if (saving) return
        failedSave = null
        saving = true
        scope.launch {
            try {
                val committed = profileSaveUseCase.commit(failed.draft, failed.failure)
                saveProfile(committed, failed.draft.originalProfile == null)
                services.tipNotifier.show(syncFailedSavedMessage)
                navigator.pop()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failedSave = failed
                services.tipNotifier.showError(error, saveFailedMessage)
            } finally {
                saving = false
            }
        }
    }

    fun saveUrlProfile(allowPlainHttp: Boolean = false) {
        val cleanName = nameState.text.toString().trim()
        if (saving || (!isNew && targetProfile == null)) return
        if (cleanName.isBlank()) {
            scope.launch { services.tipNotifier.show(nameRequiredMessage) }
            return
        }

        val trimmedUrl = urlState.text.toString().trim()
        val cleanUserAgent = userAgentState.text.toString().trim().ifBlank { app.DefaultMihomoProfileUserAgent }
        val cleanAgeSecretKey = ageSecretKeyState.text.toString().trim()
        val cleanInterval = updateIntervalState.text.toString().trim()
        if (!isValidSubscriptionIntervalInput(cleanInterval)) return
        if (!trimmedUrl.isValidManualSubscriptionUrl()) {
            scope.launch { services.tipNotifier.show(invalidUrlMessage) }
            return
        }
        if (!cleanAgeSecretKey.isValidAgeSecretKey()) {
            scope.launch { services.tipNotifier.show(invalidAgeSecretKeyMessage) }
            return
        }
        if (!allowPlainHttp && trimmedUrl.isPlainHttpSubscriptionUrl()) {
            showHttpSubscriptionWarning = true
            return
        }

        showHttpSubscriptionWarning = false
        val urlChanged = targetProfile?.url != trimmedUrl
        val cleanOverrideScriptId = selectedOverrideScriptId()
        val remoteOptionsChanged = targetProfile == null ||
            urlChanged ||
            targetProfile.userAgent != cleanUserAgent ||
            targetProfile.ageSecretKey != cleanAgeSecretKey ||
            targetProfile.updateViaProxy != updateViaProxy
        val desiredProfile = if (targetProfile != null) {
            targetProfile.copy(
                name = cleanName,
                type = MihomoProfileType.Url,
                url = trimmedUrl,
                userAgent = cleanUserAgent,
                updateInterval = cleanInterval,
                updateViaProxy = updateViaProxy,
                ageSecretKey = cleanAgeSecretKey,
                overrideScriptId = cleanOverrideScriptId,
                disableOverrides = disableOverrides,
            )
        } else {
            MihomoProfileState(
                id = DefaultMihomoProfileId,
                name = cleanName,
                type = MihomoProfileType.Url,
                url = trimmedUrl,
                userAgent = cleanUserAgent,
                updateInterval = cleanInterval,
                updateViaProxy = updateViaProxy,
                ageSecretKey = cleanAgeSecretKey,
                overrideScriptId = cleanOverrideScriptId,
                disableOverrides = disableOverrides,
            )
        }
        launchSave(
            MihomoProfileSaveDraft(
                desiredProfile = desiredProfile,
                originalProfile = targetProfile,
                remoteOptionsChanged = remoteOptionsChanged,
            ),
        )
    }

    fun onSave() {
        if (profileType == MihomoProfileType.Url) {
            saveUrlProfile()
            return
        }

        val cleanName = nameState.text.toString().trim()
        if (saving || (!isNew && targetProfile == null)) return
        if (cleanName.isBlank()) {
            scope.launch { services.tipNotifier.show(nameRequiredMessage) }
            return
        }
        val profileSnapshot = targetProfile
        val contentText = contentEditorState.snapshotText()
        val cleanOverrideScriptId = selectedOverrideScriptId()
        val contentChanged = profileSnapshot == null || profileSnapshot.contentSha256 != contentText.sha256Hex()
        val desiredProfile = profileSnapshot?.copy(
            name = cleanName,
            type = MihomoProfileType.File,
            url = "",
            overrideScriptId = cleanOverrideScriptId,
            disableOverrides = disableOverrides,
        ) ?: MihomoProfileState(
            id = DefaultMihomoProfileId,
            name = cleanName,
            type = MihomoProfileType.File,
            overrideScriptId = cleanOverrideScriptId,
            disableOverrides = disableOverrides,
        )
        showFileProperties = false
        launchSave(
            MihomoProfileSaveDraft(
                desiredProfile = desiredProfile,
                originalProfile = profileSnapshot,
                localContent = contentText,
                contentChanged = contentChanged,
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        enabled = !saving,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = ::onSave,
                        enabled = !saving && (profileType != MihomoProfileType.Url || updateIntervalValid),
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

        if (!isNew && targetProfile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.mihomo_configuration_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            key(targetProfile?.id, profileType) {
                val baseModifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)

                Column(
                    modifier = if (profileType == MihomoProfileType.Url) {
                        baseModifier.verticalScroll(rememberScrollState())
                    } else {
                        baseModifier
                    },
                ) {
                    if (profileType == MihomoProfileType.Url) {
                        OutlinedTextField(
                            state = nameState,
                            enabled = !saving,
                            label = { Text(stringResource(R.string.mihomo_configuration_name)) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        )
                        UrlProfileFields(
                            enabled = !saving,
                            urlState = urlState,
                            userAgentState = userAgentState,
                            ageSecretKeyState = ageSecretKeyState,
                            updateIntervalState = updateIntervalState,
                            updateViaProxy = updateViaProxy,
                            onUpdateViaProxyChange = { updateViaProxy = it },
                            overrideScriptOptions = overrideScriptOptions,
                            selectedOverrideScriptIndex = selectedOverrideScriptIndex,
                            onSelectedOverrideScriptIndexChange = { index ->
                                overrideScriptId = if (index == 0) {
                                    DefaultMihomoOverrideScriptId
                                } else {
                                    appState.mihomoOverrideScripts.getOrNull(index - 1)?.id
                                        ?: DefaultMihomoOverrideScriptId
                                }
                            },
                            advancedExpanded = showAdvancedOptions,
                            onAdvancedExpandedChange = { showAdvancedOptions = it },
                            disableOverrides = disableOverrides,
                            rawReadiness = rawCheck?.readiness,
                            rawSnapshot = rawParseResult?.snapshot,
                            runMode = appState.runMode,
                            onDisableOverridesChange = { enabled ->
                                if (enabled) showRawModeConfirmation = true else disableOverrides = false
                            },
                        )
                    } else {
                        Surface(
                            onClick = { showFileProperties = true },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.mihomo_configuration_properties),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = nameState.text.toString().ifBlank {
                                            stringResource(R.string.mihomo_configuration_name)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            }
                        }
                        YamlCodeEditor(
                            label = stringResource(R.string.mihomo_configuration_content),
                            state = contentEditorState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .imePadding(),
                            readOnly = saving,
                        )
                    }
                }
            }
        }
        FileProfilePropertiesSheet(
            show = showFileProperties && profileType == MihomoProfileType.File,
            nameState = nameState,
            overrideScriptOptions = overrideScriptOptions,
            selectedOverrideScriptIndex = selectedOverrideScriptIndex,
            disableOverrides = disableOverrides,
            rawReadiness = rawCheck?.readiness,
            rawSnapshot = rawParseResult?.snapshot,
            runMode = appState.runMode,
            onDisableOverridesChange = { enabled ->
                if (enabled) showRawModeConfirmation = true else disableOverrides = false
            },
            onSelectedOverrideScriptIndexChange = { index ->
                overrideScriptId = if (index == 0) {
                    DefaultMihomoOverrideScriptId
                } else {
                    appState.mihomoOverrideScripts.getOrNull(index - 1)?.id
                        ?: DefaultMihomoOverrideScriptId
                }
            },
            onDismissRequest = { showFileProperties = false },
        )
        HttpSubscriptionWarningDialog(
            show = showHttpSubscriptionWarning,
            onDismissRequest = { showHttpSubscriptionWarning = false },
            onConfirm = { saveUrlProfile(allowPlainHttp = true) },
        )
        MihomoProfileSyncProgressDialog(
            stage = syncStage.takeIf { saving },
            onCancel = { saveJob?.cancel() },
        )
        failedSave?.let { failed ->
            MihomoProfileSyncFailureDialog(
                failure = failed.failure,
                onRetry = { launchSave(failed.draft) },
                onSaveAnyway = ::saveFailedDraft,
                onCancel = { failedSave = null },
            )
        }
        RawModeConfirmationDialog(
            show = showRawModeConfirmation,
            onDismissRequest = { showRawModeConfirmation = false },
            onConfirm = {
                disableOverrides = true
                showRawModeConfirmation = false
            },
        )
        RestartRequiredDialog(
            show = showRestartRequired,
            restarting = restartInProgress,
            onRestartNow = ::restartWithSavedConfiguration,
            onLater = {
                showRestartRequired = false
                navigator.pop()
            },
        )
    }
}

private data class FailedMihomoProfileSave(
    val draft: MihomoProfileSaveDraft,
    val failure: MihomoProfileSavePreparation.Failure,
)

@Composable
internal fun MihomoProfileSyncProgressDialog(
    stage: MihomoProfileSyncStage?,
    onCancel: () -> Unit,
    title: String? = null,
    profileName: String? = null,
    progress: Float? = null,
    progressLabel: String? = null,
) {
    if (stage == null) return
    val message = stringResource(
        when (stage) {
            MihomoProfileSyncStage.Downloading -> R.string.mihomo_configuration_save_sync_downloading
            MihomoProfileSyncStage.Decrypting -> R.string.mihomo_configuration_save_sync_decrypting
            MihomoProfileSyncStage.PreparingProviders -> R.string.mihomo_configuration_save_sync_preparing
            MihomoProfileSyncStage.Verifying -> R.string.mihomo_configuration_save_sync_verifying
        },
    )
    AlertDialog(
        onDismissRequest = {},
        icon = {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
            )
        },
        title = {
            Text(title ?: stringResource(R.string.mihomo_configuration_save_sync_in_progress_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                profileName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(message)
                progress?.let { value ->
                    LinearProgressIndicator(
                        progress = { value.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                progressLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            AsteriskActionButton(
                text = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Close,
                onClick = onCancel,
            )
        },
    )
}

@Composable
internal fun MihomoProfileSyncFailureDialog(
    failure: MihomoProfileSavePreparation.Failure,
    onRetry: () -> Unit,
    onSaveAnyway: () -> Unit,
    onCancel: () -> Unit,
) {
    val stage = stringResource(
        when (failure.stage) {
            MihomoProfileSyncStage.Downloading -> R.string.mihomo_configuration_save_sync_stage_download
            MihomoProfileSyncStage.Decrypting -> R.string.mihomo_configuration_save_sync_stage_decrypt
            MihomoProfileSyncStage.PreparingProviders -> R.string.mihomo_configuration_save_sync_stage_preparing
            MihomoProfileSyncStage.Verifying -> R.string.mihomo_configuration_save_sync_stage_verifying
        },
    )
    val detail = failure.error.localizedMessage
        ?.takeIf(String::isNotBlank)
        ?: failure.error::class.java.simpleName
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
        title = { Text(stringResource(R.string.mihomo_configuration_save_sync_failed_title)) },
        text = {
            Text(stringResource(R.string.mihomo_configuration_save_sync_failed_message, stage, detail))
        },
        dismissButton = {
            Row {
                AsteriskActionButton(
                    text = stringResource(R.string.common_cancel),
                    icon = Icons.Rounded.Close,
                    onClick = onCancel,
                )
                AsteriskActionButton(
                    text = stringResource(R.string.mihomo_configuration_save_anyway),
                    icon = Icons.Rounded.Save,
                    onClick = onSaveAnyway,
                )
            }
        },
        confirmButton = {
            AsteriskActionButton(
                text = stringResource(R.string.common_retry),
                icon = Icons.Rounded.Refresh,
                onClick = onRetry,
            )
        },
    )
}

@Composable
internal fun RestartRequiredDialog(
    show: Boolean,
    restarting: Boolean,
    onRestartNow: () -> Unit,
    onLater: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = { if (!restarting) onLater() },
        icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
        title = { Text(stringResource(R.string.mihomo_configuration_restart_required)) },
        text = { Text(stringResource(R.string.mihomo_configuration_restart_required_message)) },
        dismissButton = {
            AsteriskActionButton(
                text = stringResource(R.string.mihomo_configuration_restart_later),
                icon = Icons.Rounded.History,
                onClick = onLater,
                enabled = !restarting,
            )
        },
        confirmButton = {
            AsteriskActionButton(
                text = stringResource(R.string.mihomo_configuration_restart_now),
                icon = Icons.Rounded.Refresh,
                onClick = onRestartNow,
                enabled = !restarting,
            )
        },
    )
}

@Composable
private fun HttpSubscriptionWarningDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.mihomo_configuration_http_subscription_warning_title)) },
        text = { Text(stringResource(R.string.mihomo_configuration_http_subscription_warning_message)) },
        dismissButton = {
            AsteriskActionButton(
                text = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
        confirmButton = {
            AsteriskActionButton(
                text = stringResource(R.string.mihomo_configuration_http_subscription_warning_confirm),
                icon = Icons.Rounded.Check,
                onClick = onConfirm,
            )
        },
    )
}

@Composable
private fun ColumnScope.UrlProfileFields(
    enabled: Boolean,
    urlState: TextFieldState,
    userAgentState: TextFieldState,
    ageSecretKeyState: TextFieldState,
    updateIntervalState: TextFieldState,
    updateViaProxy: Boolean,
    onUpdateViaProxyChange: (Boolean) -> Unit,
    overrideScriptOptions: List<String>,
    selectedOverrideScriptIndex: Int,
    onSelectedOverrideScriptIndexChange: (Int) -> Unit,
    advancedExpanded: Boolean,
    onAdvancedExpandedChange: (Boolean) -> Unit,
    disableOverrides: Boolean,
    rawReadiness: RawConfigReadiness?,
    rawSnapshot: MihomoRawConfigSnapshot?,
    runMode: Int,
    onDisableOverridesChange: (Boolean) -> Unit,
) {
    OutlinedTextField(
        state = urlState,
        enabled = enabled,
        label = { Text(stringResource(R.string.mihomo_configuration_url)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
    OutlinedTextField(
        state = updateIntervalState,
        enabled = enabled,
        label = { Text(stringResource(R.string.mihomo_configuration_update_interval)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation.byValue { _, proposed ->
            sanitizeSubscriptionIntervalInput(proposed.toString())
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.mihomo_configuration_update_via_proxy),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = updateViaProxy,
                onCheckedChange = onUpdateViaProxyChange,
                enabled = enabled,
            )
        }
    }
    TextButton(
        onClick = { onAdvancedExpandedChange(!advancedExpanded) },
        enabled = enabled,
        modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
    ) {
        AsteriskExpansionIndicator(expanded = advancedExpanded)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.settings_advanced))
    }
    AnimatedVisibility(
        visible = advancedExpanded,
        enter = AsteriskMotion.contentEnter(),
        exit = AsteriskMotion.contentExit(),
    ) {
        Column {
            OutlinedTextField(
                state = userAgentState,
                enabled = enabled,
                label = { Text(stringResource(R.string.mihomo_configuration_user_agent)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            OutlinedTextField(
                state = ageSecretKeyState,
                enabled = enabled,
                label = { Text(stringResource(R.string.mihomo_configuration_age_secret_key)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            ProfileOverrideScriptSelector(
                options = overrideScriptOptions,
                selectedIndex = selectedOverrideScriptIndex,
                onSelectedIndexChange = onSelectedOverrideScriptIndexChange,
                readOnly = disableOverrides || !enabled,
            )
            RawConfigModeControl(
                enabled = disableOverrides,
                readiness = rawReadiness,
                snapshot = rawSnapshot,
                runMode = runMode,
                onEnabledChange = { value -> if (enabled) onDisableOverridesChange(value) },
            )
        }
    }
}

@Composable
private fun FileProfilePropertiesSheet(
    show: Boolean,
    nameState: TextFieldState,
    overrideScriptOptions: List<String>,
    selectedOverrideScriptIndex: Int,
    disableOverrides: Boolean,
    rawReadiness: RawConfigReadiness?,
    rawSnapshot: MihomoRawConfigSnapshot?,
    runMode: Int,
    onDisableOverridesChange: (Boolean) -> Unit,
    onSelectedOverrideScriptIndexChange: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var advancedExpanded by remember { mutableStateOf(false) }
    AsteriskModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.mihomo_configuration_properties),
        endAction = {
            AsteriskActionButton(
                text = stringResource(R.string.common_complete),
                icon = Icons.Rounded.Check,
                onClick = onDismissRequest,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            OutlinedTextField(
                state = nameState,
                label = { Text(stringResource(R.string.mihomo_configuration_name)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            TextButton(
                onClick = { advancedExpanded = !advancedExpanded },
                modifier = Modifier.align(Alignment.End),
            ) {
                AsteriskExpansionIndicator(expanded = advancedExpanded)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_advanced))
            }
            AnimatedVisibility(
                visible = advancedExpanded,
                enter = AsteriskMotion.contentEnter(),
                exit = AsteriskMotion.contentExit(),
            ) {
                Column {
                    ProfileOverrideScriptSelector(
                        options = overrideScriptOptions,
                        selectedIndex = selectedOverrideScriptIndex,
                        onSelectedIndexChange = onSelectedOverrideScriptIndexChange,
                        readOnly = disableOverrides,
                    )
                    RawConfigModeControl(
                        enabled = disableOverrides,
                        readiness = rawReadiness,
                        snapshot = rawSnapshot,
                        runMode = runMode,
                        onEnabledChange = onDisableOverridesChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileOverrideScriptSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    readOnly: Boolean = false,
) {
    if (readOnly) {
        AsteriskListRow(
            title = stringResource(R.string.mihomo_configuration_override_script),
            summary = stringResource(R.string.mihomo_configuration_override_script_stopped),
            leadingIcon = Icons.Rounded.Lock,
            enabled = false,
        )
        return
    }
    SettingsDropdownRow(
        title = stringResource(R.string.mihomo_configuration_override_script),
        icon = Icons.Rounded.Code,
        items = options,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

@Composable
private fun RawConfigModeControl(
    enabled: Boolean,
    readiness: RawConfigReadiness?,
    snapshot: MihomoRawConfigSnapshot?,
    runMode: Int,
    onEnabledChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = MaterialTheme.shapes.large,
        color = if (enabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.mihomo_configuration_raw_mode),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.mihomo_configuration_raw_mode_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                Text(
                    text = stringResource(
                        when (readiness) {
                            RawConfigReadiness.Ready -> R.string.mihomo_configuration_raw_mode_ready
                            RawConfigReadiness.Degraded -> R.string.mihomo_configuration_raw_mode_degraded
                            RawConfigReadiness.Blocked -> R.string.mihomo_configuration_raw_mode_blocked
                            null -> R.string.mihomo_configuration_raw_mode_unchecked
                        },
                    ),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = when (readiness) {
                        RawConfigReadiness.Blocked -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                RawCapabilityRow(
                    title = stringResource(R.string.mihomo_configuration_raw_api),
                    value = snapshot?.api?.value?.control?.let { control ->
                        "${control.scheme.uppercase()} · ${control.host}:${control.port}"
                    } ?: stringResource(R.string.mihomo_configuration_raw_unavailable),
                    source = snapshot?.api?.path ?: "external-controller",
                )
                RawCapabilityRow(
                    title = stringResource(R.string.mihomo_configuration_raw_dns_hijack),
                    value = if (snapshot?.dnsHijack?.value?.proven == true) {
                        stringResource(R.string.mihomo_configuration_raw_configured)
                    } else {
                        stringResource(R.string.mihomo_configuration_raw_unavailable)
                    },
                    source = snapshot?.dnsHijack?.path ?: "dns.enable + rules",
                )
                RawCapabilityRow(
                    title = stringResource(R.string.mihomo_configuration_raw_run_mode),
                    value = rawRunModeLabel(runMode),
                    source = when (runMode) {
                        RunModeTproxy -> snapshot?.tproxyPort?.path
                        RunModeTun -> snapshot?.tunInbound?.path
                        RunModeTun2Socks, RunModeBpf2Socks -> snapshot?.socksInbound?.path
                        else -> "Android VpnService"
                    }.orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun RawCapabilityRow(
    title: String,
    value: String,
    source: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            if (source.isNotBlank()) {
                Text(
                    source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun rawRunModeLabel(runMode: Int): String = stringResource(
    when (runMode) {
        RunModeTproxy -> R.string.settings_run_mode_tproxy
        RunModeTun -> R.string.settings_run_mode_tun
        RunModeTun2Socks -> R.string.settings_run_mode_tun2socks
        RunModeBpf2Socks -> R.string.settings_run_mode_bpf2socks
        else -> R.string.settings_run_mode_vpn_service
    },
)

@Composable
private fun RawModeConfirmationDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.mihomo_configuration_raw_mode_confirm_title)) },
        text = { Text(stringResource(R.string.mihomo_configuration_raw_mode_confirm_message)) },
        dismissButton = {
            AsteriskActionButton(
                text = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
        confirmButton = {
            AsteriskActionButton(
                text = stringResource(R.string.mihomo_configuration_raw_mode_confirm),
                icon = Icons.Rounded.Check,
                onClick = onConfirm,
            )
        },
    )
}

private fun String.isValidAgeSecretKey(): Boolean {
    return isBlank() || Clash.veritySecretKeys(this)
}

private const val RawConfigValidationDebounceMillis = 350L
