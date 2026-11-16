// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data.backup

import features.settings.migrateConnectionTestDefaults

internal fun AppBackupFile.migrateAppBackup(): AppBackupFile {
    require(format == AppBackupFormat) {
        "Invalid backup file format"
    }
    require(version in 1..CurrentAppBackupVersion) {
        if (version > CurrentAppBackupVersion) {
            "Backup file was created by a newer version"
        } else {
            "Unsupported backup file version"
        }
    }

    val settings = data.settings
    val migrated = migrateConnectionTestDefaults(
        settings.connectionTestUrl,
        settings.connectionTestConcurrent,
        settings.connectionTestDefaultsVersion,
    )
    return copy(data = data.copy(settings = settings.copy(
        connectionTestUrl = migrated.url,
        connectionTestConcurrent = migrated.concurrent,
        connectionTestDefaultsVersion = migrated.version,
    )))
}
