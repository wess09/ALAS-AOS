# AzurPilot for Android

<p align="center"><img src="app/app/src/main/res/drawable-nodpi/azurpilot_android_logo.png" alt="AzurPilot for Android 标志" width="280"></p>

AzurPilot 的 Android 宿主。它在手机内部运行 ARM64 Ubuntu 环境和 [AzurPilot](https://github.com/wess09/AzurPilot)，通过 Shizuku 或 Root 控制游戏，并用浏览器打开本机 WebUI。App 包名为 `com.azurpilot.ghio`，桌面显示名为 **AzurPilot**。

> 当前仍处于验证阶段：rootfs 和 APK 构建链已跑通，完整真机后台挂机尚未完成验收。

## 使用条件

- Android 9（API 28）及以上的 ARM64 设备；rootfs 不支持 x86 或 32 位 ARM。
- 约 2 GB 可用内部存储空间，用于首次部署和后续完整 rootfs 更新。
- 可用的 Shizuku-m 服务，或具备 Root 权限的设备。App 内会引导授权与切换后端。
- 首次安装和检查更新时需要网络连接；运行中的本机控制接口仅监听回环地址。

## 安装与启动

1. 从本仓库的 [Releases 最新版本](https://github.com/wess09/AzurPilot-for-Android/releases/latest)下载正式签名 APK。
2. 安装后打开 AzurPilot，等待内置运行环境部署完成；首次解压可能需要数分钟。
3. 按页面提示连接 Shizuku-m，或在设置中选择 Root 后端。
4. 在挂机页检查设备画面与状态，再打开 AzurPilot WebUI 配置实例和任务。

更换为 `com.azurpilot.ghio` 后，Android 会把它视为一个新应用。旧包的数据不会自动迁移；调试签名 APK 也不能直接覆盖安装将来的正式签名 APK。

## 更新机制

App 冷启动时会读取 GitHub **Latest** release 的 `latest.json`。有新 rootfs 时，它先下载并核对大小与 SHA-256，再在启动 AzurPilot 进程前替换运行环境；用户实例配置与日志会保留。网络或下载失败时继续使用当前版本。设置页可单独查询 Latest 的运行时版本；发现新版本后重启 App 应用更新。若 Latest 同时有新 APK，App 会提示下载，校验后交给 Android 系统安装器完成覆盖安装。

工作流在推送 `main` 和每小时第 7 分钟触发。Ubuntu ARM64 runner 构建并验证 rootfs，另一台 Ubuntu runner 编译 APK；四项发布签名 Secret 齐备时生成正式签名 APK，否则生成调试 APK 供 Actions 下载，并只向 Latest 发布 rootfs。GitHub 托管的 ARM Mac runner 规格低于当前公开仓库的 Ubuntu runner，而且 rootfs 脚本依赖 Linux 的 `chroot` 与 bind mount，因此构建保留在 Linux。

正式 APK 发布使用仓库 Actions Secrets 中的 `AZURPILOT_ANDROID_KEYSTORE_BASE64`、`AZURPILOT_ANDROID_KEYSTORE_PASSWORD`、`AZURPILOT_ANDROID_KEY_ALIAS` 和 `AZURPILOT_ANDROID_KEY_PASSWORD`。签名密钥保存在维护者本机的 `keystore/`（不入库）；丢失密钥会使后续 APK 无法覆盖安装既有正式版。

## 开发

- Android 工程：[`app/`](app/)
- rootfs 构建脚本：[`rootfs/build/build-azurpilot.sh`](rootfs/build/build-azurpilot.sh)
- GitHub Actions：[`rootfs.yml`](.github/workflows/rootfs.yml)
- 项目结构和本地验证：[`development.md`](development.md)

宿主 UI 使用 Kotlin 与 Compose Material 3。AzurPilot Python 与 React 由 rootfs 构建步骤预先打包，手机端不运行 `uv sync`、`npm` 或原生编译。

本项目以 [AGPL-3.0](LICENSE) 发布，基于 [ALAS-AOS](https://github.com/Shinarin/ALAS-AOS) 改造。应用内「关于」列出主要开源组件、许可证及项目链接；Android 依赖版本见 [`libs.versions.toml`](app/gradle/libs.versions.toml)。AzurPilot 来源与相应许可证见其[上游仓库](https://github.com/wess09/AzurPilot)。
