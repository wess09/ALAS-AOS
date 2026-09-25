# AzurPilot for Android

<p align="center"><img src="app/app/src/main/res/drawable-nodpi/azurpilot_android_logo.png" alt="AzurPilot for Android 标志" width="280"></p>

[![License](https://img.shields.io/github/license/wess09/AzurPilot-for-Android?style=flat-square&color=4a90d9)](./LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%209%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/wess09/AzurPilot-for-Android)
[![API](https://img.shields.io/badge/minSdk-28-green?style=flat-square)](https://developer.android.com/google/play/requirements/target-sdk)
[![Commit Activity](https://img.shields.io/github/commit-activity/m/wess09/AzurPilot-for-Android?style=flat-square&color=00d4aa)](https://github.com/wess09/AzurPilot-for-Android/commits)
[![Stars](https://img.shields.io/github/stars/wess09/AzurPilot-for-Android?style=flat-square&color=ffca28)](https://github.com/wess09/AzurPilot-for-Android/stargazers)

AzurPilot for Android 让《碧蓝航线》的自动化助手 **AzurPilot** 直接跑在手机上。

它把一套完整的 Ubuntu Runtime（Python + AzurPilot + OCR 识别模型 + 控制台前端）打包进 APK，安装后自动部署到应用私有目录，通过 proot 在免 root 的前提下运行。游戏画面跑在后台虚拟屏上，主屏照常使用；任务配置、计划任务、运行日志都在手机里完成，不需要电脑、不需要常驻服务器。

## 功能特性

- **免 root 一键安装** — 完整Runtime内置在安装包里，装完即可用；不需要 Root，也不需要自己准备 Python、依赖或模型。
- **后台虚拟屏挂机** — 游戏运行在后台虚拟屏，主屏可以正常刷视频、聊天，挂机不被打断。
- **悬浮窗控制面板** — App 切到后台也能一键启停挂机、查看调度器状态与实时日志。
- **内嵌 AzurPilot 控制台** — 完整的 WebUI 就在手机里，任务配置、计划任务、实例管理一应俱全。
- **任务与定时** — 首页、任务、定时、设置四个页面覆盖日常挂机所需；定时任务支持到点自动开跑。
- **日志中心** — 启动器日志与 AzurPilot 日志分开呈现，出错现场能直接回看自动保存的游戏截图；支持一键导出，提 issue 时直接发压缩包。
- **自动清理日志** — 默认清理 7 天前的日志与截图，长期挂机不必担心存储被慢慢吃掉。
- **Runtime与 App 分开更新** — Runtime和 App 各自检查版本，更新Runtime不会动你的配置和日志。

## 使用条件

- Android 9（API 28）及以上的 **ARM64** 设备。
- 约 2 GB 可用内部存储空间。
- 可用的 Shizuku-m 服务，或具备 Root 权限的设备（App 内会引导授权与切换后端）。
- 首次安装和检查更新时需要网络连接；运行中的本机控制接口仅监听回环地址。

## 安装与启动

1. 从本仓库的 [Releases 最新版本](https://github.com/wess09/AzurPilot-for-Android/releases/latest) 下载正式签名 APK。
2. 安装后打开 AzurPilot，等待内置Runtime部署完成；首次解压可能需要数分钟（首次安装请选**完整版 APK**，其中包含Runtime）。
3. 按页面提示连接 Shizuku-m，或在设置中选择 Root 后端。
4. 在挂机页检查设备画面与状态，再打开 AzurPilot 控制台配置实例和任务。

## 页面说明

| 页面 | 用途 |
| --- | --- |
| 首页 | 项目与设备状态总览、诊断信息 |
| 任务 | 任务列表编排、实时画面预览、全屏手动模式 |
| 定时 | 计划任务的时间与触发规则 |
| AzurPilot | 打开内置控制台（WebUI），做完整配置 |
| 设置 | 任务 / 资源 / 显示 / 定时任务设置，Runtime与 App 更新、日志、关于 |

悬浮窗可从 App 内唤起，用于在任意界面启停挂机、查看状态与日志。

## 更新机制

App 冷启动时会读取 GitHub **Latest** release 的 `latest.json`。发现新的Runtime时，会先询问，确认后才下载并核对大小与 SHA-256，然后在启动 AzurPilot 进程前替换Runtime；用户实例配置与日志会保留，选择稍后则继续使用当前版本。网络或下载失败时同样沿用当前版本，不会卡住启动。

更新分两条通道：**Runtime（rootfs）** 通过 Latest release 分发，**App 本体** 只按宿主版本号判断。发现新 App 时会提示下载，校验完成后交给 Android 系统安装器完成覆盖安装；日常 App 更新使用不含Runtime的轻量 APK，手机上已有的Runtime继续复用。

## 开发

- Android 工程：[`app/`](app/)（Kotlin + Compose Material 3）
- Runtime构建脚本：[`rootfs/build/build-azurpilot.sh`](rootfs/build/build-azurpilot.sh)
- 持续集成：[`rootfs.yml`](.github/workflows/rootfs.yml)（推送 `main` 与每小时第 7 分钟触发；Ubuntu ARM64 runner 构建并验证 rootfs，另一台 runner 编译 APK）
- 项目结构与本地验证方式：[`development.md`](development.md)
- 宿主 UI 使用 Kotlin 与 Compose Material 3；AzurPilot Python 与 React 前端由 rootfs 构建步骤预先打包，手机端不运行 `uv sync`、`npm` 或原生编译。
- Android 依赖版本见 [`libs.versions.toml`](app/gradle/libs.versions.toml)。

正式 APK 的发布签名使用仓库 Actions Secrets 中的 `AZURPILOT_ANDROID_KEYSTORE_BASE64`、`AZURPILOT_ANDROID_KEYSTORE_PASSWORD`、`AZURPILOT_ANDROID_KEY_ALIAS` 与 `AZURPILOT_ANDROID_KEY_PASSWORD`；签名密钥保存在维护者本机（不入库），丢失密钥会使后续 APK 无法覆盖安装既有正式版。

## 许可证

本项目以 [AGPL-3.0](LICENSE) 发布。随项目一同分发的其他授权文本见：

- [`LICENSE-ALAS-AOS`](LICENSE-ALAS-AOS) — AGPL-3.0
- [`LICENSE-MaaFwApp`](LICENSE-MaaFwApp) — AGPL-3.0
- [`LICENSE-AzurPilot`](LICENSE-AzurPilot) — GPL-3.0

应用内「关于」页同样列出主要开源组件、许可证与项目链接。

## 致谢

本项目站在许多优秀开源项目的肩膀上。以下按使用位置列出主要依赖。

### 项目与授权来源

| 项目 | 许可证 | 说明 |
| --- | --- | --- |
| [ALAS-AOS](https://github.com/Shinarin/ALAS-AOS) | AGPL-3.0 | 本项目 Android 化路线的起点与重要参考 |
| MaaFwApp | AGPL-3.0 | Android 宿主工程基线 |
| [AzurPilot](https://github.com/wess09/AzurPilot) | GPL-3.0 | 自动化引擎本体，随Runtime打包分发 |

### Runtime（rootfs）

**基础系统与容器**

| 组件 | 许可证 |
| --- | --- |
| Ubuntu Base 24.04 (ARM64) | 各组件的原许可 |
| [PRoot](https://github.com/proot-me/proot) 及配套运行库（libproot、libproot-loader、libtalloc、libandroid-selinux、libandroid-shmem、shim） | GPL-2.0 / LGPL-3.0 / Apache-2.0 |
| BusyBox | GPL-2.0 |
| [uv](https://github.com/astral-sh/uv) | Apache-2.0 / MIT |
| CPython 3.14 | PSF-2.0 |

**AzurPilot Python 依赖**

numpy、scipy、pillow、opencv-python、imageio、imageio-ffmpeg、adbutils、uiautomator2、uiautomator2cache、jellyfish、pyyaml、inflection、starlette、anyio、uvicorn、aiohttp、aiortc、pypresence、rich、zerorpc、pyzmq、onepush、psutil、matplotlib、pycryptodome、pydantic、rapidocr、ncnn、onnxruntime、numba、lz4、packaging、openai、mcp、luaparser

**控制台前端**

react、react-dom、react-router-dom、vite、typescript、echarts、motion、lucide-react、CodeMirror（`@codemirror/*`、`@lezer/highlight`）、liquid-glass-react

**随Runtime分发的其他第三方许可证**

| 组件 | 许可证 |
| --- | --- |
| MaaTouch | LGPL-3.0 |
| DroidCast | Apache-2.0 |
| scrcpy / ws-scrcpy | GPL-3.0 |
| JetBrains Mono、LXGW WenKai Mono | OFL-1.1 |
| MiSans | 小米字体许可 |
| hermit | GPL-3.0 |

以上各组件的许可证原文，连同 Python 依赖的全部传递依赖，共 152 项，随Runtime分发于设备内 `/opt/azurpilot/licenses`。

### Android 宿主

| 组件 | 许可证 |
| --- | --- |
| AndroidX：core-ktx、core-splashscreen、appcompat、activity-compose、lifecycle、compose-bom、material3、material-icons、navigation-compose、datastore、window、browser | Apache-2.0 |
| [OkHttp](https://github.com/square/okhttp) | Apache-2.0 |
| [Koin](https://github.com/InsertKoinIO/koin) | Apache-2.0 |
| [Timber](https://github.com/JakeWharton/timber) | Apache-2.0 |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | Apache-2.0 |
| [Shizuku](https://github.com/RikkaApps/Shizuku) | Apache-2.0 |
| [libsu](https://github.com/topjohnwu/libsu) | Apache-2.0 |
| [XXPermissions](https://github.com/getActivity/XXPermissions) | Apache-2.0 |
| [FloatingX](https://github.com/petterpx/FloatingX) | Apache-2.0 |
| [Apache Commons Compress](https://github.com/apache/commons-compress) | Apache-2.0 |
| [XZ for Java](https://tukaani.org/xz/java.html) | Public Domain |
| [SnakeYAML Engine](https://bitbucket.org/snakeyaml/snakeyaml-engine) | Apache-2.0 |
| Kotlin / KotlinPoet / KSP / Android Gradle Plugin（构建期） | Apache-2.0 |
| JUnit、MockK、Espresso（测试） | EPL-1.0 / Apache-2.0 |

### 特别感谢

- [Shizuku-m](https://github.com/Shinarin/shizuku-m) — 免无线调试、无 WLAN 也能启动的 Shizuku 分支，让免 root 授权在更多设备上可用。
- 所有在 `devlog.md`、`debug.md` 中留下踩坑记录与验证结论的贡献者。
