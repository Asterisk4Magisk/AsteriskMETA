// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.home

import engine.mihomo.runtime.MihomoTrafficSample
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Normalized amplitudes for the home network activity waveform.
 *
 * `download[index]` and `upload[index]` describe the same `MihomoTrafficSample`, so both series
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

internal fun buildNetworkActivityWaveform(samples: List<MihomoTrafficSample>): NetworkActivityWaveform {
    if (samples.isEmpty()) return NetworkActivityWaveform(FloatArray(0), FloatArray(0))
    val downloadBytes = LongArray(samples.size) { index -> samples[index].down.coerceAtLeast(0L) }
    val uploadBytes = LongArray(samples.size) { index -> samples[index].up.coerceAtLeast(0L) }
    // Each direction owns its own reference: a slow upload must not shrink because downloads are fast.
    val downloadReference = networkActivityReferenceBytes(downloadBytes)
    val uploadReference = networkActivityReferenceBytes(uploadBytes)
    return NetworkActivityWaveform(
        download = FloatArray(samples.size) { index ->
            networkActivityAmplitude(downloadBytes[index], downloadReference)
        },
        upload = FloatArray(samples.size) { index ->
            networkActivityAmplitude(uploadBytes[index], uploadReference)
        },
        downloadScaleBytes = networkActivityScaleBytes(downloadBytes, downloadReference),
        uploadScaleBytes = networkActivityScaleBytes(uploadBytes, uploadReference),
    )
}

/**
 * Two line-chart series sharing one scale: each edge is its own speed divided by [scaleBytes], so the
 * download line and the upload line are directly comparable on the same axis.
 */
internal class NetworkActivityLineBands(
    val downloadEdge: FloatArray,
    val uploadEdge: FloatArray,
    /** Speed at the top of the plot in bytes per second, or `0` when both series are idle. */
    val scaleBytes: Long = 0L,
) {
    val sampleCount: Int = downloadEdge.size
}

/**
 * Builds the line series on the same fixed [slotCount] time axis as the dotted style: the newest
 * sample owns the rightmost slot and leading slots stay NaN, the painters' "no sample" sentinel, so
 * new data scrolls in instead of stretching a short buffer across the card.
 */
internal fun buildNetworkActivityLineBands(
    samples: List<MihomoTrafficSample>,
    slotCount: Int,
): NetworkActivityLineBands {
    if (samples.isEmpty() || slotCount <= 0) {
        return NetworkActivityLineBands(FloatArray(0), FloatArray(0))
    }
    val visible = minOf(samples.size, slotCount)
    val firstSample = samples.size - visible
    val firstSlot = slotCount - visible
    var peak = 0L
    for (index in firstSample until samples.size) {
        peak = maxOf(peak, samples[index].down, samples[index].up)
    }
    // Both curves share the larger peak (plus headroom), so their heights can be compared directly.
    val reference = maxOf(peak, 1L) * NetworkActivityLineHeadroom
    val downloadEdge = FloatArray(slotCount) { Float.NaN }
    val uploadEdge = FloatArray(slotCount) { Float.NaN }
    for (offset in 0 until visible) {
        val sample = samples[firstSample + offset]
        downloadEdge[firstSlot + offset] = sample.down.coerceAtLeast(0L) / reference
        uploadEdge[firstSlot + offset] = sample.up.coerceAtLeast(0L) / reference
    }
    return NetworkActivityLineBands(
        downloadEdge = downloadEdge,
        uploadEdge = uploadEdge,
        scaleBytes = if (peak > 0L) reference.roundToLong() else 0L,
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
internal fun networkActivityReferenceBytes(values: LongArray): Float {
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
internal fun networkActivityAmplitude(bytes: Long, referenceBytes: Float): Float {
    if (bytes <= 0L || referenceBytes <= 0f) return 0f
    return sqrt((bytes.toFloat() / referenceBytes).coerceIn(0f, 1f))
}

// The axis label states the edge of the half, not the window peak: a spike above the reference
// saturates at the edge, so the reference is what the edge actually means.
private fun networkActivityScaleBytes(values: LongArray, referenceBytes: Float): Long =
    if (values.any { value -> value > 0L }) referenceBytes.roundToLong() else 0L

private const val NetworkActivityReferencePercentile = 90
private const val NetworkActivityLineHeadroom = 1.1f
private const val NetworkActivityReferenceHeadroom = 1.35f
private const val NetworkActivityReferenceFloorBytes = 4f * 1024f
