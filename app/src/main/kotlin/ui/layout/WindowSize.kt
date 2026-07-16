// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun pageGutterForWidth(widthDp: Float): Dp = when {
    widthDp >= 840f -> 32.dp
    widthDp >= 600f -> 24.dp
    else -> 16.dp
}

internal fun useNavigationRailForWidth(widthDp: Float): Boolean = widthDp >= 600f

internal fun useSplitPaneForWidth(widthDp: Float): Boolean = widthDp >= 840f

@Composable
internal fun rememberWindowWidthDp(): Float = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.width.toDp().value
}

@Composable
internal fun rememberPageGutter(): Dp {
    return pageGutterForWidth(rememberWindowWidthDp())
}

@Composable
fun shouldShowNavigationRail(): Boolean {
    return useNavigationRailForWidth(rememberWindowWidthDp())
}

@Composable
fun shouldShowSplitPane(): Boolean {
    return useSplitPaneForWidth(rememberWindowWidthDp())
}
