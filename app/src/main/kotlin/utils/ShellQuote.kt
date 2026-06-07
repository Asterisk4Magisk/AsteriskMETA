// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package utils

internal fun String.shellQuote(): String {
    return "'${replace("'", "'\"'\"'")}'"
}

internal fun String.shellQuoteForCase(): String {
    return replace("\\", "\\\\")
        .replace("'", "'\"'\"'")
        .replace("*", "\\*")
        .replace("?", "\\?")
        .replace("[", "\\[")
}
