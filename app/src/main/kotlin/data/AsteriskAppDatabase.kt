// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data

import androidx.room.Database
import androidx.room.RoomDatabase

internal const val AsteriskDatabaseName = "asteriskmeta.db"

@Database(
    entities = [
        MihomoProfileEntity::class,
        MihomoOverrideScriptEntity::class,
        ProxyAppListSelectedAppEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class AsteriskAppDatabase : RoomDatabase() {
    abstract fun appStateDao(): AppStateDao
}
