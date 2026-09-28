English | [简体中文](README_zh_CN.md)

# AsteriskMETA

An Android Mihomo GUI client.

## Telegram Channel

[Asterisk4Magisk](https://t.me/Asterisk4Magisk)

## Run Modes

### VPN Service

- Works without root permission.
- Uses Android `VpnService`.

### TPROXY(ROOT)

- Runs the local Mihomo executable directly with libsu.
- Uses iptables and policy routing for transparent proxy traffic.

### TUN(ROOT)

- Runs the local Mihomo executable directly with libsu.
- Uses `auto-route` and `auto-redirect` in the Mihomo TUN inbound to manage routing.

### TUN2SOCKS(ROOT)

- Runs the local Mihomo executable directly with libsu.
- Uses `hev-socks5-tunnel` to create a TUN device and send traffic to the Mihomo SOCKS5 inbound.

### BPF2SOCKS(ROOT)

- Runs the local Mihomo executable directly with libsu.
- Uses `bpf2socks` to capture traffic and send it to the Mihomo SOCKS5 inbound.
- Availability depends on eBPF support in the device kernel.

### asteriskd

- Watches local IPv4/IPv6 addresses and tethering interfaces, then refreshes the relevant iptables rules or BPF maps.
- Cleans up networking rules owned by the active ROOT mode when the service stops.

## Resource Files

- ROOT runtime files are stored in the app-private `files/clash` directory.
- Custom resources can be added or replaced locally and updated from configured URLs.

## Broadcast Control

Enable **Broadcast Control** in settings, then send an explicit broadcast to the receiver below. Actions use the `org.asterisk.zcc.ameta.action.` prefix.

| Operation | Action suffix |
| --- | --- |
| Start proxy | `PROXY_START` |
| Stop proxy | `PROXY_STOP` |
| Toggle proxy | `PROXY_TOGGLE` |
| Update all URL subscriptions | `SUBSCRIPTION_UPDATE` |
| Cancel broadcast subscription update | `SUBSCRIPTION_UPDATE_CANCEL` |
| Update all resources | `RESOURCE_UPDATE` |
| Cancel resource updates | `RESOURCE_UPDATE_CANCEL` |

```sh
adb shell am broadcast -n org.asterisk.zcc.ameta/features.automation.BroadcastControlReceiver -a org.asterisk.zcc.ameta.action.SUBSCRIPTION_UPDATE
```

Subscription updates skip local entries; cancellation preserves completed results and scheduled update settings. Resources use the current Resource Management configuration; resource cancellation also clears its shared queue. Repeated update commands of the same kind are merged while running.

Updates run in the background without starting the proxy. Broadcast delivery does not mean the update has finished; check the `BroadcastControl` app logs for results.

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

The build downloads the pinned AndroidLibClashLite AAR, builds the native submodules, and produces ABI split APKs plus a universal APK.

If Gradle cannot find the Android NDK, configure it through Android Studio, `ndk.dir` in `local.properties`, or `ANDROID_NDK_HOME`.

## License

[GPL-3.0](LICENSE)

## Credits

- [@MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo)
- [@MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)
- [@Asterisk4Magisk/AndroidLibClashLite](https://github.com/Asterisk4Magisk/AndroidLibClashLite)
- [@heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- [@topjohnwu/libsu](https://github.com/topjohnwu/libsu)
- [@android/material3](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [@MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat)
- [@mayaxcn/china-ip-list](https://github.com/mayaxcn/china-ip-list)
- [@xchacha20-poly1305/husi](https://github.com/xchacha20-poly1305/husi) — reference implementation for the per-app proxy "Scan Chinese apps" feature
