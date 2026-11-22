// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.R
import coil3.compose.AsyncImage
import engine.proxy.RuntimeRouteRule
import engine.proxy.RuntimeRuleOutbound
import features.monitoring.ConnectionMonitorStatus
import features.monitoring.MonitoringRulesSummary
import java.text.DateFormat
import java.util.Date
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskInfoChip
import ui.components.AsteriskStatusCard
import ui.layout.LocalAdaptiveWindow
import ui.layout.pageListPadding
import ui.layout.pageReadableWidth
import ui.layout.rememberPageGutter
import ui.theme.AsteriskShapeTokens

internal val RulesMonitorPageMaxWidth = 1008.dp
private val RuleCardSpacing = 12.dp

@Composable
internal fun RulesMonitorContent(
    state: MonitoringRulesSummary,
    filteredRules: List<RuntimeRouteRule>,
    searching: Boolean,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val gutter = rememberPageGutter()
    val numberStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    val widestNumber = remember(state.rules) {
        val digits = ((state.rules.maxOfOrNull { it.index } ?: 0) + 1).toString().length
        "8".repeat(digits)
    }
    val density = LocalDensity.current
    val measuredNumber = rememberTextMeasurer().measure(widestNumber, style = numberStyle).size.width
    val numberWidth = with(density) { maxOf(32.dp, measuredNumber.toDp() + 16.dp) }

    BoxWithConstraints(Modifier.pageReadableWidth(RulesMonitorPageMaxWidth).fillMaxSize()) {
        val twoColumns = LocalAdaptiveWindow.current.showTwoColumns &&
            maxWidth - gutter * 2 >= 720.dp && density.fontScale <= 1.3f
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = pageListPadding(contentPadding, bottomExtra = 20.dp, horizontalExtra = gutter),
            verticalArrangement = Arrangement.spacedBy(RuleCardSpacing),
        ) {
            when (state.status) {
                ConnectionMonitorStatus.ServiceStopped -> {
                    item("rules-stopped") { StatusCard(stringResource(R.string.monitor_service_not_enabled)) }
                    return@LazyColumn
                }
                ConnectionMonitorStatus.Loading -> {
                    item("rules-loading") { StatusCard(stringResource(R.string.monitor_loading), loading = true) }
                    return@LazyColumn
                }
                ConnectionMonitorStatus.Error -> if (!state.stale) {
                    item("rules-error") {
                        StatusCard(state.error.ifBlank { stringResource(R.string.monitor_data_unavailable) })
                    }
                    return@LazyColumn
                }
                ConnectionMonitorStatus.Available -> Unit
            }
            item("rules-summary") { RulesMonitorStatus(state) }
            if (state.stale) item("rules-stale") { StatusCard(stringResource(R.string.monitor_data_stale)) }
            when {
                filteredRules.isEmpty() -> item("rules-empty") {
                    StatusCard(stringResource(if (searching) R.string.monitor_rules_no_match else R.string.monitor_rules_empty))
                }
                twoColumns -> items(
                    count = (filteredRules.size + 1) / 2,
                    key = { row -> "rule-${filteredRules[row * 2].index}" },
                ) { row ->
                    val first = row * 2
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(RuleCardSpacing),
                    ) {
                        RuleCard(
                            filteredRules[first],
                            numberWidth,
                            numberStyle,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        val next = filteredRules.getOrNull(first + 1)
                        if (next == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            RuleCard(next, numberWidth, numberStyle, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                else -> items(filteredRules, key = { "rule-${it.index}" }) { rule ->
                    RuleCard(rule, numberWidth, numberStyle, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun RulesMonitorStatus(state: MonitoringRulesSummary) {
    val totalHits = remember(state.rules) {
        state.rules.takeIf { rules -> rules.isNotEmpty() && rules.all { it.hits != null } }
            ?.sumOf { it.hits ?: 0L }
    }
    AsteriskStatusCard {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(RuleCardSpacing),
        ) {
            RulesMetric(
                label = stringResource(R.string.monitor_rules_total),
                value = state.rules.size.toString(),
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 10.dp))
            RulesMetric(
                label = stringResource(R.string.monitor_rules_hits_total),
                value = totalHits?.toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RulesMetric(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (value == null) {
            Text(
                text = stringResource(R.string.monitor_rules_hits_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: RuntimeRouteRule,
    numberWidth: Dp,
    numberStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    AsteriskExpressiveCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(RuleCardSpacing),
            verticalAlignment = Alignment.Top,
        ) {
            AsteriskInfoChip(
                text = (rule.index + 1).toString(),
                modifier = Modifier.width(numberWidth),
                textStyle = numberStyle,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = rule.payload.ifBlank { rule.type },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (rule.payload.isNotBlank() || rule.size != null) {
                        val type = rule.type.takeIf { rule.payload.isNotBlank() }
                        val size = rule.size?.let { count ->
                            pluralStringResource(R.plurals.mihomo_rule_provider_rules_count, count, count)
                        }
                        Text(
                            text = listOfNotNull(type, size).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (rule.outbound.isNotEmpty()) RulePolicyChain(rule.outbound)
                if (rule.hits != null) {
                    Text(
                        text = ruleHitSummary(rule.hits, rule.lastHitAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RulePolicyChain(outbound: List<RuntimeRuleOutbound>) {
    Surface(
        shape = AsteriskShapeTokens.SmallContainer,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            outbound.forEachIndexed { index, link ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (index > 0) {
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (link.icon.isNotBlank()) {
                        AsyncImage(
                            model = link.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).clip(AsteriskShapeTokens.SmallContainer),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text(
                        text = link.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
