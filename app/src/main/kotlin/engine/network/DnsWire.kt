// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.network

import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * The address families a host record can carry. A record type is what a DNS question asks for, so
 * the resolver keeps the two apart from the answers it reads back.
 */
internal enum class DnsRecordType(val wireType: Int) {
    A(1),
    AAAA(28),
    ;

    val isIpv6: Boolean
        get() = this == AAAA
}

/** One resolved address of a host, kept with the family it was requested for. */
internal data class DnsResolvedAddress(
    val address: String,
    val type: DnsRecordType,
)

/**
 * A plain DNS question for one host and one record type. The wire format is the classic
 * RFC 1035 header and question, which every resolver understands.
 */
internal data class DnsQuestion(
    val host: String,
    val type: DnsRecordType,
) {
    /**
     * Encodes the question as a DNS query message. The transaction id is left at zero because the
     * response is matched on the question itself, not on the id.
     */
    fun toWireMessage(): ByteArray {
        val buffer = ByteArrayOutputStreamLite(DnsQueryHeaderSize + host.length + 8)
        buffer.writeByte(DnsQueryHeaderSize shr 8)
        buffer.writeByte(DnsQueryHeaderSize and 0xFF)
        // Flags: recursion desired, everything else default.
        buffer.writeByte(0x01)
        buffer.writeByte(0x00)
        // Counts: one question, no answer, authority or additional records.
        buffer.writeByte(0x00)
        buffer.writeByte(0x01)
        buffer.writeByte(0x00)
        buffer.writeByte(0x00)
        buffer.writeByte(0x00)
        buffer.writeByte(0x00)
        buffer.writeByte(0x00)
        buffer.writeByte(0x00)
        host.split('.').filter { label -> label.isNotEmpty() }.forEach { label ->
            buffer.writeByte(label.length.coerceAtMost(DnsMaxLabelLength))
            buffer.write(label.toByteArray(Charsets.US_ASCII))
        }
        buffer.writeByte(0)
        buffer.writeByte(type.wireType shr 8)
        buffer.writeByte(type.wireType and 0xFF)
        // Class: IN.
        buffer.writeByte(0x00)
        buffer.writeByte(0x01)
        return buffer.toByteArray()
    }
}

/**
 * Reads the addresses out of a DNS response message. Only the answer section is read, and the
 * name of a record is skipped instead of decoded, because the caller asked for one host and one
 * record type.
 */
internal fun parseDnsResponseAddresses(message: ByteArray, type: DnsRecordType): List<String> {
    if (message.size < DnsQueryHeaderSize) return emptyList()
    val questionCount = message.readUint16At(DnsQuestionCountOffset)
    var offset = DnsQueryHeaderSize
    repeat(questionCount) {
        offset = skipDnsName(message, offset) ?: return emptyList()
        // Type and class of the question.
        offset += 4
    }
    val answerCount = message.readUint16At(DnsAnswerCountOffset)
    val addresses = mutableListOf<String>()
    repeat(answerCount) {
        offset = skipDnsName(message, offset) ?: return addresses
        if (offset + 10 > message.size) return addresses
        val recordType = message.readUint16At(offset)
        val dataLength = message.readUint16At(offset + 8)
        val dataOffset = offset + 10
        if (dataOffset + dataLength > message.size) return addresses
        if (recordType == type.wireType) {
            val address = decodeDnsAddress(message, dataOffset, dataLength, type)
            if (address != null && address !in addresses) addresses += address
        }
        offset = dataOffset + dataLength
    }
    return addresses
}

private fun decodeDnsAddress(
    message: ByteArray,
    offset: Int,
    length: Int,
    type: DnsRecordType,
): String? {
    return when {
        type == DnsRecordType.A && length == 4 -> buildString {
            for (index in 0 until 4) {
                if (index > 0) append('.')
                append(message[offset + index].toInt() and 0xFF)
            }
        }

        type == DnsRecordType.AAAA && length == 16 -> buildString {
            for (index in 0 until 8) {
                if (index > 0) append(':')
                val high = message[offset + index * 2].toInt() and 0xFF
                val low = message[offset + index * 2 + 1].toInt() and 0xFF
                append(Integer.toHexString((high shl 8) or low))
            }
        }

        else -> null
    }
}

/**
 * Skips a domain name in a DNS message, following the compression pointers a resolver may use.
 * Returns the offset right after the name, or null when the message ends too early.
 */
private fun skipDnsName(message: ByteArray, startOffset: Int): Int? {
    var offset = startOffset
    var jumps = 0
    while (offset < message.size) {
        val length = message[offset].toInt() and 0xFF
        when {
            length == 0 -> return offset + 1
            length and 0xC0 == 0xC0 -> {
                // A pointer ends the name; the message continues right after the two pointer bytes.
                if (offset + 1 >= message.size) return null
                jumps++
                if (jumps > DnsMaxNamePointers) return null
                val pointer = ((length and 0x3F) shl 8) or (message[offset + 1].toInt() and 0xFF)
                if (pointer >= message.size) return null
                // The pointed-to name must be well formed, but its end does not matter here.
                if (skipDnsName(message, pointer) == null) return null
                return offset + 2
            }

            length > DnsMaxLabelLength -> return null
            else -> {
                offset += 1 + length
                if (offset > message.size) return null
            }
        }
    }
    return null
}

private fun ByteArray.readUint16At(offset: Int): Int {
    if (offset < 0 || offset + 1 >= size) return 0
    return ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
}

/**
 * A tiny growable byte sink. The DNS message is small, so the buffer never needs to grow much
 * and the implementation stays free of the extra allocation a general stream would make.
 */
private class ByteArrayOutputStreamLite(initialCapacity: Int) {
    private var buffer = ByteArray(initialCapacity.coerceAtLeast(16))
    private var size = 0

    fun writeByte(value: Int) {
        ensureCapacity(size + 1)
        buffer[size++] = value.toByte()
    }

    fun write(bytes: ByteArray) {
        ensureCapacity(size + bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensureCapacity(required: Int) {
        if (required <= buffer.size) return
        var capacity = buffer.size
        while (capacity < required) capacity *= 2
        buffer = buffer.copyOf(capacity)
    }
}

/**
 * The system resolver, used when the configuration names no resolver of its own. The addresses
 * keep the family of the record they came from.
 */
internal fun resolveHostWithSystemResolver(host: String): List<DnsResolvedAddress> {
    val addresses = try {
        InetAddress.getAllByName(host)
    } catch (_: UnknownHostException) {
        emptyArray()
    }
    return addresses.mapNotNull { address ->
        val type = when (address) {
            is Inet6Address -> DnsRecordType.AAAA
            else -> DnsRecordType.A
        }
        DnsResolvedAddress(address = address.hostAddress.orEmpty(), type = type)
    }.filter { resolved -> resolved.address.isNotBlank() }
}

private const val DnsQueryHeaderSize = 12
private const val DnsQuestionCountOffset = 4
private const val DnsAnswerCountOffset = 6
private const val DnsMaxLabelLength = 63
private const val DnsMaxNamePointers = 8
