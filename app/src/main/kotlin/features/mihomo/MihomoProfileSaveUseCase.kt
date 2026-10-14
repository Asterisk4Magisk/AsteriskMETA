// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import app.MihomoProfileState
import app.MihomoProfileType
import app.MihomoSubscriptionInfo
import engine.mihomo.MihomoProfileContentRef
import engine.mihomo.MihomoProfileContentStore
import features.subscription.runtime.AndroidMihomoProfilePreparer
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import features.subscription.usecase.MihomoProfilePreparation
import features.subscription.usecase.MihomoProfileSyncStage
import features.subscription.usecase.prepareMihomoProfile
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import utils.resolveSubscriptionName

internal data class MihomoProfileSaveDraft(
    val desiredProfile: MihomoProfileState,
    val originalProfile: MihomoProfileState?,
    val localContent: String? = null,
    val contentChanged: Boolean = false,
    val remoteOptionsChanged: Boolean = false,
    // Names of the other profiles, used to disambiguate an auto-assigned name.
    val otherProfileNames: Collection<String> = emptyList(),
)

internal sealed interface MihomoProfileSavePreparation {
    data class Success(
        val content: String?,
        val subscriptionInfo: MihomoSubscriptionInfo? = null,
        val updateIntervalMillis: Long? = null,
        val synchronized: Boolean,
        val remoteName: String? = null,
    ) : MihomoProfileSavePreparation

    data class Failure(
        val stage: MihomoProfileSyncStage,
        val error: Throwable,
        val content: String? = null,
        val subscriptionInfo: MihomoSubscriptionInfo? = null,
        val updateIntervalMillis: Long? = null,
        val remoteName: String? = null,
    ) : MihomoProfileSavePreparation
}

internal class MihomoProfileSaveUseCase(
    private val prepareProfile: suspend (
        MihomoProfileState,
        String?,
        AndroidSubscriptionFetchOptions,
        (MihomoProfileSyncStage) -> Unit,
    ) -> MihomoProfilePreparation,
    private val writeContent: suspend (MihomoProfileState?, String) -> MihomoProfileContentRef,
    private val deleteContent: suspend (MihomoProfileState) -> Unit,
    private val nowMillis: () -> Long,
    private val formatDuplicateName: (name: String, ordinal: Int) -> String,
) {
    suspend fun prepare(
        draft: MihomoProfileSaveDraft,
        fetchOptions: AndroidSubscriptionFetchOptions,
        onStage: (MihomoProfileSyncStage) -> Unit = {},
    ): MihomoProfileSavePreparation {
        return when (draft.desiredProfile.type) {
            MihomoProfileType.Url -> prepareUrl(draft, fetchOptions, onStage)
            MihomoProfileType.File -> prepareFile(draft, fetchOptions, onStage)
        }
    }

    suspend fun commit(
        draft: MihomoProfileSaveDraft,
        preparation: MihomoProfileSavePreparation,
    ): MihomoProfileState {
        val desired = draft.desiredProfile
        val original = draft.originalProfile
        val failed = preparation is MihomoProfileSavePreparation.Failure
        val preparedContent = preparation.contentOrNull()
        val urlChanged = original != null && desired.url != original.url
        val clearContent = when (desired.type) {
            MihomoProfileType.Url -> failed && preparedContent == null && (original == null || urlChanged)
            MihomoProfileType.File -> preparedContent?.isBlank() == true
        }
        val contentRef = when {
            clearContent -> {
                if (original?.hasContent == true) {
                    deleteContent(original)
                }
                null
            }

            preparedContent != null -> writeContent(original, preparedContent)
            else -> original?.takeIf { profile -> profile.hasContent }?.let { profile ->
                MihomoProfileContentRef(
                    path = profile.contentPath,
                    sha256 = profile.contentSha256,
                    sizeBytes = profile.contentSizeBytes,
                )
            }
        }
        val preparedSubscriptionInfo = preparation.subscriptionInfoOrNull()
        val subscriptionInfo = when {
            preparedSubscriptionInfo != null -> preparedSubscriptionInfo
            desired.type == MihomoProfileType.Url && urlChanged && clearContent -> MihomoSubscriptionInfo()
            else -> original?.subscriptionInfo ?: desired.subscriptionInfo
        }
        val lastUpdatedAtMillis = when {
            failed -> original?.lastUpdatedAtMillis ?: 0L
            preparation.isSynchronized() -> nowMillis()
            desired.type == MihomoProfileType.File && draft.contentChanged -> nowMillis()
            else -> original?.lastUpdatedAtMillis ?: desired.lastUpdatedAtMillis
        }
        val name = resolveAutoAssignedName(
            draft = draft,
            remoteName = preparation.remoteNameOrNull(),
        ) ?: desired.name
        return desired.copy(
            name = name,
            contentPath = contentRef?.path.orEmpty(),
            contentSha256 = contentRef?.sha256.orEmpty(),
            contentSizeBytes = contentRef?.sizeBytes ?: 0L,
            subscriptionInfo = subscriptionInfo,
            lastUpdatedAtMillis = lastUpdatedAtMillis,
            syncFailed = failed,
        )
    }

    private suspend fun prepareUrl(
        draft: MihomoProfileSaveDraft,
        fetchOptions: AndroidSubscriptionFetchOptions,
        onStage: (MihomoProfileSyncStage) -> Unit,
    ): MihomoProfileSavePreparation {
        val original = draft.originalProfile
        // A blank name is auto-assigned from the response, so it needs a fetch.
        val requiresSync = original == null ||
            draft.remoteOptionsChanged ||
            draft.desiredProfile.name.isBlank() ||
            !original.hasContent ||
            original.syncFailed
        if (!requiresSync) {
            return MihomoProfileSavePreparation.Success(
                content = null,
                synchronized = false,
            )
        }
        return when (val result = prepareProfile(draft.desiredProfile, null, fetchOptions, onStage)) {
            is MihomoProfilePreparation.Success -> MihomoProfileSavePreparation.Success(
                content = result.content,
                subscriptionInfo = result.subscriptionInfo,
                updateIntervalMillis = result.updateIntervalMillis,
                synchronized = true,
                remoteName = result.remoteName,
            )

            is MihomoProfilePreparation.Failure -> MihomoProfileSavePreparation.Failure(
                stage = result.stage,
                error = result.error,
                content = result.content,
                subscriptionInfo = result.subscriptionInfo,
                updateIntervalMillis = result.updateIntervalMillis,
                remoteName = result.remoteName,
            )
        }
    }

    /**
     * Resolve a profile name when the user left it blank on a URL profile.
     *
     * Returns `null` when the user supplied a name, so later saves never rename
     * the profile. Otherwise prefers the server's `Content-Disposition` filename
     * and falls back to the URL host; see utils.SubscriptionNaming, shared with
     * AsteriskBOX's outbound group naming.
     */
    private fun resolveAutoAssignedName(
        draft: MihomoProfileSaveDraft,
        remoteName: String?,
    ): String? {
        val desired = draft.desiredProfile
        if (desired.type != MihomoProfileType.Url || desired.name.isNotBlank()) return null
        return resolveSubscriptionName(
            preferredName = remoteName,
            subscriptionUrl = desired.url,
            takenNames = draft.otherProfileNames,
            formatDuplicate = formatDuplicateName,
        ) ?: desired.url
    }

    private suspend fun prepareFile(
        draft: MihomoProfileSaveDraft,
        fetchOptions: AndroidSubscriptionFetchOptions,
        onStage: (MihomoProfileSyncStage) -> Unit,
    ): MihomoProfileSavePreparation {
        val content = checkNotNull(draft.localContent)
        val requiresPreparation = draft.contentChanged || draft.originalProfile?.syncFailed == true
        if (!requiresPreparation) {
            return MihomoProfileSavePreparation.Success(
                content = null,
                synchronized = false,
            )
        }
        return when (val result = prepareProfile(draft.desiredProfile, content, fetchOptions, onStage)) {
            is MihomoProfilePreparation.Success -> MihomoProfileSavePreparation.Success(
                content = content.takeIf { draft.contentChanged },
                updateIntervalMillis = result.updateIntervalMillis,
                synchronized = true,
            )

            is MihomoProfilePreparation.Failure -> MihomoProfileSavePreparation.Failure(
                stage = result.stage,
                error = result.error,
                content = content,
                updateIntervalMillis = result.updateIntervalMillis,
            )
        }
    }

    companion object {
        fun create(
            profilePreparer: AndroidMihomoProfilePreparer,
            contentStore: MihomoProfileContentStore,
            formatDuplicateName: (name: String, ordinal: Int) -> String,
        ): MihomoProfileSaveUseCase {
            return MihomoProfileSaveUseCase(
                prepareProfile = { profile, localContent, fetchOptions, onStage ->
                    prepareMihomoProfile(
                        profile = profile,
                        localContent = localContent,
                        profilePreparer = profilePreparer,
                        fetchOptions = fetchOptions,
                        onStage = onStage,
                    )
                },
                writeContent = { original, content ->
                    withContext(Dispatchers.IO) {
                        if (original == null) {
                            contentStore.writeNew(content)
                        } else {
                            contentStore.write(original, content)
                        }
                    }
                },
                deleteContent = { profile ->
                    withContext(Dispatchers.IO) {
                        contentStore.delete(profile)
                    }
                },
                nowMillis = { Clock.System.now().toEpochMilliseconds() },
                formatDuplicateName = formatDuplicateName,
            )
        }
    }
}

private fun MihomoProfileSavePreparation.contentOrNull(): String? {
    return when (this) {
        is MihomoProfileSavePreparation.Success -> content
        is MihomoProfileSavePreparation.Failure -> content
    }
}

private fun MihomoProfileSavePreparation.subscriptionInfoOrNull(): MihomoSubscriptionInfo? {
    return when (this) {
        is MihomoProfileSavePreparation.Success -> subscriptionInfo
        is MihomoProfileSavePreparation.Failure -> subscriptionInfo
    }
}

private fun MihomoProfileSavePreparation.remoteNameOrNull(): String? {
    return when (this) {
        is MihomoProfileSavePreparation.Success -> remoteName
        is MihomoProfileSavePreparation.Failure -> remoteName
    }
}

private fun MihomoProfileSavePreparation.isSynchronized(): Boolean {
    return this is MihomoProfileSavePreparation.Success && synchronized
}
