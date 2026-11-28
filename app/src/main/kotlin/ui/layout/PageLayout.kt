// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ui.theme.AsteriskMotion

fun Modifier.pageReadableWidth(maxWidth: Dp = 1120.dp): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = maxWidth)
    .fillMaxWidth()

internal val LocalDetailPaneActive = compositionLocalOf { true }

@Composable
internal fun <T : Any> DetailPaneTransition(
    detail: T?,
    detailKey: (T) -> Any = { Unit },
    content: @Composable (T) -> Unit,
) {
    val sizeMotion = AsteriskMotion.fastSpatial<IntSize>()
    val fadeMotion = AsteriskMotion.fastEffects<Float>()
    val activeKey by rememberUpdatedState(detail?.let(detailKey))
    AnimatedContent(
        targetState = detail,
        contentKey = { it?.let(detailKey) },
        contentAlignment = Alignment.CenterEnd,
        transitionSpec = {
            if (initialState != null && targetState != null) {
                (fadeIn(fadeMotion) togetherWith fadeOut(fadeMotion)).using(null)
            } else {
                ((expandHorizontally(sizeMotion, expandFrom = Alignment.End) + fadeIn(fadeMotion)) togetherWith
                    (shrinkHorizontally(sizeMotion, shrinkTowards = Alignment.End) + fadeOut(fadeMotion))).using(null)
            }
        },
        label = "detailPane",
    ) { item ->
        if (item != null) {
            val active = detailKey(item) == activeKey
            val modifier = if (active) Modifier else Modifier
                .clearAndSetSemantics { hideFromAccessibility() }
                .focusProperties { canFocus = false }
                .onPreviewKeyEvent { true }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
            Box(modifier) {
                CompositionLocalProvider(LocalDetailPaneActive provides active) {
                    content(item)
                }
            }
        }
    }
}

internal val SingleColumnPageMaxWidth = 760.dp

internal fun Modifier.singleColumnPageWidth(): Modifier =
    pageReadableWidth(maxWidth = SingleColumnPageMaxWidth)

@Composable
fun Modifier.pageHorizontalPadding(): Modifier = pageReadableWidth()
    .padding(horizontal = rememberPageGutter())

@Composable
fun Modifier.cutoutHorizontalPadding(): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    val cutoutPadding = WindowInsets.displayCutout.asPaddingValues()
    return padding(
        start = cutoutPadding.calculateStartPadding(layoutDirection),
        end = cutoutPadding.calculateEndPadding(layoutDirection),
    )
}

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
    val outerBottomPadding = if (isWideScreen) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + outerPadding.calculateBottomPadding()
    } else {
        outerPadding.calculateBottomPadding()
    }
    // Standalone windows have no outer page padding; retain their Scaffold's system-bar inset.
    val bottomPadding = maxOf(innerPadding.calculateBottomPadding(), outerBottomPadding)
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
    topExtra: Dp = 8.dp,
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        top = contentPadding.calculateTopPadding() + topExtra,
        start = contentPadding.calculateStartPadding(layoutDirection) + horizontalExtra,
        end = contentPadding.calculateEndPadding(layoutDirection) + horizontalExtra,
        bottom = contentPadding.calculateBottomPadding() + bottomExtra,
    )
}
