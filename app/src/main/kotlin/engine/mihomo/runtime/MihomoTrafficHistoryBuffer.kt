// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import java.util.ArrayDeque

internal class MihomoTrafficHistoryBuffer(
    private val capacity: Int,
) {
    private val samples = ArrayDeque<MihomoTrafficSample>(capacity)

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun append(sample: MihomoTrafficSample) {
        if (samples.size == capacity) samples.removeFirst()
        samples.addLast(sample)
    }

    fun snapshot(limit: Int = capacity): List<MihomoTrafficSample> {
        if (limit <= 0 || samples.isEmpty()) return emptyList()

        val firstIncludedIndex = (samples.size - limit).coerceAtLeast(0)
        return buildList(minOf(samples.size, limit)) {
            samples.forEachIndexed { index, sample ->
                if (index >= firstIncludedIndex) add(sample)
            }
        }
    }

    fun clear() {
        samples.clear()
    }
}
