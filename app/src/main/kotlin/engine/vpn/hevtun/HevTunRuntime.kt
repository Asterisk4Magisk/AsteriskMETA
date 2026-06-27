// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn.hevtun

import engine.hevtun.HevSocks5TunnelConfig
import engine.hevtun.writeConfigFile

internal class HevTunRuntime(
    private val nativeGateway: HevTunNativeGateway = HevTunNative,
) {
    private var running = false

    fun start(config: HevSocks5TunnelConfig, tunFd: Int) {
        stop()
        config.writeConfigFile()
        nativeGateway.startService(config.configPath, tunFd)
        running = true
    }

    fun stop() {
        if (!running) return
        try {
            nativeGateway.stopService()
        } finally {
            running = false
        }
    }
}
