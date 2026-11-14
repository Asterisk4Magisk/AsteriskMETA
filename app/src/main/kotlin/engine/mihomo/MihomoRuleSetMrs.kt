// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import java.math.BigInteger

/** Plain text form of a decoded MRS rule set, matching the payload of a `text` provider. */
internal data class MihomoRuleSetContent(
    val behavior: String,
    val rules: List<String>,
)

/**
 * Reads a Mihomo `mrs` rule set: one zstd frame wrapping the MRS v1 payload.
 *
 * A `mrs` file only carries the behaviors mihomo itself can load, `domain` (a succinct trie of
 * reversed domain labels) and `ipcidr` (merged address ranges), so both are restored here. Domain
 * and suffix entries are folded back into their `+.domain` shorthand and the result is sorted the
 * same way mihomo dumps a rule set, which keeps the reading order stable and comparable.
 */
internal object MihomoRuleSetMrs {
    const val Format: String = "mrs"

    /** Cheap test for a `mrs` file: the declared format, the MRS container or a bare zstd frame. */
    fun matches(format: String, header: ByteArray): Boolean {
        if (format.equals(Format, ignoreCase = true)) return true
        return header.hasPrefix(MrsMagic) || header.hasPrefix(ZstdMagic)
    }

    fun decode(bytes: ByteArray): MihomoRuleSetContent {
        val cursor = ByteCursor(MihomoZstd.decompress(bytes))
        require(cursor.readBytes(MrsMagic.size).contentEquals(MrsMagic)) {
            "MRS header is invalid"
        }
        val behavior = when (val code = cursor.readUnsignedByte()) {
            DomainBehaviorCode -> DomainBehavior
            IpCidrBehaviorCode -> IpCidrBehavior
            else -> throw IllegalArgumentException("MRS behavior $code is not supported")
        }
        cursor.readLong() // Rule count; the payload is the authority once decoded.
        cursor.skipExtra()
        val rules = when (behavior) {
            DomainBehavior -> cursor.readDomainRules()
            else -> cursor.readIpCidrRules()
        }
        return MihomoRuleSetContent(behavior = behavior, rules = rules)
    }
}

/**
 * Domain behavior payload: `leaves` marks terminal nodes, `labelBitmap` is the level-order edge
 * terminator map and `labels` holds the reversed label bytes of every edge.
 */
private fun ByteCursor.readDomainRules(): List<String> {
    require(readUnsignedByte() == DomainSetVersion) { "MRS domain set version is invalid" }
    val leaves = readLongArray()
    val labelBitmap = readLongArray()
    val labels = readBytes(readLength())
    if (leaves.isEmpty() || labelBitmap.isEmpty()) return emptyList()
    val ones = labelBitmap.onesPositions()
    val keys = ArrayList<String>(labels.size)
    walkDomainKeys(
        nodeId = 0,
        bitmapIndex = 0,
        leaves = leaves,
        labelBitmap = labelBitmap,
        labels = labels,
        ones = ones,
        key = DomainKeyBuffer(InitialDomainKeyCapacity),
        into = keys,
    )
    return compactDomainKeys(keys)
}

private fun walkDomainKeys(
    nodeId: Int,
    bitmapIndex: Int,
    leaves: LongArray,
    labelBitmap: LongArray,
    labels: ByteArray,
    ones: IntArray,
    key: DomainKeyBuffer,
    into: MutableList<String>,
) {
    if (leaves.hasBit(nodeId)) {
        into.add(key.decode())
    }
    var index = bitmapIndex
    while (true) {
        if (labelBitmap.hasBit(index)) return
        key.push(labels[index - nodeId])
        val nextNodeId = index - nodeId + 1
        walkDomainKeys(
            nodeId = nextNodeId,
            bitmapIndex = ones[nextNodeId - 1] + 1,
            leaves = leaves,
            labelBitmap = labelBitmap,
            labels = labels,
            ones = ones,
            key = key,
            into = into,
        )
        key.pop()
        index++
    }
}

/** Folds `domain` with its `.domain` sibling into one `+.domain` rule, then sorts the list. */
private fun compactDomainKeys(keys: List<String>): List<String> {
    val ordered = keys.sortedWith(DomainKeyOrder)
    val rules = ArrayList<String>(ordered.size)
    var index = 0
    while (index < ordered.size) {
        val current = ordered[index]
        val next = ordered.getOrNull(index + 1)
        if (next != null && DomainKeyOrder.compare(current, next) == 0) {
            rules.add(DomainSuffixShorthand + current.trimStart('.'))
            index += 2
        } else {
            rules.add(current)
            index++
        }
    }
    rules.sort()
    return rules
}

/** Orders keys by their pattern text, so `domain` and `.domain` stay adjacent. */
private object DomainKeyOrder : Comparator<String> {
    override fun compare(left: String, right: String): Int {
        val leftStart = if (left.startsWith('.')) 1 else 0
        val rightStart = if (right.startsWith('.')) 1 else 0
        val leftLength = left.length - leftStart
        val rightLength = right.length - rightStart
        val shared = minOf(leftLength, rightLength)
        for (index in 0 until shared) {
            val difference = left[leftStart + index].compareTo(right[rightStart + index])
            if (difference != 0) return difference
        }
        return leftLength - rightLength
    }
}

/**
 * Reads the reversed label bytes of one key back into a domain pattern.
 *
 * The trie stores the rune-reversed domain, so each rune sits in the buffer with its bytes reversed
 * and a trailing `+` marks a suffix-only pattern.
 */
private fun DomainKeyBuffer.decode(): String {
    var end = length
    if (end > 0 && bytes[end - 1] == DomainWildcardMarker) end--
    val domain = StringBuilder(end)
    var index = end - 1
    while (index >= 0) {
        val lead = bytes[index].toInt() and ByteMask
        val size = when {
            lead < Utf8SingleByteLimit -> 1
            lead and Utf8TwoByteMask == Utf8TwoByteLead -> 2
            lead and Utf8ThreeByteMask == Utf8ThreeByteLead -> 3
            lead and Utf8FourByteMask == Utf8FourByteLead -> 4
            else -> 1
        }
        if (index - size + 1 < 0) break
        if (size == 1) {
            domain.append(bytes[index].toInt().toChar())
        } else {
            val rune = ByteArray(size)
            for (offset in 0 until size) {
                rune[offset] = bytes[index - offset]
            }
            domain.append(String(rune, Charsets.UTF_8))
        }
        index -= size
    }
    return domain.toString()
}

/** IPCIDR behavior payload: inclusive address ranges, each written as a 16-byte address pair. */
private fun ByteCursor.readIpCidrRules(): List<String> {
    require(readUnsignedByte() == IpCidrSetVersion) { "MRS ipcidr set version is invalid" }
    val rangeCount = readLength()
    val rules = ArrayList<String>(rangeCount)
    repeat(rangeCount) {
        val from = readBytes(AddressSize)
        val to = readBytes(AddressSize)
        appendRangePrefixes(from, to, rules)
    }
    return rules
}

private fun appendRangePrefixes(from: ByteArray, to: ByteArray, into: MutableList<String>) {
    val ipv4 = from.isIpv4Mapped() && to.isIpv4Mapped()
    val addressBits = if (ipv4) Ipv4Bits else Ipv6Bits
    var start = BigInteger(1, if (ipv4) from.copyOfRange(Ipv4Offset, AddressSize) else from)
    val end = BigInteger(1, if (ipv4) to.copyOfRange(Ipv4Offset, AddressSize) else to)
    val lastAddress = BigInteger.ONE.shiftLeft(addressBits).subtract(BigInteger.ONE)
    while (start <= end) {
        var blockBits = if (start.signum() == 0) addressBits else minOf(addressBits, start.lowestSetBit)
        while (blockBits > 0) {
            val blockEnd = start.add(BigInteger.ONE.shiftLeft(blockBits)).subtract(BigInteger.ONE)
            if (blockEnd <= lastAddress && blockEnd <= end) break
            blockBits--
        }
        into.add(formatAddress(start, ipv4) + PrefixSeparator + (addressBits - blockBits))
        start = start.add(BigInteger.ONE.shiftLeft(blockBits))
    }
}

private fun formatAddress(address: BigInteger, ipv4: Boolean): String {
    if (ipv4) {
        val octets = address.toFixedBytes(Ipv4Bytes)
        return octets.joinToString(".") { octet -> (octet.toInt() and ByteMask).toString() }
    }
    val bytes = address.toFixedBytes(AddressSize)
    val groups = IntArray(Ipv6Groups) { index ->
        val high = bytes[index * 2].toInt() and ByteMask
        val low = bytes[index * 2 + 1].toInt() and ByteMask
        (high shl 8) or low
    }
    return groups.formatIpv6()
}

/** RFC 5952 text form: the longest run of at least two zero groups collapses into `::`. */
private fun IntArray.formatIpv6(): String {
    var bestStart = -1
    var bestLength = 0
    var currentStart = -1
    var currentLength = 0
    forEachIndexed { index, group ->
        if (group == 0) {
            if (currentStart < 0) {
                currentStart = index
                currentLength = 0
            }
            currentLength++
            if (currentLength > bestLength) {
                bestLength = currentLength
                bestStart = currentStart
            }
        } else {
            currentStart = -1
            currentLength = 0
        }
    }
    val text = StringBuilder()
    var index = 0
    while (index < size) {
        if (bestLength >= 2 && index == bestStart) {
            text.append("::")
            index += bestLength
            continue
        }
        if (text.isNotEmpty() && !text.endsWith(':')) text.append(':')
        text.append(this[index].toString(16))
        index++
    }
    return text.toString()
}

private fun BigInteger.toFixedBytes(size: Int): ByteArray {
    val raw = toByteArray()
    val fixed = ByteArray(size)
    val copied = minOf(raw.size, size)
    raw.copyInto(fixed, destinationOffset = size - copied, startIndex = raw.size - copied)
    return fixed
}

private fun ByteArray.isIpv4Mapped(): Boolean {
    for (index in 0 until Ipv4Offset - 2) {
        if (this[index] != 0.toByte()) return false
    }
    return this[Ipv4Offset - 2] == 0xFF.toByte() && this[Ipv4Offset - 1] == 0xFF.toByte()
}

private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (index in prefix.indices) {
        if (this[index] != prefix[index]) return false
    }
    return true
}

private fun LongArray.hasBit(index: Int): Boolean {
    return (this[index ushr LongShift] ushr (index and LongMask)) and 1L != 0L
}

private fun LongArray.onesPositions(): IntArray {
    val bitCount = size * Long.SIZE_BITS
    var ones = 0
    for (index in 0 until bitCount) {
        if (hasBit(index)) ones++
    }
    val positions = IntArray(ones)
    var cursor = 0
    for (index in 0 until bitCount) {
        if (hasBit(index)) positions[cursor++] = index
    }
    return positions
}

private class DomainKeyBuffer(initialCapacity: Int) {
    var bytes: ByteArray = ByteArray(initialCapacity)
        private set
    var length: Int = 0
        private set

    fun push(value: Byte) {
        if (length == bytes.size) bytes = bytes.copyOf(length * 2)
        bytes[length++] = value
    }

    fun pop() {
        if (length > 0) length--
    }
}

/** Big-endian cursor over the inflated MRS payload. */
private class ByteCursor(private val bytes: ByteArray) {
    private var position = 0

    val remaining: Int get() = bytes.size - position

    fun readUnsignedByte(): Int {
        require(position < bytes.size) { "MRS payload is truncated" }
        return bytes[position++].toInt() and ByteMask
    }

    fun readLong(): Long {
        require(remaining >= Long.SIZE_BITS / Byte.SIZE_BITS) { "MRS payload is truncated" }
        var value = 0L
        repeat(Long.SIZE_BITS / Byte.SIZE_BITS) {
            value = (value shl 8) or (bytes[position++].toLong() and 0xFFL)
        }
        return value
    }

    fun readLength(): Int {
        val length = readLong()
        require(length in 0..Int.MAX_VALUE.toLong()) { "MRS length is invalid" }
        val count = length.toInt()
        require(count <= remaining) { "MRS length exceeds the payload" }
        return count
    }

    fun readBytes(size: Int): ByteArray {
        require(size >= 0 && remaining >= size) { "MRS payload is truncated" }
        val slice = bytes.copyOfRange(position, position + size)
        position += size
        return slice
    }

    fun readLongArray(): LongArray {
        val count = readLength()
        require(count <= remaining / (Long.SIZE_BITS / Byte.SIZE_BITS)) { "MRS length exceeds the payload" }
        return LongArray(count) { readLong() }
    }

    fun skipExtra() {
        skip(readLength())
    }

    private fun skip(size: Int) {
        require(size >= 0 && remaining >= size) { "MRS payload is truncated" }
        position += size
    }
}

private const val DomainBehavior = "domain"
private const val IpCidrBehavior = "ipcidr"
private const val DomainBehaviorCode = 0
private const val IpCidrBehaviorCode = 1
private const val DomainSetVersion = 1
private const val IpCidrSetVersion = 1
private const val AddressSize = 16
private const val Ipv4Bytes = 4
private const val Ipv4Bits = 32
private const val Ipv6Bits = 128
private const val Ipv4Offset = 12
private const val Ipv6Groups = 8
private const val ByteMask = 0xFF
private const val PrefixSeparator = "/"
private const val DomainSuffixShorthand = "+."
private const val InitialDomainKeyCapacity = 64
private const val LongShift = 6
private const val LongMask = 63

private const val Utf8SingleByteLimit = 0x80
private const val Utf8TwoByteMask = 0xE0
private const val Utf8TwoByteLead = 0xC0
private const val Utf8ThreeByteMask = 0xF0
private const val Utf8ThreeByteLead = 0xE0
private const val Utf8FourByteMask = 0xF8
private const val Utf8FourByteLead = 0xF0

private val DomainWildcardMarker: Byte = '+'.code.toByte()
private val MrsMagic = byteArrayOf(
    'M'.code.toByte(),
    'R'.code.toByte(),
    'S'.code.toByte(),
    1.toByte(),
)
private val ZstdMagic = byteArrayOf(0x28.toByte(), 0xB5.toByte(), 0x2F.toByte(), 0xFD.toByte())
