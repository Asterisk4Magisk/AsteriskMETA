// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal enum class MainTransitionDirection { Backward, None, Forward }

internal data class MainDestinationSelection(
    val current: MainDestination,
    val previous: MainDestination = current,
) {
    val direction: MainTransitionDirection
        get() = when {
            current.index > previous.index -> MainTransitionDirection.Forward
            current.index < previous.index -> MainTransitionDirection.Backward
            else -> MainTransitionDirection.None
        }

    fun select(target: MainDestination): MainDestinationSelection =
        if (target == current) this else MainDestinationSelection(current = target, previous = current)
}

@Stable
internal class MainDestinationState(initialDestination: MainDestination = MainDestination.Home) {
    var selection by mutableStateOf(MainDestinationSelection(initialDestination))
        private set

    val current: MainDestination
        get() = selection.current

    fun select(destination: MainDestination) {
        selection = selection.select(destination)
    }
}

internal fun mainDestinationFromSavedValue(value: String): MainDestination = when (value) {
    "Apps", "apps" -> MainDestination.Configurations
    else -> MainDestination.entries.firstOrNull { destination ->
        destination.name == value || destination.id == value
    } ?: MainDestination.Home
}

private val MainDestinationStateSaver = Saver<MainDestinationState, String>(
    save = { it.current.id },
    restore = { MainDestinationState(mainDestinationFromSavedValue(it)) },
)

@Composable
internal fun rememberMainDestinationState(): MainDestinationState = rememberSaveable(
    saver = MainDestinationStateSaver,
) {
    MainDestinationState()
}
