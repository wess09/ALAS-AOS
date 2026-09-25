# Handoff 2026-09-18 · 启动静默期治理（阶段落盘 + 挂机页准备明细）+ 两个用户疑问的定论

> 上一篇：`2026-09-18-azurlane-ocr.md`（字体模型上机，已验证）。本篇是紧随的小迭代：把「App 假死」静默期变成可观测，并给「开机慢」「VD +1」两个用户疑问定论。

## 交付（alpha.8 已装机验证）

1. **阶段迁移落盘**：`ProotHost.setState/fail` 每步写 `[host] MM-dd HH:mm:ss PHASE detail` 到 `proot/session.log`——与 `[proot-out]` 交错即成完整生命周期时间线，绕开 release 版 Timber 只落 W+ 的盲区。
2. **挂机页/悬浮窗状态行**：`AzurPilotControlPanel` 注入 ProotHost 状态，wrapper 不可达时区分 `环境准备中 · {明细}`（PREPARING/UPDATING/STARTING）、`启动失败：{原因}`（FAILED）、`环境未就绪`（其余）。中英字符串各三条（overlay_azurpilot_preparing / _preparing_generic / _start_failed）。
3. **真机证据**：session.log 逐阶段留痕（12:43:22 清理残留 → 12:47:26 RUNNING）；截图 `.tmp/phone-preparing.png` 状态行显示「环境准备中 · 检查 AzurPilot 热更新」，按钮正确置灰。

## 定论 1：「开机慢」= 热更新检查独占 ~4 分钟（本次实测）

- 准备链总时长 4m04s：`UPDATING 检查 AzurPilot 热更新` 一步从 12:43:23 卡到 ~12:47:2x（本机网络下 ls-remote/fetch 慢至超时边界）；其余全部步骤合计秒级。
- 「以前没这么慢」：启动链是逐次加上去的（overlay 补丁、env_fix 钉版自检、regen_args、热更新），且 proot 下 syscall 密集操作慢 5~10 倍（debug.md 有账）。
- **候选改进（已实施，alpha.9）**：热更新双通道——新增 `seeds/cdn_update.py`（零依赖复刻上游 git_over_cdn 协议：latest.json(3s)→增量 pack zip(20s)→落 .git/objects/pack+refs），`update.sh` 改 CDN 优先 + git:// 兜底 + 两通道皆败当日退避。真机实证：**开机链 4m04s→5s**（19:47:59→19:48:04），CDN 检查 1s UPTODATE。PC 端三态端到端测试全过（真 CDN 下载增量 pack 仅 399KB）。PC 测试注意：MSYS 下 `python3` 是 WindowsApps 占位_stub_（静默 rc=49）需 shim 成 `python`；`MSYS_NO_PATHCONV=1` + POSIX 路径传 Windows python 会落影子树 `D:\d\`（已清理）。

## 定论 2：VD +1 不是残留，旧屏必被回收

- App 进程死 → 特权进程自杀双保险：binder linkToDeath（主）+ 心跳看门狗 5s 查 `/proc/<appPid>`（兜底，RemoteServiceImpl.kt:314）→ `cleanup()` 释放 VD → exitProcess；SIGKILL 也漏不掉——VD 经 binder 注册在系统 DMS，进程死亡即被移除。
- +1 只是 Android 全局 display id 计数器单调递增不复用；手机重启计数清零（实证：本次新屏 #9，此前 #29）。
- 查残留命令：`adb shell dumpsys display | grep -i virtual`（正常只有一块 AzurPilot 屏）。

## 新约束入账（用户指令）

- AGENTS.md 工程约定新增第一条：**AzurPilot 上游代码红线**——无明确指令不得改 AzurPilot 上游源代码（含「临时改一下再改回」），修复走 overlay/patches/seeds/环境钉版；桌面 AzurPilot 只读。

## 当前现场

- alpha.8 装机完成，wrapper RUNNING；runner 停止中（装机中断了此前的 D3 挂机，用户点「开始挂机」即恢复）。
- adb forwards 已重建：22300 / 22400（32267→22267 未重建，需要 WebUI 时再说）。
