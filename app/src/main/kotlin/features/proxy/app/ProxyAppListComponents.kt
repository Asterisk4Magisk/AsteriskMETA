// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.R
import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import coil3.compose.AsyncImage
import features.proxy.app.model.AppPackageEntry
import features.proxy.app.model.ProxyAppIconRequest
import features.proxy.app.model.ProxyAppListUserSpaceTabUi
import features.proxy.app.model.name
import system.ANDROID_APP_ICON_SIZE_DP
import ui.components.AsteriskCheckbox
import ui.components.AsteriskChipTone
import ui.components.AsteriskFilterChip
import ui.components.AsteriskInfoChip
import ui.components.AsteriskSelectionCard
import ui.text.formatTemplate
import ui.theme.AsteriskMotion
import ui.theme.AsteriskShapeTokens
import ui.icons.AsteriskIcons as Icons

internal enum class ProxyAppListMoreAction {
    ToggleSystemApps,
    ImportClipboard,
    ExportClipboard,
}

@Composable
internal fun ProxyAppListUserSpaceTabs(
    tabs: List<ProxyAppListUserSpaceTabUi>,
    selectedUserId: Int?,
    onSelectedUserIdChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.size <= 1) return

    val hapticFeedback = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        tabs.forEach { tab ->
            AsteriskFilterChip(
                selected = tab.id == selectedUserId,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSelectedUserIdChange(tab.id)
                },
                label = "${tab.label} (${tab.checkedCount})",
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
internal fun ProxyAppListMoreActionsMenu(
    showSystemApps: Boolean,
    onAction: (ProxyAppListMoreAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.proxy_app_list_more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.proxy_app_list_show_system_apps)) },
                onClick = { onAction(ProxyAppListMoreAction.ToggleSystemApps) },
                leadingIcon = { Icon(Icons.Rounded.Apps, contentDescription = null) },
                trailingIcon = { AsteriskCheckbox(checked = showSystemApps, onCheckedChange = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_import_from_clipboard)) },
                onClick = {
                    expanded = false
                    onAction(ProxyAppListMoreAction.ImportClipboard)
                },
                leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_export_to_clipboard)) },
                onClick = {
                    expanded = false
                    onAction(ProxyAppListMoreAction.ExportClipboard)
                },
                leadingIcon = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
            )
        }
    }
}

@Composable
internal fun ProxyAppListModeMenu(
    modes: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.Tune, stringResource(R.string.proxy_app_list_mode))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            modes.forEachIndexed { index, mode ->
                DropdownMenuItem(
                    text = { Text(mode) },
                    leadingIcon = { Icon(proxyAppListModeIcon(index), contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSelectedIndexChange(index)
                    },
                    trailingIcon = { RadioButton(selected = selectedIndex == index, onClick = null) },
                )
            }
        }
    }
}

internal fun proxyAppListModeIcon(mode: Int): ImageVector {
    return when (mode) {
        ProxyAppListModeBlacklist -> Icons.Rounded.Block
        ProxyAppListModeWhitelist -> Icons.Rounded.CheckCircle
        ProxyAppListModeGlobal -> Icons.Rounded.Public
        else -> Icons.Rounded.Tune
    }
}

@Composable
internal fun ProxyAppListItemCard(
    app: AppPackageEntry,
    checked: Boolean,
    enabled: Boolean,
    sharedUid: Boolean,
    iconSizePx: Int,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggle = {
        if (enabled) {
            onCheckedChange(!checked)
        }
    }

    AsteriskSelectionCard(
        selected = checked,
        onClick = toggle,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        enabled = enabled,
        containerColor = if (app.system) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                app = app,
                enabled = enabled,
                iconSizePx = iconSizePx,
            )
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.proxy_app_list_entry_summary)
                            .formatTemplate("package" to app.packageName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                UidChip(
                    uid = app.uid,
                    sharedUid = sharedUid,
                )
                AsteriskCheckbox(
                    checked = checked,
                    onCheckedChange = { onCheckedChange(it) },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
internal fun ProxyAppListEmptyState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.common_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UidChip(
    uid: Int?,
    sharedUid: Boolean,
) {
    AsteriskInfoChip(
        text = if (sharedUid) {
            "SUID:${uid?.toString() ?: "..."}"
        } else {
            "UID:${uid?.toString() ?: "..."}"
        },
        tone = if (sharedUid) AsteriskChipTone.Tertiary else AsteriskChipTone.Secondary,
    )
}

@Composable
private fun AppIcon(
    app: AppPackageEntry,
    enabled: Boolean,
    iconSizePx: Int,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (enabled) {
            if (app.system) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = AsteriskMotion.effects(),
        label = "per-app-icon-background",
    )
    val appIconAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.42f,
        animationSpec = AsteriskMotion.effects(),
        label = "per-app-icon-alpha",
    )
    val appIconColorFilter = if (enabled) {
        null
    } else {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }

    Box(
        modifier = Modifier
            .size(ANDROID_APP_ICON_SIZE_DP.dp)
            .clip(AsteriskShapeTokens.SmallContainer)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ProxyAppIconRequest(
                packageName = app.packageName,
                sizePx = iconSizePx,
            ),
            contentDescription = app.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = appIconAlpha
                },
            contentScale = ContentScale.Fit,
            colorFilter = appIconColorFilter,
        )
    }
}
