// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import app.AppState
import app.MihomoProfileType
import features.subscription.SubscriptionSchedule
import features.subscription.isTransientSubscriptionFailure
import features.subscription.parseSubscriptionSchedule
import features.subscription.usecase.MihomoProfileSubscriptionUpdateResult
import features.subscription.usecase.MihomoProfileSyncStage

internal enum class SubscriptionWorkerResult {
    SUCCESS,
    RETRY,
    FAILURE,
}

internal class SubscriptionWorkerRunner(
    private val stateProvider: () -> AppState,
    private val update: suspend (profileId: Int) -> MihomoProfileSubscriptionUpdateResult,
) {
    suspend fun run(profileId: Int): SubscriptionWorkerResult {
        val profile = stateProvider().mihomoProfiles.firstOrNull { it.id == profileId }
        if (
            profile == null ||
            profile.type != MihomoProfileType.Url ||
            !profile.enabled ||
            profile.url.isBlank() ||
            parseSubscriptionSchedule(profile.updateInterval) !is SubscriptionSchedule.Enabled
        ) {
            return SubscriptionWorkerResult.SUCCESS
        }

        val result = update(profileId)
        if (result.failures.isEmpty()) return SubscriptionWorkerResult.SUCCESS
        return if (
            result.failures.any { failure ->
                failure.stage in RetryableNetworkStages &&
                    isTransientSubscriptionFailure(failure.error)
            }
        ) {
            SubscriptionWorkerResult.RETRY
        } else {
            SubscriptionWorkerResult.FAILURE
        }
    }

    private companion object {
        val RetryableNetworkStages = setOf(
            MihomoProfileSyncStage.Downloading,
            MihomoProfileSyncStage.PreparingProviders,
        )
    }
}
