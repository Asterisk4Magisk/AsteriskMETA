// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.AppState
import app.R
import engine.mihomo.MihomoProviderType
import features.mihomo.provider.ProviderDeclarationsState
import features.mihomo.provider.loadProviderDeclarationsByType
import features.mihomo.provider.mihomoProviderDataDir
import ui.components.AsteriskSearchField
import ui.icons.AsteriskIcons as Icons
import utils.toTrimmedNonEmptyDistinctList

@Composable
internal fun TunBypassRuleSetBottomSheet(
    show: Boolean,
    appState: AppState,
    selectedTags: List<String>,
    onSelectedTagsChange: (List<String>) -> Unit,
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
        state = loadProviderDeclarationsByType(
            context,
            appState,
            context.mihomoProviderDataDir(),
        )[MihomoProviderType.Rule] ?: ProviderDeclarationsState()
    }
    val selected = selectedTags.toTrimmedNonEmptyDistinctList()
    val providers = state.providers.filter { it.ruleMetadata?.tunBypassEligible == true }
    val availableNames = providers.map { it.name }.toSet()
    val filteredProviders = providers.filter { it.name.contains(query, ignoreCase = true) }
    val unavailableTags = selected.filter {
        it !in availableNames && it.contains(query, ignoreCase = true)
    }
    SettingsModalBottomSheet(
        show = show,
        title = stringResource(R.string.settings_root_ebpf_bypass_direct_cidrs),
        startAction = {
            TextButton(
                text = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
        endAction = {
            TextButton(
                text = stringResource(R.string.common_save),
                icon = Icons.Rounded.Save,
                onClick = { onSave(selected) },
                enabled = !state.loading && state.error.isBlank(),
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        SettingsSheetContent {
            TunBypassRuleSetSection(
                title = stringResource(R.string.settings_tun_bypass_rule_sets_picker_title),
            ) {
                AsteriskSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.common_search),
                    clearContentDescription = stringResource(R.string.common_clear),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
                when {
                    state.loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    state.error.isNotBlank() -> {
                        TunBypassRuleSetStatusText(
                            text = state.error,
                            isError = true,
                        )
                        TextButton(
                            text = stringResource(R.string.common_retry),
                            icon = Icons.Rounded.Refresh,
                            onClick = { reloadToken += 1 },
                        )
                    }
                    else -> {
                        if (providers.isEmpty()) {
                            TunBypassRuleSetStatusText(
                                text = stringResource(R.string.settings_tun_bypass_rule_sets_empty),
                            )
                        }
                        if (query.isNotBlank() && filteredProviders.isEmpty() && unavailableTags.isEmpty()) {
                            TunBypassRuleSetStatusText(
                                text = stringResource(R.string.settings_tun_bypass_rule_sets_no_match),
                            )
                        }
                        filteredProviders.forEach { provider ->
                            SwitchPreference(
                                title = provider.name,
                                icon = Icons.Rounded.Route,
                                summary = listOfNotNull(
                                    provider.vehicleType,
                                    provider.ruleMetadata?.behavior,
                                    provider.ruleMetadata?.format,
                                ).joinToString(" · "),
                                checked = provider.name in selected,
                                onCheckedChange = { checked ->
                                    onSelectedTagsChange(
                                        if (checked) selected + provider.name else selected - provider.name,
                                    )
                                },
                            )
                        }
                        unavailableTags.forEach { tag ->
                            SwitchPreference(
                                title = tag,
                                icon = Icons.Rounded.Block,
                                summary = stringResource(R.string.settings_tun_bypass_rule_set_unavailable),
                                checked = true,
                                onCheckedChange = { onSelectedTagsChange(selected - tag) },
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_tun_bypass_rule_sets_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun TunBypassRuleSetSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
    }
}

@Composable
private fun TunBypassRuleSetStatusText(
    text: String,
    isError: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
