// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import engine.proxy.RuntimeRuleOutbound

internal fun MihomoProxiesState.withProviderNodes(
    providers: List<MihomoProxyProviderRuntimeDetail>,
    groupSources: Map<String, MihomoProxyGroupSources>,
): MihomoProxiesState {
    val compatibleProviders = providers.filter { it.isCompatible }.associateBy { it.name }
    val sourcesByGroup = groupSources.mapValues { (groupName, sources) ->
        // Keep incomplete include-all-proxies information when the backend omits the synthetic provider.
        val compatible = compatibleProviders[groupName]
        if (sources.includeAllProxies && compatible != null) {
            sources.copy(
                proxyNames = sources.proxyNames + compatible.nodes.map(MihomoProxyProviderNode::name),
                includeAllProxies = false,
            )
        } else {
            sources
        }
    }
    val providerNodes = providers.filterNot { it.isCompatible }.flatMap { provider ->
        provider.nodes.map { node ->
            MihomoProxyNode(
                id = MihomoProxyNodeId(node.name, provider.name),
                type = node.type,
                title = node.title.ifBlank { node.name },
                subtitle = node.subtitle.ifBlank { node.type },
                delay = node.delay,
                delayStatus = node.delayStatus,
                delayError = node.delayError,
                delayUpdatedAtMillis = node.delayUpdatedAtMillis,
            )
        }
    }
    return withResolvedGroupMembers(providerNodes, sourcesByGroup)
}

internal fun MihomoProxiesState.withPreservedProviderNodes(
    previous: MihomoProxiesState,
): MihomoProxiesState = withResolvedGroupMembers(
    providerNodes = previous.nodes.filter { it.providerName != null },
    sourcesByGroup = previous.groupSources,
)

private fun MihomoProxiesState.withResolvedGroupMembers(
    providerNodes: List<MihomoProxyNode>,
    sourcesByGroup: Map<String, MihomoProxyGroupSources>,
): MihomoProxiesState {
    val candidatesByName = providerNodes.groupBy(MihomoProxyNode::name)
    val resolvedGroups = allGroups.map { group ->
        val sources = sourcesByGroup[group.name]
        val members = group.all.map { id ->
            resolveGroupMember(id, group, sources, candidatesByName[id.name].orEmpty())
        }.distinct()
        group.copy(all = members)
    }
    val resolvedByName = resolvedGroups.associateBy(MihomoProxyGroup::name)
    val nodeById = (nodes.filter { it.id in topLevelProxyIds } + providerNodes)
        .associateByTo(linkedMapOf(), MihomoProxyNode::id)
    resolvedGroups.forEach { group ->
        group.all.forEach { id -> nodeById.putIfAbsent(id, node(id)) }
    }
    return copy(
        groups = groups.map { resolvedByName.getValue(it.name) },
        allGroups = resolvedGroups,
        groupSources = sourcesByGroup,
        nodes = nodeById.values.toList(),
        nodeById = nodeById,
    )
}

private fun MihomoProxiesState.resolveGroupMember(
    id: MihomoProxyNodeId,
    group: MihomoProxyGroup,
    sources: MihomoProxyGroupSources?,
    candidates: List<MihomoProxyNode>,
): MihomoProxyNodeId {
    if (id.providerName != null || id.fallbackGroupName != null) return id
    val unresolved = id.copy(fallbackGroupName = group.name)
    val topLevel = id in topLevelProxyIds
    if (sources == null) {
        return if (topLevel) id else candidates.singleOrNull()?.id ?: unresolved
    }
    val topLevelAllowed = topLevel && node(id).type.lowercase() !in sources.excludedTypes
    // The core puts explicit members first, then applies group-wide type exclusions.
    if (topLevelAllowed && id.name in sources.proxyNames) return id

    val providerMembers = candidates.filter { candidate ->
        candidate.providerName in sources.providerNames && candidate.type.lowercase() !in sources.excludedTypes
    }
    // A sole member matching empty-fallback may be the core's fallback proxy.
    val couldBeFallback = topLevel && id.name == sources.emptyFallback && group.all.size == 1
    val couldBeImplicitProxy = topLevelAllowed && sources.includeAllProxies
    if (couldBeFallback || couldBeImplicitProxy) {
        return if (providerMembers.isEmpty()) id else unresolved
    }
    return providerMembers.singleOrNull()?.id ?: unresolved
}

/** Resolves the current group selection and keeps provider identity at every step. */
internal fun MihomoProxiesState.resolveRuleOutboundChain(policy: String): List<RuntimeRuleOutbound> = buildList {
    if (policy.isBlank()) return@buildList
    var current = MihomoProxyNodeId(policy)
    val visited = mutableSetOf<MihomoProxyNodeId>()
    while (visited.add(current)) {
        val group = current.takeIf { it.providerName == null && it.fallbackGroupName == null }
            ?.let { id -> allGroups.firstOrNull { it.name == id.name } }
        add(RuntimeRuleOutbound(current.name, group?.icon ?: nodeById[current]?.icon.orEmpty()))
        if (group == null || group.now.isBlank()) break
        current = group.selectedMemberId()
    }
}

private fun MihomoProxyGroup.selectedMemberId(): MihomoProxyNodeId =
    all.firstOrNull { it.name == now } ?: MihomoProxyNodeId(now)

internal fun MihomoProxiesState.delayTestLeaf(id: MihomoProxyNodeId): MihomoProxyNodeId? {
    var current = id
    val visited = mutableSetOf<String>()
    repeat(allGroups.size + 1) {
        if (current.providerName != null || current.fallbackGroupName != null) return current
        val group = allGroups.firstOrNull { candidate -> candidate.name == current.name } ?: return current
        if (!visited.add(group.name)) return null
        // LoadBalance has no fixed selection; the core can test the group proxy itself.
        if (group.now.isBlank()) return current
        current = group.selectedMemberId()
    }
    return null
}

internal fun MihomoProxiesState.canShareProxyDelay(id: MihomoProxyNodeId): Boolean =
    delayTestLeaf(id)?.let { it.fallbackGroupName == null } == true

internal fun MihomoProxiesState.individualDelayTests(groupName: String): List<MihomoDelayTest>? {
    val group = groups.firstOrNull { candidate -> candidate.name == groupName }
        ?.takeIf(MihomoProxyGroup::usesIndividualDelayTests)
        ?: return null
    val tests = group.all.mapNotNull { id ->
        delayTestLeaf(id)?.takeIf { it.fallbackGroupName == null }
            ?.let { leaf -> MihomoDelayTest(resultId = id, measuredId = leaf) }
    }
    return tests.takeIf { candidates -> candidates.size == group.all.size && candidates.isNotEmpty() }
}

internal fun MihomoProxyGroup.usesIndividualDelayTests(): Boolean =
    type.lowercase().replace("-", "").replace("_", "") in
        setOf("select", "selector", "loadbalance", "smart")
