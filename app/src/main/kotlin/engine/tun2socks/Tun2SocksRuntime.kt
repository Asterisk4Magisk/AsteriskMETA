// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.tun2socks

import android.content.Context
import app.AppState
import engine.root.RootRuntimeLayout
import engine.root.appendScript
import engine.root.buildRootProcessMatchCommand
import engine.root.buildRootProcessMatchTest
import engine.mihomo.MihomoCoreLogPaths
import engine.mihomo.logDirectoryPath
import engine.mihomo.prepareMihomoCoreLogPaths
import features.resources.runtime.mihomoResourceFilesDir
import utils.shellQuote
import utils.writeAtomically
import java.io.File

internal fun Context.writeHevSocks5TunnelConfigFile(appState: AppState) {
    applicationContext.prepareHevSocks5TunnelConfig(appState).writeConfigFile()
}

internal fun HevSocks5TunnelConfig.writeConfigFile() {
    writeAtomically(File(configPath)) { output ->
        output.write(buildHevSocks5TunnelConfigYaml().toByteArray(Charsets.UTF_8))
    }
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

private fun HevSocks5TunnelConfig.buildHevSocks5TunnelConfigYaml(): String {
    return buildString {
        appendLine("tunnel:")
        appendLine("  name: 'asterisk0'")
        appendLine("  mtu: $mtu")
        appendLine("  multi-queue: true")
        appendLine("  ipv4: ${ipv4Address.toYamlSingleQuotedString()}")
        ipv6Address?.let { address ->
            appendLine("  ipv6: ${address.toYamlSingleQuotedString()}")
        }
        appendLine("socks5:")
        appendLine("  port: $socksPort")
        appendLine("  address: ${Tun2SocksListenAddress.toYamlSingleQuotedString()}")
        appendLine("  udp: 'udp'")
        appendLine("  tcp-fastopen: true")
    }
}

internal fun MihomoCoreLogPaths.hevSocks5TunnelLogFile(): File {
    return File(logDirectoryPath(), HevSocks5TunnelLogFileName)
}

internal fun Context.deleteHevSocks5TunnelConfigFile() {
    val file = File(applicationContext.mihomoResourceFilesDir(), HevSocks5TunnelConfigFileName)
    if (file.exists() && !file.delete()) {
        error("Failed to delete ${file.absolutePath}")
    }
}

internal fun Context.deleteHevSocks5TunnelLogFile() {
    val file = applicationContext.prepareMihomoCoreLogPaths().hevSocks5TunnelLogFile()
    if (file.exists() && !file.delete()) {
        error("Failed to delete ${file.absolutePath}")
    }
}

private fun String.toYamlSingleQuotedString(): String {
    return "'${replace("'", "''")}'"
}

private const val HevSocks5TunnelUid = 0
private const val HevSocks5TunnelGid = 0
