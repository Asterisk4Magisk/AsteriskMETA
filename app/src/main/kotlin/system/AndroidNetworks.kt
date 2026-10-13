// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

/**
 * One callback per process tracks available networks, including non-default
 * physical networks while a VPN is active. Only the application context is retained.
 */
internal class AndroidNetworks private constructor(context: Context) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val networks = linkedSetOf<Network>()
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(networks) { networks += network }
        }

        override fun onLost(network: Network) {
            synchronized(networks) { networks -= network }
        }
    }

    init {
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    clearCapabilities()
                } else {
                    removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                    removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                }
            }.build(),
            callback,
        )
    }

    fun snapshot(): List<Network> {
        // Callbacks populate all matching networks asynchronously. Seed the current
        // default so callers can read it before the initial callbacks arrive.
        val active = connectivity.activeNetwork
        return synchronized(networks) {
            buildList {
                active?.let(::add)
                addAll(networks)
            }.distinct()
        }
    }

    companion object {
        private var instance: AndroidNetworks? = null

        @Synchronized
        fun get(context: Context): AndroidNetworks =
            instance ?: AndroidNetworks(context).also { instance = it }
    }
}
