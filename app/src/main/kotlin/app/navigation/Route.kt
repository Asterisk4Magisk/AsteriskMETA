// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation keys for Navigation3.
 * Each destination is a NavKey (data object/data class) and can be saved/restored in the back stack.
 */
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object About : Route

    @Serializable
    data object License : Route

    @Serializable
    data object CoreLogs : Route

    @Serializable
    data object LogcatLogs : Route

    @Serializable
    data object ResourceManagement : Route

    @Serializable
    data object ResourceMonitor : Route

    @Serializable
    data object ConnectionsMonitor : Route

    @Serializable
    data object TrafficMonitor : Route

    @Serializable
    data object NetworkMonitor : Route

    @Serializable
    data object ProxyAppList : Route

    @Serializable
    data object MihomoProfileList : Route

    @Serializable
    data object MihomoProviders : Route

    @Serializable
    data class MihomoProviderDetail(val providerName: String) : Route

    @Serializable
    data object MihomoOverrideScripts : Route

    @Serializable
    data class MihomoOverrideScriptEdit(val scriptId: Int = 0, val draftId: Long = 0L) : Route

    @Serializable
    data class MihomoProfileEdit(val profileId: Int = 0, val type: Int = 0, val draftId: Long = 0L) : Route
}
