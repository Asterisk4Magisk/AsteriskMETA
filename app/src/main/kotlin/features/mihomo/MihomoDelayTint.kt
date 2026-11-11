// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import engine.mihomo.runtime.MihomoDelayStatus
import ui.isInDarkTheme

/**
 * Traffic-light tint for a measured proxy delay, shared by the proxy list and the home hero card.
 * A timeout or failure, and a negative delay from the core, read as unreachable.
 */
@Composable
internal fun mihomoDelayTint(delay: Int?, status: MihomoDelayStatus?): Color {
    val darkTheme = isInDarkTheme()
    val red = if (darkTheme) Color(0xFFF12522) else Color(0xFFE94634)
    return when {
        status == MihomoDelayStatus.Timeout || status == MihomoDelayStatus.Failed -> red
        status == null || delay == null -> MaterialTheme.colorScheme.onSurfaceVariant
        delay < 0 -> red
        delay < 100 -> if (darkTheme) Color(0xFF6BD58A) else Color(0xFF128A3C)
        delay < 200 -> if (darkTheme) Color(0xFFFFC857) else Color(0xFFD18A00)
        delay < 300 -> if (darkTheme) Color(0xFFFF9B63) else Color(0xFFE06400)
        else -> red
    }
}
