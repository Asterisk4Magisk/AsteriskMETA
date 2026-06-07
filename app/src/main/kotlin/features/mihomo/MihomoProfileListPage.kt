// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.mihomo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import engine.mihomo.hasMihomoProxyProviders
import engine.mihomo.MihomoProfileFactory
import features.subscription.SubscriptionInstallConfig
import features.subscription.toSubscriptionInstallConfigOrNull
import features.subscription.usecase.subscriptionUpdateMessage
import features.subscription.usecase.toSubscriptionFetchOptions
import features.subscription.usecase.updateSubscriptions
import features.subscription.usecase.withUpdatedMihomoProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.components.BackNavigationIcon
import ui.components.IconDropdownMenu
import ui.components.IconDropdownMenuEntry
import ui.components.NavigationIcon
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
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
) {
    val context = LocalContext.current
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var showImportDialog by remember { mutableStateOf(false) }
    var previewProfileName by remember { mutableStateOf("") }
    var previewProfileContent by remember { mutableStateOf<String?>(null) }
    var syncingProfileIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val syncSuccessMessage = stringResource(R.string.subscription_update_result)
    val syncFailedMessage = stringResource(R.string.subscription_update_result_with_failed)
    val providerSyncDoneMessage = stringResource(R.string.mihomo_configuration_provider_sync_done)
    val providerSyncFailedMessage = stringResource(R.string.mihomo_configuration_provider_sync_failed)
    val previewFailedMessage = stringResource(R.string.mihomo_configuration_preview_failed)
    val importedMessage = stringResource(R.string.mihomo_configuration_imported)
    val importFileFailedMessage = stringResource(R.string.mihomo_configuration_import_file_failed)
    val providerPrepareFailedMessage = stringResource(R.string.mihomo_configuration_provider_prepare_failed)
    val importQrFailedMessage = stringResource(R.string.mihomo_configuration_import_qr_failed)
    val invalidQrMessage = stringResource(R.string.mihomo_configuration_invalid_qr_content)
    val serviceStoppedMessage = stringResource(R.string.proxy_service_stopped)
    val stopFailedMessage = stringResource(R.string.mihomo_dashboard_stop_failed)

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
                state.copy(
                    mihomoProfiles = state.mihomoProfiles.map { item ->
                        if (item.id == profile.id) profile else item
                    },
                )
            }
        }
        return savedProfile
    }

    fun deleteProfile(profile: MihomoProfileState) {
        if (profile.builtIn) return
        val previousState = appState
        val selectedProfileDeleted = profile.id == appState.selectedMihomoProfileId
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
        if (selectedProfileDeleted) {
            scope.launch {
                stopProxyServiceAfterProfileChange(
                    appState = previousState,
                    services = services,
                    updateAppState = updateAppState,
                    stoppedMessage = serviceStoppedMessage,
                    stopFailedMessage = stopFailedMessage,
                )
            }
        }
    }

    fun selectProfile(profile: MihomoProfileState) {
        if (profile.id == appState.selectedMihomoProfileId) return
        val previousState = appState
        updateAppState { state ->
            if (profile.id == state.selectedMihomoProfileId) {
                state
            } else {
                state.copy(selectedMihomoProfileId = profile.id)
            }
        }
        scope.launch {
            stopProxyServiceAfterProfileChange(
                appState = previousState,
                services = services,
                updateAppState = updateAppState,
                stoppedMessage = serviceStoppedMessage,
                stopFailedMessage = stopFailedMessage,
            )
        }
    }

    fun previewProfile(profile: MihomoProfileState) {
        runCatching {
            MihomoProfileFactory.buildProfile(appState.copy(selectedMihomoProfileId = profile.id))
        }.onSuccess { content ->
            previewProfileName = profile.name
            previewProfileContent = content
        }.onFailure { error ->
            scope.launch { services.tipNotifier.showError(error, previewFailedMessage) }
        }
    }

    fun syncProfile(profile: MihomoProfileState) {
        if (profile.type != MihomoProfileType.Url || profile.url.isBlank() || profile.id in syncingProfileIds) return
        scope.launch {
            syncingProfileIds = syncingProfileIds + profile.id
            try {
                val snapshot = appState
                val result = updateSubscriptions(
                    profiles = listOf(profile),
                    subscriptionFetcher = services.subscriptionFetcher,
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
            } finally {
                syncingProfileIds = syncingProfileIds - profile.id
            }
        }
    }

    fun syncProfileProviders(profile: MihomoProfileState) {
        if (profile.content.isBlank() || !profile.content.hasMihomoProxyProviders() || profile.id in syncingProfileIds) {
            return
        }
        scope.launch {
            syncingProfileIds = syncingProfileIds + profile.id
            try {
                runCatching {
                    services.mihomoProviderFetcher.refreshProxyProviders(
                        profileContent = profile.content,
                        sourceUrl = profile.url,
                    )
                }.onSuccess {
                    services.tipNotifier.show(providerSyncDoneMessage)
                }.onFailure { error ->
                    services.tipNotifier.showError(error, providerSyncFailedMessage)
                }
            } finally {
                syncingProfileIds = syncingProfileIds - profile.id
            }
        }
    }

    fun importSubscription(config: SubscriptionInstallConfig) {
        val savedProfile = saveProfile(
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
        syncProfile(savedProfile)
    }

    fun importFile() {
        scope.launch {
            runCatching {
                val uri = services.mihomoProfileFilePicker() ?: return@launch
                val imported = withContext(Dispatchers.IO) {
                    context.readMihomoProfileFile(uri)
                }
                val savedProfile = saveProfile(
                    profile = MihomoProfileState(
                        id = DefaultMihomoProfileId,
                        name = imported.name,
                        type = MihomoProfileType.File,
                        lastUpdatedAtMillis = imported.modifiedAtMillis,
                        content = imported.content,
                    ),
                    isNew = true,
                )
                runCatching {
                    services.mihomoProviderFetcher.fetchMissingProviders(
                        profileContent = savedProfile.content,
                        sourceUrl = savedProfile.url,
                    )
                }.onFailure { error ->
                    services.tipNotifier.showError(error, providerPrepareFailedMessage)
                }
                services.tipNotifier.show(importedMessage)
            }.onFailure { error ->
                services.tipNotifier.showError(error, importFileFailedMessage)
            }
        }
    }

    fun importQrCode() {
        scope.launch {
            runCatching {
                val text = services.qrCodeScanner()?.trim().orEmpty()
                if (text.isBlank()) return@launch
                val config = text.toSubscriptionInstallConfigOrNull()
                    ?: error(invalidQrMessage)
                importSubscription(config)
            }.onFailure { error ->
                services.tipNotifier.showError(error, importQrFailedMessage)
            }
        }
    }

    Scaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(R.string.mihomo_configurations_title),
                subtitle = stringResource(R.string.mihomo_configurations_count)
                    .formatTemplate("count" to appState.mihomoProfiles.size),
                isWideScreen = isWideScreen,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackNavigationIcon(onClick = { navigator.pop() })
                },
                actions = {
                    NavigationIcon(
                        onClick = {
                            showImportDialog = true
                        },
                        imageVector = MiuixIcons.Add,
                        contentDescription = stringResource(R.string.mihomo_configuration_add),
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

        Box {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = listPadding,
            ) {
                item("profiles_title") {
                    SmallTitle(text = stringResource(R.string.mihomo_configurations_list))
                }
                items(
                    items = appState.mihomoProfiles,
                    key = { profile -> profile.id },
                ) { profile ->
                    MihomoProfileCard(
                        profile = profile,
                        overrideScripts = appState.mihomoOverrideScripts,
                        selected = profile.id == appState.selectedMihomoProfileId,
                        syncing = profile.id in syncingProfileIds,
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
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
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
        MihomoProfilePreviewDialog(
            show = previewProfileContent != null,
            profileName = previewProfileName,
            content = previewProfileContent.orEmpty(),
            onDismissRequest = { previewProfileContent = null },
        )
    }
}

@Composable
private fun MihomoProfileCard(
    profile: MihomoProfileState,
    overrideScripts: List<MihomoOverrideScriptState>,
    selected: Boolean,
    syncing: Boolean,
    onSelect: () -> Unit,
    onAction: (MihomoProfileAction) -> Unit,
) {
    val overrideScriptName = profile.overrideScriptName(overrideScripts)
    val interactionSource = remember { MutableInteractionSource() }
    val hasProxyProviders = remember(profile.content) {
        profile.content.hasMihomoProxyProviders()
    }
    val menuEntries = buildList {
        add(
            IconDropdownMenuEntry(
                key = MihomoProfileAction.Edit,
                title = stringResource(R.string.common_edit),
                action = MihomoProfileAction.Edit,
            ),
        )
        add(
            IconDropdownMenuEntry(
                key = MihomoProfileAction.Preview,
                title = stringResource(R.string.mihomo_configuration_preview),
                action = MihomoProfileAction.Preview,
            ),
        )
        if (profile.type == MihomoProfileType.Url && profile.url.isNotBlank() && !syncing) {
            add(
                IconDropdownMenuEntry(
                    key = MihomoProfileAction.Sync,
                    title = stringResource(R.string.mihomo_configuration_sync),
                    action = MihomoProfileAction.Sync,
                ),
            )
        }
        if (hasProxyProviders && !syncing) {
            add(
                IconDropdownMenuEntry(
                    key = MihomoProfileAction.SyncProviders,
                    title = stringResource(R.string.mihomo_configuration_sync_providers),
                    action = MihomoProfileAction.SyncProviders,
                ),
            )
        }
        if (!profile.builtIn) {
            add(
                IconDropdownMenuEntry(
                    key = MihomoProfileAction.Delete,
                    title = stringResource(R.string.common_delete),
                    action = MihomoProfileAction.Delete,
                ),
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !selected,
                onClick = onSelect,
            ),
        colors = CardDefaults.defaultColors(color = mihomoProfileCardColor(selected)),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = profile.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = profile.summaryText(),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MihomoProfileSubscriptionInfo(profile = profile)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = profile.lastUpdatedText(),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (overrideScriptName != null) {
                        MihomoProfileChip(
                            text = stringResource(R.string.mihomo_configuration_override_script_applied)
                                .formatTemplate("name" to overrideScriptName),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconDropdownMenu(
                imageVector = MiuixIcons.More,
                contentDescription = stringResource(R.string.mihomo_configuration_actions),
                entries = menuEntries,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun mihomoProfileCardColor(selected: Boolean): Color {
    return if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        MiuixTheme.colorScheme.surface
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
    WindowDialog(
        show = show,
        title = stringResource(R.string.mihomo_configuration_add_method_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MihomoProfileImportOption(
                title = stringResource(R.string.mihomo_configuration_add_qr),
                summary = stringResource(R.string.mihomo_configuration_add_qr_summary),
                icon = { Icon(imageVector = MiuixIcons.Scan, contentDescription = null) },
                onClick = { onAction(MihomoProfileImportAction.QrCode) },
            )
            MihomoProfileImportOption(
                title = stringResource(R.string.mihomo_configuration_add_file),
                summary = stringResource(R.string.mihomo_configuration_add_file_summary),
                icon = { Icon(imageVector = MiuixIcons.File, contentDescription = null) },
                onClick = { onAction(MihomoProfileImportAction.File) },
            )
            MihomoProfileImportOption(
                title = stringResource(R.string.mihomo_configuration_add_url),
                summary = stringResource(R.string.mihomo_configuration_add_url_summary),
                icon = { Icon(imageVector = MiuixIcons.Link, contentDescription = null) },
                onClick = { onAction(MihomoProfileImportAction.Url) },
            )
            TextButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(onClick = onClick),
        insideMargin = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(8.dp),
            ) {
                icon()
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MiuixTheme.colorScheme.primary),
            )
        }
        Text(
            text = stringResource(R.string.mihomo_configuration_traffic_summary)
                .formatTemplate(
                    "used" to info.usedBytes.toReadableBytes(maxUnit = ReadableByteUnit.GiB),
                    "total" to info.totalBytes.toReadableBytes(maxUnit = ReadableByteUnit.GiB),
                    "expire" to info.expireText(),
                ),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun MihomoProfileState.summaryText(): String {
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
private fun MihomoProfileChip(
    text: String,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.disabledOnSecondaryVariant.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MihomoProfilePreviewDialog(
    show: Boolean,
    profileName: String,
    content: String,
    onDismissRequest: () -> Unit,
) {
    val previewValue = remember(content) {
        TextFieldValue(
            text = content,
            selection = TextRange(0),
        )
    }

    WindowDialog(
        show = show,
        title = stringResource(R.string.mihomo_configuration_preview_title)
            .formatTemplate("name" to profileName.ifBlank { "-" }),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            YamlCodeEditor(
                label = stringResource(R.string.mihomo_configuration_content),
                value = previewValue,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 520.dp)
                    .padding(bottom = 16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )
            TextButton(
                text = stringResource(R.string.common_complete),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
            )
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

private fun Context.readMihomoProfileFile(uri: Uri): ImportedMihomoProfileFile {
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
