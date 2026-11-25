// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.overlay

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.LocalAppServices
import app.R
import kotlinx.coroutines.launch
import ui.icons.AsteriskIcons as Icons

@Composable
internal fun ConnectionOverlayToggle(running: Boolean) {
    val services = LocalAppServices.current
    val overlay = services.connectionMonitorOverlay
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    val scope = rememberCoroutineScope()
    val visible by overlay.visible.collectAsStateWithLifecycle()
    val unavailable = stringResource(R.string.monitor_overlay_open_failed)
    val permissionMessage = stringResource(R.string.monitor_overlay_permission)
    fun show() {
        if (!overlay.show(activity)) {
            scope.launch { services.tipNotifier.show(unavailable) }
        }
    }
    IconToggleButton(
        checked = visible,
        enabled = running || visible,
        onCheckedChange = { checked ->
            when {
                !checked -> overlay.dismiss()
                Settings.canDrawOverlays(context) -> show()
                else -> {
                    val launched = services.requestConnectionOverlayPermission()
                    val message = if (launched) permissionMessage else unavailable
                    scope.launch { services.tipNotifier.show(message) }
                }
            }
        },
    ) {
        Icon(
            Icons.Rounded.PictureInPictureAlt,
            contentDescription = stringResource(R.string.monitor_overlay_title),
            tint = if (visible) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}
