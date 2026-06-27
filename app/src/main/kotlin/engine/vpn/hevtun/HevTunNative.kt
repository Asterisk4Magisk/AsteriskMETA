// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn.hevtun

import androidx.annotation.Keep

internal interface HevTunNativeGateway {
    fun startService(configPath: String, fd: Int)
    fun stopService()
}

@Keep
internal object HevTunNative : HevTunNativeGateway {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyStopService()

    @JvmStatic
    @Keep
    @Suppress("FunctionName")
    private external fun TProxyGetStats(): LongArray

    override fun startService(configPath: String, fd: Int) {
        TProxyStartService(configPath, fd)
    }

    override fun stopService() {
        TProxyStopService()
    }
}
