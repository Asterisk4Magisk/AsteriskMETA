// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.tun2socks

import android.content.Context
import app.AppState
import engine.hevtun.HevSocks5TunnelConfig
import engine.hevtun.HevSocks5TunnelConfigFileName
import engine.hevtun.HevSocks5TunnelPidFileName
import engine.hevtun.writeConfigFile
import engine.root.RootRuntimeLayout
import engine.root.appendScript
import engine.root.buildRootProcessMatchCommand
import engine.root.buildRootProcessMatchTest
import features.resources.runtime.mihomoResourceFilesDir
import utils.shellQuote
import java.io.File

internal fun Context.writeHevSocks5TunnelConfigFile(appState: AppState) {
    applicationContext.prepareHevSocks5TunnelConfig(appState).writeConfigFile()
}

internal fun HevSocks5TunnelConfig.buildStartCommand(): String {
    val logDirPath = File(logPath).parentFile?.absolutePath
    return buildString {
        logDirPath?.let { path ->
            appendScript("mkdir -p ${path.shellQuote()} || exit 1")
        }
        appendScript(
            $$"""
            rm -f $${pidPath.shellQuote()} 2>/dev/null || true
            rm -f $${logPath.shellQuote()} 2>/dev/null || true
            test -r $${configPath.shellQuote()} || exit 1
            touch $${logPath.shellQuote()} || exit 1
            chmod 666 $${logPath.shellQuote()} || exit 1
            chmod 755 $${executablePath.shellQuote()} || exit 1
            ulimit -SHn 1000000 2>/dev/null || true
            $${executablePath.shellQuote()} $${configPath.shellQuote()} >> $${logPath.shellQuote()} 2>&1 < /dev/null &
            echo $! > $${pidPath.shellQuote()}
            """,
        )
    }
}

internal fun RootRuntimeLayout.buildStopHevSocks5TunnelCommand(): String {
    val dir = File(dataDir)
    val pidPath = File(dir, HevSocks5TunnelPidFileName).absolutePath
    return buildString {
        appendScript(
            $$"""
            pid="$(cat $${pidPath.shellQuote()} 2>/dev/null || true)"
            if [ -n "$pid" ] && $${buildRootProcessMatchTest(hevSocks5TunnelPath, HevSocks5TunnelUid, HevSocks5TunnelGid).trimEnd()}; then
                kill "$pid" 2>/dev/null || true
                sleep 0.2
                kill -9 "$pid" 2>/dev/null || true
            fi
            rm -f $${pidPath.shellQuote()} 2>/dev/null || true
            """,
        )
    }
}

internal fun HevSocks5TunnelConfig.buildProcessMatchCommand(): String {
    return buildHevSocks5TunnelProcessMatchCommand(
        pidPath = pidPath,
        executablePath = executablePath,
    )
}

internal fun RootRuntimeLayout.buildHevSocks5TunnelRuntimeReadyCommand(): String {
    val dir = File(dataDir)
    return buildString {
        append(
            buildHevSocks5TunnelProcessMatchCommand(
                pidPath = File(dir, HevSocks5TunnelPidFileName).absolutePath,
                executablePath = hevSocks5TunnelPath,
            ),
        )
        appendScript("ip link show dev 'asterisk0' >/dev/null 2>&1")
    }
}

private fun buildHevSocks5TunnelProcessMatchCommand(
    pidPath: String,
    executablePath: String,
): String {
    return buildRootProcessMatchCommand(
        pidPath = pidPath,
        executablePath = executablePath,
        uid = HevSocks5TunnelUid,
        gid = HevSocks5TunnelGid,
    )
}

internal fun Context.deleteHevSocks5TunnelConfigFile() {
    val file = File(applicationContext.mihomoResourceFilesDir(), HevSocks5TunnelConfigFileName)
    if (file.exists() && !file.delete()) {
        error("Failed to delete ${file.absolutePath}")
    }
}

private const val HevSocks5TunnelUid = 0
private const val HevSocks5TunnelGid = 0
