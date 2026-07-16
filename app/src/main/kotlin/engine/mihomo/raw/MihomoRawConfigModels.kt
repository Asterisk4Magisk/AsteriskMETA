// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.raw

import engine.mihomo.MihomoControlConfig

internal data class RawConfigField<T>(
    val value: T? = null,
    val path: String,
    val problem: String? = null,
)

internal data class MihomoRawApiConfig(
    val control: MihomoControlConfig,
    val hasSecret: Boolean,
)

internal data class MihomoRawSocksInbound(
    val port: Int,
    val path: String,
)

internal data class MihomoRawTunInbound(
    val device: String,
    val stack: String,
    val mtu: Int,
    val ipv4Address: String,
    val ipv6Address: String? = null,
    val path: String,
)

internal data class MihomoRawDnsHijack(
    val dnsEnabled: Boolean,
    val proven: Boolean,
    val matchedRule: String? = null,
    val outbound: String? = null,
)

internal data class MihomoRawConfigSnapshot(
    val sourceSha256: String,
    val mode: RawConfigField<String>,
    val logLevel: RawConfigField<String>,
    val ipv6: RawConfigField<Boolean>,
    val geodataMode: RawConfigField<Boolean>,
    val geodataLoader: RawConfigField<String>,
    val snifferEnabled: RawConfigField<Boolean>,
    val dnsEnabled: RawConfigField<Boolean>,
    val api: RawConfigField<MihomoRawApiConfig>,
    val tproxyPort: RawConfigField<Int>,
    val socksInbound: RawConfigField<MihomoRawSocksInbound>,
    val tunInbound: RawConfigField<MihomoRawTunInbound>,
    val dnsHijack: RawConfigField<MihomoRawDnsHijack>,
)

internal data class MihomoRawConfigParseResult(
    val sourceBytes: ByteArray,
    val snapshot: MihomoRawConfigSnapshot? = null,
    val error: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MihomoRawConfigParseResult

        if (!sourceBytes.contentEquals(other.sourceBytes)) return false
        if (snapshot != other.snapshot) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sourceBytes.contentHashCode()
        result = 31 * result + (snapshot?.hashCode() ?: 0)
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

internal enum class RawConfigReadiness {
    Ready,
    Degraded,
    Blocked,
}

internal data class RawConfigIssue(
    val readiness: RawConfigReadiness,
    val fieldPath: String,
    val reason: String,
)

internal data class MihomoRawConfigCheckResult(
    val readiness: RawConfigReadiness,
    val issues: List<RawConfigIssue>,
) {
    val canStart: Boolean
        get() = readiness != RawConfigReadiness.Blocked
}
