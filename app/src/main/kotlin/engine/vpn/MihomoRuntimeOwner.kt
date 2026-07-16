// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

internal enum class MihomoRuntimeOwner {
    None,
    Standby,
    ProxyService,
}

internal fun MihomoRuntimeOwner.canLifecycleRelease(): Boolean = this == MihomoRuntimeOwner.Standby
