// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import android.content.Intent
import android.net.Uri
import app.DefaultMihomoProfileUpdateInterval
import io.ktor.http.Url
import utils.decodeUrlComponentPreservingPlus

internal data class SubscriptionInstallConfig(
    val name: String,
    val url: String,
    val userAgent: String,
    val updateInterval: String = DefaultMihomoProfileUpdateInterval,
    val updateViaProxy: Boolean = false,
)

internal fun Intent.toSubscriptionInstallConfigOrNull(): SubscriptionInstallConfig? {
    if (action != Intent.ACTION_VIEW) return null
    return data?.toString()?.toSubscriptionInstallConfigOrNull()
}

internal fun String.toSubscriptionInstallConfigOrNull(): SubscriptionInstallConfig? {
    val value = trim()
    if (value.any(Char::isWhitespace)) return null
    val url = runCatching { Url(value) }.getOrNull() ?: return null
    return url.toSubscriptionInstallConfigOrNull(value)
}

internal fun String.isValidManualSubscriptionUrl(): Boolean {
    return isValidSubscriptionUrl(ManualSubscriptionUrlSchemes)
}

internal fun String.isPlainHttpSubscriptionUrl(): Boolean {
    val url = toSubscriptionUrlOrNull() ?: return false
    return url.protocol.name.equals("http", ignoreCase = true)
}

internal fun Uri.isSubscriptionInstallConfigUri(): Boolean {
    return runCatching { Url(toString()).isSubscriptionInstallConfigUri() }
        .getOrDefault(false)
}

private fun Url.toSubscriptionInstallConfigOrNull(rawValue: String): SubscriptionInstallConfig? {
    toRawSubscriptionInstallConfigOrNull(
        rawValue = rawValue,
        schemes = HttpsSubscriptionUrlSchemes,
    )?.let { return it }
    val url = parameters["url"]?.trim().orEmpty()
    if (!isSubscriptionInstallConfigUri() || !url.isValidSubscriptionUrl()) return null
    // Ktor has already decoded query parameters; preserve literal percent escapes in names.
    val name = parameters["name"]?.trim()?.takeIf(String::isNotBlank) ?: listOfNotNull(
        fragment,
        url.toSubscriptionUrlFragmentOrNull(),
        DefaultSubscriptionName,
    )
        .firstNotNullOfOrNull { value -> value.trim().decodeUrlComponentPreservingPlus().takeIf(String::isNotBlank) }
        ?: return null
    return SubscriptionInstallConfig(
        name = name,
        url = url,
        userAgent = app.DefaultMihomoProfileUserAgent,
    )
}

private fun Url.toRawSubscriptionInstallConfigOrNull(
    rawValue: String,
    schemes: Set<String>,
): SubscriptionInstallConfig? {
    if (!rawValue.isValidSubscriptionUrl(schemes)) return null
    val name = listOfNotNull(fragment, DefaultSubscriptionName)
        .firstNotNullOfOrNull { value -> value.trim().decodeUrlComponentPreservingPlus().takeIf(String::isNotBlank) }
        ?: return null
    return SubscriptionInstallConfig(
        name = name,
        url = rawValue,
        userAgent = app.DefaultMihomoProfileUserAgent,
    )
}

private fun String.isValidSubscriptionUrl(
    schemes: Set<String> = HttpsSubscriptionUrlSchemes,
): Boolean {
    val url = toSubscriptionUrlOrNull() ?: return false
    val scheme = url.protocol.name.lowercase()
    return url.host.isNotBlank() &&
        scheme in schemes &&
        this.any(Char::isWhitespace).not()
}

private fun String.toSubscriptionUrlOrNull(): Url? {
    val value = trim()
    if (value.any(Char::isWhitespace)) return null
    return runCatching { Url(value) }.getOrNull()
}

private fun Url.isSubscriptionInstallConfigUri(): Boolean {
    return protocol.name.lowercase() in InstallConfigSchemes &&
        host.lowercase() in InstallConfigHosts
}

private fun String.toSubscriptionUrlFragmentOrNull(): String? {
    return runCatching { Url(this).fragment }.getOrNull()
}

private const val DefaultSubscriptionName = "clashsub"
private val InstallConfigSchemes = setOf("clash", "clashmeta")
private val InstallConfigHosts = setOf("install-config", "install-sub")
private val HttpsSubscriptionUrlSchemes = setOf("https")
private val ManualSubscriptionUrlSchemes = setOf("http", "https")
