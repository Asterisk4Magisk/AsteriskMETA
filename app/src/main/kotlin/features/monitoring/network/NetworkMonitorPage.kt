// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import features.monitoring.MonitoringIntent
import features.monitoring.MonitoringScaffold
import features.monitoring.MonitoringSectionCard
import features.monitoring.MonitoringValueRow
import features.monitoring.ObserveMonitoring
import kotlinx.coroutines.launch
import app.R
import ui.layout.rememberPageGutter
import java.util.UUID
import java.util.Locale
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun NetworkMonitorPage(padding: PaddingValues) {
    val horizontalPadding = rememberPageGutter()
    val services = LocalAppServices.current
    val monitoring by services.monitoring.state.collectAsState()
    val network = monitoring.network
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageSessionId = rememberSaveable { UUID.randomUUID().toString() }
    val copiedMessage = stringResource(R.string.monitor_copied)
    ObserveMonitoring(MonitoringIntent.Network, pageSessionId)

    fun copy(label: String, value: String) {
        if (value.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        scope.launch { services.tipNotifier.show(copiedMessage) }
    }

    MonitoringScaffold(
        title = stringResource(R.string.monitor_network_title),
        outerPadding = padding,
        actions = {
            IconButton(
                onClick = { services.monitoring.refreshNetworkStatus() },
                enabled = !network.publicProbe.refreshing,
            ) {
                if (network.publicProbe.refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.monitor_refresh))
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                top = contentPadding.calculateTopPadding() + 8.dp,
                end = horizontalPadding,
                bottom = contentPadding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item("public") {
                MonitoringSectionCard(stringResource(R.string.monitor_network_public), NetworkContentModifier) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.monitor_network_probe_source),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PublicAddressFamilyCard(
                            family = AddressFamily.Ipv4,
                            generalResult = network.publicProbe.ipv4,
                            cloudflareResult = network.publicProbe.cloudflareIpv4,
                            refreshing = network.publicProbe.refreshing,
                            onCopy = { label, value -> copy(label, value) },
                            onRetry = { services.monitoring.refreshPublicNetworkProbe(AddressFamily.Ipv4) },
                        )
                        PublicAddressFamilyCard(
                            family = AddressFamily.Ipv6,
                            generalResult = network.publicProbe.ipv6,
                            cloudflareResult = network.publicProbe.cloudflareIpv6,
                            refreshing = network.publicProbe.refreshing,
                            onCopy = { label, value -> copy(label, value) },
                            onRetry = { services.monitoring.refreshPublicNetworkProbe(AddressFamily.Ipv6) },
                        )
                    }
                }
            }
            item("local") {
                MonitoringSectionCard(stringResource(R.string.monitor_network_local), NetworkContentModifier) {
                    Column {
                        CopyableRows(
                            label = stringResource(R.string.monitor_network_local_ipv4),
                            values = network.local.ipv4Addresses,
                            onCopy = ::copy,
                        )
                        CopyableRows(
                            label = stringResource(R.string.monitor_network_local_ipv6),
                            values = network.local.ipv6Addresses,
                            onCopy = ::copy,
                        )
                        CopyableRows(
                            label = stringResource(R.string.monitor_network_gateway),
                            values = network.local.gateways,
                            onCopy = ::copy,
                        )
                        CopyableRows(
                            label = stringResource(R.string.monitor_network_dns),
                            values = network.local.dnsServers,
                            onCopy = ::copy,
                        )
                        CopyableRows(
                            label = stringResource(R.string.monitor_network_interface),
                            values = listOfNotNull(network.local.interfaceName.takeIf { it.isNotBlank() }),
                            onCopy = ::copy,
                        )
                    }
                }
            }
            item("privacy") {
                MonitoringSectionCard(stringResource(R.string.monitor_network_privacy), NetworkContentModifier) {
                    Text(
                        stringResource(
                            R.string.monitor_network_privacy_body,
                            DefaultPublicProbeEndpoints.joinToString { it.host },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicAddressFamilyCard(
    family: AddressFamily,
    generalResult: PublicAddressProbeResult,
    cloudflareResult: PublicAddressProbeResult,
    refreshing: Boolean,
    onCopy: (String, String) -> Unit,
    onRetry: () -> Unit,
) {
    val familyLabel = stringResource(
        if (family == AddressFamily.Ipv4) R.string.common_ipv4 else R.string.common_ipv6
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = familyLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (generalResult.hasRetryableError || cloudflareResult.hasRetryableError) {
                    TextButton(
                        onClick = onRetry,
                        enabled = !refreshing,
                    ) {
                        Text(stringResource(R.string.monitor_retry))
                    }
                }
            }

            PublicAddressSection(
                title = stringResource(R.string.monitor_network_probe_general),
                result = generalResult,
                family = family,
                onCopy = { onCopy(familyLabel, it) },
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 2.dp),
            )

            PublicAddressSection(
                title = stringResource(R.string.monitor_network_probe_cloudflare),
                result = cloudflareResult,
                family = family,
                onCopy = { onCopy(familyLabel, it) },
            )
        }
    }
}

private val PublicAddressProbeResult.hasRetryableError: Boolean
    get() = error != null && error != PublicProbeError.Unavailable

@Composable
private fun PublicAddressSection(
    title: String,
    result: PublicAddressProbeResult,
    family: AddressFamily,
    onCopy: (String) -> Unit,
) {
    val locationSummary = result.locationSummary(formatCloudflareColo(result.colo))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (result.error == PublicProbeError.Unavailable) {
                    Text(
                        text = stringResource(
                            if (family == AddressFamily.Ipv6) {
                                R.string.monitor_network_probe_unallocated_ipv6
                            } else {
                                R.string.monitor_network_probe_unallocated_ipv4
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = result.address.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (locationSummary.isNotBlank()) {
                    Text(
                        text = locationSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                result.durationMillis?.let { duration ->
                    Text(
                        stringResource(R.string.monitor_network_request_duration_value, duration),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (result.address.isNotBlank() && result.error != PublicProbeError.Unavailable) {
                IconButton(onClick = { onCopy(result.address) }) {
                    Icon(Icons.Rounded.ContentCopy, stringResource(R.string.monitor_copy_value))
                }
            }
        }
        if (result.error != null && result.error != PublicProbeError.Unavailable) {
            Text(
                result.errorMessage.ifBlank { publicProbeErrorLabel(result.error) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (result.stale) {
                Text(stringResource(R.string.monitor_data_stale), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CopyableRows(label: String, values: List<String>, onCopy: (String, String) -> Unit) {
    if (values.isEmpty()) {
        MonitoringValueRow(
            label = label,
            value = "—",
            verticalAlignment = Alignment.CenterVertically,
            trailing = { Spacer(Modifier.size(48.dp)) },
        )
    } else {
        values.forEach { value ->
            MonitoringValueRow(
                label = label,
                value = value,
                verticalAlignment = Alignment.CenterVertically,
                trailing = {
                    IconButton(onClick = { onCopy(label, value) }) {
                        Icon(Icons.Rounded.ContentCopy, stringResource(R.string.monitor_copy_value))
                    }
                },
            )
        }
    }
}

@Composable
private fun publicProbeErrorLabel(error: PublicProbeError): String = stringResource(
    when (error) {
        PublicProbeError.Timeout -> R.string.monitor_network_error_timeout
        PublicProbeError.Network -> R.string.monitor_network_error_request
        PublicProbeError.InvalidResponse -> R.string.monitor_network_error_response
        PublicProbeError.Unavailable -> R.string.monitor_network_error_unavailable
    },
)

private val NetworkContentModifier = Modifier.fillMaxWidth().widthIn(max = 840.dp)

@Composable
internal fun formatCloudflareColo(colo: String): String {
    val code = colo.trim().uppercase(Locale.ROOT)
    if (code.isEmpty()) return ""
    val cityResource = when (code) {
        "AMS" -> R.string.monitor_network_colo_ams
        "CDG" -> R.string.monitor_network_colo_cdg
        "DFW" -> R.string.monitor_network_colo_dfw
        "EWR" -> R.string.monitor_network_colo_ewr
        "FRA" -> R.string.monitor_network_colo_fra
        "HKG" -> R.string.monitor_network_colo_hkg
        "IAD" -> R.string.monitor_network_colo_iad
        "ICN" -> R.string.monitor_network_colo_icn
        "KHH" -> R.string.monitor_network_colo_khh
        "KIX" -> R.string.monitor_network_colo_kix
        "LAX" -> R.string.monitor_network_colo_lax
        "LHR" -> R.string.monitor_network_colo_lhr
        "MEL" -> R.string.monitor_network_colo_mel
        "NRT" -> R.string.monitor_network_colo_nrt
        "ORD" -> R.string.monitor_network_colo_ord
        "SEA" -> R.string.monitor_network_colo_sea
        "SFO" -> R.string.monitor_network_colo_sfo
        "SIN" -> R.string.monitor_network_colo_sin
        "SJC" -> R.string.monitor_network_colo_sjc
        "SYD" -> R.string.monitor_network_colo_syd
        "TPE" -> R.string.monitor_network_colo_tpe
        else -> null
    }
    return if (cityResource != null) {
        stringResource(R.string.monitor_network_colo_named, stringResource(cityResource), code)
    } else {
        stringResource(R.string.monitor_network_colo_unknown, code)
    }
}
