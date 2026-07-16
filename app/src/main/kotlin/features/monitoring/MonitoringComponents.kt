// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalNavigator
import app.R
import ui.components.AsteriskStatusCard
import ui.components.AsteriskValueRow
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun MonitoringScaffold(
    title: String,
    outerPadding: PaddingValues,
    actions: @Composable () -> Unit = {},
    toolbar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val navigator = LocalNavigator.current
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                    actions = { actions() },
                )
                toolbar()
            }
        },
    ) { innerPadding ->
        content(
            PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + outerPadding.calculateBottomPadding(),
            ),
        )
    }
}

@Composable
internal fun MonitoringSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    headerContent: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                headerContent()
            }
            Box(modifier = Modifier.padding(top = 14.dp)) { content() }
        }
    }
}

@Composable
internal fun MonitoringStatusHeader(
    title: String,
    value: String,
    summary: String,
    modifier: Modifier = Modifier,
    metrics: @Composable RowScope.() -> Unit = {},
) {
    AsteriskStatusCard(
        modifier = modifier,
        status = summary,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            metrics()
        }
    }
}

@Composable
internal fun MonitoringValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    trailing: @Composable (() -> Unit)? = null,
) {
    AsteriskValueRow(
        title = label,
        value = value,
        modifier = modifier,
        titleMinWidth = 112.dp,
        valueMaxLines = 3,
        verticalAlignment = verticalAlignment,
        trailing = trailing,
    )
}
