// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import android.content.Intent
import android.net.Uri
import app.DefaultMihomoProfileUpdateInterval
import app.MihomoProfileState
import app.MihomoProfileType
import app.nextAvailableMihomoProfileId
import data.AndroidAppStateStore
import engine.mihomo.MihomoProfileContentStore
import features.subscription.runtime.AndroidMihomoProfilePreparer
import features.subscription.usecase.MihomoProfileSubscriptionUpdateResult
import features.subscription.usecase.commitMihomoProfileSubscriptionUpdates
import features.subscription.usecase.toSubscriptionFetchOptions
import features.subscription.usecase.updateSubscriptions
import io.ktor.http.Url
import utils.decodeUrlComponentPreservingPlus

internal data class SubscriptionInstallConfig(
    val name: String,
    val url: String,
    val userAgent: String,
    val updateInterval: String = DefaultMihomoProfileUpdateInterval,
    val updateViaProxy: Boolean = false,
)

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
        val result = updateSubscriptions(
            profiles = listOf(profile),
            profilePreparer = profilePreparer,
            contentStore = contentStore,
            fetchOptions = { stateStore.state.value.toSubscriptionFetchOptions(it) },
            onProfileCompleted = { _, profileResult, completedAtMillis ->
                profileResult.getOrNull()?.let { update ->
                    commitMihomoProfileSubscriptionUpdates(
                        updates = listOf(update),
                        updatedAtMillis = completedAtMillis,
                        contentStore = contentStore,
                        updateAppState = { transform -> stateStore.update(transform) },
                    )
                }
            },
        )
        return SubscriptionInstallResult(profile = profile, updateResult = result)
    }
}

internal fun Intent.toSubscriptionInstallConfigOrNull(): SubscriptionInstallConfig? {
    if (action != Intent.ACTION_VIEW) return null
    return data?.toString()?.toSubscriptionInstallConfigOrNull()
}

internal fun String.toSubscriptionInstallConfigOrNull(): SubscriptionInstallConfig? {
    val value = trim()
    if (value.any(Char::isWhitespace)) return null
    val url = runCatching { Url(value) }.getOrNull() ?: return null
    return url.toSubscriptionInstallConfigOrNull(value)
}

internal fun String.isValidManualSubscriptionUrl(): Boolean {
    return isValidSubscriptionUrl(ManualSubscriptionUrlSchemes)
}

internal fun String.isPlainHttpSubscriptionUrl(): Boolean {
    val url = toSubscriptionUrlOrNull() ?: return false
    return url.protocol.name.equals("http", ignoreCase = true)
}

internal fun Uri.isSubscriptionInstallConfigUri(): Boolean {
    return runCatching { Url(toString()).isSubscriptionInstallConfigUri() }
        .getOrDefault(false)
}

private fun Url.toSubscriptionInstallConfigOrNull(rawValue: String): SubscriptionInstallConfig? {
    toRawSubscriptionInstallConfigOrNull(
        rawValue = rawValue,
        schemes = HttpsSubscriptionUrlSchemes,
    )?.let { return it }
    val url = parameters["url"]?.trim().orEmpty()
    if (!isSubscriptionInstallConfigUri() || !url.isValidSubscriptionUrl()) return null
    // Ktor has already decoded query parameters; preserve literal percent escapes in names.
    val name = parameters["name"]?.trim()?.takeIf(String::isNotBlank) ?: listOfNotNull(
        fragment,
        url.toSubscriptionUrlFragmentOrNull(),
        DefaultSubscriptionName,
    )
        .firstNotNullOfOrNull { value -> value.trim().decodeUrlComponentPreservingPlus().takeIf(String::isNotBlank) }
        ?: return null
    return SubscriptionInstallConfig(
        name = name,
        url = url,
        userAgent = app.DefaultMihomoProfileUserAgent,
    )
}

private fun Url.toRawSubscriptionInstallConfigOrNull(
    rawValue: String,
    schemes: Set<String>,
): SubscriptionInstallConfig? {
    if (!rawValue.isValidSubscriptionUrl(schemes)) return null
    val name = listOfNotNull(fragment, DefaultSubscriptionName)
        .firstNotNullOfOrNull { value -> value.trim().decodeUrlComponentPreservingPlus().takeIf(String::isNotBlank) }
        ?: return null
    return SubscriptionInstallConfig(
        name = name,
        url = rawValue,
        userAgent = app.DefaultMihomoProfileUserAgent,
    )
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

private fun String.isValidSubscriptionUrl(
    schemes: Set<String> = HttpsSubscriptionUrlSchemes,
): Boolean {
    val url = toSubscriptionUrlOrNull() ?: return false
    val scheme = url.protocol.name.lowercase()
    return url.host.isNotBlank() &&
        scheme in schemes &&
        this.any(Char::isWhitespace).not()
}

private fun String.toSubscriptionUrlOrNull(): Url? {
    val value = trim()
    if (value.any(Char::isWhitespace)) return null
    return runCatching { Url(value) }.getOrNull()
}

private fun Url.isSubscriptionInstallConfigUri(): Boolean {
    return protocol.name.lowercase() in InstallConfigSchemes &&
        host.lowercase() in InstallConfigHosts
}

private fun String.toSubscriptionUrlFragmentOrNull(): String? {
    return runCatching { Url(this).fragment }.getOrNull()
}

private const val DefaultSubscriptionName = "clashsub"
private val InstallConfigSchemes = setOf("clash", "clashmeta")
private val InstallConfigHosts = setOf("install-config", "install-sub")
private val HttpsSubscriptionUrlSchemes = setOf("https")
private val ManualSubscriptionUrlSchemes = setOf("http", "https")
