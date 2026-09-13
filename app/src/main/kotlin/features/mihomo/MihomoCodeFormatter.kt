// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import android.content.Context
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

internal suspend fun formatMihomoCode(
    context: Context,
    source: String,
    language: MihomoCodeLanguage,
): String = withContext(Dispatchers.Default) {
    if (language == MihomoCodeLanguage.Yaml) return@withContext formatMihomoYaml(source)
    quickJs {
        memoryLimit = 32L * 1024 * 1024
        maxStackSize = 1024L * 1024
        // Compile only: reject invalid input without executing the user's script.
        compile(source, filename = "editor-content.js")
        val formatter = context.assets.open("code-formatter/beautify.min.js")
            .bufferedReader().use { it.readText() }
        evaluate<Boolean>(code = "var exports = {};\n$formatter\n;true", filename = "beautify.min.js")
        val formatted = evaluate<String>(
            code = """
                const source = ${JsonPrimitive(source)};
                exports.js_beautify(source, {
                    indent_size: 2,
                    indent_with_tabs: false,
                    preserve_newlines: true,
                    max_preserve_newlines: 2,
                    end_with_newline: source.trim().length > 0,
                    eol: "\n",
                });
            """.trimIndent(),
            filename = "format-editor-content.js",
        )
        compile(formatted, filename = "formatted-editor-content.js")
        formatted
    }
}
