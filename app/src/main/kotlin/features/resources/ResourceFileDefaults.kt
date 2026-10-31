// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources

import engine.mihomo.BundledCore

const val ResourceFileSourceMetaCubeXGithub = 0
const val ResourceFileSourceCustom = 1

// Keep custom executables separate when switching between core builds.
val ResourceFileMihomoCoreName = BundledCore.id
// Keep resource names aligned with the cores' home-dir lookup paths.
const val ResourceFileGeoIpName = "GeoIP.dat"
const val ResourceFileGeoSiteName = "GeoSite.dat"
const val ResourceFileMmdbName = "geoip.metadb"
const val ResourceFileCountryMmdbName = "Country.mmdb"
const val ResourceFileAsnName = "ASN.mmdb"
const val ResourceFileDirectCidrIpv4Name = "direct-cidr-v4.txt"
const val ResourceFileDirectCidrIpv6Name = "direct-cidr-v6.txt"

const val ResourceFileGeoIpUrl = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.dat"
const val ResourceFileGeoSiteUrl = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat"
const val ResourceFileMmdbUrl = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb"
const val ResourceFileCountryMmdbUrl =
    "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/country.mmdb"
const val ResourceFileAsnUrl = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb"
const val ResourceFileDirectCidrIpv4Url =
    "https://raw.githubusercontent.com/mayaxcn/china-ip-list/master/chnroute.txt"
const val ResourceFileDirectCidrIpv6Url =
    "https://raw.githubusercontent.com/mayaxcn/china-ip-list/master/chnroute_v6.txt"
