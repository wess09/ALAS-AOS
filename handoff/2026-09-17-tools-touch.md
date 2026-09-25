# Handoff 2026-09-17 · AzurPilot 工具独立开启 + 全屏触摸转发

> 上一篇：`2026-09-17-idle-spinner-fix.md`（闲置环修复终版）。本篇记「工具栏半自动点击/活动剧情 App 独立开启 + 全屏触摸转发复活」全案：grilling 三轮 11 决策 → 双子代理并行实现 → 真机验收通过（互斥实弹与半自动终验留用户）。

## 决策（用户锁定）

D1 触摸转发常驻（只在全屏态生效）；D2 启动工具=自动停挂机；D3 工具结束**不自动恢复**挂机；D4 入口=挂机页「工具」区+悬浮窗面板复用同组件；D5 wrapper `/tool/*` 通道（webui ProcessManager 锁不动）；D6 反向互斥=工具运行时按开始挂机自动停工具；D7 全屏态复刻 m0 原版（横屏/隐系统栏/X/BackHandler）；D8 单指点+拖拽；D9 工具参数用 config 现值（webui 工具页可调——参数保存走 `_save_config`→`write_file`，不经被锁通道）；D10 验收分工（我做无头+触摸，半自动留用户几分钟）；D11 内嵌卡点击=进全屏（内嵌永不转发）。

## 关键事实（实读代码所得）

- webui 工具页 Start/Stop 被 fork 锁定补丁 `patch_scheduler_lock` 封死（总览页+工具页同 funnel）——「webui 开不了」是 M4-a 有意为之。
- 工具任务：`daemon`（半自动点击，常驻 while 1 无自停）、`event_story`（活动剧情，一次性自退）；无 Scheduler 参数组，永不能进挂机队列。无头入口=`AzurLaneAutoScript(config).run(task, skip_first_screenshot=True)`。
- m0 原版全屏触摸链完整存于 `m0-archive 里的 fork 基线`（TasksPreviewSection.kt/SessionContracts.kt/SessionViewModel.kt/AppRoot.kt），现 fork AIDL 注入端（RemoteServiceImpl.kt:215-219，带 VD displayId）一直在岗，只丢 UI 调用方。

## 实现（双源同步遵守：assets/azurpilot/overlay ↔ rootfs/overlays）

- **wrapper.py**（471→617）+ **runner.py**（51→70）：runner.py 支持 `runner.py <config> [task]`（白名单 daemon/event_story）；wrapper 新增 `POST /tool/start?name=&config=`、`POST /tool/stop`、`/status` 追加 `tool_alive/tool_name/tool_pid`。互斥=「停对方+拉自己」同在 `_tool_lock` 临界区（全局锁序 `_tool_lock→_runner_lock`）；`start_tool` 无条件 `stop_runner()`（清 wanted 防退避期重拉）；工具自然退出留墓碑（/status 依 `poll()` 实时判死），不重拉不恢复 runner（D3）；`_cleanup` 四路杀工具防孤儿。
- **Kotlin**：`AzurPilotRunController`（toolAlive/toolName 解析+startTool/stopTool，零门控）；`AzurPilotControlPanel`+`AzurPilotToolSection`（悬浮窗自动获得）；新文件 `ui/hangar/HangarPreview.kt`（m0 移植全套，movableContentOf 方案）；`HostState.touchDown/Move/Up` 直通现存 AIDL；`AppRoot` 顶层全屏浮层盖 tab 栏。文案 zh/en 双语。

## 验收（真机全过）

- HTTP：/status 三字段 ✓；daemon 启停（截图循环实跑、exit -15）✓；幂等 started_now=false ✓；非法名 400 ✓。
- UI 点「活动剧情」全链：tool_alive=event_story → app_start 游戏 → 登录处理 → page_event → story 模式 → **finish 自退**（剧情已清，43s）→ tool_alive 回落不恢复 ✓。
- 触摸 E2E：预览卡→横屏全屏 ✓；点游戏返回键→页面真切（幽影迷城→12章）✓；拖拽→地图平移 ✓；X→退出回竖屏、镜像无损回卡 ✓。
- **已实弹（用户终验通过）**：互斥 D2/D6 与半自动点击完整验收（D10）——用户实测后确认「功能完好，可以照常运行」（2026-09-17 收官）。

## 坑（已入 debug.md）

adb 遥控点击必须当帧截屏取坐标（布局漂移首点落空）；全屏 X 小目标偏 14px 被黑边边界检查丢弃（设计行为）。

## 设备状态

手机解锁亮屏、App 前台挂机页；游戏在 VD#36 运行（停在 12章地图=触摸验收遗留，无害）；wrapper 新版在岗（tool 字段服役）；runner 未启动（未私按开始挂机）。commit 待加（见 git log 最新）。

## 后续修订（同日）

- **UI 双模块行 v2**（用户点单，commit `51d4c3d` 已 push）：运行配置卡与工具区合并为一行——左卡（上「运行配置」标签、下全宽下拉）| 右列（半自动点击/活动剧情，开始挂机同款实心按钮，IntrinsicSize 行高均分自适应）；「工具」标签移除；工具在跑时对应槽位变「停止」。`AzurPilotControlPanel` 加 `showTools` 开关：悬浮窗保留原工具区，挂机页用新行。已装机截屏验证双形态。
- **悬浮窗工具按钮样式适配 ✅（同日第五轮）**：工具区两行式（描边+「X 运行中+停止」）→ 与挂机页同款实心槽位。`ToolSlotButton` 提升为共享组件（HangarScreen→AzurPilotControlPanel.kt），`toolDisplayName` 退役。实机验证双形态+左槽变停止+daemon 预启动链在悬浮窗路径同验。
- **退出残留审查 ✅（同日，免新措施）**：两种杀法实测——`am force-stop` t+2s 全灭（进程/三端口/VD）；`kill -9` app 本体 t+3s proot 树灭（stdin EOF 看门狗实证），sticky 服务复活 app 后特权/桥/VD 自动重连但 proot 环境不拉起（恢复力缺口，非残留，留后续决策）。机制清单见 devlog 同日条目。
- **daemon 预启动修复 ✅（同日第四轮，已装机验证）**：用户实弹发现「半自动点击不拉游戏、原地空转」。根因=AzurPilot 上游 daemon 任务设计不含 app_start（`AzurLaneDaemon.run()` 直接 `while 1: screenshot()` 盯当前屏，官方前提游戏已在跑；event_story 才自带 app_start）。修复=runner.py 在 daemon 前先 `azurpilot.run('start')`（LoginHandler.app_start+handle_app_login，双源同步，AzurPilot 代码零改动）。日志链实证：APP START→Login success→GUILD_POPUP_CANCEL→Daemon 绑定后零黑帧 WARNING；预览卡实见游戏主界面。另：左卡 contentPadding 上 sm/下 xs（「运行配置」标签下移）。**D10 半自动点击手动终验条件已齐（游戏能拉起了），仍留用户。**
- **UI v3 压高对齐 + 弹层适配 ✅（同日第三轮收口，已装机验证）**：用户反馈「模块高度缩 30%」→「左边再缩、间距更小、右按钮与左卡上下对齐」→「下拉弹层也做大小适配」。根因两层：M3 48dp 最小交互尺寸强制（用 `LocalMinimumInteractiveComponentSize provides 0.dp` 关掉——M3 1.4 是非空 Dp，`provides null` 编译不过）+ M3 Button 内层 `defaultMinSize(40dp)` 不受该 local 管（右列 84dp 仍驱动行高）。终案：左卡 `fillMaxHeight()` 拉伸对齐（实测左右顶 1018=1018、底 1269≈1268）；弹层宽度用 `onGloballyPositioned` 量锚按钮宽喂 `DropdownMenu`（实测同宽 475px，缘对齐 x≈95/570）。行高实测 250px≈83dp（v2 时 104dp，-20%）。详见 devlog 2026-09-17 UI v3 条。
- **用户终验 ✅（收官）**：半自动点击手动终验（D10）+ 工具⇄挂机互斥实弹（D2/D6）由用户实测通过——「功能完好，可以照常运行」。本篇全部事项闭环，无遗留。
- **adb 插曲**：WiFi adb 掉线一次（device offline），`adb disconnect` + `adb connect 192.168.50.190:5555` 恢复。
