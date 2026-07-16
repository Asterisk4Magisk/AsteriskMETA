package ui.theme

import app.modes.ColorModeDark
import app.modes.ColorModeLight
import app.modes.ColorModeThemeDark
import app.modes.ColorModeThemeLight

internal data class ThemeResolution(
    val isDark: Boolean,
    val usesSystemDynamicColor: Boolean,
    val usesCustomSeed: Boolean,
)

internal fun resolveTheme(
    colorMode: Int,
    systemDark: Boolean,
    supportsSystemDynamicColor: Boolean,
    hasCustomSeed: Boolean,
): ThemeResolution {
    val isDark = when (colorMode) {
        ColorModeLight, ColorModeThemeLight -> false
        ColorModeDark, ColorModeThemeDark -> true
        else -> systemDark
    }
    return ThemeResolution(
        isDark = isDark,
        usesSystemDynamicColor = supportsSystemDynamicColor && !hasCustomSeed,
        usesCustomSeed = hasCustomSeed,
    )
}
