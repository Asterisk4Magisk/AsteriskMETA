// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import android.content.Intent
import app.modes.ProxyAppListModeGlobal
import engine.hevtun.DefaultHevSocks5TunnelLogLevel
import engine.hevtun.DefaultHevSocks5TunnelTcpReadWriteTimeoutMillis
import engine.hevtun.DefaultHevSocks5TunnelUdpReadWriteTimeoutMillis
import engine.hevtun.HevSocks5TunnelConfig
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyOptions
import engine.mihomo.MihomoCoreLogPaths

internal object AsteriskVpnServiceIntents {
    const val ACTION_START = "app.action.START_VPN"
    const val ACTION_STOP = "app.action.STOP_VPN"

    fun startIntent(context: Context, config: VpnServiceStartConfig): Intent {
        return Intent(context, AsteriskVpnService::class.java).apply {
            action = ACTION_START
            writeStartConfig(config)
        }
    }

    fun stopIntent(context: Context): Intent {
        return Intent(context, AsteriskVpnService::class.java).apply {
            action = ACTION_STOP
        }
    }
}

internal fun Intent.readVpnServiceStartConfig(): VpnServiceStartConfig? {
    val sessionName = getStringExtra(EXTRA_SESSION_NAME) ?: return null
    val ipv4Address = getStringExtra(EXTRA_IPV4_ADDRESS) ?: return null
    val mihomoProfilePath = getStringExtra(EXTRA_MIHOMO_PROFILE_PATH) ?: return null
    val mihomoProfileSignature = getStringExtra(EXTRA_MIHOMO_PROFILE_SIGNATURE) ?: return null
    val localProxyOptions = LocalProxyOptions(
        listenAddress = getStringExtra(EXTRA_LOCAL_PROXY_LISTEN_ADDRESS).orEmpty().ifBlank {
            LocalProxyLoopbackAddress
        },
        port = getIntExtra(EXTRA_LOCAL_PROXY_PORT, VpnDefaults.LOCAL_PROXY_PORT),
        username = getStringExtra(EXTRA_LOCAL_PROXY_USERNAME).orEmpty(),
        password = getStringExtra(EXTRA_LOCAL_PROXY_PASSWORD).orEmpty(),
    )
    return VpnServiceStartConfig(
        sessionName = sessionName,
        mtu = getIntExtra(EXTRA_MTU, VpnDefaults.MTU),
        ipv4Address = ipv4Address,
        ipv4PrefixLength = getIntExtra(EXTRA_IPV4_PREFIX_LENGTH, defaultIpv4TunAddress.prefixLength),
        ipv6Address = getStringExtra(EXTRA_IPV6_ADDRESS),
        ipv6PrefixLength = getIntExtra(EXTRA_IPV6_PREFIX_LENGTH, defaultIpv6TunAddress.prefixLength),
        enableIpv6 = getBooleanExtra(EXTRA_ENABLE_IPV6, false),
        enableLocalDns = getBooleanExtra(EXTRA_ENABLE_LOCAL_DNS, true),
        dnsServers = getStringArrayExtra(EXTRA_DNS_SERVERS)?.toList().orEmpty().ifEmpty {
            listOf(VpnDefaults.IPV4_DNS)
        },
        mihomoProfilePath = mihomoProfilePath,
        mihomoProfileSignature = mihomoProfileSignature,
        ageSecretKey = getStringExtra(EXTRA_AGE_SECRET_KEY).orEmpty(),
        mihomoTunStack = getStringExtra(EXTRA_MIHOMO_TUN_STACK).orEmpty().ifBlank { "system" },
        applicationPolicy = VpnApplicationPolicy(
            mode = getIntExtra(EXTRA_PROXY_APP_LIST_MODE, ProxyAppListModeGlobal),
            packageNames = getStringArrayExtra(EXTRA_PROXY_APP_LIST_PACKAGES)?.toList().orEmpty(),
        ),
        localProxyOptions = localProxyOptions,
        appendHttpProxyOptions = VpnAppendHttpProxyOptions(
            enabled = getBooleanExtra(EXTRA_APPEND_HTTP_PROXY_ENABLED, false),
            port = getIntExtra(EXTRA_APPEND_HTTP_PROXY_PORT, localProxyOptions.port),
        ),
        coreLogPaths = MihomoCoreLogPaths(
            errorLogPath = getStringExtra(EXTRA_ERROR_LOG_PATH).orEmpty(),
        ),
        dataDir = getStringExtra(EXTRA_DATA_DIR).orEmpty(),
        hevSocks5TunnelConfig = readHevSocks5TunnelConfig(),
    )
}

private fun Intent.writeStartConfig(config: VpnServiceStartConfig) {
    putExtra(EXTRA_SESSION_NAME, config.sessionName)
    putExtra(EXTRA_MTU, config.mtu)
    putExtra(EXTRA_IPV4_ADDRESS, config.ipv4Address)
    putExtra(EXTRA_IPV4_PREFIX_LENGTH, config.ipv4PrefixLength)
    putExtra(EXTRA_IPV6_ADDRESS, config.ipv6Address)
    putExtra(EXTRA_IPV6_PREFIX_LENGTH, config.ipv6PrefixLength)
    putExtra(EXTRA_ENABLE_IPV6, config.enableIpv6)
    putExtra(EXTRA_ENABLE_LOCAL_DNS, config.enableLocalDns)
    putExtra(EXTRA_DNS_SERVERS, config.dnsServers.toTypedArray())
    putExtra(EXTRA_MIHOMO_PROFILE_PATH, config.mihomoProfilePath)
    putExtra(EXTRA_MIHOMO_PROFILE_SIGNATURE, config.mihomoProfileSignature)
    putExtra(EXTRA_AGE_SECRET_KEY, config.ageSecretKey)
    putExtra(EXTRA_MIHOMO_TUN_STACK, config.mihomoTunStack)
    putExtra(EXTRA_PROXY_APP_LIST_MODE, config.applicationPolicy.mode)
    putExtra(EXTRA_PROXY_APP_LIST_PACKAGES, config.applicationPolicy.packageNames.toTypedArray())
    putExtra(EXTRA_LOCAL_PROXY_LISTEN_ADDRESS, config.localProxyOptions.listenAddress)
    putExtra(EXTRA_LOCAL_PROXY_PORT, config.localProxyOptions.port)
    putExtra(EXTRA_LOCAL_PROXY_USERNAME, config.localProxyOptions.username)
    putExtra(EXTRA_LOCAL_PROXY_PASSWORD, config.localProxyOptions.password)
    putExtra(EXTRA_APPEND_HTTP_PROXY_ENABLED, config.appendHttpProxyOptions.enabled)
    putExtra(EXTRA_APPEND_HTTP_PROXY_PORT, config.appendHttpProxyOptions.port)
    putExtra(EXTRA_ERROR_LOG_PATH, config.coreLogPaths.errorLogPath)
    putExtra(EXTRA_DATA_DIR, config.dataDir)
    config.hevSocks5TunnelConfig?.let { hevConfig ->
        writeHevSocks5TunnelConfig(hevConfig)
    }
}

private fun Intent.readHevSocks5TunnelConfig(): HevSocks5TunnelConfig? {
    val configPath = getStringExtra(EXTRA_HEV_TUN_CONFIG_PATH) ?: return null
    val logPath = getStringExtra(EXTRA_HEV_TUN_LOG_PATH) ?: return null
    return HevSocks5TunnelConfig(
        executablePath = getStringExtra(EXTRA_HEV_TUN_EXECUTABLE_PATH).orEmpty(),
        configPath = configPath,
        pidPath = getStringExtra(EXTRA_HEV_TUN_PID_PATH).orEmpty(),
        logPath = logPath,
        socksAddress = getStringExtra(EXTRA_HEV_TUN_SOCKS_ADDRESS).orEmpty().ifBlank {
            LocalProxyLoopbackAddress
        },
        socksPort = getIntExtra(EXTRA_HEV_TUN_SOCKS_PORT, VpnDefaults.LOCAL_PROXY_PORT),
        socksUsername = getStringExtra(EXTRA_HEV_TUN_SOCKS_USERNAME).orEmpty(),
        socksPassword = getStringExtra(EXTRA_HEV_TUN_SOCKS_PASSWORD).orEmpty(),
        mtu = getIntExtra(EXTRA_HEV_TUN_MTU, VpnDefaults.MTU),
        ipv4Address = getStringExtra(EXTRA_HEV_TUN_IPV4_ADDRESS) ?: defaultIpv4TunAddress.address,
        ipv6Address = getStringExtra(EXTRA_HEV_TUN_IPV6_ADDRESS),
        tunnelName = getStringExtra(EXTRA_HEV_TUN_TUNNEL_NAME),
        enableMultiQueue = getBooleanExtra(EXTRA_HEV_TUN_MULTI_QUEUE, false),
        enableTcpFastOpen = getBooleanExtra(EXTRA_HEV_TUN_TCP_FAST_OPEN, false),
        tcpReadWriteTimeoutMillis = getIntExtra(
            EXTRA_HEV_TUN_TCP_RW_TIMEOUT,
            DefaultHevSocks5TunnelTcpReadWriteTimeoutMillis,
        ),
        udpReadWriteTimeoutMillis = getIntExtra(
            EXTRA_HEV_TUN_UDP_RW_TIMEOUT,
            DefaultHevSocks5TunnelUdpReadWriteTimeoutMillis,
        ),
        logLevel = getStringExtra(EXTRA_HEV_TUN_LOG_LEVEL).orEmpty().ifBlank {
            DefaultHevSocks5TunnelLogLevel
        },
    )
}

private fun Intent.writeHevSocks5TunnelConfig(config: HevSocks5TunnelConfig) {
    putExtra(EXTRA_HEV_TUN_EXECUTABLE_PATH, config.executablePath)
    putExtra(EXTRA_HEV_TUN_CONFIG_PATH, config.configPath)
    putExtra(EXTRA_HEV_TUN_PID_PATH, config.pidPath)
    putExtra(EXTRA_HEV_TUN_LOG_PATH, config.logPath)
    putExtra(EXTRA_HEV_TUN_SOCKS_ADDRESS, config.socksAddress)
    putExtra(EXTRA_HEV_TUN_SOCKS_PORT, config.socksPort)
    putExtra(EXTRA_HEV_TUN_SOCKS_USERNAME, config.socksUsername)
    putExtra(EXTRA_HEV_TUN_SOCKS_PASSWORD, config.socksPassword)
    putExtra(EXTRA_HEV_TUN_MTU, config.mtu)
    putExtra(EXTRA_HEV_TUN_IPV4_ADDRESS, config.ipv4Address)
    putExtra(EXTRA_HEV_TUN_IPV6_ADDRESS, config.ipv6Address)
    putExtra(EXTRA_HEV_TUN_TUNNEL_NAME, config.tunnelName)
    putExtra(EXTRA_HEV_TUN_MULTI_QUEUE, config.enableMultiQueue)
    putExtra(EXTRA_HEV_TUN_TCP_FAST_OPEN, config.enableTcpFastOpen)
    putExtra(EXTRA_HEV_TUN_TCP_RW_TIMEOUT, config.tcpReadWriteTimeoutMillis)
    putExtra(EXTRA_HEV_TUN_UDP_RW_TIMEOUT, config.udpReadWriteTimeoutMillis)
    putExtra(EXTRA_HEV_TUN_LOG_LEVEL, config.logLevel)
}

private const val EXTRA_SESSION_NAME = "session_name"
private const val EXTRA_MTU = "mtu"
private const val EXTRA_IPV4_ADDRESS = "ipv4_address"
private const val EXTRA_IPV4_PREFIX_LENGTH = "ipv4_prefix_length"
private const val EXTRA_IPV6_ADDRESS = "ipv6_address"
private const val EXTRA_IPV6_PREFIX_LENGTH = "ipv6_prefix_length"
private const val EXTRA_ENABLE_IPV6 = "enable_ipv6"
private const val EXTRA_ENABLE_LOCAL_DNS = "enable_local_dns"
private const val EXTRA_DNS_SERVERS = "dns_servers"
private const val EXTRA_MIHOMO_PROFILE_PATH = "mihomo_profile_path"
private const val EXTRA_MIHOMO_PROFILE_SIGNATURE = "mihomo_profile_signature"
private const val EXTRA_AGE_SECRET_KEY = "age_secret_key"
private const val EXTRA_MIHOMO_TUN_STACK = "mihomo_tun_stack"
private const val EXTRA_PROXY_APP_LIST_MODE = "proxy_app_list_mode"
private const val EXTRA_PROXY_APP_LIST_PACKAGES = "proxy_app_list_packages"
private const val EXTRA_LOCAL_PROXY_LISTEN_ADDRESS = "local_proxy_listen_address"
private const val EXTRA_LOCAL_PROXY_PORT = "local_proxy_port"
private const val EXTRA_LOCAL_PROXY_USERNAME = "local_proxy_username"
private const val EXTRA_LOCAL_PROXY_PASSWORD = "local_proxy_password"
private const val EXTRA_APPEND_HTTP_PROXY_ENABLED = "append_http_proxy_enabled"
private const val EXTRA_APPEND_HTTP_PROXY_PORT = "append_http_proxy_port"
private const val EXTRA_ERROR_LOG_PATH = "error_log_path"
private const val EXTRA_DATA_DIR = "data_dir"
private const val EXTRA_HEV_TUN_EXECUTABLE_PATH = "hev_tun_executable_path"
private const val EXTRA_HEV_TUN_CONFIG_PATH = "hev_tun_config_path"
private const val EXTRA_HEV_TUN_PID_PATH = "hev_tun_pid_path"
private const val EXTRA_HEV_TUN_LOG_PATH = "hev_tun_log_path"
private const val EXTRA_HEV_TUN_SOCKS_ADDRESS = "hev_tun_socks_address"
private const val EXTRA_HEV_TUN_SOCKS_PORT = "hev_tun_socks_port"
private const val EXTRA_HEV_TUN_SOCKS_USERNAME = "hev_tun_socks_username"
private const val EXTRA_HEV_TUN_SOCKS_PASSWORD = "hev_tun_socks_password"
private const val EXTRA_HEV_TUN_MTU = "hev_tun_mtu"
private const val EXTRA_HEV_TUN_IPV4_ADDRESS = "hev_tun_ipv4_address"
private const val EXTRA_HEV_TUN_IPV6_ADDRESS = "hev_tun_ipv6_address"
private const val EXTRA_HEV_TUN_TUNNEL_NAME = "hev_tun_tunnel_name"
private const val EXTRA_HEV_TUN_MULTI_QUEUE = "hev_tun_multi_queue"
private const val EXTRA_HEV_TUN_TCP_FAST_OPEN = "hev_tun_tcp_fast_open"
private const val EXTRA_HEV_TUN_TCP_RW_TIMEOUT = "hev_tun_tcp_rw_timeout"
private const val EXTRA_HEV_TUN_UDP_RW_TIMEOUT = "hev_tun_udp_rw_timeout"
private const val EXTRA_HEV_TUN_LOG_LEVEL = "hev_tun_log_level"
