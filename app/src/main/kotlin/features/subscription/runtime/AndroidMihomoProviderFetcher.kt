// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import android.content.Context
import com.github.kr328.clash.core.Clash
import engine.mihomo.MihomoProviderType
import engine.mihomo.mihomoRemoteProviderFiles
import features.resources.runtime.prepareMihomoResourceFilePaths
import utils.writeAtomically
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AndroidMihomoProviderFetcher(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val fetchLock = Mutex()

    suspend fun fetchMissingProviders(
        profileContent: String,
        sourceUrl: String,
    ) = fetchLock.withLock {
        fetchProviders(profileContent, sourceUrl, refreshProxyProviders = false)
    }

    suspend fun refreshProxyProviders(
        profileContent: String,
        sourceUrl: String,
    ) = fetchLock.withLock {
        refreshProxyProviderFiles(profileContent, sourceUrl)
    }

    private suspend fun fetchProviders(
        profileContent: String,
        sourceUrl: String,
        refreshProxyProviders: Boolean,
    ) {
        if (profileContent.isBlank()) return
        withContext(Dispatchers.IO) {
            val dataDir = File(appContext.prepareMihomoResourceFilePaths().dataDir)
            val processingDir = File(appContext.cacheDir, "$ProcessingDirPrefix-${System.nanoTime()}")
            try {
                prepareProcessingProfileDir(
                    processingDir = processingDir,
                    dataDir = dataDir,
                    profileContent = profileContent,
                    refreshProxyProviders = refreshProxyProviders,
                )
                Clash.fetchAndValid(
                    path = processingDir,
                    url = sourceUrl,
                    force = false,
                    reportStatus = {},
                ).await()
                copyProcessingProvidersBack(processingDir = processingDir, dataDir = dataDir)
            } finally {
                processingDir.deleteRecursively()
            }
        }
    }

    private suspend fun refreshProxyProviderFiles(
        profileContent: String,
        sourceUrl: String,
    ): MihomoProviderRefreshResult {
        if (profileContent.isBlank()) return MihomoProviderRefreshResult()
        return withContext(Dispatchers.IO) {
            val dataDir = File(appContext.prepareMihomoResourceFilePaths().dataDir)
            val processingDir = File(appContext.cacheDir, "$ProcessingDirPrefix-${System.nanoTime()}")
            try {
                val refreshedFiles = prepareProcessingProfileDir(
                    processingDir = processingDir,
                    dataDir = dataDir,
                    profileContent = profileContent,
                    refreshProxyProviders = true,
                )
                if (refreshedFiles.isEmpty()) {
                    return@withContext MihomoProviderRefreshResult()
                }

                runCatching {
                    Clash.fetchAndValid(
                        path = processingDir,
                        url = sourceUrl,
                        force = false,
                        reportStatus = {},
                    ).await()
                }
                copyProcessingProvidersBack(processingDir = processingDir, dataDir = dataDir)

                val successCount = refreshedFiles.count { file -> file.isFile && file.length() > 0L }
                MihomoProviderRefreshResult(
                    totalCount = refreshedFiles.size,
                    successCount = successCount,
                    failedCount = refreshedFiles.size - successCount,
                )
            } finally {
                processingDir.deleteRecursively()
            }
        }
    }
}

internal data class MihomoProviderRefreshResult(
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
)

private fun prepareProcessingProfileDir(
    processingDir: File,
    dataDir: File,
    profileContent: String,
    refreshProxyProviders: Boolean,
): List<File> {
    processingDir.deleteRecursively()
    processingDir.mkdirs()
    val sourceProviders = File(dataDir, ProvidersDirName)
    if (sourceProviders.exists()) {
        sourceProviders.copyRecursively(
            target = File(processingDir, ProvidersDirName),
            overwrite = true,
        )
    }
    writeAtomically(File(processingDir, ConfigFileName)) { output ->
        output.write(profileContent.toByteArray(Charsets.UTF_8))
    }
    return if (refreshProxyProviders) {
        profileContent.mihomoRemoteProviderFiles(
            dataDir = processingDir,
            type = MihomoProviderType.Proxy,
        ).also { files ->
            files.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    } else {
        emptyList()
    }
}

private fun copyProcessingProvidersBack(processingDir: File, dataDir: File) {
    val sourceProviders = File(processingDir, ProvidersDirName)
    if (!sourceProviders.exists()) {
        return
    }
    sourceProviders.copyRecursively(
        target = File(dataDir, ProvidersDirName),
        overwrite = true,
    )
}

private const val ProcessingDirPrefix = "mihomo-provider-processing"
private const val ProvidersDirName = "providers"
private const val ConfigFileName = "config.yaml"
