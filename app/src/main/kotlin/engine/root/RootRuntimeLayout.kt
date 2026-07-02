// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import android.content.Context
import features.resources.runtime.MihomoResourceFilePaths
import features.resources.runtime.prepareMihomoResourceFilePaths
import java.io.File

internal data class RootRuntimeLayout(
    val configPath: String,
    val mihomoCorePath: String,
    val ipv6DisablerPath: String,
    val bpfMatcherPath: String,
    val bpf2socksPath: String,
    val hevSocks5TunnelPath: String,
    val dataDir: String,
    val pidPath: String,
)

internal fun Context.prepareRootRuntimeLayout(): RootRuntimeLayout {
    val resourceFilePaths = prepareMihomoResourceFilePaths()
    return resourceFilePaths.toRootRuntimeLayout()
}

internal fun MihomoResourceFilePaths.toRootRuntimeLayout(): RootRuntimeLayout {
    val dir = File(dataDir)
    return RootRuntimeLayout(
        configPath = File(dir, RootConfigFileName).absolutePath,
        mihomoCorePath = mihomoCorePath,
        ipv6DisablerPath = ipv6DisablerPath,
        bpfMatcherPath = bpfMatcherPath,
        bpf2socksPath = bpf2socksPath,
        hevSocks5TunnelPath = hevSocks5TunnelPath,
        dataDir = dataDir,
        pidPath = File(dir, RootPidFileName).absolutePath,
    )
}
