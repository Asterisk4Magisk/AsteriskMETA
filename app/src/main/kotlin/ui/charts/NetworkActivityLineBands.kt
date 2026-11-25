// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.charts

import kotlin.math.roundToLong

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
 * sample owns the rightmost slot, so new data scrolls in instead of stretching a short buffer across
 * the card. Leading slots are zero rather than NaN, so the zero line spans the whole plot from the
 * first sample on instead of growing in from the right.
 */
internal fun <T> buildNetworkActivityLineBands(
    samples: List<T>,
    slotCount: Int,
    download: (T) -> Long,
    upload: (T) -> Long,
): NetworkActivityLineBands {
    if (samples.isEmpty() || slotCount <= 0) {
        return NetworkActivityLineBands(FloatArray(0), FloatArray(0))
    }
    val visible = minOf(samples.size, slotCount)
    val firstSample = samples.size - visible
    val firstSlot = slotCount - visible
    var peak = 0L
    for (index in firstSample until samples.size) {
        val sample = samples[index]
        peak = maxOf(peak, download(sample), upload(sample))
    }
    // Both curves share the larger peak (plus headroom), so their heights can be compared directly.
    val reference = maxOf(peak, 1L) * NetworkActivityLineHeadroom
    val downloadEdge = FloatArray(slotCount)
    val uploadEdge = FloatArray(slotCount)
    for (offset in 0 until visible) {
        val sample = samples[firstSample + offset]
        downloadEdge[firstSlot + offset] = download(sample).coerceAtLeast(0L) / reference
        uploadEdge[firstSlot + offset] = upload(sample).coerceAtLeast(0L) / reference
    }
    return NetworkActivityLineBands(
        downloadEdge = downloadEdge,
        uploadEdge = uploadEdge,
        scaleBytes = if (peak > 0L) reference.roundToLong() else 0L,
    )
}

private const val NetworkActivityLineHeadroom = 1.1f
