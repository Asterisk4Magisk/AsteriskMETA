// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import app.AppState
import app.modes.RunModeVpnService
import engine.mihomo.MihomoControlConfig
import engine.mihomo.mihomoControlConfig
import engine.mihomo.selectedMihomoProfileOrNull

internal data class MihomoTrafficStatsRuntime(
    val control: MihomoControlConfig,
    val useBridge: Boolean,
    val nodeName: String = "",
)

internal fun AppState.toMihomoTrafficStatsRuntime(runMode: Int = this.runMode): MihomoTrafficStatsRuntime? {
    if (!enableTrafficStatsNotification) return null
    if (runMode != RunModeVpnService) return null
    return MihomoTrafficStatsRuntime(
        control = mihomoControlConfig(),
        useBridge = true,
        nodeName = selectedMihomoProfileOrNull()?.name.orEmpty(),
    )
}
