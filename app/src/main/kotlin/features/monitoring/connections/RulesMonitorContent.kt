// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.connections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.R
import coil3.compose.AsyncImage
import engine.proxy.RuntimeRouteRule
import features.monitoring.ConnectionMonitorStatus
import features.monitoring.MonitoringRulesSummary
import java.text.DateFormat
import java.util.Date
import ui.theme.AsteriskShapeTokens

internal fun LazyListScope.rulesMonitorContent(
    state: MonitoringRulesSummary,
    filteredRules: List<RuntimeRouteRule>,
    searching: Boolean,
) {
    when (state.status) {
        ConnectionMonitorStatus.ServiceStopped -> {
            item("rules-stopped") { StatusCard(stringResource(R.string.monitor_service_not_enabled)) }
            return
        }
        ConnectionMonitorStatus.Loading -> {
            item("rules-loading") { StatusCard(stringResource(R.string.monitor_loading), loading = true) }
            return
        }
        ConnectionMonitorStatus.Error -> if (!state.stale) {
            item("rules-error") {
                StatusCard(state.error.ifBlank { stringResource(R.string.monitor_data_unavailable) })
            }
            return
        }
        ConnectionMonitorStatus.Available -> Unit
    }
    item("rules-summary") { RulesMonitorStatus(state) }
    if (state.stale) item("rules-stale") { StatusCard(stringResource(R.string.monitor_data_stale)) }
    if (filteredRules.isEmpty()) {
        item("rules-empty") {
            StatusCard(stringResource(if (searching) R.string.monitor_rules_no_match else R.string.monitor_rules_empty))
        }
    } else {
        items(filteredRules, key = { "rule-${it.index}" }) { rule -> RuleCard(rule) }
    }
}

@Composable
private fun RulesMonitorStatus(state: MonitoringRulesSummary) {
    val totalHits = remember(state.rules) {
        state.rules.takeIf { rules -> rules.isNotEmpty() && rules.all { it.hits != null } }
            ?.sumOf { it.hits ?: 0L }
    }
    Card(
        modifier = ContentWidthModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(18.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.monitor_rules_total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.rules.size.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .padding(vertical = 10.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 18.dp)) {
                Text(
                    text = stringResource(R.string.monitor_rules_hits_total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = totalHits?.toString() ?: stringResource(R.string.monitor_rules_hits_unavailable),
                    style = if (totalHits == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = if (totalHits == null) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleCard(rule: RuntimeRouteRule) {
    Card(
        modifier = ContentWidthModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (rule.index + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 28.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = buildString {
                            append(rule.type)
                            if (rule.payload.isNotBlank()) append(" · ").append(rule.payload)
                            rule.size?.let { append(" ($it)") }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (rule.hits != null) {
                        Text(
                            text = ruleHitSummary(rule.hits, rule.lastHitAtMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (rule.outbound.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .padding(start = 28.dp)
                        .clip(AsteriskShapeTokens.SmallContainer)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        rule.outbound.forEachIndexed { index, link ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (index > 0) {
                                    Text(
                                        text = "→",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (link.icon.isNotBlank()) {
                                    AsyncImage(
                                        model = link.icon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(AsteriskShapeTokens.SmallContainer),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                Text(
                                    text = link.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ruleHitSummary(hits: Long, lastHitAtMillis: Long?): String {
    if (hits == 0L) return stringResource(R.string.monitor_rules_never_hit)
    val count = stringResource(R.string.monitor_rules_hits, hits)
    val lastHit = if (lastHitAtMillis == null) {
        stringResource(R.string.monitor_rules_unknown_hit)
    } else {
        val formatted = DateFormat.getDateTimeInstance().format(Date(lastHitAtMillis))
        stringResource(R.string.monitor_rules_last_hit, formatted)
    }
    return "$count · $lastHit"
}

internal fun filterRules(rules: List<RuntimeRouteRule>, query: String): List<RuntimeRouteRule> {
    val term = query.trim()
    if (term.isEmpty()) return rules
    return rules.filter { rule ->
        rule.payload.contains(term, ignoreCase = true) ||
            rule.type.contains(term, ignoreCase = true) ||
            rule.policy.contains(term, ignoreCase = true) ||
            rule.outbound.any { it.name.contains(term, ignoreCase = true) }
    }
}
