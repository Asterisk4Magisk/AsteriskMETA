// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data

import app.AppState
import app.DefaultMihomoProfileId

internal data class PersistedAppState(
    val mihomoProfiles: List<MihomoProfileEntity>,
    val mihomoOverrideScripts: List<MihomoOverrideScriptEntity>,
    val proxyAppListSelectedApps: List<ProxyAppListSelectedAppEntity>,
) {
    fun hasRoomContent(): Boolean {
        return mihomoProfiles.isNotEmpty() ||
            mihomoOverrideScripts.isNotEmpty() ||
            proxyAppListSelectedApps.isNotEmpty()
    }

    fun toAppState(settings: AppState): AppState {
        val restoredMihomoProfiles = mihomoProfiles.map { profile -> profile.toState() }
        val restoredOverrideScripts = mihomoOverrideScripts.map { script -> script.toState() }

        return settings.copy(
            mihomoProfiles = restoredMihomoProfiles,
            mihomoOverrideScripts = restoredOverrideScripts,
            nextMihomoProfileId = maxOf(
                settings.nextMihomoProfileId,
                (restoredMihomoProfiles.maxOfOrNull { profile -> profile.id } ?: 0) + 1,
            ),
            nextMihomoOverrideScriptId = maxOf(
                settings.nextMihomoOverrideScriptId,
                (restoredOverrideScripts.maxOfOrNull { script -> script.id } ?: 0) + 1,
            ),
            selectedMihomoProfileId = settings.selectedMihomoProfileId
                .takeIf { profileId -> restoredMihomoProfiles.any { profile -> profile.id == profileId } }
                ?: restoredMihomoProfiles.firstOrNull()?.id
                ?: DefaultMihomoProfileId,
            proxyRunning = false,
            proxyAppListSelectedApps = proxyAppListSelectedApps.map { app -> app.packageKey },
        )
    }
}
