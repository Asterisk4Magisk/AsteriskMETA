// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.hevtun

import android.content.Context
import engine.mihomo.prepareMihomoCoreLogPaths

internal fun Context.deleteHevSocks5TunnelLogFile() {
    val file = applicationContext.prepareMihomoCoreLogPaths().hevSocks5TunnelLogFile(HevSocks5TunnelLogFileName)
    if (file.exists() && !file.delete()) {
        error("Failed to delete ${file.absolutePath}")
    }
}
