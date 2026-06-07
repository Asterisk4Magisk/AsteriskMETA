// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import android.annotation.SuppressLint
import engine.mihomo.MihomoCoreLogPaths
import engine.mihomo.logFilePaths
import engine.mihomo.logDirectoryPath
import system.AndroidRootShellGateway
import system.ShellExecOptions
import utils.shellQuote
import utils.shellQuoteForCase
import utils.toTrimmedNonEmptyList
import utils.writeAtomically
import java.io.File

@SuppressLint("SetWorldReadable")
internal fun writeRootConfigFile(config: RootStartConfig) {
    val configFile = File(config.configPath)
    writeAtomically(configFile) { output ->
        output.write(config.mihomoProfileYaml.toByteArray(Charsets.UTF_8))
    }
    configFile.setReadable(true, false)
    configFile.setWritable(true, true)
}

internal fun buildRemoveRootBootScriptCommand(
    runtimeLayout: RootRuntimeLayout,
    coreLogPaths: MihomoCoreLogPaths,
): String {
    val bootLogPath = File(coreLogPaths.logDirectoryPath(), RootBootLogFileName).absolutePath
    return buildString {
        appendScript("rm -f ${RootBootScriptPath.shellQuote()} 2>/dev/null || true")
        appendScript("rm -f ${runtimeLayout.startupScriptPath.shellQuote()} 2>/dev/null || true")
        appendScript("rm -f ${bootLogPath.shellQuote()} 2>/dev/null || true")
    }
}

internal suspend fun AndroidRootShellGateway.removeRootBootScript(
    runtimeLayout: RootRuntimeLayout,
    coreLogPaths: MihomoCoreLogPaths,
    failureMessage: String,
) {
    val result = exec(
        buildRemoveRootBootScriptCommand(runtimeLayout, coreLogPaths),
        ShellExecOptions(logFailure = false),
    )
    if (result.errno != 0) {
        error(
            buildRootDiagnosticMessage(
                failureMessage,
                result.stderr,
                result.stdout,
            ),
        )
    }
}

internal fun buildRootStopCommand(
    runtimeLayout: RootRuntimeLayout?,
    uid: Int,
    gid: Int,
    cleanupRulesCommand: String,
): String {
    return buildString {
        runtimeLayout?.let { paths ->
            appendScript(
                $$"""
                pid="$(cat $${paths.pidPath.shellQuote()} 2>/dev/null || true)"
                if [ -n "$pid" ] && $${buildRootProcessMatchTest(paths.mihomoCorePath, uid, gid).trimEnd()}; then
                    kill "$pid" 2>/dev/null || true
                    sleep 0.2
                    kill -9 "$pid" 2>/dev/null || true
                fi
                rm -f $${paths.pidPath.shellQuote()} 2>/dev/null || true
                """,
            )
            append(paths.buildRepairRuntimePermissionsCommand())
        }
        append(cleanupRulesCommand)
    }
}

internal fun RootStartConfig.buildPrepareRuntimeCommand(): String {
    return buildString {
        append(runtimeLayout.buildRepairRuntimePermissionsCommand())
        appendScript(
            """
            rm -f ${runtimeLayout.pidPath.shellQuote()} 2>/dev/null || true
            chmod 755 ${runtimeLayout.mihomoCorePath.shellQuote()}
            chmod 644 ${configPath.shellQuote()}
            test -r ${configPath.shellQuote()} || exit 1
            """,
        )
    }
}

internal fun RootRuntimeLayout.buildRepairRuntimePermissionsCommand(): String {
    return buildRepairAppOwnedDirectoryPermissionsCommand(dataDir)
}

internal fun MihomoCoreLogPaths.buildRepairCoreLogPermissionsCommand(): String {
    return buildRepairAppOwnedDirectoryPermissionsCommand(logDirectoryPath())
}

private fun buildRepairAppOwnedDirectoryPermissionsCommand(path: String): String {
    return buildString {
        appendScript(
            $$"""
            (
            repair_dir=$${path.shellQuote()}
            repair_owner_ref="$(dirname "$repair_dir")"
            mkdir -p "$repair_dir" || exit 1
            repair_owner="$(stat -c '%u:%g' "$repair_owner_ref" 2>/dev/null || true)"
            if [ -z "$repair_owner" ]; then
                set -- $(ls -nd "$repair_owner_ref" 2>/dev/null || true)
                if [ "$#" -ge 4 ]; then
                    repair_owner="$3:$4"
                fi
            fi
            if [ -n "$repair_owner" ]; then
                chown -R "$repair_owner" "$repair_dir" 2>/dev/null || true
            fi
            chmod -R u+rwX "$repair_dir" 2>/dev/null || true
            ) || exit 1
            """,
        )
    }
}

internal fun RootStartConfig.buildStartDaemonCommand(): String {
    return buildString {
        appendScript(
            $$"""
            trap '' HUP
            cd $${runtimeLayout.dataDir.shellQuote()} || exit 1
            export MIHOMO_LOCATION_ASSET=$${runtimeLayout.dataDir.shellQuote()}
            ulimit -SHn 1000000 2>/dev/null || true
            chmod 755 $${setuidgidPath.shellQuote()}
            $${setuidgidPath.shellQuote()} $${RootMihomoUid.toString().shellQuote()} $${RootMihomoGid.toString().shellQuote()} $${runtimeLayout.mihomoCorePath.shellQuote()} -d $${runtimeLayout.dataDir.shellQuote()} -f $${configPath.shellQuote()} >> $${coreLogPaths.errorLogPath.shellQuote()} 2>&1 < /dev/null &
            echo $! > $${runtimeLayout.pidPath.shellQuote()}
            """,
        )
    }
}

internal fun RootStartConfig.buildBootStartDaemonCommand(): String {
    return buildString {
        appendScript(
            $$"""
            trap '' HUP
            cd $${runtimeLayout.dataDir.shellQuote()} || exit 1
            export MIHOMO_LOCATION_ASSET=$${runtimeLayout.dataDir.shellQuote()}
            ulimit -SHn 1000000 || true
            chmod 755 $${setuidgidPath.shellQuote()}
            chmod 644 $${configPath.shellQuote()}
            test -r $${configPath.shellQuote()} || exit 1
            echo "+ start mihomo as uid=$${RootMihomoUid} gid=$${RootMihomoGid}"
            echo "+ Mihomo stdout/stderr: $${coreLogPaths.errorLogPath.shellQuote()}"
            $${setuidgidPath.shellQuote()} $${RootMihomoUid.toString().shellQuote()} $${RootMihomoGid.toString().shellQuote()} $${runtimeLayout.mihomoCorePath.shellQuote()} -d $${runtimeLayout.dataDir.shellQuote()} -f $${configPath.shellQuote()} >> $${coreLogPaths.errorLogPath.shellQuote()} 2>&1 < /dev/null &
            echo $! > $${runtimeLayout.pidPath.shellQuote()}
            echo "mihomo pid: $(cat $${runtimeLayout.pidPath.shellQuote()})"
            """,
        )
    }
}

internal fun MihomoCoreLogPaths.buildPrepareCoreLogFilesCommand(): String {
    val logPaths = logFilePaths().filter(String::isNotBlank)
    return buildString {
        append(buildRepairCoreLogPermissionsCommand())
        logPaths
            .mapNotNull { logPath -> File(logPath).parent }
            .distinct()
            .forEach { parentPath ->
                appendScript("mkdir -p ${parentPath.shellQuote()} || exit 1")
            }
        logPaths.forEach { logPath ->
            appendScript(
                """
                touch ${logPath.shellQuote()} || exit 1
                chmod 600 ${logPath.shellQuote()} || exit 1
                """,
            )
        }
        append(buildRepairCoreLogPermissionsCommand())
    }
}

internal fun buildRootPortReadyCommand(port: Int): String {
    return "netstat -an 2>/dev/null | grep \"[.:]$port[[:space:]]\" | grep -E 'LISTEN|UNKNOWN' >/dev/null 2>&1"
}

internal fun Int.toNetstatPortHexMarker(): String {
    return ":${toString(16).uppercase().padStart(4, '0')} "
}

internal fun buildRootDiagnosticMessage(vararg sections: String): String {
    return sections.toList()
        .toTrimmedNonEmptyList()
        .joinToString("\n")
}

internal suspend fun RootStartConfig.collectRootErrorLogTail(rootAccess: AndroidRootShellGateway): String {
    val command = "tail -n 80 ${coreLogPaths.errorLogPath.shellQuote()} 2>/dev/null || true"
    val result = rootAccess.exec(command)
    return result.stdout.ifBlank { result.stderr }
}

internal suspend fun RootStartConfig.collectRootProcessDiagnostics(rootAccess: AndroidRootShellGateway): String {
    val command = $$"""
        pid="$(cat $${runtimeLayout.pidPath.shellQuote()} 2>/dev/null || true)"
        echo "pid=$pid"
        if [ -n "$pid" ]; then
            echo "cmdline=$(tr '\0' ' ' < /proc/"$pid"/cmdline 2>/dev/null || true)"
            echo "exe=$(readlink /proc/"$pid"/exe 2>/dev/null || true)"
            grep -E '^(Uid|Gid):' /proc/"$pid"/status 2>/dev/null || true
        fi
    """.trimIndent()
    val result = rootAccess.exec(command, ShellExecOptions(logFailure = false))
    return result.stdout.ifBlank { result.stderr }
}

internal fun buildRootProcessMatchCommand(
    pidPath: String,
    executablePath: String,
    uid: Int,
    gid: Int,
): String {
    return buildString {
        appendScript(
            $$"""
            pid="$(cat $${pidPath.shellQuote()} 2>/dev/null || true)"
            [ -n "$pid" ] || exit 1
            """,
        )
        append(buildRootProcessMatchTest(executablePath, uid, gid))
    }
}

internal fun RootRuntimeLayout.buildRootConfigTagMatchCommand(tag: String): String {
    val key = tag.substringBefore(':').trim().trim('\'', '"')
    val pattern = "^[[:space:]]*['\"]?${key.toExtendedGrepLiteral()}['\"]?[[:space:]]*:"
    return "grep -E ${pattern.shellQuote()} ${configPath.shellQuote()} >/dev/null 2>&1"
}

private fun String.toExtendedGrepLiteral(): String {
    return buildString {
        this@toExtendedGrepLiteral.forEach { char ->
            when (char) {
                '.', '^', '$', '*', '+', '?', '(', ')', '[', ']', '{', '}', '|', '\\' -> append('\\')
            }
            append(char)
        }
    }
}

internal fun buildRootProcessMatchTest(
    executablePath: String,
    uid: Int,
    gid: Int,
): String {
    return $$"""
        (
          kill -0 "$pid" 2>/dev/null || exit 1
          cmdline="$(tr '\0' ' ' < /proc/"$pid"/cmdline 2>/dev/null || true)"
          exe="$(readlink /proc/"$pid"/exe 2>/dev/null || true)"
          matched=0
          case "$cmdline" in *$${executablePath.shellQuoteForCase()}*) matched=1;; esac
          [ "$exe" = $${executablePath.shellQuote()} ] && matched=1
          [ "$matched" = 1 ] || exit 1
          uid_line="$(grep '^Uid:' /proc/"$pid"/status 2>/dev/null || true)"
          set -- $uid_line
          [ "$3" = "$$uid" ] || [ "$5" = "$$uid" ] || exit 1
          gid_line="$(grep '^Gid:' /proc/"$pid"/status 2>/dev/null || true)"
          set -- $gid_line
          [ "$3" = "$$gid" ] || [ "$5" = "$$gid" ] || exit 1
        )
    """.trimIndent() + "\n"
}
