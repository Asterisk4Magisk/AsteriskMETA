// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.quicksettings

import app.AppState
import app.modes.MihomoModeDirect
import engine.mihomo.runtime.MihomoRuntimeState
import engine.mihomo.runtime.activeNodeId
import features.home.toMihomoModeOrNull

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
