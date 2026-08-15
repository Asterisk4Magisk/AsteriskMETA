// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import android.content.Context
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

internal class AndroidSubscriptionScheduleGateway(
    context: Context,
) : SubscriptionScheduleGateway {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val preferences = appContext.getSharedPreferences(
        SubscriptionSchedulePreferences,
        Context.MODE_PRIVATE,
    )

    override fun scheduledProfileIds(): Set<Int> =
        preferences.getStringSet(ScheduledProfileIdsKey, emptySet())
            .orEmpty()
            .mapNotNullTo(mutableSetOf(), String::toIntOrNull)

    override fun enqueue(spec: SubscriptionWorkSpec) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequest.Builder(
            SubscriptionWorker::class.java,
            spec.repeatIntervalMillis,
            TimeUnit.MILLISECONDS,
        )
            .setInputData(workDataOf(SubscriptionProfileIdKey to spec.profileId))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                spec.backoffMillis,
                TimeUnit.MILLISECONDS,
            )
            .addTag(SubscriptionWorkTag)
            .build()
        workManager.enqueueUniquePeriodicWork(
            spec.uniqueName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun cancel(profileId: Int) {
        workManager.cancelUniqueWork(subscriptionWorkName(profileId))
    }

    override fun storeScheduledProfileIds(profileIds: Set<Int>) {
        preferences.edit {
            putStringSet(ScheduledProfileIdsKey, profileIds.mapTo(mutableSetOf(), Int::toString))
        }
    }
}

internal const val SubscriptionProfileIdKey = "subscription_profile_id"
private const val SubscriptionWorkTag = "subscription-update"
private const val SubscriptionSchedulePreferences = "subscription_schedule"
private const val ScheduledProfileIdsKey = "scheduled_profile_ids"
