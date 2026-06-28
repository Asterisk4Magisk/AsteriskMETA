// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package utils

import kotlin.io.encoding.Base64

internal fun ByteArray.encodeBase64(): String {
    return Base64.encode(this)
}

internal fun String.encodeBase64(): String {
    return toByteArray(Charsets.UTF_8).encodeBase64()
}

internal fun String.decodeBase64OrNull(): ByteArray? {
    return runCatching {
        Base64.decode(this)
    }.getOrNull()
}

internal fun String.decodeBase64StringOrNull(): String? {
    return decodeBase64OrNull()?.toString(Charsets.UTF_8)
}

