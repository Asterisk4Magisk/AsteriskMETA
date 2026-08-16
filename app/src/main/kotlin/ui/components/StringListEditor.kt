// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.R
import ui.theme.AsteriskShapeTokens
import ui.theme.AsteriskMotion

@Composable
internal fun StringListEditor(
    editorKey: Any?,
    title: String,
    values: List<String>,
    onValuesChange: (List<String>) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    validateInput: (String) -> String? = { null },
    normalizeInput: (String) -> String = String::trim,
    onPendingChange: ((Boolean) -> Unit)? = null,
) {
    var input by remember(editorKey, title) { mutableStateOf("") }
    var editingIndex by remember(editorKey, title) { mutableIntStateOf(-1) }
    var editInput by remember(editorKey, title) { mutableStateOf("") }
    var showBulkEditor by remember(editorKey, title) { mutableStateOf(false) }
    var bulkInput by remember(editorKey, title) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val sanitizedValues = normalizeStringListValues(values, normalizeInput)
    val normalizedInput = normalizeInput(input)
    val inputError = normalizedInput.takeIf(String::isNotEmpty)?.let(validateInput)
    val canAdd = normalizedInput.isNotEmpty() && inputError == null
    val hasPendingInput = hasPendingStringListEdit(input, editingIndex, normalizeInput)
    val currentOnPendingChange by rememberUpdatedState(onPendingChange)

    LaunchedEffect(editorKey, title) {
        input = ""
        editingIndex = -1
        editInput = ""
        showBulkEditor = false
    }

    LaunchedEffect(hasPendingInput) {
        currentOnPendingChange?.invoke(hasPendingInput)
    }

    Card(
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AsteriskInfoChip(text = sanitizedValues.size.toString())
                IconButton(
                    onClick = {
                        bulkInput = sanitizedValues.joinToString("\n")
                        showBulkEditor = true
                    },
                ) {
                    Icon(Icons.Rounded.EditNote, stringResource(R.string.common_edit_all))
                }
            }
            description?.let { StringListStatusText(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StringListEditableField(
                    value = input,
                    onValueChange = { input = it },
                    isError = inputError != null,
                    supportingText = inputError,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    enabled = canAdd,
                    onClick = {
                        val result = addStringListValue(
                            sanitizedValues, input, validateInput, normalizeInput,
                        )
                        if (result.error == null) {
                            onValuesChange(normalizeStringListValues(result.values, normalizeInput))
                            input = ""
                        }
                    },
                ) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.common_add))
                }
            }
            AnimatedVisibility(
                visible = hasPendingInput && onPendingChange != null,
                enter = AsteriskMotion.contentEnter(),
                exit = AsteriskMotion.contentExit(),
                label = "string-list-pending-value",
            ) {
                StringListStatusText(stringResource(R.string.string_list_pending_value))
            }
            if (sanitizedValues.isEmpty()) StringListStatusText(emptyText)
            sanitizedValues.forEachIndexed { index, value ->
                val editing = editingIndex == index
                val contentSizeMotion = AsteriskMotion.contentSpatial<androidx.compose.ui.unit.IntSize>()
                val actionMotion = AsteriskMotion.fastSpatial<Float>()
                val editError = if (editing) {
                    normalizeInput(editInput).takeIf(String::isNotEmpty)?.let(validateInput)
                        ?: if (normalizeInput(editInput).isEmpty()) {
                            stringResource(R.string.string_list_item_empty)
                        } else {
                            null
                        }
                } else {
                    null
                }
                key(stringListItemKey(index)) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(editing) {
                        if (editing) focusRequester.requestFocus()
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .animateContentSize(animationSpec = contentSizeMotion),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = AsteriskShapeTokens.InnerContainer,
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                StringListItemField(
                                    editing = editing,
                                    value = value,
                                    editValue = editInput,
                                    onEditValueChange = { editInput = it },
                                    isError = editError != null,
                                    focusRequester = focusRequester,
                                )
                                AnimatedContent(
                                    targetState = editing,
                                    modifier = Modifier.width(96.dp),
                                    transitionSpec = AsteriskMotion.scaleSwap(actionMotion),
                                    contentAlignment = Alignment.CenterEnd,
                                    label = "string-list-actions",
                                ) { isEditing ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        if (isEditing) {
                                            StringListAction(
                                                icon = Icons.Rounded.Check,
                                                description = stringResource(R.string.common_save),
                                                enabled = editError == null,
                                            ) {
                                                val result = editStringListValue(
                                                    sanitizedValues,
                                                    index,
                                                    editInput,
                                                    validateInput,
                                                    normalizeInput,
                                                )
                                                if (result.error == null) {
                                                    onValuesChange(
                                                        normalizeStringListValues(result.values, normalizeInput),
                                                    )
                                                    focusManager.clearFocus()
                                                    editingIndex = -1
                                                }
                                            }
                                            StringListAction(
                                                Icons.Rounded.Close,
                                                stringResource(R.string.common_cancel),
                                            ) {
                                                focusManager.clearFocus()
                                                editingIndex = -1
                                            }
                                        } else {
                                            StringListAction(
                                                Icons.Rounded.Edit,
                                                stringResource(R.string.common_edit),
                                            ) {
                                                editInput = value
                                                editingIndex = index
                                            }
                                            StringListAction(
                                                Icons.Rounded.Delete,
                                                stringResource(R.string.common_delete),
                                            ) {
                                                onValuesChange(
                                                    sanitizedValues.filterIndexed { valueIndex, _ ->
                                                        valueIndex != index
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            editError?.let { StringListStatusText(it, error = true) }
                        }
                    }
                }
            }
        }
    }

    StringListBulkEditorSheet(
        show = showBulkEditor,
        title = title,
        value = bulkInput,
        onValueChange = { bulkInput = it },
        validateInput = validateInput,
        normalizeInput = normalizeInput,
        onDismissRequest = { showBulkEditor = false },
        onSave = { nextValues ->
            editingIndex = -1
            onValuesChange(normalizeStringListValues(nextValues, normalizeInput))
            showBulkEditor = false
        },
    )
}

@Composable
private fun RowScope.StringListItemField(
    editing: Boolean,
    value: String,
    editValue: String,
    onEditValueChange: (String) -> Unit,
    isError: Boolean,
    focusRequester: FocusRequester,
) {
    if (editing) {
        val interactionSource = remember { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        val borderColor = when {
            isError -> MaterialTheme.colorScheme.error
            focused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        }
        val borderWidth = if (focused) 2.dp else 1.dp
        BasicTextField(
            value = editValue,
            onValueChange = onEditValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .expandHorizontallyBy(StringListItemEditorBorderExtension)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = AsteriskShapeTokens.InnerContainer,
                            )
                            .border(
                                width = borderWidth,
                                color = borderColor,
                                shape = AsteriskShapeTokens.InnerContainer,
                            ),
                    )
                    innerTextField()
                }
            },
        )
    } else {
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun Modifier.expandHorizontallyBy(extra: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val extraPx = extra.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extraPx * 2,
            maxWidth = constraints.maxWidth + extraPx * 2,
        ),
    )
    layout(width = constraints.maxWidth, height = placeable.height) {
        placeable.placeRelative(x = -extraPx, y = 0)
    }
}

private val StringListItemEditorBorderExtension = 8.dp

@Composable
private fun RowScope.StringListEditableField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    supportingText: String? = null,
) {
    Box(modifier = Modifier.weight(1f)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = isError,
            supportingText = supportingText?.let { message -> ({ Text(message) }) },
            shape = AsteriskShapeTokens.InnerContainer,
        )
    }
}

@Composable
internal fun StringListStatusText(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StringListAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, description)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StringListBulkEditorSheet(
    show: Boolean,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    validateInput: (String) -> String?,
    normalizeInput: (String) -> String,
    onDismissRequest: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val result = parseStringListBatch(value, validateInput, normalizeInput)
    val error = result.error?.let { stringResource(R.string.string_list_line_error, result.errorLine ?: 1, it) }
    AsteriskModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
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
                enabled = error == null,
                onClick = { result.values?.let(onSave) },
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).padding(top = 12.dp),
                minLines = 8,
                isError = error != null,
                supportingText = error?.let { message -> ({ Text(message) }) },
                shape = AsteriskShapeTokens.InnerContainer,
            )
        }
    }
}
