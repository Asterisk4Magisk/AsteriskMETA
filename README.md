English | [简体中文](README_zh_CN.md)

# AsteriskMETA

An Android Mihomo GUI client powered by [Mihomo](https://github.com/MetaCubeX/mihomo), [CMFA Mihomo wrapper](https://github.com/MetaCubeX/ClashMetaForAndroid/tree/main/core), and [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel).

## Telegram Channel

[Asterisk4Magisk](https://t.me/Asterisk4Magisk)

## Features

- VPN Service, TPROXY(ROOT), TUN(ROOT), TUN2SOCKS(ROOT), and BPF2SOCKS(ROOT) run modes
- Import and manage Mihomo configurations from QR codes, local files, or URL subscriptions
- JavaScript override scripts for advanced configuration changes
- Profile, proxy, connection, log, and resource management
- Material 3 Compose UI

## Screenshots

<p align="center">
  <img src="image/screenshot/5.jpg" width="24%" alt="Screenshot 1" />
  <img src="image/screenshot/6.jpg" width="24%" alt="Screenshot 2" />
  <img src="image/screenshot/7.jpg" width="24%" alt="Screenshot 3" />
  <img src="image/screenshot/8.jpg" width="24%" alt="Screenshot 4" />
</p>

## Run Modes

### VPN Service

- Works without root permission.
- Uses Android `VpnService`.
- Runs Mihomo in the app process through the CMFA bridge.

### TPROXY(ROOT)

- Runs the local Mihomo executable directly with libsu.
- Uses a TPROXY listener with iptables and policy routing for transparent proxy traffic.

### TUN(ROOT)

- Runs the local Mihomo executable directly with libsu.
- Managed configurations use the fixed TUN device `asterisk0` with Mihomo-managed `auto-route`, `auto-detect-interface`, and `auto-redirect`.
- Supports configurable Mihomo TUN stacks.
- Selected IP CIDR rule sets are passed to `route-exclude-address-set`; domain rules do not apply.
- Exact downstream interface names can be included for hotspot and tethering traffic.

### TUN2SOCKS(ROOT)

- Runs the local Mihomo executable directly with libsu.
- Uses `hev-socks5-tunnel` to create the fixed TUN device `asterisk0`.
- Sends tunnel traffic to a local Mihomo SOCKS5 listener.

### BPF2SOCKS(ROOT)

- Runs the local Mihomo executable and native `bpf2socks` helper directly with libsu.
- Uses eBPF without creating a TUN device and sends captured TCP and UDP traffic to a local Mihomo SOCKS5 listener.
- Defaults to bridge port `65532` and SOCKS5 listener port `65534`.
- Requires the eBPF probe to pass before startup. Devices with insufficient support cannot start this mode.

### asteriskd

- Watches local IPv4/IPv6 addresses and tethering interfaces, then refreshes the relevant iptables rules or BPF maps.
- Cleans up networking rules owned by the active ROOT mode when the service stops.

## Resource Files

- Runtime files are stored in the app-private `files/clash` directory.
- The bundled Mihomo executable can be replaced from Resource Management.
- Custom resources can be added or replaced locally and updated from configured URLs.

## Development

Initialize submodules before building:

```bash
git submodule update --init --recursive
```

Open the project root in Android Studio, or build it with Gradle wrapper:

```powershell
.\gradlew.bat assembleDebug
```

On macOS or Linux:

```bash
./gradlew assembleDebug
```

The build prepares Mihomo and the CMFA Go core, builds the configured native helper submodules, and produces ABI split APKs plus a universal APK.

If Gradle cannot find the Android NDK, configure it through Android Studio, `ndk.dir` in `local.properties`, or `ANDROID_NDK_HOME`.

## WSA

```bash
appops set org.asterisk.zcc.ameta ACTIVATE_VPN allow
```

## License

[GPL-3.0](LICENSE)

## Credits

- [@MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo)
- [@MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)
- [@heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- [@topjohnwu/libsu](https://github.com/topjohnwu/libsu)
- [@android/material3](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [@MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat)
- [@mayaxcn/china-ip-list](https://github.com/mayaxcn/china-ip-list)
