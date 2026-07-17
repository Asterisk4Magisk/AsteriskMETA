// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import app.AppState
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AndroidAppStateStore private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var database = buildDatabase()
    private var dao = database.appStateDao()
    private val settingsPreferences = AppSettingsPreferences(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateLock = Any()
    private val saveMutex = Mutex()
    private val saveRevision = AtomicLong(0)
    private val hasPersistedState = AtomicBoolean(false)
    private val loadedState = loadInitialState()
    private val persistenceTracker = AppStatePersistenceTracker(loadedState.state)
    private val mutableState = MutableStateFlow(loadedState.state)

    init {
        hasPersistedState.set(loadedState.loadedFromDatabase)
    }

    val state: StateFlow<AppState> = mutableState.asStateFlow()

    fun update(transform: (AppState) -> AppState) {
        val pendingSave = synchronized(updateLock) {
            val previousState = mutableState.value
            val nextState = transform(previousState)
            if (nextState === previousState || nextState.isCheapNoopUpdate(previousState)) {
                null
            } else {
                mutableState.value = nextState
                PendingStateSave(
                    nextState = nextState,
                    revision = saveRevision.incrementAndGet(),
                )
            }
        } ?: return

        persist(pendingSave.nextState, pendingSave.revision)
    }

    private fun loadInitialState(): LoadedAppState {
        return runBlocking(Dispatchers.IO) {
            val persistedState = runCatching {
                dao.loadState()
            }.onFailure { error ->
                AndroidAppLogger.error(LogTag, "Failed to load app state", error)
                resetDatabase()
            }.getOrNull()
            val settingsState = settingsPreferences.load()
            if (persistedState?.hasRoomContent() == true) {
                LoadedAppState(
                    state = persistedState.toAppState(settingsState),
                    loadedFromDatabase = true,
                )
            } else {
                LoadedAppState(
                    state = settingsState,
                    loadedFromDatabase = false,
                )
            }
        }
    }

    private fun persist(nextState: AppState, revision: Long) {
        scope.launch {
            saveMutex.withLock {
                if (revision != saveRevision.get()) {
                    return@withLock
                }
                val plan = persistenceTracker.plan(
                    nextState = nextState,
                    hasPersistedRoomState = hasPersistedState.get(),
                )
                runCatching {
                    settingsPreferences.save(plan.nextState)
                    dao.saveState(
                        previousState = plan.previousState,
                        nextState = plan.nextState,
                        replaceAll = plan.replaceAll,
                    )
                    hasPersistedState.set(true)
                    persistenceTracker.markPersisted(plan.nextState)
                }.onFailure { error ->
                    AndroidAppLogger.error(LogTag, "Failed to persist app state", error)
                    resetDatabase()
                    runCatching {
                        settingsPreferences.save(nextState)
                        dao.saveState(
                            previousState = AppState(),
                            nextState = nextState,
                            replaceAll = true,
                        )
                        hasPersistedState.set(true)
                        persistenceTracker.markPersisted(nextState)
                    }.onFailure { retryError ->
                        AndroidAppLogger.error(LogTag, "Failed to persist app state after database reset", retryError)
                    }
                }
            }
        }
    }

    private fun buildDatabase(): AsteriskAppDatabase {
        return Room.databaseBuilder(
            appContext,
            AsteriskAppDatabase::class.java,
            AsteriskDatabaseName,
        )
            // Keep committed state in the main DB file for file-based backup tools.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addMigrations(
                AsteriskAppDatabase.Migration1To2,
                AsteriskAppDatabase.Migration2To3,
                AsteriskAppDatabase.Migration3To4,
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    private fun resetDatabase() {
        runCatching { database.close() }
            .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to close app state database before reset", error) }
        runCatching { appContext.deleteDatabase(AsteriskDatabaseName) }
            .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to delete app state database during reset", error) }
        database = buildDatabase()
        dao = database.appStateDao()
        hasPersistedState.set(false)
    }

    companion object {
        private const val LogTag = "AndroidAppStateStore"

        @Volatile
        private var instance: AndroidAppStateStore? = null

        fun get(context: Context): AndroidAppStateStore {
            return instance ?: synchronized(this) {
                instance ?: AndroidAppStateStore(context).also { store ->
                    instance = store
                }
            }
        }
    }
}

private fun AppState.isCheapNoopUpdate(previous: AppState): Boolean {
    return mihomoProfiles === previous.mihomoProfiles &&
        mihomoOverrideScripts === previous.mihomoOverrideScripts &&
        customResourceFiles === previous.customResourceFiles &&
        dnsFakeIpFilter === previous.dnsFakeIpFilter &&
        dnsDefaultNameserver === previous.dnsDefaultNameserver &&
        dnsNameserver === previous.dnsNameserver &&
        dnsNameserverPolicy === previous.dnsNameserverPolicy &&
        dnsProxyServerNameserver === previous.dnsProxyServerNameserver &&
        dnsFallback === previous.dnsFallback &&
        dnsFallbackFilterGeosite === previous.dnsFallbackFilterGeosite &&
        dnsFallbackFilterIpcidr === previous.dnsFallbackFilterIpcidr &&
        dnsFallbackFilterDomain === previous.dnsFallbackFilterDomain &&
        dnsHosts === previous.dnsHosts &&
        externalInterfaces === previous.externalInterfaces &&
        ignoredInterfaces === previous.ignoredInterfaces &&
        privateAddressCidrs === previous.privateAddressCidrs &&
        proxyAppListSelectedApps === previous.proxyAppListSelectedApps &&
        this == previous
}

private data class PendingStateSave(
    val nextState: AppState,
    val revision: Long,
)

private data class LoadedAppState(
    val state: AppState,
    val loadedFromDatabase: Boolean,
)
