// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import app.ProjectInfo
import engine.mihomo.MihomoProfileContentRef
import engine.mihomo.MihomoProfileContentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal interface AppBackupDocumentGateway {
    suspend fun readText(): String?

    suspend fun writeText(
        defaultFileName: String,
        content: String,
    ): Boolean
}

internal class AppBackupUseCase(
    private val gateway: AppBackupDocumentGateway,
    private val profileContentStore: MihomoProfileContentStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val appVersionName: String = ProjectInfo.VERSION_NAME,
    private val appVersionCode: Int = ProjectInfo.VERSION_CODE,
    private val fileNameFactory: (Long) -> String = ::defaultBackupFileName,
    private val maxBackupFileBytes: Int = MaxAppBackupFileBytes,
) {
    suspend fun export(state: AppState): Boolean {
        val createdAtMillis = clock()
        val profileContents = withContext(Dispatchers.IO) {
            state.mihomoProfiles
                .filter { profile -> profile.hasContent }
                .associate { profile -> profile.id to profileContentStore.readOrEmpty(profile) }
                .filterValues(String::isNotBlank)
        }
        val backup = state.toAppBackupFile(
            createdAtMillis = createdAtMillis,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            profileContents = profileContents,
        )
        val content = encodeAppBackup(backup)
        require(content.hasUtf8SizeAtMost(maxBackupFileBytes)) {
            "Backup file exceeds the $maxBackupFileBytes-byte limit"
        }
        return gateway.writeText(
            defaultFileName = fileNameFactory(createdAtMillis),
            content = content,
        )
    }

    suspend fun readRestorePreview(): AppBackupRestorePreview? =
        gateway.readText()?.let { content ->
            decodeAppBackup(content).toRestorePreview()
        }

    /**
     * Write the backed-up profile contents to fresh files, then hand the
     * completed state to [replaceState]. Files written for a failed replacement
     * are removed; after a successful one, files no longer referenced by any
     * profile (the replaced profiles' contents) are pruned.
     */
    suspend fun applyRestore(
        preview: AppBackupRestorePreview,
        replaceState: suspend (AppState) -> Unit,
    ) {
        val writtenContents = mutableListOf<MihomoProfileContentRef>()
        val restoredState = try {
            val profiles = withContext(Dispatchers.IO) {
                preview.restoredState.mihomoProfiles.map { profile ->
                    val content = preview.profileContents[profile.id] ?: return@map profile
                    val contentRef = profileContentStore.writeNew(content)
                    writtenContents += contentRef
                    profile.copy(
                        contentPath = contentRef.path,
                        contentSha256 = contentRef.sha256,
                        contentSizeBytes = contentRef.sizeBytes,
                    )
                }
            }
            preview.restoredState.copy(mihomoProfiles = profiles).also { state -> replaceState(state) }
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                writtenContents.forEach(profileContentStore::delete)
            }
            throw error
        }
        withContext(Dispatchers.IO) {
            profileContentStore.pruneUnreferenced(
                restoredState.mihomoProfiles
                    .mapNotNullTo(mutableSetOf()) { profile -> profile.contentPath.takeIf(String::isNotBlank) },
            )
        }
    }
}

internal fun String.hasUtf8SizeAtMost(maxBytes: Int): Boolean {
    require(maxBytes > 0)
    return length <= maxBytes && toByteArray(Charsets.UTF_8).size <= maxBytes
}

internal fun defaultBackupFileName(
    createdAtMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
        this.timeZone = timeZone
    }
    return "asteriskmeta-backup-${formatter.format(Date(createdAtMillis))}.json"
}
