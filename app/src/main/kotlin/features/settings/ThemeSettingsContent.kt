// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.R
import ui.KeyColors

/**
 * Inline theme configuration block used inside Settings > Appearance.
 *
 * Renders the light/system/dark theme selector (a Material 3 single-choice
 * segmented button row) and the accent colour picker (a single-row strip of
 * solid colour circles with a sweep-gradient dot for the dynamic option).
 *
 * This composable is the in-page replacement for the previous
 * theme popup: the colour and theme state callbacks are preserved verbatim so the existing `updateAppState { ... .copy(colorMode
 * = ..., seedIndex = ...) }` path keeps driving persistence and the live
 * theme resolution.
 */
@Composable
internal fun ThemeSettingsContent(
    colorModeOptions: List<String>,
    colorMode: Int,
    keyColorOptions: List<String>,
    seedIndex: Int,
    onColorModeChange: (Int) -> Unit,
    onSeedIndexChange: (Int) -> Unit,
) {
    val showColorMode = settingsRowMatchesQuery(
        title = stringResource(R.string.settings_color_mode),
        summary = "",
        value = colorModeOptions.getOrNull(colorMode).orEmpty(),
        optionText = colorModeOptions,
    )
    val showThemeColor = settingsRowMatchesQuery(
        title = stringResource(R.string.settings_theme_color),
        summary = "",
        value = keyColorOptions.getOrNull(seedIndex).orEmpty(),
        optionText = keyColorOptions,
    )
    if (!showColorMode && !showThemeColor) return

    SettingsSectionCard {
        if (showColorMode) {
            ThemeModeSegmentedRow(
                options = colorModeOptions,
                selectedIndex = colorMode,
                onSelectedIndexChange = onColorModeChange,
            )
        }
        if (showThemeColor) {
            ThemeColorDotPicker(
                options = keyColorOptions,
                selectedIndex = seedIndex,
                onSelectedIndexChange = onSeedIndexChange,
            )
        }
    }
}

@Composable
private fun ThemeModeSegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val title = stringResource(R.string.settings_color_mode)
    val safeIndex = if (selectedIndex in options.indices) selectedIndex else 0
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = safeIndex == index,
                    onClick = { onSelectedIndexChange(index) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size,
                    ),
                ) {
                    Text(
                        text = label,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeColorDotPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val title = stringResource(R.string.settings_theme_color)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val safeIndex = if (selectedIndex in options.indices) selectedIndex else 0
        // Keep the compact single-row picker scrollable on narrow screens.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = safeIndex == index
                val accent = accentColorFor(index)
                // Selection ring colour: track the actual colour the dot
                // shows so the ring reads as part of the same accent when
                // selected. For the dynamic (index 0) option the dot is a
                // rainbow sweep, but the spec asks the ring to use the
                // currently-resolved dynamic accent (MaterialTheme primary)
                // so the selection state reads as one coherent colour.
                val ringColor = accent
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = label }
                        .selectable(
                            selected = isSelected,
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.RadioButton,
                        ) {
                            onSelectedIndexChange(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        // Outer ring: same colour as the dot, but with a
                        // clearly visible gap to the inner dot. The ring is
                        // a stroked border (no shadow, no extra decoration).
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .border(
                                    width = 2.dp,
                                    color = ringColor,
                                    shape = CircleShape,
                                ),
                        )
                    }
                    // Inner dot: solid accent for explicit colours, sweep
                    // gradient for the dynamic (index 0) option. Diameter is
                    // 18dp so the gap between ring (28dp outer, 24dp inner
                    // edge) and dot reads as 3dp on each side - clearly
                    // visible but tight enough to keep the row single-line.
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                brush = if (index == 0) dynamicAccentBrush()
                                else solidBrush(accent),
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Solid colour brush helper - kept tiny so the dot's `background(brush = ...)`
 * call has a single uniform call site regardless of which path is taken
 * (gradient vs solid).
 */
private fun solidBrush(color: Color): Brush = SolidColor(color)

/**
 * Sweep gradient for the "system dynamic accent" option. Goes around the
 * circle once so the whole inner dot reads as a continuous rainbow ring
 * without hard sector seams. Centre stays at the start colour so the dot
 * has no internal hole.
 */
private fun dynamicAccentBrush(): Brush = Brush.sweepGradient(
    0.00f to Color(0xFFFF3B30), // red
    0.17f to Color(0xFFFF9500), // orange
    0.33f to Color(0xFFFFCC00), // yellow
    0.50f to Color(0xFF34C759), // green
    0.67f to Color(0xFF007AFF), // blue
    0.83f to Color(0xFF5856D6), // indigo
    1.00f to Color(0xFFFF3B30), // back to red - closes the loop
)

/**
 * Resolves the fill colour for the i-th seed option. Index 0 is the
 * "system default / dynamic" accent - it returns the resolved primary so
 * the selection ring and any non-gradient fallback both reflect the
 * colour the user is actually seeing in the live theme. Indices
 * 1..KeyColors.size map to [KeyColors].
 */
@Composable
private fun accentColorFor(index: Int): Color = when (index) {
    0 -> MaterialTheme.colorScheme.primary
    else -> KeyColors.getOrNull(index - 1) ?: MaterialTheme.colorScheme.primary
}
