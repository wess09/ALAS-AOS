# Development

## 当前阶段

本仓库是 `wess09/AzurPilot-for-Android`，App 显示名为 AzurPilot，包名为 `com.azurpilot.ghio`。ARM64 rootfs 和 debug APK 已完成首次构建，完整真机后台挂机仍待验收。旧版资料保存在 Git 历史和 `docs/roadmap-v3.md`，不作为新构建链的运行源码。

## 仓库结构

- `app/`：Kotlin/Compose Android 宿主，源码根包、namespace 与默认 applicationId 均为 `com.azurpilot.ghio`。`provision/RootfsProvisioner.kt` 解包内置 Ubuntu rootfs；`proot/ProotHost.kt` 管理单个 AzurPilot WebUI 进程及最终清场；`remote/internal/BridgeServer.kt` 连接虚拟屏截图、触控、应用控制。React WebUI 由浏览器 Custom Tab 打开，挂机页和悬浮窗继续使用回环控制接口。
- 宿主 UI 为**标准 Material 3**：色板取系统动态色（Android 12+）/M3 基线色板（其余），形状、字阶、动效一律用 `MaterialTheme` 默认 token，组件直接用 M3 现成件（`NavigationBar`、`Card`、`Button`、`Switch`、`FilterChip`、`ModalBottomSheet`、`ListItem`、`ExposedDropdownMenuBox` 等）。改 UI 时**不要再引入自绘控件或自定义圆角/字阶体系**；`theme/Theme.kt` 是全 App 唯一主题入口，`theme/DesignTokens.kt` 只放 M3 之外的间距/图标尺寸。
- `rootfs/build/build-azurpilot.sh`：原生 ARM64 Ubuntu 24.04 构建；锁定 Python 3.14.6、`uv.lock`，预构建 React，验证 OCR CPU 推理，输出随 APK 发布的 rootfs 与构建清单。
- `.github/workflows/rootfs.yml`：推送 `main` 或每小时第 7 分钟触发完整 rootfs 与 APK 构建；rootfs 上传至正式 Latest release。发布签名 Secret 齐备时上传正式 APK，否则只将 debug APK 留在 Actions 构建产物中。
- `provision/RootfsProvisioner.kt`：冷启动先部署内置包，再检查 GitHub Latest 的 rootfs；下载并校验后，在 proot 启动前原子替换，保留实例配置与日志。离线或更新失败继续使用现有 rootfs。
- `update/AppUpdateManager.kt`：App 启动时检查 APK 更新清单，下载并校验 SHA-256，随后调用系统安装器覆盖安装。
- AzurPilot Android 设备后端、控制 API 和 proot 进程兼容位于 `C:\Users\AzurLane\Desktop\Projects\AzurLaneAutoScript` 的 `dev` 分支；AOS 构建直接钉住对应提交，不维护源码覆盖补丁。
- `rootfs/seeds/`：Android deploy 配置及实例种子；`rootfs/overlays/`：单进程入口及 Android 专用进程枚举兼容层等宿主自有文件。兼容层通过虚拟环境的 `sitecustomize` 加载，不改 AP 上游跟踪文件。
- `tools/watch-android-logs.ps1`：通过 adb 实时查看 App logcat 或 proot `session.log`，支持传入设备序列号和 adb 路径。
- `devlog.md`：倒序开发流水；`debug.md`：已解决的隐性问题；`handoff/`：阶段交接；`.tmp/`：项目内临时文件。

## 运行关系

AzurPilot 的 `ProcessManager` 是调度和工具任务的唯一进程管理者。React WebUI 和仅回环可访问的 `/android` 控制路由处于同一 Python 进程。Android 宿主仅维护 proot/WebUI 存活和进程组清场。控制 API 使用每次安装独立的 token，`25548` 为新 WebUI 端口；虚拟屏桥使用 `22301`。旧版宿主的端口为 `22267/22300/22400`。启动任务前检查旧版是否正在控制同一游戏。

## 构建与测试

使用 GitHub Actions `rootfs.yml` 自动或手动构建；本机若具备原生 Linux ARM64 环境，可执行 `rootfs/build/build-azurpilot.sh`。APK 打包要求有效的 `app/app/src/main/assets/rootfs/rootfs.tar.xz` 和对应 `BUILD_MANIFEST`。缺少资产时构建失败。不可在 Android 首启执行 `uv sync`、`npm` 或原生编译。

本地 AzurPilot Python 单测使用其 `dev` 工作区现成 Python 3.14 虚拟环境执行；测试输出置于本仓 `.tmp/`。目前通过控制 API、设备桥、Android `/proc` 兼容回归及本地 OCR CPU 推理。GitHub Actions 已通过 ARM64 rootfs 和 APK 构建门禁；仍需真机完整后台挂机验证。

宿主 UI 只改 Kotlin 时可跳过 rootfs 门禁做纯编译验证（`verifyBundledAzurPilotRuntime` 会被 `packageDebugResources` 拖进依赖图）：

```
JAVA_HOME="<Android Studio>/jbr" ./gradlew :app:compileDebugKotlin --offline -x verifyBundledAzurPilotRuntime
```

`:app:testDebugUnitTest` **当前不可用**：`app/src/test` 下仍留有上游 fork 时期的孤儿测试（`project/`、`runner/`、`session/`、`schedule/`、`telemetry/`、`notification/` 等），对应主源码已在阶段二删除，编译测试源集必然失败，详见 `debug.md`。UI 改动靠上述编译 + 真机走查验收。

## 重要约束

- AzurPilot Android 适配作为其 `dev` 分支正式源码维护；AOS 只钉 commit 和打包运行环境。
- 不修改历史上游源码或归档。新包不得访问或迁移旧版应用私有目录。
- 不擅自执行 push、release。真机虚拟屏实验必须按 `AGENTS.md` 完成前后手势窗口归属检查与清场；屏幕状态实验需用户确认。
