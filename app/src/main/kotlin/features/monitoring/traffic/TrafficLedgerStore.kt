// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.traffic

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import features.logs.AndroidAppLogger
import kotlinx.serialization.json.Json

internal class TrafficLedgerStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        TrafficLedgerPreferencesName,
        Context.MODE_PRIVATE,
    )
    private var ledger = decodeTrafficLedger(preferences.getString(TrafficLedgerKey, null))
    private var persistedLedger = ledger
    private var dirty = false
    private var lastPersistedAtElapsedMillis = 0L

    @Synchronized
    fun snapshot(): TrafficLedger = ledger

    @Synchronized
    fun update(sample: TrafficLedgerSample): TrafficLedgerReduction {
        val previousBaseline = ledger.baseline
        val reduction = reduceTrafficLedger(ledger, sample)
        if (!reduction.changed) return reduction
        ledger = reduction.ledger
        dirty = ledger != persistedLedger

        val nextBaseline = ledger.baseline
        val boundaryChanged = previousBaseline?.sessionId != nextBaseline?.sessionId ||
            previousBaseline?.sourceId != nextBaseline?.sourceId ||
            previousBaseline?.localDay != nextBaseline?.localDay
        val now = SystemClock.elapsedRealtime()
        if (lastPersistedAtElapsedMillis == 0L ||
            boundaryChanged ||
            now - lastPersistedAtElapsedMillis >= TrafficLedgerPersistIntervalMillis
        ) {
            persistLocked(commit = false, nowElapsedMillis = now)
        }
        return reduction
    }

    @Synchronized
    fun flush() {
        if (!dirty) return
        persistLocked(commit = true, nowElapsedMillis = SystemClock.elapsedRealtime())
    }

    private fun persistLocked(commit: Boolean, nowElapsedMillis: Long) {
        if (!dirty) return
        val encoded = runCatching { TrafficLedgerJson.encodeToString(ledger) }
            .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to encode traffic ledger", error) }
            .getOrNull()
            ?: return
        val editor = preferences.edit().putString(TrafficLedgerKey, encoded)
        val persisted = if (commit) {
            commitSynchronously(editor)
        } else {
            editor.apply()
            true
        }
        if (!persisted) {
            AndroidAppLogger.warn(LogTag, "Failed to persist traffic ledger")
            return
        }
        persistedLedger = ledger
        dirty = false
        lastPersistedAtElapsedMillis = nowElapsedMillis
    }
}

@SuppressLint("ApplySharedPref")
private fun commitSynchronously(editor: SharedPreferences.Editor): Boolean {
    // flush() must not return before cancellation-sensitive ledger data is persisted.
    return editor.commit()
}

internal fun decodeTrafficLedger(encoded: String?): TrafficLedger {
    if (encoded.isNullOrBlank()) return TrafficLedger()
    return runCatching { TrafficLedgerJson.decodeFromString<TrafficLedger>(encoded) }
        .getOrNull()
        ?.takeIf { ledger -> ledger.version == TrafficLedgerVersion }
        ?: TrafficLedger()
}

private val TrafficLedgerJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
private const val TrafficLedgerPreferencesName = "asteriskmeta_traffic_ledger"
private const val TrafficLedgerKey = "traffic_ledger_json"
private const val TrafficLedgerPersistIntervalMillis = 15_000L
private const val LogTag = "TrafficLedgerStore"
