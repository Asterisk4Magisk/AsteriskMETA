// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.traffic

import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

@Serializable
internal data class TrafficBytes(
    val upload: Long = 0L,
    val download: Long = 0L,
) {
    val total: Long
        get() = saturatedAdd(upload, download)

    operator fun plus(other: TrafficBytes): TrafficBytes {
        return TrafficBytes(
            upload = saturatedAdd(upload, other.upload),
            download = saturatedAdd(download, other.download),
        )
    }
}

@Serializable
internal data class TrafficBaseline(
    val sessionId: String,
    val uploadTotalBytes: Long,
    val downloadTotalBytes: Long,
    val observedAtMillis: Long,
    val localDay: String,
    val sourceId: String = "",
)

@Serializable
internal data class TrafficLedger(
    val version: Int = TrafficLedgerVersion,
    val baseline: TrafficBaseline? = null,
    val days: Map<String, TrafficBytes> = emptyMap(),
) {
    fun totalForDay(day: String): TrafficBytes = days[day] ?: TrafficBytes()

    fun totalForLastDays(
        dayCount: Int,
        endingDay: String = maxOf(baseline?.localDay.orEmpty(), days.keys.maxOrNull().orEmpty()),
    ): TrafficBytes {
        if (dayCount <= 0 || days.isEmpty()) return TrafficBytes()
        val cutoff = subtractLocalDays(endingDay, dayCount - 1) ?: return TrafficBytes()
        return days.entries
            .asSequence()
            .filter { (day) -> day >= cutoff && day <= endingDay }
            .fold(TrafficBytes()) { total, (_, bytes) -> total + bytes }
    }
}

internal data class TrafficLedgerSample(
    val uploadTotalBytes: Long,
    val downloadTotalBytes: Long,
    val observedAtMillis: Long,
    val localDay: String,
    val sourceId: String = "",
)

internal data class TrafficLedgerReduction(
    val ledger: TrafficLedger,
    val delta: TrafficBytes = TrafficBytes(),
    val changed: Boolean,
)

internal fun reduceTrafficLedger(
    ledger: TrafficLedger,
    sample: TrafficLedgerSample,
    timeZone: TimeZone = TimeZone.getDefault(),
): TrafficLedgerReduction {
    val normalizedSample = sample.copy(
        uploadTotalBytes = sample.uploadTotalBytes.coerceAtLeast(0L),
        downloadTotalBytes = sample.downloadTotalBytes.coerceAtLeast(0L),
        localDay = localTrafficDay(sample.observedAtMillis, timeZone),
    )
    val previous = ledger.baseline
    val continuousSource = previous != null && previous.isSameSourceFamily(normalizedSample.sourceId)
    if (continuousSource &&
        previous.uploadTotalBytes == normalizedSample.uploadTotalBytes &&
        previous.downloadTotalBytes == normalizedSample.downloadTotalBytes
    ) {
        if (previous.localDay == normalizedSample.localDay ||
            normalizedSample.observedAtMillis <= previous.observedAtMillis
        ) {
            return TrafficLedgerReduction(ledger = ledger, changed = false)
        }
        return TrafficLedgerReduction(
            ledger = ledger.copy(
                version = TrafficLedgerVersion,
                baseline = normalizedSample.toBaseline(),
                days = pruneTrafficDays(ledger.days, normalizedSample.localDay),
            ),
            changed = true,
        )
    }

    val delta: TrafficBytes
    val intervalStart: Long
    if (continuousSource) {
        val previousBaseline = checkNotNull(previous)
        delta = TrafficBytes(
            upload = counterDelta(previousBaseline.uploadTotalBytes, normalizedSample.uploadTotalBytes),
            download = counterDelta(previousBaseline.downloadTotalBytes, normalizedSample.downloadTotalBytes),
        )
        intervalStart = previousBaseline.observedAtMillis
    } else {
        delta = TrafficBytes(
            upload = normalizedSample.uploadTotalBytes,
            download = normalizedSample.downloadTotalBytes,
        )
        intervalStart = normalizedSample.observedAtMillis
    }
    val updatedDays = ledger.days.toMutableMap()
    if (delta.total > 0L) {
        allocateTrafficAcrossLocalDays(
            bytes = delta,
            startMillis = intervalStart,
            endMillis = normalizedSample.observedAtMillis,
            fallbackDay = normalizedSample.localDay,
            timeZone = timeZone,
        ).forEach { (day, dayBytes) ->
            updatedDays[day] = updatedDays.getOrDefault(day, TrafficBytes()) + dayBytes
        }
    }
    return TrafficLedgerReduction(
        ledger = ledger.copy(
            version = TrafficLedgerVersion,
            baseline = normalizedSample.toBaseline(),
            days = pruneTrafficDays(updatedDays, normalizedSample.localDay),
        ),
        delta = delta,
        changed = true,
    )
}

private fun allocateTrafficAcrossLocalDays(
    bytes: TrafficBytes,
    startMillis: Long,
    endMillis: Long,
    fallbackDay: String,
    timeZone: TimeZone,
): Map<String, TrafficBytes> {
    if (bytes.total <= 0L) return emptyMap()
    if (endMillis <= startMillis) return mapOf(fallbackDay to bytes)
    if (localTrafficDay(startMillis, timeZone) == fallbackDay &&
        localTrafficDay(endMillis, timeZone) == fallbackDay
    ) {
        return mapOf(fallbackDay to bytes)
    }

    val totalDuration = endMillis - startMillis
    val allocations = linkedMapOf<String, TrafficBytes>()
    val retentionStart = subtractLocalDays(fallbackDay, TrafficRetentionDays - 1)
        ?.let { day -> localDayStart(day, timeZone) }
        ?: startMillis
    var cursor = maxOf(startMillis, retentionStart)
    var cumulativeDuration = cursor - startMillis
    var allocatedUpload = proportionalBytes(bytes.upload, cumulativeDuration, totalDuration)
    var allocatedDownload = proportionalBytes(bytes.download, cumulativeDuration, totalDuration)
    while (cursor < endMillis) {
        val nextBoundary = nextLocalDayStart(cursor, timeZone)
        val segmentEnd = minOf(endMillis, nextBoundary.takeIf { it > cursor } ?: endMillis)
        cumulativeDuration += segmentEnd - cursor

        val uploadThroughSegment = proportionalBytes(bytes.upload, cumulativeDuration, totalDuration)
        val downloadThroughSegment = proportionalBytes(bytes.download, cumulativeDuration, totalDuration)
        val day = localTrafficDay(cursor, timeZone)
        val dayBytes = TrafficBytes(
            upload = uploadThroughSegment - allocatedUpload,
            download = downloadThroughSegment - allocatedDownload,
        )
        allocations[day] = allocations.getOrDefault(day, TrafficBytes()) + dayBytes
        allocatedUpload = uploadThroughSegment
        allocatedDownload = downloadThroughSegment
        cursor = segmentEnd
    }
    return allocations
}

private fun proportionalBytes(totalBytes: Long, elapsedMillis: Long, totalMillis: Long): Long {
    if (totalBytes <= 0L || elapsedMillis <= 0L) return 0L
    if (elapsedMillis >= totalMillis) return totalBytes
    return BigInteger.valueOf(totalBytes)
        .multiply(BigInteger.valueOf(elapsedMillis))
        .divide(BigInteger.valueOf(totalMillis))
        .toLong()
}

private fun nextLocalDayStart(timestampMillis: Long, timeZone: TimeZone): Long {
    val nextDay = GregorianCalendar(timeZone).apply {
        timeInMillis = timestampMillis
        add(Calendar.DAY_OF_MONTH, 1)
    }
    return localDayStart(
        year = nextDay.get(Calendar.YEAR),
        month = nextDay.get(Calendar.MONTH) + 1,
        day = nextDay.get(Calendar.DAY_OF_MONTH),
        timeZone = timeZone,
    )
}

private fun localDayStart(day: String, timeZone: TimeZone): Long? {
    val parts = day.split('-')
    if (parts.size != 3) return null
    return localDayStart(
        year = parts[0].toIntOrNull() ?: return null,
        month = parts[1].toIntOrNull() ?: return null,
        day = parts[2].toIntOrNull() ?: return null,
        timeZone = timeZone,
    )
}

private fun localDayStart(year: Int, month: Int, day: Int, timeZone: TimeZone): Long {
    val targetDay = formatLocalDay(year, month, day)
    val resolvedMidnight = GregorianCalendar(timeZone).apply {
        clear()
        set(year, month - 1, day, 0, 0, 0)
    }.timeInMillis
    if (localTrafficDay(resolvedMidnight, timeZone) != targetDay) return resolvedMidnight

    // Calendar chooses the later occurrence when midnight repeats. Locate the first instant
    // belonging to the target local date so allocation remains correct across that transition.
    var beforeTarget = resolvedMidnight - LocalDayBoundarySearchWindowMillis
    var firstTarget = resolvedMidnight
    while (firstTarget - beforeTarget > 1L) {
        val midpoint = beforeTarget + (firstTarget - beforeTarget) / 2L
        if (localTrafficDay(midpoint, timeZone) < targetDay) {
            beforeTarget = midpoint
        } else {
            firstTarget = midpoint
        }
    }
    return firstTarget
}

internal fun localTrafficDay(timestampMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
    val calendar = GregorianCalendar(timeZone).apply { timeInMillis = timestampMillis }
    return formatLocalDay(
        year = calendar.get(Calendar.YEAR),
        month = calendar.get(Calendar.MONTH) + 1,
        day = calendar.get(Calendar.DAY_OF_MONTH),
    )
}

internal fun localTrafficDaysEndingAt(endingDay: String, dayCount: Int): List<String> {
    if (dayCount <= 0) return emptyList()
    return (dayCount - 1 downTo 0).mapNotNull { offset -> subtractLocalDays(endingDay, offset) }
}

private fun TrafficLedgerSample.toBaseline(): TrafficBaseline {
    return TrafficBaseline(
        sessionId = sourceId,
        uploadTotalBytes = uploadTotalBytes,
        downloadTotalBytes = downloadTotalBytes,
        observedAtMillis = observedAtMillis,
        localDay = localDay,
        sourceId = sourceId,
    )
}

private fun TrafficBaseline.isSameSourceFamily(sampleSourceId: String): Boolean {
    if (sourceId.isBlank()) return true
    return sourceId.toTrafficSourceFamily() == sampleSourceId.toTrafficSourceFamily()
}

private fun String.toTrafficSourceFamily(): String {
    return when {
        this == "root" || startsWith("root:") -> "root"
        this == "embedded" || startsWith("embedded:") -> "embedded"
        else -> this
    }
}

private fun counterDelta(previous: Long, current: Long): Long {
    return if (current >= previous) current - previous else current
}

private fun pruneTrafficDays(days: Map<String, TrafficBytes>, currentDay: String): Map<String, TrafficBytes> {
    val cutoff = subtractLocalDays(currentDay, TrafficRetentionDays - 1) ?: return days
    return days.filterKeys { day -> day in cutoff..currentDay }
}

private fun subtractLocalDays(day: String, count: Int): String? {
    val parts = day.split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val dayOfMonth = parts[2].toIntOrNull() ?: return null
    val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
        isLenient = false
        clear()
        set(year, month - 1, dayOfMonth, 12, 0, 0)
    }
    if (runCatching { calendar.timeInMillis }.isFailure) return null
    calendar.add(Calendar.DAY_OF_MONTH, -count.coerceAtLeast(0))
    return formatLocalDay(
        year = calendar.get(Calendar.YEAR),
        month = calendar.get(Calendar.MONTH) + 1,
        day = calendar.get(Calendar.DAY_OF_MONTH),
    )
}

private fun formatLocalDay(year: Int, month: Int, day: Int): String {
    return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
}

private fun saturatedAdd(first: Long, second: Long): Long {
    if (first < 0L || second < 0L) return 0L
    return if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second
}

internal const val TrafficLedgerVersion = 1
private const val TrafficRetentionDays = 30
private const val LocalDayBoundarySearchWindowMillis = 72L * 60L * 60L * 1_000L
