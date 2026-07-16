// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.navigation

internal enum class MainDestination(
    val id: String,
) {
    Home("home"),
    Proxies("proxies"),
    Configurations("configurations"),
    Settings("settings"),
    ;

    val index: Int
        get() = ordinal
}
