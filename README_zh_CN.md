[English](README.md) | 简体中文

# AsteriskMETA

一个 Android Mihomo GUI 客户端。

## Telegram 频道

[Asterisk4Magisk](https://t.me/Asterisk4Magisk)

## 运行模式

### VPN Service

- 无需 root 权限。
- 使用 Android `VpnService`。

### TPROXY(ROOT)

- 通过 libsu 直接运行本地 Mihomo 可执行文件。
- 使用 iptables 和策略路由处理透明代理流量。

### TUN(ROOT)

- 通过 libsu 直接运行本地 Mihomo 可执行文件。
- 使用 Mihomo TUN 入站的 `auto-route` 和 `auto-redirect` 管理路由。

### TUN2SOCKS(ROOT)

- 通过 libsu 直接运行本地 Mihomo 可执行文件。
- 使用 `hev-socks5-tunnel` 创建 TUN 设备并将流量送入 Mihomo SOCKS5 入站

### BPF2SOCKS(ROOT)

- 通过 libsu 直接运行本地 Mihomo 可执行文件。
- 使用 `bpf2socks` 劫持流量并送入 Mihomo SOCKS5 入站。
- 是否可用取决于设备内核 eBPF 支持情况。

### asteriskd

- 监听本地 IPv4/IPv6 地址和热点接口变化，并刷新相应的 iptables 规则或 BPF map。
- 服务停止时清理当前 ROOT 模式负责的网络规则。

## 资源文件

- ROOT 运行文件存储在应用私有的 `files/clash` 目录。
- 自定义资源可在本地添加或替换，也可通过配置的 URL 更新。

## 广播控制

在设置中开启 **广播控制** 后，显式指定以下接收器发送广播。Action 前缀为 `org.asterisk.zcc.ameta.action.`。

| 操作 | Action 后缀 |
| --- | --- |
| 启动代理 | `PROXY_START` |
| 停止代理 | `PROXY_STOP` |
| 切换代理启停 | `PROXY_TOGGLE` |
| 更新全部 URL 订阅 | `SUBSCRIPTION_UPDATE` |
| 取消广播订阅更新 | `SUBSCRIPTION_UPDATE_CANCEL` |
| 更新全部资源 | `RESOURCE_UPDATE` |
| 取消资源更新 | `RESOURCE_UPDATE_CANCEL` |

```sh
adb shell am broadcast -n org.asterisk.zcc.ameta/features.automation.BroadcastControlReceiver -a org.asterisk.zcc.ameta.action.SUBSCRIPTION_UPDATE
```

订阅更新跳过本地项，取消时保留已完成结果及定时更新配置。资源更新沿用资源管理中的当前配置，取消资源更新会同时清空其共享队列。同类更新执行期间，重复命令会合并。

更新在后台执行，不会启动代理。广播送达不代表更新完成，结果请查看应用日志的 `BroadcastControl` 标签。

## 开发

构建前初始化 submodule：

```bash
git submodule update --init --recursive
```

使用 Android Studio 打开项目根目录，或通过 Gradle wrapper 构建：

```powershell
.\gradlew.bat assembleDebug
```

macOS 或 Linux：

```bash
./gradlew assembleDebug
```

构建会下载固定版本的 AndroidLibClashLite AAR，构建 native submodule，并生成 ABI split APK 和 universal APK。

如果 Gradle 找不到 Android NDK，请通过 Android Studio、`local.properties` 中的 `ndk.dir` 或 `ANDROID_NDK_HOME` 配置。

## 许可

[GPL-3.0](LICENSE)

## 致谢

- [@MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo)
- [@MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)
- [@Asterisk4Magisk/AndroidLibClashLite](https://github.com/Asterisk4Magisk/AndroidLibClashLite)
- [@heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- [@topjohnwu/libsu](https://github.com/topjohnwu/libsu)
- [@android/material3](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [@MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat)
- [@mayaxcn/china-ip-list](https://github.com/mayaxcn/china-ip-list)
- [@xchacha20-poly1305/husi](https://github.com/xchacha20-poly1305/husi) — 分应用代理“扫描中国应用”功能参考实现
