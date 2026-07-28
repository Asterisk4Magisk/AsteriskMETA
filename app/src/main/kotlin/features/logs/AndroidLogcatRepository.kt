// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import utils.decodeBase64StringOrNull
import utils.encodeBase64
import java.util.concurrent.atomic.AtomicBoolean

internal object AndroidLogcatRepository : InMemoryCoreLogRepository() {
    private const val LogTag = "AndroidLogcatRepository"

    private val restoredPreviousLogs = AtomicBoolean(false)
    private var appContext: Context? = null
    private val fileStore = BoundedLogFileStore(
        file = { appContext?.androidAppLogcatFile() },
        logTag = LogTag,
        onFailure = { message, error -> AndroidAppLogger.platformWarn(LogTag, message, error) },
    )

    fun initialize(context: Context) {
        appContext = context.applicationContext
        restorePreviousLogs()
    }

    override fun append(level: String, message: String, timestampMillis: Long?) {
        super.append(level, message, timestampMillis)
        appendPersistedLine(
            timestampMillis = timestampMillis,
            level = level,
            message = message,
        )
    }

    override fun clear() {
        super.clear()
        fileStore.clear()
    }

    override suspend fun refresh() {
        val restoredEntries = withContext(Dispatchers.IO) {
            readPersistedEntries()
        }
        replaceEntries(restoredEntries)
    }

    private fun restorePreviousLogs() {
        if (!restoredPreviousLogs.compareAndSet(false, true)) {
            return
        }
        val pendingEntries = entries.value
        replaceEntries(readPersistedEntries() + pendingEntries)
        pendingEntries.forEach { entry ->
            appendPersistedLine(
                timestampMillis = entry.timestampMillis,
                level = entry.level,
                message = entry.message,
            )
        }
    }

    private fun appendPersistedLine(timestampMillis: Long?, level: String, message: String) {
        fileStore.appendLine(encodeLogLine(timestampMillis, level, message))
    }

    private fun readPersistedEntries(): List<CoreLogEntry> {
        return fileStore.readLastLines()
            .mapIndexedNotNull { index, line ->
                decodeLogLine(id = index + 1L, line = line)
            }
    }
}

private const val LogcatFieldSeparator = '\t'

private fun encodeLogLine(timestampMillis: Long?, level: String, message: String): String {
    return listOf(
        timestampMillis?.toString().orEmpty(),
        level,
        message.encodeBase64(),
    ).joinToString(LogcatFieldSeparator.toString())
}

private fun decodeLogLine(id: Long, line: String): CoreLogEntry? {
    val fields = line.split(LogcatFieldSeparator, limit = 3)
    if (fields.size != 3) {
        return null
    }
    val message = fields[2].decodeBase64StringOrNull() ?: return null
    return CoreLogEntry(
        id = id,
        timestampMillis = decodePersistedLogTimestamp(fields[0]),
        level = fields[1],
        message = message,
    )
}
