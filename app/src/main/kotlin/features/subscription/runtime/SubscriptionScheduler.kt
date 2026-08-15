// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import app.MihomoProfileState
import app.MihomoProfileType
import features.subscription.SubscriptionSchedule
import features.subscription.parseSubscriptionSchedule

internal enum class SubscriptionExistingWorkPolicy {
    UPDATE,
}

internal data class SubscriptionWorkSpec(
    val profileId: Int,
    val uniqueName: String,
    val repeatIntervalMillis: Long,
    val requiresConnectedNetwork: Boolean,
    val policy: SubscriptionExistingWorkPolicy,
    val backoffMillis: Long,
)

internal interface SubscriptionScheduleGateway {
    fun scheduledProfileIds(): Set<Int>

    fun enqueue(spec: SubscriptionWorkSpec)

    fun cancel(profileId: Int)

    fun storeScheduledProfileIds(profileIds: Set<Int>)
}

internal class SubscriptionScheduler(
    private val gateway: SubscriptionScheduleGateway,
) {
    fun reconcile(profiles: List<MihomoProfileState>) {
        val desired = profiles.mapNotNull { profile ->
            val schedule = parseSubscriptionSchedule(profile.updateInterval)
            if (
                profile.type != MihomoProfileType.Url ||
                !profile.enabled ||
                profile.url.isBlank() ||
                schedule !is SubscriptionSchedule.Enabled
            ) {
                null
            } else {
                SubscriptionWorkSpec(
                    profileId = profile.id,
                    uniqueName = subscriptionWorkName(profile.id),
                    repeatIntervalMillis = schedule.repeatIntervalMillis,
                    requiresConnectedNetwork = true,
                    policy = SubscriptionExistingWorkPolicy.UPDATE,
                    backoffMillis = MinimumSubscriptionBackoffMillis,
                )
            }
        }
        val desiredIds = desired.mapTo(mutableSetOf()) { it.profileId }
        (gateway.scheduledProfileIds() - desiredIds).forEach(gateway::cancel)
        desired.forEach(gateway::enqueue)
        gateway.storeScheduledProfileIds(desiredIds)
    }
}

internal fun subscriptionWorkName(profileId: Int): String = "subscription-update-$profileId"

private const val MinimumSubscriptionBackoffMillis = 15 * 60 * 1_000L
