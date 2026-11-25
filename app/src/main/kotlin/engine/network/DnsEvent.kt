// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.network

import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class DnsEvent(
    val seq: Long,
    val time: Long,
    val delay: Long,
    val qname: String,
    val qtype: Int,
    val status: String,
    val rcode: Int? = null,
    val answers: List<DnsAnswer> = emptyList(),
    val answerSource: String? = null,
    val upstream: String? = null,
    val uid: Long? = null,
    val route: String? = null,
    val outboundTag: String? = null,
    val uidSource: String? = null,
)

@Serializable
internal data class DnsAnswer(val name: String, val type: Int, val data: String)

internal sealed interface DnsEventUpdate {
    data object Ready : DnsEventUpdate
    data class Received(val event: DnsEvent) : DnsEventUpdate
}

internal class DnsEventsUnavailable : IOException()

private val DnsEventJson = Json { ignoreUnknownKeys = true }

internal fun decodeDnsEvent(raw: String): DnsEvent? =
    runCatching { DnsEventJson.decodeFromString<DnsEvent>(raw) }.getOrNull()
        ?.takeIf { it.qname.isNotBlank() && it.qtype in 0..65535 }
