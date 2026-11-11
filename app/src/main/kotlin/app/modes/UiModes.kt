// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.modes

const val ColorModeSystem = 0
const val ColorModeLight = 1
const val ColorModeDark = 2
const val ColorModeThemeSystem = 3
const val ColorModeThemeLight = 4
const val ColorModeThemeDark = 5

fun normalizeColorMode(value: Int): Int = when (value) {
    ColorModeThemeSystem -> ColorModeSystem
    ColorModeThemeLight -> ColorModeLight
    ColorModeThemeDark -> ColorModeDark
    ColorModeSystem, ColorModeLight, ColorModeDark -> value
    else -> ColorModeSystem
}

// How the home "network activity" card draws its history.
const val NetworkActivityStyleDotted = 0
const val NetworkActivityStyleLines = 1

fun normalizeNetworkActivityStyle(value: Int): Int = when (value) {
    NetworkActivityStyleDotted, NetworkActivityStyleLines -> value
    else -> NetworkActivityStyleDotted
}

const val LanguageModeSystem = 0
const val LanguageModeEnglish = 1
const val LanguageModeSimplifiedChinese = 2

// Bottom navigation bar layout: the full-width glass bar or the compact
// circle-pill bar with icon-only tabs.
const val NavigationBarStyleClassic = 0
const val NavigationBarStyleCompact = 1

fun normalizeNavigationBarStyle(value: Int): Int = when (value) {
    NavigationBarStyleClassic, NavigationBarStyleCompact -> value
    else -> NavigationBarStyleClassic
}

