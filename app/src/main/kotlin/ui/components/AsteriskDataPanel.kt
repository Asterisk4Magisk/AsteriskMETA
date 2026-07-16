// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ui.components

internal sealed interface AsteriskDataPanelState {
    data object Ready : AsteriskDataPanelState
    data object Loading : AsteriskDataPanelState
    data class Unavailable(val message: String) : AsteriskDataPanelState
}
