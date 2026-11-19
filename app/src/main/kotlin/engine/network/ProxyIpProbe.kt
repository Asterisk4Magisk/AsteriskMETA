// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.network

import java.io.File
import java.io.IOException
import java.security.SecureRandom
import kotlinx.serialization.Serializable
import utils.writeAtomically

@Serializable
internal data class ProxyIpProbeRequest(
    val outboundTag: String,
    val url: String,
)

@Serializable
internal data class ProxyIpProbeResponse(
    val outboundTag: String,
    val status: String,
    val response: String? = null,
)

internal class ProxyIpProbeException(val code: String) : IOException(code)

@Synchronized
internal fun readOrCreateProxyIpProbeToken(directory: File): String {
    val file = File(directory, "proxy-ip-probe.token")
    if (file.isFile) {
        val token = file.readText(Charsets.US_ASCII)
        check(token.length == 64 && token.all { it in "0123456789abcdef" })
        return token
    }
    val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
    val token = bytes.joinToString("") { "%02x".format(it) }
    writeAtomically(file) { it.write(token.toByteArray(Charsets.US_ASCII)) }
    return token
}
