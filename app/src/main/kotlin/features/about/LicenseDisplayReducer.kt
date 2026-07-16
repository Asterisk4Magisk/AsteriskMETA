// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.about

import features.about.license.Library

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
    mihomoVersion: String,
    wrapperVersion: String,
): AboutIdentityState = AboutIdentityState(
    projectName = projectName,
    versionLabel = "v$versionName ($versionCode)",
    runtimeSummary = "Mihomo $mihomoVersion · CMFA $wrapperVersion",
)
