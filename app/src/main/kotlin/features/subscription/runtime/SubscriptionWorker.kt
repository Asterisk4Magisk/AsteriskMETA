// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.AsteriskApplication
import features.subscription.usecase.MihomoProfileSubscriptionUpdateResult
import features.subscription.usecase.commitMihomoProfileSubscriptionUpdates
import features.subscription.usecase.toSubscriptionFetchOptions
import features.subscription.usecase.updateSubscriptions

internal class SubscriptionWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val profileId = inputData.getInt(SubscriptionProfileIdKey, MissingProfileId)
        if (profileId == MissingProfileId) return Result.success()
        val application = applicationContext as? AsteriskApplication ?: return Result.failure()
        val runner = SubscriptionWorkerRunner(
            stateProvider = { application.stateStore.state.value },
            update = { requestedProfileId -> application.updateSubscription(requestedProfileId) },
        )
        return when (runner.run(profileId)) {
            SubscriptionWorkerResult.SUCCESS -> Result.success()
            SubscriptionWorkerResult.RETRY -> Result.retry()
            SubscriptionWorkerResult.FAILURE -> Result.failure()
        }
    }

    private suspend fun AsteriskApplication.updateSubscription(
        profileId: Int,
    ): MihomoProfileSubscriptionUpdateResult {
        val profile = stateStore.state.value.mihomoProfiles.firstOrNull { it.id == profileId }
            ?: return MihomoProfileSubscriptionUpdateResult(
                updates = emptyList(),
                failures = emptyList(),
                updatedAtMillis = 0L,
            )
        val result = updateSubscriptions(
            profiles = listOf(profile),
            profilePreparer = mihomoProfilePreparer,
            contentStore = mihomoProfileContentStore,
            fetchOptions = { currentProfile ->
                stateStore.state.value.toSubscriptionFetchOptions(currentProfile)
            },
            onProfileCompleted = { _, profileResult, completedAtMillis ->
                profileResult.getOrNull()?.let { update ->
                    commitMihomoProfileSubscriptionUpdates(
                        updates = listOf(update),
                        updatedAtMillis = completedAtMillis,
                        contentStore = mihomoProfileContentStore,
                        updateAppState = { transform -> stateStore.update(transform) },
                    )
                }
            },
        )
        return result
    }

    private companion object {
        const val MissingProfileId = Int.MIN_VALUE
    }
}
