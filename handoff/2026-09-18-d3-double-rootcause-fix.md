# Handoff 2026-09-18 · D3 崩溃循环双根因破案+修复+终验通过

> 上一篇：`2026-09-17-event-mapdetection.md`（旧结论"上游缺陷×未 3 星，环境层无解"）。**本篇推翻该旧结论**——用户"桌面端可以刷 D3"一句话 reopen，查实真正根因是两个识别问题，均已修复并端到端验证（BATTLE_1~6+boss，零错误）。

## 最终结论（双断点，互相独立）

1. **断点 1·周回/自律开关 TITLE 模板手机渲染失配（已修，模板补丁）**：上游模板为 MuMu 锐化滤镜调校，手机渲染下 sim 仅 0.829/0.783 < 0.85 → SwitchClearMode.get='unknown' → clear_mode 丢 → MAP_HAS_AMBUSH=True → 手动模式踩伏击 → MAP INIT 死等 → MapDetectionError 崩溃循环。旧结论归因的"未 3 星"只是表象——同一 99% 星状态下，clear_mode 能/不能被识别才是分水岭（桌面能识别所以能刷）。
2. **断点 2·D1 未通关徽标名字图 OCR '01' 毒害（已绕，手动清 D1）**：断点 1 修好后 runner 仍死在入口——`_get_stage_name` OCR 读 `['01','D3','B2']` → chapter='0' → CampaignNameError（campaign_ui.py:418 ScriptEnd）。'01'=D1 0% 红签小字名字图 D 字二值化糊成实心团；桌面 azur_lane cnocr 字体模型对同帧读 `['D1','D3-','B2']`——像素无罪，通用 PP-OCR 模型差距。

## 修复动作（AzurPilot 代码零改动，合规）

- **断点 1**：手机实拍帧（整帧 1280x720，与上游同约定）自制 `CLEAR_MODE_TITLE.png`/`AUTO_SEARCH_TITLE.png`，双源部署 `rootfs/patches/assets/cn/handler/` + `app/app/src/main/assets/azurpilot/patches/assets/cn/handler/`（cmp 一致），重打包 release APK 装机，重启 App 后 overlay 幂等铺 /opt/azurpilot。离线 sim=1.0（新）vs 0.829/0.783（旧，确定性失败）。
- **断点 2**：桥手动把 D1 打通（5 杀+boss S 胜，威胁排除 100%），徽标变 Clear! 大签 → 名字 OCR 正常。通用修法（azur_lane 模型上机）留后续。

## 终验证据（日志 `.tmp/d3cap/verify-log-2.txt` / `verify-log-3.txt`，截图 `final_proof.png`）

- `[Stage] d3, ai, b2`（d3 正确读出选中，无 '01' 无 CampaignNameError）→ `[Map_info] 99%, star_1, star_2, 100_percent_clear, **clear_mode**` → `Clear_Mode on` / `Auto_Search on` / `fleet1_mob_fleet2_boss, sub_standby` → BATTLE_1~6 连续战斗 → boss 战（Lv.105）自律中。最近 200 行 0 ERROR / 0 MapDetectionError / 0 CampaignNameError。

## 环境速查（本会话实测有效）

- adb forwards：`tcp:22300` 桥 / `tcp:22400` wrapper / `tcp:32267→tcp:22267` WebUI。桥断先重建 forward。
- wrapper：`GET /status` `/logs?tail=N`、`POST /start|/stop`。
- 桥：`python .tmp/bridge_cli.py cap|click|swipe|shell`。
- VD 探测：`adb shell "dumpsys display | grep -oE 'displayId=[0-9]+, uniqueId=.virtual:com.android.shell,2000,AzurPilot'"`（本 session 16→18 变过一次）；起游戏 `bridge_cli.py shell "am start --display <VID> -n com.bilibili.azurlane/com.manjuu.azurlane.MainActivity"`。
- overlay 机制：改 `assets/azurpilot/patches|overlay` 只需重装 APK 重启 App，不必重烘 rootfs。
- 桌面 AzurPilot OCR 对质（只读合规用法）：`cd /c/other/AzurLaneAutoScript && ./toolkit/python.exe -c "from module.ocr.ocr import Ocr; ..."`（azur_lane cnocr 模型，charset 39 无 'O'）。
- 帧/证据 `.tmp/d3cap/`：nav_*、cur_20~68（D1 作战全程）、play_*、nameimg_*（糊 D 铁证）、final_proof.png（boss 战）、verify-log-*.txt。

## 悬案与后续项（未做，勿擅自启动）

- **App 自体死亡 ×2**：均伴随 runner 崩溃循环后发生，死时游戏被杀、VD 重建换号；monkey 拉起后 proot ~15s 自愈。原因未查明，下轮查 logcat/墓碑。
- **azur_lane 字体模型上机**（治 '01' 毒害防复发：下次新活动 0% 徽标会复发）：MXNet→ONNX 转换（GRU 导出支持存疑）或纯 numpy 手写 densenet-lite-gru 前向，集成 `rpc.py` 按 lang='azur_lane' 路由 + 39 字符 keys。
- **紧急委托选关报错**（三件套 #2 另一半）：流程不同（委托入口非 campaign 面板），证据未收集，用户复现再查。
- **通用 OCR 候选模型 A/B 全灭记录**：v5_ch_mobile 'ս'、v4_ch_mobile 'սս'、v4_en_mobile ''（D3/B2 能读）、v5_en_mobile out_dim 438 vs keys 95 不匹配。模型在 `.tmp/ocr-models/`。

## 当前现场

- runner 正在跑 D3 自律（用户可随意 /stop 或停挂机）。油 ~13111、币 118917。
- 用户游戏资产变化：D1 已通关（Clear!）、D1  boss 掉落若干、心智单元 x18400——均为用户账号正收益。
- **git 未提交**：断点 1 模板双源 4 文件（rootfs/patches 2 + app assets 2）+ 本篇账册 3 文件（devlog.md / debug.md / handoff/）。
