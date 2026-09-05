// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.common.FlowStyle
import java.io.File
import java.security.MessageDigest

internal data class MihomoProviderDeclaration(
    val name: String,
    val providerType: MihomoProviderType,
    val vehicleType: String,
    val sourceSummary: String,
    val declarationYaml: String,
    val rawSource: MihomoProviderRawSource,
    val ageSecretKey: String = "",
    val ruleMetadata: MihomoRuleProviderDeclarationMetadata? = null,
)

internal data class MihomoRuleProviderDeclarationMetadata(
    val behavior: String,
    val format: String,
    val tunBypassEligible: Boolean = false,
)

internal sealed interface MihomoProviderRawSource {
    data class Inline(val content: String) : MihomoProviderRawSource
    data class File(val candidates: List<java.io.File>) : MihomoProviderRawSource
    data object Missing : MihomoProviderRawSource
}

internal sealed interface MihomoProviderRawContent {
    val lastError: String

    data class Text(
        val content: String = "",
        override val lastError: String = "",
        val declarationOnly: Boolean = false,
    ) : MihomoProviderRawContent

    data class Binary(
        val byteSize: Long,
        val format: String,
        val ruleCount: Int? = null,
        override val lastError: String = "",
    ) : MihomoProviderRawContent
}

internal fun String.parseMihomoProviderDeclarations(
    dataDir: File,
    type: MihomoProviderType,
): List<MihomoProviderDeclaration> {
    val root = parseMihomoYamlRoot() ?: return emptyList()
    val providers = root[type.topLevelKey].asProviderMap()
    return providers.mapNotNull { (providerName, providerValue) ->
        val name = providerName.asProviderTextOrNull() ?: return@mapNotNull null
        val provider = providerValue as? Map<*, *> ?: return@mapNotNull null
        provider.toMihomoProviderDeclaration(name, type, dataDir)
    }
}

internal fun String.hasMihomoProvider(type: MihomoProviderType): Boolean {
    return parseMihomoYamlRoot()
        ?.get(type.topLevelKey)
        .asProviderMap()
        .isNotEmpty()
}

internal fun String.hasMihomoProviders(): Boolean {
    val root = parseMihomoYamlRoot() ?: return false
    return MihomoProviderType.entries.any { type ->
        root[type.topLevelKey].asProviderMap().isNotEmpty()
    }
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
            val normalizedProvider = provider.normalizedProviderMap()
            normalizedProvider["url"].asProviderTextOrNull() ?: return@mapNotNull null
            val path = normalizedProvider.cmfaProviderPath(type.cmfaPrefix) ?: return@mapNotNull null
            File(dataDir, "$CmfaProvidersDirectory/$path")
        }
        .distinctBy { file -> file.absolutePath }
}

internal fun Map<*, *>.normalizedProviderMap(): LinkedHashMap<String, Any?> {
    val normalized = linkedMapOf<String, Any?>()
    normalized.mergeProviderYamlValue(this["<<"])
    forEach { (key, value) ->
        val name = key as? String ?: return@forEach
        if (name == "<<") return@forEach
        normalized[name] = normalizeProviderYamlValue(value)
    }
    return normalized
}

internal fun Any?.asProviderTextOrNull(): String? {
    return (this as? String)?.trim()?.takeIf(String::isNotEmpty)
}

internal fun Any?.asProviderMap(): Map<*, *> {
    return this as? Map<*, *> ?: emptyMap<Any?, Any?>()
}

internal fun Map<*, *>.cmfaProviderPath(prefix: String): String? {
    val path = this["path"].asProviderTextOrNull()
    if (path != null) {
        return path.resolveAsCmfaProviderRoot()
    }
    val url = this["url"].asProviderTextOrNull() ?: return null
    return "$prefix/${url.md5Hex()}"
}

internal fun Map<*, *>.providerFileCandidates(
    dataDir: File,
    type: MihomoProviderType,
): List<File> {
    return buildList {
        val path = this@providerFileCandidates["path"].asProviderTextOrNull()
            ?.takeIf { value -> value.startsWith("$CmfaProvidersDirectory/") }
            ?.resolveAsCmfaProviderRoot()
        if (path != null) {
            add(File(dataDir, path))
        }
        this@providerFileCandidates.cmfaProviderPath(type.cmfaPrefix)?.let { cmfaPath ->
            add(File(dataDir, "$CmfaProvidersDirectory/$cmfaPath"))
        }
    }.distinctBy { file -> file.absolutePath }
}

internal fun normalizeProviderYamlValue(value: Any?): Any? {
    return when (value) {
        is Map<*, *> -> value.normalizedProviderMap()
        is List<*> -> value.map(::normalizeProviderYamlValue)
        else -> value
    }
}

private fun LinkedHashMap<String, Any?>.mergeProviderYamlValue(value: Any?) {
    when (value) {
        is Map<*, *> -> {
            value.normalizedProviderMap().forEach { (key, childValue) ->
                putIfAbsent(key, childValue)
            }
        }

        is List<*> -> {
            value.forEach { item -> mergeProviderYamlValue(item) }
        }
    }
}

private fun Map<*, *>.toMihomoProviderDeclaration(
    name: String,
    type: MihomoProviderType,
    dataDir: File,
): MihomoProviderDeclaration {
    val normalized = normalizedProviderMap()
    val vehicleType = normalized["type"].asProviderTextOrNull().orEmpty()
    val ageSecretKey = normalized["age-secret-key"].asProviderTextOrNull().orEmpty()
    val declarationYaml = mihomoProviderDeclarationYaml(type, name, normalized)
    val rawSource = when (vehicleType.lowercase()) {
        "inline" -> MihomoProviderRawSource.Inline(declarationYaml)
        "http", "file" -> MihomoProviderRawSource.File(providerFileCandidates(dataDir, type))
        else -> MihomoProviderRawSource.Missing
    }
    val ruleMetadata = if (type == MihomoProviderType.Rule) {
        MihomoRuleProviderDeclarationMetadata(
            behavior = normalized["behavior"].asProviderTextOrNull()?.lowercase().orEmpty(),
            tunBypassEligible = name in usableMihomoTunBypassProviders(mapOf(name to normalized)),
            format = normalized["format"].asProviderTextOrNull()
                ?.lowercase()
                .orEmpty()
                .ifBlank { "yaml" },
        )
    } else {
        null
    }
    return MihomoProviderDeclaration(
        name = name,
        providerType = type,
        vehicleType = vehicleType.ifBlank { "unknown" },
        sourceSummary = providerSourceSummary(vehicleType, normalized),
        declarationYaml = declarationYaml,
        rawSource = rawSource,
        ageSecretKey = ageSecretKey,
        ruleMetadata = ruleMetadata,
    )
}

private fun providerSourceSummary(
    vehicleType: String,
    provider: Map<String, Any?>,
): String {
    return when (vehicleType.lowercase()) {
        "http" -> provider["url"].asProviderTextOrNull().orEmpty()
        "file" -> provider["path"].asProviderTextOrNull().orEmpty()
        "inline" -> "profile payload"
        else -> provider["path"].asProviderTextOrNull()
            ?: provider["url"].asProviderTextOrNull()
            ?: ""
    }
}

private fun mihomoProviderDeclarationYaml(
    type: MihomoProviderType,
    name: String,
    provider: Map<String, Any?>,
): String {
    return dumpProviderYaml(
        linkedMapOf(
            type.topLevelKey to linkedMapOf(
                name to provider,
            ),
        ),
    ).trimEnd()
}

private fun String.parseMihomoYamlRoot(): Map<*, *>? {
    val escaped = escapeSupplementaryYamlCodePoints()
    return runCatching {
        val parsed = Load(MihomoYamlLoadSettings).loadFromString(escaped.value)
        escaped.restoreParsedValue(parsed) as? Map<*, *>
    }.getOrNull()
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

private fun dumpProviderYaml(value: Any?): String {
    return Dump(ProviderYamlDumpSettings).dumpToString(value)
}

private val ProviderYamlDumpSettings = DumpSettings.builder()
    .setDefaultFlowStyle(FlowStyle.BLOCK)
    .build()

internal const val CmfaProvidersDirectory = "providers"
