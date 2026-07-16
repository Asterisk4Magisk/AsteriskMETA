// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.runtime.Stable
import features.resources.ResourceFileDirectCidrIpv4Name
import features.resources.ResourceFileDirectCidrIpv4Url
import features.resources.ResourceFileDirectCidrIpv6Name
import features.resources.ResourceFileDirectCidrIpv6Url
import features.resources.ResourceFileMihomoCoreName
import features.resources.MihomoCoreVersion
import features.resources.ResourceFileAsnName
import features.resources.ResourceFileAsnUrl
import features.resources.ResourceFileGeoIpName
import features.resources.ResourceFileGeoIpUrl
import features.resources.ResourceFileGeoSiteName
import features.resources.ResourceFileGeoSiteUrl
import features.resources.ResourceFileMmdbName
import features.resources.ResourceFileMmdbUrl
import features.resources.ResourceFileSourceCustom
import features.resources.ResourceFileSourceMetaCubeXGithub

@Stable
data class MihomoProfileState(
    val id: Int,
    val name: String,
    val type: MihomoProfileType = MihomoProfileType.File,
    val url: String = "",
    val userAgent: String = DefaultMihomoProfileUserAgent,
    val updateInterval: String = DefaultMihomoProfileUpdateInterval,
    val updateViaProxy: Boolean = false,
    val ageSecretKey: String = "",
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    val lastUpdatedAtMillis: Long = 0L,
    val contentPath: String = "",
    val contentSha256: String = "",
    val contentSizeBytes: Long = 0L,
    val subscriptionInfo: MihomoSubscriptionInfo = MihomoSubscriptionInfo(),
    val overrideScriptId: Int = DefaultMihomoOverrideScriptId,
    val disableOverrides: Boolean = false,
) {
    val hasContent: Boolean
        get() = contentPath.isNotBlank() && contentSizeBytes > 0L
}

enum class MihomoProfileType(
    val storageValue: Int,
) {
    File(0),
    Url(1),
    ;

    companion object {
        fun fromStorageValue(value: Int): MihomoProfileType {
            return entries.first { type -> type.storageValue == value }
        }
    }
}

@Stable
data class MihomoSubscriptionInfo(
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val expireAtSeconds: Long = 0L,
) {
    val usedBytes: Long
        get() = uploadBytes + downloadBytes

    val hasTraffic: Boolean
        get() = totalBytes > 0L
}

const val DefaultMihomoProfileId = 0
const val DefaultMihomoProfileUserAgent = "clash.meta"
const val DefaultMihomoProfileUpdateInterval = ""
const val DefaultMihomoOverrideScriptId = 0
const val DefaultMihomoOverrideScript = """const main = (config) => {
  return config
}"""

val DefaultMihomoProfiles = emptyList<MihomoProfileState>()

@Stable
data class MihomoOverrideScriptState(
    val id: Int,
    val name: String,
    val content: String = DefaultMihomoOverrideScript,
)

enum class ResourceFileKind(
    val fileName: String,
) {
    MihomoCore(ResourceFileMihomoCoreName),
    GeoIp(ResourceFileGeoIpName),
    GeoSite(ResourceFileGeoSiteName),
    Mmdb(ResourceFileMmdbName),
    Asn(ResourceFileAsnName),
    DirectCidrIpv4(ResourceFileDirectCidrIpv4Name),
    DirectCidrIpv6(ResourceFileDirectCidrIpv6Name),
    ;

    val displayName: String
        get() = when (this) {
            MihomoCore -> "Mihomo $MihomoCoreVersion"
            else -> fileName
        }
}

@Stable
data class ResourceFileStatus(
    val exists: Boolean = false,
    val sizeBytes: Long = 0,
    val updatedAtMillis: Long = 0,
)

@Stable
data class CustomResourceFileState(
    val id: Int,
    val name: String,
    val url: String,
)

@Stable
data class CustomResourceFileStatus(
    val file: CustomResourceFileState,
    val status: ResourceFileStatus = ResourceFileStatus(),
)

@Stable
data class ResourceFilesStatus(
    val resourceFiles: Map<ResourceFileKind, ResourceFileStatus> = emptyMap(),
    val customResourceFiles: List<CustomResourceFileStatus> = emptyList(),
)

data class ResourceFileUpdateSource(
    val id: Int,
    val geoIpUrl: String,
    val geoSiteUrl: String,
    val mmdbUrl: String,
    val asnUrl: String,
    val directCidrIpv4Url: String,
    val directCidrIpv6Url: String,
)

val ResourceFileUpdateSources = listOf(
    ResourceFileUpdateSource(
        id = ResourceFileSourceMetaCubeXGithub,
        geoIpUrl = ResourceFileGeoIpUrl,
        geoSiteUrl = ResourceFileGeoSiteUrl,
        mmdbUrl = ResourceFileMmdbUrl,
        asnUrl = ResourceFileAsnUrl,
        directCidrIpv4Url = ResourceFileDirectCidrIpv4Url,
        directCidrIpv6Url = ResourceFileDirectCidrIpv6Url,
    ),
)

fun resourceFileUpdateSourceAt(index: Int): ResourceFileUpdateSource {
    return ResourceFileUpdateSources.getOrElse(index) { ResourceFileUpdateSources.first() }
}

fun AppState.nextAvailableMihomoProfileId(): Int {
    val usedIds = mihomoProfiles.mapTo(mutableSetOf()) { profile -> profile.id }
    var candidate = nextMihomoProfileId.coerceAtLeast(1)
    while (candidate in usedIds) {
        candidate += 1
    }
    return candidate
}

fun AppState.nextAvailableMihomoOverrideScriptId(): Int {
    val usedIds = mihomoOverrideScripts.mapTo(mutableSetOf()) { script -> script.id }
    var candidate = nextMihomoOverrideScriptId.coerceAtLeast(1)
    while (candidate in usedIds) {
        candidate += 1
    }
    return candidate
}

fun AppState.nextAvailableCustomResourceFileId(): Int {
    val usedIds = customResourceFiles.mapTo(mutableSetOf()) { file -> file.id }
    var candidate = nextCustomResourceFileId.coerceAtLeast(1)
    while (candidate in usedIds) {
        candidate += 1
    }
    return candidate
}

fun AppState.resourceFileUpdateSource(): ResourceFileUpdateSource {
    if (resourceFileSource != ResourceFileSourceCustom) {
        return resourceFileUpdateSourceAt(resourceFileSource)
    }
    val fallback = ResourceFileUpdateSources.first()
    return ResourceFileUpdateSource(
        id = ResourceFileSourceCustom,
        geoIpUrl = customResourceFileGeoIpUrl.trim().ifBlank { fallback.geoIpUrl },
        geoSiteUrl = customResourceFileGeoSiteUrl.trim().ifBlank { fallback.geoSiteUrl },
        mmdbUrl = customResourceFileMmdbUrl.trim().ifBlank { fallback.mmdbUrl },
        asnUrl = customResourceFileAsnUrl.trim().ifBlank { fallback.asnUrl },
        directCidrIpv4Url = customResourceFileDirectCidrIpv4Url.trim().ifBlank { fallback.directCidrIpv4Url },
        directCidrIpv6Url = customResourceFileDirectCidrIpv6Url.trim().ifBlank { fallback.directCidrIpv6Url },
    )
}

fun MihomoProfileState.hasRuntimeRelevantChanges(next: MihomoProfileState): Boolean =
    type != next.type ||
        url != next.url ||
        ageSecretKey != next.ageSecretKey ||
        contentPath != next.contentPath ||
        contentSha256 != next.contentSha256 ||
        contentSizeBytes != next.contentSizeBytes ||
        overrideScriptId != next.overrideScriptId ||
        disableOverrides != next.disableOverrides

fun ResourceFileUpdateSource.urlFor(kind: ResourceFileKind): String? {
    return when (kind) {
        ResourceFileKind.MihomoCore -> null
        ResourceFileKind.GeoIp -> geoIpUrl
        ResourceFileKind.GeoSite -> geoSiteUrl
        ResourceFileKind.Mmdb -> mmdbUrl
        ResourceFileKind.Asn -> asnUrl
        ResourceFileKind.DirectCidrIpv4 -> directCidrIpv4Url
        ResourceFileKind.DirectCidrIpv6 -> directCidrIpv6Url
    }
}

fun ResourceFilesStatus.statusOf(kind: ResourceFileKind): ResourceFileStatus {
    return resourceFiles[kind] ?: ResourceFileStatus()
}

fun sanitizeCustomResourceFileName(value: String, fallback: String): String {
    val candidate = value
        .trim()
        .replace('\\', '/')
        .substringAfterLast('/')
        .map { char -> if (char.isResourceFileNameChar()) char else '_' }
        .joinToString("")
        .trim()
    return candidate
        .takeUnless { it.isBlank() || it == "." || it == ".." }
        ?: fallback
}

fun customResourceFileNameOrNull(value: String): String? {
    if (value.isBlank() || value != value.trim()) return null
    if (value.any { char -> char.isWhitespace() || char == ':' }) return null
    return sanitizeCustomResourceFileName(value, fallback = "")
        .takeIf { sanitized -> sanitized == value }
}

private fun Char.isResourceFileNameChar(): Boolean {
    return code >= 32 && this != '/' && this != '\\'
}
