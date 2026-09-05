// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.AppState
import app.R
import engine.mihomo.MihomoProviderType
import features.mihomo.provider.ProviderDeclarationsState
import features.mihomo.provider.loadProviderDeclarationsByType
import features.mihomo.provider.mihomoProviderDataDir
import ui.components.AsteriskSearchField
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun TunBypassProvidersBottomSheet(
    show: Boolean,
    appState: AppState,
    selectedNames: List<String>,
    onSelectedNamesChange: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    if (!show) return
    val context = LocalContext.current.applicationContext
    var state by remember { mutableStateOf(ProviderDeclarationsState(loading = true)) }
    var query by remember { mutableStateOf("") }
    var reloadToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(appState, reloadToken) {
        state = ProviderDeclarationsState(loading = true)
        state = loadProviderDeclarationsByType(context, appState, context.mihomoProviderDataDir())[MihomoProviderType.Rule]
            ?: ProviderDeclarationsState()
    }
    val providers = state.providers.filter { it.ruleMetadata?.tunBypassEligible == true }
    val availableNames = providers.map { it.name }.toSet()
    val filteredProviders = providers.filter { it.name.contains(query, ignoreCase = true) }
    val unavailableNames = selectedNames.filter { it !in availableNames && it.contains(query, ignoreCase = true) }
    SettingsModalBottomSheet(
        show = show,
        title = stringResource(R.string.settings_tun_bypass_providers),
        startAction = {
            TextButton(stringResource(R.string.common_cancel), Icons.Rounded.Close, onDismissRequest)
        },
        endAction = {
            TextButton(stringResource(R.string.common_save), Icons.Rounded.Save, {
                onSave(selectedNames.distinct())
            }, enabled = !state.loading && state.error.isBlank())
        },
        onDismissRequest = onDismissRequest,
    ) {
        SettingsSheetContent {
            Text(stringResource(R.string.settings_tun_bypass_providers_description), Modifier.padding(16.dp))
            AsteriskSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.common_search),
                clearContentDescription = stringResource(R.string.common_clear),
            )
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                state.error.isNotBlank() -> {
                    Text(state.error, Modifier.padding(16.dp))
                    TextButton(stringResource(R.string.common_retry), Icons.Rounded.Refresh, { reloadToken += 1 })
                }
                else -> {
                    if (providers.isEmpty()) Text(stringResource(R.string.settings_tun_bypass_providers_empty), Modifier.padding(16.dp))
                    if (query.isNotBlank() && filteredProviders.isEmpty() && unavailableNames.isEmpty()) {
                        Text(stringResource(R.string.settings_tun_bypass_providers_no_match), Modifier.padding(16.dp))
                    }
                    filteredProviders.forEach { provider ->
                        SwitchPreference(
                            title = provider.name,
                            icon = Icons.Rounded.Route,
                            summary = listOfNotNull(provider.vehicleType, provider.ruleMetadata?.behavior, provider.ruleMetadata?.format).joinToString(" · "),
                            checked = provider.name in selectedNames,
                            onCheckedChange = { checked ->
                                onSelectedNamesChange(if (checked) (selectedNames + provider.name).distinct() else selectedNames - provider.name)
                            },
                        )
                    }
                    unavailableNames.forEach { name ->
                        SwitchPreference(
                            title = name,
                            icon = Icons.Rounded.Block,
                            summary = stringResource(R.string.settings_tun_bypass_provider_unavailable),
                            checked = true,
                            onCheckedChange = { onSelectedNamesChange(selectedNames - name) },
                        )
                    }
                }
            }
        }
    }
}
