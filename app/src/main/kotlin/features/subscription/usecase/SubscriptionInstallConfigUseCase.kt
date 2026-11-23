// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.usecase

import app.MihomoProfileState
import app.MihomoProfileType
import app.nextAvailableMihomoProfileId
import data.AndroidAppStateStore
import engine.mihomo.MihomoProfileContentStore
import features.subscription.SubscriptionInstallConfig
import features.subscription.runtime.AndroidMihomoProfilePreparer

internal data class SubscriptionInstallResult(
    val profile: MihomoProfileState,
    val updateResult: MihomoProfileSubscriptionUpdateResult,
)

internal class SubscriptionInstallConfigUseCase(
    private val stateStore: AndroidAppStateStore,
    private val profilePreparer: AndroidMihomoProfilePreparer,
    private val contentStore: MihomoProfileContentStore,
) {
    suspend fun install(config: SubscriptionInstallConfig): SubscriptionInstallResult {
        val profile = stateStore.addMihomoProfile(config)
        val acceptedProfileIds = mutableSetOf<Int>()
        val result = updateSubscriptions(
            profiles = listOf(profile),
            profilePreparer = profilePreparer,
            contentStore = contentStore,
            fetchOptions = { stateStore.state.value.toSubscriptionFetchOptions(it) },
            onProfileCompleted = { _, profileResult, completedAtMillis ->
                profileResult.getOrNull()?.let { update ->
                    acceptedProfileIds += commitMihomoProfileSubscriptionUpdates(
                        updates = listOf(update),
                        updatedAtMillis = completedAtMillis,
                        contentStore = contentStore,
                        updateAppState = { transform -> stateStore.update(transform) },
                        // A new install has no update schedule by default; its first content
                        // must still land.
                        requireScheduled = false,
                    )
                }
            },
        )
        return SubscriptionInstallResult(
            profile = profile,
            updateResult = result.withAcceptedUpdatesOnly(acceptedProfileIds),
        )
    }
}

private fun AndroidAppStateStore.addMihomoProfile(config: SubscriptionInstallConfig): MihomoProfileState {
    var savedProfile: MihomoProfileState? = null
    update { state ->
        val profileId = state.nextAvailableMihomoProfileId()
        val profile = newMihomoProfile(config, profileId)
        val shouldSelectProfile = state.mihomoProfiles.isEmpty()
        savedProfile = profile
        state.copy(
            mihomoProfiles = state.mihomoProfiles + profile,
            nextMihomoProfileId = profileId + 1,
            selectedMihomoProfileId = if (shouldSelectProfile) {
                profile.id
            } else {
                state.selectedMihomoProfileId
            },
        )
    }
    return checkNotNull(savedProfile)
}

private fun newMihomoProfile(config: SubscriptionInstallConfig, profileId: Int): MihomoProfileState {
    return MihomoProfileState(
        id = profileId,
        name = config.name,
        type = MihomoProfileType.Url,
        url = config.url,
        userAgent = config.userAgent,
        updateInterval = config.updateInterval,
        updateViaProxy = config.updateViaProxy,
        enabled = true,
    )
}
