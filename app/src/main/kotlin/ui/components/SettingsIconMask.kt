// Copyright 2026, AsteriskBOX contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ui.isInDarkTheme

/**
 * Semantic accent slot for a preference-row icon. Each entry holds a paired (background,
 * foreground) color taken from the Material3 reference palette tones `_90` / `_80` (background)
 * and `_30` (foreground). Those tones are the same ones upstream AOSP / crdroid uses for
 * dashboard tiles — see `packages/apps/Settings/res/values/colors.xml` `homepage_*_fg` /
 * `homepage_*_bg` mappings in `android-15+` settingslib; see also the night override at
 * `packages/apps/Settings/res/values-night/colors.xml` which swaps the background tone from
 * `_90` to `_80` while keeping the foreground at `_30`.
 *
 * The pair (bg, fg) is kept together so the per-row color recipe is self-documenting and
 * the light/dark scheme only has to swap background tone, never pick colors.
 *
 * Mapping table (mirrors crdroid's expressive preference tiles):
 * | Accent          | bg light (_90) | bg dark (_80) | fg (_30)   | Used for                              |
 * |-----------------|----------------|---------------|------------|---------------------------------------|
 * | MaskBlue        | #D0E4FF        | #A1C9FF       | #04409F    | Network / general-purpose blue         |
 * | MaskBlueVariant| #BDE9FF        | #67D4FF       | #004D68    | Dns / generic "cool" tone             |
 * | MaskPink       | #FFD8EF        | #FFAEE4       | #8D0053    | Notification / modes / sound          |
 * | MaskOrange     | #FFDCC3        | #FFB683       | #753403    | Display / wallpaper / theme           |
 * | MaskYellow     | #FFE07C        | #FCBD00       | #6D3A01    | Storage / backup / hev-tun            |
 * | MaskGreen      | #BEEFBB        | #80DA88       | #00522C    | Routing / battery / log / ipv6-prefer |
 * | MaskGrey       | #E3E3E3        | #C7C7C7       | #474747    | System / muted / licences             |
 * | MaskRed        | #FFDADC        | #FFB3AE       | #8A1A16    | Safety / alert / blacklist           |
 * | MaskPurple     | #EEDCFE        | #D9BAFD       | #5629A4    | About / modes-hub / accent controls   |
 */
@Immutable
enum class IconAccent(
    internal val lightBg: Color,
    internal val darkBg: Color,
    internal val lightFg: Color,
) {
    /** Fallback: render using the existing Material `secondaryContainer` swatch. */
    Surface(Color.Unspecified, Color.Unspecified, Color.Unspecified),

    MaskBlue(Color(0xFFD0E4FF), Color(0xFFA1C9FF), Color(0xFF04409F)),
    MaskBlueVariant(Color(0xFFBDE9FF), Color(0xFF67D4FF), Color(0xFF004D68)),
    MaskPink(Color(0xFFFFD8EF), Color(0xFFFFAEE4), Color(0xFF8D0053)),
    MaskOrange(Color(0xFFFFDCC3), Color(0xFFFFB683), Color(0xFF753403)),
    MaskYellow(Color(0xFFFFE07C), Color(0xFFFCBD00), Color(0xFF6D3A01)),
    MaskGreen(Color(0xFFBEEFBB), Color(0xFF80DA88), Color(0xFF00522C)),
    MaskGrey(Color(0xFFE3E3E3), Color(0xFFC7C7C7), Color(0xFF474747)),
    MaskRed(Color(0xFFFFDADC), Color(0xFFFFB3AE), Color(0xFF8A1A16)),
    MaskPurple(Color(0xFFEEDCFE), Color(0xFFD9BAFD), Color(0xFF5629A4)),
}

/**
 * Preference-row leading icon: a 40dp circular swatch with a 24dp icon centered inside.
 *
 * Light scheme: swatch = the accent's `_90` pale tint, icon = the accent's `_30` deep hue.
 * Dark  scheme: swatch = the accent's `_80` saturated tone (crdroid swaps `_90` → `_80`
 *               in values-night/colors.xml; the foreground stays `_30` so the icon keeps
 *               its strong saturated identity on top of the bright swatch),
 *               icon    = `_30` at full opacity.
 *
 * Falls through to the legacy Material `secondaryContainer` swatch when [accent] is
 * [IconAccent.Surface], so any preference that hasn't opted in keeps the existing look.
 *
 * Geometry matches upstream AOSP dashboard tile (`dashboard_tile_image_size = 40dp`,
 * `dashboard_tile_foreground_image_size = 24dp`); the padding comes from centering the
 * 24dp icon inside the 40dp circle (8dp inset on every side).
 */
@Composable
fun MaskedPreferenceIcon(
    icon: ImageVector,
    accent: IconAccent,
    modifier: Modifier = Modifier,
) {
    if (accent == IconAccent.Surface) {
        LegacySecondaryContainerIcon(icon = icon, modifier = modifier)
        return
    }
    val darkTheme = isInDarkTheme()
    val containerColor = if (darkTheme) accent.darkBg else accent.lightBg
    val contentColor = accent.lightFg
    Box(
        modifier = modifier
            .size(40.dp)
            .background(color = containerColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun LegacySecondaryContainerIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}