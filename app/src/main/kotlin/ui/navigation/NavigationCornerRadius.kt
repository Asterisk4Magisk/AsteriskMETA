// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp

/** Uses the same bottom screen radius as NG's Miuix navigation. */
@Composable
internal fun rememberNavigationCornerRadius(): Dp {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val fallback = remember(context, configuration) { systemBottomCornerRadius(context) }
    fun readRadius(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
            ?.radius?.takeIf { it > 0 } ?: fallback
    } else {
        fallback
    }

    var radiusPx by remember(view, configuration) { mutableIntStateOf(readRadius()) }
    DisposableEffect(view, configuration) {
        // Insets may not exist during first composition. Observe layout without replacing
        // Compose's insets listener, including after rotation or a window resize.
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnGlobalLayoutListener { radiusPx = readRadius() }
        observer.addOnGlobalLayoutListener(listener)
        radiusPx = readRadius()
        onDispose {
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
        }
    }
    return with(LocalDensity.current) { radiusPx.toDp() }
}

@SuppressLint("DiscouragedApi") // Framework fallback for devices without rounded-corner insets.
private fun systemBottomCornerRadius(context: Context): Int {
    val resources = context.resources
    val id = resources.getIdentifier("rounded_corner_radius_bottom", "dimen", "android")
    return if (id != 0) resources.getDimensionPixelSize(id) else 0
}
