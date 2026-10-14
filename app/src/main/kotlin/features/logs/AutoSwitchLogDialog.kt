// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.logs

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.R
import engine.proxy.AutoSwitchCheckKind
import engine.proxy.AutoSwitchCheckProgress
import engine.proxy.AutoSwitchCheckResult
import engine.proxy.AutoSwitchCheckStage
import engine.proxy.AutoSwitchProbePurpose
import features.settings.AutoSwitchRegionLabels
import kotlinx.coroutines.delay
import ui.components.AsteriskActionButton
import java.text.DateFormat
import java.util.Date
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun AutoSwitchLogDialog(
    repository: AutoSwitchLogRepository,
    onDismissRequest: () -> Unit,
) {
    val entries by repository.entries.collectAsState()
    val visibleEntries = remember(entries) {
        // Keep older persisted records readable while hiding the former per-candidate/per-verification rows.
        entries.filter { entry ->
            val result = entry.checkResult
            result == null || result.kind !in setOf(
                AutoSwitchCheckKind.Healthy,
                AutoSwitchCheckKind.Failed
            ) ||
                    result.purpose == AutoSwitchProbePurpose.Current
        }
    }
    val monitorState by repository.monitorState.collectAsState()
    val remainingSeconds by produceState(
        initialValue = monitorState.remainingSeconds(SystemClock.elapsedRealtime()),
        key1 = monitorState.phase,
        key2 = monitorState.nextCheckAtElapsedMillis,
    ) {
        while (monitorState.phase == AutoSwitchMonitorPhase.Waiting) {
            value = monitorState.remainingSeconds(SystemClock.elapsedRealtime())
            delay(1000L)
        }
    }
    val statusText = when (monitorState.phase) {
        AutoSwitchMonitorPhase.Stopped -> stringResource(R.string.auto_switch_log_stopped)
        AutoSwitchMonitorPhase.Checking -> autoSwitchProgressText(monitorState.progress)
        AutoSwitchMonitorPhase.Waiting -> pluralStringResource(
            R.plurals.auto_switch_log_countdown,
            remainingSeconds.toInt(),
            remainingSeconds
        )
    }
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
        title = { Text(stringResource(R.string.auto_switch_log_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // Keep the live countdown above the scrolling history; never append countdown ticks to the log.
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (monitorState.phase == AutoSwitchMonitorPhase.Checking && monitorState.progress.groupName.isNotBlank()) {
                    Text(
                        stringResource(
                            R.string.auto_switch_log_group,
                            monitorState.progress.groupName
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (visibleEntries.isEmpty()) {
                    Text(stringResource(R.string.auto_switch_log_empty))
                } else {
                    LazyColumn(Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)) {
                        itemsIndexed(visibleEntries) { index, entry ->
                            if (index > 0) HorizontalDivider()
                            SelectionContainer {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = timeFormat.format(Date(entry.timestampMillis)),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (entry.groupName.isNotBlank()) {
                                        Text(
                                            text = stringResource(
                                                R.string.auto_switch_log_group,
                                                entry.groupName
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                    if (entry.profileName.isNotBlank()) {
                                        Text(
                                            text = stringResource(
                                                R.string.auto_switch_log_profile,
                                                entry.profileName
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    val result = entry.checkResult
                                    if (result == null) {
                                        Text(
                                            stringResource(
                                                R.string.auto_switch_log_from,
                                                entry.fromNode
                                            )
                                        )
                                        Text(
                                            stringResource(
                                                R.string.auto_switch_log_to,
                                                entry.toNode
                                            )
                                        )
                                    } else {
                                        if (result.kind == AutoSwitchCheckKind.CandidateSummary) {
                                            Text(
                                                stringResource(
                                                    if (result.preferredOnly) R.string.auto_switch_log_preferred_summary
                                                    else R.string.auto_switch_log_all_summary,
                                                )
                                            )
                                            Text(
                                                stringResource(
                                                    R.string.auto_switch_log_node_counts,
                                                    result.totalNodeCount ?: 0,
                                                    result.availableNodeCount ?: 0
                                                )
                                            )
                                            Text(
                                                if (result.nodeName.isNotBlank() && result.delayMillis != null) {
                                                    stringResource(
                                                        R.string.auto_switch_log_fastest,
                                                        result.nodeName,
                                                        result.delayMillis
                                                    )
                                                } else stringResource(R.string.auto_switch_log_fastest_none),
                                            )
                                        } else {
                                            if (result.nodeName.isNotBlank()) {
                                                Text(
                                                    stringResource(
                                                        R.string.auto_switch_log_node,
                                                        stringResource(R.string.auto_switch_log_current),
                                                        result.nodeName
                                                    )
                                                )
                                            }
                                            Text(autoSwitchCheckText(result))
                                            if (result.failureCount != null && result.failureThreshold != null) {
                                                Text(
                                                    stringResource(
                                                        R.string.auto_switch_log_failure_count,
                                                        result.failureCount,
                                                        result.failureThreshold
                                                    )
                                                )
                                            }
                                        }
                                    }
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
private fun autoSwitchProgressText(progress: AutoSwitchCheckProgress): String =
    when (progress.stage) {
        AutoSwitchCheckStage.Preparing -> stringResource(R.string.auto_switch_progress_preparing)
        AutoSwitchCheckStage.CurrentNode -> stringResource(
            R.string.auto_switch_progress_current,
            progress.nodeName
        )

        AutoSwitchCheckStage.PreferredNodes -> {
            val labels = progress.preferredRegions.mapNotNull { code ->
                AutoSwitchRegionLabels.firstOrNull { it.first == code }?.second
            }.map { stringResource(it) } +
                    if (progress.usesCustomKeywords) listOf(stringResource(R.string.auto_switch_progress_custom)) else emptyList()
            val preferences =
                labels.joinToString(stringResource(R.string.auto_switch_progress_region_separator))
                    .ifBlank { stringResource(R.string.auto_switch_progress_preferred) }
            stringResource(
                R.string.auto_switch_progress_regions,
                preferences,
                progress.completedNodes,
                progress.totalNodes
            )
        }

        AutoSwitchCheckStage.AllNodes -> stringResource(
            R.string.auto_switch_progress_all,
            progress.completedNodes,
            progress.totalNodes
        )

        AutoSwitchCheckStage.RecheckingReplacement -> stringResource(
            R.string.auto_switch_progress_recheck_replacement,
            progress.nodeName
        )

        AutoSwitchCheckStage.Switching -> stringResource(
            R.string.auto_switch_progress_switching,
            progress.nodeName
        )
    }

@Composable
private fun autoSwitchCheckText(result: AutoSwitchCheckResult): String = when (result.kind) {
    AutoSwitchCheckKind.Healthy -> if (result.delayMillis != null) {
        stringResource(R.string.auto_switch_log_current_healthy_with_delay, result.delayMillis)
    } else {
        stringResource(R.string.auto_switch_log_current_healthy)
    }

    AutoSwitchCheckKind.Failed -> stringResource(R.string.auto_switch_log_failed)
    AutoSwitchCheckKind.Offline -> stringResource(R.string.auto_switch_log_offline)
    AutoSwitchCheckKind.SkippedSelection -> stringResource(R.string.auto_switch_log_skipped)
    AutoSwitchCheckKind.NoGroups -> stringResource(R.string.auto_switch_log_no_groups)
    AutoSwitchCheckKind.NoReplacement -> stringResource(R.string.auto_switch_log_no_replacement)
    AutoSwitchCheckKind.SelectionChanged -> stringResource(R.string.auto_switch_log_selection_changed)
    AutoSwitchCheckKind.ControlError -> stringResource(
        R.string.auto_switch_log_control_error,
        result.errorCategory
    )

    AutoSwitchCheckKind.Recovered -> stringResource(R.string.auto_switch_log_recovered)
    AutoSwitchCheckKind.ReplacementUnavailable -> stringResource(R.string.auto_switch_log_replacement_unavailable)
    AutoSwitchCheckKind.CandidateSummary -> stringResource(R.string.auto_switch_log_all_summary)
    AutoSwitchCheckKind.InteractiveOperation -> stringResource(R.string.auto_switch_log_interactive)
}
