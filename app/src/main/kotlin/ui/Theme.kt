// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui

import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.Resources
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import app.R
import app.modes.ColorModeDark
import app.modes.ColorModeLight
import app.modes.ColorModeSystem
import app.modes.normalizeColorMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import ui.theme.AsteriskMotion
import ui.theme.AsteriskShapes
import ui.theme.AsteriskSpacing
import ui.theme.AsteriskTypography
import ui.theme.LocalReduceMotion
import ui.theme.LocalSpacing
import ui.theme.resolveTheme

val LocalColorMode = compositionLocalOf { ColorModeSystem }
private val LocalResolvedDarkTheme = compositionLocalOf { false }

private const val ThemeSchemeSwitchFraction = 0.35f
private const val ThemeTransitionMaxAlpha = 0.12f

@Composable
fun AppTheme(
    colorMode: Int = ColorModeSystem,
    keyColor: Color? = null,
    systemDark: Boolean,
    content: @Composable () -> Unit,
) {
    SynchronizeSplashTheme(colorMode)
    val resolution = resolveTheme(
        colorMode = colorMode,
        systemDark = systemDark,
        supportsSystemDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        hasCustomSeed = keyColor != null,
    )
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var animationsEnabled by remember(context) {
        mutableStateOf(systemAnimationsEnabled())
    }
    DisposableEffect(context.contentResolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animationsEnabled = systemAnimationsEnabled()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val themeTransitionMotion = AsteriskMotion.effects<Float>(reducedMotion = !animationsEnabled)
    val fallbackSeed = if (resolution.usesCustomSeed) keyColor!! else DefaultSeedColor
    val fallbackScheme = rememberDynamicColorScheme(
        seedColor = fallbackSeed,
        isDark = resolution.isDark,
        style = PaletteStyle.TonalSpot,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
    val systemDynamicScheme = if (
        resolution.usesSystemDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        remember(context, configuration, resolution.isDark) {
            if (resolution.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
    } else {
        null
    }
    val materialScheme = systemDynamicScheme ?: fallbackScheme
    var displayedScheme by remember { mutableStateOf(materialScheme) }
    var displayedIsDark by remember { mutableStateOf(resolution.isDark) }
    var systemBarIsDark by remember { mutableStateOf(resolution.isDark) }
    var transitionActive by remember { mutableStateOf(false) }
    var transitionStartTint by remember { mutableStateOf(materialScheme.primaryContainer) }
    var transitionEndTint by remember { mutableStateOf(materialScheme.primaryContainer) }
    val transitionProgress = remember { Animatable(1f) }

    LaunchedEffect(materialScheme, resolution.isDark) {
        if (displayedScheme === materialScheme && displayedIsDark == resolution.isDark) {
            transitionProgress.snapTo(1f)
            transitionActive = false
            systemBarIsDark = resolution.isDark
            return@LaunchedEffect
        }

        transitionProgress.snapTo(0f)
        transitionStartTint = displayedScheme.primaryContainer
        transitionEndTint = materialScheme.primaryContainer
        transitionActive = true

        withFrameNanos { }
        var targetApplied = false
        transitionProgress.animateTo(
            targetValue = 1f,
            animationSpec = themeTransitionMotion,
        ) {
            if (!targetApplied && value >= ThemeSchemeSwitchFraction) {
                displayedScheme = materialScheme
                displayedIsDark = resolution.isDark
                systemBarIsDark = resolution.isDark
                targetApplied = true
            }
        }
        if (!targetApplied) {
            displayedScheme = materialScheme
            displayedIsDark = resolution.isDark
            systemBarIsDark = resolution.isDark
        }
        transitionActive = false
    }

    CompositionLocalProvider(
        LocalColorMode provides colorMode,
        LocalResolvedDarkTheme provides displayedIsDark,
        LocalReduceMotion provides !animationsEnabled,
    ) {
        CompositionLocalProvider(LocalSpacing provides AsteriskSpacing()) {
            AsteriskMaterialTheme(colorScheme = displayedScheme) {
                SystemBarAppearance(
                    colorScheme = displayedScheme,
                    isDark = systemBarIsDark,
                )
                ThemeTransitionOverlay(
                    transitionActive = transitionActive,
                    transitionProgress = transitionProgress,
                    startTint = transitionStartTint,
                    endTint = transitionEndTint,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SynchronizeSplashTheme(colorMode: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    LaunchedEffect(view, colorMode) {
        val activity = view.context as? Activity ?: return@LaunchedEffect
        val themeId = when (normalizeColorMode(colorMode)) {
            ColorModeLight -> R.style.AppTheme_Starting_Light
            ColorModeDark -> R.style.AppTheme_Starting_Dark
            else -> Resources.ID_NULL
        }
        activity.splashScreen.setSplashScreenTheme(themeId)
    }
}

private fun systemAnimationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AsteriskMaterialTheme(
    colorScheme: ColorScheme,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = AsteriskShapes,
        typography = AsteriskTypography,
        content = content,
    )
}

@Composable
private fun ThemeTransitionOverlay(
    transitionActive: Boolean,
    transitionProgress: Animatable<Float, AnimationVector1D>,
    startTint: Color,
    endTint: Color,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (transitionActive) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        onDrawBehind {
                            val progress = transitionProgress.value.coerceIn(0f, 1f)
                            val intensity = if (progress <= ThemeSchemeSwitchFraction) {
                                progress / ThemeSchemeSwitchFraction
                            } else {
                                (1f - progress) / (1f - ThemeSchemeSwitchFraction)
                            }
                            drawRect(
                                color = lerp(startTint, endTint, progress),
                                alpha = intensity.coerceIn(0f, 1f) * ThemeTransitionMaxAlpha,
                            )
                        }
                    },
            )
        }
    }
}

@Composable
fun isInDarkTheme(): Boolean = LocalResolvedDarkTheme.current

val KeyColors: List<Color> = listOf(
    Color(0xFF3482FF),
    Color(0xFF36D167),
    Color(0xFF7C4DFF),
    Color(0xFFFFB21D),
    Color(0xFFFF5722),
    Color(0xFFE91E63),
    Color(0xFF00BCD4),
)

fun keyColorFor(index: Int): Color? = if (index <= 0) null else KeyColors.getOrNull(index - 1)

private val DefaultSeedColor = Color(0xFF6750A4)

@Composable
private fun SystemBarAppearance(
    colorScheme: ColorScheme,
    isDark: Boolean,
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.setBackgroundDrawable(colorScheme.surface.toArgb().toDrawable())
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}
