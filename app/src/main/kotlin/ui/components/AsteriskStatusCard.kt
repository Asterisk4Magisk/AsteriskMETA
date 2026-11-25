// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun AsteriskStatusCard(
    modifier: Modifier = Modifier,
    status: String? = null,
    compactStatus: Boolean = false,
    controls: @Composable RowScope.() -> Unit = EmptyStatusCardControls,
    statusRowOffset: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    AsteriskPageCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
            if (status != null || controls !== EmptyStatusCardControls) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (compactStatus) 0.dp else 40.dp)
                        .offset(y = statusRowOffset),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = if (compactStatus) Alignment.Bottom else Alignment.CenterVertically,
                ) {
                    if (status == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Text(
                            text = status,
                            modifier = if (compactStatus) {
                                Modifier.weight(1f).alignByBaseline()
                            } else {
                                Modifier.weight(1f)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    controls()
                }
            }
        }
    }
}

@Composable
internal fun AsteriskStatusCardHeader(
    action: @Composable ((Modifier) -> Unit)?,
    actionWidth: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) {
    if (action == null) {
        Row(content = content)
        return
    }
    // Align the header actions and their touch targets while keeping the title at the card inset.
    Box(modifier = Modifier.fillMaxWidth().offset(x = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().offset(x = (-12).dp).padding(end = actionWidth),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        action(
            Modifier
                .matchParentSize()
                .wrapContentSize(Alignment.CenterEnd, unbounded = true)
                .size(width = actionWidth, height = 48.dp),
        )
    }
}

private val EmptyStatusCardControls: @Composable RowScope.() -> Unit = {}
