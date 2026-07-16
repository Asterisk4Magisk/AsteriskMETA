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
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import app.R
import features.monitoring.MonitoringIntent
import features.monitoring.MonitoringScaffold
import features.monitoring.MonitoringSectionCard
import features.monitoring.MonitoringValueRow
import features.monitoring.ObserveMonitoring
import kotlinx.coroutines.launch
import ui.layout.rememberPageGutter
import java.util.UUID

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
                        PublicAddressCard(
                            family = AddressFamily.Ipv4,
                            result = network.publicProbe.ipv4,
                            refreshing = network.publicProbe.refreshing,
                            onCopy = { value -> copy("Public IPv4", value) },
                            onRetry = { services.monitoring.refreshPublicNetworkProbe(AddressFamily.Ipv4) },
                        )
                        PublicAddressCard(
                            family = AddressFamily.Ipv6,
                            result = network.publicProbe.ipv6,
                            refreshing = network.publicProbe.refreshing,
                            onCopy = { value -> copy("Public IPv6", value) },
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
private fun PublicAddressCard(
    family: AddressFamily,
    result: PublicAddressProbeResult,
    refreshing: Boolean,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (family == AddressFamily.Ipv4) "IPv4" else "IPv6", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = result.address.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    result.durationMillis?.let { duration ->
                        Text(
                            stringResource(R.string.monitor_network_request_duration_value, duration),
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (result.address.isNotBlank()) {
                    IconButton(onClick = { onCopy(result.address) }) {
                        Icon(Icons.Rounded.ContentCopy, stringResource(R.string.monitor_copy_value))
                    }
                }
            }
            if (result.error != null) {
                Text(
                    result.errorMessage.ifBlank { publicProbeErrorLabel(result.error) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (result.stale) {
                    Text(stringResource(R.string.monitor_data_stale), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onRetry, enabled = !refreshing) {
                    Text(stringResource(R.string.monitor_retry))
                }
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
    },
)

private val NetworkContentModifier = Modifier.fillMaxWidth().widthIn(max = 840.dp)
