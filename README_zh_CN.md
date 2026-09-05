[English](README.md) | 简体中文

# AsteriskMETA

一个 Android Mihomo GUI 客户端，使用 [Mihomo](https://github.com/MetaCubeX/mihomo)、[CMFA Mihomo wrapper](https://github.com/MetaCubeX/ClashMetaForAndroid/tree/main/core) 和 [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) 实现。

## Telegram 频道

[Asterisk4Magisk](https://t.me/Asterisk4Magisk)

## 功能

- VPN Service、TPROXY(ROOT)、TUN(ROOT)、TUN2SOCKS(ROOT) 和 BPF2SOCKS(ROOT) 运行模式
- 通过二维码、本地文件或 URL 订阅导入并管理 Mihomo 配置
- 支持 JavaScript 覆写脚本，用于进阶配置调整
- 配置、代理、连接、日志和资源管理
- Material 3 Compose UI

## 预览

<p align="center">
  <img src="image/screenshot/1.jpg" width="24%" alt="截图 1" />
  <img src="image/screenshot/2.jpg" width="24%" alt="截图 2" />
  <img src="image/screenshot/3.jpg" width="24%" alt="截图 3" />
  <img src="image/screenshot/4.jpg" width="24%" alt="截图 4" />
</p>

## 运行模式

### VPN Service

- 无需 root 权限。
- 使用 Android `VpnService`。
- 通过 CMFA bridge 在应用进程中运行 Mihomo。

### TPROXY(ROOT)

- 通过 libsu 直接运行本地 Mihomo 可执行文件。
- 使用 TPROXY listener、iptables 和策略路由处理透明代理流量。

### TUN(ROOT)

- 通过 libsu 直接运行本地 Mihomo 可执行文件。
- 托管配置使用固定 TUN 设备 `asterisk0`，并由 Mihomo 的 `auto-route`、`auto-detect-interface` 和 `auto-redirect` 管理路由。
- 支持配置 Mihomo TUN 栈。
- 所选 IP CIDR 规则集会写入 `route-exclude-address-set`，域名规则不生效。
- 可加入准确的下游接口名以接管热点和网络共享流量。

### TUN2SOCKS(ROOT)

- 通过 libsu 直接运行本地 Mihomo 可执行文件。
- 使用 `hev-socks5-tunnel` 创建固定 TUN 设备 `asterisk0`。
- 将隧道流量送入本地 Mihomo SOCKS5 listener。

### BPF2SOCKS(ROOT)

- 通过 libsu 直接运行本地 Mihomo 可执行文件和 native `bpf2socks` helper。
- 使用 eBPF 接管 TCP、UDP 流量并送入本地 Mihomo SOCKS5 listener，不创建 TUN 设备。
- 默认 bridge 端口为 `65532`，SOCKS5 listener 端口为 `65534`。
- 启动前要求 eBPF probe 通过。设备支持不足时，该模式无法启动。

### asteriskd

- 监听本地 IPv4/IPv6 地址和热点接口变化，并刷新相应的 iptables 规则或 BPF map。
- 服务停止时清理当前 ROOT 模式负责的网络规则。

## 资源文件

- 运行文件存储在应用私有的 `files/clash` 目录。
- 内置 Mihomo 可执行文件可在资源管理中替换。
- 自定义资源可在本地添加或替换，也可通过配置的 URL 更新。

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

构建会准备 Mihomo 和 CMFA Go core，构建已配置的 native helper submodule，并生成 ABI split APK 和 universal APK。

如果 Gradle 找不到 Android NDK，请通过 Android Studio、`local.properties` 中的 `ndk.dir` 或 `ANDROID_NDK_HOME` 配置。

## WSA

```bash
appops set org.asterisk.zcc.ameta ACTIVATE_VPN allow
```

## 许可

[GPL-3.0](LICENSE)

## 致谢

- [@MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo)
- [@MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)
- [@heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- [@topjohnwu/libsu](https://github.com/topjohnwu/libsu)
- [@android/material3](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [@MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat)
- [@mayaxcn/china-ip-list](https://github.com/mayaxcn/china-ip-list)
