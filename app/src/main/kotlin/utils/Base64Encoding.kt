// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package utils

import kotlin.io.encoding.Base64

internal fun ByteArray.encodeBase64(): String {
    return Base64.Default.encode(this)
}

internal fun String.decodeBase64OrNull(): ByteArray? {
    return runCatching {
        Base64.Default.decode(this)
    }.getOrNull()
}

