// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.connections

import engine.mihomo.runtime.MihomoConnection
import java.util.Locale

/**
 * The owner of a connection as far as the page can tell it apart.
 *
 * [packageName] is set only for an installed application, because it is also the
 * icon source. A connection owned by a process that resolves to no package keeps
 * [label] only, and a connection with no attribution at all uses [unknownConnectionAppIdentity].
 */
internal data class ConnectionAppIdentity(
    val key: String,
    val packageName: String? = null,
    val label: String = "",
)

internal val unknownConnectionAppIdentity = ConnectionAppIdentity(key = "")

internal data class ConnectionAppGroup(
    val identity: ConnectionAppIdentity,
    val connections: List<MihomoConnection>,
) {
    val key: String get() = identity.key
    val packageName: String? get() = identity.packageName
    val label: String get() = identity.label
    val connectionCount: Int get() = connections.size
}

/**
 * Groups already filtered and sorted connections by their owner application.
 *
 * Connections keep the incoming order inside a group, so the page's sort option
 * still decides how each application lists its own connections. Groups are ordered
 * by connection count, then by name, which keeps the busiest applications on top.
 */
internal fun groupConnectionsByApp(
    connections: List<MihomoConnection>,
    resolve: (MihomoConnection) -> ConnectionAppIdentity,
): List<ConnectionAppGroup> {
    if (connections.isEmpty()) return emptyList()
    val connectionsByKey = LinkedHashMap<String, MutableList<MihomoConnection>>()
    val identitiesByKey = HashMap<String, ConnectionAppIdentity>()
    connections.forEach { connection ->
        val identity = resolve(connection)
        identitiesByKey[identity.key] = identity
        connectionsByKey.getOrPut(identity.key) { mutableListOf() }.add(connection)
    }
    return connectionsByKey.map { (key, members) ->
        ConnectionAppGroup(identity = identitiesByKey.getValue(key), connections = members)
    }.sortedWith(
        compareByDescending<ConnectionAppGroup> { group -> group.connectionCount }
            .thenBy { group ->
                group.label.ifBlank { group.packageName.orEmpty() }.lowercase(Locale.ROOT)
            },
    )
}

/** A flat list row: one application header, optionally followed by its connections. */
internal sealed interface ConnectionAppRow {
    val key: String

    data class Group(val group: ConnectionAppGroup) : ConnectionAppRow {
        override val key: String get() = "app:${group.key}"
    }

    data class Link(
        val group: ConnectionAppGroup,
        val connection: MihomoConnection,
    ) : ConnectionAppRow {
        override val key: String get() = "link:${connection.id}"
    }
}

internal fun buildConnectionAppRows(
    groups: List<ConnectionAppGroup>,
    expandedAppKeys: Set<String>,
): List<ConnectionAppRow> = buildList {
    groups.forEach { group ->
        add(ConnectionAppRow.Group(group))
        if (group.key in expandedAppKeys) {
            group.connections.forEach { connection ->
                add(ConnectionAppRow.Link(group = group, connection = connection))
            }
        }
    }
}
