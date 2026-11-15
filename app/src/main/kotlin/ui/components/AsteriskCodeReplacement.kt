// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import io.github.rosemoe.sora.text.TextUtils
import java.util.regex.Pattern

/** Use Sora's matching rules and literal replacement text, including when searching with a regex. */
internal fun replaceCodeSearchMatches(
    text: String,
    query: String,
    matchCase: Boolean,
    useRegex: Boolean,
    replacement: String,
): String {
    require(query.isNotEmpty())
    return buildString(text.length) {
        var offset = 0
        if (useRegex) {
            val flags = Pattern.MULTILINE or if (matchCase) 0 else Pattern.CASE_INSENSITIVE
            val matcher = Pattern.compile(query, flags).matcher(text)
            while (matcher.find()) {
                append(text, offset, matcher.start())
                append(replacement)
                offset = matcher.end()
                // Sora ends its regex scan when a match reaches the end of the document.
                if (offset == text.length) break
            }
        } else {
            var next = TextUtils.indexOf(text, query, !matchCase, offset)
            while (next >= 0) {
                append(text, offset, next)
                append(replacement)
                offset = next + query.length
                next = TextUtils.indexOf(text, query, !matchCase, offset)
            }
        }
        append(text, offset, text.length)
    }
}
