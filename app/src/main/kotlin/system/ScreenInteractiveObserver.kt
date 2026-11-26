// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Reports screen on/off changes. Registration and callbacks run on the main thread.
 */
internal class ScreenInteractiveObserver(
    private val context: Context,
    private val onChange: (interactive: Boolean) -> Unit,
) {
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> onChange(true)
                Intent.ACTION_SCREEN_OFF -> onChange(false)
            }
        }
    }

    val isInteractive: Boolean
        get() = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }
}
