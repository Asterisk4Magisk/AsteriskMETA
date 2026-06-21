// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import utils.shellQuote

internal fun <Config : RootModeStartConfig> Config.buildInstallRootBootScriptCommand(
    modeName: String,
    buildSetupRulesCommand: (Config) -> String,
    buildPostCoreStartCommand: (Config) -> String,
    buildReadinessCheck: (Config) -> RootReadinessCheck,
    bootReadinessCheckAttempts: Int,
    appendStartupSummary: StringBuilder.(Config) -> Unit,
    appendStartupFailureDiagnostics: StringBuilder.(Config) -> Unit,
): String {
    val bootScript = root.buildRootBootScript()
    val startupScript = buildRootStartupScript(
        modeName = modeName,
        buildSetupRulesCommand = buildSetupRulesCommand,
        buildPostCoreStartCommand = buildPostCoreStartCommand,
        buildReadinessCheck = buildReadinessCheck,
        bootReadinessCheckAttempts = bootReadinessCheckAttempts,
        appendStartupSummary = appendStartupSummary,
        appendStartupFailureDiagnostics = appendStartupFailureDiagnostics,
    )
    return buildString {
        appendScript("mkdir -p ${RootBootScriptDir.shellQuote()}")
        appendScript("mkdir -p ${root.runtimeLayout.dataDir.shellQuote()}")
        appendScript("mkdir -p ${root.bootLogDirPath.shellQuote()}")
        append(root.coreLogPaths.buildRepairCoreLogPermissionsCommand())
        appendHeredoc(
            targetPath = root.startupScriptPath,
            content = startupScript,
        )
        appendScript("chmod 755 ${root.startupScriptPath.shellQuote()}")
        appendScript("touch ${root.bootLogPath.shellQuote()}")
        appendScript("chmod 600 ${root.bootLogPath.shellQuote()}")
        append(root.coreLogPaths.buildRepairCoreLogPermissionsCommand())
        appendHeredoc(
            targetPath = RootBootScriptPath,
            content = bootScript,
        )
        appendScript("chmod 755 ${RootBootScriptPath.shellQuote()}")
    }
}

private fun <Config : RootModeStartConfig> Config.buildRootStartupScript(
    modeName: String,
    buildSetupRulesCommand: (Config) -> String,
    buildPostCoreStartCommand: (Config) -> String,
    buildReadinessCheck: (Config) -> RootReadinessCheck,
    bootReadinessCheckAttempts: Int,
    appendStartupSummary: StringBuilder.(Config) -> Unit,
    appendStartupFailureDiagnostics: StringBuilder.(Config) -> Unit,
): String {
    return buildString {
        appendRootStartupPreamble(
            config = this@buildRootStartupScript,
            modeName = modeName,
            appendStartupSummary = appendStartupSummary,
            appendStartupFailureDiagnostics = appendStartupFailureDiagnostics,
        )
        if (root.shouldStartIpv6Disabler) {
            appendScript("section \"Start IPv6 disabler\"")
            append(root.buildStartIpv6DisablerCommand())
        }
        appendScript("section \"Prepare core logs\"")
        append(root.coreLogPaths.buildPrepareCoreLogFilesCommand())
        appendScript("section \"Start mihomo\"")
        append(root.buildBootStartDaemonCommand())
        val postCoreStartCommand = buildPostCoreStartCommand(this@buildRootStartupScript)
        if (postCoreStartCommand.isNotBlank()) {
            appendScript("section \"Start $modeName helper runtime\"")
            append(postCoreStartCommand)
        }
        appendRootBootReadinessCheck(
            readinessCheck = buildReadinessCheck(this@buildRootStartupScript),
            attempts = bootReadinessCheckAttempts,
        )
        appendScript("section \"Repair runtime permissions\"")
        append(root.runtimeLayout.buildRepairRuntimePermissionsCommand())
        append(root.coreLogPaths.buildRepairCoreLogPermissionsCommand())
        append('\n')
        appendScript("section \"Install $modeName rules\"")
        append(buildSetupRulesCommand(this@buildRootStartupScript))
        appendScript("section \"$modeName boot setup is ready\"")
    }
}

private fun StringBuilder.appendRootBootReadinessCheck(
    readinessCheck: RootReadinessCheck,
    attempts: Int,
) {
    val sectionTitle = "Wait for ${readinessCheck.description}".shellQuote()
    val failureMessage = "ERROR: ${readinessCheck.failureMessage}".shellQuote()
    appendScript(
        $$"""

        section $$sectionTitle
        runtime_ready=0
        attempt=0
        while [ "$attempt" -lt $$attempts ]; do
            echo "Attempt $((attempt + 1))/$${attempts}"
            if $${readinessCheck.command.trimEnd()}; then
                runtime_ready=1
                break
            fi
            attempt=$((attempt + 1))
            sleep 1
        done
        if [ "$runtime_ready" != "1" ]; then
            echo $$failureMessage >&2
            dump_failure_diagnostics
            exit 1
        fi
        """,
    )
}

private fun <Config : RootModeStartConfig> StringBuilder.appendRootStartupPreamble(
    config: Config,
    modeName: String,
    appendStartupSummary: StringBuilder.(Config) -> Unit,
    appendStartupFailureDiagnostics: StringBuilder.(Config) -> Unit,
) {
    appendScript(
        $$"""
        #!/system/bin/sh
        # Generated by AsteriskMETA. This script is invoked by ROOT boot script.

        set -e
        diagnostics_dumped=0

        timestamp() {
            date '+%Y-%m-%d %H:%M:%S %z' || date
        }

        section() {
            echo
            echo "[$(timestamp)] ===== $* ====="
        }

        finish() {
            rc=$?
            if [ "$rc" != "0" ] && [ "$diagnostics_dumped" != "1" ]; then
                dump_failure_diagnostics
            fi
            echo
            if [ "$rc" = "0" ]; then
                echo "[$(timestamp)] Startup completed successfully"
            else
                echo "[$(timestamp)] Startup failed with exit code $rc"
            fi
        }

        dump_failure_diagnostics() {
            diagnostics_dumped=1
            echo
            echo "Recent Mihomo error log:"
            tail -n 80 $${config.root.coreLogPaths.errorLogPath.shellQuote()} || true
        """,
    )
    appendStartupFailureDiagnostics(config)
    if (config.root.shouldStartIpv6Disabler) {
        appendScript(
            $$"""
                echo
                echo "IPv6 disabler log:"
                tail -n 80 $${config.root.ipv6DisablerLogPath.shellQuote()} || true
            """,
        )
    }
    appendScript(
        $$"""
            echo
            echo "Process snapshot:"
            pid="$(cat $${config.root.runtimeLayout.pidPath.shellQuote()} || true)"
            echo "pid=$pid"
            if [ -n "$pid" ]; then
                echo "cmdline=$(tr '\0' ' ' < /proc/"$pid"/cmdline || true)"
                echo "exe=$(readlink /proc/"$pid"/exe || true)"
                grep -E '^(Uid|Gid):' /proc/"$pid"/status || true
            fi
        }

        trap finish EXIT

        echo "AsteriskMETA $$modeName startup"
        echo "Started at: $(timestamp)"
        echo "Data dir: $${config.root.runtimeLayout.dataDir.shellQuote()}"
        echo "Config: $${config.root.configPath.shellQuote()}"
        echo "PID file: $${config.root.runtimeLayout.pidPath.shellQuote()}"
        """,
    )
    appendStartupSummary(config)
        appendScript(
            $$"""
        echo "IPv6 enabled: $${config.root.enableIpv6}"
        echo "IPv6 disabler enabled: $${config.root.shouldStartIpv6Disabler}"
        echo "Local DNS enabled: $${config.root.enableLocalDns}"
        echo "FakeIp enabled: $${config.root.enableFakeIp}"
        echo "Core error log: $${config.root.coreLogPaths.errorLogPath.shellQuote()}"
        echo "IPv6 disabler log: $${config.root.ipv6DisablerLogPath.shellQuote()}"

        section "Prepare runtime"
        """,
    )
    append(config.root.runtimeLayout.buildRepairRuntimePermissionsCommand())
    appendScript(
        $$"""
        rm -f $${config.root.runtimeLayout.pidPath.shellQuote()} || true
        chmod 755 $${config.root.runtimeLayout.mihomoCorePath.shellQuote()}
        chmod 644 $${config.root.configPath.shellQuote()}
        test -r $${config.root.configPath.shellQuote()} || exit 1
        """,
    )
}

internal fun RootStartConfig.buildRootBootScript(): String {
    return buildString {
        appendScript(
            $$"""
            # Generated by AsteriskMETA. This script is executed by Magisk service.d at boot.

            (
            until [ "$(getprop sys.boot_completed)" = "1" ]; do
                sleep 1
            done
            until [ -x $${startupScriptPath.shellQuote()} ]; do
                sleep 1
            done

            $${startupScriptPath.shellQuote()} &> $${bootLogPath.shellQuote()}
            ) &
            """,
        )
    }
}
