// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import utils.shellQuote

internal fun StringBuilder.appendScript(script: String) {
    append(script.trimIndent())
    append('\n')
}

internal fun StringBuilder.appendHeredoc(
    targetPath: String,
    content: String,
) {
    appendScript("cat > ${targetPath.shellQuote()} <<'$BootScriptHeredocDelimiter'")
    append(content)
    if (!content.endsWith('\n')) {
        append('\n')
    }
    appendScript(BootScriptHeredocDelimiter)
}

internal fun StringBuilder.appendDeleteRuleLoop(
    command: String,
    chain: String,
    rule: String,
    table: String = "mangle",
) {
    appendScript("while $command -t $table -D $chain $rule 2>/dev/null; do :; done")
}

