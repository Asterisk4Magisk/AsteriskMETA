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
import engine.mihomo.runtime.MihomoRuntimeRepository
import engine.mihomo.runtime.activeNodeId
import features.home.toMihomoModeOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Keeps the active node's latency fresh while the app is in the foreground: it is probed when the
 * service comes up, when the node changes and on every resume. The runtime throttles repeats of
 * the same node and lets any user-started test preempt a probe, so this effect only decides when.
 */
@Composable
internal fun NodeDelayAutoProbe(
    stateStore: AndroidAppStateStore,
    mihomoRuntime: MihomoRuntimeRepository,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, stateStore, mihomoRuntime) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            mihomoRuntime.state
                // The state ticks every second for traffic; the proxy snapshot keeps its identity
                // in between, so the node is resolved only when the snapshot really changes.
                .map { runtime -> Triple(runtime.running, runtime.configs.mode, runtime.proxies) }
                .distinctUntilChanged()
                .map { (running, mode, proxies) ->
                    val direct = (mode.toMihomoModeOrNull() ?: stateStore.state.value.mihomoMode) == MihomoModeDirect
                    if (running && !direct) proxies.activeNodeId() else null
                }
                .distinctUntilChanged()
                // A node change cancels the probe of the previous one.
                .collectLatest { id ->
                    // Built-in outbounds are not nodes the core can test.
                    if (id == null || id.providerName == null && id.name.uppercase() in BuiltInOutbounds) {
                        return@collectLatest
                    }
                    mihomoRuntime.probeProxyDelay(stateStore.state.value, id, SystemClock.elapsedRealtime())
                }
        }
    }
}

private val BuiltInOutbounds = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE")
