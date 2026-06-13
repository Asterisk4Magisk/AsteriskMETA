// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.tproxy

import engine.root.RootProxyRouteRulePriority
import engine.root.appendDeleteRuleLoop
import engine.root.appendIpRuleDeleteLoop
import engine.root.appendScript
import utils.shellQuote

internal fun StringBuilder.appendDummyRouteRules(variant: TproxyIptablesVariant) {
    val dummyInterface = variant.dummyInterface ?: return
    appendScript(
        """
        ${variant.ipCommand} link add ${dummyInterface.device.shellQuote()} type dummy 2>/dev/null || true
        ${variant.ipCommand} addr add ${dummyInterface.address.shellQuote()} dev ${dummyInterface.device.shellQuote()} 2>/dev/null || true
        ${variant.ipCommand} link set ${dummyInterface.device.shellQuote()} up 2>/dev/null || true
        ${variant.ipCommand} rule add priority $RootProxyRouteRulePriority not from all fwmark ${dummyInterface.mark} table ${dummyInterface.routeTable} 2>/dev/null || true
        ${variant.ipCommand} route add local default dev ${dummyInterface.device.shellQuote()} table ${dummyInterface.routeTable} 2>/dev/null || true
        """,
    )
}

internal fun StringBuilder.appendDummyPreroutingRules(
    command: String,
    dummyInterface: TproxyDummyInterfaceConfig,
    port: Int,
) {
    appendScript(
        """
        $command -t mangle -N ${dummyInterface.preroutingChain} 2>/dev/null || true
        $command -t mangle -A ${dummyInterface.preroutingChain} -i ${dummyInterface.device.shellQuote()} -p tcp -j TPROXY --on-ip :: --on-port $port --tproxy-mark ${dummyInterface.mark}
        $command -t mangle -A ${dummyInterface.preroutingChain} -i ${dummyInterface.device.shellQuote()} -p udp -j TPROXY --on-ip :: --on-port $port --tproxy-mark ${dummyInterface.mark}
        $command -t mangle -A PREROUTING -j ${dummyInterface.preroutingChain}
        """,
    )
}

internal fun StringBuilder.appendDummyOutputRules(
    command: String,
    dummyInterface: TproxyDummyInterfaceConfig,
) {
    appendScript(
        """
        $command -t mangle -N ${dummyInterface.outputChain} 2>/dev/null || true
        $command -t mangle -A ${dummyInterface.outputChain} -p tcp -j MARK --set-xmark ${dummyInterface.mark}
        $command -t mangle -A ${dummyInterface.outputChain} -p udp -j MARK --set-xmark ${dummyInterface.mark}
        $command -t mangle -A OUTPUT -j ${dummyInterface.outputChain}
        """,
    )
}

internal fun StringBuilder.appendDummyCleanupRules(
    command: String,
    ipCommand: String,
    dummyInterface: TproxyDummyInterfaceConfig,
) {
    appendDeleteRuleLoop(command, "OUTPUT", "-j ${dummyInterface.outputChain}")
    appendDeleteRuleLoop(command, "PREROUTING", "-j ${dummyInterface.preroutingChain}")
    listOf(dummyInterface.outputChain, dummyInterface.preroutingChain).forEach { chain ->
        appendScript(
            """
            $command -t mangle -F $chain 2>/dev/null || true
            $command -t mangle -X $chain 2>/dev/null || true
            """,
        )
    }
    appendIpRuleDeleteLoop(
        ipCommand = ipCommand,
        rule = "priority $RootProxyRouteRulePriority not from all fwmark ${dummyInterface.mark} table ${dummyInterface.routeTable}",
    )
    appendScript(
        """
        $ipCommand route del local default dev ${dummyInterface.device.shellQuote()} table ${dummyInterface.routeTable} 2>/dev/null || true
        $ipCommand link set ${dummyInterface.device.shellQuote()} down 2>/dev/null || true
        $ipCommand link del ${dummyInterface.device.shellQuote()} type dummy 2>/dev/null || true
        """,
    )
}
