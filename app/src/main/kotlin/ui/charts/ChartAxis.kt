// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.charts

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space between an axis label gutter and the plot; shared by every chart style here. */
val ChartAxisLabelSpacing: Dp = 6.dp

/**
 * Minimum gutter width. The label text changes size as the scale changes, and the plot is laid out
 * next to it, so a content-sized gutter would make the chart jump horizontally every update.
 */
val ChartAxisLabelMinWidth: Dp = 58.dp
