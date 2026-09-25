# Handoff 2026-09-17 · 「开始挂机 touch down failed」根因修复（桥侧 down 有界重试）

> 上一篇：`2026-09-16-hangar-page.md`（挂机页落地）。本篇是挂机页上线后的首个线上 bug 修复。

## 症状与根因

- 用户按「开始挂机」→ 调度器正常识别任务（GET_SHIP）→ 首击 `Click` → `ScriptError: AzurPilot proxy error: touch down failed` → CRITICAL 死。复现率：每次开始挂机必死。
- 根因 = **input 窗注册竞态**：`am start --display N` 把游戏拉上 VD 后，SurfaceFlinger 先出帧（桥 screencap 已见游戏画面、OCR 能匹配），但 **input 窗注册滞后 ~1s**；`InputControlUtils` 走 WAIT_FOR_FINISH，此间注入框架原生返 false（debug.md 旧案「空 VD 返 false」的升级版——窗"可见"≠"可点"）。AzurPilot 链路 am start→识别→立即点，首击必踩；单击失败即抛 ScriptError 死调度器（上游行为，不动）。
- 实验证据：空 VD click → false 复现；am start 后 0.4s 轮询 click 首拍必败、~1s 后恒 ok；游戏窗在且注册后 click/swipe 恒 ok。

## 修复（已装机真机回归）

`app/app/src/main/java/com/aliothmoon/azurpilot/remote/internal/BridgeServer.kt`：
- 新增 `downWithRetry(x, y, displayId)`：down 失败后 **3s 预算 / 200ms 间隔**重试（失败事件未投递、无悬挂 DOWN，安全）；成功记 `Ln.w(race absorbed)`，耗尽记 `Ln.e`。
- handleClick / handleSwipe 的 down 均改走它；最终报错带诊断：`touch down failed (no touchable window on display N within 3000ms)`——带这句 = 真空 VD（游戏未起/崩溃），区别于竞态。
- 协议兼容：错误帧形状不变，AzurPilot 侧 azurpilot.py 只读 error 文本。

## 真机回归（VD #23，HONOR PPG-AN00）

1. force-stop 游戏 → am start 上 VD → **立刻 click（修复前 100% 败的时点）→ ok:true** ✅
2. 空 VD click → 3.18s 后带诊断报错（预算有界，行为正确）✅
3. 游戏窗稳定后 click×3 + swipe 500ms 全 ok ✅
4. 清场：游戏 force-stop、/sdcard/.di.txt 删除 ✅

## 待用户

- **重新按「开始挂机」验证**（修复后的首次端到端真实挂机）。若再失败且报错带 `no touchable window`，说明游戏没起来/崩了，把日志发来即可。
- 装机版本：本次 install -r 的 debug 包（构建于本修复后）。`git status` 未提交部分即本修复 + 账册。

## 环境备忘（本轮新增）

- adb 重连后 `adb forward tcp:22300 tcp:22300` 会丢，桥 socket 测试前要重设。
- install -r 杀 app → VD 随之销毁；重进 app（挂机页为默认首页）自动重建 VD（本次 #22→#23）。bridge 由特权进程自起，install 后 ~7s 自愈，无需手拉。
- 复现/回归脚本手法：`am force-stop` 清窗 → 桥 shell `am start --display N` → 立即桥 click 探竞态。
