// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import engine.mihomo.DefaultMihomoDelayTestUrl
import engine.mihomo.DefaultMihomoDelayTimeoutMillis
import engine.mihomo.MihomoControlConfig
import engine.vpn.AndroidMihomoRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.EOFException
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import kotlin.time.Duration.Companion.milliseconds

internal class MihomoControlClient {
    fun isApiAvailable(config: MihomoControlConfig): Boolean {
        return runCatching {
            request(
                config = config,
                path = "/version",
                connectTimeoutMillis = ApiAvailabilityConnectTimeoutMillis,
                readTimeoutMillis = ApiAvailabilityReadTimeoutMillis,
            )
        }.isSuccess
    }

    fun getConfigs(config: MihomoControlConfig, useBridge: Boolean): MihomoConfigsState {
        if (useBridge) {
            val state = Clash.queryTunnelState()
            return MihomoConfigsState(mode = state.mode.mihomoModeName())
        }
        val root = requestJsonObject(config, "/configs")
        return MihomoConfigsState(
            mode = root.stringValue("mode").orEmpty(),
            mixedPort = root.intValue("mixed-port"),
        )
    }

    fun getVersion(config: MihomoControlConfig, useBridge: Boolean): MihomoVersionState {
        if (useBridge) {
            return MihomoVersionState(version = Bridge.nativeCoreVersion())
        }
        val root = requestJsonObject(config, "/version")
        return MihomoVersionState(
            version = root.stringValue("version")
                ?: root.stringValue("premium")
                ?: root.stringValue("meta")
                ?: "",
        )
    }

    suspend fun getMemory(config: MihomoControlConfig, useBridge: Boolean): MihomoMemoryState {
        if (useBridge) {
            return MihomoMemoryState(inUseBytes = Clash.queryMemory())
        }
        val root = requestMemoryJsonObject(config)
        return MihomoMemoryState(
            inUseBytes = root.longValue("inuse") ?: root.longValue("inUse") ?: 0L,
            osLimitBytes = root.longValue("oslimit") ?: root.longValue("osLimit") ?: 0L,
        )
    }

    fun getConnections(
        config: MihomoControlConfig,
        useBridge: Boolean,
    ): MihomoConnectionsState {
        val response = if (useBridge) {
            Clash.queryConnections()
        } else {
            request(config, "/connections")
        }
        return parseMihomoConnectionsJson(response)
    }

    fun closeConnection(
        config: MihomoControlConfig,
        connectionId: String,
        useBridge: Boolean,
    ): Boolean {
        if (useBridge) {
            return Clash.closeConnection(connectionId)
        }
        return try {
            request(config, "/connections/${connectionId.urlEncode()}", method = "DELETE")
            true
        } catch (error: MihomoApiException) {
            if (error.status == HttpURLConnection.HTTP_NOT_FOUND) false else throw error
        }
    }

    fun closeAllConnections(
        config: MihomoControlConfig,
        useBridge: Boolean,
    ) {
        if (useBridge) {
            Clash.closeAllConnections()
            return
        }
        request(config, "/connections", method = "DELETE")
    }

    fun getProxies(
        config: MihomoControlConfig,
        useBridge: Boolean,
        mode: String,
    ): MihomoProxiesState {
        if (useBridge) {
            return queryBridgeProxies()
        }
        val root = requestJsonObject(config, "/proxies")
        val proxyObjects = root.objectValue("proxies").orEmpty()
        val nodes = linkedMapOf<String, MihomoProxyNode>()
        val groups = mutableListOf<MihomoProxyGroup>()

        proxyObjects.forEach { (name, element) ->
            val item = element as? JsonObject ?: return@forEach
            val proxyName = item.stringValue("name") ?: name
            val all = item.stringListValue("all")
            val hidden = item.booleanValue("hidden") ?: false
            val type = item.stringValue("type").orEmpty().ifBlank { "Proxy" }
            if (all.isNotEmpty()) {
                groups += MihomoProxyGroup(
                    name = proxyName,
                    type = type,
                    now = item.stringValue("now").orEmpty(),
                    all = all,
                    hidden = hidden,
                    icon = item.stringValue("icon").orEmpty(),
                    testUrl = item.stringValue("testUrl").orEmpty(),
                )
            }
            nodes[proxyName] = MihomoProxyNode(
                name = proxyName,
                type = type,
                udp = item.booleanValue("udp") ?: false,
                delay = item.latestDelay(),
            )
        }

        val globalProxyNames = proxyObjects[MihomoGlobalGroupName]
            .jsonObjectOrNull()
            ?.stringListValue("all")
            .orEmpty()
        return MihomoProxiesState(
            groups = groups.filterVisibleForMode(mode, globalProxyNames),
            nodes = nodes.values.toList(),
            nodeByName = nodes,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    fun getProxyProvider(
        config: MihomoControlConfig,
        providerName: String,
        useBridge: Boolean,
    ): MihomoProxyProviderRuntimeDetail {
        val root = if (useBridge) {
            val response = Bridge.nativeQueryProvider(Provider.Type.Proxy.toString(), providerName)
                ?: error("Invalid Mihomo bridge provider response for $providerName")
            RuntimeJson.parseToJsonElement(response).jsonObjectOrNull()
                ?: error("Invalid Mihomo bridge provider response for $providerName")
        } else {
            requestJsonObject(config, "/providers/proxies/${providerName.urlEncode()}")
        }
        root.errorMessageOrNull()?.let { message -> error(message) }
        return root.toProxyProviderRuntimeDetail(providerName)
    }

    suspend fun updateProxyProvider(
        config: MihomoControlConfig,
        providerName: String,
        useBridge: Boolean,
    ) {
        if (useBridge) {
            Clash.updateProvider(Provider.Type.Proxy, providerName).await()
            return
        }
        request(config, "/providers/proxies/${providerName.urlEncode()}", method = "PUT")
    }

    suspend fun patchMode(
        config: MihomoControlConfig,
        mode: String,
        useBridge: Boolean,
    ) {
        if (useBridge) {
            val override = Clash.queryOverride(Clash.OverrideSlot.Session)
            Clash.patchOverride(
                Clash.OverrideSlot.Session,
                override.copyMode(mode.toTunnelMode()),
            )
            AndroidMihomoRuntime.reloadProfile()
            return
        }
        val body = buildJsonObject {
            put("mode", mode)
        }
        request(config, "/configs", method = "PATCH", body = body.toString())
    }

    suspend fun patchLogLevel(
        config: MihomoControlConfig,
        logLevel: String,
        useBridge: Boolean,
    ) {
        if (useBridge) {
            val override = Clash.queryOverride(Clash.OverrideSlot.Session)
            Clash.patchOverride(
                Clash.OverrideSlot.Session,
                override.copyLogLevel(logLevel.toLogMessageLevel()),
            )
            AndroidMihomoRuntime.reloadProfile()
            return
        }
        val body = buildJsonObject {
            put("log-level", logLevel)
        }
        request(config, "/configs", method = "PATCH", body = body.toString())
    }

    fun selectProxy(
        config: MihomoControlConfig,
        groupName: String,
        proxyName: String,
        useBridge: Boolean,
    ) {
        if (useBridge) {
            check(Clash.patchSelector(groupName, proxyName)) {
                "Failed to switch Mihomo selector $groupName to $proxyName"
            }
            return
        }
        val body = buildJsonObject {
            put("name", proxyName)
        }
        request(config, "/proxies/${groupName.urlEncode()}", method = "PUT", body = body.toString())
    }

    suspend fun testProxyDelay(
        config: MihomoControlConfig,
        proxyName: String,
        url: String = DefaultMihomoDelayTestUrl,
        timeoutMillis: Int = DefaultMihomoDelayTimeoutMillis,
        useBridge: Boolean,
    ): MihomoDelayResult {
        val delay = if (useBridge) {
            runDelayTestOrTimeout {
                withTimeoutOrNull((timeoutMillis.toLong() + BridgeHealthCheckGraceMillis).milliseconds) {
                    Clash.queryProxyDelay(proxyName, url, timeoutMillis)
                } ?: MihomoTimeoutDelay
            }
        } else {
            runDelayTestOrTimeout {
                val root = requestJsonObject(
                    config,
                    "/proxies/${proxyName.urlEncode()}/delay?url=${url.urlEncode()}&timeout=$timeoutMillis",
                )
                root.intValue("delay") ?: MihomoTimeoutDelay
            }
        }
        return MihomoDelayResult(mapOf(proxyName to delay.toMihomoTestDelay(timeoutMillis)))
    }

    suspend fun testGroupDelay(
        config: MihomoControlConfig,
        groupName: String,
        url: String = DefaultMihomoDelayTestUrl,
        timeoutMillis: Int = DefaultMihomoDelayTimeoutMillis,
        expectedProxyNames: List<String> = emptyList(),
        useBridge: Boolean,
    ): MihomoDelayResult {
        if (useBridge) {
            val groupProxyNames = expectedProxyNames.ifEmpty {
                runCatching {
                    Clash.queryGroup(groupName, ProxySort.Default).proxies.map { proxy -> proxy.name }
                }.getOrDefault(emptyList())
            }
            val delays = runGroupDelayTestOrTimeout {
                withTimeoutOrNull((timeoutMillis.toLong() + BridgeHealthCheckGraceMillis).milliseconds) {
                    Clash.queryGroupDelay(groupName, url, timeoutMillis)
                } ?: emptyMap()
            }
            return MihomoDelayResult(delays.withTimeoutFallback(groupProxyNames, timeoutMillis))
        }
        val delays = runGroupDelayTestOrTimeout {
            val root = requestJsonObject(
                config,
                "/group/${groupName.urlEncode()}/delay?url=${url.urlEncode()}&timeout=$timeoutMillis",
            )
            root.mapValues { (_, value) ->
                value.intValue() ?: MihomoTimeoutDelay
            }
        }
        return MihomoDelayResult(delays.withTimeoutFallback(expectedProxyNames, timeoutMillis))
    }

    fun traffic(config: MihomoControlConfig, useBridge: Boolean): Flow<MihomoTrafficSample> = flow {
        while (currentCoroutineContext().isActive && useBridge) {
            val sample = runCatching {
                val now = Clash.queryTrafficNow().toTrafficBytes()
                val total = Clash.queryTrafficTotal().toTrafficBytes()
                MihomoTrafficSample(
                    up = now.up,
                    down = now.down,
                    totalUp = total.up,
                    totalDown = total.down,
                )
            }.getOrNull()
            if (sample == null) {
                break
            }
            emit(sample)
            delay(BridgeTrafficPollIntervalMillis.milliseconds)
        }
        if (useBridge) {
            return@flow
        }
        while (currentCoroutineContext().isActive) {
            val connection = openConnection(config, "/traffic", readTimeoutMillis = TrafficReadTimeoutMillis)
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                if (status !in 200..299) {
                    val response = stream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
                    error("Mihomo API GET /traffic failed with HTTP $status: $response")
                }
                BufferedReader(InputStreamReader(stream ?: error("Invalid Mihomo API response for /traffic"))).use { reader ->
                    while (currentCoroutineContext().isActive) {
                        val line = reader.readNextNonBlankLine() ?: break
                        val sample = parseTrafficLine(line) ?: continue
                        emit(sample)
                    }
                }
            } catch (error: IOException) {
                if (!error.isExpectedMihomoStreamEnd()) {
                    throw error
                }
            } finally {
                connection.disconnect()
            }
            delay(StreamReconnectDelayMillis.milliseconds)
        }
    }

    private fun queryBridgeProxies(): MihomoProxiesState {
        val nodes = linkedMapOf<String, MihomoProxyNode>()
        val groups = Clash.queryGroupNames(excludeNotSelectable = false).map { groupName ->
            val group = Clash.queryGroup(groupName, ProxySort.Default)
            val proxies = group.proxies
            val proxyNames = proxies.map { proxy -> proxy.name }
            nodes[groupName] = MihomoProxyNode(
                name = groupName,
                type = group.type,
                delay = null,
            )
            proxies.forEach { proxy ->
                nodes[proxy.name] = MihomoProxyNode(
                    name = proxy.name,
                    type = proxy.type,
                    udp = false,
                    delay = proxy.delay.toMihomoHistoryDelayOrNull(),
                )
            }
            MihomoProxyGroup(
                name = groupName,
                type = group.type,
                now = group.now,
                all = proxyNames,
                hidden = false,
                icon = "",
                testUrl = DefaultMihomoDelayTestUrl,
            )
        }
        return MihomoProxiesState(
            groups = groups,
            nodes = nodes.values.toList(),
            nodeByName = nodes,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun parseTrafficLine(line: String): MihomoTrafficSample? {
        val root = runCatching {
            RuntimeJson.parseToJsonElement(line).jsonObjectOrNull()
        }.getOrNull() ?: return null
        return MihomoTrafficSample(
            up = root.longValue("up") ?: 0L,
            down = root.longValue("down") ?: 0L,
            totalUp = root.longValue("upTotal"),
            totalDown = root.longValue("downTotal"),
        )
    }

    private fun requestJsonObject(
        config: MihomoControlConfig,
        path: String,
    ): JsonObject {
        return RuntimeJson.parseToJsonElement(request(config, path)).jsonObjectOrNull()
            ?: error("Invalid Mihomo API response for $path")
    }

    private suspend fun requestMemoryJsonObject(
        config: MihomoControlConfig,
    ): JsonObject {
        val connection = openConnection(
            config = config,
            path = "/memory",
            readTimeoutMillis = MemoryReadTimeoutMillis,
        )
        var latest: JsonObject? = null
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            if (status !in 200..299) {
                val response = stream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
                error("Mihomo API GET /memory failed with HTTP $status: $response")
            }

            val reader = stream?.bufferedReader() ?: error("Invalid Mihomo API response for /memory")
            reader.use {
                repeat(MemorySampleReadLimit) {
                    val line = reader.readNextNonBlankLine()
                        ?: return latest ?: EmptyMemoryJson
                    val root = RuntimeJson.parseToJsonElement(line).jsonObjectOrNull()
                        ?: error("Invalid Mihomo API response for /memory")
                    latest = root
                    val inUse = root.longValue("inuse") ?: root.longValue("inUse") ?: 0L
                    if (inUse > 0L) {
                        return root
                    }
                }
            }
            return latest ?: EmptyMemoryJson
        } catch (error: IOException) {
            if (!error.isExpectedMihomoStreamEnd()) {
                throw error
            }
            return latest ?: EmptyMemoryJson
        } finally {
            connection.disconnect()
        }
    }

    private fun request(
        config: MihomoControlConfig,
        path: String,
        method: String = "GET",
        body: String? = null,
        connectTimeoutMillis: Int = DefaultConnectTimeoutMillis,
        readTimeoutMillis: Int = DefaultReadTimeoutMillis,
    ): String {
        val connection = openConnection(
            config = config,
            path = path,
            method = method,
            connectTimeoutMillis = connectTimeoutMillis,
            readTimeoutMillis = readTimeoutMillis,
        )
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw MihomoApiException(method, path, status, response)
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        config: MihomoControlConfig,
        path: String,
        method: String = "GET",
        connectTimeoutMillis: Int = DefaultConnectTimeoutMillis,
        readTimeoutMillis: Int = DefaultReadTimeoutMillis,
    ): HttpURLConnection {
        val connection = (URI("${config.baseUrl}$path").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            setRequestProperty("Accept", "application/json")
            if (config.secret.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${config.secret}")
            }
        }
        return connection
    }

    private companion object {
        val RuntimeJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val EmptyMemoryJson = buildJsonObject {
            put("inuse", 0L)
            put("oslimit", 0L)
        }
        const val DefaultConnectTimeoutMillis = 2_500
        const val DefaultReadTimeoutMillis = 8_000
        const val ApiAvailabilityConnectTimeoutMillis = 500
        const val ApiAvailabilityReadTimeoutMillis = 1_000
        const val TrafficReadTimeoutMillis = 3_000
        const val MemoryReadTimeoutMillis = 3_500
        const val MemorySampleReadLimit = 3
        const val StreamReconnectDelayMillis = 1_000L
        const val BridgeTrafficPollIntervalMillis = 1_000L
        const val BridgeHealthCheckGraceMillis = 1_000L
    }
}

private suspend fun IOException.isExpectedMihomoStreamEnd(): Boolean {
    if (!currentCoroutineContext().isActive) {
        return false
    }
    return when (this) {
        is EOFException,
        is SocketTimeoutException -> true
        is SocketException -> message.isExpectedStreamCloseMessage()
        else -> message.isExpectedStreamCloseMessage()
    }
}

private fun BufferedReader.readNextNonBlankLine(): String? {
    while (true) {
        val line = readLine() ?: return null
        if (line.isNotBlank()) {
            return line
        }
    }
}

private fun String?.isExpectedStreamCloseMessage(): Boolean {
    val value = this?.lowercase().orEmpty()
    return ExpectedStreamCloseMessages.any(value::contains)
}

private fun ConfigurationOverride.copyMode(mode: TunnelState.Mode): ConfigurationOverride {
    return copy().also { override -> override.mode = mode }
}

private fun ConfigurationOverride.copyLogLevel(logLevel: LogMessage.Level): ConfigurationOverride {
    return copy().also { override -> override.logLevel = logLevel }
}

private fun String.toTunnelMode(): TunnelState.Mode {
    return when (lowercase()) {
        "direct" -> TunnelState.Mode.Direct
        "global" -> TunnelState.Mode.Global
        "script" -> TunnelState.Mode.Script
        else -> TunnelState.Mode.Rule
    }
}

private fun String.toLogMessageLevel(): LogMessage.Level {
    return when (lowercase()) {
        "debug" -> LogMessage.Level.Debug
        "warning" -> LogMessage.Level.Warning
        "error" -> LogMessage.Level.Error
        "silent" -> LogMessage.Level.Silent
        else -> LogMessage.Level.Info
    }
}

private fun TunnelState.Mode.mihomoModeName(): String {
    return when (this) {
        TunnelState.Mode.Direct -> "direct"
        TunnelState.Mode.Global -> "global"
        TunnelState.Mode.Rule -> "rule"
        TunnelState.Mode.Script -> "script"
    }
}

private fun List<MihomoProxyGroup>.filterVisibleForMode(
    mode: String,
    globalProxyNames: List<String>,
): List<MihomoProxyGroup> {
    if (mode.equals("direct", ignoreCase = true)) {
        return emptyList()
    }

    val groupByName = associateBy(MihomoProxyGroup::name)
    val candidateNames = globalProxyNames.ifEmpty { map(MihomoProxyGroup::name) }
    val result = mutableListOf<MihomoProxyGroup>()
    val added = mutableSetOf<String>()

    if (mode.equals("global", ignoreCase = true)) {
        groupByName[MihomoGlobalGroupName]?.let { globalGroup ->
            result += globalGroup
            added += globalGroup.name
        }
    }

    candidateNames.forEach { name ->
        val group = groupByName[name] ?: return@forEach
        if (group.name in added || group.hidden) {
            return@forEach
        }
        result += group
        added += group.name
    }
    return result
}

private data class TrafficBytes(
    val up: Long,
    val down: Long,
)

private fun Long.toTrafficBytes(): TrafficBytes {
    return TrafficBytes(
        up = (this ushr 32).scaledTrafficBytes(),
        down = (this and 0xFFFF_FFFFL).scaledTrafficBytes(),
    )
}

private fun Long.scaledTrafficBytes(): Long {
    val type = (this ushr 30) and 0x3
    val data = this and 0x3FFF_FFFFL
    return when (type) {
        0L -> data
        1L -> data.toBytesFromHundredths(1024L)
        2L -> data.toBytesFromHundredths(1024L * 1024L)
        3L -> data.toBytesFromHundredths(1024L * 1024L * 1024L)
        else -> 0L
    }
}

private fun Long.toBytesFromHundredths(unitBytes: Long): Long {
    return (this * unitBytes + 50L) / 100L
}

private fun String.urlEncode(): String {
    return URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
}

private fun JsonElement?.jsonObjectOrNull(): JsonObject? {
    return this as? JsonObject
}

private fun JsonElement?.jsonArrayOrNull(): JsonArray? {
    return this as? JsonArray
}

private fun JsonElement?.jsonPrimitiveOrNull(): JsonPrimitive? {
    return this as? JsonPrimitive
}

private fun JsonObject.stringValue(name: String): String? {
    return this[name].jsonPrimitiveOrNull()?.contentOrNull?.takeIf(String::isNotBlank)
}

private fun JsonObject.longValue(name: String): Long? {
    return this[name].longValue()
}

private fun JsonObject.intValue(name: String): Int? {
    return this[name].intValue()
}

private fun JsonObject.booleanValue(name: String): Boolean? {
    return this[name].jsonPrimitiveOrNull()?.contentOrNull?.toBooleanStrictOrNull()
}

private fun JsonObject.objectValue(name: String): JsonObject? {
    return this[name].jsonObjectOrNull()
}

private fun JsonObject.stringListValue(name: String): List<String> {
    return this[name].jsonArrayOrNull()
        ?.mapNotNull { item -> item.jsonPrimitiveOrNull()?.contentOrNull?.takeIf(String::isNotBlank) }
        .orEmpty()
}

private fun JsonObject.latestDelay(): Int? {
    val history = this["history"].jsonArrayOrNull().orEmpty()
    return history
        .asReversed()
        .firstNotNullOfOrNull { entry ->
            entry.jsonObjectOrNull()?.intValue("delay")?.toMihomoHistoryDelayOrNull()
        }
}

private fun JsonObject.toProxyProviderRuntimeDetail(fallbackName: String): MihomoProxyProviderRuntimeDetail {
    val nodes = this["proxies"].jsonArrayOrNull()
        ?.mapNotNull { item -> item.jsonObjectOrNull()?.toMihomoProviderNode() }
        .orEmpty()
    return MihomoProxyProviderRuntimeDetail(
        name = stringValue("name") ?: stringValue("Name") ?: fallbackName,
        type = stringValue("type") ?: stringValue("Type") ?: "",
        vehicleType = stringValue("vehicleType") ?: stringValue("VehicleType") ?: "",
        updatedAtMillis = longValue("updatedAt") ?: longValue("UpdatedAt") ?: 0L,
        testUrl = stringValue("testUrl") ?: stringValue("TestUrl") ?: "",
        expectedStatus = stringValue("expectedStatus") ?: stringValue("ExpectedStatus") ?: "",
        subscriptionInfo = objectValue("subscriptionInfo")
            ?.toMihomoProviderSubscriptionInfo()
            ?: objectValue("SubscriptionInfo")?.toMihomoProviderSubscriptionInfo(),
        nodes = nodes,
    )
}

private fun JsonObject.toMihomoProviderNode(): MihomoProviderNode {
    val name = stringValue("name") ?: stringValue("Name") ?: ""
    val type = stringValue("type") ?: stringValue("Type") ?: ""
    return MihomoProviderNode(
        name = name,
        title = stringValue("title") ?: stringValue("Title") ?: name,
        subtitle = stringValue("subtitle") ?: stringValue("Subtitle") ?: type,
        type = type,
        delay = intValue("delay")?.toMihomoHistoryDelayOrNull()
            ?: intValue("Delay")?.toMihomoHistoryDelayOrNull()
            ?: latestDelay(),
    )
}

private fun JsonObject.toMihomoProviderSubscriptionInfo(): MihomoProviderSubscriptionInfo {
    return MihomoProviderSubscriptionInfo(
        upload = longValue("upload") ?: longValue("Upload") ?: 0L,
        download = longValue("download") ?: longValue("Download") ?: 0L,
        total = longValue("total") ?: longValue("Total") ?: 0L,
        expire = longValue("expire") ?: longValue("Expire") ?: 0L,
    )
}

private fun JsonObject.errorMessageOrNull(): String? {
    return stringValue("error") ?: stringValue("Error")
}

private fun Int.toMihomoHistoryDelayOrNull(
    timeoutMillis: Int = DefaultMihomoDelayTimeoutMillis,
): Int? {
    return when {
        this <= 0 -> null
        this == MihomoUntestedDelay -> null
        this >= timeoutMillis -> MihomoTimeoutDelay
        else -> this
    }
}

private fun Int.toMihomoTestDelay(timeoutMillis: Int): Int {
    return when {
        this <= 0 -> MihomoTimeoutDelay
        this == MihomoUntestedDelay -> MihomoTimeoutDelay
        this >= timeoutMillis -> MihomoTimeoutDelay
        else -> this
    }
}

private suspend fun runDelayTestOrTimeout(block: suspend () -> Int): Int {
    return runCatching { block() }
        .getOrElse { error ->
            if (error is CancellationException) throw error
            MihomoTimeoutDelay
        }
}

private suspend fun runGroupDelayTestOrTimeout(block: suspend () -> Map<String, Int>): Map<String, Int> {
    return runCatching { block() }
        .getOrElse { error ->
            if (error is CancellationException) throw error
            emptyMap()
        }
}

private fun Map<String, Int>.withTimeoutFallback(
    proxyNames: List<String>,
    timeoutMillis: Int,
): Map<String, Int> {
    val normalized = linkedMapOf<String, Int>()
    forEach { (proxyName, delay) ->
        normalized[proxyName] = delay.toMihomoTestDelay(timeoutMillis)
    }
    proxyNames.forEach { proxyName ->
        normalized.putIfAbsent(proxyName, MihomoTimeoutDelay)
    }
    return normalized
}

private fun JsonElement?.longValue(): Long? {
    return jsonPrimitiveOrNull()?.longOrNull ?: jsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull()
}

private fun JsonElement?.intValue(): Int? {
    return jsonPrimitiveOrNull()?.intOrNull ?: jsonPrimitiveOrNull()?.contentOrNull?.toIntOrNull()
}

private class MihomoApiException(
    method: String,
    path: String,
    val status: Int,
    response: String,
) : IllegalStateException("Mihomo API $method $path failed with HTTP $status: $response")

private const val MihomoUntestedDelay = 65_535
private const val MihomoTimeoutDelay = -1
private const val MihomoGlobalGroupName = "GLOBAL"
private val ExpectedStreamCloseMessages = listOf(
    "eof",
    "unexpected end of stream",
    "end of stream",
    "stream was reset",
    "connection reset",
    "socket closed",
)
