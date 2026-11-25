// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.charts

import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Normalized amplitudes for the network activity waveform.
 *
 * `download[index]` and `upload[index]` describe the same sample, so both series
 * share one time axis. Every value is in `0f..1f` and is produced by [networkActivityAmplitude],
 * which is also what drives the per-dot color ramp: a dot's height and tone always come from the
 * same normalized speed.
 */
internal class NetworkActivityWaveform(
    val download: FloatArray,
    val upload: FloatArray,
    /** Speed at the far edge of each half in bytes per second, or `0` when the series is idle. */
    val downloadScaleBytes: Long = 0L,
    val uploadScaleBytes: Long = 0L,
) {
    val sampleCount: Int = download.size
}

/**
 * Builds the dotted series on a fixed [slotCount] time axis. Slots before the first sample are
 * zero instead of NaN, so the base row of dots (the zero line) spans the whole card from the first
 * sample on, and only the traffic itself scrolls in from the right.
 */
internal fun <T> buildNetworkActivityWaveform(
    samples: List<T>,
    slotCount: Int,
    download: (T) -> Long,
    upload: (T) -> Long,
): NetworkActivityWaveform {
    if (samples.isEmpty() || slotCount <= 0) return NetworkActivityWaveform(FloatArray(0), FloatArray(0))
    val downloadBytes = LongArray(samples.size) { index -> download(samples[index]).coerceAtLeast(0L) }
    val uploadBytes = LongArray(samples.size) { index -> upload(samples[index]).coerceAtLeast(0L) }
    // Each direction owns its own reference: a slow upload must not shrink because downloads are fast.
    val downloadReference = networkActivityReferenceBytes(downloadBytes)
    val uploadReference = networkActivityReferenceBytes(uploadBytes)
    // The newest sample owns the rightmost slot; a slot index maps back to a sample through this shift.
    val sampleShift = samples.size - slotCount
    return NetworkActivityWaveform(
        download = FloatArray(slotCount) { slot ->
            val index = slot + sampleShift
            if (index < 0) 0f else networkActivityAmplitude(downloadBytes[index], downloadReference)
        },
        upload = FloatArray(slotCount) { slot ->
            val index = slot + sampleShift
            if (index < 0) 0f else networkActivityAmplitude(uploadBytes[index], uploadReference)
        },
        downloadScaleBytes = networkActivityScaleBytes(downloadBytes, downloadReference),
        uploadScaleBytes = networkActivityScaleBytes(uploadBytes, uploadReference),
    )
}

/**
 * Speed that maps to the far edge of a half.
 *
 * The reference follows a high percentile of the window with headroom instead of the window peak,
 * so an isolated spike saturates at the edge rather than flattening every other sample. Idle
 * seconds are excluded from the percentile: a mostly idle window would otherwise pull it down to
 * zero, and every active sample would then saturate at full height. For short windows the index
 * still lands on the largest samples, so startup traffic is scaled to what is actually present.
 * The floor keeps sub-kilobyte speeds measurable.
 */
private fun networkActivityReferenceBytes(values: LongArray): Float {
    val sorted = values.sortedArray()
    // Sorted ascending, so the idle seconds form a prefix that must not count towards the rank.
    val activeStart = sorted.indexOfFirst { value -> value > 0L }
    if (activeStart < 0) return NetworkActivityReferenceFloorBytes
    val activeCount = sorted.size - activeStart
    val percentileIndex = activeStart + ((activeCount - 1) * NetworkActivityReferencePercentile) / 100
    return maxOf(
        sorted[percentileIndex] * NetworkActivityReferenceHeadroom,
        NetworkActivityReferenceFloorBytes,
    )
}

/**
 * Square-root compression keeps low speeds separated from the axis while still ranking peaks above
 * ordinary traffic, which a linear mapping against a per-window maximum cannot do.
 */
private fun networkActivityAmplitude(bytes: Long, referenceBytes: Float): Float {
    if (bytes <= 0L || referenceBytes <= 0f) return 0f
    return sqrt((bytes.toFloat() / referenceBytes).coerceIn(0f, 1f))
}

// The axis label states the edge of the half, not the window peak: a spike above the reference
// saturates at the edge, so the reference is what the edge actually means.
private fun networkActivityScaleBytes(values: LongArray, referenceBytes: Float): Long =
    if (values.any { value -> value > 0L }) referenceBytes.roundToLong() else 0L

private const val NetworkActivityReferencePercentile = 90
private const val NetworkActivityReferenceHeadroom = 1.35f
private const val NetworkActivityReferenceFloorBytes = 4f * 1024f
