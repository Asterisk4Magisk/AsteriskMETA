// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.traffic

import kotlinx.serialization.Serializable
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
    val sessionId: String,
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
): TrafficLedgerReduction {
    val normalizedSample = sample.copy(
        uploadTotalBytes = sample.uploadTotalBytes.coerceAtLeast(0L),
        downloadTotalBytes = sample.downloadTotalBytes.coerceAtLeast(0L),
    )
    val previous = ledger.baseline
    if (previous == null ||
        previous.sessionId != normalizedSample.sessionId ||
        previous.sourceId != normalizedSample.sourceId
    ) {
        return TrafficLedgerReduction(
            ledger = ledger.copy(
                version = TrafficLedgerVersion,
                baseline = normalizedSample.toBaseline(),
                days = pruneTrafficDays(ledger.days, normalizedSample.localDay),
            ),
            changed = true,
        )
    }
    if (previous.uploadTotalBytes == normalizedSample.uploadTotalBytes &&
        previous.downloadTotalBytes == normalizedSample.downloadTotalBytes
    ) {
        return TrafficLedgerReduction(ledger = ledger, changed = false)
    }

    val effectiveDay = maxOf(previous.localDay, normalizedSample.localDay)
    val delta = TrafficBytes(
        upload = monotonicDelta(previous.uploadTotalBytes, normalizedSample.uploadTotalBytes),
        download = monotonicDelta(previous.downloadTotalBytes, normalizedSample.downloadTotalBytes),
    )
    val updatedDays = ledger.days.toMutableMap()
    if (delta.total > 0L) {
        updatedDays[effectiveDay] = updatedDays.getOrDefault(effectiveDay, TrafficBytes()) + delta
    }
    return TrafficLedgerReduction(
        ledger = ledger.copy(
            version = TrafficLedgerVersion,
            baseline = normalizedSample.copy(localDay = effectiveDay).toBaseline(),
            days = pruneTrafficDays(updatedDays, effectiveDay),
        ),
        delta = delta,
        changed = true,
    )
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
        sessionId = sessionId,
        uploadTotalBytes = uploadTotalBytes,
        downloadTotalBytes = downloadTotalBytes,
        observedAtMillis = observedAtMillis,
        localDay = localDay,
        sourceId = sourceId,
    )
}

private fun monotonicDelta(previous: Long, current: Long): Long {
    return if (current >= previous) current - previous else 0L
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
