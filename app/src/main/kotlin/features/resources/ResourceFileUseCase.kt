// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources

import android.content.Context
import android.net.Uri
import app.CustomResourceFileState
import app.ResourceFileKind
import app.ResourceFileUpdateSource
import app.ResourceFilesStatus
import features.resources.runtime.AndroidResourceFileRepository

class ResourceFileUseCase(
    context: Context,
    private val resourceFilePicker: suspend () -> Uri?,
) {
    private val repository = AndroidResourceFileRepository(context.applicationContext)

    suspend fun status(customResourceFiles: List<CustomResourceFileState> = emptyList()): ResourceFilesStatus {
        return repository.status(customResourceFiles)
    }

    suspend fun restoreBundledDefaults(): ResourceFilesStatus {
        return repository.restoreBundledDefaults()
    }

    suspend fun update(
        source: ResourceFileUpdateSource,
        options: ResourceFileUpdateOptions = ResourceFileUpdateOptions(),
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus {
        return repository.update(source, options, customResourceFiles)
    }

    suspend fun renameCustom(
        previousFile: CustomResourceFileState,
        customFile: CustomResourceFileState,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus {
        return repository.renameCustom(previousFile, customFile, customResourceFiles)
    }

    suspend fun replace(
        kind: ResourceFileKind,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus? {
        val uri = resourceFilePicker() ?: return null
        return repository.replace(kind, uri, customResourceFiles)
    }

    suspend fun replaceCustom(
        customFile: CustomResourceFileState,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus? {
        val uri = resourceFilePicker() ?: return null
        return repository.replaceCustom(customFile, uri, customResourceFiles)
    }

    suspend fun restoreBundled(
        kind: ResourceFileKind,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus {
        return repository.restoreBundled(kind, customResourceFiles)
    }

    suspend fun deleteCustom(
        customFile: CustomResourceFileState,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus {
        return repository.deleteCustom(customFile, customResourceFiles)
    }
}

data class ResourceFileUpdateOptions(
    val useRunningProxy: Boolean = false,
    val fallbackProxyPort: Int? = null,
    val fallbackProxyUsername: String = "",
    val fallbackProxyPassword: String = "",
)
