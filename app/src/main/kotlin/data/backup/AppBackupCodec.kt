// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data.backup

import kotlinx.serialization.json.Json

private val AppBackupJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

internal fun encodeAppBackup(backup: AppBackupFile): String =
    AppBackupJson.encodeToString(backup)

internal fun decodeAppBackup(content: String): AppBackupFile =
    AppBackupJson.decodeFromString<AppBackupFile>(content).migrateAppBackup()
