// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Owns shared requests independently of any one group waiting for their results. */
internal class MihomoSharedDelayTasks<K, R> {
    private class Entry<R> {
        val result = CompletableDeferred<R>()
        val owners = mutableMapOf<String?, Int>()
        lateinit var worker: Job
        var started = false
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val tasks = linkedMapOf<K, Entry<R>>()
    private var focusedOwner: String? = null
    private var active = 0
    private var limit = 1

    @Synchronized
    fun focus(owner: String?) {
        focusedOwner = owner
        dispatch()
    }

    suspend fun run(
        key: K,
        concurrency: Int,
        owner: String? = null,
        block: suspend () -> R,
    ): R {
        require(concurrency > 0)
        currentCoroutineContext().ensureActive()
        val entry = synchronized(this) {
            limit = concurrency
            val entry = tasks[key]?.takeUnless { it.result.isCompleted } ?: Entry<R>().also { created ->
                tasks[key] = created
                created.worker = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        currentCoroutineContext().ensureActive()
                        val result = block()
                        currentCoroutineContext().ensureActive()
                        created.result.complete(result)
                    } catch (error: Throwable) {
                        created.result.completeExceptionally(error)
                        if (error is CancellationException) throw error
                    }
                }
                created.worker.invokeOnCompletion { error ->
                    error?.let(created.result::completeExceptionally)
                    synchronized(this) {
                        if (tasks[key] === created) tasks.remove(key)
                        if (created.started) active -= 1
                        dispatch()
                    }
                }
            }
            entry.owners[owner] = entry.owners.getOrDefault(owner, 0) + 1
            dispatch()
            entry
        }
        try {
            return entry.result.await()
        } finally {
            synchronized(this) {
                val remaining = entry.owners.getValue(owner) - 1
                if (remaining == 0) entry.owners.remove(owner) else entry.owners[owner] = remaining
                if (entry.owners.isEmpty() && !entry.result.isCompleted) {
                    entry.result.cancel()
                    // A blocking core call retains its permit until it actually returns.
                    entry.worker.cancel()
                }
            }
        }
    }

    @Synchronized
    private fun dispatch() {
        while (active < limit) {
            var next: Entry<R>? = null
            var bestPriority = Int.MAX_VALUE
            // Linked-map order is FIFO within a priority; no queue/list allocation per result.
            for (entry in tasks.values) {
                if (entry.started || entry.result.isCompleted || entry.owners.isEmpty()) continue
                val priority = when {
                    null in entry.owners -> 0 // Explicit single-node test.
                    focusedOwner in entry.owners -> 1
                    else -> 2
                }
                if (priority < bestPriority) {
                    next = entry
                    bestPriority = priority
                    if (priority == 0) break
                }
            }
            val task = next ?: return
            task.started = true
            active += 1
            task.worker.start()
        }
    }
}

/** Aggregate completion must not replay early results over a newer test from another group. */
internal suspend fun publishMihomoDelayResults(
    onResult: (MihomoDelayResult) -> Unit,
    test: suspend (onResult: (MihomoDelayResult) -> Unit) -> MihomoDelayResult,
): MihomoDelayResult {
    val published = mutableSetOf<MihomoProxyNodeId>()
    val result = test { result ->
        synchronized(published) { published.addAll(result.measurements.keys) }
        onResult(result)
    }
    currentCoroutineContext().ensureActive()
    val remaining = result.measurements.filterKeys { id -> id !in published }
    if (remaining.isNotEmpty()) onResult(MihomoDelayResult(remaining))
    return result
}

internal suspend fun <T, R> runMihomoDelayBatch(
    targets: List<T>,
    onResult: (R) -> Unit = {},
    test: suspend (T) -> R,
): List<R> = coroutineScope {
    targets.map { target ->
        // Register every waiter in list order; only the shared scheduler dispatches IO workers.
        async(start = CoroutineStart.UNDISPATCHED) {
            test(target).also { result ->
                currentCoroutineContext().ensureActive()
                onResult(result)
            }
        }
    }.awaitAll()
}
