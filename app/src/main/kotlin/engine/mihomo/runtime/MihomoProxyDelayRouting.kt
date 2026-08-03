// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

internal fun MihomoProxiesState.groupFallbackFor(proxyId: MihomoProxyNodeId): String? {
    if (!proxyId.providerName.isNullOrBlank() || nodeById.containsKey(proxyId)) return null

    return groups.firstOrNull { group ->
        group.all.any { memberId -> memberId.name == proxyId.name }
    }?.name
}
