package engine.mihomo.binding

import libclash.*
import android.app.Application
import android.os.Build
import androidx.core.net.toUri
import go.Seq
import engine.mihomo.binding.model.*
import engine.mihomo.binding.util.parseInetSocketAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

internal object MihomoBridge {
    private lateinit var application: Application
    private val initialized by lazy {
        Seq.setContext(application)
        val home = application.filesDir.resolve("clash").apply { mkdirs() }
        Libclash.init(home.absolutePath, app.ProjectInfo.VERSION_NAME, Build.VERSION.SDK_INT, object : ContentResolver {
            override fun openContent(uri: String): Int {
                val parsed = uri.toUri()
                require(parsed.scheme == "content") { "Unsupported content scheme" }
                return application.contentResolver.openFileDescriptor(parsed, "r")?.detachFd()
                    ?: throw java.io.FileNotFoundException("Content provider returned no descriptor")
            }
        })
        true
    }

    fun initialize(application: Application) {
        this.application = application
    }

    private fun ensureInitialized() { check(initialized) }

    fun coreVersion(): String {
        ensureInitialized()
        return Libclash.coreVersion()
    }

    fun queryProvider(type: Provider.Type, name: String): String {
        ensureInitialized()
        return Libclash.queryProvider(type.toString(), name)
    }

    enum class OverrideSlot {
        Persist, Session
    }

    private val ConfigurationOverrideJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun reset() {
        ensureInitialized()
        Libclash.reset()
    }

    fun queryTunnelState(): TunnelState {
        ensureInitialized()
        val json = Libclash.queryTunnelState()

        return Json.decodeFromString(TunnelState.serializer(), json)
    }

    fun queryTrafficNow(): Traffic {
        ensureInitialized()
        return Libclash.queryTrafficNow()
    }

    fun queryTrafficTotal(): Traffic {
        ensureInitialized()
        return Libclash.queryTrafficTotal()
    }

    fun queryMemory(): Long {
        ensureInitialized()
        return Libclash.queryMemory()
    }

    fun queryConnectionCount(): Int {
        ensureInitialized()
        return Libclash.queryConnectionCount()
    }

    fun queryConnections(): String {
        ensureInitialized()
        return Libclash.queryConnections()
    }

    fun closeConnection(id: String): Boolean {
        ensureInitialized()
        return Libclash.closeConnection(id)
    }

    fun closeAllConnections() {
        ensureInitialized()
        Libclash.closeAllConnections()
    }

    fun startTun(
        fd: Int,
        stack: String,
        gateway: String,
        portal: String,
        dns: String,
        markSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int
    ) {
        ensureInitialized()
        Libclash.startTun(fd, stack, gateway, portal, dns, object : TunInterface {
            override fun markSocket(fd: Int) {
                markSocket(fd)
            }

            override fun querySocketUid(protocol: Int, source: String, target: String): Int {
                return querySocketUid(
                    protocol,
                    parseInetSocketAddress(source),
                    parseInetSocketAddress(target)
                )
            }
        })
    }

    fun stopTun() {
        ensureInitialized()
        Libclash.stopTun()
    }

    fun startTunContext(
        markSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int
    ) {
        ensureInitialized()
        Libclash.startTunContext(object : TunInterface {
            override fun markSocket(fd: Int) {
                markSocket(fd)
            }

            override fun querySocketUid(protocol: Int, source: String, target: String): Int {
                return querySocketUid(
                    protocol,
                    parseInetSocketAddress(source),
                    parseInetSocketAddress(target)
                )
            }
        })
    }

    fun stopTunContext() {
        ensureInitialized()
        Libclash.stopTunContext()
    }

    fun queryProxies(): String {
        ensureInitialized()
        return Libclash.queryProxies()
    }

    fun queryProxyDelay(
        name: String,
        url: String,
        timeoutMillis: Int,
        expectedStatus: String = "",
    ): Int {
        ensureInitialized()
        val response = Json.Default.decodeFromString(
            JsonObject.serializer(),
            Libclash.queryProxyDelay(name, url, timeoutMillis, expectedStatus),
        )
        response.errorOrNull()?.let { throw MihomoCoreException(it) }

        return response["delay"]?.jsonPrimitive?.intOrNull
            ?: throw MihomoCoreException("Invalid proxy delay response")
    }

    fun queryProviderProxyDelay(
        providerName: String,
        name: String,
        url: String,
        timeoutMillis: Int,
        expectedStatus: String,
    ): Int {
        ensureInitialized()
        val response = Json.Default.decodeFromString(
            JsonObject.serializer(),
            Libclash.queryProviderProxyDelay(
                providerName,
                name,
                url,
                timeoutMillis,
                expectedStatus,
            ),
        )
        response.errorOrNull()?.let { throw MihomoCoreException(it) }

        return response["delay"]?.jsonPrimitive?.intOrNull
            ?: throw MihomoCoreException("Invalid provider proxy delay response")
    }

    fun queryGroupDelay(
        name: String,
        url: String,
        timeoutMillis: Int,
        expectedStatus: String = "",
    ): Map<String, Int> {
        ensureInitialized()
        val response = Json.Default.decodeFromString(
            JsonObject.serializer(),
            Libclash.queryGroupDelay(name, url, timeoutMillis, expectedStatus),
        )
        response.errorOrNull()?.let { throw MihomoCoreException(it) }

        return response.mapValues { (_, value) ->
            value.jsonPrimitive.intOrNull
                ?: throw MihomoCoreException("Invalid group delay response")
        }
    }

    fun patchSelector(selector: String, name: String): Boolean {
        ensureInitialized()
        return Libclash.patchSelector(selector, name)
    }

    suspend fun fetchAndValid(
        path: File,
        url: String,
        options: FetchOptions = FetchOptions(),
        reportStatus: (FetchStatus) -> Unit
    ) {
        ensureInitialized()
        val taskId = nextFetchTaskId.getAndIncrement()
        val completion = CompletableDeferred<Unit>()
        Libclash.fetchAndValid(
            object : FetchCallback {
                override fun report(statusJson: String) {
                    reportStatus(
                        Json.Default.decodeFromString(
                            FetchStatus.serializer(),
                            statusJson
                        )
                    )
                }

                override fun complete(error: String) {
                    if (error.isNotEmpty()) {
                        completion.completeExceptionally(MihomoCoreException(error))
                    } else {
                        completion.complete(Unit)
                    }
                }
            },
            taskId,
            path.absolutePath,
            url,
            Json.Default.encodeToString(FetchOptions.serializer(), options),
        )
        try {
            completion.await()
        } catch (error: CancellationException) {
            Libclash.cancelFetch(taskId)
            withContext(NonCancellable) {
                runCatching { completion.await() }
            }
            throw error
        }
    }

    fun load(path: File): CompletableDeferred<Unit> {
        ensureInitialized()
        return CompletableDeferred<Unit>().apply {
            Libclash.load(this.asCompletion(), path.absolutePath)
        }
    }

    fun load(path: File, content: ByteArray): CompletableDeferred<Unit> {
        ensureInitialized()
        return CompletableDeferred<Unit>().apply {
            Libclash.loadFromBytes(this.asCompletion(), path.absolutePath, content)
        }
    }

    fun queryProviders(): List<Provider> {
        ensureInitialized()
        val providers =
            Json.Default.decodeFromString(JsonArray.serializer(), Libclash.queryProviders())

        return List(providers.size) {
            Json.Default.decodeFromJsonElement(Provider.serializer(), providers[it])
        }
    }

    fun updateProvider(type: Provider.Type, name: String): CompletableDeferred<Unit> {
        ensureInitialized()
        return CompletableDeferred<Unit>().apply {
            Libclash.updateProvider(this.asCompletion(), type.toString(), name)
        }
    }

    fun queryOverride(slot: OverrideSlot): ConfigurationOverride {
        ensureInitialized()
        return try {
            ConfigurationOverrideJson.decodeFromString(
                ConfigurationOverride.serializer(),
                Libclash.readOverride(slot.ordinal)
            )
        } catch (e: Exception) {
            ConfigurationOverride()
        }
    }

    fun patchOverride(slot: OverrideSlot, configuration: ConfigurationOverride) {
        ensureInitialized()
        Libclash.writeOverride(
            slot.ordinal,
            ConfigurationOverrideJson.encodeToString(
                ConfigurationOverride.serializer(),
                configuration
            )
        )
    }

    fun clearOverride(slot: OverrideSlot) {
        ensureInitialized()
        Libclash.clearOverride(slot.ordinal)
    }

    fun subscribeLogcat(): ReceiveChannel<LogMessage> {
        ensureInitialized()
        return Channel<LogMessage>(32).apply {
            val subscription = Libclash.subscribeLogcat(object : LogcatInterface {
                override fun received(jsonPayload: String): Boolean {
                    val message = runCatching {
                        Json.decodeFromString(LogMessage.serializer(), jsonPayload)
                    }.getOrNull() ?: return true

                    return !trySend(message).isClosed
                }
            })
            invokeOnClose { subscription.close() }
        }
    }

    fun setAgeSecretKey(key: String?) {
        ensureInitialized()
        Libclash.setAgeSecretKey(key.orEmpty())
    }

    fun decryptAge(content: String, secretKey: String?): String {
        ensureInitialized()
        return Libclash.decryptAge(content, secretKey.orEmpty())
    }

    fun veritySecretKeys(vararg secretKeys: String): Boolean {
        ensureInitialized()
        return Libclash.veritySecretKeys(secretKeys.firstOrNull() ?: "")
    }

}

private val nextFetchTaskId = AtomicLong(1L)

private fun JsonObject.errorOrNull(): String? {
    return this["error"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
}

private class MihomoCoreException(message: String) : Exception(message)

private fun CompletableDeferred<Unit>.asCompletion(): Completion = object : Completion {
    override fun complete(errorText: String) {
        if (errorText.isEmpty()) this@asCompletion.complete(Unit)
        else this@asCompletion.completeExceptionally(MihomoCoreException(errorText))
    }
}
