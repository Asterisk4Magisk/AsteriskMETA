// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

internal data class RuntimeRouteRule(
    val index: Int,
    val type: String,
    val payload: String,
    val policy: String = "",
    val size: Int? = null,
    val hits: Long? = null,
    val lastHitAtMillis: Long? = null,
    val outbound: List<RuntimeRuleOutbound> = emptyList(),
)

internal data class RuntimeRuleOutbound(
    val name: String,
    val icon: String = "",
)
