// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources

import android.content.Context
import android.net.Uri
import app.ResourceFileKind
import app.ResourceFileUpdateSource
import app.ResourceFilesStatus
import features.resources.runtime.AndroidResourceFileRepository
import system.AndroidRootShellGateway
import system.RootShellGateway

class ResourceFileUseCase(
    context: Context,
    private val resourceFilePicker: suspend () -> Uri?,
    currentRunMode: () -> Int,
    rootShell: RootShellGateway = AndroidRootShellGateway(),
) {
    private val repository = AndroidResourceFileRepository(
        context = context.applicationContext,
        currentRunMode = currentRunMode,
        rootShell = rootShell,
    )

    suspend fun status(): ResourceFilesStatus {
        return repository.status()
    }

    suspend fun hasCustomMihomoCore(): Boolean = repository.hasCustomMihomoCore()

    suspend fun restoreBundledDefaults(resourceFileSource: Int): ResourceFilesStatus {
        return repository.restoreBundledDefaults(resourceFileSource)
    }

    suspend fun update(
        source: ResourceFileUpdateSource,
        options: ResourceFileUpdateOptions = ResourceFileUpdateOptions(),
    ): ResourceFilesStatus {
        return repository.update(source, options)
    }

    suspend fun update(
        kind: ResourceFileKind,
        source: ResourceFileUpdateSource,
        options: ResourceFileUpdateOptions = ResourceFileUpdateOptions(),
    ): ResourceFilesStatus {
        return repository.update(kind, source, options)
    }

    suspend fun replace(
        kind: ResourceFileKind,
    ): ResourceFilesStatus? {
        val uri = resourceFilePicker() ?: return null
        return repository.replace(kind, uri)
    }

    suspend fun restoreBundled(
        kind: ResourceFileKind,
    ): ResourceFilesStatus {
        return repository.restoreBundled(kind)
    }

}

data class ResourceFileUpdateOptions(
    val useRunningProxy: Boolean = false,
    val fallbackProxyPort: Int? = null,
    val fallbackProxyUsername: String = "",
    val fallbackProxyPassword: String = "",
)
