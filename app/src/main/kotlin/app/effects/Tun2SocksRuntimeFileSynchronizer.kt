// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.effects

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.AppState
import app.modes.RunModeTun2Socks
import data.AndroidAppStateStore
import engine.tun2socks.deleteHevSocks5TunnelConfigFile
import engine.tun2socks.writeHevSocks5TunnelConfigFile
import features.logs.AndroidAppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Composable
internal fun Tun2SocksRuntimeFileSynchronizer(
    context: Context,
    stateStore: AndroidAppStateStore,
) {
    val appContext = context.applicationContext
    LaunchedEffect(appContext, stateStore) {
        stateStore.state
            .map { state -> state.toTun2SocksRuntimeFileRefresh() }
            .distinctUntilChanged { previous, next -> previous.signature == next.signature }
            .conflate()
            .collect { refresh ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        if (refresh.appState.runMode == RunModeTun2Socks) {
                            appContext.writeHevSocks5TunnelConfigFile(refresh.appState)
                        } else {
                            appContext.deleteHevSocks5TunnelConfigFile()
                        }
                    }
                }.onFailure { error ->
                    AndroidAppLogger.warn(LogTag, "Failed to sync tun2socks runtime file", error)
                }
            }
    }
}

private data class Tun2SocksRuntimeFileRefresh(
    val appState: AppState,
    val signature: Tun2SocksRuntimeFileSignature,
)

private data class Tun2SocksRuntimeFileSignature(
    val runMode: Int,
    val socks5ProxyPort: String? = null,
    val enableIpv6: Boolean? = null,
    val tunMtu: String? = null,
    val tunIpv4Cidr: String? = null,
    val tunIpv6Cidr: String? = null,
)

private fun AppState.toTun2SocksRuntimeFileRefresh(): Tun2SocksRuntimeFileRefresh {
    return Tun2SocksRuntimeFileRefresh(
        appState = this,
        signature = if (runMode == RunModeTun2Socks) {
            Tun2SocksRuntimeFileSignature(
                runMode = runMode,
                socks5ProxyPort = socks5ProxyPort,
                enableIpv6 = enableIpv6,
                tunMtu = tunMtu,
                tunIpv4Cidr = tunIpv4Cidr,
                tunIpv6Cidr = tunIpv6Cidr,
            )
        } else {
            Tun2SocksRuntimeFileSignature(runMode = runMode)
        },
    )
}

private const val LogTag = "Tun2SocksRuntimeFile"
