// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.connections

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.R
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskModalBottomSheet
import features.monitoring.MonitoringValueRow
import ui.icons.AsteriskIcons as Icons
import ui.layout.LocalAdaptiveWindow
import ui.layout.pageListPadding
import ui.layout.pageReadableWidth
import ui.theme.AsteriskMotion

internal val ConnectionMonitorContentMaxWidth = 960.dp
private val ConnectionMonitorPageMaxWidth = 1260.dp

@Composable
internal fun <T : Any> ConnectionMonitorLayout(
    contentPadding: PaddingValues,
    selection: T?,
    title: String,
    onDismiss: () -> Unit,
    selectionKey: (T) -> Any,
    list: @Composable (Modifier) -> Unit,
    details: @Composable (T) -> Unit,
) {
    BoxWithConstraints(Modifier.pageReadableWidth(ConnectionMonitorPageMaxWidth).fillMaxSize()) {
        val split = LocalAdaptiveWindow.current.supportsWideContent &&
            maxWidth >= 760.dp && LocalDensity.current.fontScale <= 1.3f
        val detailsWidth = (maxWidth * 0.43f).coerceAtMost(520.dp)
        val spatial = AsteriskMotion.fastSpatial<IntSize>()
        val effects = AsteriskMotion.fastEffects<Float>()
        val padding = pageListPadding(contentPadding, bottomExtra = 20.dp)
        val endPadding = padding.calculateEndPadding(LocalLayoutDirection.current)
        if (split) {
            BackHandler(enabled = selection != null, onBack = onDismiss)
        } else {
            MonitoringDetailsSheet(selection, selectionKey, title, onDismiss, details)
        }
        Row(Modifier.fillMaxSize()) {
            list(Modifier.weight(1f).fillMaxHeight())
            AnimatedContent(
                targetState = selection.takeIf { split },
                contentKey = { it != null },
                contentAlignment = Alignment.CenterEnd,
                transitionSpec = {
                    ((expandHorizontally(spatial, expandFrom = Alignment.End) + fadeIn(effects)) togetherWith
                        (shrinkHorizontally(spatial, shrinkTowards = Alignment.End) + fadeOut(effects))).using(null)
                },
                label = "monitorDetails",
            ) { item ->
                if (item != null) {
                    val scrollState = key(selectionKey(item)) { rememberScrollState() }
                    Column(
                        modifier = Modifier.width(detailsWidth).fillMaxHeight()
                            .padding(
                                top = padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding(),
                                end = endPadding,
                            )
                            .verticalScroll(scrollState),
                    ) {
                        AsteriskExpressiveCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 8.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.common_close))
                                }
                            }
                            MonitoringDetailContent { details(item) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun <T : Any> MonitoringRecordList(
    state: LazyListState,
    contentPadding: PaddingValues,
    selection: T?,
    title: String,
    onDismiss: () -> Unit,
    selectionKey: (T) -> Any,
    details: @Composable (T) -> Unit,
    content: LazyListScope.() -> Unit,
) {
    ConnectionMonitorLayout(
        contentPadding = contentPadding,
        selection = selection,
        title = title,
        onDismiss = onDismiss,
        selectionKey = selectionKey,
        list = { modifier ->
            val padding = pageListPadding(contentPadding, bottomExtra = 20.dp)
            val layoutDirection = LocalLayoutDirection.current
            val listWidth = ConnectionMonitorContentMaxWidth +
                padding.calculateStartPadding(layoutDirection) + padding.calculateEndPadding(layoutDirection)
            LazyColumn(
                modifier = modifier.pageReadableWidth(listWidth),
                state = state,
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        },
        details = details,
    )
}

@Composable
private fun <T : Any> MonitoringDetailsSheet(
    selection: T?,
    selectionKey: (T) -> Any,
    title: String,
    onDismiss: () -> Unit,
    details: @Composable (T) -> Unit,
) {
    AsteriskModalBottomSheet(
        show = selection != null,
        onDismissRequest = onDismiss,
        foldedDismissGestures = true,
        title = title,
    ) {
        selection?.let { item ->
            val scrollState = key(selectionKey(item)) { rememberScrollState() }
            MonitoringDetailContent(Modifier.verticalScroll(scrollState)) { details(item) }
        }
    }
}

@Composable
private fun MonitoringDetailContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
internal fun MonitoringDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    MonitoringValueRow(
        label = label,
        value = value,
        modifier = modifier,
        valueMaxLines = Int.MAX_VALUE,
        trailing = trailing,
    )
}
