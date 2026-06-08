// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
import app.MihomoSubscriptionInfo
import app.R
import app.collectAppState
import app.nextAvailableMihomoProfileId
import engine.mihomo.sha256Hex
import features.subscription.toRawHttpsSubscriptionInstallConfigOrNull
import features.subscription.usecase.subscriptionUpdateMessage
import features.subscription.usecase.toSubscriptionFetchOptions
import features.subscription.usecase.updateSubscriptions
import features.subscription.usecase.withUpdatedMihomoProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.components.BackNavigationIcon
import ui.components.NavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout

@Composable
fun MihomoProfileEditPage(
    padding: PaddingValues,
    profileId: Int,
    type: Int,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
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
    val syncSuccessMessage = stringResource(R.string.subscription_update_result)
    val syncFailedMessage = stringResource(R.string.subscription_update_result_with_failed)
    val providerPrepareFailedMessage = stringResource(R.string.mihomo_configuration_provider_prepare_failed)

    var saving by remember { mutableStateOf(false) }
    val nameState = rememberTextFieldState(initialText = targetProfile?.name.orEmpty())
    val urlState = rememberTextFieldState(initialText = targetProfile?.url ?: "")
    val userAgentState = rememberTextFieldState(
        initialText = targetProfile?.userAgent ?: app.DefaultMihomoProfileUserAgent,
    )
    val updateIntervalState = rememberTextFieldState(
        initialText = targetProfile?.updateInterval ?: DefaultMihomoProfileUpdateInterval,
    )
    var updateViaProxy by remember(targetProfile?.id, isNew) {
        mutableStateOf(targetProfile?.updateViaProxy ?: false)
    }
    var contentValue by remember(targetProfile?.id, isNew) {
        mutableStateOf(
            TextFieldValue(
                text = "",
                selection = TextRange(0),
            ),
        )
    }
    var overrideScriptId by remember(targetProfile?.id, isNew) {
        mutableIntStateOf(targetProfile?.overrideScriptId ?: DefaultMihomoOverrideScriptId)
    }
    val selectedOverrideScript = appState.mihomoOverrideScripts.firstOrNull { script ->
        script.id == overrideScriptId
    }
    val selectedOverrideScriptId = if (
        overrideScriptId == DefaultMihomoOverrideScriptId ||
        selectedOverrideScript != null
    ) {
        overrideScriptId
    } else {
        DefaultMihomoOverrideScriptId
    }
    val overrideScriptOptions = listOf(stringResource(R.string.mihomo_configuration_override_script_none)) +
        appState.mihomoOverrideScripts.map { script -> script.name }
    val selectedOverrideScriptIndex = selectedOverrideScript
        ?.let { script -> appState.mihomoOverrideScripts.indexOfFirst { it.id == script.id } + 1 }
        ?: 0
    val nameRequiredMessage = stringResource(R.string.mihomo_configuration_name_required)
    val invalidUrlMessage = stringResource(R.string.mihomo_configuration_invalid_subscription_url)

    LaunchedEffect(targetProfile?.id, targetProfile?.contentPath, profileType) {
        if (profileType != MihomoProfileType.File || targetProfile == null) {
            if (targetProfile == null) {
                contentValue = TextFieldValue("")
            }
            return@LaunchedEffect
        }
        val initialContent = withContext(Dispatchers.IO) {
            services.mihomoProfileContentStore.readOrEmpty(targetProfile)
        }
        contentValue = TextFieldValue(
            text = initialContent,
            selection = TextRange(initialContent.length),
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
                state.copy(
                    mihomoProfiles = state.mihomoProfiles.map { item ->
                        if (item.id == profile.id) profile else item
                    },
                )
            }
        }
        return savedProfile
    }

    suspend fun syncProfile(profile: MihomoProfileState) {
        if (profile.type != MihomoProfileType.Url || profile.url.isBlank()) return
        val snapshot = appState
        val result = updateSubscriptions(
            profiles = listOf(profile),
            subscriptionFetcher = services.subscriptionFetcher,
            contentStore = services.mihomoProfileContentStore,
            providerFetcher = services.mihomoProviderFetcher,
            fetchOptions = { snapshot.toSubscriptionFetchOptions(it) },
        )
        updateAppState { state ->
            state.withUpdatedMihomoProfiles(
                updates = result.updates,
                updatedAtMillis = result.updatedAtMillis,
            )
        }
        services.tipNotifier.show(
            subscriptionUpdateMessage(
                result = result,
                successTemplate = syncSuccessMessage,
                failedTemplate = syncFailedMessage,
            ),
        )
    }

    fun onSave() {
        val cleanName = nameState.text.toString().trim()
        if (saving || (!isNew && targetProfile == null)) return
        if (cleanName.isBlank()) {
            scope.launch { services.tipNotifier.show(nameRequiredMessage) }
            return
        }
        if (profileType == MihomoProfileType.File) {
            saving = true
            val profileSnapshot = targetProfile
            val contentText = contentValue.text
            scope.launch {
                val saved = runCatching {
                    val contentChanged = withContext(Dispatchers.IO) {
                        profileSnapshot == null || profileSnapshot.contentSha256 != contentText.sha256Hex()
                    }
                    val contentRef = withContext(Dispatchers.IO) {
                        when {
                            contentText.isBlank() -> {
                                if (profileSnapshot?.hasContent == true) {
                                    services.mihomoProfileContentStore.delete(profileSnapshot)
                                }
                                null
                            }
                            contentChanged && profileSnapshot != null -> services.mihomoProfileContentStore.write(
                                profileSnapshot,
                                contentText,
                            )
                            contentChanged -> services.mihomoProfileContentStore.writeNew(contentText)
                            else -> null
                        }
                    }
                    val localProfileModified = profileSnapshot == null ||
                        profileSnapshot.type != MihomoProfileType.File ||
                        profileSnapshot.name != cleanName ||
                        contentChanged ||
                        profileSnapshot.overrideScriptId != selectedOverrideScriptId
                    val savedProfile = if (profileSnapshot != null) {
                        profileSnapshot.copy(
                            name = cleanName,
                            type = MihomoProfileType.File,
                            url = "",
                            contentPath = when {
                                contentText.isBlank() -> ""
                                contentRef != null -> contentRef.path
                                else -> profileSnapshot.contentPath
                            },
                            contentSha256 = when {
                                contentText.isBlank() -> ""
                                contentRef != null -> contentRef.sha256
                                else -> profileSnapshot.contentSha256
                            },
                            contentSizeBytes = when {
                                contentText.isBlank() -> 0L
                                contentRef != null -> contentRef.sizeBytes
                                else -> profileSnapshot.contentSizeBytes
                            },
                            lastUpdatedAtMillis = if (localProfileModified) {
                                System.currentTimeMillis()
                            } else {
                                profileSnapshot.lastUpdatedAtMillis
                            },
                            overrideScriptId = selectedOverrideScriptId,
                        )
                    } else {
                        MihomoProfileState(
                            id = DefaultMihomoProfileId,
                            name = cleanName,
                            type = MihomoProfileType.File,
                            contentPath = contentRef?.path.orEmpty(),
                            contentSha256 = contentRef?.sha256.orEmpty(),
                            contentSizeBytes = contentRef?.sizeBytes ?: 0L,
                            lastUpdatedAtMillis = System.currentTimeMillis(),
                            overrideScriptId = selectedOverrideScriptId,
                        )
                    }
                    saveProfile(savedProfile, profileSnapshot == null)
                }.onFailure { error ->
                    services.tipNotifier.showError(error, providerPrepareFailedMessage)
                }.getOrNull()
                if (saved == null) {
                    saving = false
                    return@launch
                }
                if (!saved.hasContent) {
                    saving = false
                    navigator.pop()
                    return@launch
                }
                runCatching {
                    services.mihomoProviderFetcher.fetchMissingProviders(
                        profileContent = contentText,
                        sourceUrl = saved.url,
                    )
                }.onFailure { error ->
                    services.tipNotifier.showError(error, providerPrepareFailedMessage)
                }
                saving = false
                navigator.pop()
            }
            return
        }

        val trimmedUrl = urlState.text.toString().trim()
        val cleanUserAgent = userAgentState.text.toString().trim().ifBlank { app.DefaultMihomoProfileUserAgent }
        val cleanInterval = updateIntervalState.text.toString().trim()
        if (trimmedUrl.toRawHttpsSubscriptionInstallConfigOrNull() == null) {
            scope.launch { services.tipNotifier.show(invalidUrlMessage) }
            return
        }
        val urlChanged = targetProfile?.url != trimmedUrl
        val remoteOptionsChanged = targetProfile == null ||
            urlChanged ||
            targetProfile.userAgent != cleanUserAgent ||
            targetProfile.updateViaProxy != updateViaProxy
        val savedProfile = if (targetProfile != null) {
            if (urlChanged && targetProfile.hasContent) {
                services.mihomoProfileContentStore.delete(targetProfile)
            }
            targetProfile.copy(
                name = cleanName,
                type = MihomoProfileType.Url,
                url = trimmedUrl,
                userAgent = cleanUserAgent,
                updateInterval = cleanInterval,
                updateViaProxy = updateViaProxy,
                contentPath = if (urlChanged) "" else targetProfile.contentPath,
                contentSha256 = if (urlChanged) "" else targetProfile.contentSha256,
                contentSizeBytes = if (urlChanged) 0L else targetProfile.contentSizeBytes,
                subscriptionInfo = if (urlChanged) MihomoSubscriptionInfo() else targetProfile.subscriptionInfo,
                lastUpdatedAtMillis = if (urlChanged) 0L else targetProfile.lastUpdatedAtMillis,
                overrideScriptId = selectedOverrideScriptId,
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
                overrideScriptId = selectedOverrideScriptId,
            )
        }
        val saved = saveProfile(savedProfile, targetProfile == null)
        if (!remoteOptionsChanged && saved.hasContent) {
            navigator.pop()
            return
        }
        saving = true
        scope.launch {
            runCatching {
                syncProfile(saved)
            }.onFailure { error ->
                services.tipNotifier.showError(error, syncFailedMessage)
            }
            saving = false
            navigator.pop()
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
                        imageVector = MiuixIcons.Ok,
                        contentDescription = stringResource(R.string.common_save),
                        onClick = ::onSave,
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
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            key(targetProfile?.id, profileType) {
                val baseModifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 12.dp, vertical = 12.dp)

                Column(
                    modifier = if (profileType == MihomoProfileType.Url) {
                        baseModifier.verticalScroll(rememberScrollState())
                    } else {
                        baseModifier
                    },
                ) {
                    TextField(
                        state = nameState,
                        label = stringResource(R.string.mihomo_configuration_name),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.mihomo_configuration_override_script),
                            items = overrideScriptOptions,
                            selectedIndex = selectedOverrideScriptIndex.coerceIn(overrideScriptOptions.indices),
                            onSelectedIndexChange = { index ->
                                overrideScriptId = if (index == 0) {
                                    DefaultMihomoOverrideScriptId
                                } else {
                                    appState.mihomoOverrideScripts.getOrNull(index - 1)?.id
                                        ?: DefaultMihomoOverrideScriptId
                                }
                            },
                        )
                    }
                    if (profileType == MihomoProfileType.Url) {
                        UrlProfileFields(
                            urlState = urlState,
                            userAgentState = userAgentState,
                            updateIntervalState = updateIntervalState,
                            updateViaProxy = updateViaProxy,
                            onUpdateViaProxyChange = { updateViaProxy = it },
                        )
                    } else {
                        YamlCodeEditor(
                            label = stringResource(R.string.mihomo_configuration_content),
                            value = contentValue,
                            onValueChange = { contentValue = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlProfileFields(
    urlState: TextFieldState,
    userAgentState: TextFieldState,
    updateIntervalState: TextFieldState,
    updateViaProxy: Boolean,
    onUpdateViaProxyChange: (Boolean) -> Unit,
) {
    TextField(
        state = urlState,
        label = stringResource(R.string.mihomo_configuration_url),
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
    TextField(
        state = userAgentState,
        label = stringResource(R.string.mihomo_configuration_user_agent),
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
    TextField(
        state = updateIntervalState,
        label = stringResource(R.string.mihomo_configuration_update_interval),
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation.byValue { _, proposed ->
            proposed.toString().filter(Char::isDigit).take(5)
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
    SwitchPreference(
        title = stringResource(R.string.mihomo_configuration_update_via_proxy),
        checked = updateViaProxy,
        onCheckedChange = onUpdateViaProxyChange,
    )
}
