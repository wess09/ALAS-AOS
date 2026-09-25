# Handoff 2026-09-16 · 可行性分析：应用内「挂机页」（游戏画面 + 操作面板 + AzurPilot 任务概览）

> 上一篇：`2026-09-16-m5a.md`。本文是一次**只分析、未实施**的取证记录；用户尚未拍板做不做。

## 用户想法（原话要点）

把悬浮窗「操作面板」做进应用内页面：上游 上游 fork 显示游戏窗口的那个页面，保留游戏画面在上，下面一部分改成操作面板；原先选择任务配置的框，内部改为显示 AzurPilot 各任务管理配置。问"行不行"。

## 核心发现：预览通道在阶段二减法中幸存

减法只删了 UI 层（ui/home、ui/tasks、PreviewSurface、runner/PreviewPort），**底层链路整段还在现役 build 里**：

- AIDL：`app/app/src/main/aidl/com/aliothmoon/azurpilot/RemoteService.aidl:62` — `setMonitorSurface(in Surface)` 仍在（transaction 20）；触摸注入 touchDown/Move/Up 也在。
- 特权端：`remote/RemoteServiceImpl.kt:203-207` — `setMonitorSurface` → `VirtualDisplayManager.setMonitorSurface` + `NativeBridgeLib.setPreviewSurface`。
- native：`app/src/main/native/` 仍编译 bridge_preview.cpp / bridge_capture.cpp / bridge_frame_buffer.cpp（CMakeLists 在册）；`bridge/NativeBridgeLib.java:35` 声明 native 方法。
- 帧源活着的实证：桥 screencap（同帧源 socket 通道）真机定档 p50=30ms（阶段五-4）。
- 缺口只有 app 侧：一个 SurfaceView 组合件（上游 `ui/components/PreviewSurface.kt` 91 行可近乎照抄）+ 通过 `PrivilegedServicePort.serviceOrNull()` 调一次 `service.setMonitorSurface(surface)`。零 native/AIDL 工作量。
- 触摸注入通道（`RemoteServiceImpl.kt:215-219`）也在，点画面控游戏是后续可选项，不在本次范围。

## 分层结论

- **A 游戏画面 = 小~中**：UI + 一次 AIDL 调用；唯一未知是 bridge_preview 渲染管线真机首验（通道在≠无残留坑，但 capture 同帧源活着，信心高）。页面可见时才挂 surface（省电）；VD 未起显示占位 + 复用 `HostState.ensureEnvironmentStarted()` 一键拉起。
- **B 操作面板 = 小**：`overlay/OverlayPanel.kt` 组合件 + `proot/AzurPilotRunController`（Koin 单例，wrapper 薄 HTTP /status /start /stop /logs）+ `service/HostState.kt` 全部可直接复用；抽公共面板（状态行+日志板+启停），悬浮窗保留其壳（回 App/关闭/锁定）。
- **C 任务概览 = 小~中（只读）**：wrapper.py 加 `GET /tasks`，解析 guest `/opt/azurpilot/config/<上游配置文件>` 中带 `Scheduler` 节的任务组（启用态/NextRun）→ app 只读列表。**不做原生编辑**（直写上游配置 会被 WebUI 保存覆盖；违反「原生=控制面、WebUI=配置面」双头纪律），编辑入口跳 AzurPilot WebUI tab。
- **结构**：`ui/AppRoot.kt` 的 `TopDestination` 加第三个主 tab（如「挂机」）：上=游戏画面 16:9，中=任务概览卡，下=操作面板。AzurPilot WebUI tab 不动。pager/底栏结构现成。

## 工作量与建议

A+B+C 合计约 2 天含真机验证。建议顺序 **A 先行**（最大未知先排雷）→ B → C。属路线图新增范围（可立项 M5-α），不影响待做的用户在场「开始挂机」演示。

## 待用户拍板

做不做 / 一次做完还是分期 / 新 tab 命名与位置。
