// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

/** Low 32 bits of seed-zero XXH64, the content checksum specified by RFC 8878. */
internal fun mihomoZstdChecksum(bytes: ByteArray, offset: Int, length: Int): Int {
    var position = offset
    val end = offset + length
    var hash: Long
    if (length >= 32) {
        var first = XxhPrime1 + XxhPrime2
        var second = XxhPrime2
        var third = 0L
        var fourth = -XxhPrime1
        do {
            first = xxhRound(first, bytes.xxhLong(position))
            second = xxhRound(second, bytes.xxhLong(position + 8))
            third = xxhRound(third, bytes.xxhLong(position + 16))
            fourth = xxhRound(fourth, bytes.xxhLong(position + 24))
            position += 32
        } while (position <= end - 32)
        hash = java.lang.Long.rotateLeft(first, 1) + java.lang.Long.rotateLeft(second, 7) +
            java.lang.Long.rotateLeft(third, 12) + java.lang.Long.rotateLeft(fourth, 18)
        for (lane in longArrayOf(first, second, third, fourth)) {
            hash = (hash xor xxhRound(0L, lane)) * XxhPrime1 + XxhPrime4
        }
    } else {
        hash = XxhPrime5
    }
    hash += length.toLong()
    while (position <= end - 8) {
        hash = java.lang.Long.rotateLeft(hash xor xxhRound(0L, bytes.xxhLong(position)), 27) * XxhPrime1 + XxhPrime4
        position += 8
    }
    if (position <= end - 4) {
        var word = 0L
        repeat(4) { index -> word = word or ((bytes[position + index].toLong() and 0xFFL) shl (index * 8)) }
        hash = java.lang.Long.rotateLeft(hash xor (word * XxhPrime1), 23) * XxhPrime2 + XxhPrime3
        position += 4
    }
    while (position < end) {
        hash = java.lang.Long.rotateLeft(hash xor ((bytes[position++].toLong() and 0xFFL) * XxhPrime5), 11) * XxhPrime1
    }
    hash = (hash xor (hash ushr 33)) * XxhPrime2
    hash = (hash xor (hash ushr 29)) * XxhPrime3
    return (hash xor (hash ushr 32)).toInt()
}

private fun xxhRound(accumulator: Long, word: Long): Long =
    java.lang.Long.rotateLeft(accumulator + word * XxhPrime2, 31) * XxhPrime1

private fun ByteArray.xxhLong(offset: Int): Long {
    var value = 0L
    repeat(8) { index -> value = value or ((this[offset + index].toLong() and 0xFFL) shl (index * 8)) }
    return value
}

private val XxhPrime1 = 0x9E3779B185EBCA87uL.toLong()
private val XxhPrime2 = 0xC2B2AE3D27D4EB4FuL.toLong()
private const val XxhPrime3 = 0x165667B19E3779F9L
private val XxhPrime4 = 0x85EBCA77C2B2AE63uL.toLong()
private const val XxhPrime5 = 0x27D4EB2F165667C5L
