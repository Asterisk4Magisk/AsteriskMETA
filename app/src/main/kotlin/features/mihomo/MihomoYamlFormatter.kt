// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Parse
import org.snakeyaml.engine.v2.api.lowlevel.Present

internal fun formatMihomoYaml(source: String): String {
    // Re-emit syntax events rather than loading objects, preserving comments,
    // anchors, scalar styles and quoted values such as numeric passwords.
    val loadSettings = LoadSettings.builder().setParseComments(true).build()
    val dumpSettings = DumpSettings.builder()
        .setDumpComments(true)
        .setIndent(2)
        .setSplitLines(false)
        .build()
    return Present(dumpSettings).emitToString(Parse(loadSettings).parseString(source).iterator())
}
