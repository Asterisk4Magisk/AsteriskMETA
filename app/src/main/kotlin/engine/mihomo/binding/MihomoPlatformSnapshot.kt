package engine.mihomo.binding

internal data class MihomoDnsNetwork(
    val id: String,
    val active: Boolean,
    val notVpn: Boolean,
    val internet: Boolean,
    val validated: Boolean,
    val dnsServers: List<String>,
)

internal fun mihomoSystemDnsSnapshot(networks: List<MihomoDnsNetwork>): String {
    val network = networks.asSequence()
        .filter { it.notVpn && it.internet && it.dnsServers.isNotEmpty() }
        .sortedWith(compareByDescending<MihomoDnsNetwork> { it.active }
            .thenByDescending { it.validated }
            .thenBy { it.id })
        .firstOrNull()
    return network?.dnsServers.orEmpty().distinct().joinToString(",") { address ->
        if (':' in address) "[$address]:53" else "$address:53"
    }
}

internal fun mihomoInstalledAppsSnapshot(apps: List<Pair<Int, String>>): String =
    apps.joinToString(",") { (uid, name) -> "$uid:$name" }
