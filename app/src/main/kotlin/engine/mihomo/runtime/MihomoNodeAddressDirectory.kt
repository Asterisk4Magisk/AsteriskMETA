// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import android.content.Context
import app.AppState
import app.modes.isRootRunMode
import engine.mihomo.CmfaProvidersDirectory
import engine.mihomo.MihomoProfileFactory
import engine.mihomo.MihomoRootProvidersDirectory
import engine.mihomo.loadMihomoProfileObject
import engine.mihomo.mihomoProfileContentStore
import engine.mihomo.raw.usesRawMihomoConfig
import engine.mihomo.selectedMihomoProfileOrNull
import engine.mihomo.toDnsResolutionPolicy
import engine.network.DnsResolutionPolicy
import java.io.File
import java.security.MessageDigest

/**
 * The address of a proxy node, as the configuration states it. The name is the node name the
 * runtime reports, the host is the address the client connects to.
 */
internal data class MihomoNodeAddress(
    val name: String,
    val host: String,
    val port: Int = 0,
    val type: String = "",
)

/**
 * The addresses of the proxy nodes of a configuration, and the nameserver policy that applies to
 * the node domains.
 *
 * The runtime reports a node by name only, so the address behind a name is read from the
 * configuration the core runs: the inline `proxies` list first, then the provider files that hold
 * the nodes of a provider.
 */
internal class MihomoNodeAddressDirectory(
    private val context: Context,
) {
    /**
     * The addresses of every node the configuration declares, keyed by node name, together with
     * the nameserver policy of the configuration. Both come from the same document, so the
     * configuration is read once.
     */
    suspend fun read(appState: AppState): MihomoNodeAddressCatalog {
        val document = loadRuntimeDocument(appState) ?: return MihomoNodeAddressCatalog()
        return MihomoNodeAddressCatalog(
            addresses = document.readAddresses(appState, context),
            dnsPolicy = document.toDnsResolutionPolicy(),
        )
    }

    /**
     * The document the core runs. A raw configuration is the stored profile itself; a managed one
     * is the stored profile with the managed settings applied, which is what the core loads.
     */
    private suspend fun loadRuntimeDocument(appState: AppState): Map<*, *>? {
        val profile = appState.selectedMihomoProfileOrNull() ?: return null
        if (!profile.hasContent) return null
        val content = runCatching {
            context.mihomoProfileContentStore().read(profile)
        }.getOrNull() ?: return null
        if (appState.usesRawMihomoConfig()) {
            return runCatching { content.loadMihomoProfileObject() }.getOrNull()
        }
        // The managed configuration is the one the core receives, so the node domains and the
        // nameserver policy are read from it.
        return runCatching {
            MihomoProfileFactory.buildProfile(context, appState).loadMihomoProfileObject()
        }.getOrElse {
            runCatching { content.loadMihomoProfileObject() }.getOrNull()
        }
    }
}

/**
 * The addresses of the nodes of a configuration and the nameserver policy that applies to the
 * node domains.
 */
internal data class MihomoNodeAddressCatalog(
    val addresses: Map<String, MihomoNodeAddress> = emptyMap(),
    val dnsPolicy: DnsResolutionPolicy = DnsResolutionPolicy(),
)

private data class MihomoProxyEntry(
    val name: String,
    val host: String,
    val port: Int,
    val type: String,
)

private data class MihomoProviderEntry(
    val name: String,
    val path: String?,
    val url: String?,
)

/**
 * The addresses of the inline proxies of a document and of the proxies its providers declare.
 */
private suspend fun Map<*, *>.readAddresses(
    appState: AppState,
    context: Context,
): Map<String, MihomoNodeAddress> {
    val addresses = linkedMapOf<String, MihomoNodeAddress>()
    proxyEntries().forEach { entry ->
        val name = entry.name
        val host = entry.host
        if (name.isNotBlank() && host.isNotBlank()) {
            addresses[name] = MihomoNodeAddress(name = name, host = host, port = entry.port, type = entry.type)
        }
    }
    providerEntries().forEach { provider ->
        readProviderProxies(provider, appState, context).forEach { entry ->
            val name = entry.name
            val host = entry.host
            if (name.isNotBlank() && host.isNotBlank() && name !in addresses) {
                addresses[name] = MihomoNodeAddress(name = name, host = host, port = entry.port, type = entry.type)
            }
        }
    }
    return addresses
}

/**
 * The nodes of a provider, read from the file the core downloaded for it.
 */
private suspend fun readProviderProxies(
    provider: MihomoProviderEntry,
    appState: AppState,
    context: Context,
): List<MihomoProxyEntry> {
    val file = providerFile(provider, appState, context) ?: return emptyList()
    val content = runCatching { file.readText() }.getOrNull() ?: return emptyList()
    val document = runCatching { content.loadMihomoProfileObject() }.getOrNull() ?: return emptyList()
    return document.proxyEntries()
}

/**
 * The provider file of a provider declaration. The path of the declaration is relative to the
 * core home directory, and the ROOT runtime publishes the providers under another name.
 */
private fun providerFile(
    provider: MihomoProviderEntry,
    appState: AppState,
    context: Context,
): File? {
    val dataDir = File(context.filesDir, MihomoHomeDirectory)
    val candidates = buildList {
        provider.path
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->
                add(File(dataDir, path.removePrefix("./")))
                if (appState.runMode.isRootRunMode()) {
                    add(File(dataDir, "$MihomoRootProvidersDirectory/${path.removePrefix("./")}"))
                }
            }
        provider.url
            ?.takeIf { it.isNotBlank() }
            ?.let { url ->
                add(File(dataDir, "$CmfaProvidersDirectory/proxies/${url.md5Hex()}"))
            }
    }
    return candidates.firstOrNull { candidate -> candidate.isFile }
}

/** The inline proxies of a configuration document. */
private fun Map<*, *>.proxyEntries(): List<MihomoProxyEntry> {
    val proxies = this["proxies"] as? List<*> ?: return emptyList()
    return proxies.mapNotNull { value ->
        val proxy = value as? Map<*, *> ?: return@mapNotNull null
        MihomoProxyEntry(
            name = proxy.stringValue("name"),
            host = proxy.stringValue("server"),
            port = (proxy["port"] as? Number)?.toInt() ?: 0,
            type = proxy.stringValue("type"),
        )
    }
}

/** The proxy providers of a configuration document. */
private fun Map<*, *>.providerEntries(): List<MihomoProviderEntry> {
    val providers = this["proxy-providers"] as? Map<*, *> ?: return emptyList()
    return providers.mapNotNull { (key, value) ->
        val provider = value as? Map<*, *> ?: return@mapNotNull null
        MihomoProviderEntry(
            name = key as? String ?: return@mapNotNull null,
            path = provider.stringValue("path"),
            url = provider.stringValue("url"),
        )
    }
}

private fun Map<*, *>.stringValue(key: String): String {
    return (this[key] as? String)?.trim().orEmpty()
}

private fun String.md5Hex(): String {
    val bytes = MessageDigest.getInstance("MD5").digest(toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

/** The core home directory, the directory the provider files live in. */
internal const val MihomoHomeDirectory = "clash"
