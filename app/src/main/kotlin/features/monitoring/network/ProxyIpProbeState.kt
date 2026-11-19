// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.network

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.R
import engine.network.isIpv4Address
import engine.network.ProxyIpProbeException
import engine.network.ProxyIpProbeRequest
import engine.network.ProxyIpProbeResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.SocketTimeoutException
import ui.components.AsteriskDropdownAnchor
import ui.components.AsteriskDropdownMenuItem
import kotlin.time.Duration.Companion.milliseconds

internal data class ProxyIpProbeTarget(val tag: String, val label: String = tag)

@Composable
internal fun ProxyIpProbeTargetPicker(
    groups: List<ProxyIpProbeTarget>,
    target: ProxyIpProbeTarget?,
    onSelect: (ProxyIpProbeTarget?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentNetwork = stringResource(R.string.monitor_network_probe_current)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = { expanded = true })
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.monitor_network_probe_target),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                target?.label ?: currentNetwork,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AsteriskDropdownAnchor(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            menuModifier = Modifier.widthIn(max = 300.dp),
        ) {
            AsteriskDropdownMenuItem(currentNetwork, selected = target == null, onClick = {
                expanded = false
                onSelect(null)
            })
            groups.forEach { group ->
                AsteriskDropdownMenuItem(group.label, selected = group.tag == target?.tag, onClick = {
                    expanded = false
                    onSelect(group)
                })
            }
        }
    }
}

internal class ProxyIpProbeState {
    private val measurements = mutableStateMapOf<Pair<AddressFamily, ProbeTarget>, Measurement>()
    private val jobs = mutableMapOf<Pair<AddressFamily, ProbeTarget>, Job>()
    private var entryMeasurements by mutableStateOf(emptyMap<AddressFamily, List<PublicAddressProbeResult>>())
    private var entryJob: Job? = null
    var entryRefreshing by mutableStateOf(false)
        private set

    val refreshing: Boolean
        get() = entryRefreshing || measurements.values.any { it.refreshing }

    fun entry(family: AddressFamily): List<PublicAddressProbeResult>? = entryMeasurements[family]

    fun startEntry(
        scope: CoroutineScope,
        family: AddressFamily?,
        lookup: suspend () -> List<PublicAddressProbeResult>,
    ) {
        if (entryJob?.isActive == true) return
        entryRefreshing = true
        entryJob = scope.launch {
            try {
                val results = try {
                    withTimeoutOrNull(15_000.milliseconds) { lookup() }.orEmpty()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    emptyList()
                }
                val byFamily = results.groupBy {
                    if (isIpv4Address(it.address)) AddressFamily.Ipv4 else AddressFamily.Ipv6
                }
                val families = family?.let(::listOf) ?: AddressFamily.entries
                entryMeasurements += families.associateWith { byFamily[it].orEmpty() }
            } finally {
                entryRefreshing = false
            }
        }
    }

    fun measurement(family: AddressFamily, target: ProbeTarget): Measurement =
        measurements[family to target] ?: Measurement(PublicAddressProbeResult())

    fun start(
        scope: CoroutineScope,
        tag: String,
        family: AddressFamily? = null,
        probe: suspend (ProxyIpProbeRequest) -> ProxyIpProbeResponse,
    ) {
        DefaultPublicProbeEndpoints.forEach { endpoint ->
            if (family != null && endpoint.family != family) return@forEach
            val key = endpoint.family to endpoint.target
            val previous = measurement(endpoint.family, endpoint.target)
            if (previous.refreshing) return@forEach
            val request = ProxyIpProbeRequest(tag, endpoint.url)
            measurements[key] = previous.copy(refreshing = true)
            jobs[key] = scope.launch {
                measurements[key] = try {
                    val startedAt = SystemClock.elapsedRealtime()
                    val response = withTimeoutOrNull(15_000.milliseconds) { probe(request) }
                    if (response == null) {
                        previous.failed(PublicProbeError.Timeout)
                    } else if (response.outboundTag != tag) {
                        previous.failed(PublicProbeError.InvalidResponse)
                    } else {
                        val durationMillis = SystemClock.elapsedRealtime() - startedAt
                        previous.withResponse(response, endpoint, durationMillis)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: SocketTimeoutException) {
                    previous.failed(PublicProbeError.Timeout)
                } catch (error: ProxyIpProbeException) {
                    val message = when (error.code) {
                        "UNSUPPORTED" -> R.string.monitor_network_probe_unsupported
                        "OUTBOUND_NOT_FOUND" -> R.string.monitor_network_probe_group_missing
                        "NOT_RUNNING", "RUNTIME_CHANGED" -> R.string.monitor_network_probe_runtime_changed
                        else -> null
                    }
                    previous.failed(PublicProbeError.Network, message)
                } catch (_: Exception) {
                    previous.failed(PublicProbeError.Network)
                }
            }
        }
    }

    fun cancel() {
        entryJob?.cancel()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    data class Measurement(
        val result: PublicAddressProbeResult,
        val refreshing: Boolean = false,
        val message: Int? = null,
    ) {
        fun failed(error: PublicProbeError, message: Int? = null): Measurement = Measurement(
            result = PublicAddressProbeResult(error = error),
            message = message,
        )

        fun withResponse(
            response: ProxyIpProbeResponse,
            endpoint: PublicProbeEndpoint,
            durationMillis: Long,
        ): Measurement {
            when (response.status) {
                "timeout" -> return failed(PublicProbeError.Timeout)
                "unreachable" -> return failed(PublicProbeError.Network)
                "blocked" -> return failed(PublicProbeError.Network, R.string.monitor_network_probe_blocked)
                "ok" -> Unit
                else -> return failed(PublicProbeError.InvalidResponse)
            }
            val body = response.response ?: return failed(PublicProbeError.InvalidResponse)
            val outcome = parsePublicProbeOutcome(body, endpoint.family, endpoint.target)
            if (outcome !is PublicProbeParseOutcome.Success) return failed(PublicProbeError.InvalidResponse)
            val address = outcome.parsed
            return Measurement(
                PublicAddressProbeResult(
                    address = address.address,
                    durationMillis = durationMillis,
                    country = address.country,
                    countryCode = address.countryCode,
                    region = address.region,
                    city = address.city,
                    isp = address.isp,
                    asn = address.asn,
                    colo = address.colo,
                    warp = address.warp,
                ),
            )
        }
    }
}
