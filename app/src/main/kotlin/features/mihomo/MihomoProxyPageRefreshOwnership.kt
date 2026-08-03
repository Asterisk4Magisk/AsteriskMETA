// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

internal class MihomoProxyPageRefreshOwnership {
    private var refreshId = 0L

    fun beginRefresh(): Long = ++refreshId

    fun isCurrent(candidateRefreshId: Long): Boolean = candidateRefreshId == refreshId
}
