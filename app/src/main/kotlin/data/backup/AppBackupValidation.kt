// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.MihomoProfileType

internal fun AppBackupData.validateForRestore() {
    requireValidIds("Mihomo profile", mihomoProfiles, AppBackupMihomoProfile::id)
    requireValidIds("override script", mihomoOverrideScripts, AppBackupMihomoOverrideScript::id)
    mihomoProfiles.forEach { profile ->
        require(MihomoProfileType.entries.any { type -> type.storageValue == profile.type }) {
            "Unknown Mihomo profile type: ${profile.type}"
        }
    }
}

private inline fun <T> requireValidIds(
    label: String,
    items: List<T>,
    idOf: (T) -> Int,
) {
    val ids = mutableSetOf<Int>()
    items.forEach { item ->
        val id = idOf(item)
        require(id > 0) { "$label ID must be positive" }
        require(id < Int.MAX_VALUE) { "$label ID is too large" }
        require(ids.add(id)) { "Duplicate $label ID: $id" }
    }
}
