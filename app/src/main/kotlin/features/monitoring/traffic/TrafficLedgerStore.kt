// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.traffic

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.Json

internal class TrafficLedgerStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        TrafficLedgerPreferencesName,
        Context.MODE_PRIVATE,
    )
    private var ledger = decodeTrafficLedger(preferences.getString(TrafficLedgerKey, null))

    @Synchronized
    fun snapshot(): TrafficLedger = ledger

    @Synchronized
    fun update(sample: TrafficLedgerSample): TrafficLedgerReduction {
        val reduction = reduceTrafficLedger(ledger, sample)
        if (!reduction.changed) return reduction
        ledger = reduction.ledger
        preferences.edit {
            putString(TrafficLedgerKey, TrafficLedgerJson.encodeToString(ledger))
        }
        return reduction
    }
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
