// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import kotlin.coroutines.cancellation.CancellationException

internal inline fun <T> runMihomoRuntimeCatching(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        Result.failure(error)
    }
}
