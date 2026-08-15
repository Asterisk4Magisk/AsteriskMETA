// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState
import engine.root.runtime.model.RootRuntimeOwner
import engine.root.runtime.model.RootRuntimeSnapshot
import engine.mihomo.raw.MihomoRawConfigCheckResult
import engine.mihomo.raw.MihomoRawConfigSnapshot
import engine.mihomo.raw.usesRawMihomoConfig

internal data class ProxyEngineStartRequest(
    val appState: AppState,
    val rawConfig: MihomoRawConfigSnapshot? = null,
    val rawConfigCheck: MihomoRawConfigCheckResult? = null,
    val preparedMihomoProfileBytes: ByteArray? = null,
)

internal fun ProxyEngineStartRequest.prepareNormalMihomoProfile(
    resolveAppState: (AppState) -> AppState,
    buildProfileBytes: (AppState) -> ByteArray,
): ProxyEngineStartRequest {
    if (appState.usesRawMihomoConfig()) return this
    val resolvedAppState = resolveAppState(appState)
    val profileBytes = buildProfileBytes(resolvedAppState)
    return copy(
        appState = resolvedAppState,
        preparedMihomoProfileBytes = profileBytes,
    )
}

data class ProxyEngineStatus(
    val running: Boolean,
    val runMode: Int? = null,
    val appState: AppState? = null,
    val rootSnapshot: RootRuntimeSnapshot? = null,
) {
    companion object {
        fun fromRootSnapshot(
            localOwner: RootRuntimeOwner,
            runMode: Int,
            snapshot: RootRuntimeSnapshot,
        ): ProxyEngineStatus = ProxyEngineStatus(
            running = snapshot.owner == localOwner && snapshot.running,
            runMode = runMode,
            rootSnapshot = snapshot,
        )
    }
}
