// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ui.theme.AsteriskMotion

internal data class EditorListItem(val id: Int, val title: String)

@Composable
internal fun EditorListPane(
    title: String,
    entries: List<EditorListItem>,
    selectedId: Int?,
    padding: PaddingValues,
    show: Boolean = true,
    animateFocusLayout: Boolean = false,
    onSelect: ((Int) -> Unit)? = null,
    selectionEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val visible = show && entries.isNotEmpty() && LocalAdaptiveWindow.current.showTwoColumns
    val sizeMotion = if (animateFocusLayout) codeEditorFocusSize() else AsteriskMotion.fastSpatial<IntSize>()
    val fadeMotion = AsteriskMotion.fastEffects<Float>()
    val sidebarFade = if (animateFocusLayout) codeEditorFocusFade() else fadeMotion
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    CodeEditorPaneLayout(sidebarVisible = visible, animateFocusLayout = animateFocusLayout) {
        AnimatedVisibility(
            visible = visible,
            enter = expandHorizontally(sizeMotion, expandFrom = Alignment.Start) + fadeIn(sidebarFade),
            exit = shrinkHorizontally(sizeMotion, shrinkTowards = Alignment.Start) + fadeOut(sidebarFade),
        ) {
            LazyColumn(
                modifier = Modifier
                    .width(268.dp)
                    .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding())
                    .padding(start = 16.dp, end = 8.dp)
                    .background(colors.surfaceContainerLow, shape)
                    .then(if (onSelect != null) Modifier.selectableGroup() else Modifier),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                    )
                }
                items(entries, key = { it.id }) { item ->
                    val selected = selectedId == item.id
                    Text(
                        text = item.title,
                        modifier = Modifier.fillMaxWidth()
                            .clip(shape)
                            .then(
                                if (selected) Modifier.background(colors.secondaryContainer)
                                else Modifier,
                            )
                            .then(
                                if (onSelect != null) {
                                    Modifier.heightIn(min = 48.dp).selectable(
                                        selected = selected,
                                        enabled = selectionEnabled && visible,
                                        role = Role.Button,
                                        onClick = { onSelect(item.id) },
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            var previousId by remember { mutableStateOf(selectedId) }
            val detailVisibility = remember(selectedId) {
                val animate = visible && onSelect != null && selectedId != previousId
                MutableTransitionState(!animate).apply { targetState = true }
            }
            SideEffect { previousId = selectedId }

            androidx.compose.animation.AnimatedVisibility(
                visibleState = detailVisibility,
                enter = fadeIn(fadeMotion),
                exit = ExitTransition.None,
                label = "editorEntryChange",
            ) {
                content()
            }
        }
    }
}
