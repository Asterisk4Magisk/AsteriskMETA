// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import engine.proxy.AutoSwitchCheckProgress
import engine.proxy.AutoSwitchCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
internal data class AutoSwitchLogEntry(
    val timestampMillis: Long,
    val profileName: String,
    val groupName: String,
    val fromNode: String = "",
    val toNode: String = "",
    val checkResult: AutoSwitchCheckResult? = null,
)

internal enum class AutoSwitchMonitorPhase { Stopped, Checking, Waiting }

internal data class AutoSwitchMonitorState(
    val phase: AutoSwitchMonitorPhase = AutoSwitchMonitorPhase.Stopped,
    val nextCheckAtElapsedMillis: Long? = null,
    val progress: AutoSwitchCheckProgress = AutoSwitchCheckProgress(),
) {
    fun remainingSeconds(nowElapsedMillis: Long): Long =
        ((nextCheckAtElapsedMillis ?: nowElapsedMillis) - nowElapsedMillis).coerceAtLeast(0L)
            .let { (it + 999L) / 1000L }
}

/** App-private, structured history. Node names are kept out of the general diagnostic log. */
internal class AutoSwitchLogRepository(
    file: () -> File?,
    maxEntries: Int = AutoSwitchLogMaxEntries,
    private val onFailure: (String, Throwable) -> Unit,
) {
    private val limit = maxEntries.coerceAtLeast(1)
    private val lock = Any()
    private val store = BoundedLogFileStore(
        file = file,
        logTag = "automatic switching",
        maxLines = limit,
        trimExtraLines = 0,
        onFailure = onFailure,
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableEntries = MutableStateFlow(
        store.readLastLines().mapNotNull { line ->
            runCatching { json.decodeFromString<AutoSwitchLogEntry>(line) }
                .onFailure { onFailure("Failed to read an automatic switch log entry", it) }
                .getOrNull()
        }.asReversed(),
    )

    val entries: StateFlow<List<AutoSwitchLogEntry>> = mutableEntries.asStateFlow()
    private val mutableMonitorState = MutableStateFlow(AutoSwitchMonitorState())
    val monitorState: StateFlow<AutoSwitchMonitorState> = mutableMonitorState.asStateFlow()
    private val monitorLock = Any()
    private var monitorGeneration = 0L

    fun beginMonitoring(): Long = synchronized(monitorLock) {
        monitorGeneration += 1L
        mutableMonitorState.value = AutoSwitchMonitorState(AutoSwitchMonitorPhase.Checking)
        monitorGeneration
    }

    fun updateMonitorState(generation: Long, state: AutoSwitchMonitorState) =
        synchronized(monitorLock) {
            if (generation == monitorGeneration) mutableMonitorState.value = state
        }

    fun stopMonitoring() = synchronized(monitorLock) {
        monitorGeneration += 1L
        mutableMonitorState.value = AutoSwitchMonitorState()
    }

    fun append(entry: AutoSwitchLogEntry) = synchronized(lock) {
        store.appendLine(json.encodeToString(entry))
        mutableEntries.value = (listOf(entry) + mutableEntries.value).take(limit)
    }
}

internal const val AutoSwitchLogMaxEntries = 200
