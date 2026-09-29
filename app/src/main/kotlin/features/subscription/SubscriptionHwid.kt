// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0
package features.subscription

private val subscriptionHwidPattern = Regex("[a-zA-Z0-9=-]{10,64}")

internal fun String.isValidSubscriptionHwid(): Boolean =
    trim().let { it.isEmpty() || subscriptionHwidPattern.matches(it) }

internal fun resolveSubscriptionHwid(custom: String, installationHwid: () -> String): String {
    val value = custom.trim().ifEmpty(installationHwid)
    require(subscriptionHwidPattern.matches(value)) { "HWID_INVALID" }
    return value
}
