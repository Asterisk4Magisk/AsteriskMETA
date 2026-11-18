// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import android.content.Context
import app.AppState
import app.ProjectInfo
import app.modes.RunModeTun
import engine.mihomo.binding.MihomoBridge
import features.logs.AndroidAppLogger
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

internal object BundledCore {
    val id: String
        get() = ProjectInfo.CORE_ID

    val displayName: String
        get() = if (id == MeowCoreId) "Meow" else "Mihomo"

    val launcherFileName: String
        get() = "lib$id.so"

    val usesRealMmdb: Boolean
        get() = id == MeowCoreId
}

internal const val MihomoCoreId = "mihomo"
internal const val MeowCoreId = "meow"

internal fun AppState.withCompatibleCoreSettings(): AppState {
    if (BundledCore.id != MeowCoreId || runMode != RunModeTun) return this
    val defaults = AppState()
    return copy(
        runMode = defaults.runMode,
        enableAutoConnect = defaults.enableAutoConnect,
        enableRootBootScript = defaults.enableRootBootScript,
    )
}

@Serializable
internal data class CoreCapabilities(
    val rootTun: Boolean = false,
) {
    fun supportsRunMode(runMode: Int): Boolean {
        return runMode != RunModeTun || rootTun
    }
}

/**
 * The native query may wait behind another in-process core call. Keep its immutable result cached
 * so capability reads used to compose a screen never enter JNI.
 */
internal object CoreCapabilitiesStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val started = AtomicBoolean(false)
    private val loaded = CompletableDeferred<CoreCapabilities>()
    private val mutableState = MutableStateFlow(
        CoreCapabilities(rootTun = BundledCore.id == MihomoCoreId),
    )
    private lateinit var scope: CoroutineScope

    val state: StateFlow<CoreCapabilities> = mutableState.asStateFlow()

    fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    fun initialize() {
        check(::scope.isInitialized) { "Core capabilities store is not attached" }
        if (!started.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                val capabilities = json.decodeFromString<CoreCapabilities>(MihomoBridge.capabilities())
                mutableState.value = capabilities
                loaded.complete(capabilities)
            } catch (error: Throwable) {
                loaded.completeExceptionally(error)
                if (error is CancellationException) throw error
                AndroidAppLogger.error(LogTag, "Failed to load bundled core capabilities", error)
            }
        }
    }

    suspend fun await(): CoreCapabilities {
        initialize()
        return loaded.await()
    }
}

internal object BundledCoreMarker {
    private const val FileName = "bundled-core-id"

    fun needsRepublish(context: Context): Boolean {
        val recorded = runCatching {
            File(context.noBackupFilesDir, FileName).takeIf(File::isFile)?.readText()?.trim()
        }.getOrNull()
        return recorded != ProjectInfo.CORE_ID
    }

    fun record(context: Context) {
        val directory = context.noBackupFilesDir
        if (!directory.exists() && !directory.mkdirs()) return
        runCatching { File(directory, FileName).writeText(ProjectInfo.CORE_ID) }
            .onFailure { error ->
                AndroidAppLogger.warn(LogTag, "Failed to record the bundled core id", error)
            }
    }
}

private const val LogTag = "BundledCore"
