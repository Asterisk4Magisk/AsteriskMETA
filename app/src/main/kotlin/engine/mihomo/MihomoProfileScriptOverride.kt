// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import app.DefaultMihomoOverrideScriptId
import app.MihomoOverrideScriptState
import app.MihomoProfileState
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
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.common.FlowStyle

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
    engine: MihomoProfileScriptEngine = QuickJsMihomoProfileScriptEngine,
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

    val result = engine.evaluate(script)

    val element = OverrideJson.parseToJsonElement(result)
    if (element !is JsonObject) {
        error("Override script must return a config object")
    }
    @Suppress("UNCHECKED_CAST")
    return jsonToYamlValue(element) as Map<String, Any?>
}

internal fun debugMihomoProfileScriptOverride(
    rawProfileContent: String,
    scriptContent: String,
    engine: MihomoProfileScriptDebugEngine = QuickJsMihomoProfileScriptDebugEngine,
): MihomoProfileScriptDebugResult {
    if (scriptContent.isBlank()) {
        return MihomoProfileScriptDebugResult(error = "Override script is blank")
    }

    val escaped = rawProfileContent.escapeSupplementaryYamlCodePoints()
    val root = runCatching {
        Load(MihomoYamlLoadSettings).loadFromString(escaped.value) as? Map<*, *>
    }.getOrElse { error ->
        return MihomoProfileScriptDebugResult(error = error.message ?: error.toString())
    }
    if (root == null) {
        return MihomoProfileScriptDebugResult(error = "Override script requires a YAML object profile")
    }

    val input = linkedMapOf<String, Any?>()
    root.forEach { (key, value) ->
        val name = key as? String ?: return@forEach
        input[name] = normalizeYamlValue(value)
    }
    if (input["proxy-providers"] == null) {
        input["proxy-providers"] = linkedMapOf<String, Any?>()
    }

    val configJson = yamlValueToJson(input).toString()
    return runCatching {
        val rawResult = engine.evaluate(scriptContent, configJson)
        rawResult.toDebugResult(input).let { result ->
            result.copy(outputYaml = result.outputYaml?.let(escaped::restore))
        }
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

private object QuickJsMihomoProfileScriptEngine : MihomoProfileScriptEngine {
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
          var config = JSON.parse(${JsonPrimitive(configJson)})
          var result = main(config)
          if (result === undefined || result === null) {
            result = config
          }
          return JSON.stringify({
            logs: globalThis.__asteriskMetaScriptDebugLogs || [],
            config: result
          })
        })(globalThis)
    """.trimIndent()
}

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

private fun String.toDebugResult(input: Map<String, Any?>): MihomoProfileScriptDebugResult {
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
    val output = jsonToYamlValue(config) as Map<String, Any?>
    return MihomoProfileScriptDebugResult(
        logs = logs,
        outputYaml = dumpDebugYaml(normalizeYamlValue(output)),
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

private val DebugYamlDumpSettings = DumpSettings.builder()
    .setDefaultFlowStyle(FlowStyle.BLOCK)
    .build()

private fun dumpDebugYaml(value: Any?): String {
    return Dump(DebugYamlDumpSettings).dumpToString(value)
}
