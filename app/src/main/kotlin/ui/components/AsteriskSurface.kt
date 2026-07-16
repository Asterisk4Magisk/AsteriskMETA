// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.theme.AsteriskShapeTokens

@Composable
internal fun AsteriskPageCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(
        width = if (selected) 2.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
    )
    AsteriskExpressiveCard(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = border,
        content = content,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AsteriskModalBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable () -> Unit = EmptySheetAction,
    endAction: @Composable () -> Unit = EmptySheetAction,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var renderSheet by remember { mutableStateOf(show) }

    LaunchedEffect(show) {
        if (show) {
            renderSheet = true
            if (sheetState.isVisible) sheetState.show()
        } else if (renderSheet) {
            sheetState.hide()
            if (!sheetState.isVisible) renderSheet = false
        }
    }

    if (!renderSheet) return
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = AsteriskShapeTokens.Sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ),
        ) {
            if (title != null || startAction !== EmptySheetAction || endAction !== EmptySheetAction) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        startAction()
                    }
                    Box(modifier = Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
                        title?.let { sheetTitle ->
                            Text(
                                text = sheetTitle,
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        endAction()
                    }
                }
            }
            content()
        }
    }
}

private val EmptySheetAction: @Composable () -> Unit = {}
