// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import android.content.Context
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.FetchOptions
import engine.mihomo.MihomoProviderType
import engine.mihomo.mihomoRemoteProviderFiles
import features.resources.runtime.prepareMihomoResourceFilePaths
import utils.writeAtomically
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AndroidMihomoProviderFetcher(
    context: Context,
) {
    private val appContext = context.applicationContext
    suspend fun refreshProxyProviders(
        profileContent: String,
        sourceUrl: String,
        ageSecretKey: String = "",
        fetchOptions: AndroidSubscriptionFetchOptions = AndroidSubscriptionFetchOptions(),
    ) = mihomoCoreFetchLock.withLock {
        refreshProxyProviderFiles(profileContent, sourceUrl, ageSecretKey, fetchOptions)
    }

    private suspend fun refreshProxyProviderFiles(
        profileContent: String,
        sourceUrl: String,
        ageSecretKey: String,
        fetchOptions: AndroidSubscriptionFetchOptions,
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
                    withMihomoAgeSecretKey(ageSecretKey) {
                        Clash.fetchAndValid(
                            path = processingDir,
                            url = sourceUrl,
                            options = FetchOptions(proxy = fetchOptions.toCoreFetchProxy()),
                            reportStatus = {},
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
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

internal suspend fun <T> withMihomoAgeSecretKey(
    ageSecretKey: String,
    block: suspend () -> T,
): T = ageSecretKeyLock.withLock {
    val key = ageSecretKey.trim().takeIf(String::isNotBlank)
    Clash.setAgeSecretKey(key)
    try {
        block()
    } finally {
        Clash.setAgeSecretKey(null)
    }
}

internal data class MihomoProviderRefreshResult(
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
)

internal fun prepareProcessingProfileDir(
    processingDir: File,
    dataDir: File,
    profileContent: String?,
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
    if (profileContent != null) {
        writeAtomically(File(processingDir, ConfigFileName)) { output ->
            output.write(profileContent.toByteArray(Charsets.UTF_8))
        }
    }
    return if (refreshProxyProviders) {
        profileContent.orEmpty().mihomoRemoteProviderFiles(
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

internal fun copyProcessingProvidersBack(processingDir: File, dataDir: File) {
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
internal const val ProvidersDirName = "providers"
internal const val ConfigFileName = "config.yaml"
internal val mihomoCoreFetchLock = Mutex()
private val ageSecretKeyLock = Mutex()
