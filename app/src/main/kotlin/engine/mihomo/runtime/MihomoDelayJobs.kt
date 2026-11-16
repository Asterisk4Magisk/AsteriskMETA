// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import kotlinx.coroutines.Job

/** Ordinary groups and individual leaf tests share slots; core-scheduled tests stay exclusive. */
internal class MihomoDelayJobs {
    private data class Entry(
        val groupName: String?,
        val nodeId: MihomoProxyNodeId?,
        val exclusive: Boolean,
        val preemptible: Boolean,
    )

    private val jobs = mutableMapOf<Job, Entry>()

    @Synchronized
    fun tryAcquire(
        job: Job,
        groupName: String? = null,
        exclusive: Boolean = true,
        preemptible: Boolean = false,
        nodeId: MihomoProxyNodeId? = null,
    ): Boolean {
        if (jobs.isNotEmpty() && (exclusive || jobs.values.any { it.exclusive })) return false
        if (groupName != null && jobs.values.any { it.groupName == groupName }) return false
        if (nodeId != null && jobs.values.any { it.nodeId == nodeId }) return false
        jobs[job] = Entry(groupName, nodeId, exclusive, preemptible)
        return true
    }

    @Synchronized
    fun preemptibleJob(): Job? = jobs.entries.firstOrNull { it.value.preemptible }?.key

    /** False for an old lifecycle's completion, which must not clear a new test's progress. */
    @Synchronized
    fun release(job: Job): Boolean = jobs.remove(job) != null

    @Synchronized
    fun cancel() {
        val cancelled = jobs.keys.toList()
        jobs.clear()
        cancelled.forEach(Job::cancel)
    }
}
