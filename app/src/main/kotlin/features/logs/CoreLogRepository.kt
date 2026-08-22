// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CoreLogEntry(
    val id: Long,
    val timestampMillis: Long?,
    val level: String,
    val message: String,
)

interface CoreLogRepository {
    val entries: StateFlow<List<CoreLogEntry>>

    fun append(level: String, message: String, timestampMillis: Long?)

    fun clear()

    suspend fun refresh() = Unit
}

open class InMemoryCoreLogRepository(
    private val maxEntries: Int = CoreLogMaxEntries,
    initialEntries: List<CoreLogEntry> = emptyList(),
) : CoreLogRepository {
    private val mutableEntries = MutableStateFlow(initialEntries.takeLast(maxEntries))

    override val entries: StateFlow<List<CoreLogEntry>> = mutableEntries.asStateFlow()

    override fun append(level: String, message: String, timestampMillis: Long?) {
        val normalizedMessage = message.trim()
        if (normalizedMessage.isEmpty()) {
            return
        }
        mutableEntries.update { entries ->
            val nextId = (entries.lastOrNull()?.id ?: 0L) + 1L
            (entries + CoreLogEntry(
                id = nextId,
                timestampMillis = timestampMillis,
                level = level.normalizedLogLevel(),
                message = normalizedMessage,
            )).takeLast(maxEntries)
        }
    }

    override fun clear() {
        clearInMemory()
    }

    fun clearInMemory() {
        mutableEntries.value = emptyList()
    }

    protected fun replaceEntries(entries: List<CoreLogEntry>) {
        val normalizedEntries = entries
            .mapNotNull { entry ->
                val normalizedMessage = entry.message.trim()
                if (normalizedMessage.isEmpty()) {
                    null
                } else {
                    entry.copy(
                        level = entry.level.normalizedLogLevel(),
                        message = normalizedMessage,
                    )
                }
            }
            .takeLast(maxEntries)

        mutableEntries.value = normalizedEntries.mapIndexed { index, entry ->
            entry.copy(id = index + 1L)
        }
    }
}

private fun String.normalizedLogLevel(): String {
    return when (lowercase()) {
        "debug" -> "debug"
        "warning", "warn" -> "warning"
        "error" -> "error"
        else -> "info"
    }
}
