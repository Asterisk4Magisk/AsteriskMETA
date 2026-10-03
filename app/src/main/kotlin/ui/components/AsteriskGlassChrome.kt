// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import ui.theme.AsteriskMotion

internal val LocalChromeBackdrop = compositionLocalOf<HazeState?> { null }

// Shared by top bars, floating navigation controls, and the no-blur fallback.
private const val ChromeTransparency = 0.60f

@Composable
internal fun chromeStyle(): HazeBlurStyle {
    val surface = MaterialTheme.colorScheme.surface
    val tint = HazeColorEffect.tint(surface.copy(alpha = 1f - ChromeTransparency))
    return HazeBlurStyle {
        backgroundColor(surface)
        colorEffects(listOf(tint))
        blurRadius(18.dp)
        noiseFactor(0f)
        fallbackColorEffect(tint)
    }
}

/** Keep each page's backdrop separate so transitions never sample another page. */
@Composable
internal fun AsteriskScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = rememberHazeState()
    val style = chromeStyle()
    Scaffold(
        modifier = modifier,
        topBar = {
            Box(Modifier.fillMaxWidth().clipToBounds().hazeBlur(HazeInput.Sources(backdrop), style)) {
                topBar()
            }
        },
        bottomBar = {
            CompositionLocalProvider(LocalChromeBackdrop provides backdrop) {
                bottomBar()
            }
        },
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
    ) { padding ->
        // Capture content only; including the bars would feed an effect back into itself.
        Box(
            modifier = Modifier.fillMaxSize().hazeSource(backdrop),
        ) {
            CompositionLocalProvider(LocalChromeBackdrop provides backdrop) {
                content(padding)
            }
        }
    }
}

@Composable
internal fun AsteriskTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun Modifier.floatingChrome(): Modifier =
    shadow(6.dp, CircleShape)
        .clip(CircleShape)
        .hazeBlur(
            input = LocalChromeBackdrop.current?.let { HazeInput.Sources(it) }
                ?: HazeInput.Content,
            style = chromeStyle(),
        )
        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), CircleShape)

@Composable
private fun floatingNavigationContentColor(selected: Boolean): Color {
    val color by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = AsteriskMotion.fastEffects(),
        label = "navigation-item-color",
    )
    return color
}

@Composable
internal fun AsteriskFloatingNavigationAction(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val contentColor = floatingNavigationContentColor(selected)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(72.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().floatingChrome())
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .asteriskNavigationSelection(selected)
                .indication(interactionSource, ripple()),
        )
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
        }
    }
}

@Composable
internal fun AsteriskFloatingNavigationBar(
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Box(Modifier.matchParentSize().floatingChrome())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp)
                        .selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
            trailingAction?.invoke()
        }
    }
}

@Composable
internal fun RowScope.AsteriskFloatingNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val contentColor = floatingNavigationContentColor(selected)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .weight(1f)
            .asteriskNavigationSelection(selected)
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        // Clip only the pressed feedback; large labels must not be cut by the pill curve.
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .indication(interactionSource, ripple()),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = contentColor,
            )
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
