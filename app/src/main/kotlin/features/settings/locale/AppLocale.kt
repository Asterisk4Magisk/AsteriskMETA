// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.modes.ColorModeDark
import app.modes.ColorModeLight
import app.modes.LanguageModeEnglish
import app.modes.LanguageModeSimplifiedChinese
import app.modes.normalizeColorMode
import java.util.Locale

private fun languageTagForMode(mode: Int): String? = when (mode) {
    LanguageModeEnglish -> "en"
    LanguageModeSimplifiedChinese -> "zh-CN"
    else -> null
}

@Composable
fun ProvideAppLanguage(
    languageMode: Int,
    systemLocale: Locale,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val locale = remember(languageMode, systemLocale) {
        resolveAppLocale(languageMode, systemLocale)
    }
    val configuration = remember(context, locale) { context.localizedConfiguration(locale) }
    val localizedContext = remember(context, configuration) {
        context.createConfigurationContext(configuration)
    }

    SideEffect {
        if (Locale.getDefault().toLanguageTag() != locale.toLanguageTag()) {
            Locale.setDefault(locale)
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        content = content,
    )
}

internal fun resolveAppLocale(languageMode: Int, systemLocale: Locale): Locale {
    return languageTagForMode(languageMode)?.let(Locale::forLanguageTag) ?: systemLocale
}

internal fun Context.localizedAppContext(
    languageMode: Int,
    colorMode: Int? = null,
): Context {
    val locale = resolveAppLocale(languageMode, resources.configuration.primaryLocale())
    return createConfigurationContext(localizedConfiguration(locale, colorMode))
}

private fun Configuration.primaryLocale(): Locale {
    return locales[0] ?: Locale.getDefault()
}

private fun Context.localizedConfiguration(
    locale: Locale,
    colorMode: Int? = null,
): Configuration {
    return Configuration(resources.configuration).apply {
        setLocales(LocaleList(locale))
        setLayoutDirection(locale)
        colorMode?.let(::applyAppColorMode)
    }
}

private fun Configuration.applyAppColorMode(colorMode: Int) {
    val nightMode = when (normalizeColorMode(colorMode)) {
        ColorModeLight -> Configuration.UI_MODE_NIGHT_NO
        ColorModeDark -> Configuration.UI_MODE_NIGHT_YES
        else -> return
    }
    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
}
