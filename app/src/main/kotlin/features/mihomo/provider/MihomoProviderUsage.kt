// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import engine.mihomo.runtime.MihomoProxyProviderRuntimeDetail
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

internal const val MihomoProviderUsageLoadingDelayMillis = 500L

internal fun nextMihomoProviderUsageReloadToken(current: Int): Int = current + 1

internal enum class MihomoProviderUsageKind {
    Metered,
    Unlimited,
    Missing,
    Unavailable,
}

internal data class MihomoProviderUsageItem(
    val name: String,
    val kind: MihomoProviderUsageKind,
    val usedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val expireAtSeconds: Long = 0L,
    val declarationIndex: Int,
) {
    val progress: Float
        get() = clampedUsageProgress(usedBytes, totalBytes)

    val remainingPercent: Int
        get() = ((1f - progress) * 100f).roundToInt()
}

internal data class MihomoProviderUsageSummary(
    val providerCount: Int,
    val usedBytes: Long,
    val totalBytes: Long,
    val unlimitedCount: Int,
    val missingCount: Int,
    val unavailableCount: Int,
    val items: List<MihomoProviderUsageItem>,
) {
    val progress: Float
        get() = clampedUsageProgress(usedBytes, totalBytes)
}

internal sealed interface MihomoProviderUsageLoadState {
    data object Hidden : MihomoProviderUsageLoadState
    data object Loading : MihomoProviderUsageLoadState
    data object RequiresProxyRunning : MihomoProviderUsageLoadState
    data object Failed : MihomoProviderUsageLoadState
    data class Ready(
        val summary: MihomoProviderUsageSummary,
    ) : MihomoProviderUsageLoadState
}

internal data class KeyedMihomoProviderUsageState<K>(
    val loadKey: K? = null,
    val state: MihomoProviderUsageLoadState = MihomoProviderUsageLoadState.Hidden,
) {
    fun stateFor(currentLoadKey: K?): MihomoProviderUsageLoadState {
        return when {
            currentLoadKey == null -> MihomoProviderUsageLoadState.Hidden
            currentLoadKey == loadKey -> state
            else -> MihomoProviderUsageLoadState.Hidden
        }
    }
}

internal suspend fun <T> withDelayedMihomoProviderUsageLoading(
    delayMillis: Long = MihomoProviderUsageLoadingDelayMillis,
    onLoading: () -> Unit,
    load: suspend () -> T,
): T = coroutineScope {
    val loadingJob = launch {
        delay(delayMillis.milliseconds)
        onLoading()
    }
    try {
        load()
    } finally {
        withContext(NonCancellable) {
            loadingJob.cancelAndJoin()
        }
    }
}

internal fun MihomoProviderUsageSummary.toMihomoProviderUsageLoadState(): MihomoProviderUsageLoadState {
    return if (providerCount > 0 && unavailableCount == providerCount) {
        MihomoProviderUsageLoadState.Failed
    } else {
        MihomoProviderUsageLoadState.Ready(this)
    }
}

internal fun resolveMihomoProviderUsagePreflightState(
    providerCount: Int,
    rawConfiguration: Boolean,
    proxyRunning: Boolean,
): MihomoProviderUsageLoadState? {
    return when {
        providerCount <= 0 -> MihomoProviderUsageLoadState.Hidden
        rawConfiguration && !proxyRunning -> MihomoProviderUsageLoadState.RequiresProxyRunning
        else -> null
    }
}

internal fun reduceMihomoProviderUsage(
    providerNames: List<String>,
    runtimeResults: Map<String, Result<MihomoProxyProviderRuntimeDetail>>,
): MihomoProviderUsageSummary {
    val items = providerNames.mapIndexed { index, name ->
        val detail = runtimeResults[name]?.getOrNull()
        val subscription = detail?.subscriptionInfo
        when {
            runtimeResults[name]?.isFailure != false -> MihomoProviderUsageItem(
                name = name,
                kind = MihomoProviderUsageKind.Unavailable,
                declarationIndex = index,
            )

            subscription == null -> MihomoProviderUsageItem(
                name = name,
                kind = MihomoProviderUsageKind.Missing,
                declarationIndex = index,
            )

            subscription.total <= 0L -> MihomoProviderUsageItem(
                name = name,
                kind = MihomoProviderUsageKind.Unlimited,
                expireAtSeconds = subscription.expire.coerceAtLeast(0L),
                declarationIndex = index,
            )

            else -> MihomoProviderUsageItem(
                name = name,
                kind = MihomoProviderUsageKind.Metered,
                usedBytes = subscription.upload.saturatingPlus(subscription.download),
                totalBytes = subscription.total,
                expireAtSeconds = subscription.expire.coerceAtLeast(0L),
                declarationIndex = index,
            )
        }
    }.sortedWith(MihomoProviderUsageComparator)
    val metered = items.filter { item -> item.kind == MihomoProviderUsageKind.Metered }
    return MihomoProviderUsageSummary(
        providerCount = providerNames.size,
        usedBytes = metered.fold(0L) { total, item -> total.saturatingPlus(item.usedBytes) },
        totalBytes = metered.fold(0L) { total, item -> total.saturatingPlus(item.totalBytes) },
        unlimitedCount = items.count { item -> item.kind == MihomoProviderUsageKind.Unlimited },
        missingCount = items.count { item -> item.kind == MihomoProviderUsageKind.Missing },
        unavailableCount = items.count { item -> item.kind == MihomoProviderUsageKind.Unavailable },
        items = items,
    )
}

internal suspend fun loadMihomoProviderUsage(
    providerNames: List<String>,
    fetchDetail: suspend (String) -> Result<MihomoProxyProviderRuntimeDetail>,
): MihomoProviderUsageSummary = coroutineScope {
    val semaphore = Semaphore(permits = MaximumConcurrentProviderRequests)
    val runtimeResults = providerNames.map { name ->
        async {
            val result = semaphore.withPermit {
                try {
                    fetchDetail(name)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Result.failure(error)
                }
            }
            name to result
        }
    }.awaitAll().toMap()
    reduceMihomoProviderUsage(providerNames, runtimeResults)
}

private fun Long.saturatingPlus(other: Long): Long {
    val safeLeft = coerceAtLeast(0L)
    val safeRight = other.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - safeLeft < safeRight) {
        Long.MAX_VALUE
    } else {
        safeLeft + safeRight
    }
}

private fun clampedUsageProgress(usedBytes: Long, totalBytes: Long): Float {
    if (totalBytes <= 0L) return 0f
    return (usedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

private val MihomoProviderUsageComparator = Comparator<MihomoProviderUsageItem> { left, right ->
    val kindComparison = left.kind.ordinal.compareTo(right.kind.ordinal)
    if (kindComparison != 0) {
        kindComparison
    } else {
        val ratioComparison = if (
            left.kind == MihomoProviderUsageKind.Metered &&
            right.kind == MihomoProviderUsageKind.Metered
        ) {
            val leftCrossProduct = BigInteger.valueOf(left.usedBytes)
                .multiply(BigInteger.valueOf(right.totalBytes))
            val rightCrossProduct = BigInteger.valueOf(right.usedBytes)
                .multiply(BigInteger.valueOf(left.totalBytes))
            rightCrossProduct.compareTo(leftCrossProduct)
        } else {
            0
        }
        if (ratioComparison != 0) {
            ratioComparison
        } else {
            left.declarationIndex.compareTo(right.declarationIndex)
        }
    }
}

private const val MaximumConcurrentProviderRequests = 4
