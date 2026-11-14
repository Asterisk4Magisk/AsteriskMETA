// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

/**
 * Decompressor for the zstd frames a Mihomo `mrs` rule set is wrapped in.
 *
 * Only decompression is implemented, and only what a rule set can contain: the frame and block
 * levels of RFC 8878, raw/RLE/Huffman literals and FSE-coded sequences. Dictionaries are rejected;
 * the content checksum is skipped rather than verified, since the rule set is validated by decoding
 * it. The structure follows the reference decoder closely so every bit-level rule stays checkable
 * against it.
 */
internal object MihomoZstd {
    /** Decompresses every frame in [input] and appends their content in order. */
    fun decompress(input: ByteArray): ByteArray {
        val output = ZstdOutput(input.size * OutputGrowthEstimate)
        var position = 0
        while (input.size - position >= MagicSize) {
            val magic = input.readIntLe(position)
            if (magic == FrameMagic) {
                position = decompressFrame(input, position + MagicSize, output)
                continue
            }
            if (magic and SkippableMagicMask == SkippableMagic) {
                require(input.size - position >= MagicSize + IntBytes) { ZstdTruncated }
                position += MagicSize + IntBytes + input.readIntLe(position + MagicSize)
                continue
            }
            require(position == 0) { "Unsupported zstd frame" }
            break
        }
        require(position > 0) { "Missing zstd frame" }
        return output.toByteArray()
    }

    private fun decompressFrame(input: ByteArray, start: Int, output: ZstdOutput): Int {
        require(input.size - start >= 1) { ZstdTruncated }
        var position = start
        val descriptor = input[position++].toInt() and ByteMask
        val contentSizeFlag = descriptor ushr 6
        val singleSegment = (descriptor ushr 5) and 1 == 1
        val hasChecksum = (descriptor ushr 2) and 1 == 1
        val dictionaryIdFlag = descriptor and 3
        // The window descriptor only bounds the back-reference distance; match distances are
        // validated against the produced output instead.
        if (!singleSegment) position++
        val dictionaryIdSize = when (dictionaryIdFlag) {
            0 -> 0
            1 -> 1
            2 -> 2
            else -> IntBytes
        }
        if (dictionaryIdSize > 0) {
            require(readVariable(input, position, dictionaryIdSize) == 0L) {
                "Zstd dictionaries are not supported"
            }
            position += dictionaryIdSize
        }
        position += when (contentSizeFlag) {
            0 -> if (singleSegment) 1 else 0
            1 -> 2
            2 -> IntBytes
            else -> LongBytes
        }

        val blockState = BlockState()
        while (true) {
            require(input.size - position >= BlockHeaderSize) { ZstdTruncated }
            val header = input.readIntLe24(position)
            position += BlockHeaderSize
            val isLastBlock = header and 1 == 1
            val blockType = (header ushr 1) and 3
            val blockSize = header ushr 3
            when (blockType) {
                BlockTypeRaw -> {
                    require(input.size - position >= blockSize) { ZstdTruncated }
                    output.append(input, position, blockSize)
                    position += blockSize
                }

                BlockTypeRle -> {
                    require(input.size - position >= 1) { ZstdTruncated }
                    output.appendRepeated(input[position], blockSize)
                    position++
                }

                BlockTypeCompressed -> {
                    require(input.size - position >= blockSize) { ZstdTruncated }
                    decodeCompressedBlock(input, position, position + blockSize, output, blockState)
                    position += blockSize
                }

                else -> throw IllegalArgumentException("Reserved zstd block type")
            }
            if (isLastBlock) break
        }
        if (hasChecksum) position += IntBytes
        return position
    }

    private fun decodeCompressedBlock(
        input: ByteArray,
        start: Int,
        end: Int,
        output: ZstdOutput,
        state: BlockState,
    ) {
        val literals = readLiterals(input, start, end, state)
        decodeSequences(input, start + literals.consumed, end, literals.bytes, output, state)
    }

    // ---------------------------------------------------------------- literals

    private class Literals(val bytes: ByteArray, val consumed: Int)

    private fun readLiterals(input: ByteArray, start: Int, end: Int, state: BlockState): Literals {
        require(end - start >= 1) { ZstdTruncated }
        val first = input[start].toInt() and ByteMask
        val blockType = first and 3
        val sizeFormat = (first ushr 2) and 3
        return when (blockType) {
            LiteralsRaw, LiteralsRle -> {
                val headerSize = when (sizeFormat) {
                    0, 2 -> 1
                    1 -> 2
                    else -> 3
                }
                val size = when (headerSize) {
                    1 -> first ushr 3
                    2 -> input.readIntLe16(start) ushr 4
                    else -> input.readIntLe24(start) ushr 4
                }
                if (blockType == LiteralsRaw) {
                    require(end - start >= headerSize + size) { ZstdTruncated }
                    Literals(
                        bytes = input.copyOfRange(start + headerSize, start + headerSize + size),
                        consumed = headerSize + size,
                    )
                } else {
                    require(end - start >= headerSize + 1) { ZstdTruncated }
                    Literals(
                        bytes = ByteArray(size) { input[start + headerSize] },
                        consumed = headerSize + 1,
                    )
                }
            }

            else -> {
                val fourStreams = sizeFormat != 0
                val headerSize = when (sizeFormat) {
                    0, 1 -> 3
                    2 -> IntBytes
                    else -> 5
                }
                require(end - start >= headerSize) { ZstdTruncated }
                val header = input.readIntLe(start)
                val regeneratedSize = when (sizeFormat) {
                    0, 1 -> (header ushr 4) and 0x3FF
                    2 -> (header ushr 4) and 0x3FFF
                    else -> (header ushr 4) and 0x3FFFF
                }
                val compressedSize = when (sizeFormat) {
                    0, 1 -> (header ushr 14) and 0x3FF
                    2 -> header ushr 18
                    else -> (header ushr 22) + ((input[start + 4].toInt() and ByteMask) shl 10)
                }
                require(end - start >= headerSize + compressedSize) { ZstdTruncated }
                val payloadStart = start + headerSize
                val payloadEnd = payloadStart + compressedSize
                val table: HuffmanTable
                val treeSize: Int
                if (blockType == LiteralsCompressed) {
                    val description = readHuffmanTable(input, payloadStart, payloadEnd)
                    table = description.table
                    treeSize = description.consumed
                    state.huffman = table
                } else {
                    // Treeless literals reuse the previous block's tree, so the payload starts at once.
                    table = requireNotNull(state.huffman) { "Treeless literals without a Huffman table" }
                    treeSize = 0
                }
                val streamStart = payloadStart + treeSize
                val bytes = if (fourStreams) {
                    decodeHuffmanFourStreams(table, input, streamStart, payloadEnd, regeneratedSize)
                } else {
                    decodeHuffmanOneStream(table, input, streamStart, payloadEnd, regeneratedSize)
                }
                Literals(bytes = bytes, consumed = headerSize + compressedSize)
            }
        }
    }

    private class HuffmanTable(
        val tableLog: Int,
        val symbols: ByteArray,
        val nbBits: ByteArray,
    )

    private class HuffmanTableDescription(val table: HuffmanTable, val consumed: Int)

    private fun readHuffmanTable(input: ByteArray, start: Int, end: Int): HuffmanTableDescription {
        val weights = ShortArray(MaxHuffmanSymbols)
        var weightCount: Int
        var headerSize: Int
        val first = input[start].toInt() and ByteMask
        if (first >= HuffmanDirectWeightsHeader) {
            weightCount = first - HuffmanDirectWeightsHeader + 1
            val packedBytes = (weightCount + 1) / 2
            require(end - start >= 1 + packedBytes) { ZstdTruncated }
            for (index in 0 until weightCount) {
                val packed = input[start + 1 + index / 2].toInt() and ByteMask
                weights[index] = (if (index % 2 == 0) packed ushr 4 else packed and 0xF).toShort()
            }
            headerSize = 1 + packedBytes
        } else {
            require(first > 0) { "Invalid Huffman weights header" }
            weightCount = decodeFseBlock(input, start + 1, start + 1 + first, weights, MaxHuffmanSymbols - 1)
            require(weightCount > 0) { "Invalid Huffman weights" }
            headerSize = 1 + first
        }

        val rankVal = IntArray(MaxHuffmanTableLog + 2)
        var weightTotal = 0
        for (index in 0 until weightCount) {
            val weight = weights[index].toInt()
            require(weight <= MaxHuffmanTableLog) { "Invalid Huffman weight" }
            rankVal[weight]++
            weightTotal += (1 shl weight) shr 1
        }
        require(weightTotal > 0) { "Invalid Huffman weights" }
        val tableLog = highBit(weightTotal) + 1
        require(tableLog <= MaxHuffmanTableLog) { "Invalid Huffman table log" }
        val rest = (1 shl tableLog) - weightTotal
        require(rest > 0 && Integer.bitCount(rest) == 1) { "Invalid Huffman weights total" }
        val lastWeight = highBit(rest) + 1
        weights[weightCount] = lastWeight.toShort()
        rankVal[lastWeight]++
        weightCount++

        val tableSize = 1 shl tableLog
        val rankStart = IntArray(MaxHuffmanTableLog + 2)
        var nextRankStart = 0
        for (weight in 0..tableLog) {
            rankStart[weight] = nextRankStart
            nextRankStart += rankVal[weight]
        }
        val orderedSymbols = ByteArray(weightCount)
        val cursors = rankStart.copyOf()
        for (symbol in 0 until weightCount) {
            val weight = weights[symbol].toInt()
            orderedSymbols[cursors[weight]++] = symbol.toByte()
        }

        val symbols = ByteArray(tableSize)
        val nbBits = ByteArray(tableSize)
        var symbolCursor = rankVal[0]
        var tableCursor = 0
        for (weight in 1..tableLog) {
            val symbolCount = rankVal[weight]
            val runLength = (1 shl weight) shr 1
            val bits = (tableLog + 1 - weight).toByte()
            for (index in 0 until symbolCount) {
                val symbol = orderedSymbols[symbolCursor + index]
                symbols.fill(symbol, tableCursor, tableCursor + runLength)
                nbBits.fill(bits, tableCursor, tableCursor + runLength)
                tableCursor += runLength
            }
            symbolCursor += symbolCount
        }
        require(tableCursor == tableSize) { "Invalid Huffman table" }
        return HuffmanTableDescription(
            table = HuffmanTable(tableLog = tableLog, symbols = symbols, nbBits = nbBits),
            consumed = headerSize,
        )
    }

    private fun decodeHuffmanOneStream(
        table: HuffmanTable,
        input: ByteArray,
        start: Int,
        end: Int,
        size: Int,
    ): ByteArray {
        val output = ByteArray(size)
        val reader = BackwardBitReader(input, start, end)
        for (index in 0 until size) {
            output[index] = table.nextSymbol(reader)
        }
        return output
    }

    private fun decodeHuffmanFourStreams(
        table: HuffmanTable,
        input: ByteArray,
        start: Int,
        end: Int,
        size: Int,
    ): ByteArray {
        require(size >= MinimumFourStreamSize) { "Literals too small for four streams" }
        require(end - start >= FourStreamJumpTableSize + 4) { ZstdTruncated }
        val firstLength = input.readIntLe16(start)
        val secondLength = input.readIntLe16(start + 2)
        val thirdLength = input.readIntLe16(start + 4)
        val streamsStart = start + FourStreamJumpTableSize
        val fourthLength = end - streamsStart - firstLength - secondLength - thirdLength
        require(fourthLength > 0) { ZstdTruncated }
        val segmentSize = (size + 3) / 4
        require(segmentSize * 3 <= size) { "Invalid four-stream literals" }
        val output = ByteArray(size)
        val starts = intArrayOf(
            streamsStart,
            streamsStart + firstLength,
            streamsStart + firstLength + secondLength,
            streamsStart + firstLength + secondLength + thirdLength,
        )
        val lengths = intArrayOf(firstLength, secondLength, thirdLength, fourthLength)
        for (stream in 0 until 4) {
            val from = stream * segmentSize
            val to = if (stream == 3) size else from + segmentSize
            if (from >= to) continue
            val reader = BackwardBitReader(input, starts[stream], starts[stream] + lengths[stream])
            for (index in from until to) {
                output[index] = table.nextSymbol(reader)
            }
        }
        return output
    }

    private fun HuffmanTable.nextSymbol(reader: BackwardBitReader): Byte {
        val index = reader.peekBits(tableLog)
        reader.skipBits(nbBits[index].toInt())
        reader.load()
        return symbols[index]
    }

    // --------------------------------------------------------------- sequences

    private fun decodeSequences(
        input: ByteArray,
        start: Int,
        end: Int,
        literals: ByteArray,
        output: ZstdOutput,
        state: BlockState,
    ) {
        require(end - start >= 1) { ZstdTruncated }
        var position = start
        val first = input[position++].toInt() and ByteMask
        var sequenceCount = first
        if (first > 0x7F) {
            if (first == 0xFF) {
                require(end - position >= 2) { ZstdTruncated }
                sequenceCount = input.readIntLe16(position) + LongSequenceOffset
                position += 2
            } else {
                require(end - position >= 1) { ZstdTruncated }
                sequenceCount = ((first - 0x80) shl 8) + (input[position++].toInt() and ByteMask)
            }
        }
        if (sequenceCount == 0) {
            output.append(literals, 0, literals.size)
            return
        }

        require(end - position >= 1) { ZstdTruncated }
        val modes = input[position++].toInt() and ByteMask
        require(modes and 3 == 0) { "Reserved sequence mode bits are set" }

        val literalsLengthRead = readSequenceTable(
            input = input,
            start = position,
            end = end,
            mode = modes ushr 6,
            maxSymbol = MaxLiteralsLengthCode,
            maxTableLog = LiteralsLengthTableLog,
            defaultDistribution = LiteralsLengthDefaultNorm,
            defaultTableLog = LiteralsLengthDefaultNormLog,
            extraBits = LiteralsLengthExtraBits,
            base = literalsLengthBase,
            previous = state.literalsLengthTable,
        )
        position += literalsLengthRead.consumed
        val offsetRead = readSequenceTable(
            input = input,
            start = position,
            end = end,
            mode = (modes ushr 4) and 3,
            maxSymbol = MaxOffsetCode,
            maxTableLog = OffsetTableLog,
            defaultDistribution = OffsetDefaultNorm,
            defaultTableLog = OffsetDefaultNormLog,
            extraBits = OffsetExtraBits,
            base = offsetBase,
            previous = state.offsetTable,
        )
        position += offsetRead.consumed
        val matchLengthRead = readSequenceTable(
            input = input,
            start = position,
            end = end,
            mode = (modes ushr 2) and 3,
            maxSymbol = MaxMatchLengthCode,
            maxTableLog = MatchLengthTableLog,
            defaultDistribution = MatchLengthDefaultNorm,
            defaultTableLog = MatchLengthDefaultNormLog,
            extraBits = MatchLengthExtraBits,
            base = matchLengthBase,
            previous = state.matchLengthTable,
        )
        position += matchLengthRead.consumed

        val literalsLengthTable = literalsLengthRead.table
        val offsetTable = offsetRead.table
        val matchLengthTable = matchLengthRead.table
        state.literalsLengthTable = literalsLengthTable
        state.offsetTable = offsetTable
        state.matchLengthTable = matchLengthTable

        val reader = BackwardBitReader(input, position, end)
        var literalsLengthState = reader.readBits(literalsLengthTable.tableLog)
        var offsetState = reader.readBits(offsetTable.tableLog)
        var matchLengthState = reader.readBits(matchLengthTable.tableLog)

        var literalsPosition = 0
        for (index in 0 until sequenceCount) {
            reader.load()
            val literalsLengthSymbol = literalsLengthTable.symbol[literalsLengthState].toInt()
            val offsetSymbol = offsetTable.symbol[offsetState].toInt()
            val matchLengthSymbol = matchLengthTable.symbol[matchLengthState].toInt()
            val literalsLengthExtra = literalsLengthTable.extraBits[literalsLengthSymbol]
            val matchLengthExtra = matchLengthTable.extraBits[matchLengthSymbol]
            val offsetExtra = offsetTable.extraBits[offsetSymbol]
            val literalsLengthBaseline = literalsLengthTable.base[literalsLengthSymbol]

            val offset: Int
            if (offsetExtra > 1) {
                offset = offsetTable.base[offsetSymbol] + reader.readBits(offsetExtra)
                state.repeatOffsets[2] = state.repeatOffsets[1]
                state.repeatOffsets[1] = state.repeatOffsets[0]
                state.repeatOffsets[0] = offset
            } else {
                // Offset values 1..3 are repeat codes, shifted by one when there are no literals.
                val shift = if (literalsLengthBaseline == 0) 1 else 0
                if (offsetExtra == 0) {
                    offset = state.repeatOffsets[shift]
                    state.repeatOffsets[1] = state.repeatOffsets[1 - shift]
                    state.repeatOffsets[0] = offset
                } else {
                    val repeatCode = offsetTable.base[offsetSymbol] + shift + reader.readBits(1)
                    val resolved = if (repeatCode == 3) {
                        state.repeatOffsets[0] - 1
                    } else {
                        state.repeatOffsets[repeatCode]
                    }
                    require(resolved > 0) { "Invalid repeat offset" }
                    if (repeatCode != 1) state.repeatOffsets[2] = state.repeatOffsets[1]
                    state.repeatOffsets[1] = state.repeatOffsets[0]
                    state.repeatOffsets[0] = resolved
                    offset = resolved
                }
            }

            var matchLength = matchLengthTable.base[matchLengthSymbol]
            if (matchLengthExtra > 0) matchLength += reader.readBits(matchLengthExtra)
            var literalsLength = literalsLengthBaseline
            if (literalsLengthExtra > 0) literalsLength += reader.readBits(literalsLengthExtra)

            if (index + 1 < sequenceCount) {
                literalsLengthState = literalsLengthTable.advance(literalsLengthState, reader)
                matchLengthState = matchLengthTable.advance(matchLengthState, reader)
                offsetState = offsetTable.advance(offsetState, reader)
                reader.load()
            }

            output.append(literals, literalsPosition, literalsLength)
            literalsPosition += literalsLength
            output.copyWithin(offset, matchLength)
        }
        output.append(literals, literalsPosition, literals.size - literalsPosition)
    }

    private class SequenceTableRead(val table: SequenceTable, val consumed: Int)

    /** Decoding table of one sequence symbol family, with the code information per symbol. */
    private class SequenceTable(
        val tableLog: Int,
        val symbol: ByteArray,
        val nbBits: ByteArray,
        val newState: IntArray,
        val base: IntArray,
        val extraBits: IntArray,
    ) {
        fun advance(state: Int, reader: BackwardBitReader): Int {
            return newState[state] + reader.readBits(nbBits[state].toInt())
        }
    }

    private fun readSequenceTable(
        input: ByteArray,
        start: Int,
        end: Int,
        mode: Int,
        maxSymbol: Int,
        maxTableLog: Int,
        defaultDistribution: IntArray,
        defaultTableLog: Int,
        extraBits: IntArray,
        base: IntArray,
        previous: SequenceTable?,
    ): SequenceTableRead {
        return when (mode) {
            SequenceModePredefined -> SequenceTableRead(
                table = buildSequenceTable(
                    counts = defaultDistribution,
                    // A default distribution covers only the symbols it defines.
                    maxSymbol = defaultDistribution.size - 1,
                    tableLog = defaultTableLog,
                    base = base,
                    extraBits = extraBits,
                ),
                consumed = 0,
            )

            SequenceModeRle -> {
                require(end - start >= 1) { ZstdTruncated }
                val symbol = input[start].toInt() and ByteMask
                require(symbol <= maxSymbol) { "Invalid sequence symbol" }
                val counts = IntArray(maxSymbol + 1)
                counts[symbol] = 1
                SequenceTableRead(
                    table = buildSequenceTable(counts, maxSymbol, 0, base, extraBits),
                    consumed = 1,
                )
            }

            SequenceModeFse -> {
                val counts = readNormalizedCounts(input, start, end, maxSymbol)
                require(counts.tableLog <= maxTableLog) { "Sequence table log is too large" }
                SequenceTableRead(
                    table = buildSequenceTable(
                        counts = counts.counts,
                        maxSymbol = counts.maxSymbol,
                        tableLog = counts.tableLog,
                        base = base,
                        extraBits = extraBits,
                    ),
                    consumed = counts.consumed,
                )
            }

            else -> {
                requireNotNull(previous) { "Repeat sequence table without a previous table" }
                SequenceTableRead(table = previous, consumed = 0)
            }
        }
    }

    private fun buildSequenceTable(
        counts: IntArray,
        maxSymbol: Int,
        tableLog: Int,
        base: IntArray,
        extraBits: IntArray,
    ): SequenceTable {
        if (tableLog == 0) {
            // A single-state family: every state resolves to the same symbol.
            val symbol = counts.indexOfFirst { count -> count > 0 }
            return SequenceTable(
                tableLog = 0,
                symbol = byteArrayOf(symbol.toByte()),
                nbBits = ByteArray(1),
                newState = IntArray(1),
                base = base,
                extraBits = extraBits,
            )
        }
        val tableSize = 1 shl tableLog
        val lastSymbol = minOf(maxSymbol, counts.size - 1)
        val symbol = ByteArray(tableSize)
        val symbolNext = IntArray(lastSymbol + 1)
        var highThreshold = tableSize - 1
        for (value in 0..lastSymbol) {
            if (counts[value] == -1) {
                symbol[highThreshold--] = value.toByte()
                symbolNext[value] = 1
            } else {
                symbolNext[value] = counts[value]
            }
        }
        val step = (tableSize shr 1) + (tableSize shr 3) + 3
        val mask = tableSize - 1
        var position = 0
        for (value in 0..lastSymbol) {
            var placed = 0
            while (placed < counts[value]) {
                symbol[position] = value.toByte()
                position = (position + step) and mask
                while (position > highThreshold) position = (position + step) and mask
                placed++
            }
        }
        require(position == 0) { "Invalid FSE distribution" }
        val nbBits = ByteArray(tableSize)
        val newState = IntArray(tableSize)
        for (state in 0 until tableSize) {
            val next = symbolNext[symbol[state].toInt()]++
            val bits = tableLog - highBit(next)
            nbBits[state] = bits.toByte()
            newState[state] = (next shl bits) - tableSize
        }
        return SequenceTable(
            tableLog = tableLog,
            symbol = symbol,
            nbBits = nbBits,
            newState = newState,
            base = base,
            extraBits = extraBits,
        )
    }

    // --------------------------------------------------------------------- FSE

    private class NormalizedCounts(
        val counts: IntArray,
        val maxSymbol: Int,
        val tableLog: Int,
        val consumed: Int,
    )

    private fun readNormalizedCounts(
        input: ByteArray,
        start: Int,
        end: Int,
        maxSymbolLimit: Int,
    ): NormalizedCounts {
        val reader = ForwardBitReader(input, start, end)
        val counts = IntArray(maxSymbolLimit + 1)
        val tableLog = reader.readBits(FseTableLogBits) + MinimumFseTableLog
        require(tableLog <= MaximumFseTableLog) { "FSE table log is too large" }
        var remaining = (1 shl tableLog) + 1
        var threshold = 1 shl tableLog
        var bitWidth = tableLog + 1
        var symbol = 0
        var previousZero = false
        while (true) {
            if (previousZero) {
                var repeats = 0
                while (reader.peekBits(2) == 3) {
                    reader.skipBits(2)
                    repeats++
                }
                symbol += 3 * repeats + reader.readBits(2)
                require(symbol <= maxSymbolLimit + 1) { "FSE symbol value is too large" }
            }
            val limit = (2 * threshold - 1) - remaining
            val count: Int
            if (reader.peekBits(bitWidth - 1) < limit) {
                count = reader.readBits(bitWidth - 1)
            } else {
                val peeked = reader.peekBits(bitWidth)
                count = if (peeked >= threshold) peeked - limit else peeked
                reader.skipBits(bitWidth)
            }
            val normalized = count - 1
            if (normalized >= 0) remaining -= normalized else remaining += normalized
            require(symbol <= maxSymbolLimit) { "FSE symbol value is too large" }
            counts[symbol] = normalized
            previousZero = normalized == 0
            symbol++
            if (remaining < threshold) {
                if (remaining <= 1) break
                bitWidth = highBit(remaining) + 1
                threshold = 1 shl (bitWidth - 1)
            }
            if (symbol > maxSymbolLimit) break
        }
        require(remaining == 1) { "Invalid FSE distribution" }
        return NormalizedCounts(
            counts = counts,
            maxSymbol = symbol - 1,
            tableLog = tableLog,
            consumed = reader.consumedBytes,
        )
    }

    /** Decodes an FSE-compressed symbol stream, used for Huffman weights. */
    private fun decodeFseBlock(
        input: ByteArray,
        start: Int,
        end: Int,
        into: ShortArray,
        capacity: Int,
    ): Int {
        val counts = readNormalizedCounts(input, start, end, MaxHuffmanSymbols - 1)
        val table = buildSymbolTable(counts.counts, counts.maxSymbol, counts.tableLog)
        val reader = BackwardBitReader(input, start + counts.consumed, end)
        var firstState = reader.readBits(counts.tableLog)
        var secondState = reader.readBits(counts.tableLog)
        var written = 0
        while (true) {
            if (written >= capacity) break
            into[written++] = table.symbol[firstState].toShort()
            firstState = table.newState[firstState] + reader.readBits(table.nbBits[firstState].toInt())
            reader.load()
            if (reader.isOverflow) {
                if (written < capacity) into[written++] = table.symbol[secondState].toShort()
                break
            }
            if (written >= capacity) break
            into[written++] = table.symbol[secondState].toShort()
            secondState = table.newState[secondState] + reader.readBits(table.nbBits[secondState].toInt())
            reader.load()
            if (reader.isOverflow) {
                if (written < capacity) into[written++] = table.symbol[firstState].toShort()
                break
            }
        }
        return written
    }

    private fun buildSymbolTable(counts: IntArray, maxSymbol: Int, tableLog: Int): SymbolTable {
        val tableSize = 1 shl tableLog
        val lastSymbol = minOf(maxSymbol, counts.size - 1)
        val symbol = ByteArray(tableSize)
        val symbolNext = IntArray(lastSymbol + 1)
        var highThreshold = tableSize - 1
        for (value in 0..lastSymbol) {
            if (counts[value] == -1) {
                symbol[highThreshold--] = value.toByte()
                symbolNext[value] = 1
            } else {
                symbolNext[value] = counts[value]
            }
        }
        val step = (tableSize shr 1) + (tableSize shr 3) + 3
        val mask = tableSize - 1
        var position = 0
        for (value in 0..maxSymbol) {
            var placed = 0
            while (placed < counts[value]) {
                symbol[position] = value.toByte()
                position = (position + step) and mask
                while (position > highThreshold) position = (position + step) and mask
                placed++
            }
        }
        require(position == 0) { "Invalid FSE distribution" }
        val nbBits = ByteArray(tableSize)
        val newState = IntArray(tableSize)
        for (state in 0 until tableSize) {
            val next = symbolNext[symbol[state].toInt()]++
            val bits = tableLog - highBit(next)
            nbBits[state] = bits.toByte()
            newState[state] = (next shl bits) - tableSize
        }
        return SymbolTable(tableLog = tableLog, symbol = symbol, nbBits = nbBits, newState = newState)
    }

    private class SymbolTable(
        val tableLog: Int,
        val symbol: ByteArray,
        val nbBits: ByteArray,
        val newState: IntArray,
    )

    /** Per-block decoder state: literals entropy tables, sequence tables and the repeat offsets. */
    private class BlockState {
        var huffman: HuffmanTable? = null
        var literalsLengthTable: SequenceTable? = null
        var offsetTable: SequenceTable? = null
        var matchLengthTable: SequenceTable? = null
        val repeatOffsets = intArrayOf(1, 4, 8)
    }
}

/** Growable output buffer that also serves as the match window. */
private class ZstdOutput(initialCapacity: Int) {
    private var bytes = ByteArray(initialCapacity.coerceIn(MinimumZstdOutput, MaximumZstdOutput))
    private var size = 0

    fun append(source: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        require(length > 0 && offset >= 0 && offset + length <= source.size) { "Invalid zstd literals" }
        ensureCapacity(size + length)
        source.copyInto(bytes, destinationOffset = size, startIndex = offset, endIndex = offset + length)
        size += length
    }

    fun appendRepeated(value: Byte, length: Int) {
        if (length == 0) return
        ensureCapacity(size + length)
        bytes.fill(value, size, size + length)
        size += length
    }

    fun copyWithin(distance: Int, length: Int) {
        require(distance in 1..size) { "Invalid zstd match distance" }
        ensureCapacity(size + length)
        var from = size - distance
        var to = size
        repeat(length) {
            bytes[to++] = bytes[from++]
        }
        size += length
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    private fun ensureCapacity(required: Int) {
        require(required <= MaximumZstdOutput) { "Zstd output is too large" }
        if (required <= bytes.size) return
        var capacity = bytes.size
        while (capacity < required) capacity *= 2
        bytes = bytes.copyOf(capacity)
    }
}

/**
 * Reads a zstd bitstream backwards through a sliding 64-bit window, exactly as the reference
 * decoder does: [bitsConsumed] counts the bits already taken from the top of the window, and only
 * a read past the whole window is an overflow. That boundary is what tells an FSE stream where its
 * last symbol is, so it has to match the encoder's view of the stream end.
 */
private class BackwardBitReader(
    private val bytes: ByteArray,
    start: Int,
    end: Int,
) {
    private val startAddress = start
    private var currentAddress: Int
    private var bits: Long
    private var bitsConsumed: Int = 0
    private var overflowed = false

    init {
        require(end > start) { "Empty zstd bitstream" }
        val lastByte = bytes[end - 1].toInt() and ByteMask
        require(lastByte != 0) { "Invalid zstd bitstream padding" }
        bitsConsumed = LongBytes - highBit(lastByte)
        val size = end - start
        if (size >= LongBytes) {
            currentAddress = end - LongBytes
            bits = readLongLe(currentAddress)
        } else {
            currentAddress = start
            bits = readTail(start, size)
            bitsConsumed += (LongBytes - size) * Byte.SIZE_BITS
        }
    }

    val isOverflow: Boolean get() = overflowed

    fun peekBits(count: Int): Int {
        if (count == 0) return 0
        return (((bits shl bitsConsumed) ushr 1) ushr (63 - count)).toInt()
    }

    fun readBits(count: Int): Int {
        val value = peekBits(count)
        bitsConsumed += count
        return value
    }

    fun skipBits(count: Int) {
        bitsConsumed += count
    }

    /** Advances the window by whole bytes; a window that cannot advance keeps filling up. */
    fun load() {
        if (bitsConsumed > Long.SIZE_BITS) {
            overflowed = true
            return
        }
        if (currentAddress == startAddress) return
        var byteCount = bitsConsumed ushr 3
        if (currentAddress >= startAddress + LongBytes) {
            if (byteCount > 0) {
                currentAddress -= byteCount
                bits = readLongLe(currentAddress)
            }
            bitsConsumed = bitsConsumed and 7
        } else if (currentAddress - byteCount < startAddress) {
            byteCount = currentAddress - startAddress
            currentAddress = startAddress
            bitsConsumed -= byteCount * Byte.SIZE_BITS
            bits = readLongLe(startAddress)
        } else {
            currentAddress -= byteCount
            bitsConsumed -= byteCount * Byte.SIZE_BITS
            bits = readLongLe(currentAddress)
        }
    }

    private fun readLongLe(address: Int): Long {
        var value = 0L
        for (index in 0 until LongBytes) {
            val position = address + index
            val byte = if (position in bytes.indices) bytes[position].toLong() and 0xFFL else 0L
            value = value or (byte shl (index * Byte.SIZE_BITS))
        }
        return value
    }

    private fun readTail(address: Int, size: Int): Long {
        var value = bytes[address].toLong() and 0xFFL
        for (index in 1 until size) {
            value = value or ((bytes[address + index].toLong() and 0xFFL) shl (index * Byte.SIZE_BITS))
        }
        return value
    }
}

/** Reads a zstd entropy header forwards, least significant bit first. */
private class ForwardBitReader(
    private val bytes: ByteArray,
    start: Int,
    private val end: Int,
) {
    private val startBit = start * Byte.SIZE_BITS
    private var bitIndex = startBit

    val consumedBytes: Int get() = (bitIndex - startBit + 7) / 8

    fun peekBits(count: Int): Int {
        var value = 0
        for (offset in 0 until count) {
            val position = bitIndex + offset
            val byteIndex = position ushr 3
            val bit = if (byteIndex >= end || byteIndex < 0) 0 else {
                (bytes[byteIndex].toInt() ushr (position and 7)) and 1
            }
            value = value or (bit shl offset)
        }
        return value
    }

    fun readBits(count: Int): Int {
        val value = peekBits(count)
        bitIndex += count
        return value
    }

    fun skipBits(count: Int) {
        bitIndex += count
    }
}

private fun highBit(value: Int): Int {
    require(value > 0) { "FSE state is invalid" }
    return 31 - Integer.numberOfLeadingZeros(value)
}

private fun ByteArray.readIntLe(offset: Int): Int {
    require(size - offset >= IntBytes) { ZstdTruncated }
    return (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)
}

private fun ByteArray.readIntLe16(offset: Int): Int {
    require(size - offset >= 2) { ZstdTruncated }
    return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}

private fun ByteArray.readIntLe24(offset: Int): Int {
    require(size - offset >= 3) { ZstdTruncated }
    return (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16)
}

private fun readVariable(bytes: ByteArray, offset: Int, size: Int): Long {
    var value = 0L
    for (index in 0 until size) {
        value = value or ((bytes[offset + index].toLong() and 0xFF) shl (8 * index))
    }
    return value
}

private const val ZstdTruncated = "Zstd payload is truncated"
private const val MagicSize = 4
private const val IntBytes = 4
private const val LongBytes = 8
private const val ByteMask = 0xFF
private const val BlockHeaderSize = 3
private const val BlockTypeRaw = 0
private const val BlockTypeRle = 1
private const val BlockTypeCompressed = 2
private const val LiteralsRaw = 0
private const val LiteralsRle = 1
private const val LiteralsCompressed = 2
private const val HuffmanDirectWeightsHeader = 128
private const val MaxHuffmanSymbols = 256
private const val MaxHuffmanTableLog = 11
private const val MinimumFourStreamSize = 6
private const val FourStreamJumpTableSize = 6
private const val LongSequenceOffset = 0x7F00
private const val SequenceModePredefined = 0
private const val SequenceModeRle = 1
private const val SequenceModeFse = 2
private const val FseTableLogBits = 4
private const val MinimumFseTableLog = 5
private const val MaximumFseTableLog = 9
private const val OutputGrowthEstimate = 4
private const val MinimumZstdOutput = 1024
private const val MaximumZstdOutput = 64 * 1024 * 1024
private const val FrameMagic = 0xFD2FB528.toInt()
private const val SkippableMagic = 0x184D2A50
private const val SkippableMagicMask = 0xFFFFFFF0.toInt()
private const val MinimalMatchLength = 3
private const val MinimalOffsetValue = 3

private const val MaxLiteralsLengthCode = 35
private const val MaxMatchLengthCode = 52
private const val MaxOffsetCode = 31
private const val LiteralsLengthTableLog = 9
private const val MatchLengthTableLog = 9
private const val OffsetTableLog = 8

private val LiteralsLengthExtraBits = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    1, 1, 1, 1, 2, 2, 3, 3,
    4, 6, 7, 8, 9, 10, 11, 12,
    13, 14, 15, 16,
)

private val MatchLengthExtraBits = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    1, 1, 1, 1, 2, 2, 3, 3,
    4, 4, 5, 7, 8, 9, 10, 11,
    12, 13, 14, 15, 16,
)

private val OffsetExtraBits = IntArray(MaxOffsetCode + 1) { it }

private val LiteralsLengthDefaultNorm = intArrayOf(
    4, 3, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 1, 1, 1,
    2, 2, 2, 2, 2, 2, 2, 2,
    2, 3, 2, 1, 1, 1, 1, 1,
    -1, -1, -1, -1,
)
private const val LiteralsLengthDefaultNormLog = 6

private val MatchLengthDefaultNorm = intArrayOf(
    1, 4, 3, 2, 2, 2, 2, 2,
    2, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, -1, -1,
    -1, -1, -1, -1, -1,
)
private const val MatchLengthDefaultNormLog = 6

private val OffsetDefaultNorm = intArrayOf(
    1, 1, 1, 1, 1, 1, 2, 2,
    2, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1,
    -1, -1, -1, -1, -1,
)
private const val OffsetDefaultNormLog = 5

private val literalsLengthBase = IntArray(MaxLiteralsLengthCode + 1).also { base ->
    for (code in 0..15) base[code] = code
    for (code in 16..MaxLiteralsLengthCode) {
        base[code] = base[code - 1] + (1 shl LiteralsLengthExtraBits[code - 1])
    }
}

private val matchLengthBase = IntArray(MaxMatchLengthCode + 1).also { base ->
    for (code in 0..31) base[code] = code + MinimalMatchLength
    for (code in 32..MaxMatchLengthCode) {
        base[code] = base[code - 1] + (1 shl MatchLengthExtraBits[code - 1])
    }
}

private val offsetBase = IntArray(MaxOffsetCode + 1).also { base ->
    base[0] = 1
    base[1] = 1
    for (code in 2..MaxOffsetCode) base[code] = (1 shl code) - MinimalOffsetValue
}
