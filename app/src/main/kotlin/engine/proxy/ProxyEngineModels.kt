// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import android.content.Intent
import app.AppState
import engine.root.runtime.model.RootRuntimeOwner
import engine.root.runtime.model.RootRuntimeSnapshot
import engine.mihomo.raw.MihomoRawConfigCheckResult
import engine.mihomo.raw.MihomoRawConfigSnapshot
import engine.mihomo.raw.usesRawMihomoConfig

/** Asks the user for VPN consent; returns whether it was granted. */
typealias VpnPermissionRequester = suspend (Intent) -> Boolean

/** Used by entry points that cannot show the system VPN consent dialog. */
val DenyVpnPermission: VpnPermissionRequester = { false }

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

/** Why a changed profile did not reach the running core the ordinary way. */
internal enum class ProfileApplyFailure {
    /** Compiling, publishing or reaching the core failed before it was asked; it runs what it ran. */
    NotPrepared,

    /** The core parsed the new configuration and refused it; it runs what it ran. */
    Rejected,

    /** The request was sent and no answer arrived, so either configuration may be running. */
    Unconfirmed,

    /** The core runs the new configuration, but the runtime file still holds the old one. */
    RuntimeFileNotSaved,
}

/**
 * A hot reload that did not complete. The service is never restarted because of it: [failure]
 * tells what the core is running now.
 */
internal class ProfileApplyException(
    val failure: ProfileApplyFailure,
    cause: Throwable,
) : IllegalStateException(cause.message, cause)

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
