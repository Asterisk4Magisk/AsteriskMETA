// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.tun

import engine.root.RootModeRunner
import engine.root.RootReadinessCheck
import engine.root.RootRuntimeLayout
import engine.root.appendScript
import engine.tun2socks.buildCleanupRulesCommand
import engine.tun2socks.buildSetupRulesCommand
import system.AndroidRootShellGateway
import system.ShellExecOptions
import utils.shellQuote

internal class TunRootRunner(
    rootAccess: AndroidRootShellGateway,
) : RootModeRunner<TunStartConfig>(
    rootAccess = rootAccess,
    modeName = "TUN",
    runtimeConfigTag = MihomoTunRuntimeConfigTag,
    logTag = LogTag,
) {
    override fun buildSetupRulesCommand(config: TunStartConfig): String {
        return config.iptablesConfig.buildSetupRulesCommand(
            enableIpv6 = config.root.enableIpv6,
            enableLocalDns = config.root.enableLocalDns,
            enableFakeIp = config.root.enableFakeIp,
            fakeIpIpv4Pool = config.root.fakeIpIpv4Pool,
        )
    }

    override fun buildCleanupRulesCommand(): String {
        return TunBaseIptablesConfig.buildCleanupRulesCommand()
    }

    override suspend fun isRunning(runtimeLayout: RootRuntimeLayout): Boolean {
        if (!super.isRunning(runtimeLayout)) {
            return false
        }
        val result = rootAccess.exec(buildTunRuntimeReadyCommand(), ShellExecOptions(logFailure = false))
        return result.errno == 0
    }

    override fun buildReadinessCheck(config: TunStartConfig): RootReadinessCheck {
        return RootReadinessCheck(
            description = "TUN device ${config.tunConfig.device}",
            command = buildTunRuntimeReadyCommand(),
            failureMessage = "mihomo started but TUN device ${config.tunConfig.device} is not ready",
        )
    }

    override suspend fun collectReadinessDiagnostics(config: TunStartConfig): String {
        val command = $$"""
            echo "== TUN config =="
            echo "device=$${config.tunConfig.device}"
            echo "stack=$${config.tunConfig.stack}"
            echo "mtu=$${config.tunConfig.mtu}"
            echo "ipv4=$${config.tunConfig.ipv4Address}"
            echo "ipv6=$${config.tunConfig.ipv6Address.orEmpty()}"
            echo "== ip link =="
            ip link show dev $${config.tunConfig.device.shellQuote()} 2>&1 || true
            echo "== ip addr =="
            ip addr show dev $${config.tunConfig.device.shellQuote()} 2>&1 || true
            echo "== ip rule =="
            ip rule 2>&1 | head -n 40 || true
            ip -6 rule 2>&1 | head -n 40 || true
            echo "== route table =="
            ip route show table $${config.iptablesConfig.ipv4Table.shellQuote()} 2>&1 || true
            ip -6 route show table $${config.iptablesConfig.ipv6Table.shellQuote()} 2>&1 || true
        """.trimIndent()
        val result = rootAccess.exec(command, ShellExecOptions(logFailure = false))
        return result.stdout.ifBlank { result.stderr }
    }

    override fun StringBuilder.appendStartupSummary(config: TunStartConfig) {
        appendScript("echo \"TUN device: ${config.tunConfig.device}\"")
        appendScript("echo \"TUN stack: ${config.tunConfig.stack}\"")
        appendScript("echo \"TUN MTU: ${config.tunConfig.mtu}\"")
        appendScript("echo \"TUN IPv4: ${config.tunConfig.ipv4Address}\"")
        config.tunConfig.ipv6Address?.let { ipv6Address ->
            appendScript("echo \"TUN IPv6: $ipv6Address\"")
        }
    }

    override fun StringBuilder.appendStartupFailureDiagnostics(config: TunStartConfig) {
        appendScript(
            $$"""
                echo
                echo "TUN device snapshot:"
                ip link show dev $${config.tunConfig.device.shellQuote()} 2>&1 || true
                ip addr show dev $${config.tunConfig.device.shellQuote()} 2>&1 || true
                echo
                echo "Routing rule snapshot:"
                ip rule 2>&1 | head -n 40 || true
                ip -6 rule 2>&1 | head -n 40 || true
                echo
                echo "Route table snapshot:"
                ip route show table $${config.iptablesConfig.ipv4Table.shellQuote()} 2>&1 || true
                ip -6 route show table $${config.iptablesConfig.ipv6Table.shellQuote()} 2>&1 || true
            """,
        )
    }

    private fun buildTunRuntimeReadyCommand(): String {
        return "ip link show dev ${MihomoTunDevice.shellQuote()} >/dev/null 2>&1"
    }

    private companion object {
        private const val LogTag = "TunRootRunner"
    }
}
