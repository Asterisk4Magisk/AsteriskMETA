// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val AsteriskDatabaseName = "asteriskmeta.db"

@Database(
    entities = [
        MihomoProfileEntity::class,
        MihomoOverrideScriptEntity::class,
        ProxyAppListSelectedAppEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
internal abstract class AsteriskAppDatabase : RoomDatabase() {
    abstract fun appStateDao(): AppStateDao

    companion object {
        val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mihomo_profiles ADD COLUMN hwid TEXT NOT NULL DEFAULT ''")
            }
        }

        val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mihomo_profiles ADD COLUMN ageSecretKey TEXT NOT NULL DEFAULT ''")
            }
        }

        val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mihomo_profiles ADD COLUMN disableOverrides INTEGER NOT NULL DEFAULT 0")
            }
        }

        val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mihomo_profiles ADD COLUMN syncFailed INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
