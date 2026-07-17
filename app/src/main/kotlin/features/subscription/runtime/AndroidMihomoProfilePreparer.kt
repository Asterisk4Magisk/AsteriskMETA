// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import android.content.Context
import app.MihomoSubscriptionInfo
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.FetchOptions
import com.github.kr328.clash.core.model.FetchStatus
import features.resources.runtime.prepareMihomoResourceFilePaths
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface AndroidMihomoProfilePreparation {
    data class Success(
        val content: String,
        val subscriptionInfo: MihomoSubscriptionInfo,
        val updateIntervalMillis: Long? = null,
    ) : AndroidMihomoProfilePreparation

    data class Failure(
        val action: FetchStatus.Action,
        val error: Throwable,
        val content: String? = null,
        val subscriptionInfo: MihomoSubscriptionInfo? = null,
        val updateIntervalMillis: Long? = null,
    ) : AndroidMihomoProfilePreparation
}

internal class AndroidMihomoProfilePreparer(
    context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun prepare(
        profileContent: String?,
        sourceUrl: String,
        userAgent: String,
        ageSecretKey: String,
        fetchOptions: AndroidSubscriptionFetchOptions,
        onStatus: (FetchStatus) -> Unit = {},
    ): AndroidMihomoProfilePreparation = mihomoCoreFetchLock.withLock {
        val dataDir = withContext(Dispatchers.IO) {
            File(appContext.prepareMihomoResourceFilePaths().dataDir)
        }
        val processingDir = File(appContext.cacheDir, "$ProcessingDirPrefix-${System.nanoTime()}")
        var lastAction = if (profileContent == null) {
            FetchStatus.Action.FetchConfiguration
        } else {
            FetchStatus.Action.Decrypting
        }
        var subscriptionInfo: MihomoSubscriptionInfo? = null
        var updateIntervalMillis: Long? = null
        try {
            withContext(Dispatchers.IO) {
                prepareProcessingProfileDir(
                    processingDir = processingDir,
                    dataDir = dataDir,
                    profileContent = profileContent,
                    refreshProxyProviders = false,
                )
            }
            val statuses = Channel<FetchStatus>(Channel.UNLIMITED)
            try {
                coroutineScope {
                    val collector = launch {
                        for (status in statuses) {
                            lastAction = status.action
                            if (status.action == FetchStatus.Action.SubscriptionInfo) {
                                subscriptionInfo = status.toSubscriptionInfo()
                                updateIntervalMillis = status.subUpdateInterval
                            }
                            onStatus(status)
                        }
                    }
                    try {
                        withMihomoAgeSecretKey(ageSecretKey) {
                            Clash.fetchAndValid(
                                path = processingDir,
                                url = sourceUrl,
                                options = FetchOptions(
                                    force = profileContent == null,
                                    userAgent = userAgent,
                                    proxy = fetchOptions.toCoreFetchProxy(),
                                ),
                                reportStatus = { status -> statuses.trySend(status) },
                            )
                        }
                    } finally {
                        statuses.close()
                        collector.join()
                    }
                }
                val content = withContext(Dispatchers.IO) {
                    processingDir.readPreparedProfileContent(ageSecretKey)
                } ?: error("Configuration file is empty")
                withContext(Dispatchers.IO) {
                    copyProcessingProvidersBack(processingDir = processingDir, dataDir = dataDir)
                }
                AndroidMihomoProfilePreparation.Success(
                    content = content,
                    subscriptionInfo = subscriptionInfo ?: MihomoSubscriptionInfo(),
                    updateIntervalMillis = updateIntervalMillis,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AndroidMihomoProfilePreparation.Failure(
                    action = lastAction,
                    error = error,
                    content = runCatching {
                        withContext(Dispatchers.IO) {
                            processingDir.readPreparedProfileContent(ageSecretKey)
                        }
                    }.getOrNull(),
                    subscriptionInfo = subscriptionInfo,
                    updateIntervalMillis = updateIntervalMillis,
                )
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                processingDir.deleteRecursively()
            }
        }
    }
}

private fun FetchStatus.toSubscriptionInfo(): MihomoSubscriptionInfo {
    return MihomoSubscriptionInfo(
        uploadBytes = subUpload?.coerceAtLeast(0L) ?: 0L,
        downloadBytes = subDownload?.coerceAtLeast(0L) ?: 0L,
        totalBytes = subTotal?.coerceAtLeast(0L) ?: 0L,
        expireAtSeconds = subExpire?.coerceAtLeast(0L)?.div(1000L) ?: 0L,
    )
}

private fun File.readPreparedProfileContent(ageSecretKey: String): String? {
    val file = File(this, ConfigFileName)
    if (!file.isFile) return null
    return Clash.decryptAge(
        file.readText(Charsets.UTF_8),
        ageSecretKey.trim().takeIf(String::isNotBlank),
    ).takeIf(String::isNotBlank)
}

private const val ProcessingDirPrefix = "mihomo-profile-processing"
