// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import app.DefaultMihomoOverrideScriptId
import app.MihomoOverrideScriptState
import app.MihomoProfileState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.mozilla.javascript.Context

private val OverrideJson = Json {
    ignoreUnknownKeys = true
}

internal fun Map<String, Any?>.applyMihomoProfileScriptOverride(
    profile: MihomoProfileState?,
    scripts: List<MihomoOverrideScriptState>,
): Map<String, Any?> {
    if (profile == null || profile.overrideScriptId == DefaultMihomoOverrideScriptId) {
        return this
    }
    val overrideScript = scripts.firstOrNull { script -> script.id == profile.overrideScriptId }
        ?: error("Override script does not exist")
    if (overrideScript.content.isBlank()) {
        return this
    }

    val input = LinkedHashMap(this)
    if (input["proxy-providers"] == null) {
        input["proxy-providers"] = linkedMapOf<String, Any?>()
    }

    val configJson = yamlValueToJson(input).toString()
    val script = """
        ${overrideScript.content}
        ;(function() {
          if (typeof main !== 'function') {
            throw new Error('Override script must define main(config)')
          }
          var config = JSON.parse(${JsonPrimitive(configJson)})
          var result = main(config)
          if (result === undefined || result === null) {
            result = config
          }
          return JSON.stringify(result)
        })()
    """.trimIndent()

    val result = evaluateOverrideScript(script)

    val element = OverrideJson.parseToJsonElement(result)
    if (element !is JsonObject) {
        error("Override script must return a config object")
    }
    @Suppress("UNCHECKED_CAST")
    return jsonToYamlValue(element) as Map<String, Any?>
}

private fun evaluateOverrideScript(script: String): String {
    val context = Context.enter()
    return try {
        context.setLanguageVersion(Context.VERSION_ES6)
        context.setOptimizationLevel(-1)
        val scope = context.initStandardObjects()
        Context.toString(context.evaluateString(scope, script, "asteriskmeta-profile-override.js", 1, null))
    } finally {
        Context.exit()
    }
}

private fun yamlValueToJson(value: Any?): JsonElement {
    return when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(
            value.mapNotNull { (key, childValue) ->
                val name = key as? String ?: return@mapNotNull null
                name to yamlValueToJson(childValue)
            }.toMap(LinkedHashMap()),
        )
        is List<*> -> JsonArray(value.map(::yamlValueToJson))
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}

private fun jsonToYamlValue(value: JsonElement): Any? {
    return when (value) {
        JsonNull -> null
        is JsonObject -> linkedMapOf<String, Any?>().apply {
            value.forEach { (key, childValue) ->
                put(key, jsonToYamlValue(childValue))
            }
        }
        is JsonArray -> value.map(::jsonToYamlValue)
        is JsonPrimitive -> when {
            value.isString -> value.content
            value.booleanOrNull != null -> value.booleanOrNull
            value.longOrNull != null -> value.longOrNull
            value.doubleOrNull != null -> value.doubleOrNull
            else -> value.content
        }
    }
}
