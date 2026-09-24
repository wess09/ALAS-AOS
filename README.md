# AzurPilot Android

本仓库正在将原 ALAS-AOS 改造为独立的 AzurPilot Android App。新包名为 `io.github.shinarin.azurpilotandroid`，使用独立私有数据目录，可与旧版 ALAS-AOS 并存；旧配置不会自动迁移。**当前尚未完成 ARM64 构建和真机后台挂机验收，请勿将源码状态当作可安装成品。**

## 目标功能

- Shizuku 虚拟屏承载 1280×720 的游戏画面；本机桥提供截图、触控和应用控制。
- AzurPilot React WebUI、挂机页和悬浮窗共用同一个 `ProcessManager`，支持调度与工具任务、状态和日志。
- WebUI 使用回环端口 `25548`，特权桥使用 `22301`，与旧版端口分离。
- APK 内置 Python 3.14、锁定依赖、OCR 模型和预构建的 React 页面；手机首启仅部署与校验。
- AzurPilot `dev` 更新后由 GitHub Actions 重新构建完整 APK；App 校验更新清单与 APK SHA-256 后交给系统覆盖安装。

## 架构

```text
Android App（挂机页 / 悬浮窗 / WebView）
    │ 回环控制 API :25548
AzurPilot WebUI + RuntimeService + ProcessManager（单进程任务管理）
    │ 本机桥 :22301
Shizuku 特权进程（虚拟屏 / 截图 / 点击 / 滑动 / 应用控制）
```

AzurPilot 源码基线是 `wess09/AzurPilot` 的 `dev` commit `bda43b6467f06bd25c986714ab6b1580ac6a7150`。Android 设备后端、控制 API、移动端侧边栏修复和 Android 更新器分流均已作为 AzurPilot 正式源码提交，AOS 构建只检出该 commit，不再覆盖 AzurPilot 源文件。旧 ALAS rootfs 补丁和 `wrapper/runner` 已从新构建链移除。

## 构建与验证

`.github/workflows/rootfs.yml` 支持手动触发、AzurPilot 仓库派发和每小时兜底轮询。检测到新的 `dev` commit 后，ARM64 runner 预构建 React 前端、Python 3.14 rootfs 和 OCR 资源并执行导入/OCR 门禁；随后构建稳定签名的 release APK，发布到 `azurpilot-android-dev` 更新通道。APK 缺少有效 rootfs、构建清单或签名配置时构建会失败。

App 启动时读取更新通道的 `latest.json`。远端 `versionCode` 更新时弹出安装提示，下载后校验文件大小与 SHA-256，再调用 Android 系统安装器覆盖安装。首次切换到自动更新通道需要安装使用该通道固定签名的 APK；之后可连续覆盖更新。

**尚待完成的验收**：ARM64 runner 首次构建、APK 安装、新旧双包并存、Shizuku 授权、虚拟屏手势窗口归属检查、截图触控、WebUI/挂机页/悬浮窗共用任务状态、实际挂机、工具任务及划掉 App 后清场。任何失败的原生依赖或设备能力均属于交付阻断项。

开发结构、构建约束与状态见 [development.md](development.md) 和最新 [handoff](handoff/)；历史设计保存在 `docs/roadmap-v3.md` 与 `m0-archive/`。
