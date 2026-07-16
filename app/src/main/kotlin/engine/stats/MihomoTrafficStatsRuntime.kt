// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import app.AppState
import app.modes.RunModeVpnService
import engine.mihomo.MihomoControlConfig
import engine.mihomo.mihomoControlConfig
import engine.mihomo.selectedMihomoProfileOrNull
import engine.mihomo.raw.MihomoRawConfigSnapshot
import engine.mihomo.raw.usesRawMihomoConfig

internal data class MihomoTrafficStatsRuntime(
    val control: MihomoControlConfig,
    val useBridge: Boolean,
    val nodeName: String = "",
)

internal fun AppState.toMihomoTrafficStatsRuntime(
    runMode: Int = this.runMode,
    rawConfig: MihomoRawConfigSnapshot? = null,
): MihomoTrafficStatsRuntime? {
    if (!enableTrafficStatsNotification) return null
    if (runMode != RunModeVpnService) return null
    val usesRawConfig = usesRawMihomoConfig()
    val control = if (usesRawConfig) {
        rawConfig?.api?.value?.control ?: return null
    } else {
        mihomoControlConfig()
    }
    return MihomoTrafficStatsRuntime(
        control = control,
        useBridge = !usesRawConfig,
        nodeName = selectedMihomoProfileOrNull()?.name.orEmpty(),
    )
}
