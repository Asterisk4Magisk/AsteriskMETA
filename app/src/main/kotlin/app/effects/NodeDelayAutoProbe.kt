// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.modes.MihomoModeDirect
import data.AndroidAppStateStore
import engine.mihomo.runtime.MihomoProxyNodeId
import engine.mihomo.runtime.MihomoRuntimeRepository
import engine.mihomo.runtime.MihomoRuntimeState
import engine.mihomo.runtime.activeNodeId
import features.home.toMihomoModeOrNull
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Keeps the active node's latency fresh when the app enters the foreground. It probes only the
 * first testable active node resolved for that resume; later selector changes are user actions and
 * must not implicitly start another delay test. An already started probe finishes across selections.
 */
@Composable
internal fun NodeDelayAutoProbe(
    stateStore: AndroidAppStateStore,
    mihomoRuntime: MihomoRuntimeRepository,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, stateStore, mihomoRuntime) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val selectionRevision = mihomoRuntime.state.value.proxySelectionRevision
            val id = mihomoRuntime.state.awaitResumeProbeNode(selectionRevision) {
                stateStore.state.value.mihomoMode
            } ?: return@repeatOnLifecycle
            mihomoRuntime.probeProxyDelay(stateStore.state.value, id, SystemClock.elapsedRealtime())
        }
    }
}

internal suspend fun StateFlow<MihomoRuntimeState>.awaitResumeProbeNode(
    selectionRevision: Long,
    fallbackMode: () -> Int,
): MihomoProxyNodeId? {
    val runtime = first { runtime ->
        runtime.proxySelectionRevision != selectionRevision || runtime.activeProbeNode(fallbackMode()) != null
    }
    if (runtime.proxySelectionRevision != selectionRevision) return null
    return runtime.activeProbeNode(fallbackMode())
}

internal fun MihomoRuntimeState.activeProbeNode(
    fallbackMode: Int,
): MihomoProxyNodeId? {
    val direct = (configs.mode.toMihomoModeOrNull() ?: fallbackMode) == MihomoModeDirect
    if (!running || direct) return null
    return proxies.activeNodeId()?.takeUnless { id ->
        id.providerName == null && id.name.uppercase() in BuiltInOutbounds
    }
}

private val BuiltInOutbounds = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE")
