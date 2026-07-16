// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.layout

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val ReadablePageMaxWidth = 1120.dp

fun Modifier.pageReadableWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = ReadablePageMaxWidth)
    .fillMaxWidth()

@Composable
fun Modifier.pageHorizontalPadding(): Modifier = pageReadableWidth()
    .padding(horizontal = rememberPageGutter())

fun Modifier.pageScrollModifiers(
    topAppBarScrollBehavior: TopAppBarScrollBehavior,
): Modifier = this
    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
    .fillMaxHeight()

@Composable
fun Modifier.pageWindowPadding(
    outerPadding: PaddingValues,
): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    return padding(
        start = outerPadding.calculateStartPadding(layoutDirection),
        end = outerPadding.calculateEndPadding(layoutDirection),
    ).imePadding()
}

@Composable
fun pageContentPadding(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues,
    isWideScreen: Boolean,
    extraTop: Dp = 0.dp,
    extraStart: Dp = 0.dp,
    extraEnd: Dp = 0.dp,
): PaddingValues {
    val topPadding = innerPadding.calculateTopPadding() + extraTop
    val bottomPadding = if (isWideScreen) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + outerPadding.calculateBottomPadding()
    } else {
        outerPadding.calculateBottomPadding()
    }
    return remember(topPadding, bottomPadding, extraStart, extraEnd) {
        PaddingValues(
            top = topPadding,
            start = extraStart,
            end = extraEnd,
            bottom = bottomPadding,
        )
    }
}

@Composable
fun pageContentPaddingWithCutout(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues,
    isWideScreen: Boolean,
    extraTop: Dp = 0.dp,
): PaddingValues {
    val cutoutPadding = WindowInsets.displayCutout.asPaddingValues()
    return pageContentPadding(
        innerPadding = innerPadding,
        outerPadding = outerPadding,
        isWideScreen = isWideScreen,
        extraTop = extraTop,
        extraStart = cutoutPadding.calculateStartPadding(LayoutDirection.Ltr),
        extraEnd = cutoutPadding.calculateEndPadding(LayoutDirection.Ltr),
    )
}

@Composable
fun pageListPadding(
    contentPadding: PaddingValues,
    bottomExtra: Dp = 12.dp,
    horizontalExtra: Dp = rememberPageGutter(),
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        top = contentPadding.calculateTopPadding(),
        start = contentPadding.calculateStartPadding(layoutDirection) + horizontalExtra,
        end = contentPadding.calculateEndPadding(layoutDirection) + horizontalExtra,
        bottom = contentPadding.calculateBottomPadding() + bottomExtra,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AdaptiveTopAppBar(
    title: String,
    isWideScreen: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                bottomContent()
            }
        },
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

