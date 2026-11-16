// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

/** /proxies omits provider-only nodes. Keep their identities instead of testing a whole group. */
internal fun MihomoProxiesState.withProviderNodes(
    providers: List<MihomoProxyProviderRuntimeDetail>,
): MihomoProxiesState {
    val providerNodes = providers.flatMap { provider ->
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
    val candidatesByName = providerNodes.groupBy(MihomoProxyNode::name)
    fun resolve(id: MihomoProxyNodeId): MihomoProxyNodeId {
        if (id.providerName != null || id in topLevelProxyIds) return id
        // Names shared by providers are ambiguous in the official group snapshot.
        return candidatesByName[id.name]?.singleOrNull()?.id ?: id
    }
    val resolvedNodes = nodes.map { node ->
        val id = resolve(node.id)
        if (id == node.id) node else candidatesByName.getValue(node.name).single()
    }
    val nodeById = (resolvedNodes + providerNodes).associateBy(MihomoProxyNode::id)
    return copy(
        groups = groups.map { group -> group.copy(all = group.all.map(::resolve)) },
        allGroups = allGroups.map { group -> group.copy(all = group.all.map(::resolve)) },
        nodes = nodeById.values.toList(),
        nodeById = nodeById,
    )
}

internal fun MihomoProxiesState.withPreservedProviderNodes(
    previous: MihomoProxiesState,
): MihomoProxiesState {
    val previousProviderNodes = previous.nodes.filter { node -> node.providerName != null }
    if (previousProviderNodes.isEmpty()) return this
    val candidatesByName = previousProviderNodes.groupBy(MihomoProxyNode::name)
    fun resolve(id: MihomoProxyNodeId): MihomoProxyNodeId {
        if (id.providerName != null || id in topLevelProxyIds) return id
        return candidatesByName[id.name]?.singleOrNull()?.id ?: id
    }
    val resolvedNodes = nodes.map { node ->
        val id = resolve(node.id)
        if (id == node.id) {
            node
        } else {
            // Plain /proxies members are placeholders; keep provider metadata, but let the
            // timestamp-aware delay reconciliation decide which measurement is newer.
            previous.nodeById[id]?.copy(
                delay = node.delay,
                delayStatus = node.delayStatus,
                delayError = node.delayError,
                delayUpdatedAtMillis = node.delayUpdatedAtMillis,
            ) ?: node.copy(id = id)
        }
    }
    val nodeById = (previousProviderNodes + resolvedNodes).associateBy(MihomoProxyNode::id)
    return copy(
        groups = groups.map { group -> group.copy(all = group.all.map(::resolve)) },
        allGroups = allGroups.map { group -> group.copy(all = group.all.map(::resolve)) },
        nodes = nodeById.values.toList(),
        nodeById = nodeById,
    )
}

internal fun MihomoProxiesState.delayTestLeaf(id: MihomoProxyNodeId): MihomoProxyNodeId? {
    var current = id
    val visited = mutableSetOf<String>()
    repeat(allGroups.size + 1) {
        if (current.providerName != null) return current
        val group = allGroups.firstOrNull { candidate -> candidate.name == current.name } ?: return current
        if (!visited.add(group.name)) return null
        // LoadBalance has no fixed selection; the core can test the group proxy itself.
        if (group.now.isBlank()) return current
        current = group.all.firstOrNull { member -> member.name == group.now }
            ?: MihomoProxyNodeId(group.now)
    }
    return null
}

internal fun MihomoProxiesState.canShareProxyDelay(id: MihomoProxyNodeId): Boolean =
    delayTestLeaf(id)?.let { leaf -> groupFallbackFor(leaf) == null } == true

internal fun MihomoProxiesState.individualDelayTests(groupName: String): List<MihomoDelayTest>? {
    val group = groups.firstOrNull { candidate -> candidate.name == groupName }
        ?.takeIf(MihomoProxyGroup::usesIndividualDelayTests)
        ?: return null
    val tests = group.all.mapNotNull { id ->
        delayTestLeaf(id)?.takeIf { leaf -> groupFallbackFor(leaf) == null }
            ?.let { leaf -> MihomoDelayTest(resultId = id, measuredId = leaf) }
    }
    return tests.takeIf { candidates -> candidates.size == group.all.size && candidates.isNotEmpty() }
}

internal fun MihomoProxyGroup.usesIndividualDelayTests(): Boolean =
    type.lowercase().replace("-", "").replace("_", "") in
        setOf("select", "selector", "loadbalance", "smart")
