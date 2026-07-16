// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources

import app.CustomResourceFileState
import app.ResourceFileKind
import app.ResourceFileStatus
import app.ResourceFilesStatus
import app.customResourceFileNameOrNull
import app.statusOf
import java.net.URI

internal enum class ResourceDisplayAction {
    Update,
    Replace,
    Restore,
    Edit,
    Delete,
}

internal enum class ResourceVisualKind {
    Core,
    GeoIp,
    GeoSite,
    Database,
    Asn,
    Cidr,
    Custom,
}

internal data class ResourceOverviewState(
    val readyCount: Int,
    val totalCount: Int,
    val totalSizeBytes: Long,
)

internal enum class CustomResourceDraftError {
    InvalidName,
    DuplicateName,
    InvalidUrl,
}

internal data class CustomResourceDraftValidation(
    val name: String,
    val url: String,
    val error: CustomResourceDraftError? = null,
) {
    val valid: Boolean
        get() = error == null
}

internal fun reduceResourceOverview(
    status: ResourceFilesStatus,
    customFiles: List<CustomResourceFileState>,
): ResourceOverviewState {
    val builtInStatuses = ResourceFileKind.entries.map(status::statusOf)
    val customStatuses = customFiles.map { file ->
        status.customResourceFiles.firstOrNull { fileStatus -> fileStatus.file.id == file.id }
            ?.status ?: ResourceFileStatus()
    }
    val allStatuses = builtInStatuses + customStatuses
    return ResourceOverviewState(
        readyCount = allStatuses.count(ResourceFileStatus::exists),
        totalCount = allStatuses.size,
        totalSizeBytes = allStatuses.sumOf(ResourceFileStatus::sizeBytes),
    )
}

internal fun customResourceDisplayActions(file: CustomResourceFileState): List<ResourceDisplayAction> {
    return buildList {
        if (file.url.isNotBlank()) add(ResourceDisplayAction.Update)
        add(ResourceDisplayAction.Replace)
        add(ResourceDisplayAction.Edit)
        add(ResourceDisplayAction.Delete)
    }
}

internal fun resourceVisualKind(fileName: String): ResourceVisualKind {
    return when (fileName) {
        ResourceFileMihomoCoreName -> ResourceVisualKind.Core
        ResourceFileGeoIpName -> ResourceVisualKind.GeoIp
        ResourceFileGeoSiteName -> ResourceVisualKind.GeoSite
        ResourceFileMmdbName -> ResourceVisualKind.Database
        ResourceFileAsnName -> ResourceVisualKind.Asn
        ResourceFileDirectCidrIpv4Name,
        ResourceFileDirectCidrIpv6Name,
        -> ResourceVisualKind.Cidr
        else -> ResourceVisualKind.Custom
    }
}

internal fun validateCustomResourceDraft(
    name: String,
    url: String,
    reservedNames: Set<String>,
): CustomResourceDraftValidation {
    val cleanName = name.trim()
    val cleanUrl = url.trim()
    val fileName = customResourceFileNameOrNull(cleanName)
        ?: return CustomResourceDraftValidation(cleanName, cleanUrl, CustomResourceDraftError.InvalidName)
    if (fileName in reservedNames) {
        return CustomResourceDraftValidation(fileName, cleanUrl, CustomResourceDraftError.DuplicateName)
    }
    if (cleanUrl.isNotEmpty() && !cleanUrl.isValidHttpResourceUrl()) {
        return CustomResourceDraftValidation(fileName, cleanUrl, CustomResourceDraftError.InvalidUrl)
    }
    return CustomResourceDraftValidation(fileName, cleanUrl)
}

private fun String.isValidHttpResourceUrl(): Boolean {
    return runCatching {
        val uri = URI(this)
        (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
