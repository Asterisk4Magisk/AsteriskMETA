// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.MihomoOverrideScriptState
import app.MihomoProfileState
import app.MihomoProfileType

@Entity(
    tableName = "mihomo_profiles",
    indices = [Index("position")],
)
internal data class MihomoProfileEntity(
    @PrimaryKey val id: Int,
    val position: Int,
    val name: String,
    val type: Int,
    val url: String,
    val userAgent: String,
    val updateInterval: String,
    val updateViaProxy: Boolean,
    @ColumnInfo(defaultValue = "")
    val ageSecretKey: String,
    val enabled: Boolean,
    val builtIn: Boolean,
    val lastUpdatedAtMillis: Long,
    val contentPath: String,
    val contentSha256: String,
    val contentSizeBytes: Long,
    val subscriptionUploadBytes: Long,
    val subscriptionDownloadBytes: Long,
    val subscriptionTotalBytes: Long,
    val subscriptionExpireAtSeconds: Long,
    val overrideScriptId: Int,
    @ColumnInfo(defaultValue = "0")
    val disableOverrides: Boolean,
    @ColumnInfo(defaultValue = "0")
    val syncFailed: Boolean,
) {
    fun toState(): MihomoProfileState {
        return MihomoProfileState(
            id = id,
            name = name,
            type = MihomoProfileType.fromStorageValue(type),
            url = url,
            userAgent = userAgent,
            updateInterval = updateInterval,
            updateViaProxy = updateViaProxy,
            ageSecretKey = ageSecretKey,
            enabled = enabled,
            builtIn = builtIn,
            lastUpdatedAtMillis = lastUpdatedAtMillis,
            contentPath = contentPath,
            contentSha256 = contentSha256,
            contentSizeBytes = contentSizeBytes,
            subscriptionInfo = app.MihomoSubscriptionInfo(
                uploadBytes = subscriptionUploadBytes,
                downloadBytes = subscriptionDownloadBytes,
                totalBytes = subscriptionTotalBytes,
                expireAtSeconds = subscriptionExpireAtSeconds,
            ),
            overrideScriptId = overrideScriptId,
            disableOverrides = disableOverrides,
            syncFailed = syncFailed,
        )
    }

    companion object {
        fun from(position: Int, profile: MihomoProfileState): MihomoProfileEntity {
            return MihomoProfileEntity(
                id = profile.id,
                position = position,
                name = profile.name,
                type = profile.type.storageValue,
                url = profile.url,
                userAgent = profile.userAgent,
                updateInterval = profile.updateInterval,
                updateViaProxy = profile.updateViaProxy,
                ageSecretKey = profile.ageSecretKey,
                enabled = profile.enabled,
                builtIn = profile.builtIn,
                lastUpdatedAtMillis = profile.lastUpdatedAtMillis,
                contentPath = profile.contentPath,
                contentSha256 = profile.contentSha256,
                contentSizeBytes = profile.contentSizeBytes,
                subscriptionUploadBytes = profile.subscriptionInfo.uploadBytes,
                subscriptionDownloadBytes = profile.subscriptionInfo.downloadBytes,
                subscriptionTotalBytes = profile.subscriptionInfo.totalBytes,
                subscriptionExpireAtSeconds = profile.subscriptionInfo.expireAtSeconds,
                overrideScriptId = profile.overrideScriptId,
                disableOverrides = profile.disableOverrides,
                syncFailed = profile.syncFailed,
            )
        }
    }
}

@Entity(
    tableName = "mihomo_override_scripts",
    indices = [Index("position")],
)
internal data class MihomoOverrideScriptEntity(
    @PrimaryKey val id: Int,
    val position: Int,
    val name: String,
    val content: String,
) {
    fun toState(): MihomoOverrideScriptState {
        return MihomoOverrideScriptState(
            id = id,
            name = name,
            content = content,
        )
    }

    companion object {
        fun from(position: Int, script: MihomoOverrideScriptState): MihomoOverrideScriptEntity {
            return MihomoOverrideScriptEntity(
                id = script.id,
                position = position,
                name = script.name,
                content = script.content,
            )
        }
    }
}

@Entity(
    tableName = "proxy_app_list_selected_apps",
    indices = [Index("position")],
)
internal data class ProxyAppListSelectedAppEntity(
    @PrimaryKey val packageKey: String,
    val position: Int,
)
