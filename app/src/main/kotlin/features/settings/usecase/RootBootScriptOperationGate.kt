// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.usecase

import engine.proxy.ProxyLifecycleLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RootBootScriptOperationGate(
    private val lifecycleLock: ProxyLifecycleLock,
) {
    private val mutex = Mutex()

    /** Boot script removal touches no runtime configuration file. */
    suspend fun <T> exclusive(action: suspend () -> T): T =
        mutex.withLock { action() }

    /**
     * Boot publication writes the runtime configuration a running or launching cycle reads,
     * so it waits for any lifecycle operation. The lifecycle lock is always taken first.
     */
    suspend fun <T> exclusiveWithLifecycle(action: suspend () -> T): T =
        lifecycleLock.withLifecycleLock { mutex.withLock { action() } }
}
