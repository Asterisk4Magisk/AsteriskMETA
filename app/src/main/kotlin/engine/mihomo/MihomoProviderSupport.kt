// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.io.File
import java.security.MessageDigest

internal enum class MihomoProviderType(
    val topLevelKey: String,
    val cmfaPrefix: String,
) {
    Proxy(topLevelKey = "proxy-providers", cmfaPrefix = "proxies"),
    Rule(topLevelKey = "rule-providers", cmfaPrefix = "rules"),
}

internal fun MutableMap<String, Any?>.putCmfaRootProviderPaths() {
    MihomoProviderType.entries.forEach { type ->
        val providers = this[type.topLevelKey] as? Map<*, *> ?: return@forEach
        val updatedProviders = linkedMapOf<String, Any?>()
        providers.forEach { (providerName, providerValue) ->
            val name = providerName as? String ?: return@forEach
            val provider = providerValue as? Map<*, *>
            if (provider == null) {
                updatedProviders[name] = providerValue
                return@forEach
            }
            val updatedProvider = provider.normalizedProviderMap()
            updatedProvider.cmfaProviderPath(type.cmfaPrefix)?.let { path ->
                updatedProvider["path"] = "$CmfaProvidersDirectory/$path"
            }
            updatedProviders[name] = updatedProvider
        }
        this[type.topLevelKey] = updatedProviders
    }
}

internal fun String.hasMihomoProxyProviders(): Boolean {
    return parseMihomoYamlRoot()
        ?.get(MihomoProviderType.Proxy.topLevelKey)
        .asProviderMap()
        .isNotEmpty()
}

internal fun String.mihomoRemoteProviderFiles(
    dataDir: File,
    type: MihomoProviderType,
): List<File> {
    val root = parseMihomoYamlRoot() ?: return emptyList()
    return root[type.topLevelKey]
        .asProviderMap()
        .values
        .mapNotNull { providerValue ->
            val provider = providerValue as? Map<*, *> ?: return@mapNotNull null
            provider["url"].asProviderTextOrNull() ?: return@mapNotNull null
            val path = provider.cmfaProviderPath(type.cmfaPrefix) ?: return@mapNotNull null
            File(dataDir, "$CmfaProvidersDirectory/$path")
        }
        .distinctBy { file -> file.absolutePath }
}

internal fun mihomoProxyProviderFileCandidates(dataDir: File, provider: Map<*, *>): List<File> {
    return buildList {
        val path = provider["path"].asProviderTextOrNull()
            ?.takeIf { value -> value.startsWith("$CmfaProvidersDirectory/") }
            ?.resolveAsCmfaProviderRoot()
        if (path != null) {
            add(File(dataDir, path))
        }
        provider.cmfaProviderPath(MihomoProviderType.Proxy.cmfaPrefix)?.let { cmfaPath ->
            add(File(dataDir, "$CmfaProvidersDirectory/$cmfaPath"))
        }
    }.distinctBy { file -> file.absolutePath }
}

private fun String.parseMihomoYamlRoot(): Map<*, *>? {
    val escaped = escapeSupplementaryYamlCodePoints()
    return runCatching {
        val parsed = Load(LoadSettings.builder().build()).loadFromString(escaped.value)
        escaped.restoreParsedValue(parsed) as? Map<*, *>
    }.getOrNull()
}

private fun Map<*, *>.normalizedProviderMap(): LinkedHashMap<String, Any?> {
    return linkedMapOf<String, Any?>().apply {
        this@normalizedProviderMap.forEach { (key, value) ->
            val name = key as? String ?: return@forEach
            put(name, normalizeProviderYamlValue(value))
        }
    }
}

private fun normalizeProviderYamlValue(value: Any?): Any? {
    return when (value) {
        is Map<*, *> -> linkedMapOf<String, Any?>().apply {
            value.forEach { (key, childValue) ->
                val name = key as? String ?: return@forEach
                put(name, normalizeProviderYamlValue(childValue))
            }
        }
        is List<*> -> value.map(::normalizeProviderYamlValue)
        else -> value
    }
}

private fun Map<*, *>.cmfaProviderPath(prefix: String): String? {
    val path = this["path"].asProviderTextOrNull()
    if (path != null) {
        return path.resolveAsCmfaProviderRoot()
    }
    val url = this["url"].asProviderTextOrNull() ?: return null
    return "$prefix/${url.md5Hex()}"
}

private fun String.resolveAsCmfaProviderRoot(): String {
    val directories = split("/")
    val result = mutableListOf<String>()
    directories.forEach { directory ->
        when (directory) {
            "", "." -> Unit
            ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex)
            else -> result.add(directory)
        }
    }
    return result.joinToString("/")
}

private fun String.md5Hex(): String {
    val bytes = MessageDigest.getInstance("MD5").digest(toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun Any?.asProviderTextOrNull(): String? {
    return (this as? String)?.trim()?.takeIf(String::isNotEmpty)
}

private fun Any?.asProviderMap(): Map<*, *> {
    return this as? Map<*, *> ?: emptyMap<Any?, Any?>()
}

private const val CmfaProvidersDirectory = "providers"
