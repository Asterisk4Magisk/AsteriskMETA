// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.R
import ui.icons.AsteriskIcons as Icons
import ui.theme.AsteriskMotion
import ui.theme.AsteriskShapeTokens

@Composable
internal fun AsteriskTonalButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = AsteriskShapeTokens.Pill,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
internal fun AsteriskActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
) {
    val loadingAnimationSpec = AsteriskMotion.fastEffects<Float>()
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        colors = colors,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = AsteriskMotion.fadeThrough(loadingAnimationSpec),
            label = "action-button-loading",
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(text)
    }
}

@Composable
internal fun AsteriskExtendedFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = AsteriskShapeTokens.Pill,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(text) },
    )
}

/** The Cancel action of a sheet or dialog. */
@Composable
internal fun AsteriskCancelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    AsteriskActionButton(
        text = stringResource(R.string.common_cancel),
        icon = Icons.Rounded.Close,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
    )
}

/** The Save action of a sheet or editor. */
@Composable
internal fun AsteriskSaveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    AsteriskActionButton(
        text = stringResource(R.string.common_save),
        icon = Icons.Rounded.Save,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
    )
}
