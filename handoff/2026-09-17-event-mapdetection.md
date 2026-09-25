# Handoff 2026-09-17 · 活动图崩溃循环调查（进图伏击战 × map_init 死等）

> 上一篇：`2026-09-17-t1t2t3-fixes.md`（三项修复，已提交 `6e08c23` 并 push）。本篇记：用户报告"刷活动图自动退出挂机+WARNING 刷屏"的完整调查——**根因已定论（上游缺陷×图状态，环境层无解），等用户在方案 A/B/C 中拍板**。另修了一个自己的装机事故（env_fix 60s 包版本滞后）。

## 用户报告与最终结论

- **现象**：Event 任务刷幽影迷城 D3，自动退出挂机状态，日志刷 `WARNING | Image to detect is not in_map`；截图=游戏在「敌方潜艇出现中」伏击战画面，调度器显示"运行中·pid 9400"。
- **结论**：**不是 AzurPilot 环境问题，是 AzurPilot 上游缺陷被"未 3 星图的手动模式"必然触发**：
  1. D3 进图必发潜艇伏击战（spawn_data：battle 0 双 siren、`MOVABLE_ENEMY_TURN=(2,)`）；
  2. 上游 `campaign_base.run()` 在 enter_map 的 `is_combat_loading` 出口后直接 map_init，**无伏击战处理**；map_init 容错 ~18s < 伏击战 40-90s → 必崩 `MapDetectionError`；
  3. 用户 D3 **未 3 星**（`Map_info 99%, star_1, star_2, 100_percent_clear` → `No auto search option.`）→ 无自律寻敌只能手动模式 → 每进新图必踩伏击窗口；
  4. 崩溃重拉时 `Already in map, retreating` 撤退重进 = 再踩一次伏击 → **死循环**（runner 进程连崩退出由 wrapper 重拉，观测期 respawns 累计 13）。
- **上游实锤**：issue #5969/#5970（2026-09-11 同活动 B3，桌面雷电模拟器同款崩溃，排除 proot 性能/环境）；修复 commit `46fe341` 只加 AUTO_SEARCH_TITLE2（自律开关 JP 模板），不覆盖本场景；设备 AzurPilot 经热更新 ≥46fe341 仍崩=佐证。
- **游戏实际有推进**：崩溃间隙游戏自律打赢若干场（柴郡 boss S 胜、掉落富特/伦敦、油 13237→12884）——是"每张新图崩一次"的报错循环，不是完全卡死。

## 本轮修复（env_fix 60s 装机事故，已装机验证）

- **事故**：设备跑的是 T2 验证时装的 0.1.1-alpha.1 (46)，`ENV_FIX_TIMEOUT_MS` 实为 60s（300s 版改了源码没装机）→ app.log `env_fix exit=null`（61s 被杀）→ pip 中途死 → imageio 半装 → runner 14-37s 连环崩（session.log respawn #1-#27）。21:57 `RemoteService binder died` 后 app 复活但 proot 不自愈（已知"点开 App 才恢复"语义）→ wrapper 死 = 用户感知的"退出挂机"。
- **修复**：`ProotHost.kt` env_fix 失败输出改 `Timber.w("env_fix exit=%s out=%s", ...)`（FileLogTree 只收 W+，i 级 release 不可见）——一行改动+注释。
- **验证**：构建装机 **0.1.1-alpha.2 (47)**（22:05:19 Startup）后 session.log 零 runner 进程级崩溃、/status gui_alive=true、app.log 无 WARN。**教训：验证修复前先核设备 versionCode，HEAD≠装机版；adb forward 跨装机/重启会断，curl 空响应先重建 forward。**

## 待用户决策（方案菜单，汇报已发出）

- **A（推荐）**：WebUI 换已 3 星的活动图挂（B3/C3 等），或先用半自动点击/手动把 D3 打到 3 星再挂。零代码改动，立刻可用。
- **B**：AzurPilot 层缓解（不动 AzurPilot）：runner/wrapper 检测 `MapDetectionError+is_combat_loading` 崩溃签名 → 延长退避 + UI 明示"该图未 3 星，手动模式与进图伏击冲突，建议换图或先 3 星"。错误体验改进，工作量小。
- **C**：向 AzurPilot 上游报 issue（附完整日志：手动模式 enter_map→is_combat_loading→map_init 必崩，建议 map_init 前等伏击战结束/combat 处理）。慢但治本。
- **不做**：改 AzurPilot campaign_base/camera（用户红线）。

## 设备现场（已交还）

- runner 已停（POST /stop 实证 `runner_wanted=false`、pid null）；游戏已 force-stop；油 ~12884。D3 已通关（100%）未 3 星。
- adb forward 已重建（22400/22267/22300）；桥截图工具 `.tmp/bridge_cli.py cap out.png` 可用。
- git：三项修复在 `6e08c23`（已 push）；本轮 ProotHost.kt env_fix W 级输出改动待提交（commit+push 已预授权，随账册同步一起提）。

## 未直接验证项（如实记录）

- MAP_PREPARATION 界面截图始终没抓到（每次到那步都在战斗/委托）——"无自律寻敌开关"由日志 `No auto search option.` + Map_info 无 clear_mode 间接定论，未目验界面。
- 设备 AzurPilot 精确 commit 未读（`/opt/azurpilot/.azurpilot_commit` 在 proot 私有目录，shell 不可达）；">=46fe341" 由烘焙日期（9-15/16 depth-1 clone master）+ 每次启动热更新推定。

## 工具速查（本轮新增实证）

- **手机 WebUI 的 PC 侧转发固定 `adb forward tcp:32267 tcp:22267`**，浏览器访问 127.0.0.1:**32267**——绝不直接用 22267（与桌面版 AzurPilot WebUI 默认端口撞车，2026-09-17 用户桌面被劫持事故，详见 debug.md 撞车条目）。
- wrapper API：`POST /stop` 幂等（返回 `was_alive`）；`/status` 的 `runner_respawns` 是 wrapper 生命周期累计值，不随 /stop 清零。
- 设备日志时间 = guest UTC = 设备 CST-8h（崩溃时间戳换算注意）。
- 崩溃现场取证：`/logs?tail=N` 拿 runner 日志；error 目录 `./log/error/<ts>` 在 proot 私有目录，shell 不可达——靠桥截图 `.tmp/bridge_cli.py cap` 补画面证据。
