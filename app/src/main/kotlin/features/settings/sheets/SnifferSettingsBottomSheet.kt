// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.R
import ui.icons.AsteriskIcons as Icons
import engine.mihomo.DefaultMihomoSnifferHttpPorts
import engine.mihomo.DefaultMihomoSnifferQuicPorts
import engine.mihomo.DefaultMihomoSnifferTlsPorts
import engine.mihomo.MihomoSnifferProtocolOverrideDisabled
import engine.mihomo.MihomoSnifferProtocolOverrideEnabled
import engine.mihomo.MihomoSnifferProtocolOverrideFollowGlobal
import engine.mihomo.isSnifferDomainRule
import engine.mihomo.isSnifferPortOrRange
import engine.mihomo.sanitizedSnifferCidrs
import engine.mihomo.sanitizedSnifferDomainRules
import engine.mihomo.sanitizedSnifferPorts
import engine.network.isCidrAddress
import features.settings.SnifferSettingsDraft
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import ui.components.StringListEditor
import utils.toTrimmedNonEmptyDistinctList

@Composable
internal fun snifferSettingsSummary(
    enableSniffer: Boolean,
    snifferHttpPorts: List<String>,
    snifferTlsPorts: List<String>,
    snifferQuicPorts: List<String>,
): String {
    if (!enableSniffer) {
        return stringResource(R.string.settings_sniffer_summary_disabled)
    }

    val separator = stringResource(R.string.settings_sniffer_summary_option_separator)
    val enabledProtocols = listOfNotNull(
        "HTTP".takeIf { snifferHttpPorts.hasEnabledSnifferPorts() },
        "TLS".takeIf { snifferTlsPorts.hasEnabledSnifferPorts() },
        "QUIC".takeIf { snifferQuicPorts.hasEnabledSnifferPorts() },
    )
    if (enabledProtocols.isEmpty()) {
        return stringResource(R.string.settings_sniffer_summary_enabled)
    }

    return stringResource(
        R.string.settings_sniffer_summary_enabled_with_options,
        enabledProtocols.joinToString(separator = separator),
    )
}

private fun List<String>.hasEnabledSnifferPorts(): Boolean {
    return toTrimmedNonEmptyDistinctList().any(::isSnifferPortOrRange)
}

@Composable
internal fun SnifferSettingsBottomSheet(
    show: Boolean,
    draft: SnifferSettingsDraft,
    onDraftChange: (SnifferSettingsDraft) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (SnifferSettingsDraft) -> Unit,
) {
    val portInvalidMessage = stringResource(R.string.settings_sniffer_port_invalid)
    val domainInvalidMessage = stringResource(R.string.settings_sniffer_domain_invalid)
    val cidrInvalidMessage = stringResource(R.string.settings_sniffer_cidr_invalid)
    val overrideOptions = snifferProtocolOverrideOptions()
    val sanitizedDraft = draft.sanitized()

    SettingsModalBottomSheet(
        show = show,
        title = stringResource(R.string.settings_sniffer),
        startAction = {
            TextButton(
                text = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
        endAction = {
            TextButton(
                text = stringResource(R.string.common_save),
                icon = Icons.Rounded.Save,
                onClick = { onSave(sanitizedDraft) },
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        key(show) {
            SettingsSheetContent {
                SnifferSheetSection(title = stringResource(R.string.settings_sniffer_section_basic)) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_sniffer_enable),
                        icon = Icons.Rounded.TravelExplore,
                        summary = stringResource(R.string.settings_sniffer_summary),
                        checked = draft.enableSniffer,
                        onCheckedChange = { onDraftChange(draft.copy(enableSniffer = it)) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_sniffer_override_destination),
                        icon = Icons.AutoMirrored.Rounded.AltRoute,
                        summary = stringResource(R.string.settings_sniffer_override_destination_summary),
                        checked = draft.enableSnifferOverrideDestination,
                        onCheckedChange = {
                            onDraftChange(draft.copy(enableSnifferOverrideDestination = it))
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_sniffer_force_dns_mapping),
                        icon = Icons.Rounded.Dns,
                        summary = stringResource(R.string.settings_sniffer_force_dns_mapping_summary),
                        checked = draft.snifferForceDnsMapping,
                        onCheckedChange = { onDraftChange(draft.copy(snifferForceDnsMapping = it)) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_sniffer_parse_pure_ip),
                        icon = Icons.Rounded.Public,
                        summary = stringResource(R.string.settings_sniffer_parse_pure_ip_summary),
                        checked = draft.snifferParsePureIp,
                        onCheckedChange = { onDraftChange(draft.copy(snifferParsePureIp = it)) },
                    )
                }

                SnifferSheetSection(title = stringResource(R.string.settings_sniffer_section_protocols)) {
                    SnifferProtocolFields(
                        show = show,
                        editorKey = "sniffer-http-ports",
                        portsTitle = stringResource(R.string.settings_sniffer_http_ports),
                        ports = draft.snifferHttpPorts,
                        onPortsChange = { onDraftChange(draft.copy(snifferHttpPorts = it)) },
                        overrideTitle = stringResource(R.string.settings_sniffer_http_override_destination),
                        overrideIcon = Icons.Rounded.Http,
                        overrideMode = draft.snifferHttpOverrideDestinationMode,
                        overrideOptions = overrideOptions,
                        onOverrideModeChange = {
                            onDraftChange(draft.copy(snifferHttpOverrideDestinationMode = it))
                        },
                        portInvalidMessage = portInvalidMessage,
                    )
                    Spacer(Modifier.height(8.dp))
                    SnifferProtocolFields(
                        show = show,
                        editorKey = "sniffer-tls-ports",
                        portsTitle = stringResource(R.string.settings_sniffer_tls_ports),
                        ports = draft.snifferTlsPorts,
                        onPortsChange = { onDraftChange(draft.copy(snifferTlsPorts = it)) },
                        overrideTitle = stringResource(R.string.settings_sniffer_tls_override_destination),
                        overrideIcon = Icons.Rounded.Lock,
                        overrideMode = draft.snifferTlsOverrideDestinationMode,
                        overrideOptions = overrideOptions,
                        onOverrideModeChange = {
                            onDraftChange(draft.copy(snifferTlsOverrideDestinationMode = it))
                        },
                        portInvalidMessage = portInvalidMessage,
                    )
                    Spacer(Modifier.height(8.dp))
                    SnifferProtocolFields(
                        show = show,
                        editorKey = "sniffer-quic-ports",
                        portsTitle = stringResource(R.string.settings_sniffer_quic_ports),
                        ports = draft.snifferQuicPorts,
                        onPortsChange = { onDraftChange(draft.copy(snifferQuicPorts = it)) },
                        overrideTitle = stringResource(R.string.settings_sniffer_quic_override_destination),
                        overrideIcon = Icons.Rounded.Speed,
                        overrideMode = draft.snifferQuicOverrideDestinationMode,
                        overrideOptions = overrideOptions,
                        onOverrideModeChange = {
                            onDraftChange(draft.copy(snifferQuicOverrideDestinationMode = it))
                        },
                        portInvalidMessage = portInvalidMessage,
                    )
                }

                SnifferSheetSection(title = stringResource(R.string.settings_sniffer_section_domain_rules)) {
                    StringListEditor(
                        editorKey = "sniffer-force-domain:$show",
                        title = stringResource(R.string.settings_sniffer_force_domain),
                        values = draft.snifferForceDomain.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = {
                            onDraftChange(draft.copy(snifferForceDomain = it.toTrimmedNonEmptyDistinctList()))
                        },
                        emptyText = stringResource(R.string.settings_sniffer_list_empty),
                        validateInput = { if (isSnifferDomainRule(it)) null else domainInvalidMessage },
                    )
                    Spacer(Modifier.height(8.dp))
                    StringListEditor(
                        editorKey = "sniffer-skip-domain:$show",
                        title = stringResource(R.string.settings_sniffer_skip_domain),
                        values = draft.snifferSkipDomain.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = {
                            onDraftChange(draft.copy(snifferSkipDomain = it.toTrimmedNonEmptyDistinctList()))
                        },
                        emptyText = stringResource(R.string.settings_sniffer_list_empty),
                        validateInput = { if (isSnifferDomainRule(it)) null else domainInvalidMessage },
                    )
                }

                SnifferSheetSection(title = stringResource(R.string.settings_sniffer_section_address_skips)) {
                    StringListEditor(
                        editorKey = "sniffer-skip-src-address:$show",
                        title = stringResource(R.string.settings_sniffer_skip_src_address),
                        values = draft.snifferSkipSrcAddress.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = {
                            onDraftChange(draft.copy(snifferSkipSrcAddress = it.toTrimmedNonEmptyDistinctList()))
                        },
                        emptyText = stringResource(R.string.settings_sniffer_list_empty),
                        validateInput = { if (isCidrAddress(it)) null else cidrInvalidMessage },
                    )
                    Spacer(Modifier.height(8.dp))
                    StringListEditor(
                        editorKey = "sniffer-skip-dst-address:$show",
                        title = stringResource(R.string.settings_sniffer_skip_dst_address),
                        values = draft.snifferSkipDstAddress.toTrimmedNonEmptyDistinctList(),
                        onValuesChange = {
                            onDraftChange(draft.copy(snifferSkipDstAddress = it.toTrimmedNonEmptyDistinctList()))
                        },
                        emptyText = stringResource(R.string.settings_sniffer_list_empty),
                        validateInput = { if (isCidrAddress(it)) null else cidrInvalidMessage },
                    )
                }
            }
        }
    }
}

@Composable
private fun snifferProtocolOverrideOptions(): List<String> {
    return listOf(
        stringResource(R.string.settings_sniffer_protocol_override_follow_global),
        stringResource(R.string.settings_sniffer_protocol_override_enabled),
        stringResource(R.string.settings_sniffer_protocol_override_disabled),
    )
}

@Composable
private fun SnifferSheetSection(
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
            color = MaterialTheme.colorScheme.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
    }
}

@Composable
private fun SnifferProtocolFields(
    show: Boolean,
    editorKey: String,
    portsTitle: String,
    ports: List<String>,
    onPortsChange: (List<String>) -> Unit,
    overrideTitle: String,
    overrideIcon: ImageVector,
    overrideMode: Int,
    overrideOptions: List<String>,
    onOverrideModeChange: (Int) -> Unit,
    portInvalidMessage: String,
) {
    WindowDropdownPreference(
        title = overrideTitle,
        icon = overrideIcon,
        items = overrideOptions,
        selectedIndex = overrideMode.coerceSnifferOverrideMode(),
        onSelectedIndexChange = onOverrideModeChange,
    )
    StringListEditor(
        editorKey = "$editorKey:$show",
        title = portsTitle,
        values = ports.toTrimmedNonEmptyDistinctList(),
        onValuesChange = { onPortsChange(it.toTrimmedNonEmptyDistinctList()) },
        emptyText = stringResource(R.string.settings_sniffer_list_empty),
        validateInput = { if (isSnifferPortOrRange(it)) null else portInvalidMessage },
    )
}

internal fun SnifferSettingsDraft.sanitized(): SnifferSettingsDraft {
    return copy(
        snifferHttpPorts = snifferHttpPorts.sanitizedSnifferPorts(DefaultMihomoSnifferHttpPorts),
        snifferTlsPorts = snifferTlsPorts.sanitizedSnifferPorts(DefaultMihomoSnifferTlsPorts),
        snifferQuicPorts = snifferQuicPorts.sanitizedSnifferPorts(DefaultMihomoSnifferQuicPorts),
        snifferHttpOverrideDestinationMode = snifferHttpOverrideDestinationMode.coerceSnifferOverrideMode(),
        snifferTlsOverrideDestinationMode = snifferTlsOverrideDestinationMode.coerceSnifferOverrideMode(),
        snifferQuicOverrideDestinationMode = snifferQuicOverrideDestinationMode.coerceSnifferOverrideMode(),
        snifferForceDomain = snifferForceDomain.sanitizedSnifferDomainRules(),
        snifferSkipDomain = snifferSkipDomain.sanitizedSnifferDomainRules(),
        snifferSkipSrcAddress = snifferSkipSrcAddress.sanitizedSnifferCidrs(),
        snifferSkipDstAddress = snifferSkipDstAddress.sanitizedSnifferCidrs(),
    )
}

private fun Int.coerceSnifferOverrideMode(): Int {
    return when (this) {
        MihomoSnifferProtocolOverrideEnabled,
        MihomoSnifferProtocolOverrideDisabled,
        -> this
        else -> MihomoSnifferProtocolOverrideFollowGlobal
    }
}
