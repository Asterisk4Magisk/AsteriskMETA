// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.R
import ui.components.AsteriskExpansionIndicator
import ui.theme.AsteriskMotion
import utils.ReadableByteUnit
import utils.toReadableBytes
import utils.toReadableDateOrDash

@Composable
internal fun MihomoProviderUsageSection(
    state: MihomoProviderUsageLoadState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var retainedVisibleState by remember {
        mutableStateOf<MihomoProviderUsageLoadState?>(null)
    }
    val visibleState = state.takeUnless { it == MihomoProviderUsageLoadState.Hidden }
    val renderedState = visibleState ?: retainedVisibleState
    LaunchedEffect(visibleState) {
        if (visibleState != null) retainedVisibleState = visibleState
    }
    val effectsMotion = AsteriskMotion.effects<Float>()
    val sizeMotion = AsteriskMotion.contentSpatial<IntSize>()

    AnimatedVisibility(
        visible = visibleState != null,
        enter = AsteriskMotion.contentEnter(),
        exit = AsteriskMotion.contentExit(),
    ) {
        if (renderedState != null) {
            MihomoProviderUsageVisibleContent(
                state = renderedState,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                onRetry = onRetry,
                onOpenDetails = onOpenDetails,
                effectsMotion = effectsMotion,
                sizeMotion = sizeMotion,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MihomoProviderUsageVisibleContent(
    state: MihomoProviderUsageLoadState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onOpenDetails: () -> Unit,
    effectsMotion: FiniteAnimationSpec<Float>,
    sizeMotion: FiniteAnimationSpec<IntSize>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, end = 8.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AnimatedContent(
            targetState = state,
            transitionSpec = AsteriskMotion.fadeThrough(
                effectsSpec = effectsMotion,
                sizeSpec = sizeMotion,
            ),
            contentKey = { target -> target::class },
            contentAlignment = Alignment.TopStart,
            label = "provider-usage-state",
        ) { targetState ->
            when (targetState) {
                MihomoProviderUsageLoadState.Hidden -> Unit
                MihomoProviderUsageLoadState.Loading -> MihomoProviderUsageLoading()
                MihomoProviderUsageLoadState.RequiresProxyRunning -> MihomoProviderUsageMessage(
                    text = stringResource(R.string.mihomo_configuration_provider_usage_requires_proxy),
                )
                MihomoProviderUsageLoadState.Failed -> MihomoProviderUsageMessage(
                    text = stringResource(R.string.mihomo_configuration_provider_usage_failed),
                    supportingText = stringResource(R.string.mihomo_configuration_provider_usage_retry),
                    onClick = onRetry,
                )
                is MihomoProviderUsageLoadState.Ready -> MihomoProviderUsageReady(
                    summary = targetState.summary,
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                    onOpenDetails = onOpenDetails,
                )
            }
        }
    }
}

@Composable
private fun MihomoProviderUsageLoading() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.mihomo_configuration_provider_usage_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MihomoProviderUsageMessage(
    text: String,
    supportingText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Column(
        modifier = clickModifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MihomoProviderUsageReady(
    summary: MihomoProviderUsageSummary,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenDetails: () -> Unit,
) {
    val expansionState = stringResource(
        if (expanded) {
            R.string.mihomo_configuration_provider_usage_expanded
        } else {
            R.string.mihomo_configuration_provider_usage_collapsed
        },
    )
    val meteredSummary = if (summary.totalBytes > 0L) {
        stringResource(
            R.string.mihomo_configuration_provider_usage_total,
            summary.usedBytes.toReadableBytes(maxUnit = ReadableByteUnit.GiB),
            summary.totalBytes.toReadableBytes(maxUnit = ReadableByteUnit.GiB),
        )
    } else {
        null
    }
    val statusSummary = buildList {
        if (summary.unlimitedCount > 0) {
            add(
                pluralStringResource(
                    R.plurals.mihomo_configuration_provider_usage_unlimited_count,
                    summary.unlimitedCount,
                    summary.unlimitedCount,
                ),
            )
        }
        if (summary.missingCount > 0) {
            add(
                pluralStringResource(
                    R.plurals.mihomo_configuration_provider_usage_missing_count,
                    summary.missingCount,
                    summary.missingCount,
                ),
            )
        }
        if (summary.unavailableCount > 0) {
            add(
                pluralStringResource(
                    R.plurals.mihomo_configuration_provider_usage_unavailable_count,
                    summary.unavailableCount,
                    summary.unavailableCount,
                ),
            )
        }
    }.joinToString(" · ")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onExpandedChange(!expanded) }
                .semantics { stateDescription = expansionState }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mihomo_configuration_provider_usage_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = listOfNotNull(
                        pluralStringResource(
                            R.plurals.mihomo_configuration_provider_usage_count,
                            summary.providerCount,
                            summary.providerCount,
                        ),
                        meteredSummary,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (statusSummary.isNotBlank()) {
                    Text(
                        text = statusSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (summary.totalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = { summary.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            AsteriskExpansionIndicator(
                expanded = expanded,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = AsteriskMotion.contentEnter(),
            exit = AsteriskMotion.contentExit(),
        ) {
            Column {
                summary.items.forEach { item ->
                    MihomoProviderUsageItemRow(item)
                }
                TextButton(
                    onClick = onOpenDetails,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.mihomo_configuration_provider_usage_open_details))
                }
            }
        }
    }
}

@Composable
private fun MihomoProviderUsageItemRow(
    item: MihomoProviderUsageItem,
) {
    val status = when (item.kind) {
        MihomoProviderUsageKind.Metered -> listOf(
            "${item.usedBytes.toReadableBytes(maxUnit = ReadableByteUnit.GiB)} / " +
                item.totalBytes.toReadableBytes(maxUnit = ReadableByteUnit.GiB),
            stringResource(
                R.string.mihomo_configuration_provider_usage_remaining,
                item.remainingPercent,
            ),
        ).joinToString(" · ")
        MihomoProviderUsageKind.Unlimited -> stringResource(R.string.mihomo_provider_traffic_unlimited)
        MihomoProviderUsageKind.Missing -> {
            stringResource(R.string.mihomo_configuration_provider_usage_missing)
        }
        MihomoProviderUsageKind.Unavailable -> {
            stringResource(R.string.mihomo_configuration_provider_usage_unavailable)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = if (item.kind == MihomoProviderUsageKind.Unavailable) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (item.kind == MihomoProviderUsageKind.Metered) {
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )
        }
        if (
            item.kind == MihomoProviderUsageKind.Metered ||
            item.kind == MihomoProviderUsageKind.Unlimited
        ) {
            Text(
                text = if (item.expireAtSeconds > 0L) {
                    stringResource(
                        R.string.mihomo_configuration_provider_usage_expires,
                        item.expireAtSeconds.toEpochMillis().toReadableDateOrDash(),
                    )
                } else {
                    stringResource(R.string.mihomo_configuration_provider_usage_no_expiry)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

private fun Long.toEpochMillis(): Long {
    return coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
}
