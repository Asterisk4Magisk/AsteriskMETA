// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.quicksettings

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import app.AppState
import app.modes.MihomoModeDirect
import engine.mihomo.runtime.MihomoRuntimeState
import engine.mihomo.runtime.activeNodeId
import features.home.toMihomoModeOrNull
import features.logs.AndroidAppLogger
import java.util.concurrent.atomic.AtomicReference

private const val LogTag = "QuickSettingsTileRefresh"

/** The part of the app state the tile shows; the tile needs a refresh only when it changes. */
internal data class QuickSettingsTileState(
    val runMode: Int,
    val proxyRunning: Boolean,
    val languageMode: Int,
    val activeNodeName: String?,
)

internal fun AppState.quickSettingsTileState(runtime: MihomoRuntimeState): QuickSettingsTileState =
    QuickSettingsTileState(
        runMode = runMode,
        proxyRunning = proxyRunning,
        languageMode = languageMode,
        activeNodeName = quickSettingsActiveNodeName(this, runtime),
    )

/**
 * The node shown in the running tile's subtitle: the live selection followed down nested groups.
 * Nothing is cached; a stopped core or Direct mode returns no node name.
 */
internal fun quickSettingsActiveNodeName(state: AppState, runtime: MihomoRuntimeState): String? {
    if (!runtime.running) return null
    val mode = runtime.configs.mode.toMihomoModeOrNull() ?: state.mihomoMode
    if (mode == MihomoModeDirect) return null
    return runtime.proxies.activeNodeId()?.name?.takeIf { name -> name.isNotBlank() }
}

private val tileShownState = AtomicReference<QuickSettingsTileState?>(null)

/**
 * Marks the state that the tile is about to write to the store and then show itself, so the
 * state observer does not rebind the tile only to read the same status again. Call it before
 * to write: the observer runs on another thread and may see the write at once.
 */
internal fun markQuickSettingsTileShowing(state: QuickSettingsTileState) {
    tileShownState.set(state)
}

/**
 * Called by the state observer for every distinct tile state. Every call consumes the mark,
 * so a mark the observer never saw (to write was conflated or changed nothing) cannot skip a
 * later state.
 */
internal fun refreshQuickSettingsTileUnlessShowing(context: Context, state: QuickSettingsTileState) {
    if (tileShownState.getAndSet(null) == state) return
    requestQuickSettingsTileRefresh(context)
}

/** Asks SystemUI to bind the tile, which then reads the current proxy status itself. */
internal fun requestQuickSettingsTileRefresh(context: Context) {
    runCatching {
        TileService.requestListeningState(
            context,
            ComponentName(context, ProxyQuickSettingsTileService::class.java),
        )
    }.onFailure { error ->
        AndroidAppLogger.warn(LogTag, "Failed to request quick settings tile refresh", error)
    }
}
