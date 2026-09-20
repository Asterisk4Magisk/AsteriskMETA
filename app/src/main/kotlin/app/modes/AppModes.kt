// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app.modes

const val RunModeVpnService = 0
const val RunModeTproxy = 1
const val RunModeTun2Socks = 2
const val RunModeTun = 3
const val RunModeBpf2Socks = 4

fun Int.isRootRunMode(): Boolean {
    return this == RunModeTproxy ||
        this == RunModeTun ||
        this == RunModeTun2Socks ||
        this == RunModeBpf2Socks
}

const val MihomoModeRule = 0
const val MihomoModeGlobal = 1
const val MihomoModeDirect = 2

const val MihomoTunStackSystem = 0
const val MihomoTunStackGvisor = 1
const val MihomoTunStackMixed = 2
const val MihomoTunStackMips = 3

const val ProxyAppListModeBlacklist = 0
const val ProxyAppListModeWhitelist = 1
const val ProxyAppListModeGlobal = 2

// Interception itself always covers every application, because the platform
// resolver answers for all of them at once. The scope only decides whether the
// applications the list leaves out are kept working: with the application policy
// the daemon connects the fake addresses they receive for them, and globally they
// are left to the answer they got.
const val DnsHijackScopeAllApps = 0
const val DnsHijackScopeProxyApps = 1

const val MihomoProxyLayoutAuto = 0
const val MihomoProxyLayoutSingle = 1
const val MihomoProxyLayoutDouble = 2
const val MihomoProxyLayoutMultiple = 3

const val MihomoProxySortDefault = 0
const val MihomoProxySortName = 1
const val MihomoProxySortDelay = 2
