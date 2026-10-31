// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.about

import app.ProjectInfo
import features.about.license.Library
import features.about.license.Scm

internal fun List<Library>.forBundledCore(): List<Library> {
    if (ProjectInfo.CORE_ID != "meow") return this
    return map { library ->
        when (library.uniqueId) {
            "github:MetaCubeX/mihomo" -> library.copy(
                uniqueId = "github:meow-rs/meow-rs",
                name = "Meow",
                description = "Rust proxy core bundled with this build.",
                website = "https://github.com/meow-rs/meow-rs",
                scm = Scm(url = "https://github.com/meow-rs/meow-rs"),
                licenses = listOf("MIT"),
            )
            "github:Asterisk4Magisk/AndroidLibClashLite" -> library.copy(
                description = "Android bindings and standalone CLI sharing one Meow core.",
            )
            else -> library
        }
    }
}

internal fun reduceLibraries(libraries: List<Library>, query: String): List<Library> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return libraries
    return libraries.filter { library ->
        buildList {
            add(library.name)
            add(library.uniqueId)
            library.artifactVersion?.let(::add)
            library.description?.let(::add)
            addAll(library.licenses)
        }.any { value -> value.contains(normalizedQuery, ignoreCase = true) }
    }
}

internal fun hasLibraryWebsite(library: Library): Boolean = !library.website.isNullOrBlank()

internal data class AboutIdentityState(
    val projectName: String,
    val versionLabel: String,
    val runtimeSummary: String,
)

internal fun buildAboutIdentityState(
    projectName: String,
    versionName: String,
    versionCode: Int,
    androidLibClashLiteVersion: String,
): AboutIdentityState = AboutIdentityState(
    projectName = projectName,
    versionLabel = "v$versionName ($versionCode)",
    runtimeSummary = "AndroidLibClashLite $androidLibClashLiteVersion",
)
