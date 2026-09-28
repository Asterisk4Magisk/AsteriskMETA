package engine.mihomo.binding

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import features.logs.AndroidAppLogger
import libclash.Libclash
import system.getInstalledApplicationsCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

// These notifications belong to the embedded Mihomo core. Other app cores have
// their own platform adapters, and the standalone ROOT core discovers its OS state.
internal class AndroidMihomoPlatformContext private constructor(
    private val application: Application,
    private val notifications: MihomoPlatformNotifications,
) {
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val worker = HandlerThread("MihomoPlatformContext").apply { start() }
    private val handler = Handler(worker.looper)
    private val networks = mutableMapOf<Network, MihomoDnsNetwork>()
    private var lastDns: String? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshPackages()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            networks.putIfAbsent(network, emptyNetwork(network))
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val previous = networks[network] ?: emptyNetwork(network)
            networks[network] = previous.withCapabilities(capabilities)
            publishDns()
        }

        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            val previous = networks[network] ?: emptyNetwork(network)
            networks[network] = previous.copy(dnsServers = properties.dnsServers.mapNotNull { it.hostAddress })
            publishDns()
        }

        override fun onLost(network: Network) {
            networks.remove(network)
            publishDns()
        }
    }

    // The default may change without changing either physical network's link
    // properties. Its events only trigger selection; its VPN DNS is never read.
    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publishDns()
        override fun onLost(network: Network) = publishDns()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publishDns()
    }

    @Suppress("DEPRECATION")
    private fun initialize() {
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(application, packageReceiver, packageFilter, null, handler,
            ContextCompat.RECEIVER_NOT_EXPORTED)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivity.registerNetworkCallback(request, networkCallback, handler)
        connectivity.registerDefaultNetworkCallback(defaultNetworkCallback, handler)
        // Register first so changes during initial enumeration are delivered.
        // Initial and later snapshots share the same handler; initialization
        // waits so callers cannot load system:// before the first notification.
        val initialized = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        handler.post {
            try {
                refreshPackages()
                networks.clear()
                for (network in connectivity.allNetworks) {
                    val capabilities = connectivity.getNetworkCapabilities(network) ?: continue
                    val properties = connectivity.getLinkProperties(network)
                    networks[network] = emptyNetwork(network).withCapabilities(capabilities)
                        .copy(dnsServers = properties?.dnsServers.orEmpty().mapNotNull { it.hostAddress })
                }
                publishDns()
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                initialized.countDown()
            }
        }
        initialized.await()
        failure.get()?.let { throw it }
    }

    private fun refreshPackages() {
        runCatching {
            val apps = application.packageManager.getInstalledApplicationsCompat()
                .map { it.uid to it.packageName }
            notifications.notifyInstalledAppsChanged(mihomoInstalledAppsSnapshot(apps))
        }.onFailure { AndroidAppLogger.warn(LogTag, "Failed to refresh Mihomo package mapping", it) }
    }

    private fun publishDns() {
        val activeNetwork = connectivity.activeNetwork
        val snapshot = mihomoSystemDnsSnapshot(networks.map { (network, state) ->
            state.copy(active = network == activeNetwork)
        })
        if (snapshot == lastDns) return
        runCatching { notifications.notifyDnsChanged(snapshot) }
            .onSuccess { lastDns = snapshot }
            .onFailure { AndroidAppLogger.warn(LogTag, "Failed to refresh Mihomo system DNS", it) }
    }

    private fun emptyNetwork(network: Network) =
        MihomoDnsNetwork(network.toString(), false, false, false, false, emptyList())

    private fun MihomoDnsNetwork.withCapabilities(capabilities: NetworkCapabilities) = copy(
        notVpn = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
        internet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    )

    companion object {
        private const val LogTag = "MihomoPlatformContext"
        private var instance: AndroidMihomoPlatformContext? = null
        private val notifications by lazy { MihomoPlatformNotifications.discover(Libclash::class.java) }
        private var unavailableWarningLogged = false

        @Synchronized
        fun start(application: Application) {
            if (instance != null) return
            val notifications = notifications
            if (notifications == null) {
                if (!unavailableWarningLogged) {
                    AndroidAppLogger.warn(LogTag, "Mihomo core lacks platform notifications; update the core AAR")
                    unavailableWarningLogged = true
                }
                return
            }
            val context = AndroidMihomoPlatformContext(application, notifications)
            try {
                context.initialize()
            } catch (error: Throwable) {
                runCatching { application.unregisterReceiver(context.packageReceiver) }
                runCatching { context.connectivity.unregisterNetworkCallback(context.networkCallback) }
                runCatching { context.connectivity.unregisterNetworkCallback(context.defaultNetworkCallback) }
                context.worker.quitSafely()
                throw error
            }
            instance = context
        }
    }
}
