// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.usecase

import app.AppState
import app.MihomoProfileState
import engine.mihomo.MihomoProfileContentRef
import engine.mihomo.MihomoProfileContentStore
import engine.network.toPortOrNull
import features.logs.AndroidAppLogger
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import features.subscription.runtime.AndroidMihomoProviderFetcher
import features.subscription.runtime.AndroidSubscriptionFetcher
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import ui.text.formatTemplate

private const val LogTag = "SubscriptionUpdateUseCase"

internal data class MihomoProfileSubscriptionUpdate(
    val profileId: Int,
    val contentRef: MihomoProfileContentRef,
    val subscriptionInfo: app.MihomoSubscriptionInfo,
)

internal data class MihomoProfileSubscriptionUpdateResult(
    val updates: List<MihomoProfileSubscriptionUpdate>,
    val failedProfileCount: Int,
    val updatedAtMillis: Long,
) {
    val updatedProfileCount: Int
        get() = updates.size
}

internal suspend fun updateSubscriptions(
    profiles: List<MihomoProfileState>,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    contentStore: MihomoProfileContentStore,
    providerFetcher: AndroidMihomoProviderFetcher? = null,
    fetchOptions: (MihomoProfileState) -> AndroidSubscriptionFetchOptions,
): MihomoProfileSubscriptionUpdateResult = supervisorScope {
    val results = profiles.map { profile ->
        async {
            updateMihomoProfile(
                profile = profile,
                subscriptionFetcher = subscriptionFetcher,
                contentStore = contentStore,
                providerFetcher = providerFetcher,
                fetchOptions = fetchOptions(profile),
            )
        }
    }.awaitAll()
    val updates = results.filterNotNull()
    MihomoProfileSubscriptionUpdateResult(
        updates = updates,
        failedProfileCount = results.size - updates.size,
        updatedAtMillis = Clock.System.now().toEpochMilliseconds(),
    )
}

private suspend fun updateMihomoProfile(
    profile: MihomoProfileState,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    contentStore: MihomoProfileContentStore,
    providerFetcher: AndroidMihomoProviderFetcher?,
    fetchOptions: AndroidSubscriptionFetchOptions,
): MihomoProfileSubscriptionUpdate? {
    return runCatching {
        val result = subscriptionFetcher.fetchWithMetadata(
            url = profile.url,
            userAgent = profile.userAgent,
            options = fetchOptions,
        )
        providerFetcher?.fetchMissingProviders(
            profileContent = result.content,
            sourceUrl = profile.url,
        )
        val contentRef = contentStore.write(profile, result.content)
        MihomoProfileSubscriptionUpdate(
            profileId = profile.id,
            contentRef = contentRef,
            subscriptionInfo = result.subscriptionInfo,
        ).also { update ->
            if (update.contentRef.sizeBytes <= 0L) {
                AndroidAppLogger.warn(
                    LogTag,
                    "Subscription update fetched blank profile ${profile.logIdentity()}",
                )
            }
        }
    }.onFailure { error ->
        if (error is CancellationException) throw error
        AndroidAppLogger.warn(
            LogTag,
            "Subscription update failed ${profile.logIdentity()}",
            error,
        )
    }.getOrNull()
}

internal fun CoroutineScope.launchMihomoProfileSubscriptionUpdate(
    profiles: List<MihomoProfileState>,
    appStateSnapshot: AppState,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    contentStore: MihomoProfileContentStore,
    providerFetcher: AndroidMihomoProviderFetcher? = null,
    updateAppState: ((AppState) -> AppState) -> Unit,
    onResult: suspend (MihomoProfileSubscriptionUpdateResult) -> Unit = {},
    onFailure: suspend (Throwable) -> Unit = {},
): Job = launch {
    runCatching {
        val result = updateSubscriptions(
            profiles = profiles,
            subscriptionFetcher = subscriptionFetcher,
            contentStore = contentStore,
            providerFetcher = providerFetcher,
            fetchOptions = { profile -> appStateSnapshot.toSubscriptionFetchOptions(profile) },
        )
        if (result.updates.isNotEmpty()) {
            updateAppState { state ->
                state.withUpdatedMihomoProfiles(
                    updates = result.updates,
                    updatedAtMillis = result.updatedAtMillis,
                )
            }
        }
        result
    }.onSuccess { result ->
        onResult(result)
    }.onFailure { error ->
        if (error is CancellationException) throw error
        onFailure(error)
    }
}

internal fun AppState.toSubscriptionFetchOptions(profile: MihomoProfileState): AndroidSubscriptionFetchOptions {
    return AndroidSubscriptionFetchOptions(
        useRunningProxy = profile.updateViaProxy,
        fallbackProxyPort = localProxyPort.toPortOrNull(),
        fallbackProxyUsername = localProxyUsername,
        fallbackProxyPassword = localProxyPassword,
    )
}

internal fun AppState.withUpdatedMihomoProfiles(
    updates: List<MihomoProfileSubscriptionUpdate>,
    updatedAtMillis: Long,
): AppState {
    if (updates.isEmpty()) return this
    val updatesById = updates.associateBy { update -> update.profileId }
    return copy(
        mihomoProfiles = mihomoProfiles.map { profile ->
            val update = updatesById[profile.id] ?: return@map profile
            profile.copy(
                contentPath = update.contentRef.path,
                contentSha256 = update.contentRef.sha256,
                contentSizeBytes = update.contentRef.sizeBytes,
                subscriptionInfo = update.subscriptionInfo,
                lastUpdatedAtMillis = updatedAtMillis,
            )
        },
    )
}

internal fun List<MihomoProfileState>.dueSubscriptionProfiles(nowMillis: Long): List<MihomoProfileState> {
    return filter { profile ->
        profile.enabled &&
            profile.url.isNotBlank() &&
            profile.updateInterval.toLongOrNull()?.let { hours ->
                hours > 0 && nowMillis - profile.lastUpdatedAtMillis >= hours * 60L * 60L * 1000L
            } == true
    }
}

internal fun subscriptionUpdateMessage(
    result: MihomoProfileSubscriptionUpdateResult,
    successTemplate: String,
    failedTemplate: String,
): String {
    val template = if (result.failedProfileCount > 0) failedTemplate else successTemplate
    return template.formatTemplate(
        "profileCount" to result.updatedProfileCount,
        "failedCount" to result.failedProfileCount,
    )
}

private fun MihomoProfileState.logIdentity(): String {
    return "profileId=$id profileName=${name.ifBlank { "<blank>" }} " +
        "urlHost=${url.toLogHost()} userAgent=${userAgent.ifBlank { "<blank>" }}"
}

private fun String.toLogHost(): String {
    return runCatching { URI(this).host }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: "<unknown>"
}
