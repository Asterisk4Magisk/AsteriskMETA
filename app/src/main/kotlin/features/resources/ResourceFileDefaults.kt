// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources

import app.ProjectInfo

const val ResourceFileSourceMetaCubeXGithub = 0
const val ResourceFileSourceCustom = 1

// Keep local target names aligned with mihomo's home-dir lookup paths.
const val ResourceFileMihomoCoreName = "mihomo"
const val ResourceFileGeoIpName = "GeoIP.dat"
const val ResourceFileGeoSiteName = "GeoSite.dat"
const val ResourceFileMmdbName = "geoip.metadb"
const val ResourceFileAsnName = "ASN.mmdb"

const val MihomoCoreVersion = ProjectInfo.MIHOMO_CORE_VERSION

const val ResourceFileGeoIpUrl = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.dat"
const val ResourceFileGeoSiteUrl = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat"
const val ResourceFileMmdbUrl = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb"
const val ResourceFileAsnUrl = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb"
