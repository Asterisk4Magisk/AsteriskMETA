// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.connections

import android.content.Context
import engine.mihomo.runtime.MihomoConnection
import system.getApplicationInfoCompat
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
    private val packageManager = context.applicationContext.packageManager
    private val packageByUid = HashMap<Int, String?>()
    private val labelByPackage = HashMap<String, String?>()

    fun resolve(connection: MihomoConnection): ConnectionAppIdentity {
        val processName = connection.appProcessName()
        val packageName = connection.uid?.let { uid -> packageForUid(uid, processName) }
            ?: processName?.takeIf { name -> labelForPackage(name) != null }
        if (packageName != null) {
            return ConnectionAppIdentity(
                key = "package:$packageName",
                packageName = packageName,
                label = labelForPackage(packageName) ?: packageName,
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
        if (uid <= 0L || uid > Int.MAX_VALUE) return null
        val appUid = uid.toInt()
        if (packageByUid.containsKey(appUid)) return packageByUid[appUid]
        val resolved = runCatching {
            val candidates = packageManager.getPackagesForUid(appUid)
                .orEmpty()
                .filter(String::isNotBlank)
            // A shared UID exposes several packages; prefer the one the core named.
            candidates.firstOrNull { candidate -> candidate == processName }
                ?: candidates.minWithOrNull(String.CASE_INSENSITIVE_ORDER)
        }.getOrNull()
        packageByUid[appUid] = resolved
        return resolved
    }

    private fun labelForPackage(packageName: String): String? {
        if (labelByPackage.containsKey(packageName)) return labelByPackage[packageName]
        val label: String? = runCatching {
            packageManager.getApplicationInfoCompat(packageName)
                .loadLabel(packageManager)
                .toString()
                .trim()
                .takeIf(String::isNotBlank)
        }.getOrNull()
        labelByPackage[packageName] = label
        return label
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
