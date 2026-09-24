# Development

## 当前阶段

正在将 ALAS-AOS 转为独立的 AzurPilot Android App。ARM64 rootfs 和 debug APK 已完成首次构建，当前依据真机日志修复运行时兼容问题，完整后台挂机尚未验收。旧版 v0.1.4 与其历史路线图保存在 Git 历史、`docs/roadmap-v3.md` 和 `m0-archive/`，不作为新构建链的运行源码。

## 仓库结构

- `app/`：Kotlin/Compose Android 宿主。`provision/RootfsProvisioner.kt` 解包内置 Ubuntu rootfs；`proot/ProotHost.kt` 管理单个 AzurPilot WebUI 进程及最终清场；`remote/internal/BridgeServer.kt` 连接虚拟屏截图、触控、应用控制。React WebUI 由浏览器 Custom Tab 打开，挂机页和悬浮窗继续使用回环控制接口。
- `rootfs/build/build-azurpilot.sh`：原生 ARM64 Ubuntu 24.04 构建；锁定 Python 3.14.6、`uv.lock`，预构建 React，验证 OCR CPU 推理，输出随 APK 发布的 rootfs 与构建清单。
- `.github/workflows/rootfs.yml`：由 AOS 每 15 分钟轮询 AzurPilot `dev`；新 commit 通过门禁后构建固定签名 APK，更新 `azurpilot-android-dev` release 和 App 更新清单。
- `update/AppUpdateManager.kt`：App 启动时检查 APK 更新清单，下载并校验 SHA-256，随后调用系统安装器覆盖安装。
- AzurPilot Android 设备后端、控制 API 和 proot 进程兼容位于 `C:\Users\AzurLane\Desktop\Projects\AzurLaneAutoScript` 的 `dev` 分支；AOS 构建直接钉住对应提交，不维护源码覆盖补丁。
- `rootfs/seeds/`：Android deploy 配置及实例种子；`rootfs/overlays/`：单进程入口和兼容性检查、原子切换、失败回滚脚本。
- `tools/watch-android-logs.ps1`：通过 adb 实时查看 App logcat 或 proot `session.log`，支持传入设备序列号和 adb 路径。
- `devlog.md`：倒序开发流水；`debug.md`：已解决的隐性问题；`handoff/`：阶段交接；`.tmp/`：项目内临时文件。

## 运行关系

AzurPilot 的 `ProcessManager` 是调度和工具任务的唯一进程管理者。React WebUI 和仅回环可访问的 `/android` 控制路由处于同一 Python 进程。Android 宿主仅维护 proot/WebUI 存活和进程组清场。控制 API 使用每次安装独立的 token，`25548` 为新 WebUI 端口；虚拟屏桥使用 `22301`。旧版 ALAS-AOS 的端口为 `22267/22300/22400`。启动任务前检查旧版是否正在控制同一游戏。

## 构建与测试

使用 GitHub Actions `rootfs.yml` 手动构建；本机若具备原生 ARM64 runner，可执行 `rootfs/build/build-azurpilot.sh`。APK 打包要求有效的 `app/app/src/main/assets/rootfs/rootfs.tar.xz` 和对应 `BUILD_MANIFEST`。缺少资产时构建失败。不可在 Android 首启执行 `uv sync`、`npm` 或原生编译。

本地 AzurPilot Python 单测使用其 `dev` 工作区现成 Python 3.14 虚拟环境执行；测试输出置于本仓 `.tmp/`。目前通过控制 API、设备桥、Android `/proc` 兼容回归及本地 OCR CPU 推理。GitHub Actions 已通过 ARM64 rootfs 和 APK 构建门禁；仍需真机完整后台挂机验证。

## 重要约束

- AzurPilot Android 适配作为其 `dev` 分支正式源码维护；AOS 只钉 commit 和打包运行环境。
- 不修改历史 ALAS 上游源码或归档。新包不得访问或迁移旧版应用私有目录。
- 不擅自执行 push、release。真机虚拟屏实验必须按 `AGENTS.md` 完成前后手势窗口归属检查与清场；屏幕状态实验需用户确认。
