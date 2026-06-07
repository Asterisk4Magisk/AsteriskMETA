// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package system

import utils.toTrimmedNonEmptyDistinctList

class AndroidNetworkInterfaceProvider(
    private val rootAccess: AndroidRootShellGateway,
) {
    suspend fun listNetworkInterfaces(): List<String> {
        val result = rootAccess.exec(RootNetworkInterfaceCommand, ShellExecOptions(logFailure = false))
        if (result.errno != 0) {
            error(result.stderr.ifBlank { "Unable to read Android network interfaces" })
        }
        return result.stdout
            .lineSequence()
            .toList()
            .normalizedNetworkInterfaceNames()
    }
}

private fun List<String>.normalizedNetworkInterfaceNames(): List<String> {
    return toTrimmedNonEmptyDistinctList()
        .asSequence()
        .filter { it != "." && it != ".." }
        .toList()
}

private val RootNetworkInterfaceCommand = $$"""
    for path in /sys/class/net/*; do
        [ -e "$path" ] || continue
        name="${path##*/}"
        [ -n "$name" ] && echo "$name"
    done
""".trimIndent()
