// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import app.DefaultMihomoOverrideScriptId
import app.MihomoOverrideScriptState
import app.MihomoProfileState
import app.MihomoRuleOverrideState
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private val OverrideJson = Json {
    ignoreUnknownKeys = true
}

internal fun interface MihomoProfileScriptEngine {
    fun evaluate(script: String): String
}

internal fun interface MihomoProfileScriptDebugEngine {
    fun evaluate(scriptContent: String, configJson: String): String
}

internal data class MihomoProfileScriptDebugResult(
    val logs: List<MihomoProfileScriptDebugLog> = emptyList(),
    val error: String? = null,
    val outputYaml: String? = null,
    val summary: MihomoProfileScriptDebugSummary? = null,
) {
    val success: Boolean
        get() = error == null
}

internal data class MihomoProfileScriptDebugLog(
    val level: String,
    val message: String,
)

internal data class MihomoProfileScriptDebugSummary(
    val inputProxyCount: Int,
    val outputProxyCount: Int,
    val inputProxyGroupCount: Int,
    val outputProxyGroupCount: Int,
    val inputRuleCount: Int,
    val outputRuleCount: Int,
)

internal fun Map<String, Any?>.applyMihomoProfileScriptOverride(
    profile: MihomoProfileState?,
    scripts: List<MihomoOverrideScriptState>,
    engine: MihomoProfileScriptEngine = DefaultMihomoProfileScriptEngine,
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
          var config = JSON.parse(${configJson.toAsciiJsStringLiteral()})
          var result = main(config)
          if (result === undefined || result === null) {
            result = config
          }
          return $AsciiJsonStringify(result)
        })()
    """.trimIndent()

    val result = engine.evaluate(script)

    val element = OverrideJson.parseToJsonElement(result)
    if (element !is JsonObject) {
        error("Override script must return a config object")
    }
    @Suppress("UNCHECKED_CAST")
    return jsonToYamlValue(element) as Map<String, Any?>
}

/**
 * Runs [scriptContent] against the configuration a real compile would hand it: the source
 * profile after [ruleOverride], the profile's GUI rule override. The output then passes through
 * [managedStage], the settings AsteriskMETA owns, so it shows what the core would receive.
 */
internal fun debugMihomoProfileScriptOverride(
    rawProfileContent: String,
    scriptContent: String,
    ruleOverride: MihomoRuleOverrideState = MihomoRuleOverrideState(),
    managedStage: (Map<String, Any?>) -> Map<String, Any?>,
    engine: MihomoProfileScriptDebugEngine = QuickJsMihomoProfileScriptDebugEngine,
): MihomoProfileScriptDebugResult {
    if (scriptContent.isBlank()) {
        return MihomoProfileScriptDebugResult(error = "Override script is blank")
    }

    val source = runCatching { rawProfileContent.loadMihomoProfileObject() }.getOrElse { error ->
        return MihomoProfileScriptDebugResult(error = error.message ?: error.toString())
    } ?: return MihomoProfileScriptDebugResult(error = "Override script requires a YAML object profile")
    val input = runCatching { LinkedHashMap(source.applyMihomoRuleOverride(ruleOverride).config) }.getOrElse { error ->
        return MihomoProfileScriptDebugResult(error = error.message ?: error.toString())
    }
    if (input["proxy-providers"] == null) {
        input["proxy-providers"] = linkedMapOf<String, Any?>()
    }

    val configJson = yamlValueToJson(input).toString()
    return runCatching {
        engine.evaluate(scriptContent, configJson).toDebugResult(input, managedStage)
    }.getOrElse { error ->
        MihomoProfileScriptDebugResult(error = error.message ?: error.toString())
    }
}

private object QuickJsMihomoProfileScriptDebugEngine : MihomoProfileScriptDebugEngine {
    override fun evaluate(scriptContent: String, configJson: String): String {
        return runBlocking(Dispatchers.Default) {
            quickJs {
                evaluate<Boolean>(
                    code = DebugConsolePrelude,
                    filename = "asteriskmeta-profile-debug-console.js",
                )
                evaluate<String>(
                    code = buildDebugOverrideScript(scriptContent, configJson),
                    filename = "asteriskmeta-profile-override.js",
                )
            }
        }
    }
}

internal object DefaultMihomoProfileScriptEngine : MihomoProfileScriptEngine {
    override fun evaluate(script: String): String {
        return runBlocking(Dispatchers.Default) {
            quickJs {
                evaluate<String>(
                    code = script,
                    filename = "asteriskmeta-profile-override.js",
                )
            }
        }
    }
}

private fun buildDebugOverrideScript(
    scriptContent: String,
    configJson: String,
): String {
    return """
        $scriptContent
        ;(function(globalThis) {
          if (typeof main !== 'function') {
            throw new Error('Override script must define main(config)')
          }
          var config = JSON.parse(${configJson.toAsciiJsStringLiteral()})
          var result = main(config)
          if (result === undefined || result === null) {
            result = config
          }
          return $AsciiJsonStringify({
            logs: globalThis.__asteriskMetaScriptDebugLogs || [],
            config: result
          })
        })(globalThis)
    """.trimIndent()
}

/**
 * The configuration now carries real non-BMP characters (flag emoji in policy group names), and
 * JNI strings are modified UTF-8. Both directions of the exchange are therefore pure ASCII:
 * the input is an escaped string literal and the output escapes everything above ASCII.
 */
internal fun String.toAsciiJsStringLiteral(): String {
    val literal = JsonPrimitive(this).toString()
    if (literal.all { char -> char.code < 0x7f }) return literal
    return buildString(literal.length + 16) {
        literal.forEach { char ->
            if (char.code < 0x7f) append(char) else append("\\u").append(char.code.toString(16).padStart(4, '0'))
        }
    }
}

private const val AsciiJsonStringify =
    "(function(value) { return JSON.stringify(value).replace(/[\\u007f-\\uffff]/g, function(c) { " +
        "return '\\\\u' +('0000' + c.charCodeAt(0).toString(16)).slice(-4) }) })"

private val DebugConsolePrelude = """
    ;(function(globalThis) {
      var logs = []
      function format(value) {
        if (typeof value === 'string') return value
        if (typeof value === 'undefined') return 'undefined'
        if (typeof value === 'function') return '[Function ' + (value.name || 'anonymous') + ']'
        try {
          var json = JSON.stringify(value)
          return typeof json === 'undefined' ? String(value) : json
        } catch (error) {
          return String(value)
        }
      }
      function record(level, args) {
        logs.push({
          level: level,
          message: Array.prototype.slice.call(args).map(format).join(' ')
        })
      }
      globalThis.__asteriskMetaScriptDebugLogs = logs
      globalThis.console = {
        log: function() { record('log', arguments) },
        info: function() { record('info', arguments) },
        warn: function() { record('warn', arguments) },
        error: function() { record('error', arguments) },
        debug: function() { record('debug', arguments) }
      }
      return true
    })(globalThis)
""".trimIndent()

private fun String.toDebugResult(
    input: Map<String, Any?>,
    managedStage: (Map<String, Any?>) -> Map<String, Any?>,
): MihomoProfileScriptDebugResult {
    val element = OverrideJson.parseToJsonElement(this)
    if (element !is JsonObject) {
        return MihomoProfileScriptDebugResult(error = "Script debug result is not an object")
    }
    val logs = element["logs"]?.jsonArray?.mapNotNull { item ->
        val log = item as? JsonObject ?: return@mapNotNull null
        MihomoProfileScriptDebugLog(
            level = log["level"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "log" },
            message = log["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }.orEmpty()
    val config = element["config"]
    if (config !is JsonObject) {
        return MihomoProfileScriptDebugResult(
            logs = logs,
            error = "Override script must return a config object",
        )
    }

    @Suppress("UNCHECKED_CAST")
    val scripted = jsonToYamlValue(config) as Map<String, Any?>
    val output = runCatching { managedStage(scripted) }.getOrElse { error ->
        return MihomoProfileScriptDebugResult(logs = logs, error = error.message ?: error.toString())
    }
    return MihomoProfileScriptDebugResult(
        logs = logs,
        outputYaml = dumpMihomoProfileYaml(output),
        summary = input.toDebugSummary(output),
    )
}

private fun Map<String, Any?>.toDebugSummary(output: Map<String, Any?>): MihomoProfileScriptDebugSummary {
    return MihomoProfileScriptDebugSummary(
        inputProxyCount = listSizeOf("proxies"),
        outputProxyCount = output.listSizeOf("proxies"),
        inputProxyGroupCount = listSizeOf("proxy-groups"),
        outputProxyGroupCount = output.listSizeOf("proxy-groups"),
        inputRuleCount = listSizeOf("rules"),
        outputRuleCount = output.listSizeOf("rules"),
    )
}

private fun Map<String, Any?>.listSizeOf(key: String): Int {
    return (this[key] as? List<*>)?.size ?: 0
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
