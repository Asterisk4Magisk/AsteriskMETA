// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal suspend fun <T, R> runMihomoDelayBatch(
    targets: List<T>,
    concurrency: Int,
    test: suspend (T) -> R,
): List<R> = coroutineScope {
    require(concurrency > 0)
    val semaphore = Semaphore(concurrency)
    targets.map { target ->
        async(Dispatchers.IO) {
            semaphore.withPermit { test(target) }
        }
    }.awaitAll()
}
