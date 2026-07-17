// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.usecase

import app.AppState
import app.MihomoProfileState
import app.withMihomoRestartRequired
import engine.mihomo.MihomoProfileContentRef
import engine.mihomo.MihomoProfileContentStore
import engine.network.toPortOrNull
import features.logs.AndroidAppLogger
import features.subscription.runtime.AndroidMihomoProfilePreparer
import features.subscription.runtime.AndroidSubscriptionFetchOptions
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
    val updateInterval: String? = null,
)

internal data class MihomoProfileSubscriptionFailure(
    val profileId: Int,
    val error: Throwable,
)

internal data class MihomoProfileSubscriptionUpdateResult(
    val updates: List<MihomoProfileSubscriptionUpdate>,
    val failures: List<MihomoProfileSubscriptionFailure>,
    val updatedAtMillis: Long,
) {
    val updatedProfileCount: Int
        get() = updates.size

    val failedProfileCount: Int
        get() = failures.size
}

internal suspend fun updateSubscriptions(
    profiles: List<MihomoProfileState>,
    profilePreparer: AndroidMihomoProfilePreparer,
    contentStore: MihomoProfileContentStore,
    fetchOptions: (MihomoProfileState) -> AndroidSubscriptionFetchOptions,
): MihomoProfileSubscriptionUpdateResult = supervisorScope {
    val results = profiles.map { profile ->
        async {
            updateMihomoProfile(
                profile = profile,
                profilePreparer = profilePreparer,
                contentStore = contentStore,
                fetchOptions = fetchOptions(profile),
            )
        }
    }.awaitAll()
    val updates = results.mapNotNull { result -> result.getOrNull() }
    val failures = results.mapIndexedNotNull { index, result ->
        result.exceptionOrNull()?.let { error ->
            MihomoProfileSubscriptionFailure(
                profileId = profiles[index].id,
                error = error,
            )
        }
    }
    MihomoProfileSubscriptionUpdateResult(
        updates = updates,
        failures = failures,
        updatedAtMillis = Clock.System.now().toEpochMilliseconds(),
    )
}

private suspend fun updateMihomoProfile(
    profile: MihomoProfileState,
    profilePreparer: AndroidMihomoProfilePreparer,
    contentStore: MihomoProfileContentStore,
    fetchOptions: AndroidSubscriptionFetchOptions,
): Result<MihomoProfileSubscriptionUpdate> {
    return try {
        when (
            val prepared = prepareMihomoProfileSubscription(
                profile = profile,
                profilePreparer = profilePreparer,
                fetchOptions = fetchOptions,
            )
        ) {
            is MihomoProfilePreparation.Success -> {
                val contentRef = contentStore.write(profile, prepared.content)
                Result.success(
                    MihomoProfileSubscriptionUpdate(
                        profileId = profile.id,
                        contentRef = contentRef,
                        subscriptionInfo = prepared.subscriptionInfo,
                        updateInterval = prepared.updateIntervalMillis?.toStoredUpdateInterval(),
                    ),
                )
            }

            is MihomoProfilePreparation.Failure -> Result.failure(prepared.error)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }.also { result ->
        result.exceptionOrNull()?.let { error ->
            AndroidAppLogger.warn(
                LogTag,
                "Subscription update failed ${profile.logIdentity()}",
                error,
            )
        }
    }
}

internal fun CoroutineScope.launchMihomoProfileSubscriptionUpdate(
    profiles: List<MihomoProfileState>,
    appStateSnapshot: AppState,
    profilePreparer: AndroidMihomoProfilePreparer,
    contentStore: MihomoProfileContentStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    onResult: suspend (MihomoProfileSubscriptionUpdateResult) -> Unit = {},
    onFailure: suspend (Throwable) -> Unit = {},
): Job = launch {
    runCatching {
        val result = updateSubscriptions(
            profiles = profiles,
            profilePreparer = profilePreparer,
            contentStore = contentStore,
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
    val changedSelectedProfile = updatesById[selectedMihomoProfileId]?.let { update ->
        mihomoProfiles.firstOrNull { profile -> profile.id == selectedMihomoProfileId }
            ?.contentSha256 != update.contentRef.sha256
    } == true
    return copy(
        mihomoProfiles = mihomoProfiles.map { profile ->
            val update = updatesById[profile.id] ?: return@map profile
            profile.copy(
                contentPath = update.contentRef.path,
                contentSha256 = update.contentRef.sha256,
                contentSizeBytes = update.contentRef.sizeBytes,
                subscriptionInfo = update.subscriptionInfo,
                updateInterval = update.updateInterval ?: profile.updateInterval,
                lastUpdatedAtMillis = updatedAtMillis,
                syncFailed = false,
            )
        },
    ).withMihomoRestartRequired(selectedMihomoProfileId, changedSelectedProfile)
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

private fun Long.toStoredUpdateInterval(): String {
    if (this <= 0L) return "0"
    return (this / MillisPerHour).coerceAtLeast(1L).toString()
}

private const val MillisPerHour = 60L * 60L * 1000L
