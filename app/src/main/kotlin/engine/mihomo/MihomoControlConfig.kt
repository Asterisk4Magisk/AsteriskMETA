// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import app.AppState
import engine.network.toPortOrNull

internal const val MihomoControlHost = "127.0.0.1"
internal const val DefaultMihomoControlPort = 9090
internal const val DefaultMihomoDelayTestUrl = "https://www.gstatic.com/generate_204"
internal const val DefaultMihomoDelayTimeoutMillis = 5000

internal data class MihomoControlConfig(
    val host: String = MihomoControlHost,
    val port: Int = DefaultMihomoControlPort,
    val secret: String = "",
) {
    val address: String
        get() = "$host:$port"

    val baseUrl: String
        get() = "http://$address"
}

internal fun AppState.mihomoControlConfig(): MihomoControlConfig {
    return MihomoControlConfig(
        port = mihomoControlPort.toPortOrNull() ?: DefaultMihomoControlPort,
        secret = mihomoControlSecret.trim(),
    )
}
