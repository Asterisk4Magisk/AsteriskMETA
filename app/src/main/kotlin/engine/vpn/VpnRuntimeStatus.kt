// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

internal fun resolveVpnRuntimeRunning(
    runtimeRunning: Boolean,
    ownsVpnPreparation: Boolean,
): Boolean {
    return runtimeRunning && ownsVpnPreparation
}
