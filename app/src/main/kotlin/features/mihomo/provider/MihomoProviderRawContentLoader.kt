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
            is MihomoProviderRawSource.Inline -> MihomoProviderRawContent(
                content = source.content,
                declarationOnly = true,
            )

            is MihomoProviderRawSource.File -> loadFileProvider(
                provider = provider,
                source = source,
                profileAgeSecretKey = profileAgeSecretKey,
                unavailableMessage = unavailableMessage,
            )

            MihomoProviderRawSource.Missing -> MihomoProviderRawContent(
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
            return MihomoProviderRawContent(
                lastError = unavailableMessage,
            )
        }
        if (!file.isFile) {
            return MihomoProviderRawContent(
                lastError = unavailableMessage,
            )
        }
        return runCatching {
            val content = file.readText(Charsets.UTF_8)
                .decryptAge(provider.ageSecretKey.ifBlank { profileAgeSecretKey })
            MihomoProviderRawContent(
                content = content,
            )
        }.getOrElse { error ->
            MihomoProviderRawContent(
                lastError = error.message.orEmpty(),
            )
        }
    }
}

internal fun Context.mihomoProviderDataDir(): File {
    return File(applicationContext.prepareMihomoResourceFilePaths().dataDir)
}

private fun String.decryptAge(ageSecretKey: String): String {
    return Clash.decryptAge(this, ageSecretKey.trim().takeIf(String::isNotBlank))
}
