// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import android.content.Context
import android.net.Uri
import app.R
import app.CustomResourceFileState
import app.ResourceFileKind
import app.ResourceFileUpdateSource
import app.ResourceFilesStatus
import app.urlFor
import app.modes.isRootRunMode
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime
import engine.network.isPort
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import features.resources.ResourceFileUpdateOptions
import engine.root.publication.RootCoreRemovalCommand
import engine.root.runtime.RootSupervisorController
import system.AndroidRootShellGateway
import system.RootShellGateway
import system.ShellExecOptions
import java.io.File

internal class AndroidResourceFileRepository(
    context: Context,
    private val currentRunMode: () -> Int,
    private val rootShell: RootShellGateway = AndroidRootShellGateway(),
) {
    private val appContext = context.applicationContext
    private val store = AndroidResourceFileStore(appContext)
    private val downloader = AndroidResourceFileDownloader()
    private val rootSupervisor = RootSupervisorController(appContext, rootShell)

    suspend fun status(customResourceFiles: List<CustomResourceFileState> = emptyList()): ResourceFilesStatus =
        withContext(Dispatchers.IO) {
            store.status(customResourceFiles)
        }

    suspend fun restoreBundledDefaults(resourceFileSource: Int): ResourceFilesStatus = withContext(Dispatchers.IO) {
        store.restoreBundledDefaults(resourceFileSource)
        if (store.shouldPublishBundledMihomoCore(resourceFileSource)) {
            publishBundledCoreIfPossible()
        }
        store.currentStatus()
    }

    suspend fun deleteCustom(
        customFile: CustomResourceFileState,
        customResourceFiles: List<CustomResourceFileState>,
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        store.deleteCustom(customFile)
        store.currentStatus(customResourceFiles)
    }

    suspend fun renameCustom(
        previousFile: CustomResourceFileState,
        customFile: CustomResourceFileState,
        customResourceFiles: List<CustomResourceFileState>,
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        store.renameCustom(previousFile, customFile)
        store.currentStatus(customResourceFiles)
    }

    suspend fun update(
        source: ResourceFileUpdateSource,
        options: ResourceFileUpdateOptions,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        updateTargets(
            downloads = ResourceFileKind.entries.mapNotNull { kind -> kind.toDownloadTargetOrNull(source) } +
                customResourceFiles.mapNotNull { customFile -> customFile.toDownloadTargetOrNull() },
            options = options,
            customResourceFiles = customResourceFiles,
        )
    }

    suspend fun update(
        kind: ResourceFileKind,
        source: ResourceFileUpdateSource,
        options: ResourceFileUpdateOptions,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        updateTargets(
            downloads = listOfNotNull(kind.toDownloadTargetOrNull(source)),
            options = options,
            customResourceFiles = customResourceFiles,
        )
    }

    suspend fun updateCustom(
        customFile: CustomResourceFileState,
        options: ResourceFileUpdateOptions,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        updateTargets(
            downloads = listOfNotNull(customFile.toDownloadTargetOrNull()),
            options = options,
            customResourceFiles = customResourceFiles,
        )
    }

    private suspend fun updateTargets(
        downloads: List<ResourceFileDownloadTarget>,
        options: ResourceFileUpdateOptions,
        customResourceFiles: List<CustomResourceFileState>,
    ): ResourceFilesStatus {
        if (downloads.isEmpty()) {
            return store.currentStatus(customResourceFiles)
        }
        store.dataDir.mkdirs()
        AndroidResourceFileDownloadCancellation.begin()
        val notifier = AndroidResourceFileDownloadNotifier(appContext)
        val downloadProxy = options.toDownloadProxy()
        if (downloadProxy != null) {
            AndroidResourceFileLogger.info(
                "Resource file update will use local proxy ${downloadProxy.host}:${downloadProxy.port}",
            )
        }
        val result = runCatching {
            downloads.forEachIndexed { index, download ->
                try {
                    notifier.showProgress(download.displayName, progress = null, force = true)
                    downloader.download(download.url, download.targetFile, downloadProxy) { downloadedBytes, totalBytes ->
                        notifier.showProgress(
                            fileName = download.displayName,
                            progress = overallProgress(
                                fileIndex = index,
                                fileCount = downloads.size,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                            ),
                        )
                    }
                    if (download.coreCandidate) {
                        installOrPublishCoreCandidate {
                            store.normalizeMihomoCoreCandidate(download.targetFile)
                        }
                    } else {
                        download.applyPermissions()
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    if (error is AndroidResourceFileDownloadCancelledException) throw error
                    throw ResourceFileDownloadFailedException(download.displayName, error)
                } finally {
                    if (download.coreCandidate) download.targetFile.delete()
                }
            }
            store.currentStatus(customResourceFiles)
        }
        result.onSuccess {
            runCatching { notifier.showComplete() }
        }.onFailure { error ->
            if (error is CancellationException) {
                throw error
            } else if (error is AndroidResourceFileDownloadCancelledException) {
                AndroidResourceFileLogger.info("Resource file update cancelled")
                runCatching { notifier.showCancelled() }
            } else {
                AndroidResourceFileLogger.error("Failed to update resource files", error)
                runCatching { notifier.showFailed(error.message ?: error::class.simpleName.orEmpty()) }
            }
        }
        return result.getOrElse { error ->
            if (error is CancellationException) {
                throw error
            }
            if (error is AndroidResourceFileDownloadCancelledException) {
                throw AndroidResourceFileDownloadCancelledException(
                    appContext.getString(R.string.resource_file_download_notification_cancelled),
                )
            }
            throw error
        }
    }

    private fun CustomResourceFileState.toDownloadTargetOrNull(): ResourceFileDownloadTarget? {
        val target = store.file(this)
        if (ResourceFileKind.entries.any { kind -> kind.fileName == target.name }) return null
        val updateUrl = url.trim()
        if (updateUrl.isBlank()) return null
        return ResourceFileDownloadTarget(
            displayName = name,
            url = updateUrl,
            targetFile = target,
        )
    }

    private fun ResourceFileKind.toDownloadTargetOrNull(source: ResourceFileUpdateSource): ResourceFileDownloadTarget? {
        val updateUrl = source.urlFor(this)?.trim().orEmpty()
        if (updateUrl.isBlank()) return null
        val coreCandidate = this == ResourceFileKind.MihomoCore
        return ResourceFileDownloadTarget(
            displayName = displayName,
            url = updateUrl,
            targetFile = if (coreCandidate) store.createMihomoCoreDownloadCandidate() else store.file(this),
            applyPermissions = { if (!coreCandidate) store.applyPermissions(this) },
            coreCandidate = coreCandidate,
        )
    }

    suspend fun replaceCustom(
        customFile: CustomResourceFileState,
        uri: Uri,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        store.replaceCustom(customFile, uri)
        store.currentStatus(customResourceFiles)
    }

    suspend fun replace(
        kind: ResourceFileKind,
        uri: Uri,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        if (kind == ResourceFileKind.MihomoCore) {
            installOrPublishCoreCandidate {
                store.stageMihomoCoreCandidate(uri)
            }
        } else {
            store.replace(kind, uri)
        }
        store.currentStatus(customResourceFiles)
    }

    suspend fun restoreBundled(
        kind: ResourceFileKind,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus = withContext(Dispatchers.IO) {
        if (kind == ResourceFileKind.MihomoCore) {
            installOrPublishCoreCandidate {
                store.stageBundledMihomoCoreCandidate()
            }
        } else {
            store.restoreBundled(kind)
        }
        store.currentStatus(customResourceFiles)
    }

    private suspend fun publishBundledCoreIfPossible() {
        when (coreCandidateInstallPath()) {
            CoreCandidateInstallPath.ReplaceWithRoot -> {
                if (!rootSupervisor.isUnbound()) return
                replaceCoreCandidateWithRoot(store.stageBundledMihomoCoreCandidate())
            }
            CoreCandidateInstallPath.ReplaceAppOwned -> replaceAppOwnedCoreCandidate(
                store.stageBundledMihomoCoreCandidate(),
            )
            CoreCandidateInstallPath.InitialNoReplace -> {
                installInitialCoreCandidate(store.stageBundledMihomoCoreCandidate())
            }
        }
    }

    private suspend fun installOrPublishCoreCandidate(
        candidateFactory: () -> File,
    ) {
        when (coreCandidateInstallPath()) {
            CoreCandidateInstallPath.ReplaceWithRoot -> {
                rootSupervisor.requireUnbound()
                replaceCoreCandidateWithRoot(candidateFactory())
            }
            CoreCandidateInstallPath.ReplaceAppOwned -> replaceAppOwnedCoreCandidate(candidateFactory())
            CoreCandidateInstallPath.InitialNoReplace -> installInitialCoreCandidate(candidateFactory())
        }
    }

    private fun installInitialCoreCandidate(candidate: File) {
        try {
            val installed = store.installInitialMihomoCoreCandidate(candidate)
            require(installed || store.file(ResourceFileKind.MihomoCore).isFile) { "Failed to install the initial Mihomo core" }
        } finally {
            candidate.delete()
        }
    }

    private suspend fun coreCandidateInstallPath(): CoreCandidateInstallPath {
        val targetExists = store.file(ResourceFileKind.MihomoCore).exists()
        return resolveCoreCandidateInstallPath(targetExists) { currentRunMode().isRootRunMode() }
    }

    private fun replaceAppOwnedCoreCandidate(candidate: File) {
        try {
            store.replaceMihomoCoreCandidate(candidate)
        } finally {
            candidate.delete()
        }
    }

    private suspend fun replaceCoreCandidateWithRoot(candidate: File) {
        try {
            val target = store.file(ResourceFileKind.MihomoCore)
            val removal = rootShell.exec(
                RootCoreRemovalCommand.build(target.absolutePath),
                ShellExecOptions(logFailure = false),
            )
            if (removal.errno != 0) {
                error(removal.stderr.ifBlank { "Failed to remove the existing Mihomo core" })
            }
            store.replaceMihomoCoreCandidate(candidate)
        } finally {
            candidate.delete()
        }
    }
}

private data class ResourceFileDownloadTarget(
    val displayName: String,
    val url: String,
    val targetFile: File,
    val applyPermissions: () -> Unit = {},
    val coreCandidate: Boolean = false,
)

private class ResourceFileDownloadFailedException(
    fileName: String,
    cause: Throwable,
) : RuntimeException("$fileName: ${cause.message ?: cause::class.simpleName.orEmpty()}", cause)

private fun ResourceFileUpdateOptions.toDownloadProxy(): AndroidResourceFileDownloadProxy? {
    if (!useRunningProxy) return null
    val runtimeOptions = LocalProxyRuntime.current()
    val port = runtimeOptions?.port
        ?: fallbackProxyPort?.takeIf(Int::isPort)
        ?: error("Local proxy port is unavailable")
    return AndroidResourceFileDownloadProxy(
        host = LocalProxyLoopbackAddress,
        port = port,
        username = runtimeOptions?.username ?: fallbackProxyUsername,
        password = runtimeOptions?.password ?: fallbackProxyPassword,
    )
}
