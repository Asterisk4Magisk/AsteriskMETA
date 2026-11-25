// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.connections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.R
import features.monitoring.ConnectionMonitorStatus
import features.monitoring.MonitoringMenuGroupHeader
import features.monitoring.MonitoringPauseButton
import features.monitoring.MonitoringStatusHeader
import features.monitoring.MonitoringStatusMenu
import features.monitoring.dns.DnsQueryTypeFilters
import features.monitoring.dns.DnsRecord
import features.monitoring.dns.DnsRecordFilter
import features.monitoring.dns.DnsRoute
import features.monitoring.dns.DnsRouteFilters
import features.monitoring.dns.dnsRcodeLabel
import features.monitoring.dns.dnsStatusLabel
import java.text.SimpleDateFormat
import java.util.Date
import ui.components.AsteriskDropdownMenuItem
import ui.components.AsteriskFilterChip
import ui.components.AsteriskSelectionCard
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun DnsMonitorContent(
    status: ConnectionMonitorStatus,
    records: List<DnsRecord>,
    filteredRecords: List<DnsRecord>,
    paused: Boolean,
    onTogglePaused: () -> Unit,
    error: String,
    hasActiveFilter: Boolean,
    filter: DnsRecordFilter?,
    onFilterChange: (DnsRecordFilter?) -> Unit,
    selectedUpstream: String?,
    onUpstreamSelect: (String?) -> Unit,
    iconSizePx: Int,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val locale = LocalLocale.current.platformLocale
    val listTimeFormat = remember(locale) { SimpleDateFormat("HH:mm:ss", locale) }
    val detailTimeFormat = remember(locale) { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale) }
    var detailRecord by remember { mutableStateOf<DnsRecord?>(null) }
    val visibleRecords = remember(filteredRecords) { filteredRecords.asReversed() }
    val sessionActive = status != ConnectionMonitorStatus.ServiceStopped &&
        status != ConnectionMonitorStatus.Loading
    LaunchedEffect(sessionActive) {
        if (!sessionActive) detailRecord = null
    }

    MonitoringRecordList(
        state = listState,
        contentPadding = contentPadding,
        selection = detailRecord.takeIf { sessionActive },
        title = stringResource(R.string.monitor_dns_detail_title),
        selectionKey = DnsRecord::id,
        onDismiss = { detailRecord = null },
        details = { record ->
            val upstreamSelected = record.upstream == selectedUpstream
            DnsDetailContent(
                record = record,
                timeFormat = detailTimeFormat,
                upstreamSelected = upstreamSelected,
                onToggleUpstream = {
                    onUpstreamSelect(record.upstream.takeUnless { upstreamSelected })
                    detailRecord = null
                },
            )
        },
    ) {
        when (status) {
            ConnectionMonitorStatus.ServiceStopped -> {
                item("dns-stopped") { StatusCard(stringResource(R.string.monitor_service_not_enabled)) }
                return@MonitoringRecordList
            }
            ConnectionMonitorStatus.Loading -> {
                item("dns-loading") { StatusCard(stringResource(R.string.monitor_loading), loading = true) }
                return@MonitoringRecordList
            }
            ConnectionMonitorStatus.Error -> {
                item("dns-error") {
                    StatusCard(error.ifBlank { stringResource(R.string.monitor_data_unavailable) })
                }
                return@MonitoringRecordList
            }
            ConnectionMonitorStatus.Available -> Unit
        }
        item("dns-summary") {
            MonitoringStatusHeader(
                title = stringResource(R.string.monitor_dns_count),
                value = records.size.toString(),
                summary = null,
                controls = {
                    DnsFilterMenu(
                        filter = filter,
                        onFilterChange = onFilterChange,
                    )
                },
                statusRowOffset = 4.dp,
                titleAction = { actionModifier ->
                    MonitoringPauseButton(
                        paused = paused,
                        contentDescription = stringResource(
                            if (paused) R.string.monitor_dns_resume else R.string.monitor_dns_pause,
                        ),
                        onClick = onTogglePaused,
                        modifier = actionModifier,
                    )
                },
            )
        }
        if (selectedUpstream != null) {
            item("dns-upstream-filter") {
                ActiveUpstreamFilter(
                    upstream = selectedUpstream,
                    count = visibleRecords.size,
                    onClear = { onUpstreamSelect(null) },
                )
            }
        }
        when {
            visibleRecords.isEmpty() -> item("dns-empty") {
                StatusCard(
                    stringResource(
                        if (hasActiveFilter) R.string.monitor_dns_no_match
                        else R.string.monitor_dns_empty,
                    ),
                )
            }
            else -> items(
                visibleRecords,
                key = { "dns-${it.id}" },
            ) { record ->
                DnsRecordCard(
                    record = record,
                    timeFormat = listTimeFormat,
                    iconSizePx = iconSizePx,
                    selected = detailRecord?.id == record.id,
                    onDetail = { detailRecord = record.takeUnless { it.id == detailRecord?.id } },
                )
            }
        }
    }
}

/** The chip that names the upstream the list is narrowed to, with a way out. */
@Composable
private fun ActiveUpstreamFilter(
    upstream: String,
    count: Int,
    onClear: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        AsteriskFilterChip(
            selected = true,
            onClick = onClear,
            label = "${upstream} · $count",
            trailingIcon = {
                Icon(
                    Icons.Rounded.Clear,
                    contentDescription = stringResource(R.string.monitor_dns_filter_clear),
                )
            },
        )
    }
}

@Composable
private fun DnsDetailContent(
    record: DnsRecord,
    timeFormat: SimpleDateFormat,
    upstreamSelected: Boolean,
    onToggleUpstream: () -> Unit,
) {
    MonitoringDetailRow(
        label = stringResource(R.string.monitor_dns_detail_time),
        value = timeFormat.format(Date(record.timestampMillis)),
    )
    MonitoringDetailRow(
        label = stringResource(R.string.monitor_dns_detail_domain),
        value = record.domain,
    )
    MonitoringDetailRow(
        label = stringResource(R.string.monitor_dns_detail_type),
        value = record.recordType,
    )
    MonitoringDetailRow(
        label = stringResource(R.string.monitor_dns_detail_route),
        value = dnsRouteLabel(record.route) ?: DnsDetailUnavailable,
    )
    if (record.upstream.isNotBlank()) {
        MonitoringDetailRow(
            label = stringResource(R.string.monitor_dns_upstream),
            value = record.upstream,
            modifier = Modifier.clickable(
                onClickLabel = stringResource(
                    if (upstreamSelected) R.string.monitor_dns_filter_clear else R.string.monitor_dns_filter_upstream,
                ),
                role = Role.Button,
                onClick = onToggleUpstream,
            ),
            trailing = {
                Icon(
                    imageVector = if (upstreamSelected) Icons.Rounded.Clear else Icons.Rounded.FilterAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
    }
    MonitoringDetailRow(
        label = stringResource(R.string.monitor_dns_detail_source),
        value = dnsRequesterLabel(record),
    )
    MonitoringDetailRow(
        label = stringResource(R.string.monitor_dns_detail_answer_source),
        value = when (record.event.answerSource) {
            "cache" -> stringResource(R.string.monitor_dns_cached)
            "hosts" -> stringResource(R.string.monitor_dns_hosts)
            "fake-ip" -> stringResource(R.string.monitor_dns_fake_ip)
            "local" -> stringResource(R.string.monitor_dns_local)
            "upstream" -> stringResource(R.string.monitor_dns_upstream)
            else -> DnsDetailUnavailable
        },
    )
    MonitoringDetailRow(
        label = stringResource(R.string.monitor_dns_detail_status),
        value = dnsStatusLabel(record),
    )
    MonitoringDetailRow(
        label = stringResource(R.string.monitor_dns_detail_rcode),
        value = record.event.rcode?.let(::dnsRcodeLabel) ?: DnsDetailUnavailable,
    )
    MonitoringDetailRow(
        label = stringResource(R.string.monitor_dns_detail_delay),
        value = stringResource(R.string.monitor_dns_elapsed, record.elapsedMillis),
    )
    record.event.uid?.let { uid ->
        MonitoringDetailRow(stringResource(R.string.monitor_dns_detail_uid), uid.toString())
    }
    record.event.outboundTag?.let { tag ->
        MonitoringDetailRow(stringResource(R.string.monitor_dns_detail_outbound), tag)
    }
    MonitoringDetailRow(
        label = stringResource(R.string.monitor_dns_detail_answers),
        value = record.event.answers.joinToString("\n") { answer ->
            answer.data
        }.ifBlank { DnsDetailUnavailable },
    )
}

private const val DnsDetailUnavailable = "—"

@Composable
private fun DnsRecordCard(
    record: DnsRecord,
    timeFormat: SimpleDateFormat,
    iconSizePx: Int,
    selected: Boolean,
    onDetail: () -> Unit,
) {
    val failed = record.failed
    val answer = if (failed) {
        dnsResultLabel(record)
    } else {
        record.event.answers.joinToString(limit = 2) { it.data }.ifBlank { DnsDetailUnavailable }
    }
    val metadata = listOfNotNull(
        record.recordType,
        dnsRouteLabel(record.route),
        if (record.cached && !failed) stringResource(R.string.monitor_dns_cached) else null,
        timeFormat.format(Date(record.timestampMillis)),
    ).joinToString(" · ")
    val metadataStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val colors = MaterialTheme.colorScheme

    AsteriskSelectionCard(
        selected = selected,
        onClick = onDetail,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        containerColor = colors.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConnectionAppIcon(
                packageName = record.appPackage.takeIf(String::isNotBlank),
                label = dnsRequesterLabel(record),
                iconSizePx = iconSizePx,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = record.domain.ifBlank { DnsDetailUnavailable },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.monitor_dns_detail_title),
                        modifier = Modifier.size(20.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) colors.error else colors.onSurfaceVariant,
                    fontWeight = if (failed) FontWeight.SemiBold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = metadata,
                        modifier = Modifier.weight(1f),
                        style = metadataStyle,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.monitor_dns_elapsed, record.elapsedMillis),
                        style = metadataStyle,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun dnsRouteLabel(route: DnsRoute): String? = when (route) {
    DnsRoute.Proxy -> stringResource(R.string.monitor_filter_proxy)
    DnsRoute.Direct -> stringResource(R.string.monitor_filter_direct)
    DnsRoute.Unknown -> null
}

/** The label of a DNS filter, used by the filter menu. */
@Composable
private fun dnsFilterLabel(filter: DnsRecordFilter): String = when (filter) {
    DnsRecordFilter.A -> "A"
    DnsRecordFilter.AAAA -> "AAAA"
    DnsRecordFilter.HttpsOrSvcb -> stringResource(R.string.monitor_dns_filter_https)
    DnsRecordFilter.Proxy -> stringResource(R.string.monitor_filter_proxy)
    DnsRecordFilter.Direct -> stringResource(R.string.monitor_filter_direct)
}

@Composable
private fun dnsRequesterLabel(record: DnsRecord): String =
    record.app.ifBlank {
        record.event.uid?.let { stringResource(R.string.monitor_dns_uid, it) }
            ?: stringResource(R.string.monitor_connections_unknown_source)
    }

@Composable
private fun dnsResultLabel(record: DnsRecord): String =
    if (record.event.status == "answered") {
        record.event.rcode?.let(::dnsRcodeLabel) ?: DnsDetailUnavailable
    } else {
        dnsStatusLabel(record)
    }

@Composable
private fun DnsFilterMenu(
    filter: DnsRecordFilter?,
    onFilterChange: (DnsRecordFilter?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filterLabel = stringResource(R.string.monitor_dns_filter)
    MonitoringStatusMenu(
        label = if (filter == null) filterLabel else "$filterLabel · ${dnsFilterLabel(filter)}",
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        MonitoringMenuGroupHeader(stringResource(R.string.monitor_dns_filter_routes))
        DnsRouteFilters.forEach { option ->
            AsteriskDropdownMenuItem(
                text = dnsFilterLabel(option),
                selected = option == filter,
                onClick = {
                    onFilterChange(option.takeUnless { it == filter })
                    expanded = false
                },
            )
        }
        MonitoringMenuGroupHeader(stringResource(R.string.monitor_dns_filter_types))
        DnsQueryTypeFilters.forEach { option ->
            AsteriskDropdownMenuItem(
                text = dnsFilterLabel(option),
                selected = option == filter,
                onClick = {
                    onFilterChange(option.takeUnless { it == filter })
                    expanded = false
                },
            )
        }
    }
}
