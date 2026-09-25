// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Parse
import org.snakeyaml.engine.v2.api.lowlevel.Present
import org.snakeyaml.engine.v2.comments.CommentType
import org.snakeyaml.engine.v2.events.CollectionEndEvent
import org.snakeyaml.engine.v2.events.CollectionStartEvent
import org.snakeyaml.engine.v2.events.CommentEvent
import org.snakeyaml.engine.v2.events.Event

internal fun formatMihomoYaml(source: String): String {
    // Re-emit syntax events rather than loading objects, preserving comments,
    // anchors, scalar styles and quoted values such as numeric passwords.
    val loadSettings = LoadSettings.builder().setParseComments(true).build()
    val dumpSettings = DumpSettings.builder()
        .setDumpComments(true)
        .setIndent(2)
        .setSplitLines(false)
        .build()
    val events = Parse(loadSettings).parseString(source)
    return Present(dumpSettings).emitToString(events.withBlankLinesAfterBlockEnds())
}

private fun Iterable<Event>.withBlankLinesAfterBlockEnds(): Iterator<Event> = sequence {
    val pendingBlankLines = mutableListOf<CommentEvent>()
    val collectionFlowStyles = ArrayDeque<Boolean>()
    for (event in this@withBlankLinesAfterBlockEnds) {
        if (event is CommentEvent && event.commentType == CommentType.BLANK_LINE) {
            pendingBlankLines.add(event)
            continue
        }
        if (event is CollectionEndEvent && !collectionFlowStyles.removeLast()) {
            // SnakeYAML otherwise writes blank-line indentation before dedenting,
            // producing an extra empty line on every formatting pass.
            yield(event)
            continue
        }
        yieldAll(pendingBlankLines)
        pendingBlankLines.clear()
        if (event is CollectionStartEvent) collectionFlowStyles.addLast(event.isFlow)
        yield(event)
    }
    yieldAll(pendingBlankLines)
}.iterator()
