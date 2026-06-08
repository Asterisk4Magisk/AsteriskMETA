// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.AppState
import data.AndroidAppStateStore
import engine.mihomo.MihomoProfileContentStore
import features.subscription.AutoSubscriptionCheckIntervalMillis
import features.subscription.AutoSubscriptionRetryDelayMillis
import features.subscription.runtime.AndroidMihomoProviderFetcher
import features.subscription.runtime.AndroidSubscriptionFetcher
import features.subscription.usecase.dueSubscriptionProfiles
import features.subscription.usecase.toSubscriptionFetchOptions
import features.subscription.usecase.updateSubscriptions
import features.subscription.usecase.withUpdatedMihomoProfiles
import kotlinx.coroutines.delay
import kotlin.time.Clock

@Composable
internal fun SubscriptionAutoUpdater(
    stateStore: AndroidAppStateStore,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    contentStore: MihomoProfileContentStore,
    providerFetcher: AndroidMihomoProviderFetcher,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    LaunchedEffect(stateStore, subscriptionFetcher, contentStore, providerFetcher) {
        val lastAttemptMillisByProfileId = mutableMapOf<Int, Long>()
        while (true) {
            val currentState = stateStore.state.value
            val nowMillis = Clock.System.now().toEpochMilliseconds()
            val dueProfiles = currentState.mihomoProfiles
                .dueSubscriptionProfiles(nowMillis)
                .filter { profile ->
                    nowMillis - (lastAttemptMillisByProfileId[profile.id] ?: 0L) >= AutoSubscriptionRetryDelayMillis
                }
            if (dueProfiles.isNotEmpty()) {
                dueProfiles.forEach { profile -> lastAttemptMillisByProfileId[profile.id] = nowMillis }
                val result = updateSubscriptions(
                    profiles = dueProfiles,
                    subscriptionFetcher = subscriptionFetcher,
                    contentStore = contentStore,
                    providerFetcher = providerFetcher,
                    fetchOptions = { profile -> currentState.toSubscriptionFetchOptions(profile) },
                )
                if (result.updates.isNotEmpty()) {
                    updateAppState { state ->
                        state.withUpdatedMihomoProfiles(
                            updates = result.updates,
                            updatedAtMillis = result.updatedAtMillis,
                        )
                    }
                }
            }
            delay(AutoSubscriptionCheckIntervalMillis)
        }
    }
}
