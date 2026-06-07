// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package utils

import io.ktor.http.decodeURLQueryComponent

internal fun String.decodeUrlComponentPreservingPlus(): String {
    return runCatching {
        decodeURLQueryComponent(plusIsSpace = false)
    }.getOrElse { this }
}
