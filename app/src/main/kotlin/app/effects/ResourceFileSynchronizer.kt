// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import data.AndroidAppStateStore
import features.logs.AndroidAppLogger
import features.resources.ResourceFileUseCase

@Composable
internal fun ResourceFileSynchronizer(
    resourceFileUseCase: ResourceFileUseCase,
    stateStore: AndroidAppStateStore,
) {
    LaunchedEffect(resourceFileUseCase, stateStore) {
        runCatching {
            resourceFileUseCase.restoreBundledDefaults(
                resourceFileSource = stateStore.state.value.resourceFileSource,
            )
        }
            .onFailure { error ->
                AndroidAppLogger.warn(
                    LogTag,
                    "Failed to synchronize bundled resource files",
                    error,
                )
            }
    }
}

private const val LogTag = "ResourceFileSync"
