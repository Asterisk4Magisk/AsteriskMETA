// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import features.subscription.formatSubscriptionRequestHeaders
import features.subscription.defaultSubscriptionRequestHeaders
import data.AppSettingsPreferences
import features.subscription.parseSubscriptionRequestHeaders
import features.subscription.withoutImplicitSubscriptionHeaders
import ui.layout.pageReadableWidth
import ui.layout.EditorListPane
import ui.layout.EditorListItem
import ui.components.AsteriskCancelButton
import ui.layout.rememberCodeEditorFocusState
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
import ui.components.AsteriskScaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import ui.components.AsteriskTopAppBar
import androidx.compose.runtime.Composable
import ui.components.WarningConfirmDialog
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import app.MihomoRuleOverrideState
import app.effectiveEntryCount
import app.modes.MihomoModeDirect
import app.modes.MihomoModeGlobal
import app.runtimeProjection
import engine.mihomo.MihomoProfileFactory
import androidx.compose.ui.platform.LocalContext
import app.R
import app.collectAppState
import app.modes.RunModeBpf2Socks
import app.modes.RunModeTproxy
import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import engine.mihomo.binding.MihomoBridge as Clash
import engine.mihomo.raw.MihomoRawConfigParseResult
import engine.mihomo.raw.MihomoRawConfigParser
import engine.mihomo.raw.MihomoRawConfigSnapshot
import engine.mihomo.raw.RawConfigReadiness
import engine.mihomo.raw.check
import engine.mihomo.raw.provesDnsHijack
import utils.sha256Hex
import features.settings.SettingsActionRow
import features.settings.SettingsDropdownRow
import features.subscription.isPlainHttpSubscriptionUrl
import features.subscription.isValidSubscriptionIntervalInput
import features.subscription.isValidManualSubscriptionUrl
import features.subscription.sanitizeSubscriptionIntervalInput
import features.subscription.usecase.MihomoProfileSyncStage
import features.subscription.usecase.toSubscriptionFetchOptions
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.components.AsteriskActionButton
import ui.components.AsteriskCodeActionButton
import ui.components.AsteriskCodeSearchHost
import ui.components.rememberAsteriskCodeSearchState
import ui.components.AsteriskExpansionIndicator
import ui.components.AsteriskModalBottomSheet
import ui.components.AsteriskNoWrapTextField
import ui.layout.pageContentPaddingWithCutout
import ui.theme.AsteriskMotion
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import ui.icons.AsteriskIcons as Icons
import ui.theme.AsteriskShapeTokens

@Composable
fun MihomoProfileEditPage(
    padding: PaddingValues,
    profileId: Int,
    type: Int,
    editContent: Boolean = false,
) {
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var activeProfileId by rememberSaveable(profileId) { mutableIntStateOf(profileId) }
    val isNew = activeProfileId <= 0
    val targetProfile = remember(appState.mihomoProfiles, activeProfileId) {
        appState.mihomoProfiles.firstOrNull { profile -> profile.id == activeProfileId }
    }
    val profileType = targetProfile?.type ?: MihomoProfileType.fromStorageValue(type)
    // A URL profile's stored configuration can be edited like a local file; the next
    // subscription update replaces it with the downloaded content.
    val editsUrlContent = editContent && !isNew && profileType == MihomoProfileType.Url
    val editsContent = profileType == MihomoProfileType.File || editsUrlContent
    val title = when {
        editsUrlContent -> stringResource(R.string.mihomo_profile_edit_url_content)
        isNew && profileType == MihomoProfileType.Url -> stringResource(R.string.mihomo_profile_add_url)
        profileType == MihomoProfileType.Url -> stringResource(R.string.mihomo_profile_edit_url)
        else -> stringResource(R.string.mihomo_profile_edit_file)
    }
    val syncSuccessMessage = stringResource(R.string.mihomo_profile_save_sync_success)
    val syncFailedSavedMessage = stringResource(R.string.mihomo_profile_save_sync_failed_saved)
    val saveFailedMessage = stringResource(R.string.mihomo_profile_save_failed)
    val profileSaveUseCase = rememberMihomoProfileSaveUseCase()

    var saving by remember(activeProfileId) { mutableStateOf(false) }
    var syncStage by remember(activeProfileId) { mutableStateOf<MihomoProfileSyncStage?>(null) }
    var saveJob by remember(activeProfileId) { mutableStateOf<Job?>(null) }
    var failedSave by remember(activeProfileId) { mutableStateOf<FailedMihomoProfileSave?>(null) }
    var pendingSwitchId by remember(activeProfileId) { mutableStateOf<Int?>(null) }
    val nameState = key(activeProfileId) { rememberTextFieldState(initialText = targetProfile?.name.orEmpty()) }
    val urlState = key(activeProfileId) { rememberTextFieldState(initialText = targetProfile?.url.orEmpty()) }
    val initialUserAgent = targetProfile?.userAgent ?: app.DefaultMihomoProfileUserAgent
    val userAgentState = key(activeProfileId) { rememberTextFieldState(initialText = initialUserAgent) }
    val appContext = LocalContext.current.applicationContext
    val defaultRequestHeaders = remember(appContext) {
        defaultSubscriptionRequestHeaders(AppSettingsPreferences(appContext).getOrCreateSubscriptionHwid())
    }
    val initialRequestHeaders = remember(activeProfileId, defaultRequestHeaders) {
        formatSubscriptionRequestHeaders(targetProfile?.requestHeaders ?: defaultRequestHeaders)
    }
    val requestHeadersState = key(activeProfileId) { rememberTextFieldState(initialText = initialRequestHeaders) }
    val requestHeadersText = requestHeadersState.text.toString()
    val requestHeaders = remember(requestHeadersText) { parseSubscriptionRequestHeaders(requestHeadersText) }
    val ageSecretKeyState = key(activeProfileId) {
        rememberTextFieldState(initialText = targetProfile?.ageSecretKey.orEmpty())
    }
    val initialUpdateInterval = targetProfile?.updateInterval ?: DefaultMihomoProfileUpdateInterval
    val updateIntervalState = key(activeProfileId) { rememberTextFieldState(initialText = initialUpdateInterval) }
    val updateIntervalValid = isValidSubscriptionIntervalInput(updateIntervalState.text.toString())
    var updateViaProxy by remember(activeProfileId) {
        mutableStateOf(targetProfile?.updateViaProxy ?: false)
    }
    val contentEditorState = remember(activeProfileId) {
        MihomoCodeEditorState()
    }
    var overrideScriptId by remember(activeProfileId) {
        mutableIntStateOf(targetProfile?.overrideScriptId ?: DefaultMihomoOverrideScriptId)
    }
    var disableOverrides by remember(activeProfileId) {
        mutableStateOf(targetProfile?.disableOverrides ?: false)
    }
    // The rule override is edited on a copy and only stored by this page's Save.
    var ruleOverrideDraft by remember(activeProfileId) {
        mutableStateOf(targetProfile?.ruleOverride ?: MihomoRuleOverrideState())
    }
    // The source configuration the open rule override editor works on; `null` while it is closed.
    var ruleOverrideEditorSource by remember(activeProfileId) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var showRawModeConfirmation by remember(activeProfileId) { mutableStateOf(false) }
    var rawContentForCheck by remember(activeProfileId) { mutableStateOf("") }
    val selectedOverrideScript = appState.mihomoOverrideScripts.firstOrNull { script ->
        script.id == overrideScriptId
    }
    val overrideScriptOptions = listOf(stringResource(R.string.mihomo_profile_override_script_none)) +
        appState.mihomoOverrideScripts.map { script -> script.name }
    val selectedOverrideScriptIndex = selectedOverrideScript
        ?.let { script -> appState.mihomoOverrideScripts.indexOfFirst { it.id == script.id } + 1 }
        ?: 0
    val nameRequiredMessage = stringResource(R.string.mihomo_profile_name_required)
    val invalidUrlMessage = stringResource(R.string.mihomo_profile_invalid_subscription_url)
    val invalidAgeSecretKeyMessage = stringResource(R.string.mihomo_profile_invalid_age_secret_key)
    var showHttpSubscriptionWarning by remember(activeProfileId) { mutableStateOf(false) }
    var showAdvancedOptions by remember(activeProfileId) { mutableStateOf(false) }
    var showFileProperties by remember(activeProfileId) { mutableStateOf(false) }
    var rawParseResult by remember(activeProfileId) {
        mutableStateOf<MihomoRawConfigParseResult?>(null)
    }

    val codeSearch = key(activeProfileId) { rememberAsteriskCodeSearchState() }
    val editorFocus = key(activeProfileId) {
        rememberCodeEditorFocusState(
            editorFocused = contentEditorState.isFocused,
            clearEditorFocus = codeSearch::clearEditorFocus,
            searchActive = codeSearch.active,
            backEnabled = !saving,
        )
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
        if (editsContent) {
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
        if (editsContent) {
            delay(RawConfigValidationDebounceMillis.milliseconds)
        }
        val content = if (editsContent) {
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

    fun saveProfile(profile: MihomoProfileState, isNewProfile: Boolean): MihomoProfileState =
        saveMihomoProfileState(updateAppState, profile, isNewProfile)

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

    fun openRuleOverrideEditor() {
        // A local profile is previewed from the text being edited, a URL profile from the
        // content of its last sync.
        ruleOverrideEditorSource = if (editsContent) contentEditorState.snapshotText() else rawContentForCheck
    }

    fun launchSave(draft: MihomoProfileSaveDraft) {
        if (saving) return
        failedSave = null
        if (draft.changesOnlyRuleOverride()) {
            saveMihomoProfileRuleOverride(updateAppState, draft.desiredProfile.id, draft.desiredProfile.ruleOverride)
            navigator.pop()
            return
        }
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
                        saveProfile(committed, draft.originalProfile == null)
                        if (preparation.synchronized) {
                            services.tipNotifier.show(syncSuccessMessage)
                        }
                        // A running core picks the saved content up through the application's
                        // automatic apply; there is nothing left to ask here.
                        navigator.pop()
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
        // A blank name is auto-assigned after the subscription is fetched.
        val cleanName = nameState.text.toString().trim()
        if (saving || (!isNew && targetProfile == null)) return

        val trimmedUrl = urlState.text.toString().trim()
        val cleanUserAgent = userAgentState.text.toString().trim().ifBlank { app.DefaultMihomoProfileUserAgent }
        val parsedHeaders = requestHeaders ?: return
        val cleanAgeSecretKey = ageSecretKeyState.text.toString().trim()
        val cleanInterval = updateIntervalState.text.toString().trim()
        if (!isValidSubscriptionIntervalInput(cleanInterval)) return
        if (!trimmedUrl.isValidManualSubscriptionUrl()) {
            scope.launch { services.tipNotifier.show(invalidUrlMessage) }
            return
        }
        if (!cleanAgeSecretKey.isValidAgeSecretKey(targetProfile?.ageSecretKey)) {
            scope.launch { services.tipNotifier.show(invalidAgeSecretKeyMessage) }
            return
        }
        if (!allowPlainHttp && trimmedUrl.isPlainHttpSubscriptionUrl()) {
            showHttpSubscriptionWarning = true
            return
        }

        showHttpSubscriptionWarning = false
        val storedRequestHeaders = parsedHeaders.withoutImplicitSubscriptionHeaders(
            targetProfile?.requestHeaders, defaultRequestHeaders,
        )
        val urlChanged = targetProfile?.url != trimmedUrl
        val cleanOverrideScriptId = selectedOverrideScriptId()
        val remoteOptionsChanged = targetProfile == null ||
            urlChanged ||
            targetProfile.userAgent != cleanUserAgent ||
            targetProfile.requestHeaders != storedRequestHeaders ||
            targetProfile.ageSecretKey != cleanAgeSecretKey ||
            targetProfile.updateViaProxy != updateViaProxy
        val desiredProfile = targetProfile?.copy(
            name = cleanName,
            type = MihomoProfileType.Url,
            url = trimmedUrl,
            userAgent = cleanUserAgent,
            requestHeaders = storedRequestHeaders,
            updateInterval = cleanInterval,
            updateViaProxy = updateViaProxy,
            ageSecretKey = cleanAgeSecretKey,
            overrideScriptId = cleanOverrideScriptId,
            disableOverrides = disableOverrides,
            ruleOverride = ruleOverrideDraft,
        )
            ?: MihomoProfileState(
                id = DefaultMihomoProfileId,
                name = cleanName,
                type = MihomoProfileType.Url,
                url = trimmedUrl,
                userAgent = cleanUserAgent,
                requestHeaders = storedRequestHeaders,
                updateInterval = cleanInterval,
                updateViaProxy = updateViaProxy,
                ageSecretKey = cleanAgeSecretKey,
                overrideScriptId = cleanOverrideScriptId,
                disableOverrides = disableOverrides,
                ruleOverride = ruleOverrideDraft,
            )
        launchSave(
            MihomoProfileSaveDraft(
                desiredProfile = desiredProfile,
                originalProfile = targetProfile,
                remoteOptionsChanged = remoteOptionsChanged,
                otherProfileNames = stateStore.state.value.mihomoProfiles
                    .filter { profile -> profile.id != targetProfile?.id }
                    .map { profile -> profile.name },
            ),
        )
    }

    fun saveUrlContent() {
        val profile = targetProfile ?: return
        if (saving) return
        val contentText = contentEditorState.snapshotText()
        launchSave(
            MihomoProfileSaveDraft(
                desiredProfile = profile,
                originalProfile = profile,
                localContent = contentText,
                contentChanged = profile.contentSha256 != contentText.sha256Hex(),
            ),
        )
    }

    fun onSave() {
        if (editsUrlContent) {
            saveUrlContent()
            return
        }
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
        val contentText = contentEditorState.snapshotText()
        val cleanOverrideScriptId = selectedOverrideScriptId()
        val contentChanged = targetProfile == null || targetProfile.contentSha256 != contentText.sha256Hex()
        val desiredProfile = targetProfile?.copy(
            name = cleanName,
            type = MihomoProfileType.File,
            url = "",
            overrideScriptId = cleanOverrideScriptId,
            disableOverrides = disableOverrides,
            ruleOverride = ruleOverrideDraft,
        ) ?: MihomoProfileState(
            id = DefaultMihomoProfileId,
            name = cleanName,
            type = MihomoProfileType.File,
            overrideScriptId = cleanOverrideScriptId,
            disableOverrides = disableOverrides,
            ruleOverride = ruleOverrideDraft,
        )
        showFileProperties = false
        launchSave(
            MihomoProfileSaveDraft(
                desiredProfile = desiredProfile,
                originalProfile = targetProfile,
                localContent = contentText,
                contentChanged = contentChanged,
            ),
        )
    }

    fun switchToProfile(targetId: Int) {
        if (stateStore.state.value.mihomoProfiles.none { it.id == targetId }) return
        codeSearch.close()
        contentEditorState.clearFocus()
        focusManager.clearFocus(force = true)
        pendingSwitchId = null
        activeProfileId = targetId
    }

    fun requestProfileSwitch(targetId: Int) {
        if (saving || targetId == activeProfileId) return
        val dirty = nameState.text.toString() != targetProfile?.name.orEmpty() ||
            urlState.text.toString() != targetProfile?.url.orEmpty() ||
            userAgentState.text.toString() != initialUserAgent ||
            requestHeadersState.text.toString() != initialRequestHeaders ||
            ageSecretKeyState.text.toString() != targetProfile?.ageSecretKey.orEmpty() ||
            updateIntervalState.text.toString() != initialUpdateInterval ||
            updateViaProxy != (targetProfile?.updateViaProxy ?: false) ||
            overrideScriptId != (targetProfile?.overrideScriptId ?: DefaultMihomoOverrideScriptId) ||
            disableOverrides != (targetProfile?.disableOverrides ?: false) ||
            ruleOverrideDraft != (targetProfile?.ruleOverride ?: MihomoRuleOverrideState()) ||
            (editsContent && contentEditorState.snapshotText() != rawContentForCheck)
        if (dirty) {
            pendingSwitchId = targetId
        } else {
            switchToProfile(targetId)
        }
    }

    val currentProfileSwitch by rememberUpdatedState { targetId: Int -> requestProfileSwitch(targetId) }

    AsteriskCodeSearchHost(
        state = codeSearch,
        modifier = Modifier.fillMaxSize(),
        enabled = editsContent && !saving && (isNew || targetProfile != null),
        editorTools = { searchButton ->
            searchButton()
            MihomoCodeFormatButton(contentEditorState, MihomoCodeLanguage.Yaml)
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
                            enabled = !saving,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                    actions = {
                        AsteriskCodeActionButton(
                            icon = Icons.Rounded.Save,
                            label = stringResource(R.string.common_save),
                            onClick = ::onSave,
                            enabled = !saving && (profileType != MihomoProfileType.Url ||
                                (updateIntervalValid && (editsUrlContent || requestHeaders != null))),
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

            val profileEntries = remember(appState.mihomoProfiles) {
                appState.mihomoProfiles.map { EditorListItem(it.id, it.name) }
            }
            EditorListPane(
                title = stringResource(R.string.mihomo_profiles_title),
                entries = profileEntries,
                selectedId = targetProfile?.id,
                padding = contentPadding,
                show = !editorFocus.expanded,
                onSelect = { targetId -> currentProfileSwitch(targetId) },
                selectionEnabled = !saving,
            ) {
                if (!isNew && targetProfile == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.mihomo_profile_missing),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    key(targetProfile?.id, profileType, editsContent) {
                        val scrollModifier = if (!editsContent) {
                            Modifier.pageReadableWidth(maxWidth = 800.dp).verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(scrollModifier)
                                .padding(contentPadding)
                                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
                        ) {
                            if (!editsContent) {
                                OutlinedTextField(
                                    shape = AsteriskShapeTokens.InnerContainer,
                                    state = nameState,
                                    enabled = !saving,
                                    label = { Text(stringResource(R.string.mihomo_profile_name)) },
                                    placeholder = { Text(stringResource(R.string.mihomo_profile_name_placeholder)) },
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                )
                                UrlProfileFields(
                                    enabled = !saving,
                                    urlState = urlState,
                                    userAgentState = userAgentState,
                                    requestHeadersState = requestHeadersState,
                                    invalidRequestHeaders = requestHeaders == null,
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
                                    ruleOverride = ruleOverrideDraft,
                                    onOpenRuleOverride = ::openRuleOverrideEditor,
                                    disableOverrides = disableOverrides,
                                    rawReadiness = rawCheck?.readiness,
                                    rawSnapshot = rawParseResult?.snapshot,
                                    runMode = appState.runMode,
                                    vpnUsesHev = appState.enableVpnHevTun,
                                    onDisableOverridesChange = { enabled ->
                                        if (enabled) showRawModeConfirmation = true else disableOverrides = false
                                    },
                                )
                            } else {
                                AnimatedVisibility(
                                    visible = !editorFocus.expanded,
                                    enter = AsteriskMotion.contentEnter(),
                                    exit = AsteriskMotion.contentExit(),
                                ) {
                                    Column {
                                        if (editsUrlContent) Text(
                                            text = stringResource(R.string.mihomo_profile_edit_url_content_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        ) else Surface(
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
                                                        text = stringResource(R.string.mihomo_profile_properties),
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                    Text(
                                                        text = nameState.text.toString().ifBlank {
                                                            stringResource(R.string.mihomo_profile_name)
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
                                    }
                                }
                                YamlCodeEditor(
                                    state = contentEditorState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .imePadding()
                                        .then(editorModifier),
                                    readOnly = saving,
                                    searchState = codeSearch,
                                    showFormatButton = false,
                                )
                            }
                        }
                    }
                }
            }
            FileProfilePropertiesSheet(
                show = showFileProperties && profileType == MihomoProfileType.File,
                nameState = nameState,
                overrideScriptOptions = overrideScriptOptions,
                selectedOverrideScriptIndex = selectedOverrideScriptIndex,
                ruleOverride = ruleOverrideDraft,
                onOpenRuleOverride = ::openRuleOverrideEditor,
                disableOverrides = disableOverrides,
                rawReadiness = rawCheck?.readiness,
                rawSnapshot = rawParseResult?.snapshot,
                runMode = appState.runMode,
                vpnUsesHev = appState.enableVpnHevTun,
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
            ruleOverrideEditorSource?.let { sourceContent ->
                MihomoRuleOverrideEditorDialog(
                    initial = ruleOverrideDraft,
                    sourceContent = sourceContent,
                    sourceIsCachedSubscription = !editsContent,
                    ruleModeActive = appState.mihomoMode != MihomoModeGlobal && appState.mihomoMode != MihomoModeDirect,
                    compilePreview = { draft ->
                        // Everything the compile reads is captured here; nothing is written back.
                        val profile = (targetProfile ?: MihomoProfileState(id = DefaultMihomoProfileId, name = "")).copy(
                            overrideScriptId = selectedOverrideScriptId(),
                            disableOverrides = false,
                            ruleOverride = draft,
                        )
                        MihomoProfileFactory.compilePreview(context.applicationContext, stateStore.state.value, profile, sourceContent)
                    },
                    onConfirm = { edited ->
                        ruleOverrideDraft = edited
                        ruleOverrideEditorSource = null
                    },
                    onDismissRequest = { ruleOverrideEditorSource = null },
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
        }
    }
    pendingSwitchId?.let { targetId ->
        WarningConfirmDialog(
            show = true,
            title = stringResource(R.string.editor_switch_unsaved_title),
            summary = stringResource(R.string.editor_switch_unsaved_message),
            dismissText = stringResource(R.string.editor_switch_cancel),
            confirmText = stringResource(R.string.editor_switch_discard),
            onDismissRequest = { pendingSwitchId = null },
            onConfirm = {
                pendingSwitchId = null
                switchToProfile(targetId)
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
            MihomoProfileSyncStage.Downloading -> R.string.mihomo_profile_save_sync_downloading
            MihomoProfileSyncStage.Decrypting -> R.string.mihomo_profile_save_sync_decrypting
            MihomoProfileSyncStage.PreparingProviders -> R.string.mihomo_profile_save_sync_preparing
            MihomoProfileSyncStage.Verifying -> R.string.mihomo_profile_save_sync_verifying
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
            Text(title ?: stringResource(R.string.mihomo_profile_save_sync_in_progress_title))
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
            AsteriskCancelButton(
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
            MihomoProfileSyncStage.Downloading -> R.string.mihomo_profile_save_sync_stage_download
            MihomoProfileSyncStage.Decrypting -> R.string.mihomo_profile_save_sync_stage_decrypt
            MihomoProfileSyncStage.PreparingProviders -> R.string.mihomo_profile_save_sync_stage_preparing
            MihomoProfileSyncStage.Verifying -> R.string.mihomo_profile_save_sync_stage_verifying
        },
    )
    val detail = failure.error.localizedMessage
        ?.takeIf(String::isNotBlank)
        ?: failure.error::class.java.simpleName
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
        title = { Text(stringResource(R.string.mihomo_profile_save_sync_failed_title)) },
        text = {
            Text(stringResource(R.string.mihomo_profile_save_sync_failed_message, stage, detail))
        },
        dismissButton = {
            Row {
                AsteriskCancelButton(
                    onClick = onCancel,
                )
                AsteriskActionButton(
                    text = stringResource(R.string.mihomo_profile_save_anyway),
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
private fun HttpSubscriptionWarningDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.mihomo_profile_http_subscription_warning_title)) },
        text = { Text(stringResource(R.string.mihomo_profile_http_subscription_warning_message)) },
        dismissButton = {
            AsteriskCancelButton(
                onClick = onDismissRequest,
            )
        },
        confirmButton = {
            AsteriskActionButton(
                text = stringResource(R.string.mihomo_profile_http_subscription_warning_confirm),
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
    requestHeadersState: TextFieldState,
    invalidRequestHeaders: Boolean,
    ageSecretKeyState: TextFieldState,
    updateIntervalState: TextFieldState,
    updateViaProxy: Boolean,
    onUpdateViaProxyChange: (Boolean) -> Unit,
    overrideScriptOptions: List<String>,
    selectedOverrideScriptIndex: Int,
    onSelectedOverrideScriptIndexChange: (Int) -> Unit,
    advancedExpanded: Boolean,
    onAdvancedExpandedChange: (Boolean) -> Unit,
    ruleOverride: MihomoRuleOverrideState,
    onOpenRuleOverride: () -> Unit,
    disableOverrides: Boolean,
    rawReadiness: RawConfigReadiness?,
    rawSnapshot: MihomoRawConfigSnapshot?,
    runMode: Int,
    vpnUsesHev: Boolean,
    onDisableOverridesChange: (Boolean) -> Unit,
) {
    OutlinedTextField(
        shape = AsteriskShapeTokens.InnerContainer,
        state = urlState,
        enabled = enabled,
        label = { Text(stringResource(R.string.mihomo_profile_url)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
    OutlinedTextField(
        shape = AsteriskShapeTokens.InnerContainer,
        state = updateIntervalState,
        enabled = enabled,
        label = { Text(stringResource(R.string.mihomo_profile_update_interval)) },
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
                text = stringResource(R.string.mihomo_profile_update_via_proxy),
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
                shape = AsteriskShapeTokens.InnerContainer,
                state = userAgentState,
                enabled = enabled,
                label = { Text(stringResource(R.string.mihomo_profile_user_agent)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            OutlinedTextField(
                shape = AsteriskShapeTokens.InnerContainer,
                state = ageSecretKeyState,
                enabled = enabled,
                label = { Text(stringResource(R.string.mihomo_profile_age_secret_key)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            AsteriskNoWrapTextField(
                shape = AsteriskShapeTokens.InnerContainer,
                state = requestHeadersState,
                enabled = enabled,
                label = { Text(stringResource(R.string.subscription_request_headers)) },
                isError = invalidRequestHeaders,
                supportingText = if (invalidRequestHeaders) {
                    { Text(stringResource(R.string.subscription_request_headers_invalid)) }
                } else null,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            ProfileRuleOverrideRow(
                ruleOverride = ruleOverride,
                rawMode = disableOverrides,
                enabled = enabled,
                onClick = onOpenRuleOverride,
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
                vpnUsesHev = vpnUsesHev,
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
    ruleOverride: MihomoRuleOverrideState,
    onOpenRuleOverride: () -> Unit,
    disableOverrides: Boolean,
    rawReadiness: RawConfigReadiness?,
    rawSnapshot: MihomoRawConfigSnapshot?,
    runMode: Int,
    vpnUsesHev: Boolean,
    onDisableOverridesChange: (Boolean) -> Unit,
    onSelectedOverrideScriptIndexChange: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var advancedExpanded by remember { mutableStateOf(false) }
    AsteriskModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.mihomo_profile_properties),
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
                shape = AsteriskShapeTokens.InnerContainer,
                state = nameState,
                label = { Text(stringResource(R.string.mihomo_profile_name)) },
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
                    ProfileRuleOverrideRow(
                        ruleOverride = ruleOverride,
                        rawMode = disableOverrides,
                        enabled = true,
                        onClick = onOpenRuleOverride,
                    )
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
                        vpnUsesHev = vpnUsesHev,
                        onEnabledChange = onDisableOverridesChange,
                    )
                }
            }
        }
    }
}

/** Opens the rule override editor; the override runs before the override script below it. */
@Composable
private fun ProfileRuleOverrideRow(
    ruleOverride: MihomoRuleOverrideState,
    rawMode: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val effectiveCount = ruleOverride.runtimeProjection().effectiveEntryCount
    SettingsActionRow(
        title = stringResource(R.string.mihomo_rule_override),
        horizontalPadding = 0.dp,
        summary = when {
            // The stored override is kept; raw mode just does not apply it.
            rawMode -> stringResource(R.string.mihomo_rule_override_summary_raw)
            !ruleOverride.enabled -> stringResource(R.string.mihomo_rule_override_summary_off)
            else -> stringResource(R.string.mihomo_rule_override_summary_on, effectiveCount)
        },
        icon = if (rawMode) Icons.Rounded.Lock else Icons.Rounded.Route,
        enabled = enabled && !rawMode,
        onClick = onClick,
        showNavigationIcon = !rawMode,
    )
}

@Composable
private fun ProfileOverrideScriptSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    readOnly: Boolean = false,
) {
    if (readOnly) {
        SettingsActionRow(
            title = stringResource(R.string.mihomo_profile_override_script),
            horizontalPadding = 0.dp,
            icon = Icons.Rounded.Lock,
            enabled = false,
            onClick = {},
            showNavigationIcon = false,
        )
        return
    }
    SettingsDropdownRow(
        title = stringResource(R.string.mihomo_profile_override_script),
        horizontalPadding = 0.dp,
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
    vpnUsesHev: Boolean,
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
                        text = stringResource(R.string.mihomo_profile_raw_mode),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.mihomo_profile_raw_mode_summary),
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
                            RawConfigReadiness.Ready -> R.string.mihomo_profile_raw_mode_ready
                            RawConfigReadiness.Degraded -> R.string.mihomo_profile_raw_mode_degraded
                            RawConfigReadiness.Blocked -> R.string.mihomo_profile_raw_mode_blocked
                            null -> R.string.mihomo_profile_raw_mode_unchecked
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
                    title = stringResource(R.string.mihomo_profile_raw_api),
                    value = snapshot?.api?.value?.control?.let { control ->
                        "${control.scheme.uppercase()} · ${control.host}:${control.port}"
                    } ?: stringResource(R.string.mihomo_profile_raw_unavailable),
                    source = snapshot?.api?.path ?: "external-controller",
                )
                RawCapabilityRow(
                    title = stringResource(R.string.mihomo_profile_raw_dns_hijack),
                    value = if (snapshot?.provesDnsHijack(runMode, vpnUsesHev) == true) {
                        stringResource(R.string.mihomo_profile_raw_configured)
                    } else {
                        stringResource(R.string.mihomo_profile_raw_unavailable)
                    },
                    source = snapshot?.dnsHijack?.path ?: "dns.enable + rules",
                )
                RawCapabilityRow(
                    title = stringResource(R.string.mihomo_profile_raw_run_mode),
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
        title = { Text(stringResource(R.string.mihomo_profile_raw_mode_confirm_title)) },
        text = { Text(stringResource(R.string.mihomo_profile_raw_mode_confirm_message)) },
        dismissButton = {
            AsteriskCancelButton(
                onClick = onDismissRequest,
            )
        },
        confirmButton = {
            AsteriskActionButton(
                text = stringResource(R.string.mihomo_profile_raw_mode_confirm),
                icon = Icons.Rounded.Check,
                onClick = onConfirm,
            )
        },
    )
}

private fun String.isValidAgeSecretKey(stored: String?): Boolean {
    return isBlank() || trim() == stored?.trim() || Clash.veritySecretKeys(this)
}

private const val RawConfigValidationDebounceMillis = 350L

/** The profile save use case, rebuilt when its services or the duplicate-name format change. */
@Composable
internal fun rememberMihomoProfileSaveUseCase(): MihomoProfileSaveUseCase {
    val services = LocalAppServices.current
    val duplicateNameFormat = stringResource(R.string.mihomo_profile_name_duplicate)
    return remember(
        services.mihomoProfilePreparer,
        services.mihomoProfileContentStore,
        duplicateNameFormat,
    ) {
        MihomoProfileSaveUseCase.create(
            profilePreparer = services.mihomoProfilePreparer,
            contentStore = services.mihomoProfileContentStore,
            formatDuplicateName = { name, ordinal -> duplicateNameFormat.format(name, ordinal) },
        )
    }
}
