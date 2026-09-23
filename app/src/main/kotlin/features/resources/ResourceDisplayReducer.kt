// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources

import app.ResourceFileKind
import app.ResourceFileStatus
import app.ResourceFilesStatus
import app.statusOf

internal enum class ResourceDisplayAction {
    Update,
    Replace,
    Restore,
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

internal fun reduceResourceOverview(
    status: ResourceFilesStatus,
): ResourceOverviewState {
    val builtInStatuses = ResourceFileKind.entries.map(status::statusOf)
    val allStatuses = builtInStatuses
    return ResourceOverviewState(
        readyCount = allStatuses.count(ResourceFileStatus::exists),
        totalCount = allStatuses.size,
        totalSizeBytes = allStatuses.sumOf(ResourceFileStatus::sizeBytes),
    )
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
