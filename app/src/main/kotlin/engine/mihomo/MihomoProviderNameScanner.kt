// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import java.io.Reader
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Parse
import org.snakeyaml.engine.v2.events.AliasEvent
import org.snakeyaml.engine.v2.events.Event
import org.snakeyaml.engine.v2.events.NodeEvent
import org.snakeyaml.engine.v2.events.ScalarEvent
import kotlin.coroutines.cancellation.CancellationException

internal fun String.parseMihomoProxyProviderNames(): List<String> {
    return reader().use { source -> source.parseMihomoProxyProviderNames() }
}

internal fun Reader.parseMihomoProxyProviderNames(): List<String> {
    return try {
        val events = Parse(ProviderNameScanSettings).parseReader(this).iterator()
        events.readProxyProviderNames(mutableMapOf())
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        emptyList()
    }
}

private fun Iterator<Event>.readProxyProviderNames(
    anchoredMappingKeys: MutableMap<String, List<String>>,
): List<String> {
    while (hasNext()) {
        val event = nextNodeEvent() ?: return emptyList()
        when (event.eventId) {
            Event.ID.MappingStart -> return readRootMapping(anchoredMappingKeys)
            Event.ID.StreamStart,
            Event.ID.DocumentStart,
            -> Unit

            else -> {
                skipNode(event, anchoredMappingKeys)
                return emptyList()
            }
        }
    }
    return emptyList()
}

private fun Iterator<Event>.readRootMapping(
    anchoredMappingKeys: MutableMap<String, List<String>>,
): List<String> {
    while (hasNext()) {
        val keyEvent = nextNodeEvent() ?: return emptyList()
        if (keyEvent.eventId == Event.ID.MappingEnd) return emptyList()

        val key = (keyEvent as? ScalarEvent)?.value
        skipNode(keyEvent, anchoredMappingKeys)
        val valueEvent = nextNodeEvent() ?: return emptyList()
        if (key == MihomoProviderType.Proxy.topLevelKey) {
            return readProviderMapping(valueEvent, anchoredMappingKeys)
        }
        skipNode(valueEvent, anchoredMappingKeys)
    }
    return emptyList()
}

private fun Iterator<Event>.readProviderMapping(
    startEvent: Event,
    anchoredMappingKeys: MutableMap<String, List<String>>,
): List<String> {
    if (startEvent is AliasEvent) {
        return anchoredMappingKeys[startEvent.alias.value].orEmpty()
    }
    if (startEvent.eventId != Event.ID.MappingStart) {
        skipNode(startEvent, anchoredMappingKeys)
        return emptyList()
    }

    val names = linkedSetOf<String>()
    while (hasNext()) {
        val keyEvent = nextNodeEvent() ?: return emptyList()
        if (keyEvent.eventId == Event.ID.MappingEnd) return names.toList()

        val name = (keyEvent as? ScalarEvent)?.value?.trim().orEmpty()
        skipNode(keyEvent, anchoredMappingKeys)
        val valueEvent = nextNodeEvent() ?: return emptyList()
        skipNode(valueEvent, anchoredMappingKeys)
        if (name.isNotEmpty() && name != YamlMergeKey) names += name
    }
    return emptyList()
}

private fun Iterator<Event>.skipNode(
    startEvent: Event,
    anchoredMappingKeys: MutableMap<String, List<String>>,
) {
    when (startEvent.eventId) {
        Event.ID.MappingStart -> {
            val anchor = (startEvent as NodeEvent).anchor.orElse(null)?.value
            val keys = skipMapping(
                collectKeys = anchor != null,
                anchoredMappingKeys = anchoredMappingKeys,
            )
            if (anchor != null) anchoredMappingKeys[anchor] = keys
        }

        Event.ID.SequenceStart -> skipSequence(anchoredMappingKeys)
        Event.ID.Scalar,
        Event.ID.Alias,
        -> Unit

        else -> error("Unexpected YAML event ${startEvent.eventId}")
    }
}

private fun Iterator<Event>.skipMapping(
    collectKeys: Boolean,
    anchoredMappingKeys: MutableMap<String, List<String>>,
): List<String> {
    val keys = linkedSetOf<String>()
    while (hasNext()) {
        val keyEvent = nextNodeEvent() ?: error("Unterminated YAML mapping")
        if (keyEvent.eventId == Event.ID.MappingEnd) return keys.toList()
        val key = (keyEvent as? ScalarEvent)?.value?.trim().orEmpty()
        skipNode(keyEvent, anchoredMappingKeys)

        val valueEvent = nextNodeEvent() ?: error("Missing YAML mapping value")
        skipNode(valueEvent, anchoredMappingKeys)
        if (collectKeys && key.isNotEmpty() && key != YamlMergeKey) keys += key
    }
    error("Unterminated YAML mapping")
}

private fun Iterator<Event>.skipSequence(
    anchoredMappingKeys: MutableMap<String, List<String>>,
) {
    while (hasNext()) {
        val itemEvent = nextNodeEvent() ?: error("Unterminated YAML sequence")
        if (itemEvent.eventId == Event.ID.SequenceEnd) return
        skipNode(itemEvent, anchoredMappingKeys)
    }
    error("Unterminated YAML sequence")
}

private fun Iterator<Event>.nextNodeEvent(): Event? {
    while (hasNext()) {
        val event = next()
        if (event.eventId != Event.ID.Comment) return event
    }
    return null
}

private val ProviderNameScanSettings = LoadSettings.builder()
    .setCodePointLimit(Int.MAX_VALUE)
    .build()

private const val YamlMergeKey = "<<"
