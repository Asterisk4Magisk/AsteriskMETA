// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import app.AppState
import engine.mihomo.DefaultMihomoDelayTestUrl
import engine.mihomo.selectedMihomoProfileOrNull
import engine.proxy.AutoSwitchCheckKind
import engine.proxy.AutoSwitchCheckResult
import engine.proxy.AutoSwitchController
import engine.proxy.AutoSwitchGateway
import engine.proxy.AutoSwitchGroup
import features.logs.AndroidAppLogger
import features.logs.AutoSwitchLogEntry
import features.logs.AutoSwitchMonitorPhase
import features.logs.AutoSwitchMonitorState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

internal suspend fun monitorMihomoAutoSwitch(
    context: Context,
    runtime: MihomoRuntimeRepository,
    appState: AppState,
    currentState: () -> AppState,
) {
    val settings = appState.autoSwitch.normalized()
    if (!settings.enabled || !appState.proxyRunning ||
        settings.profileId != appState.selectedMihomoProfileId || settings.groups.isEmpty()
    ) return
    val networkMonitor =
        AutoSwitchNetworkMonitor(context.getSystemService(ConnectivityManager::class.java))
    var snapshot = MihomoProxiesState()
    suspend fun measureUrl(id: MihomoProxyNodeId, url: String): Int? {
        currentCoroutineContext().ensureActive()
        if (!networkMonitor.available) return null
        return runtime.autoSwitchDelay(appState, id, url)
    }

    var manualGeneration = runtime.manualSelectionGeneration()
    val gateway = object : AutoSwitchGateway {
        override fun networkAvailable(): Boolean = networkMonitor.available

        override suspend fun groups(): List<AutoSwitchGroup> {
            snapshot = runtime.autoSwitchSnapshot(appState)
            val groupNames = snapshot.groups.mapTo(hashSetOf()) { it.name }
            return snapshot.groups.filter { it.type.equals("Selector", ignoreCase = true) }
                .map { group ->
                    AutoSwitchGroup(
                        name = group.name,
                        selected = group.now,
                        candidates = group.all.filter { id ->
                            val node = snapshot.node(id)
                            id.name !in groupNames && node.type.lowercase() !in NonNodeTypes &&
                                    id.name.uppercase() !in NonNodeNames
                        }.map { it.name },
                    )
                }
        }

        override suspend fun measure(group: AutoSwitchGroup, node: String): Int? {
            val proxyId =
                snapshot.groups.first { it.name == group.name }.all.first { it.name == node }
            val testUrl = snapshot.groups.first { it.name == group.name }.testUrl
                .ifBlank { DefaultMihomoDelayTestUrl }
            // Confirm a failed primary endpoint against a second independent endpoint.
            val first = measureUrl(proxyId, testUrl)
            if (first != null) return first
            if (!networkAvailable()) return null
            val confirmationUrl =
                if (testUrl == ConfirmationUrl) DefaultMihomoDelayTestUrl else ConfirmationUrl
            return measureUrl(proxyId, confirmationUrl)
        }

        override suspend fun selectIfUnchanged(group: AutoSwitchGroup, node: String): Boolean =
            runtime.autoSwitchSelect(appState, group, node) {
                currentState().let { state ->
                    state.autoSwitchSessionKey() == appState.autoSwitchSessionKey()
                } && runtime.manualSelectionGeneration() == manualGeneration && networkAvailable()
            }
    }

    fun recordCheck(result: AutoSwitchCheckResult) {
        runtime.autoSwitchLog.append(
            AutoSwitchLogEntry(
                timestampMillis = System.currentTimeMillis(),
                profileName = appState.selectedMihomoProfileOrNull()?.name.orEmpty(),
                groupName = result.groupName,
                checkResult = result,
            ),
        )
    }

    val monitorGeneration = runtime.autoSwitchLog.beginMonitoring()
    val controller = AutoSwitchController(
        onCheckResult = ::recordCheck,
        onProgress = { progress ->
            runtime.autoSwitchLog.updateMonitorState(
                monitorGeneration,
                AutoSwitchMonitorState(AutoSwitchMonitorPhase.Checking, progress = progress),
            )
        },
    )
    try {
        while (currentCoroutineContext().isActive) {
            runtime.autoSwitchLog.updateMonitorState(
                monitorGeneration,
                AutoSwitchMonitorState(AutoSwitchMonitorPhase.Checking)
            )
            try {
                val latestManualGeneration = runtime.manualSelectionGeneration()
                if (latestManualGeneration != manualGeneration) {
                    controller.reset()
                    manualGeneration = latestManualGeneration
                }
                if (!runtime.runAutoSwitchCheck { controller.check(settings, gateway) }) {
                    controller.reset()
                    recordCheck(AutoSwitchCheckResult(AutoSwitchCheckKind.InteractiveOperation))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                controller.reset()
                recordCheck(
                    AutoSwitchCheckResult(
                        AutoSwitchCheckKind.ControlError,
                        errorCategory = error.javaClass.simpleName
                    )
                )
                // Keep the error category diagnosable without exposing names or API secrets.
                AndroidAppLogger.warn(
                    "AutoSwitch",
                    "Automatic node health check failed (${error.javaClass.simpleName})"
                )
            }
            runtime.autoSwitchLog.updateMonitorState(
                monitorGeneration,
                AutoSwitchMonitorState(
                    AutoSwitchMonitorPhase.Waiting,
                    nextCheckAtElapsedMillis = SystemClock.elapsedRealtime() + settings.intervalSeconds * 1000L,
                ),
            )
            delay(settings.intervalSeconds.seconds)
        }
    } finally {
        runtime.autoSwitchLog.updateMonitorState(monitorGeneration, AutoSwitchMonitorState())
        networkMonitor.close()
    }
}

private class AutoSwitchNetworkMonitor(private val connectivity: ConnectivityManager) :
    AutoCloseable {
    private val networks = ConcurrentHashMap<Network, Boolean>()
    val available: Boolean get() = networks.values.any { it }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            networks[network] =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        override fun onLost(network: Network) {
            networks.remove(network)
        }
    }

    init {
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(),
            callback,
        )
    }

    override fun close() {
        connectivity.unregisterNetworkCallback(callback)
    }
}

internal fun AppState.autoSwitchSessionKey(): List<Any?> = listOf(
    proxyRunning, runMode, mihomoMode, selectedMihomoProfileId, selectedMihomoProfileOrNull(),
    autoSwitch, mihomoControlPort, mihomoControlSecret, enableLocalDns, enableAppListEnhancement,
)

private const val ConfirmationUrl = "https://www.gstatic.com/generate_204"
private val NonNodeTypes = setOf(
    "direct",
    "reject",
    "rejectdrop",
    "pass",
    "selector",
    "urltest",
    "fallback",
    "loadbalance",
    "relay"
)
private val NonNodeNames = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE")
