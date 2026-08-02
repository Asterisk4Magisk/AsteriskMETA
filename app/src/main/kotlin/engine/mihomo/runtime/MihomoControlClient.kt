// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.core.model.Provider
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

    fun getConnectionCount(
        config: MihomoControlConfig,
        useBridge: Boolean,
    ): Int {
        if (useBridge) {
            return Clash.queryConnectionCount()
        }
        return requestJsonObject(config, "/connections")["connections"]
            .jsonArrayOrNull()
            ?.size
            ?: error("Invalid Mihomo API response for /connections")
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
        val response = if (useBridge) {
            Bridge.nativeQueryRuntimeProxies()
        } else {
            request(config, "/asterisk/runtime/proxies")
        }
        val snapshot = parseMihomoRuntimeProxySnapshot(response)
        val globalProxyNames = snapshot.groups
            .firstOrNull { group -> group.name == MihomoGlobalGroupName }
            ?.all
            ?.map(MihomoProxyNodeId::name)
            .orEmpty()
        return snapshot.copy(
            groups = snapshot.groups.filterVisibleForMode(mode, globalProxyNames),
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

    fun getRuleProviders(
        config: MihomoControlConfig,
        useBridge: Boolean,
    ): Map<String, MihomoRuleProviderRuntimeSummary> {
        if (useBridge) {
            return Clash.queryProviders().queryMihomoRuleProviderSummaries { provider ->
                Bridge.nativeQueryProvider(Provider.Type.Rule.toString(), provider.name)
            }
        }
        return requestJsonObject(config, "/providers/rules")
            .toMihomoRuleProviderRuntimeSummaries()
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

    suspend fun updateRuleProvider(
        config: MihomoControlConfig,
        providerName: String,
        useBridge: Boolean,
    ) {
        if (useBridge) {
            Clash.updateProvider(Provider.Type.Rule, providerName).await()
            return
        }
        request(config, "/providers/rules/${providerName.urlEncode()}", method = "PUT")
    }

    suspend fun reloadProfile(
        config: MihomoControlConfig,
        profilePath: String,
        reloadLocally: Boolean,
    ) {
        if (reloadLocally) {
            AndroidMihomoRuntime.reloadProfile()
            return
        }
        val body = buildJsonObject {
            put("path", profilePath)
        }
        request(config, "/configs?force=true", method = "PUT", body = body.toString())
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
        proxyId: MihomoProxyNodeId,
        url: String = DefaultMihomoDelayTestUrl,
        timeoutMillis: Int = DefaultMihomoDelayTimeoutMillis,
        expectedStatus: String = "",
        useBridge: Boolean,
    ): MihomoDelayResult {
        val response = if (useBridge) {
            Bridge.nativeQueryRuntimeNodeDelay(
                name = proxyId.name,
                provider = proxyId.providerName.orEmpty(),
                url = url,
                expected = expectedStatus,
                timeoutMillis = timeoutMillis,
            )
        } else {
            request(
                config,
                "/asterisk/runtime/delay/node" +
                    "?name=${proxyId.name.urlEncode()}" +
                    "&provider=${proxyId.providerName.orEmpty().urlEncode()}" +
                    "&url=${url.urlEncode()}" +
                    "&expected=${expectedStatus.urlEncode()}" +
                    "&timeout=$timeoutMillis",
            )
        }
        return parseMihomoDelayResult(response)
    }

    suspend fun testGroupDelay(
        config: MihomoControlConfig,
        groupName: String,
        url: String = DefaultMihomoDelayTestUrl,
        timeoutMillis: Int = DefaultMihomoDelayTimeoutMillis,
        expectedStatus: String = "",
        useBridge: Boolean,
    ): MihomoDelayResult {
        val response = if (useBridge) {
            Bridge.nativeQueryRuntimeGroupDelay(
                name = groupName,
                url = url,
                expected = expectedStatus,
                timeoutMillis = timeoutMillis,
            )
        } else {
            request(
                config,
                "/asterisk/runtime/delay/group/${groupName.urlEncode()}" +
                    "?url=${url.urlEncode()}" +
                    "&expected=${expectedStatus.urlEncode()}" +
                    "&timeout=$timeoutMillis",
            )
        }
        return parseMihomoDelayResult(response)
    }

    suspend fun testProviderDelay(
        config: MihomoControlConfig,
        providerName: String,
        url: String = DefaultMihomoDelayTestUrl,
        timeoutMillis: Int = DefaultMihomoDelayTimeoutMillis,
        expectedStatus: String = "",
        useBridge: Boolean,
    ): MihomoDelayResult {
        val response = if (useBridge) {
            Bridge.nativeQueryRuntimeProviderDelay(
                name = providerName,
                url = url,
                expected = expectedStatus,
                timeoutMillis = timeoutMillis,
            )
        } else {
            request(
                config,
                "/asterisk/runtime/delay/provider/${providerName.urlEncode()}" +
                    "?url=${url.urlEncode()}" +
                    "&expected=${expectedStatus.urlEncode()}" +
                    "&timeout=$timeoutMillis",
            )
        }
        return parseMihomoDelayResult(response)
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
    }
}

internal fun Iterable<Provider>.queryMihomoRuleProviderSummaries(
    query: (Provider) -> String?,
): Map<String, MihomoRuleProviderRuntimeSummary> {
    val runtimeJson = Json { ignoreUnknownKeys = true }
    return asSequence()
        .filter { provider -> provider.type == Provider.Type.Rule }
        .mapNotNull { provider ->
            try {
                val response = query(provider) ?: return@mapNotNull null
                val root = runtimeJson.parseToJsonElement(response).jsonObjectOrNull()
                    ?: return@mapNotNull null
                if (root.errorMessageOrNull() != null) return@mapNotNull null
                val summary = root.toMihomoRuleProviderRuntimeSummary(provider.name)
                summary.name to summary
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                null
            }
        }
        .toMap()
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
        ?.mapNotNull { item -> item.jsonObjectOrNull()?.toMihomoProxyProviderNode() }
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

private fun JsonObject.toMihomoProxyProviderNode(): MihomoProxyProviderNode {
    val name = stringValue("name") ?: stringValue("Name") ?: ""
    val type = stringValue("type") ?: stringValue("Type") ?: ""
    val normalizedDelay = intValue("delay")?.toMihomoHistoryDelayOrNull()
        ?: intValue("Delay")?.toMihomoHistoryDelayOrNull()
        ?: latestDelay()
    return MihomoProxyProviderNode(
        name = name,
        title = stringValue("title") ?: stringValue("Title") ?: name,
        subtitle = stringValue("subtitle") ?: stringValue("Subtitle") ?: type,
        type = type,
        delay = normalizedDelay?.takeIf { delay -> delay > 0 },
        delayStatus = when {
            normalizedDelay == null -> null
            normalizedDelay < 0 -> MihomoDelayStatus.Timeout
            else -> MihomoDelayStatus.Success
        },
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
