// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.connections

import android.content.Context
import engine.mihomo.runtime.MihomoConnection
import features.monitoring.MonitoringAppLookup
import java.util.Locale

/**
 * Resolves which installed application owns a connection.
 *
 * The core attributes a connection to a UID and, when it can observe the original
 * socket, to a process name. The UID is authoritative because the process name is
 * missing in tunnel modes that cannot read it; the process name is still useful for
 * a connection whose owner does not resolve to an installed package.
 *
 * Package manager lookups are cached per UID and per package name: the monitor
 * refreshes at most once per second and only hosts a small number of owners.
 */
internal class ConnectionAppResolver(context: Context) {
    private val appLookup = MonitoringAppLookup(context)

    fun resolve(connection: MihomoConnection): ConnectionAppIdentity {
        val processName = connection.appProcessName()
        val packageName = connection.uid?.let { uid -> packageForUid(uid, processName) }
            ?: processName?.takeIf { name -> appLookup.labelForPackage(name) != null }
        if (packageName != null) {
            return ConnectionAppIdentity(
                key = "package:$packageName",
                packageName = packageName,
                label = appLookup.labelForPackage(packageName) ?: packageName,
            )
        }
        if (processName != null) {
            return ConnectionAppIdentity(
                key = "process:${processName.lowercase(Locale.ROOT)}",
                label = processName,
            )
        }
        return unknownConnectionAppIdentity
    }

    private fun packageForUid(uid: Long, processName: String?): String? {
        if (uid <= 0L) return null
        val candidates = appLookup.packagesForUid(uid)
        // A shared UID exposes several packages; prefer the one the core named.
        return candidates.firstOrNull { it == processName }
            ?: candidates.minWithOrNull(String.CASE_INSENSITIVE_ORDER)
    }
}

/**
 * The process name the core reported, normalized to the application id it can carry.
 *
 * Android reports app processes as `com.example.app` or `com.example.app:service`, while
 * native processes arrive as a path such as `/system/bin/ping`. A process path is only
 * used when it still contains an application id, so an opaque binary path does not
 * become its own group.
 */
private fun MihomoConnection.appProcessName(): String? {
    val reportName = process.trim().substringAfterLast('/').substringBefore(':').trim()
    if (reportName.isNotEmpty()) return reportName
    val pathName = processPath.trim().substringAfterLast('/').substringBefore(':').trim()
    return pathName.takeIf { name -> '.' in name }
}
