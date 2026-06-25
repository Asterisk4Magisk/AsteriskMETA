// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.R
import engine.mihomo.DefaultMihomoDnsFakeIpRange
import engine.mihomo.MihomoDnsModeFakeIp
import engine.mihomo.MihomoDnsModeValues
import engine.network.isCidrAddress
import engine.network.isIpv4CidrAddress
import engine.network.isIpAddress
import features.settings.DnsSettingsDraft
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import ui.components.StringListEditor
import utils.toTrimmedNonEmptyDistinctList

private const val DnsHostSeparator = ':'
private const val NameserverPolicySeparator = "=>"
private const val SystemDnsServer = "system://"

private val MihomoDnsUrlSchemes = setOf(
    "https",
    "h2c",
    "h3",
    "https+local",
    "h2c+local",
    "h3+local",
    "quic",
    "quic+local",
    "tls",
    "tls+local",
    "tcp",
    "tcp+local",
    "udp",
    "udp+local",
    "dhcp",
    "system",
    "rcode",
)

@Composable
internal fun DnsSettingsBottomSheet(
    show: Boolean,
    draft: DnsSettingsDraft,
    onDraftChange: (DnsSettingsDraft) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (DnsSettingsDraft) -> Unit,
) {
    val dnsServerInvalidMessage = stringResource(R.string.settings_dns_server_invalid)
    val dnsDomainInvalidMessage = stringResource(R.string.settings_dns_domain_invalid)
    val dnsPolicyInvalidMessage = stringResource(R.string.settings_dns_policy_invalid)
    val dnsHostsInvalidMessage = stringResource(R.string.settings_dns_hosts_invalid)
    val dnsCidrInvalidMessage = stringResource(R.string.settings_dns_cidr_invalid)
    val dnsGeoipCodeInvalidMessage = stringResource(R.string.settings_dns_geoip_code_invalid)

    val sanitizedDraft = draft.sanitized()
    val fakeIpRangeError = if (
        sanitizedDraft.dnsEnhancedMode != MihomoDnsModeFakeIp ||
        isIpv4CidrAddress(draft.dnsFakeIpRange)
    ) {
        null
    } else {
        dnsCidrInvalidMessage
    }
    val geoipCodeError = if (
        !draft.dnsFallbackFilterGeoip ||
        isGeoipCode(draft.dnsFallbackFilterGeoipCode)
    ) {
        null
    } else {
        dnsGeoipCodeInvalidMessage
    }
    val canSave = fakeIpRangeError == null && geoipCodeError == null

    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.settings_dns),
        startAction = {
            TextButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismissRequest,
            )
        },
        endAction = {
            TextButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    if (canSave) {
                        onSave(sanitizedDraft)
                    }
                },
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        key(show) {
            SettingsSheetContent {
                DnsSheetSection(title = stringResource(R.string.settings_dns_section_basic)) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_vpn_local_dns),
                        summary = stringResource(R.string.settings_vpn_local_dns_summary),
                        checked = draft.enableLocalDns,
                        onCheckedChange = { onDraftChange(draft.copy(enableLocalDns = it)) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_dns_override),
                        summary = stringResource(R.string.settings_dns_override_summary),
                        checked = draft.overrideDns,
                        onCheckedChange = { onDraftChange(draft.copy(overrideDns = it)) },
                    )
                    WindowDropdownPreference(
                        title = stringResource(R.string.settings_dns_enhanced_mode),
                        items = MihomoDnsModeValues,
                        selectedIndex = draft.dnsEnhancedMode.coerceIn(MihomoDnsModeValues.indices),
                        onSelectedIndexChange = { onDraftChange(draft.copy(dnsEnhancedMode = it)) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_dns_respect_rules),
                        checked = draft.dnsRespectRules,
                        onCheckedChange = { onDraftChange(draft.copy(dnsRespectRules = it)) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_dns_prefer_h3),
                        checked = draft.dnsPreferH3,
                        onCheckedChange = { onDraftChange(draft.copy(dnsPreferH3 = it)) },
                    )
                }

                AnimatedVisibility(
                    visible = draft.dnsEnhancedMode == MihomoDnsModeFakeIp,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    DnsSheetSection(title = stringResource(R.string.settings_dns_section_fake_ip)) {
                        DnsInlineTextField(
                            value = draft.dnsFakeIpRange,
                            onValueChange = { onDraftChange(draft.copy(dnsFakeIpRange = it)) },
                            label = stringResource(R.string.settings_dns_fake_ip_range),
                            errorText = fakeIpRangeError,
                        )
                        StringListEditor(
                            editorKey = "dns-fake-ip-filter:$show",
                            title = stringResource(R.string.settings_dns_fake_ip_filter),
                            values = draft.dnsFakeIpFilter.toTrimmedNonEmptyDistinctList(),
                            onValuesChange = { onDraftChange(draft.copy(dnsFakeIpFilter = it.toTrimmedNonEmptyDistinctList())) },
                            emptyText = stringResource(R.string.settings_dns_list_empty),
                            validateInput = { dnsDomainInputError(it, dnsDomainInvalidMessage) },
                        )
                    }
                }

                DnsSheetSection(title = stringResource(R.string.settings_dns_section_nameserver)) {
                    StringListEditor(
                        editorKey = "dns-default-nameserver:$show",
                        title = stringResource(R.string.settings_dns_default_nameserver),
                        description = stringResource(R.string.settings_dns_default_nameserver_summary),
                        values = draft.dnsDefaultNameserver.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = { onDraftChange(draft.copy(dnsDefaultNameserver = it.toTrimmedNonEmptyDistinctList())) },
                        emptyText = stringResource(R.string.settings_dns_list_empty),
                        validateInput = { dnsServerInputError(it, dnsServerInvalidMessage) },
                    )
                    Spacer(Modifier.height(8.dp))
                    StringListEditor(
                        editorKey = "dns-nameserver:$show",
                        title = stringResource(R.string.settings_dns_nameserver),
                        description = stringResource(R.string.settings_dns_nameserver_summary),
                        values = draft.dnsNameserver.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = { onDraftChange(draft.copy(dnsNameserver = it.toTrimmedNonEmptyDistinctList())) },
                        emptyText = stringResource(R.string.settings_dns_list_empty),
                        validateInput = { dnsServerInputError(it, dnsServerInvalidMessage) },
                    )
                    Spacer(Modifier.height(8.dp))
                    StringListEditor(
                        editorKey = "dns-proxy-server-nameserver:$show",
                        title = stringResource(R.string.settings_dns_proxy_server_nameserver),
                        values = draft.dnsProxyServerNameserver.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = {
                            onDraftChange(draft.copy(dnsProxyServerNameserver = it.toTrimmedNonEmptyDistinctList()))
                        },
                        emptyText = stringResource(R.string.settings_dns_list_empty),
                        validateInput = { dnsServerInputError(it, dnsServerInvalidMessage) },
                    )
                    Spacer(Modifier.height(8.dp))
                    StringListEditor(
                        editorKey = "dns-fallback:$show",
                        title = stringResource(R.string.settings_dns_fallback),
                        values = draft.dnsFallback.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = { onDraftChange(draft.copy(dnsFallback = it.toTrimmedNonEmptyDistinctList())) },
                        emptyText = stringResource(R.string.settings_dns_list_empty),
                        validateInput = { dnsServerInputError(it, dnsServerInvalidMessage) },
                    )
                }

                DnsSheetSection(title = stringResource(R.string.settings_dns_section_policy)) {
                    StringListEditor(
                        editorKey = "dns-nameserver-policy:$show",
                        title = stringResource(R.string.settings_dns_nameserver_policy),
                        description = stringResource(R.string.settings_dns_nameserver_policy_format),
                        values = draft.dnsNameserverPolicy.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = {
                            onDraftChange(draft.copy(dnsNameserverPolicy = it.toTrimmedNonEmptyDistinctList()))
                        },
                        emptyText = stringResource(R.string.settings_dns_list_empty),
                        validateInput = { nameserverPolicyInputError(it, dnsPolicyInvalidMessage, dnsServerInvalidMessage) },
                    )
                }

                DnsSheetSection(title = stringResource(R.string.settings_dns_section_fallback_filter)) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_dns_geoip_filter),
                        checked = draft.dnsFallbackFilterGeoip,
                        onCheckedChange = { onDraftChange(draft.copy(dnsFallbackFilterGeoip = it)) },
                    )
                    AnimatedVisibility(
                        visible = draft.dnsFallbackFilterGeoip,
                        enter = fadeIn() + expandVertically(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        DnsInlineTextField(
                            value = draft.dnsFallbackFilterGeoipCode,
                            onValueChange = { onDraftChange(draft.copy(dnsFallbackFilterGeoipCode = it)) },
                            label = stringResource(R.string.settings_dns_geoip_code),
                            errorText = geoipCodeError,
                        )
                    }
                    StringListEditor(
                        editorKey = "dns-fallback-geosite:$show",
                        title = "Geosite",
                        values = draft.dnsFallbackFilterGeosite.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = {
                            onDraftChange(draft.copy(dnsFallbackFilterGeosite = it.toTrimmedNonEmptyDistinctList()))
                        },
                        emptyText = stringResource(R.string.settings_dns_list_empty),
                        validateInput = { dnsDomainInputError(it, dnsDomainInvalidMessage) },
                    )
                    Spacer(Modifier.height(8.dp))
                    StringListEditor(
                        editorKey = "dns-fallback-ipcidr:$show",
                        title = "IP CIDR",
                        values = draft.dnsFallbackFilterIpcidr.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = {
                            onDraftChange(draft.copy(dnsFallbackFilterIpcidr = it.toTrimmedNonEmptyDistinctList()))
                        },
                        emptyText = stringResource(R.string.settings_dns_list_empty),
                        validateInput = { if (isCidrAddress(it)) null else dnsCidrInvalidMessage },
                    )
                    Spacer(Modifier.height(8.dp))
                    StringListEditor(
                        editorKey = "dns-fallback-domain:$show",
                        title = stringResource(R.string.settings_dns_domain),
                        values = draft.dnsFallbackFilterDomain.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = {
                            onDraftChange(draft.copy(dnsFallbackFilterDomain = it.toTrimmedNonEmptyDistinctList()))
                        },
                        emptyText = stringResource(R.string.settings_dns_list_empty),
                        validateInput = { dnsDomainInputError(it, dnsDomainInvalidMessage) },
                    )
                }

                DnsSheetSection(title = stringResource(R.string.settings_dns_section_hosts)) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_dns_use_hosts),
                        checked = draft.dnsUseHosts,
                        onCheckedChange = { onDraftChange(draft.copy(dnsUseHosts = it)) },
                    )
                    AnimatedVisibility(
                        visible = draft.dnsUseHosts,
                        enter = fadeIn() + expandVertically(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            SwitchPreference(
                                title = stringResource(R.string.settings_dns_use_system_hosts),
                                checked = draft.dnsUseSystemHosts,
                                onCheckedChange = { onDraftChange(draft.copy(dnsUseSystemHosts = it)) },
                            )
                            StringListEditor(
                                editorKey = "dns-hosts:$show",
                                title = stringResource(R.string.settings_dns_hosts),
                                description = stringResource(R.string.settings_dns_hosts_format),
                                values = draft.dnsHosts.toTrimmedNonEmptyDistinctList(),
                                onValuesChange = { onDraftChange(draft.copy(dnsHosts = it.toTrimmedNonEmptyDistinctList())) },
                                emptyText = stringResource(R.string.settings_dns_hosts_empty),
                                validateInput = { dnsHostInputError(it, dnsHostsInvalidMessage) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsSheetSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        content()
    }
}

@Composable
private fun DnsInlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorText: String?,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        SettingsTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            errorText = errorText,
        )
    }
}

private fun DnsSettingsDraft.sanitized(): DnsSettingsDraft {
    return copy(
        enableLocalDns = enableLocalDns,
        dnsEnhancedMode = dnsEnhancedMode.coerceIn(MihomoDnsModeValues.indices),
        dnsFakeIpRange = dnsFakeIpRange.trim().ifBlank { DefaultMihomoDnsFakeIpRange },
        dnsFakeIpFilter = dnsFakeIpFilter.toTrimmedNonEmptyDistinctList(),
        dnsDefaultNameserver = dnsDefaultNameserver.toTrimmedNonEmptyDistinctList(),
        dnsNameserver = dnsNameserver.toTrimmedNonEmptyDistinctList(),
        dnsNameserverPolicy = dnsNameserverPolicy.toTrimmedNonEmptyDistinctList(),
        dnsProxyServerNameserver = dnsProxyServerNameserver.toTrimmedNonEmptyDistinctList(),
        dnsFallback = dnsFallback.toTrimmedNonEmptyDistinctList(),
        dnsFallbackFilterGeoipCode = dnsFallbackFilterGeoipCode.trim().uppercase().ifBlank { "CN" },
        dnsFallbackFilterGeosite = dnsFallbackFilterGeosite.toTrimmedNonEmptyDistinctList(),
        dnsFallbackFilterIpcidr = dnsFallbackFilterIpcidr.toTrimmedNonEmptyDistinctList(),
        dnsFallbackFilterDomain = dnsFallbackFilterDomain.toTrimmedNonEmptyDistinctList(),
        dnsHosts = dnsHosts.toTrimmedNonEmptyDistinctList(),
    )
}

private fun dnsServerInputError(input: String, invalidMessage: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return invalidMessage
    return if (isMihomoDnsServer(trimmed)) null else invalidMessage
}

private fun isMihomoDnsServer(value: String): Boolean {
    if (value.equals("localhost", ignoreCase = true)) {
        return true
    }

    val schemeEnd = value.indexOf("://")
    if (schemeEnd >= 0) {
        val scheme = value.substring(0, schemeEnd).lowercase()
        if (scheme !in MihomoDnsUrlSchemes) return false
        if (scheme == "system") return value == SystemDnsServer
        val authority = value.substring(schemeEnd + 3)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
        if (scheme == "rcode") return authority.isNotBlank()
        if (scheme == "dhcp") return authority.isNotBlank()
        return isMihomoDnsAuthority(authority)
    }

    return isIpAddress(value) || (!value.contains(":") && isDnsHostDomain(value))
}

private fun isMihomoDnsAuthority(authority: String): Boolean {
    val trimmed = authority.trim()
    if (trimmed.isBlank()) return false

    if (trimmed.startsWith("[")) {
        val closeBracketIndex = trimmed.indexOf(']')
        if (closeBracketIndex <= 1) return false
        val host = trimmed.substring(1, closeBracketIndex)
        val rest = trimmed.substring(closeBracketIndex + 1)
        return isIpAddress(host) && (rest.isEmpty() || rest.startsWith(":") && isPort(rest.drop(1)))
    }

    val colonCount = trimmed.count { it == ':' }
    if (colonCount == 0) {
        return isIpAddress(trimmed) || isDnsHostDomain(trimmed)
    }
    if (colonCount == 1) {
        val host = trimmed.substringBefore(':')
        val port = trimmed.substringAfter(':')
        return (isIpAddress(host) || isDnsHostDomain(host)) && isPort(port)
    }

    return isIpAddress(trimmed)
}

private fun dnsDomainInputError(input: String, invalidMessage: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return invalidMessage
    if (trimmed.startsWith("regexp:", ignoreCase = true)) {
        return if (trimmed.substringAfter(":").isBlank()) invalidMessage else null
    }

    val supportedPrefix = trimmed.substringBefore(":", missingDelimiterValue = "")
        .lowercase()
        .takeIf { it in setOf("domain", "full", "keyword", "geosite", "rule-set", "ext") }
    if (supportedPrefix != null) {
        return if (trimmed.substringAfter(":").isBlank()) invalidMessage else null
    }

    return if (trimmed.contains("://") || trimmed.contains("/")) invalidMessage else null
}

private fun nameserverPolicyInputError(
    input: String,
    invalidMessage: String,
    invalidServerMessage: String,
): String? {
    val separatorIndex = input.indexOf(NameserverPolicySeparator)
    if (separatorIndex <= 0 || separatorIndex == input.lastIndex - NameserverPolicySeparator.lastIndex) {
        return invalidMessage
    }
    val rule = input.substring(0, separatorIndex).trim()
    val servers = input.substring(separatorIndex + NameserverPolicySeparator.length)
        .split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
    if (dnsDomainInputError(rule, invalidMessage) != null || servers.isEmpty()) return invalidMessage
    return servers.firstNotNullOfOrNull { server -> dnsServerInputError(server, invalidServerMessage) }
}

private fun dnsHostInputError(input: String, invalidMessage: String): String? {
    val separatorIndex = input.indexOf(DnsHostSeparator)
    if (separatorIndex <= 0 || separatorIndex == input.lastIndex) return invalidMessage

    val domain = input.substring(0, separatorIndex).trim()
    val addresses = input.substring(separatorIndex + 1)
        .split(",")
        .map { it.trim().trim('[', ']') }

    if (!isDnsHostDomain(domain)) return invalidMessage
    if (addresses.isEmpty() || addresses.any { it.isEmpty() || !isIpAddress(it) }) return invalidMessage
    return null
}

private fun isGeoipCode(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.length in 2..8 && trimmed.all { it.isLetter() || it.isDigit() || it == '-' }
}

private fun isDnsHostDomain(domain: String): Boolean {
    val normalized = domain.removeSuffix(".")
    if (normalized.isEmpty() || normalized.length > 253) return false
    if (normalized.any { it.isWhitespace() || it == '/' || it == DnsHostSeparator }) return false
    if (normalized == "*") return true

    return normalized.split(".").all { label ->
        label == "*" ||
            label.startsWith("+") ||
            (
                label.isNotEmpty() &&
                    label.length <= 63 &&
                    label.first() != '-' &&
                    label.last() != '-' &&
                    label.all { it.isLetterOrDigit() || it == '-' }
                )
    }
}
