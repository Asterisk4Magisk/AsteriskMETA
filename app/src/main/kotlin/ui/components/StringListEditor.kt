// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.R
import ui.theme.AsteriskShapeTokens
import ui.theme.AsteriskMotion
import utils.toTrimmedNonEmptyList

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
) {
    var input by remember(editorKey, title) { mutableStateOf("") }
    var editingIndex by remember(editorKey, title) { mutableIntStateOf(-1) }
    var editInput by remember(editorKey, title) { mutableStateOf("") }
    var showBulkEditor by remember(editorKey, title) { mutableStateOf(false) }
    var bulkInput by remember(editorKey, title) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val sanitizedValues = values.toTrimmedNonEmptyList()
    val inputError = input.trim().takeIf(String::isNotEmpty)?.let(validateInput)
    val canAdd = input.trim().isNotEmpty() && inputError == null

    LaunchedEffect(editorKey, title) {
        input = ""
        editingIndex = -1
        editInput = ""
        showBulkEditor = false
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
                        val result = addStringListValue(sanitizedValues, input, validateInput)
                        if (result.error == null) {
                            onValuesChange(result.values.toTrimmedNonEmptyList())
                            input = ""
                        }
                    },
                ) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.common_add))
                }
            }
            if (sanitizedValues.isEmpty()) StringListStatusText(emptyText)
            sanitizedValues.forEachIndexed { index, value ->
                val editing = editingIndex == index
                val contentSizeMotion = AsteriskMotion.spatial<androidx.compose.ui.unit.IntSize>()
                val actionMotion = AsteriskMotion.fastSpatial<Float>()
                val editError = if (editing) {
                    editInput.trim().takeIf(String::isNotEmpty)?.let(validateInput)
                        ?: if (editInput.trim().isEmpty()) stringResource(R.string.string_list_item_empty) else null
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
                                    transitionSpec = {
                                        scaleIn(
                                            initialScale = 0.92f,
                                            animationSpec = actionMotion,
                                        ).togetherWith(
                                            scaleOut(
                                                targetScale = 0.92f,
                                                animationSpec = actionMotion,
                                            ),
                                        )
                                    },
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
                                                )
                                                if (result.error == null) {
                                                    onValuesChange(result.values.toTrimmedNonEmptyList())
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
        onDismissRequest = { showBulkEditor = false },
        onSave = { nextValues ->
            editingIndex = -1
            onValuesChange(nextValues.toTrimmedNonEmptyList())
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
    val activeContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeBorderColor = MaterialTheme.colorScheme.outlineVariant
    val containerColor by animateColorAsState(
        targetValue = if (editing) {
            activeContainerColor
        } else {
            activeContainerColor.copy(alpha = 0f)
        },
        animationSpec = AsteriskMotion.effects(),
        label = "string-list-field-container",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            editing && isError -> MaterialTheme.colorScheme.error
            editing -> activeBorderColor
            else -> activeBorderColor.copy(alpha = 0f)
        },
        animationSpec = AsteriskMotion.effects(),
        label = "string-list-field-border",
    )
    OutlinedTextField(
        value = if (editing) editValue else value,
        onValueChange = { if (editing) onEditValueChange(it) },
        modifier = Modifier
            .weight(1f)
            .focusRequester(focusRequester),
        enabled = editing,
        singleLine = true,
        isError = editing && isError,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = AsteriskShapeTokens.InnerContainer,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            disabledTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            errorContainerColor = containerColor,
            focusedBorderColor = borderColor,
            unfocusedBorderColor = borderColor,
            disabledBorderColor = borderColor,
            errorBorderColor = MaterialTheme.colorScheme.error,
        ),
    )
}

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
    onDismissRequest: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val result = parseStringListBatch(value, validateInput)
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
