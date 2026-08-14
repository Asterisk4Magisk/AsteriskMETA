// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.publication

import android.content.Context
import features.resources.runtime.MihomoResourceFilePaths
import features.resources.runtime.mihomoResourceFilePaths
import java.io.File

private const val RootConfigFileName = "config.yaml"

internal data class RootRuntimeLayout(
    val configPath: String,
    val mihomoCorePath: String,
    val asteriskdPath: String,
    val bpfMatcherPath: String,
    val bpf2socksPath: String,
    val hevSocks5TunnelPath: String,
    val dataDir: String,
) {
    val startupScriptPath: String
        get() = dataDir.posixChild("startup.sh")

    val asteriskdConfigPath: String
        get() = dataDir.posixChild("asteriskd.json")

    val asteriskdStatePath: String
        get() = dataDir.posixChild("asteriskd.state")

    val asteriskdLogPath: String
        get() = dataDir.posixChild("logs").posixChild("asteriskd.log")
}

internal fun Context.prepareRootRuntimeLayout(): RootRuntimeLayout = rootRuntimeLayout()

internal fun Context.rootRuntimeLayout(): RootRuntimeLayout = mihomoResourceFilePaths().toRootRuntimeLayout()

internal fun Context.prepareRootPublicationDirectories(): RootRuntimeLayout {
    val layout = rootRuntimeLayout()
    val dataDirectory = File(layout.dataDir)
    require(dataDirectory.exists() || dataDirectory.mkdirs())
    require(dataDirectory.isDirectory && dataDirectory.hasSafeRootPublicationIdentity(filesDir))
    val logDirectory = File(dataDirectory, "logs")
    require(logDirectory.exists() || logDirectory.mkdirs())
    require(
        logDirectory.isDirectory &&
            logDirectory.absoluteFile.parentFile == dataDirectory.absoluteFile &&
            logDirectory.canonicalFile.parentFile == dataDirectory.canonicalFile &&
            logDirectory.canonicalFile.name == "logs",
    )
    return layout
}

private fun File.hasSafeRootPublicationIdentity(filesDirectory: File): Boolean =
    isSafeRootPublicationDirectoryIdentity(
        directoryAbsolutePath = absolutePath,
        directoryCanonicalPath = canonicalPath,
        filesAbsolutePath = filesDirectory.absolutePath,
        filesCanonicalPath = filesDirectory.canonicalPath,
    )

internal fun isSafeRootPublicationDirectoryIdentity(
    directoryAbsolutePath: String,
    directoryCanonicalPath: String,
    filesAbsolutePath: String,
    filesCanonicalPath: String,
): Boolean {
    val absoluteDirectory = File(directoryAbsolutePath)
    val canonicalDirectory = File(directoryCanonicalPath)
    return absoluteDirectory.parentFile == File(filesAbsolutePath) &&
        canonicalDirectory.parentFile == File(filesCanonicalPath) &&
        absoluteDirectory.name == canonicalDirectory.name
}

internal fun MihomoResourceFilePaths.toRootRuntimeLayout(): RootRuntimeLayout {
    val dir = File(dataDir)
    return RootRuntimeLayout(
        configPath = File(dir, RootConfigFileName).absolutePath,
        mihomoCorePath = mihomoCorePath,
        asteriskdPath = asteriskdPath,
        bpfMatcherPath = bpfMatcherPath,
        bpf2socksPath = bpf2socksPath,
        hevSocks5TunnelPath = hevSocks5TunnelPath,
        dataDir = dataDir,
    )
}

private fun String.posixChild(name: String): String = "${trimEnd('/')}/$name"
