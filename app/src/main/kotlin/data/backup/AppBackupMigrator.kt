// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data.backup

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

    return when (version) {
        CurrentAppBackupVersion -> this
        else -> error("Unsupported backup file version")
    }
}
