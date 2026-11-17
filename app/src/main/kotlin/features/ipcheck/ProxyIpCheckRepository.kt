// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.ipcheck

import android.content.Context
import app.AppState
import engine.mihomo.runtime.MihomoNodeAddressDirectory
import engine.mihomo.runtime.MihomoProxiesState
import engine.mihomo.runtime.MihomoProxyGroup
import engine.mihomo.runtime.MihomoRuntimeRepository
import engine.mihomo.runtime.activeNodeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The state of the proxy IP check.
 */
internal data class ProxyIpCheckState(
    val groups: List<MihomoProxyGroup> = emptyList(),
    val selectedGroup: String = "",
    val checking: Boolean = false,
    val result: IpCheckResult = IpCheckResult(),
    val error: String = "",
) {
    val hasResult: Boolean
        get() = result.entry.hasAddress || result.exit.hasAddress

    /**
     * The group the check runs against. The first group of the snapshot is the default, so the
     * card is usable before anything is selected.
     */
    fun effectiveGroup(): String {
        return selectedGroup.takeIf { name -> groups.any { group -> group.name == name } }
            ?: groups.firstOrNull()?.name.orEmpty()
    }
}

/**
 * Holds the state of the proxy IP check and runs the check.
 *
 * The check is a manual action, so it runs only when the user asks for it; the state keeps the
 * last answer until the next one.
 */
internal class ProxyIpCheckRepository(
    private val appScope: CoroutineScope,
    private val context: Context,
    private val mihomoRuntime: MihomoRuntimeRepository,
    private val stateStore: data.AndroidAppStateStore,
) {
    private val service = ProxyIpCheckService(
        context = context,
        mihomoRuntime = mihomoRuntime,
        addressDirectory = MihomoNodeAddressDirectory(context),
    )
    private val mutableState = MutableStateFlow(ProxyIpCheckState())
    private var checkJob: kotlinx.coroutines.Job? = null

    val state: StateFlow<ProxyIpCheckState> = mutableState.asStateFlow()

    /**
     * Publishes the groups the card offers. The snapshot changes with every runtime refresh, so
     * the groups are published whenever they change.
     */
    fun updateGroups(proxies: MihomoProxiesState) {
        val groups = service.selectableGroups(proxies)
        mutableState.update { current ->
            val selected = current.selectedGroup.takeIf { name -> groups.any { group -> group.name == name } }
                ?: groups.firstOrNull()?.name.orEmpty()
            if (current.groups == groups && current.selectedGroup == selected) {
                current
            } else {
                current.copy(groups = groups, selectedGroup = selected)
            }
        }
    }

    /**
     * Selects the group the check runs against.
     */
    fun selectGroup(groupName: String) {
        mutableState.update { current -> current.copy(selectedGroup = groupName) }
    }

    /**
     * Runs the check for the selected group. A running check is not started twice, so a second
     * tap does not replace a running one.
     */
    fun check(proxies: MihomoProxiesState) {
        val groupName = mutableState.value.effectiveGroup()
        // A group is only needed while the tunnel of this app carries the traffic. Without it the
        // check reports the network of the device, which needs no group at all.
        if (groupName.isBlank() && stateStore.state.value.proxyRunning) {
            mutableState.update { current ->
                current.copy(checking = false, error = IpCheckErrorUnknownGroup)
            }
            return
        }
        if (checkJob?.isActive == true) return
        checkJob = appScope.launch {
            mutableState.update { current -> current.copy(checking = true, error = "") }
            val appState = stateStore.state.value
            val result = withContext(Dispatchers.IO) {
                service.check(appState, proxies, groupName)
            }
            mutableState.update { current ->
                current.copy(checking = false, result = result, error = result.entry.error)
            }
        }
    }

    /**
     * The node the selected group carries the traffic with.
     */
    fun selectedNodeName(proxies: MihomoProxiesState): String {
        val groupName = mutableState.value.effectiveGroup()
        val group = proxies.groups.firstOrNull { candidate -> candidate.name == groupName } ?: return ""
        return group.resolvedNodeId(proxies)?.name.orEmpty()
    }

    /**
     * The node the whole configuration carries the traffic with, which is the node the current
     * configuration selects regardless of the group the check runs against.
     */
    fun activeNodeName(proxies: MihomoProxiesState): String {
        return proxies.activeNodeId()?.name.orEmpty()
    }
}
