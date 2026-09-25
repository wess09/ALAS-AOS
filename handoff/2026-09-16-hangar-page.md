# Handoff 2026-09-16 · 挂机页落地（游戏画面 + 运行配置 + 操作面板，真机实证）

> 上一篇：`2026-09-16-gamepage-analysis.md`（可行性分析，结论"预览通道幸存"已被本里程碑坐实）。

## 干了什么

用户想法落地：**应用内「挂机」页 = 虚拟屏实时画面（上）+ 运行配置下拉（中）+ AzurPilot 控制面板（下）**，成为 App 第一主 tab（默认首页）；悬浮窗面板保留不动。用户明确：配置区只显示 config 文件里的**配置名称**下拉框，不显示具体内容。

## 改动清单（均已装机实证）

- `rootfs/overlays/wrapper.py`（+ `app/app/src/main/assets/azurpilot/overlay/wrapper.py` 同源同步，diff 校验过）：
  - `GET /configs` → config/*.json 去 template* 的实例名列表（'azurpilot' 排最前）
  - `POST /start?config=N` → 实例名白名单校验后透传 runner argv[1]（runner.py 本就支持）
  - `/status` 新增 `config` 字段（在跑实例名，没在跑为 null）
- `proot/AzurPilotRunController.kt`：configs/selectedConfig/runningConfig 三态；选择存 SharedPreferences(`azurpilot`)；startRunner() 带 `?config=`；选择失效自愈回列表首项。
- `service/HostState.kt`：`attachPreviewSurface/detachPreviewSurface` → AIDL `setMonitorSurface`（失败静默，页面显示占位）。
- `ui/components/AzurPilotControlPanel.kt`（新）：状态行+日志板+调度器启停，悬浮窗与挂机页共享。
- `overlay/OverlayPanel.kt`：重构为壳（标题/锁定/回App/环境启停）+ 共享面板。
- `ui/hangar/HangarScreen.kt`（新）：16:9 SurfaceView 预览（setFixedSize 1280×720 延50ms，active 才挂面，surfaceChanged 不重发时靠 attachedSurface 重挂）+ 配置卡（runnerAlive 时锁选择）+ 面板。
- `ui/AppRoot.kt`/`ui/navigation/Routes.kt`：TopDestination 加 Hangar（首位），NavHost startDestination=HANGAR，mainTabs 同步。
- `res/values(-en)/strings.xml`：nav_hangar 挂机/Farming 等 7 条。
- `README.md`：原理图 + 使用段改为「挂机页/悬浮窗 = 控制面」。

## 真机验证证据（HONOR PPG-AN00，versionCode 22）

- `GET /configs` → `{"configs": ["azurpilot"]}` ✅；`/status` 含 `"config": null` ✅（runner 未跑）
- 页面全渲染：标题/预览框/运行配置卡(azurpilot+切换)/状态行/日志板/开始挂机/底栏三 tab ✅（`.tmp/hangar-1.png`）
- 下拉点开列出 azurpilot ✅（`.tmp/hangar-3.png`）
- **预览通道像素级实证**：VD 空载 → 桥帧全零 & app 预览同黑；`am start --display 22 com.android.settings/.Settings` 后 → 桥帧白+左黑条（`.tmp/vd-frame2.png`）& app 预览同构图（`.tmp/hangar-2.png` 裁区一致）✅。Settings 事后已 force-stop，临时图已清。
- **未按「开始挂机」**（纪律：`/start?config=` 的真跑验证留给用户在场演示）。

## 坑（已入 devlog）

MagicOS 无线调试休眠掉线（install 空报错/device offline，重连恢复）；构建漏 import 快败一次。

## 下一步（不变）

- 等用户在场：开始挂机端到端演示（阶段四最后 DoD）——届时顺带验证 `/start?config=` 生效与预览出游戏画面。
- 等用户：多 ROM 实测 / 长稳+息屏 / 重启验证 / 油数验收 / appId 定夺。
