// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.AppState

@Dao
internal abstract class AppStateDao {
    @Transaction
    open suspend fun loadState(): PersistedAppState {
        return PersistedAppState(
            mihomoProfiles = findMihomoProfiles(),
            mihomoOverrideScripts = findMihomoOverrideScripts(),
            proxyAppListSelectedApps = findProxyAppListSelectedApps(),
        )
    }

    @Transaction
    open suspend fun saveState(previousState: AppState, nextState: AppState, replaceAll: Boolean) {
        saveLists(previousState, nextState, replaceAll)
    }

    private suspend fun saveLists(previousState: AppState, nextState: AppState, replaceAll: Boolean) {
        if (replaceAll || previousState.mihomoProfiles != nextState.mihomoProfiles) {
            replaceMihomoProfiles(nextState.mihomoProfiles.mapIndexed { index, profile ->
                MihomoProfileEntity.from(index, profile)
            })
        }

        if (replaceAll || previousState.mihomoOverrideScripts != nextState.mihomoOverrideScripts) {
            replaceMihomoOverrideScripts(nextState.mihomoOverrideScripts.mapIndexed { index, script ->
                MihomoOverrideScriptEntity.from(index, script)
            })
        }

        if (replaceAll || previousState.proxyAppListSelectedApps != nextState.proxyAppListSelectedApps) {
            replaceProxyAppListSelectedApps(nextState.proxyAppListSelectedApps.mapIndexed { index, packageKey ->
                ProxyAppListSelectedAppEntity(position = index, packageKey = packageKey)
            })
        }
    }

    @Query("SELECT * FROM mihomo_profiles ORDER BY position ASC")
    protected abstract suspend fun findMihomoProfiles(): List<MihomoProfileEntity>

    @Query("SELECT * FROM mihomo_override_scripts ORDER BY position ASC")
    protected abstract suspend fun findMihomoOverrideScripts(): List<MihomoOverrideScriptEntity>

    @Query("SELECT * FROM proxy_app_list_selected_apps ORDER BY position ASC")
    protected abstract suspend fun findProxyAppListSelectedApps(): List<ProxyAppListSelectedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertMihomoProfiles(entities: List<MihomoProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertMihomoOverrideScripts(entities: List<MihomoOverrideScriptEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertProxyAppListSelectedApps(entities: List<ProxyAppListSelectedAppEntity>)

    @Query("DELETE FROM mihomo_profiles")
    protected abstract suspend fun deleteMihomoProfiles()

    @Query("DELETE FROM mihomo_override_scripts")
    protected abstract suspend fun deleteMihomoOverrideScripts()

    @Query("DELETE FROM proxy_app_list_selected_apps")
    protected abstract suspend fun deleteProxyAppListSelectedApps()

    private suspend fun replaceMihomoProfiles(entities: List<MihomoProfileEntity>) {
        deleteMihomoProfiles()
        insertMihomoProfiles(entities)
    }

    private suspend fun replaceMihomoOverrideScripts(entities: List<MihomoOverrideScriptEntity>) {
        deleteMihomoOverrideScripts()
        insertMihomoOverrideScripts(entities)
    }

    private suspend fun replaceProxyAppListSelectedApps(entities: List<ProxyAppListSelectedAppEntity>) {
        deleteProxyAppListSelectedApps()
        insertProxyAppListSelectedApps(entities)
    }
}
