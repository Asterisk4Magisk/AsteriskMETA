// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import app.DefaultMihomoOverrideScriptId
import app.MihomoProfileState
import app.MihomoProfileType

internal enum class MihomoProfileDisplayKind {
    RemoteSubscription,
    LocalFile,
}

internal data class MihomoProfileDisplayState(
    val kind: MihomoProfileDisplayKind,
    val showSync: Boolean,
    val hasOverrideScript: Boolean,
    val rawConfiguration: Boolean,
)

internal fun reduceMihomoProfileDisplay(profile: MihomoProfileState): MihomoProfileDisplayState {
    val isRemote = profile.type == MihomoProfileType.Url
    return MihomoProfileDisplayState(
        kind = if (isRemote) {
            MihomoProfileDisplayKind.RemoteSubscription
        } else {
            MihomoProfileDisplayKind.LocalFile
        },
        showSync = isRemote && profile.url.isNotBlank(),
        hasOverrideScript = profile.overrideScriptId != DefaultMihomoOverrideScriptId,
        rawConfiguration = profile.disableOverrides,
    )
}
