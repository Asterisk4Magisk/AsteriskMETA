// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.R
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun MihomoDelayToolbar(
    enabled: Boolean,
    testing: Boolean,
    onDelayTest: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(
            end = 20.dp,
            bottom = bottomPadding + MihomoFloatingToolbarBottomSpacing,
        ),
    ) {
        ExtendedFloatingActionButton(
            onClick = { if (enabled) onDelayTest() },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                alpha = if (enabled && !testing) 1f else 0.45f,
            ),
            icon = {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = stringResource(R.string.mihomo_proxies_group_test),
                    )
                }
            },
            text = { Text(stringResource(R.string.mihomo_proxies_group_test)) },
        )
    }
}

private val MihomoFloatingToolbarBottomSpacing = 16.dp
