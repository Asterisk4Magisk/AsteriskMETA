// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import features.subscription.encodeSubscriptionRequestHeaders
import features.subscription.legacySubscriptionRequestHeaders

internal const val AsteriskDatabaseName = "asteriskmeta.db"

@Database(
    entities = [
        MihomoProfileEntity::class,
        MihomoOverrideScriptEntity::class,
        ProxyAppListSelectedAppEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
internal abstract class AsteriskAppDatabase : RoomDatabase() {
    abstract fun appStateDao(): AppStateDao

    companion object {
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

        val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mihomo_profiles ADD COLUMN hwid TEXT NOT NULL DEFAULT ''")
            }
        }

        val Migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mihomo_profiles ADD COLUMN ruleOverrideJson TEXT NOT NULL DEFAULT '{}'")
            }
        }

        val Migration6To7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                        CREATE TABLE `mihomo_profiles_new` (
                            `id` INTEGER NOT NULL,
                            `position` INTEGER NOT NULL,
                            `name` TEXT NOT NULL,
                            `type` INTEGER NOT NULL,
                            `url` TEXT NOT NULL,
                            `userAgent` TEXT NOT NULL,
                        `requestHeaders` TEXT,
                            `updateInterval` TEXT NOT NULL,
                            `updateViaProxy` INTEGER NOT NULL,
                            `ageSecretKey` TEXT NOT NULL DEFAULT '',
                            `enabled` INTEGER NOT NULL,
                            `builtIn` INTEGER NOT NULL,
                            `lastUpdatedAtMillis` INTEGER NOT NULL,
                            `contentPath` TEXT NOT NULL,
                            `contentSha256` TEXT NOT NULL,
                            `contentSizeBytes` INTEGER NOT NULL,
                            `subscriptionUploadBytes` INTEGER NOT NULL,
                            `subscriptionDownloadBytes` INTEGER NOT NULL,
                            `subscriptionTotalBytes` INTEGER NOT NULL,
                            `subscriptionExpireAtSeconds` INTEGER NOT NULL,
                            `overrideScriptId` INTEGER NOT NULL,
                            `disableOverrides` INTEGER NOT NULL DEFAULT 0,
                            `syncFailed` INTEGER NOT NULL DEFAULT 0,
                            `ruleOverrideJson` TEXT NOT NULL DEFAULT '{}',
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                        INSERT INTO mihomo_profiles_new (
                            id, position, name, type, url,
                            userAgent, requestHeaders, updateInterval, updateViaProxy, ageSecretKey,
                            enabled, builtIn, lastUpdatedAtMillis, contentPath, contentSha256,
                            contentSizeBytes, subscriptionUploadBytes, subscriptionDownloadBytes, subscriptionTotalBytes, subscriptionExpireAtSeconds,
                            overrideScriptId, disableOverrides, syncFailed, ruleOverrideJson
                        )
                        SELECT
                            id, position, name, type, url,
                        userAgent, NULL, updateInterval, updateViaProxy, ageSecretKey,
                            enabled, builtIn, lastUpdatedAtMillis, contentPath, contentSha256,
                            contentSizeBytes, subscriptionUploadBytes, subscriptionDownloadBytes, subscriptionTotalBytes, subscriptionExpireAtSeconds,
                            overrideScriptId, disableOverrides, syncFailed, ruleOverrideJson
                        FROM mihomo_profiles
                    """.trimIndent(),
                )
                db.query("SELECT id, hwid FROM mihomo_profiles WHERE hwid <> ''").use { cursor ->
                    db.compileStatement("UPDATE mihomo_profiles_new SET requestHeaders = ? WHERE id = ?").use { update ->
                        while (cursor.moveToNext()) {
                            val headers = legacySubscriptionRequestHeaders(cursor.getString(1)) ?: continue
                            update.bindString(1, encodeSubscriptionRequestHeaders(headers))
                            update.bindLong(2, cursor.getLong(0))
                            update.executeUpdateDelete()
                        }
                    }
                }
                db.execSQL("DROP TABLE mihomo_profiles")
                db.execSQL("ALTER TABLE mihomo_profiles_new RENAME TO mihomo_profiles")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mihomo_profiles_position ON mihomo_profiles(position)")
            }
        }
    }
}
