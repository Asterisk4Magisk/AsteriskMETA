// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.connections

internal data class ClosedPageConnection<T>(val connection: T, val detectedAtMillis: Long)

internal data class ConnectionPageSnapshot<T>(
    val active: List<T> = emptyList(),
    val closed: Map<String, ClosedPageConnection<T>> = emptyMap(),
)

/** Page-owned memory only. Failed/stale snapshots never imply a closed connection. */
internal class ConnectionPageHistory<T>(private val id: (T) -> String) {
    private var previousRunning: Boolean? = null
    private var lastSnapshotMillis = 0L
    private var snapshot = ConnectionPageSnapshot<T>()

    fun update(
        running: Boolean,
        successful: Boolean,
        snapshotMillis: Long,
        connections: List<T>,
        nowMillis: Long,
    ): ConnectionPageSnapshot<T> {
        val restarted = previousRunning == false && running
        previousRunning = running
        if (restarted) {
            snapshot = ConnectionPageSnapshot()
            // The monitoring store may still contain the previous session's snapshot.
            lastSnapshotMillis = maxOf(lastSnapshotMillis, snapshotMillis)
            return snapshot
        }
        if (!running) {
            archiveMissing(emptyList(), nowMillis)
        } else if (successful && snapshotMillis > lastSnapshotMillis) {
            lastSnapshotMillis = snapshotMillis
            archiveMissing(connections, nowMillis)
        }
        return snapshot
    }

    private fun archiveMissing(current: List<T>, nowMillis: Long) {
        val currentIds = current.mapTo(HashSet(), id)
        val closed = LinkedHashMap(snapshot.closed)
        snapshot.active.filterNot { id(it) in currentIds }.forEach {
            closed[id(it)] = ClosedPageConnection(it, nowMillis)
        }
        currentIds.forEach(closed::remove)
        while (closed.size > 500) closed.remove(closed.keys.first())
        snapshot = ConnectionPageSnapshot(current.toList(), closed.toMap())
    }
}
