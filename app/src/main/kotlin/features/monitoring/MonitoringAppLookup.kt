// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring

import android.content.Context
import system.getApplicationInfoCompat

internal class MonitoringAppLookup(context: Context, private val cacheLimit: Int = Int.MAX_VALUE) {
    private val packageManager = context.applicationContext.packageManager
    private val packagesByUid = HashMap<Int, List<String>>()
    private val labelsByPackage = HashMap<String, String?>()

    fun packagesForUid(uid: Long): List<String> {
        if (uid !in 0..Int.MAX_VALUE.toLong()) return emptyList()
        val appUid = uid.toInt()
        packagesByUid[appUid]?.let { return it }
        if (packagesByUid.size >= cacheLimit) packagesByUid.clear()
        return runCatching {
            packageManager.getPackagesForUid(appUid).orEmpty().filter(String::isNotBlank)
        }.getOrDefault(emptyList()).also { packagesByUid[appUid] = it }
    }

    fun labelForPackage(packageName: String): String? {
        if (labelsByPackage.containsKey(packageName)) return labelsByPackage[packageName]
        if (labelsByPackage.size >= cacheLimit) labelsByPackage.clear()
        return runCatching {
            packageManager.getApplicationInfoCompat(packageName).loadLabel(packageManager)
                .toString().trim().takeIf(String::isNotBlank)
        }.getOrNull().also { labelsByPackage[packageName] = it }
    }
}
