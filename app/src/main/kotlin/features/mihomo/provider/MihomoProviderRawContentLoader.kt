// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import android.content.Context
import engine.mihomo.binding.MihomoBridge as Clash
import engine.mihomo.MihomoProviderDeclaration
import engine.mihomo.MihomoProviderRawContent
import engine.mihomo.MihomoProviderRawSource
import engine.mihomo.MihomoRuleSetMrs
import features.resources.runtime.prepareMihomoResourceFilePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal class MihomoProviderRawContentLoader {
    suspend fun load(
        provider: MihomoProviderDeclaration,
        profileAgeSecretKey: String,
        unavailableMessage: String,
    ): MihomoProviderRawContent = withContext(Dispatchers.IO) {
        when (val source = provider.rawSource) {
            is MihomoProviderRawSource.Inline -> MihomoProviderRawContent.Text(
                content = source.content,
                declarationOnly = true,
            )

            is MihomoProviderRawSource.File -> loadFileProvider(
                provider = provider,
                source = source,
                profileAgeSecretKey = profileAgeSecretKey,
                unavailableMessage = unavailableMessage,
            )

            MihomoProviderRawSource.Missing -> MihomoProviderRawContent.Text(
                content = provider.declarationYaml,
                declarationOnly = true,
                lastError = unavailableMessage,
            )
        }
    }

    private fun loadFileProvider(
        provider: MihomoProviderDeclaration,
        source: MihomoProviderRawSource.File,
        profileAgeSecretKey: String,
        unavailableMessage: String,
    ): MihomoProviderRawContent {
        val file = source.candidates.firstOrNull { candidate -> candidate.isFile }
            ?: source.candidates.firstOrNull()
        if (file == null) {
            return providerDeclarationFallbackContent(
                declarationYaml = provider.declarationYaml,
                errorMessage = "",
                unavailableMessage = unavailableMessage,
            )
        }
        if (!file.isFile) {
            return providerDeclarationFallbackContent(
                declarationYaml = provider.declarationYaml,
                errorMessage = "",
                unavailableMessage = unavailableMessage,
            )
        }
        return runCatching {
            val bytes = file.readBytes()
            val format = provider.ruleMetadata?.format.orEmpty()
            if (MihomoRuleSetMrs.matches(format, bytes)) {
                return@runCatching decodeRuleSet(file = file, bytes = bytes, format = format)
            }
            val content = String(bytes, Charsets.UTF_8)
                .decryptAge(provider.ageSecretKey.ifBlank { profileAgeSecretKey })
            MihomoProviderRawContent.Text(
                content = content,
                byteSize = file.length(),
            )
        }.getOrElse { error ->
            providerDeclarationFallbackContent(
                declarationYaml = provider.declarationYaml,
                errorMessage = error.message.orEmpty(),
                unavailableMessage = unavailableMessage,
            )
        }
    }

    /**
     * Restores a binary rule set as text. A rule set keeps its binary card only when the payload
     * cannot be read, which stays diagnosable through the card's error line.
     */
    private fun decodeRuleSet(
        file: File,
        bytes: ByteArray,
        format: String,
    ): MihomoProviderRawContent {
        return runCatching { MihomoRuleSetMrs.decode(bytes) }.fold(
            onSuccess = { decoded ->
                MihomoProviderRawContent.Text(
                    content = decoded.rules.joinToString(LineSeparator),
                    sourceFormat = MihomoRuleSetMrs.Format,
                    byteSize = file.length(),
                )
            },
            onFailure = { error ->
                MihomoProviderRawContent.Binary(
                    byteSize = file.length(),
                    format = format.ifBlank { MihomoRuleSetMrs.Format },
                    lastError = error.message.orEmpty(),
                )
            },
        )
    }
}

internal fun providerDeclarationFallbackContent(
    declarationYaml: String,
    errorMessage: String,
    unavailableMessage: String,
): MihomoProviderRawContent.Text {
    return MihomoProviderRawContent.Text(
        content = declarationYaml,
        lastError = errorMessage.ifBlank { unavailableMessage },
        declarationOnly = true,
    )
}

internal fun Context.mihomoProviderDataDir(): File {
    return File(applicationContext.prepareMihomoResourceFilePaths().dataDir)
}

private fun String.decryptAge(ageSecretKey: String): String {
    return Clash.decryptAge(this, ageSecretKey.trim().takeIf(String::isNotBlank))
}

private const val LineSeparator = "\n"
