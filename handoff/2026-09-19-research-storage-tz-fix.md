# Handoff 2026-09-19 · 科研卡仓库 + 时区 + 孤儿游戏 三修复

> 上一篇：`2026-09-18-release-v011.md`（v0.1.1 发版收官）。本篇记用户报障双问题的根因与修复，均已真机验证；**改动未 commit、未发版**（等用户指令）。

## 修复与验证结论（全部 ✅）

1. **科研卡仓库死循环**：游戏仓库页 UI 改版，上游 `MATERIAL_CHECK.png`（GitHub API 实证 **2022-08 创建后从未改动**，桌面与 master 逐字节一致；桌面 git log 的「2026-03-27」是 CDN 镜像历史重写假象）模板匹配 ccoeff 0.04 → `_storage_enter_material` 永不确认 → 空点 MATERIAL_ENTER 至死 → wrapper 无限重拉（session.log 累计 87 次 respawn）。同服同版本同布局 → **桌面端跑此步同样必卡**（上游事故史：#4276 JP 同症状已修、#4815 CN 至今 open）。**修复**：真机实帧重制模板（新帧 1.0、异页 ≤0.27），走既有 patches/assets 校准通道双源落位（第 8 个校准资产）。真机验证：拆解链 41ms 通过、`Total_Disassemble 0/15→15/15` 9 秒完成。
2. **AzurPilot 时间差 8h**：rootfs 无 tzdata/TZ → 全环境 UTC。**修复**：`ProotHost.baseEnv` 加 `TZ=CST-8`（POSIX 形式不依赖 zoneinfo）。验证：`gui_started_at` 与设备时钟一致。副作用：UTC 时代写的 NextRun 首轮集中补跑后自愈。
3. **（顺带实锤）孤儿游戏开局死循环**：App 重装/重启拆 VD → 游戏成无窗孤儿（pid 活、帧纯黑）→ AzurPilot 按 pid 判活 → 黑帧 GamePageUnknownError → exit(1) 无限 respawn（实测连涨 3 次）。**修复**：`runner.py` 进 loop 前加 daemon 同款黑帧判定，孤儿即 app_stop，调度器走 GameNotRunningError → Restart 官方任务重新拉起。验证：35s 恢复到 `[UI] page_main`，respawns=0。

## 未 commit 改动清单

- 新增 `rootfs/patches/assets/cn/storage/MATERIAL_CHECK.png` + `app/app/src/main/assets/azurpilot/patches/assets/cn/storage/MATERIAL_CHECK.png`（cmp 一致）
- `app/app/src/main/java/com/aliothmoon/azurpilot/proot/ProotHost.kt`（baseEnv 加 TZ=CST-8）
- `rootfs/overlays/runner.py` + `app/app/src/main/assets/azurpilot/overlay/runner.py`（孤儿清理，cmp 一致）
- 账册：devlog.md（未发布段）、本篇 handoff

## 本次新攒的调试手段（release 无 run-as 环境下）

- **wrapper HTTP 取证**：`adb forward tcp:22400 tcp:22400` → `/status` `/logs?tail=N`；**/logs 只给 mtime 最新 txt**——要拉 runner 日志需先 POST /start 让 azurpilot.txt 变最新（gui.txt 常驻最新会挡）。
- **桥 22300 手驱动**：行分隔 JSON + 二进制帧协议，`.tmp/vd_probe.py`（cap/click）可直接截虚拟屏、点坐标；`shell` 方法以 shell uid 执行（读不了 proot 私有目录）。
- **资产离线打分**：`.tmp/asset_score.py <截图> <资产名...>` = 同位 ccoeff + 色差批量算（数据源：桌面 `C:\other\AzurLaneAutoScript` 的 assets.py，只读）；Scroll 类资产不吃模板只吃构造色，wait_until_stable 类只吃实时帧——打分前先确认消费方式。
- **游戏方 UI 差异判据**：模板半年未动 + 同页签布局重排 = 游戏改版，上游同害；修复走 patches/assets 不碰上游代码。

## 未决事项（等自然触发，勿主动追）

- GemsFarming 'MRT' 崩溃复验（OCR 同源已证，预期已愈）。
- CDN UPDATED 路径真机首验（等上游推 commit；另：9-19 17:55 曾一次 `FAILED reset --hard`，当日退避，次日自愈——留意是否复现）。
- 科研「receive_6th_research wait timeout」WARNING（流程能继续，非致命，未查）。
- 本次修复**待发版**：下个版本（v0.1.2？）打包时纳入 CHANGELOG。
- goal（roadmap-v3 主线）仍 blocked，阶段五 -1/-2/-5 随用户安排。

## 设备当前状态

- release 版三修复装机（aapt 实证 versionCode 57 / versionName 0.1.2-alpha.1，GitVersion 派生），挂机 runner 在岗（19:32 会话，调度正常推进）。
- 游戏在 VD 上健康（page_main 系正常翻页）；物理屏勿开游戏抢渲染。

## 追加验证（2026-09-19 午后）：CDN 全量同源 + E 触发面

- 应用户要求拉全量 CDN 资源：`latest.json` = `ls-remote git.lyoko.io` = GitHub master = `74e8231`；全量 clone（`.tmp/cdn_check/repo`，9437 文件）与桌面端 diff 整个 `assets/cn/` + `module/` **全空**（仅 `__pycache__`）；`MATERIAL_CHECK.png` 三方 sha256 一致 = 2022 版（API 订正：唯一提交 `faf5a74e` @ **2022-08-17**）。→ 任何渠道都没藏修复。
- E 触发链：`research.py:265` 唯一 `E + equipment_amount>0` 分支走 storage → **E 碰到必炸**；但 E 在 ta152 预设第 13 位后、默认过滤器尾部 → 桌面 50 天 Research 0 次 E 选中（日志实证）→ 「碰到必炸、但很难碰到」。
- **待用户决策**：是否向上游提 issue/PR（建议附黑底+稳定锚点版模板，非整屏实帧版，防格子内容差异拖垮整屏相关度）。桌面端救场 = 拷补丁 png 到 `C:\other\AzurLaneAutoScript\assets\cn\storage\`（会被热更新冲掉，治本靠上游）。
