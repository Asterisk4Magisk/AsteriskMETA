// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources

import app.ResourceFileKind
import engine.network.parseCidrAddressOrNull

internal fun ResourceFileKind.isDirectCidrResource(): Boolean =
    this == ResourceFileKind.DirectCidrIpv4 || this == ResourceFileKind.DirectCidrIpv6

internal class InvalidCidrResourceException(val lineNumber: Int) :
    IllegalArgumentException("Invalid CIDR at line $lineNumber")

internal class EmptyCidrResourceException : IllegalArgumentException("The CIDR file is empty")

internal class CidrResourceChangedException(fileName: String) :
    IllegalStateException("$fileName changed after it was opened")

internal fun requireValidCidrResource(kind: ResourceFileKind, content: String) {
    require(kind.isDirectCidrResource())
    if (content.isBlank()) throw EmptyCidrResourceException()
    val ipv6 = kind == ResourceFileKind.DirectCidrIpv6
    content.lineSequence().forEachIndexed { index, line ->
        val value = line.substringBefore('#').trim()
        if (value.isNotEmpty()) {
            val cidr = parseCidrAddressOrNull(value)
            if (cidr == null || (':' in cidr.address) != ipv6) {
                throw InvalidCidrResourceException(index + 1)
            }
        }
    }
}
