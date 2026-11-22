// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.network

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalAppStateStore
import app.collectAppState
import engine.mihomo.runtime.MihomoProxyNodeId
import engine.mihomo.runtime.delayTestLeaf
import features.monitoring.MonitoringIntent
import features.monitoring.MonitoringScaffold
import features.monitoring.MonitoringSectionCard
import features.monitoring.MonitoringValueRow
import features.monitoring.ObserveMonitoring
import kotlinx.coroutines.launch
import app.R
import ui.layout.LocalAdaptiveWindow
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
    val appState by LocalAppStateStore.current.collectAppState()
    val runtime by services.mihomoRuntime.state.collectAsState()
    val rootGeneration by services.proxyEngine.rootStatusWatchGeneration.collectAsState()
    val groups = remember(runtime.proxies.groups) {
        val groupTypes = setOf("Selector", "URLTest", "Fallback", "LoadBalance", "Relay")
        runtime.proxies.groups.filter { it.type in groupTypes }.map { ProxyIpProbeTarget(it.name) }
    }
    val serviceEpoch = runtime.runtimeEpoch
    val sessionKey = listOf(
        appState.runMode, appState.proxyRunning, runtime.running, runtime.control, rootGeneration, serviceEpoch,
    )
    var bundledCore by remember(sessionKey) { mutableStateOf(false) }
    LaunchedEffect(sessionKey) {
        bundledCore = appState.proxyRunning && runtime.running && services.mihomoRuntime.usesBundledCore(appState.runMode)
    }
    val showGroupPicker = bundledCore && groups.isNotEmpty()
    var selectedGroup by remember(sessionKey) { mutableStateOf<String?>(null) }
    val target = if (showGroupPicker) groups.firstOrNull { it.tag == selectedGroup } else null
    val selectedOutbound = runtime.proxies.groups.firstOrNull { it.name == target?.tag }?.now
    val selectedNode = target?.let { runtime.proxies.delayTestLeaf(MihomoProxyNodeId(it.tag)) }
    val strategyProbe = remember(sessionKey, target?.tag, selectedOutbound, selectedNode, runtime.proxySelectionRevision) {
        ProxyIpProbeState()
    }
    DisposableEffect(strategyProbe) {
        onDispose { strategyProbe.cancel() }
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val pageSessionId = rememberSaveable { UUID.randomUUID().toString() }
    val copiedMessage = stringResource(R.string.common_copied)
    ObserveMonitoring(MonitoringIntent.Network, pageSessionId)

    fun refreshStrategy(family: AddressFamily? = null) {
        val tag = target?.tag ?: return
        strategyProbe.startEntry(scope, family) {
            val node = selectedNode ?: return@startEntry emptyList()
            val hosts = services.mihomoRuntime.proxyEntryHosts(appState, node)
            services.monitoring.probeProxyEntry(hosts, family)
        }
        strategyProbe.start(scope, tag, family) { request -> services.mihomoRuntime.probeProxyIp(appState, request) }
    }

    LaunchedEffect(strategyProbe) {
        refreshStrategy()
    }

    fun copy(label: String, value: String) {
        if (value.isBlank()) return
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
            services.tipNotifier.show(copiedMessage)
        }
    }

    val refreshing = if (target == null) network.publicProbe.refreshing else strategyProbe.refreshing
    MonitoringScaffold(
        title = stringResource(R.string.monitor_network_title),
        outerPadding = padding,
        actions = {
            IconButton(
                onClick = {
                    if (target == null) services.monitoring.refreshNetworkStatus() else refreshStrategy()
                },
                enabled = !refreshing,
            ) {
                if (refreshing) {
                    NetworkRefreshIndicator()
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
                        if (showGroupPicker) {
                            ProxyIpProbeTargetPicker(groups, target) { next ->
                                if (next?.tag != target?.tag) {
                                    strategyProbe.cancel()
                                    selectedGroup = next?.tag
                                    if (next == null) services.monitoring.refreshPublicNetworkProbe()
                                }
                            }
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val sideBySide = LocalAdaptiveWindow.current.supportsWideContent &&
                                target == null && maxWidth >= 620.dp * LocalDensity.current.fontScale
                            PublicAddressFamilyLayout(sideBySide) { family, cardModifier ->
                                val isIpv4 = family == AddressFamily.Ipv4
                                val general = target?.let { strategyProbe.measurement(family, ProbeTarget.General) }
                                val cloudflare = target?.let { strategyProbe.measurement(family, ProbeTarget.Cloudflare) }
                                val entryRefreshing = family in strategyProbe.entryRefreshingFamilies
                                val familyRefreshing = if (target == null) {
                                    family in network.publicProbe.refreshingFamilies
                                } else {
                                    entryRefreshing || general?.refreshing == true || cloudflare?.refreshing == true
                                }
                                key(strategyProbe, family) {
                                    Box(cardModifier) {
                                        PublicAddressFamilyCard(
                                            family = family,
                                            results = AddressFamilyProbeResults(
                                                general = general?.displayResult()
                                                    ?: if (isIpv4) network.publicProbe.ipv4 else network.publicProbe.ipv6,
                                                cloudflare = cloudflare?.displayResult()
                                                    ?: if (isIpv4) network.publicProbe.cloudflareIpv4 else network.publicProbe.cloudflareIpv6,
                                                entries = target?.let { strategyProbe.entry(family).orEmpty() },
                                            ),
                                            entryRefreshing = entryRefreshing,
                                            refreshing = familyRefreshing,
                                            retryEnabled = if (target == null) !network.publicProbe.refreshing
                                                else strategyProbe.entryRefreshingFamilies.isEmpty(),
                                            onCopy = ::copy,
                                            onRetry = {
                                                if (target == null) services.monitoring.refreshPublicNetworkProbe(family)
                                                else refreshStrategy(family)
                                            },
                                        )
                                    }
                                }
                            }
                        }
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
private fun PublicAddressFamilyLayout(
    sideBySide: Boolean,
    content: @Composable (AddressFamily, Modifier) -> Unit,
) {
    // A proxy target card uses BoxWithConstraints. Use layouts that measure
    // it directly instead of asking for the intrinsic size of a weighted child.
    if (sideBySide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AddressFamily.entries.forEach { family -> content(family, Modifier.weight(1f)) }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AddressFamily.entries.forEach { family -> content(family, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun NetworkRefreshIndicator() {
    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
}

@Composable
private fun ProxyIpProbeState.Measurement.displayResult(): PublicAddressProbeResult = result.copy(
    errorMessage = message?.let { stringResource(it) }.orEmpty(),
)

private data class AddressFamilyProbeResults(
    val general: PublicAddressProbeResult,
    val cloudflare: PublicAddressProbeResult,
    val entries: List<PublicAddressProbeResult>?,
) {
    val hasRetryableError: Boolean
        get() = general.hasRetryableError || cloudflare.hasRetryableError ||
            entries?.any { it.hasRetryableError } == true
}

@Composable
private fun PublicAddressFamilyCard(
    family: AddressFamily,
    results: AddressFamilyProbeResults,
    entryRefreshing: Boolean,
    refreshing: Boolean,
    retryEnabled: Boolean,
    onCopy: (String, String) -> Unit,
    onRetry: () -> Unit,
) {
    val familyLabel = stringResource(
        if (family == AddressFamily.Ipv4) R.string.common_ipv4 else R.string.common_ipv6
    )
    // Keep the full card steady until all requests for this family finish.
    var settledResults by remember { mutableStateOf(results) }
    if (!refreshing) SideEffect { settledResults = results }
    val retrying = refreshing && settledResults.hasRetryableError
    val displayed = if (retrying) settledResults else results
    val (generalResult, cloudflareResult, entryResults) = displayed
    val waitingForEntry = entryRefreshing && !retrying
    Card(
        // Nested inside MonitoringSectionCard, so it stays one step below it.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = familyLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (displayed.hasRetryableError) {
                    // Keep the retry button's touch target without changing the title row height.
                    Box(
                        modifier = Modifier.matchParentSize()
                            .wrapContentSize(Alignment.CenterEnd, unbounded = true),
                    ) {
                        if (retrying) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                NetworkRefreshIndicator()
                            }
                        } else {
                            TextButton(onClick = onRetry, enabled = retryEnabled && !refreshing) {
                                Text(stringResource(R.string.common_retry))
                            }
                        }
                    }
                }
            }

            if (entryResults == null) {
                PublicAddressSection(
                    title = stringResource(R.string.monitor_network_probe_general),
                    result = generalResult,
                    family = family,
                    onCopy = { onCopy(familyLabel, it) },
                )
            } else {
                val entries = entryResults.ifEmpty {
                    listOfNotNull(generalResult.takeIf { !waitingForEntry && it.address.isNotBlank() })
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val sideBySide = !LocalAdaptiveWindow.current.supportsWideContent ||
                        maxWidth >= 540.dp * LocalDensity.current.fontScale
                    FlowRow(
                        maxItemsInEachRow = if (sideBySide) 2 else 1,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ProbeSectionTitle(stringResource(R.string.monitor_network_probe_entry))
                            if (entries.isEmpty()) {
                                Text(
                                    text = if (waitingForEntry) "—" else stringResource(R.string.monitor_network_probe_entry_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            entries.forEach { entry ->
                                PublicAddressValues(entry, family) { onCopy(familyLabel, it) }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ProbeSectionTitle(stringResource(R.string.monitor_network_probe_exit))
                            PublicAddressValues(generalResult, family) { onCopy(familyLabel, it) }
                        }
                    }
                }
            }

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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProbeSectionTitle(title)
        PublicAddressValues(result, family, onCopy)
    }
}

@Composable
private fun ProbeSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PublicAddressValues(
    result: PublicAddressProbeResult,
    family: AddressFamily,
    onCopy: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (result.error == PublicProbeError.Unavailable || result.address.isBlank()) {
            val unavailable = if (family == AddressFamily.Ipv6) {
                R.string.monitor_network_probe_unallocated_ipv6
            } else {
                R.string.monitor_network_probe_unallocated_ipv4
            }
            Text(
                text = if (result.error == PublicProbeError.Unavailable) stringResource(unavailable) else "—",
                style = typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        } else {
            CopyableProbeText(
                result.address,
                typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, color = scheme.onSurface),
                onCopy,
            )
        }
        val location = result.locationSummary(formatCloudflareColo(result.colo)).ifBlank { result.countryCode }
        val flag = countryCodeToEmoji(result.countryCode)
        CopyableProbeText(
            location,
            typography.bodyMedium.copy(color = scheme.primary),
            onCopy,
            label = listOf(flag, location).filter(String::isNotBlank).joinToString(" "),
        )
        val detailStyle = typography.bodyMedium.copy(color = scheme.onSurfaceVariant)
        CopyableProbeText(result.asn, detailStyle, onCopy)
        CopyableProbeText(result.isp, detailStyle, onCopy)
        if (result.warp.equals("on", ignoreCase = true)) {
            Text("WARP", style = detailStyle)
        }
        result.durationMillis?.let { duration ->
            Text(
                stringResource(R.string.monitor_network_request_duration_value, duration),
                style = typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        if (result.error != null && result.error != PublicProbeError.Unavailable) {
            Text(
                result.errorMessage.ifBlank { publicProbeErrorLabel(result.error) },
                style = typography.bodySmall,
                color = scheme.error,
            )
        }
    }
}

@Composable
private fun CopyableProbeText(
    value: String,
    style: TextStyle,
    onCopy: (String) -> Unit,
    label: String = value,
) {
    if (value.isBlank()) return
    Text(
        text = label,
        modifier = Modifier.clickable(
            onClickLabel = stringResource(R.string.monitor_copy_value),
            onClick = { onCopy(value) },
        ),
        style = style,
    )
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

private val NetworkContentModifier = Modifier.widthIn(max = 960.dp).fillMaxWidth()

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
