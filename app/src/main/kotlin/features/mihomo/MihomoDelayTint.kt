// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import engine.mihomo.runtime.MihomoDelayStatus
import ui.isInDarkTheme

internal enum class MihomoDelayTintBand {
    Neutral,
    DeepGreen,
    Green,
    Yellow,
    Orange,
    Red,
}

internal fun mihomoDelayTintBand(delay: Int?, status: MihomoDelayStatus?): MihomoDelayTintBand = when {
    status == MihomoDelayStatus.Timeout || status == MihomoDelayStatus.Failed -> MihomoDelayTintBand.Red
    status == null || delay == null -> MihomoDelayTintBand.Neutral
    delay < 0 || delay >= 1_500 -> MihomoDelayTintBand.Red
    delay < 100 -> MihomoDelayTintBand.DeepGreen
    delay < 300 -> MihomoDelayTintBand.Green
    delay < 500 -> MihomoDelayTintBand.Yellow
    else -> MihomoDelayTintBand.Orange
}

/**
 * Traffic-light tint for a measured proxy delay, shared by every delay surface.
 * A timeout or failure, and a negative delay from the core, read as unreachable.
 */
@Composable
internal fun mihomoDelayTint(delay: Int?, status: MihomoDelayStatus?): Color {
    val darkTheme = isInDarkTheme()
    return when (mihomoDelayTintBand(delay, status)) {
        MihomoDelayTintBand.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        MihomoDelayTintBand.DeepGreen -> if (darkTheme) Color(0xFF38C96B) else Color(0xFF087A32)
        MihomoDelayTintBand.Green -> if (darkTheme) Color(0xFF86D993) else Color(0xFF2E9B47)
        MihomoDelayTintBand.Yellow -> if (darkTheme) Color(0xFFFFD166) else Color(0xFFC78A00)
        MihomoDelayTintBand.Orange -> if (darkTheme) Color(0xFFFF9B63) else Color(0xFFE06400)
        MihomoDelayTintBand.Red -> if (darkTheme) Color(0xFFF12522) else Color(0xFFE94634)
    }
}
