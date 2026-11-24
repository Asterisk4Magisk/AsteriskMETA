// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.R
import engine.mihomo.runtime.MihomoDnsRecord
import engine.mihomo.runtime.MihomoDnsRoute
import features.monitoring.ConnectionMonitorStatus
import features.monitoring.dns.DnsRecordFilter
import features.monitoring.dns.SystemDnsMarker
import ui.components.AsteriskCancelButton
import ui.components.AsteriskChipTone
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskFilterChip
import ui.components.AsteriskInfoChip
import ui.components.AsteriskModalBottomSheet
import ui.components.AsteriskStatusCard
import ui.icons.AsteriskIcons as Icons
import ui.layout.LocalAdaptiveWindow
import ui.layout.pageListPadding
import ui.layout.pageReadableWidth
import system.ANDROID_APP_ICON_SIZE_DP
import ui.layout.rememberPageGutter
import ui.theme.AsteriskShapeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val DnsMonitorPageMaxWidth = 1008.dp
private val DnsCardSpacing = 12.dp

/**
 * The DNS records the core answered while the monitor was open.
 *
 * Each card shows the queried type, the name, the addresses the core returned and the
 * upstream it asked, which is what the DNS panel of the reference screenshots shows.
 * The upstream on a card is tappable, and tapping it narrows the list to the lookups
 * that went through that upstream.
 */
@Composable
internal fun DnsMonitorContent(
    status: ConnectionMonitorStatus,
    records: List<MihomoDnsRecord>,
    filteredRecords: List<MihomoDnsRecord>,
    error: String,
    searching: Boolean,
    selectedUpstream: String?,
    onUpstreamSelect: (String?) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val gutter = rememberPageGutter()
    val timeStyle = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val iconSizePx = with(LocalDensity.current) { ANDROID_APP_ICON_SIZE_DP.dp.roundToPx() }
    var detailRecord by remember { mutableStateOf<MihomoDnsRecord?>(null) }
    val visibleRecords = remember(filteredRecords, selectedUpstream) {
        val narrowed = if (selectedUpstream == null) {
            filteredRecords
        } else {
            filteredRecords.filter { it.upstream == selectedUpstream }
        }
        // The newest lookup sits on top, so the list reads like a live feed.
        narrowed.asReversed()
    }

    BoxWithConstraints(Modifier.pageReadableWidth(DnsMonitorPageMaxWidth).fillMaxSize()) {
        val twoColumns = LocalAdaptiveWindow.current.showTwoColumns &&
            maxWidth - gutter * 2 >= 720.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = pageListPadding(contentPadding, bottomExtra = 20.dp, horizontalExtra = gutter),
            verticalArrangement = Arrangement.spacedBy(DnsCardSpacing),
        ) {
            when (status) {
                ConnectionMonitorStatus.ServiceStopped -> {
                    item("dns-stopped") { StatusCard(stringResource(R.string.monitor_service_not_enabled)) }
                    return@LazyColumn
                }
                ConnectionMonitorStatus.Loading -> {
                    item("dns-loading") { StatusCard(stringResource(R.string.monitor_loading), loading = true) }
                    return@LazyColumn
                }
                ConnectionMonitorStatus.Error -> {
                    item("dns-error") {
                        StatusCard(error.ifBlank { stringResource(R.string.monitor_data_unavailable) })
                    }
                    return@LazyColumn
                }
                ConnectionMonitorStatus.Available -> Unit
            }
            item("dns-summary") { DnsMonitorStatus(records.size) }
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
                            if (searching || selectedUpstream != null) R.string.monitor_dns_no_match
                            else R.string.monitor_dns_empty,
                        ),
                    )
                }
                twoColumns -> items(
                    count = (visibleRecords.size + 1) / 2,
                    key = { row -> "dns-${visibleRecords[row * 2].id}" },
                ) { row ->
                    val first = row * 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DnsCardSpacing),
                    ) {
                        DnsRecordCard(
                            visibleRecords[first],
                            timeStyle,
                            selectedUpstream,
                            onUpstreamSelect,
                            iconSizePx,
                            modifier = Modifier.weight(1f),
                            onDetail = { detailRecord = visibleRecords[first] },
                        )
                        val next = visibleRecords.getOrNull(first + 1)
                        if (next == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            DnsRecordCard(
                                next,
                                timeStyle,
                                selectedUpstream,
                                onUpstreamSelect,
                                iconSizePx,
                                modifier = Modifier.weight(1f),
                                onDetail = { detailRecord = next },
                            )
                        }
                    }
                }
                else -> items(
                    visibleRecords,
                    key = { "dns-${it.id}" },
                ) { record ->
                    DnsRecordCard(
                        record,
                        timeStyle,
                        selectedUpstream,
                        onUpstreamSelect,
                        iconSizePx,
                        modifier = Modifier.fillMaxWidth(),
                        onDetail = { detailRecord = record },
                    )
                }
            }
        }
        val detail = detailRecord
        AsteriskModalBottomSheet(
            show = detail != null,
            onDismissRequest = { detailRecord = null },
            title = stringResource(R.string.monitor_dns_detail_title),
            startAction = { AsteriskCancelButton(onClick = { detailRecord = null }) },
        ) {
            if (detail != null) {
                DnsDetailContent(detail, timeStyle)
            }
        }
    }
}

@Composable
private fun DnsMonitorStatus(total: Int) {
    AsteriskStatusCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DnsCardSpacing),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.monitor_dns_total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = total.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            label = "${dnsUpstreamDisplay(upstream)} · $count",
            trailingIcon = {
                Icon(
                    Icons.Rounded.Clear,
                    contentDescription = stringResource(R.string.monitor_dns_filter_clear),
                )
            },
        )
    }
}

/**
 * The upstream as the card shows it.
 *
 * The system resolver logs every system server it asked inside its marker, which is
 * noise on a card, so it reads as the system resolver instead.
 */
@Composable
internal fun dnsUpstreamDisplay(upstream: String): String =
    if (upstream.startsWith(SystemDnsMarker)) stringResource(R.string.monitor_dns_system)
    else upstream

/** The detail sheet of one DNS lookup, which mirrors the reference screenshots. */
@Composable
private fun DnsDetailContent(
    record: MihomoDnsRecord,
    timeStyle: SimpleDateFormat,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.monitor_dns_detail_basic),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        DnsDetailRow(
            label = stringResource(R.string.monitor_dns_detail_time),
            value = timeStyle.format(Date(record.timestampMillis)),
        )
        DnsDetailRow(
            label = stringResource(R.string.monitor_dns_detail_domain),
            value = record.domain,
        )
        DnsDetailRow(
            label = stringResource(R.string.monitor_dns_detail_type),
            value = record.recordType,
        )
        DnsDetailRow(
            label = stringResource(R.string.monitor_dns_detail_initiator),
            value = when (record.route) {
                MihomoDnsRoute.Proxy -> stringResource(R.string.monitor_dns_route_proxy)
                MihomoDnsRoute.Direct -> stringResource(R.string.monitor_dns_route_direct)
                MihomoDnsRoute.Unknown -> DnsDetailUnavailable
            },
        )
        DnsDetailRow(
            label = stringResource(R.string.monitor_dns_detail_source),
            value = record.app.ifBlank { DnsDetailUnavailable },
        )
        DnsDetailRow(
            label = stringResource(R.string.monitor_dns_detail_cached),
            value = stringResource(
                if (record.cached) R.string.monitor_dns_yes else R.string.monitor_dns_no,
            ),
        )
        DnsDetailRow(
            label = stringResource(R.string.monitor_dns_detail_rcode),
            value = if (record.failed) DnsDetailRcodeNameError else DnsDetailRcodeSuccess,
        )
        DnsDetailRow(
            label = stringResource(R.string.monitor_dns_detail_delay),
            value = record.elapsedMillis
                ?.let { stringResource(R.string.monitor_dns_elapsed, it) }
                ?: DnsDetailUnavailable,
        )
        Spacer(Modifier.height(DnsCardSpacing))
        Text(
            text = stringResource(R.string.monitor_dns_detail_answers),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        val answers = record.addresses.ifEmpty { listOf(DnsDetailUnavailable) }
        answers.forEach { answer ->
            Surface(
                shape = AsteriskShapeTokens.SmallContainer,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/** One label and value line of the detail sheet, with a divider below. */
@Composable
private fun DnsDetailRow(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private const val DnsDetailUnavailable = "—"
private const val DnsDetailRcodeSuccess = "NOERROR"
private const val DnsDetailRcodeNameError = "NXDOMAIN"

@Composable
private fun DnsRecordCard(
    record: MihomoDnsRecord,
    timeStyle: SimpleDateFormat,
    selectedUpstream: String?,
    onUpstreamSelect: (String?) -> Unit,
    iconSizePx: Int,
    modifier: Modifier = Modifier,
    onDetail: () -> Unit = {},
) {
    AsteriskExpressiveCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onDetail,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The app the lookup was made for, which reads like the app groups above.
            ConnectionAppIcon(
                packageName = record.appPackage.takeIf(String::isNotBlank),
                label = record.app.ifBlank { stringResource(R.string.monitor_connections_unknown_source) },
                iconSizePx = iconSizePx,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = timeStyle.format(Date(record.timestampMillis)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AsteriskInfoChip(text = record.recordType)
                    if (record.cached) {
                        AsteriskInfoChip(text = stringResource(R.string.monitor_dns_cached))
                    }
                    if (record.failed) {
                        AsteriskInfoChip(
                            text = stringResource(R.string.monitor_dns_failed),
                            tone = AsteriskChipTone.Error,
                        )
                    }
                }
                Text(
                    text = record.domain,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (record.addresses.isNotEmpty()) {
                    Text(
                        text = record.addresses.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (record.upstream.isNotBlank() || record.route != MihomoDnsRoute.Unknown) {
                    // The route sits next to the upstream, because together they say where
                    // the lookup went: the server and the way the core reached it.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (record.upstream.isNotBlank()) {
                            // The upstream answers through one block: tapping it narrows the
                            // list to the lookups that went through this server, and tapping
                            // it again brings the whole list back.
                            val selected = record.upstream == selectedUpstream
                            Surface(
                                onClick = { onUpstreamSelect(if (selected) null else record.upstream) },
                                shape = AsteriskShapeTokens.SmallContainer,
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = dnsUpstreamDisplay(record.upstream),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (selected) {
                                        Icon(
                                            Icons.Rounded.Clear,
                                            contentDescription = stringResource(R.string.monitor_dns_filter_clear),
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        when (record.route) {
                            MihomoDnsRoute.Proxy -> AsteriskInfoChip(
                                text = stringResource(R.string.monitor_dns_route_proxy),
                                tone = AsteriskChipTone.Secondary,
                            )
                            MihomoDnsRoute.Direct -> AsteriskInfoChip(
                                text = stringResource(R.string.monitor_dns_route_direct),
                            )
                            MihomoDnsRoute.Unknown -> Unit
                        }
                    }
                }
                record.elapsedMillis?.let { elapsed ->
                    Text(
                        text = stringResource(R.string.monitor_dns_elapsed, elapsed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The label of a DNS filter, used by the filter menu. */
@Composable
internal fun dnsFilterLabel(filter: DnsRecordFilter): String = when (filter) {
    DnsRecordFilter.A -> "A"
    DnsRecordFilter.AAAA -> "AAAA"
    DnsRecordFilter.Https -> stringResource(R.string.monitor_dns_filter_https)
    DnsRecordFilter.Proxy -> stringResource(R.string.monitor_dns_route_proxy)
    DnsRecordFilter.Direct -> stringResource(R.string.monitor_dns_route_direct)
}
