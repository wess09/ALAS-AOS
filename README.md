# AzurPilot Android

本仓库正在将原 ALAS-AOS 改造为独立的 AzurPilot Android App。新包名为 `io.github.shinarin.azurpilotandroid`，使用独立私有数据目录，可与旧版 ALAS-AOS 并存；旧配置不会自动迁移。**当前尚未完成 ARM64 构建和真机后台挂机验收，请勿将源码状态当作可安装成品。**

## 目标功能

- Shizuku 虚拟屏承载 1280×720 的游戏画面；本机桥提供截图、触控和应用控制。
- AzurPilot React WebUI、挂机页和悬浮窗共用同一个 `ProcessManager`，支持调度与工具任务、状态和日志。
- WebUI 使用回环端口 `25548`，特权桥使用 `22301`，与旧版端口分离。
- APK 内置 Python 3.14、锁定依赖、OCR 模型和预构建的 React 页面；手机首启仅部署与校验。
- 更新包以同一 AzurPilot commit 的源码、前端及兼容清单组成；不兼容时保留当前版本并提示升级 APK。

## 架构

```text
Android App（挂机页 / 悬浮窗 / WebView）
    │ 回环控制 API :25548
AzurPilot WebUI + RuntimeService + ProcessManager（单进程任务管理）
    │ 本机桥 :22301
Shizuku 特权进程（虚拟屏 / 截图 / 点击 / 滑动 / 应用控制）
```

AzurPilot 源码基线是 `wess09/AzurPilot` 的 `origin/master` commit `88c4a41cea8aeaeafa7536db510d383ebba7213b`。适配补丁保存在 [rootfs/patches/azurpilot-android.patch](rootfs/patches/azurpilot-android.patch)。本地对应工作区在 `.tmp/AzurPilot-master`，不会改动 AzurPilot 仓库当前检出的 `dev` 分支。旧 ALAS rootfs 补丁和 `wrapper/runner` 已从新构建链移除。

## 构建与验证

从仓库根目录触发 `.github/workflows/rootfs.yml` 的 `workflow_dispatch`。ARM64 runner 执行 `rootfs/build/build-azurpilot.sh`，输出 `rootfs.tar.xz`、`BUILD_MANIFEST`、`runtime.tar.xz` 和 `latest.json`；后续 x86 runner 将 rootfs 与清单加入 APK，并执行 Gradle debug 构建。构建门禁要求依赖导入、配置生成、OCR CPU 推理通过。APK 缺少有效 rootfs 与清单时构建会失败。

运行时更新脚本默认从本项目的 `azurpilot-runtime` 资产读取 `latest.json`。该资产尚未发布，因此当前更新检查会保留内置版本。发布更新资产或 APK 需另行明确授权。

**尚待完成的验收**：ARM64 runner 首次构建、APK 安装、新旧双包并存、Shizuku 授权、虚拟屏手势窗口归属检查、截图触控、WebUI/挂机页/悬浮窗共用任务状态、实际挂机、工具任务及划掉 App 后清场。任何失败的原生依赖或设备能力均属于交付阻断项。

开发结构、构建约束与状态见 [development.md](development.md) 和最新 [handoff](handoff/)；历史设计保存在 `docs/roadmap-v3.md` 与 `m0-archive/`。
