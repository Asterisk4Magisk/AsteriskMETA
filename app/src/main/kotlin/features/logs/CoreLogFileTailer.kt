// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import android.os.FileObserver
import android.system.Os
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.RandomAccessFile
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

internal data class CoreLogFile(
    val path: String,
    val defaultLevel: String,
)

internal class CoreLogFileTailer(
    private val logFiles: List<CoreLogFile>,
    private val repository: CoreLogRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        logFiles
            .filter { logFile -> logFile.path.isNotBlank() }
            .forEach { logFile ->
                scope.launch {
                    tail(logFile)
                }
            }
    }

    fun stop() {
        scope.cancel()
    }

    private suspend fun tail(logFile: CoreLogFile) {
        val file = File(logFile.path)
        val directory = file.parentFile ?: return
        directory.mkdirs()
        var position = runCatching { file.length() }.getOrDefault(0L)
        var failureLogged = false
        var reader: RandomAccessFile? = null
        var fileIdentity: Long? = null
        val signals = Channel<Unit>(Channel.CONFLATED)
        val reopenRequested = AtomicBoolean(false)
        @Suppress("DEPRECATION")
        val observer = object : FileObserver(directory.absolutePath, FileEventMask) {
            override fun onEvent(event: Int, path: String?) {
                if (path != file.name) return
                if (event and ReopenEventMask != 0) {
                    reopenRequested.set(true)
                }
                signals.trySend(Unit)
            }
        }

        try {
            observer.startWatching()
            signals.trySend(Unit)
            while (currentCoroutineContext().isActive) {
                withTimeoutOrNull(TailFallbackIntervalMillis.milliseconds) {
                    signals.receive()
                }

                if (!file.exists()) {
                    reader?.close()
                    reader = null
                    fileIdentity = null
                    continue
                }
                val currentFileIdentity = runCatching { Os.stat(file.absolutePath).st_ino }.getOrNull()
                if (reader != null && fileIdentity != currentFileIdentity) {
                    reader.close()
                    reader = null
                    position = 0L
                }
                if (reopenRequested.getAndSet(false)) {
                    reader?.close()
                    reader = null
                    position = 0L
                }

                runCatching {
                    val activeReader = reader ?: RandomAccessFile(file, "r").also { opened ->
                        reader = opened
                        fileIdentity = currentFileIdentity
                    }
                    if (position > activeReader.length()) {
                        position = 0L
                    }
                    activeReader.seek(position)

                    var line = activeReader.readUtf8Line()
                    while (line != null) {
                        repository.appendParsedCoreLogLine(line, logFile.defaultLevel)
                        line = activeReader.readUtf8Line()
                    }
                    position = activeReader.filePointer
                }.onSuccess {
                    failureLogged = false
                }.onFailure { error ->
                    runCatching { reader?.close() }
                    reader = null
                    fileIdentity = null
                    if (!failureLogged) {
                        AndroidAppLogger.warn(LogTag, "Failed to tail Mihomo log file: ${file.absolutePath}", error)
                        failureLogged = true
                    }
                }
            }
        } finally {
            observer.stopWatching()
            signals.close()
            runCatching { reader?.close() }
        }
    }

    private fun RandomAccessFile.readUtf8Line(): String? {
        return readLine()?.let { line ->
            String(line.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        }
    }

    private companion object {
        private const val LogTag = "CoreLogFileTailer"
        private const val TailFallbackIntervalMillis = 5_000L
        private const val ReopenEventMask = FileObserver.CREATE or FileObserver.MOVED_TO or
            FileObserver.DELETE or FileObserver.MOVED_FROM
        private const val FileEventMask = FileObserver.CLOSE_WRITE or FileObserver.MODIFY or ReopenEventMask
    }
}

internal data class ParsedCoreLogLine(
    val timestampMillis: Long?,
    val level: String,
    val message: String,
)

private const val PersistedCoreLogTimestampPattern =
    """(?:\d+|—|\d{4}[-/]\d{2}[-/]\d{2}\s+\d{2}:\d{2}:\d{2})"""

private val MihomoLogrusLineRegex =
    Regex("""^time=(?:"([^"]+)"|(\S+))\s+level=([A-Za-z]+)\s+msg=(.*)$""")
private val MihomoLogLineRegex =
    Regex("""^($PersistedCoreLogTimestampPattern)\s+\[([A-Za-z]+)]\s*(.*)$""")
private val MihomoLogLineWithoutLevelRegex =
    Regex("""^($PersistedCoreLogTimestampPattern)\s+(.*)$""")

internal fun CoreLogRepository.appendParsedCoreLogLine(line: String, defaultLevel: String) {
    val parsedLine = parseCoreLogLine(line, defaultLevel) ?: return
    append(
        level = parsedLine.level,
        message = parsedLine.message,
        timestampMillis = parsedLine.timestampMillis,
    )
}

internal fun parseCoreLogLine(
    line: String,
    defaultLevel: String,
    timeZone: TimeZone = TimeZone.getDefault(),
): ParsedCoreLogLine? {
    val trimmedLine = line.trim()
    if (trimmedLine.isEmpty()) {
        return null
    }

    MihomoLogrusLineRegex.matchEntire(trimmedLine)?.let { match ->
        val timestamp = match.groupValues[1].ifEmpty { match.groupValues[2] }
        return ParsedCoreLogLine(
            timestampMillis = parseCoreLogRfc3339Timestamp(timestamp),
            level = match.groupValues[3],
            message = decodeLogrusValue(match.groupValues[4]),
        )
    }

    MihomoLogLineRegex.matchEntire(trimmedLine)?.let { match ->
        val (time, level, message) = match.destructured
        return ParsedCoreLogLine(
            timestampMillis = parseCoreLogTimestamp(time, timeZone),
            level = level,
            message = message,
        )
    }

    MihomoLogLineWithoutLevelRegex.matchEntire(trimmedLine)?.let { match ->
        val (time, message) = match.destructured
        return ParsedCoreLogLine(
            timestampMillis = parseCoreLogTimestamp(time, timeZone),
            level = defaultLevel,
            message = message,
        )
    }

    return ParsedCoreLogLine(
        timestampMillis = null,
        level = defaultLevel,
        message = trimmedLine,
    )
}

internal fun restoredCoreLogEntries(
    lines: List<String>,
    defaultLevel: String,
    timeZone: TimeZone = TimeZone.getDefault(),
): List<CoreLogEntry> {
    return lines
        .mapNotNull { line -> parseCoreLogLine(line, defaultLevel, timeZone) }
        .mapIndexed { index, parsedLine ->
            CoreLogEntry(
                id = index + 1L,
                timestampMillis = parsedLine.timestampMillis,
                level = parsedLine.level,
                message = parsedLine.message,
            )
        }
}

private fun decodeLogrusValue(value: String): String {
    val trimmedValue = value.trim()
    if (trimmedValue.length < 2 || trimmedValue.first() != '"' || trimmedValue.last() != '"') {
        return trimmedValue
    }

    return buildString(trimmedValue.length - 2) {
        var escaped = false
        trimmedValue.substring(1, trimmedValue.lastIndex).forEach { char ->
            if (!escaped) {
                if (char == '\\') {
                    escaped = true
                } else {
                    append(char)
                }
                return@forEach
            }

            when (char) {
                '\\' -> append('\\')
                '"' -> append('"')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                else -> {
                    append('\\')
                    append(char)
                }
            }
            escaped = false
        }
        if (escaped) {
            append('\\')
        }
    }
}
