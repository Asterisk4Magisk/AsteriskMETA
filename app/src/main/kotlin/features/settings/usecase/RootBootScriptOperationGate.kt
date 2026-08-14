// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.usecase

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RootBootScriptOperationGate {
    private val mutex = Mutex()

    suspend fun <T> exclusive(action: suspend () -> T): T =
        mutex.withLock { action() }
}
