// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package features.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.CustomResourceFileState
import app.CustomResourceFileStatus
import app.R
import app.ResourceFileStatus
import ui.components.AsteriskActionButton
import ui.components.AsteriskChipTone
import ui.components.AsteriskExpansionIndicator
import ui.components.AsteriskInfoChip
import ui.components.AsteriskModalBottomSheet
import ui.components.AsteriskStatusCard
import ui.components.AsteriskTonalButton
import ui.text.formatTemplate
import utils.ReadableByteUnit
import utils.toReadableBytes
import utils.toReadableDateTimeOrDash
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun settingsResourceFileSourceOptions() = listOf(
    stringResource(R.string.settings_resource_files_source_metacubex_github),
    stringResource(R.string.settings_resource_files_source_custom),
)

@Composable
internal fun ResourceOverviewCard(
    overview: ResourceOverviewState,
    sourceOptions: List<String>,
    selectedSource: Int,
    lastUpdatedAtMillis: Long,
    updating: Boolean,
    onSourceChange: (Int) -> Unit,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    val safeSource = selectedSource.coerceIn(sourceOptions.indices)
    val sourceText = stringResource(R.string.settings_resource_files_source_value, sourceOptions[safeSource])
    val lastCheckText = stringResource(R.string.settings_resource_files_last_check)
        .formatTemplate("time" to lastUpdatedAtMillis.toReadableDateTimeOrDash())
    AsteriskStatusCard(
        modifier = modifier,
        status = "$sourceText\n$lastCheckText",
        controls = {
            if (updating) {
                AsteriskTonalButton(
                    text = stringResource(R.string.common_cancel),
                    icon = Icons.Rounded.Close,
                    onClick = onCancel,
                )
            } else {
                AsteriskTonalButton(
                    text = stringResource(R.string.settings_resource_files_update),
                    icon = Icons.Rounded.Refresh,
                    onClick = onUpdate,
                )
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.settings_resource_files_overview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = pluralStringResource(
                R.plurals.settings_resource_files_overview_ready,
                overview.readyCount,
                overview.readyCount,
                overview.totalCount,
                overview.totalSizeBytes.toReadableBytes(maxUnit = ReadableByteUnit.GiB),
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (updating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Box(modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = { sourceMenuExpanded = true }, enabled = !updating) {
                Text(stringResource(R.string.settings_resource_files_source))
                Spacer(Modifier.width(4.dp))
                AsteriskExpansionIndicator(expanded = sourceMenuExpanded)
            }
            DropdownMenu(
                expanded = sourceMenuExpanded,
                onDismissRequest = { sourceMenuExpanded = false },
            ) {
                sourceOptions.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            sourceMenuExpanded = false
                            onSourceChange(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun CustomResourceSourceEditorSheet(
    show: Boolean,
    geoIpUrlState: TextFieldState,
    geoSiteUrlState: TextFieldState,
    mmdbUrlState: TextFieldState,
    asnUrlState: TextFieldState,
    directCidrIpv4UrlState: TextFieldState,
    directCidrIpv6UrlState: TextFieldState,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
) {
    AsteriskModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.settings_resource_files_source_custom_title),
        startAction = {
            AsteriskActionButton(
                text = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
        endAction = {
            AsteriskActionButton(
                text = stringResource(R.string.common_save),
                icon = Icons.Rounded.Save,
                onClick = onSave,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ResourceUrlField(geoIpUrlState, ResourceFileGeoIpName)
                ResourceUrlField(geoSiteUrlState, ResourceFileGeoSiteName)
                ResourceUrlField(mmdbUrlState, ResourceFileMmdbName)
                ResourceUrlField(asnUrlState, ResourceFileAsnName)
                ResourceUrlField(directCidrIpv4UrlState, ResourceFileDirectCidrIpv4Name)
                ResourceUrlField(directCidrIpv6UrlState, ResourceFileDirectCidrIpv6Name)
            }
        }
    }
}

@Composable
private fun ResourceUrlField(state: TextFieldState, label: String) {
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ResourceFileCard(
    fileName: String,
    status: ResourceFileStatus,
    updating: Boolean,
    onReplace: () -> Unit,
    modifier: Modifier = Modifier,
    onUpdate: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    description: String? = null,
) {
    val actions = buildList {
        onUpdate?.let { add(ResourceMenuEntry(ResourceDisplayAction.Update, it)) }
        add(ResourceMenuEntry(ResourceDisplayAction.Replace, onReplace))
        onRestore?.let { add(ResourceMenuEntry(ResourceDisplayAction.Restore, it)) }
    }
    ResourceFileCardSurface(
        fileName = fileName,
        status = status,
        modifier = modifier,
        description = description,
        updating = updating,
        actions = actions,
    )
}

@Composable
internal fun CustomResourceFileCard(
    fileStatus: CustomResourceFileStatus,
    updating: Boolean,
    onUpdate: (CustomResourceFileState) -> Unit,
    onReplace: (CustomResourceFileState) -> Unit,
    onEdit: (CustomResourceFileState) -> Unit,
    onDelete: (CustomResourceFileState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val file = fileStatus.file
    val callbacks = mapOf(
        ResourceDisplayAction.Update to { onUpdate(file) },
        ResourceDisplayAction.Replace to { onReplace(file) },
        ResourceDisplayAction.Edit to { onEdit(file) },
        ResourceDisplayAction.Delete to { onDelete(file) },
    )
    ResourceFileCardSurface(
        fileName = file.name,
        status = fileStatus.status,
        description = file.url.ifBlank { stringResource(R.string.settings_resource_files_local_only) },
        modifier = modifier,
        updating = updating,
        actions = customResourceDisplayActions(file).mapNotNull { action ->
            callbacks[action]?.let { callback -> ResourceMenuEntry(action, callback) }
        },
    )
}

private data class ResourceMenuEntry(
    val action: ResourceDisplayAction,
    val onClick: () -> Unit,
)

@Composable
private fun ResourceFileCardSurface(
    fileName: String,
    status: ResourceFileStatus,
    updating: Boolean,
    actions: List<ResourceMenuEntry>,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 84.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Icon(
                    imageVector = resourceVisualKind(fileName).icon(),
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (status.exists) {
                    Text(
                        text = status.sizeText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = status.updatedAtText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_resource_files_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ResourceFileStatusChip(
                text = stringResource(
                    if (status.exists) {
                        R.string.settings_resource_files_ready
                    } else {
                        R.string.settings_resource_files_missing
                    },
                ),
                ready = status.exists,
            )
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = !updating) {
                    if (updating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.mihomo_configuration_actions),
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    actions.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(entry.action.label()) },
                            leadingIcon = { Icon(entry.action.icon(), contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                entry.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun ResourceVisualKind.icon(): ImageVector {
    return when (this) {
        ResourceVisualKind.Core -> Icons.Rounded.Memory
        ResourceVisualKind.GeoIp -> Icons.Rounded.Public
        ResourceVisualKind.GeoSite -> Icons.Rounded.TravelExplore
        ResourceVisualKind.Database -> Icons.Rounded.Storage
        ResourceVisualKind.Asn -> Icons.Rounded.Hub
        ResourceVisualKind.Cidr -> Icons.Rounded.Route
        ResourceVisualKind.Custom -> Icons.Rounded.DataObject
    }
}

private fun ResourceDisplayAction.icon(): ImageVector {
    return when (this) {
        ResourceDisplayAction.Update -> Icons.Rounded.Refresh
        ResourceDisplayAction.Replace -> Icons.Rounded.FileUpload
        ResourceDisplayAction.Restore -> Icons.Rounded.History
        ResourceDisplayAction.Edit -> Icons.Rounded.Edit
        ResourceDisplayAction.Delete -> Icons.Rounded.Delete
    }
}

@Composable
private fun ResourceDisplayAction.label(): String {
    return stringResource(
        when (this) {
            ResourceDisplayAction.Update -> R.string.common_update
            ResourceDisplayAction.Replace -> R.string.common_replace
            ResourceDisplayAction.Restore -> R.string.common_restore
            ResourceDisplayAction.Edit -> R.string.common_edit
            ResourceDisplayAction.Delete -> R.string.common_delete
        },
    )
}

@Composable
private fun ResourceFileStatusChip(text: String, ready: Boolean) {
    AsteriskInfoChip(
        text = text,
        tone = if (ready) AsteriskChipTone.Primary else AsteriskChipTone.Error,
    )
}

@Composable
internal fun CustomResourceFileEditorSheet(
    show: Boolean,
    nameState: TextFieldState,
    urlState: TextFieldState,
    reservedNames: Set<String>,
    onDismissRequest: () -> Unit,
    onSave: (name: String, url: String) -> Boolean,
) {
    var error by remember { mutableStateOf<CustomResourceDraftError?>(null) }
    AsteriskModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.settings_resource_files_custom_file),
        startAction = {
            AsteriskActionButton(
                text = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Close,
                onClick = onDismissRequest,
            )
        },
        endAction = {
            AsteriskActionButton(
                text = stringResource(R.string.common_save),
                icon = Icons.Rounded.Save,
                onClick = {
                    val validation = validateCustomResourceDraft(
                        name = nameState.text.toString(),
                        url = urlState.text.toString(),
                        reservedNames = reservedNames,
                    )
                    error = validation.error
                    if (validation.valid && onSave(validation.name, validation.url)) {
                        onDismissRequest()
                    }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
        ) {
            OutlinedTextField(
                state = nameState,
                label = { Text(stringResource(R.string.settings_resource_files_custom_name)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = error == CustomResourceDraftError.InvalidName ||
                    error == CustomResourceDraftError.DuplicateName,
                supportingText = error.nameErrorText(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state = urlState,
                label = { Text(stringResource(R.string.settings_resource_files_custom_url_optional)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = error == CustomResourceDraftError.InvalidUrl,
                supportingText = if (error == CustomResourceDraftError.InvalidUrl) {
                    { Text(stringResource(R.string.settings_resource_files_custom_url_invalid)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CustomResourceDraftError?.nameErrorText(): (@Composable () -> Unit)? {
    val text = when (this) {
        CustomResourceDraftError.InvalidName -> stringResource(R.string.settings_resource_files_custom_name_invalid)
        CustomResourceDraftError.DuplicateName -> stringResource(R.string.settings_resource_files_custom_name_duplicate)
        else -> return null
    }
    return { Text(text) }
}

@Composable
private fun ResourceFileStatus.updatedAtText(): String {
    return stringResource(R.string.settings_resource_files_updated_at)
        .formatTemplate("time" to updatedAtMillis.toReadableDateTimeOrDash())
}

@Composable
private fun ResourceFileStatus.sizeText(): String {
    return stringResource(R.string.settings_resource_files_size)
        .formatTemplate("size" to sizeBytes.toReadableBytes(maxUnit = ReadableByteUnit.MiB))
}
