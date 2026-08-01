// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import android.content.Context
import com.github.kr328.clash.core.Clash
import engine.mihomo.MihomoProviderDeclaration
import engine.mihomo.MihomoProviderRawContent
import engine.mihomo.MihomoProviderRawSource
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
            val header = file.inputStream().use { input ->
                val bytes = ByteArray(MihomoProviderHeaderSize)
                val count = input.read(bytes)
                if (count > 0) bytes.copyOf(count) else byteArrayOf()
            }
            val format = provider.ruleMetadata?.format.orEmpty()
            if (isMihomoProviderBinaryContent(format, header)) {
                return@runCatching MihomoProviderRawContent.Binary(
                    byteSize = file.length(),
                    format = format.ifBlank { MihomoMrsFormat },
                )
            }
            val content = file.readText(Charsets.UTF_8)
                .decryptAge(provider.ageSecretKey.ifBlank { profileAgeSecretKey })
            MihomoProviderRawContent.Text(
                content = content,
            )
        }.getOrElse { error ->
            providerDeclarationFallbackContent(
                declarationYaml = provider.declarationYaml,
                errorMessage = error.message.orEmpty(),
                unavailableMessage = unavailableMessage,
            )
        }
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

internal fun isMihomoProviderBinaryContent(format: String, header: ByteArray): Boolean {
    val hasMrsMagic = header.size >= 4 &&
        header[0] == 'M'.code.toByte() &&
        header[1] == 'R'.code.toByte() &&
        header[2] == 'S'.code.toByte() &&
        header[3] == 1.toByte()
    return format.equals("mrs", ignoreCase = true) || hasMrsMagic
}

private fun String.decryptAge(ageSecretKey: String): String {
    return Clash.decryptAge(this, ageSecretKey.trim().takeIf(String::isNotBlank))
}

private const val MihomoProviderHeaderSize = 4
private const val MihomoMrsFormat = "mrs"
