// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import data.AndroidAppStateStore

data class AppChromeState(
    val colorMode: Int,
    val languageMode: Int,
    val seedIndex: Int,
)

val LocalAppStateStore = staticCompositionLocalOf<AndroidAppStateStore> {
    error("No AndroidAppStateStore provided!")
}

val LocalUpdateAppState = staticCompositionLocalOf<((AppState) -> AppState) -> Unit> {
    error("No AppState updater provided!")
}

val LocalAppChromeState = compositionLocalOf<AppChromeState> {
    error("No AppChromeState provided!")
}

@Composable
fun AndroidAppStateStore.collectAppState(): State<AppState> {
    return state.collectAsState()
}

@Composable
fun AndroidAppStateStore.collectAppChromeState(): State<AppChromeState> {
    val appState = state.collectAsState()
    return remember {
        derivedStateOf { appState.value.toAppChromeState() }
    }
}

private fun AppState.toAppChromeState(): AppChromeState {
    return AppChromeState(
        colorMode = colorMode,
        languageMode = languageMode,
        seedIndex = seedIndex,
    )
}
