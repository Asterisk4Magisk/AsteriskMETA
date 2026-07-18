// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.resource

internal data class ProcessStat(
    val pid: Int,
    val processTicks: Long,
    val startTimeTicks: Long,
)

internal data class ProcessTickSnapshot(
    val pid: Int,
    val processTicks: Long,
    val totalCpuTicks: Long,
    val processorCount: Int,
    val startTimeTicks: Long,
)

internal data class ProcessStatsSample(
    val timestampMillis: Long,
    val cpuPercent: Double?,
    val memoryBytes: Long?,
)

internal data class ProcessStatsHistory(
    val fifteenMinutes: List<ProcessStatsSample>,
    val oneHour: List<ProcessStatsSample>,
)

internal fun parseTotalCpuTicks(procStat: String): Long? {
    val aggregate = procStat.lineSequence()
        .map(String::trim)
        .firstOrNull { line -> line.startsWith("cpu ") }
        ?: return null
    val values = aggregate.split(WhitespaceRegex)
        .drop(1)
        .take(AggregateCpuFieldCount)
        .map { value -> value.toLongOrNull() ?: return null }
    if (values.size != AggregateCpuFieldCount) return null
    return values.fold(0L) { total, value ->
        if (value < 0L || Long.MAX_VALUE - total < value) return null
        total + value
    }
}

internal fun parseProcessStat(procStat: String): ProcessStat? {
    val openParenthesis = procStat.indexOf('(')
    val closeParenthesis = procStat.lastIndexOf(')')
    if (openParenthesis <= 0 || closeParenthesis <= openParenthesis) return null
    val pid = procStat.substring(0, openParenthesis).trim().toIntOrNull() ?: return null
    val fields = procStat.substring(closeParenthesis + 1)
        .trim()
        .split(WhitespaceRegex)
    val userTicks = fields.getOrNull(ProcessUserTicksIndex)?.toLongOrNull() ?: return null
    val systemTicks = fields.getOrNull(ProcessSystemTicksIndex)?.toLongOrNull() ?: return null
    val startTimeTicks = fields.getOrNull(ProcessStartTimeTicksIndex)?.toLongOrNull() ?: return null
    if (userTicks < 0L || systemTicks < 0L || startTimeTicks < 0L) return null
    if (Long.MAX_VALUE - userTicks < systemTicks) return null
    return ProcessStat(
        pid = pid,
        processTicks = userTicks + systemTicks,
        startTimeTicks = startTimeTicks,
    )
}

internal fun calculateProcessCpuPercent(
    previous: ProcessTickSnapshot?,
    current: ProcessTickSnapshot,
): Double? {
    if (previous == null ||
        previous.pid != current.pid ||
        previous.startTimeTicks != current.startTimeTicks
    ) {
        return null
    }
    val processDelta = current.processTicks - previous.processTicks
    val totalDelta = current.totalCpuTicks - previous.totalCpuTicks
    if (processDelta < 0L || totalDelta <= 0L || current.processorCount <= 0) return null
    val capacity = current.processorCount * 100.0
    return (processDelta.toDouble() * capacity / totalDelta.toDouble())
        .coerceIn(0.0, capacity)
}

internal fun buildAppProcessTickSnapshot(
    pid: Int,
    processCpuMillis: Long,
    elapsedRealtimeMillis: Long,
    processorCount: Int,
    processStartElapsedRealtimeMillis: Long,
): ProcessTickSnapshot? {
    if (pid <= 0 ||
        processCpuMillis < 0L ||
        elapsedRealtimeMillis < 0L ||
        processorCount <= 0 ||
        processStartElapsedRealtimeMillis < 0L
    ) {
        return null
    }
    val aggregateElapsedMillis = runCatching {
        Math.multiplyExact(elapsedRealtimeMillis, processorCount.toLong())
    }.getOrNull() ?: return null
    return ProcessTickSnapshot(
        pid = pid,
        processTicks = processCpuMillis,
        totalCpuTicks = aggregateElapsedMillis,
        processorCount = processorCount,
        startTimeTicks = processStartElapsedRealtimeMillis,
    )
}

internal fun appendProcessStatsSample(
    previousHour: List<ProcessStatsSample>,
    sample: ProcessStatsSample,
): ProcessStatsHistory {
    val oneHourStart = sample.timestampMillis - OneHourMillis
    val firstInWindow = previousHour.indexOfFirst { candidate ->
        candidate.timestampMillis >= oneHourStart
    }.let { index -> if (index >= 0) index else previousHour.size }
    val firstRetained = maxOf(
        firstInWindow,
        previousHour.size - (MaxOneHourSamples - 1),
    )
    val oneHour = ArrayList<ProcessStatsSample>(
        (previousHour.size - firstRetained + 1).coerceAtMost(MaxOneHourSamples),
    )
    for (index in firstRetained until previousHour.size) {
        val candidate = previousHour[index]
        if (candidate.timestampMillis <= sample.timestampMillis) {
            oneHour += candidate
        }
    }
    oneHour += sample

    val fifteenMinuteStart = sample.timestampMillis - FifteenMinutesMillis
    val firstFifteenMinuteSample = oneHour.indexOfFirst { candidate ->
        candidate.timestampMillis >= fifteenMinuteStart
    }.let { index -> if (index >= 0) index else oneHour.size }
    val firstFifteenMinuteRetained = maxOf(
        firstFifteenMinuteSample,
        oneHour.size - MaxFifteenMinuteSamples,
    )
    val fifteenMinutes = ArrayList<ProcessStatsSample>(oneHour.size - firstFifteenMinuteRetained)
    for (index in firstFifteenMinuteRetained until oneHour.size) {
        fifteenMinutes += oneHour[index]
    }
    return ProcessStatsHistory(
        fifteenMinutes = fifteenMinutes,
        oneHour = oneHour,
    )
}

private val WhitespaceRegex = Regex("\\s+")
private const val AggregateCpuFieldCount = 8
private const val ProcessUserTicksIndex = 11
private const val ProcessSystemTicksIndex = 12
private const val ProcessStartTimeTicksIndex = 19
private const val FifteenMinutesMillis = 15L * 60L * 1_000L
private const val OneHourMillis = 60L * 60L * 1_000L
private const val MaxFifteenMinuteSamples = 901
private const val MaxOneHourSamples = 3_601
