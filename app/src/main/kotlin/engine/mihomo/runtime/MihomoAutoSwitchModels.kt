// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import engine.mihomo.MihomoAutoSwitchOptions
import kotlinx.serialization.Serializable

/** Mirrors AndroidLibClashLite `GET /asterisk/auto-switch`; enum-like values stay strings. */
@Serializable
internal data class MihomoAutoSwitchState(
    val settings: MihomoAutoSwitchOptions = MihomoAutoSwitchOptions(),
    val source: String = "",
    val phase: String = MihomoAutoSwitchPhaseStopped,
    val nextCheckAt: Long? = null,
    val progress: MihomoAutoSwitchProgress? = null,
    val groups: List<MihomoAutoSwitchGroupStatus> = emptyList(),
    val events: List<MihomoAutoSwitchEvent> = emptyList(),
)

@Serializable
internal data class MihomoAutoSwitchProgress(
    val group: String = "",
    val stage: String = "",
    val completed: Int = 0,
    val total: Int = 0,
)

@Serializable
internal data class MihomoAutoSwitchGroupStatus(
    val name: String = "",
    val kind: String = "",
    val node: String = "",
    val delay: Int? = null,
    val failures: Int? = null,
    val inUse: Boolean = false,
    val checkedAt: Long = 0L,
)

@Serializable
internal data class MihomoAutoSwitchEvent(
    val time: Long = 0L,
    val group: String = "",
    val kind: String = "",
    val node: String = "",
    val target: String = "",
    val delay: Int? = null,
    val failures: Int? = null,
    val threshold: Int? = null,
    val tested: Int? = null,
    val available: Int? = null,
    val preferred: Boolean = false,
)

internal const val MihomoAutoSwitchPhaseStopped = "stopped"
internal const val MihomoAutoSwitchPhaseChecking = "checking"
internal const val MihomoAutoSwitchPhaseWaiting = "waiting"
