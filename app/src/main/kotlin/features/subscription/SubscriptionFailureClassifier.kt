// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private val httpStatusRegex = Regex("""(?i)\bHTTP\s+(\d{3})\b""")

internal fun isTransientSubscriptionFailure(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any { cause ->
        when (cause) {
            is ConnectException,
            is NoRouteToHostException,
            is SocketException,
            is SocketTimeoutException,
            is UnknownHostException,
            -> true

            else -> {
                val statusCode =
                    cause.message
                        ?.let(httpStatusRegex::find)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                statusCode == 408 || statusCode == 429 || (statusCode != null && statusCode in 500..599)
            }
        }
    }
