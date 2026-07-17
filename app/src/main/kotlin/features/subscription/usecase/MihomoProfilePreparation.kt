// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.usecase

import app.MihomoProfileState
import app.MihomoSubscriptionInfo
import com.github.kr328.clash.core.model.FetchStatus
import features.subscription.runtime.AndroidMihomoProfilePreparation
import features.subscription.runtime.AndroidMihomoProfilePreparer
import features.subscription.runtime.AndroidSubscriptionFetchOptions

internal enum class MihomoProfileSyncStage {
    Downloading,
    Decrypting,
    PreparingProviders,
    Verifying,
}

internal sealed interface MihomoProfilePreparation {
    data class Success(
        val content: String,
        val subscriptionInfo: MihomoSubscriptionInfo,
        val updateIntervalMillis: Long? = null,
    ) : MihomoProfilePreparation

    data class Failure(
        val stage: MihomoProfileSyncStage,
        val error: Throwable,
        val content: String? = null,
        val subscriptionInfo: MihomoSubscriptionInfo? = null,
        val updateIntervalMillis: Long? = null,
    ) : MihomoProfilePreparation
}

internal suspend fun prepareMihomoProfile(
    profile: MihomoProfileState,
    localContent: String?,
    profilePreparer: AndroidMihomoProfilePreparer,
    fetchOptions: AndroidSubscriptionFetchOptions,
    onStage: (MihomoProfileSyncStage) -> Unit = {},
): MihomoProfilePreparation {
    return when (
        val result = profilePreparer.prepare(
            profileContent = localContent,
            sourceUrl = profile.url,
            userAgent = profile.userAgent,
            ageSecretKey = profile.ageSecretKey,
            fetchOptions = fetchOptions,
            onStatus = { status ->
                status.action.toSyncStageOrNull()?.let(onStage)
            },
        )
    ) {
        is AndroidMihomoProfilePreparation.Success -> MihomoProfilePreparation.Success(
            content = result.content,
            subscriptionInfo = result.subscriptionInfo,
            updateIntervalMillis = result.updateIntervalMillis,
        )

        is AndroidMihomoProfilePreparation.Failure -> MihomoProfilePreparation.Failure(
            stage = result.action.toFailureStage(),
            error = result.error,
            content = result.content,
            subscriptionInfo = result.subscriptionInfo,
            updateIntervalMillis = result.updateIntervalMillis,
        )
    }
}

internal suspend fun prepareMihomoProfileSubscription(
    profile: MihomoProfileState,
    profilePreparer: AndroidMihomoProfilePreparer,
    fetchOptions: AndroidSubscriptionFetchOptions,
    onStage: (MihomoProfileSyncStage) -> Unit = {},
): MihomoProfilePreparation {
    return prepareMihomoProfile(
        profile = profile,
        localContent = null,
        profilePreparer = profilePreparer,
        fetchOptions = fetchOptions,
        onStage = onStage,
    )
}

private fun FetchStatus.Action.toSyncStageOrNull(): MihomoProfileSyncStage? {
    return when (this) {
        FetchStatus.Action.FetchConfiguration -> MihomoProfileSyncStage.Downloading
        FetchStatus.Action.Decrypting -> MihomoProfileSyncStage.Decrypting
        FetchStatus.Action.FetchProviders -> MihomoProfileSyncStage.PreparingProviders
        FetchStatus.Action.Verifying -> MihomoProfileSyncStage.Verifying
        FetchStatus.Action.SubscriptionInfo -> null
    }
}

private fun FetchStatus.Action.toFailureStage(): MihomoProfileSyncStage {
    return toSyncStageOrNull() ?: MihomoProfileSyncStage.Downloading
}
