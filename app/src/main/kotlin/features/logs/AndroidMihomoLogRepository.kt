// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal object AndroidCoreLogRepository : AndroidMihomoLogRepository(
    logFile = { context -> context.androidMihomoErrorLog() },
    logTag = "AndroidCoreLogRepository",
)

internal abstract class AndroidMihomoLogRepository(
    private val logFile: (Context) -> CoreLogFile,
    private val logTag: String,
) : InMemoryCoreLogRepository() {
    private val restoredPreviousLogs = AtomicBoolean(false)
    private var appContext: Context? = null
    private val fileStore = BoundedLogFileStore(
        file = { appContext?.let(logFile)?.let { File(it.path) } },
        logTag = logTag,
        onFailure = { message, error -> AndroidAppLogger.warn(logTag, message, error) },
    )

    fun initialize(context: Context) {
        appContext = context.applicationContext
        restorePreviousLogs()
    }

    fun appendPersisted(level: String, message: String, timestampMillis: Long?) {
        super.append(level, message, timestampMillis)
        val persistedTime = timestampMillis?.toString() ?: "—"
        fileStore.appendLine("$persistedTime [$level] $message")
    }

    override fun clear() {
        super.clear()
        fileStore.clear()
    }

    override suspend fun refresh() {
        val context = appContext ?: return
        val restoredEntries = withContext(Dispatchers.IO) {
            readRestoredEntries(context)
        }
        replaceEntries(restoredEntries)
    }

    private fun restorePreviousLogs() {
        val context = appContext ?: return
        if (!restoredPreviousLogs.compareAndSet(false, true) || entries.value.isNotEmpty()) {
            return
        }

        replaceEntries(readRestoredEntries(context))
    }

    private fun readRestoredEntries(context: Context): List<CoreLogEntry> {
        val file = logFile(context)
        return restoredCoreLogEntries(
            lines = fileStore.readLastLines(),
            defaultLevel = file.defaultLevel,
        )
    }
}
