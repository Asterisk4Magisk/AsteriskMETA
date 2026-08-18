// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import app.modes.isRootRunMode

internal enum class MihomoRuntimeMutation {
    Mode,
    ProfileReload,
    LogLevel,
}

internal fun MihomoRuntimeMutation.requiresSupervisedRestart(runMode: Int): Boolean {
    if (!runMode.isRootRunMode()) return false
    // Every mutation listed here has both an embedded bridge operation and a
    // Mihomo Clash API endpoint, so ROOT can apply it without restarting.
    return when (this) {
        MihomoRuntimeMutation.Mode,
        MihomoRuntimeMutation.ProfileReload,
        MihomoRuntimeMutation.LogLevel,
        -> false
    }
}
