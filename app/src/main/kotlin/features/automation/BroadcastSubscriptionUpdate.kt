// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.automation

import app.AsteriskApplication
import app.MihomoProfileType
import features.logs.AndroidAppLogger
import features.subscription.usecase.commitMihomoProfileSubscriptionUpdates
import features.subscription.usecase.toSubscriptionFetchOptions
import features.subscription.usecase.updateSubscriptions
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun AsteriskApplication.updateBroadcastSubscriptions(progress: BroadcastUpdateProgress): Boolean {
    val ids = stateStore.state.value.mihomoProfiles
        .filter { it.type == MihomoProfileType.Url && it.url.isNotBlank() }.map { it.id }
    var failures = 0
    for (id in ids) {
        currentCoroutineContext().ensureActive()
        if (progress.completed(id.toString())) continue
        val profile = stateStore.state.value.mihomoProfiles.firstOrNull { it.id == id } ?: continue
        if (profile.type != MihomoProfileType.Url || profile.url.isBlank()) continue
        val result = updateSubscriptions(
            profiles = listOf(profile),
            profilePreparer = mihomoProfilePreparer,
            contentStore = mihomoProfileContentStore,
            fetchOptions = { stateStore.state.value.toSubscriptionFetchOptions(it) },
            onProfileCompleted = { _, profileResult, completedAtMillis ->
                profileResult.getOrNull()?.let { update ->
                    commitMihomoProfileSubscriptionUpdates(
                        requireScheduled = false,
                        updates = listOf(update),
                        updatedAtMillis = completedAtMillis,
                        contentStore = mihomoProfileContentStore,
                        updateAppState = { stateStore.update(it) },
                    )
                }
            },
        )
        currentCoroutineContext().ensureActive()
        stateStore.awaitPersistence()
        progress.record(id.toString(), result.failures.isEmpty())
        failures += result.failedProfileCount
        AndroidAppLogger.info("BroadcastControl", "Subscription item=$id success=${result.failures.isEmpty()}")
    }
    return failures == 0 && progress.succeeded()
}
