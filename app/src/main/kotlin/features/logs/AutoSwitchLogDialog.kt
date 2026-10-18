// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.AppState
import app.LocalAppServices
import app.R
import engine.mihomo.runtime.MihomoAutoSwitchEvent
import engine.mihomo.runtime.MihomoAutoSwitchGroupStatus
import engine.mihomo.runtime.MihomoAutoSwitchPhaseChecking
import engine.mihomo.runtime.MihomoAutoSwitchPhaseWaiting
import engine.mihomo.runtime.MihomoAutoSwitchProgress
import engine.mihomo.runtime.MihomoAutoSwitchState
import kotlinx.coroutines.delay
import ui.components.AsteriskActionButton
import java.text.DateFormat
import java.util.Date
import ui.icons.AsteriskIcons as Icons

/** Shows the running core's in-memory history; nothing is stored by the app. */
@Composable
internal fun AutoSwitchLogDialog(
    appState: AppState,
    onDismissRequest: () -> Unit,
) {
    val runtime = LocalAppServices.current.mihomoRuntime
    val latestAppState by rememberUpdatedState(appState)
    var state by remember { mutableStateOf<MihomoAutoSwitchState?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(runtime, appState.proxyRunning) {
        state = null
        loadFailed = false
        while (appState.proxyRunning) {
            val result = runtime.autoSwitchState(latestAppState)
            state = result.getOrNull() ?: state
            loadFailed = result.isFailure
            nowMillis = System.currentTimeMillis()
            delay(AutoSwitchLogRefreshMillis)
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) { DateFormat.getTimeInstance(DateFormat.MEDIUM, locale) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
        title = { Text(stringResource(R.string.auto_switch_log_title)) },
        text = {
            val current = state
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = when {
                        !appState.proxyRunning -> stringResource(R.string.auto_switch_log_not_running)
                        current == null && loadFailed -> stringResource(R.string.auto_switch_log_unavailable)
                        current == null -> stringResource(R.string.mihomo_proxies_loading)
                        else -> autoSwitchPhaseText(current, nowMillis)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (current != null) {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                    ) {
                        if (current.groups.isNotEmpty()) {
                            item { LogSectionTitle(stringResource(R.string.auto_switch_log_groups)) }
                            items(current.groups, key = { "group:${it.name}" }) { group ->
                                Text(
                                    text = autoSwitchGroupText(group),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                        item { LogSectionTitle(stringResource(R.string.auto_switch_log_events)) }
                        if (current.events.isEmpty()) {
                            item { Text(stringResource(R.string.auto_switch_log_empty)) }
                        }
                        items(current.events) { event ->
                            SelectionContainer {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = "${timeFormat.format(Date(event.time))} · ${event.group}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(autoSwitchEventText(event), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AsteriskActionButton(
                text = stringResource(R.string.common_close),
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
    )
}

@Composable
private fun LogSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun autoSwitchPhaseText(state: MihomoAutoSwitchState, nowMillis: Long): String = when (state.phase) {
    MihomoAutoSwitchPhaseChecking -> state.progress?.let { autoSwitchProgressText(it) }
        ?: stringResource(R.string.auto_switch_progress_preparing)

    MihomoAutoSwitchPhaseWaiting -> {
        val seconds = (((state.nextCheckAt ?: nowMillis) - nowMillis).coerceAtLeast(0L) + 999L) / 1000L
        pluralStringResource(R.plurals.auto_switch_log_countdown, seconds.toInt(), seconds)
    }

    else -> stringResource(R.string.auto_switch_log_stopped)
}

@Composable
private fun autoSwitchProgressText(progress: MihomoAutoSwitchProgress): String = when (progress.stage) {
    "preferred" -> stringResource(
        R.string.auto_switch_progress_preferred, progress.group, progress.completed, progress.total,
    )

    "all" -> stringResource(R.string.auto_switch_progress_all, progress.group, progress.completed, progress.total)
    else -> stringResource(R.string.auto_switch_progress_current, progress.group)
}

@Composable
private fun autoSwitchGroupText(group: MihomoAutoSwitchGroupStatus): String = when (group.kind) {
    "healthy" -> stringResource(R.string.auto_switch_group_healthy, group.name, group.node, group.delay ?: 0)
    "failed" -> stringResource(R.string.auto_switch_group_failed, group.name, group.node, group.failures ?: 0)
    "skipped" -> stringResource(R.string.auto_switch_group_skipped, group.name, group.node)
    else -> stringResource(R.string.auto_switch_group_unavailable, group.name)
}

@Composable
private fun autoSwitchEventText(event: MihomoAutoSwitchEvent): String = when (event.kind) {
    "failed" -> stringResource(
        R.string.auto_switch_event_failed, event.node, event.failures ?: 0, event.threshold ?: 0,
    )

    "switched" -> stringResource(
        if (event.preferred) R.string.auto_switch_event_switched_preferred else R.string.auto_switch_event_switched,
        event.node, event.target, event.delay ?: 0, event.available ?: 0, event.tested ?: 0,
    )

    "no-replacement" -> stringResource(R.string.auto_switch_event_no_replacement, event.node, event.tested ?: 0)
    else -> stringResource(R.string.auto_switch_event_selection_changed, event.node)
}

private const val AutoSwitchLogRefreshMillis = 1_000L
