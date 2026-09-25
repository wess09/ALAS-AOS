# Handoff 2026-09-17 · 「AzurPilot 重启即断连」根因修复（补丁漂移重打 + runner 自拉起）

> 上一篇：`2026-09-17-touch-race-fix.md`（挂机首击竞态修复）。本篇修用户回报的第二连问题：AzurPilot 重启游戏 → 任务断开要手点。

## 根因（双层）

- **A 致命崩溃**：`patches/module/base/base.py` 是 m0 时代**全量拷贝**（cp -rf 整文件覆盖施加，build-rootfs.sh:175），把热更新到上游 master 的 base.py 冻回旧版：签名 `image_color_button(color_threshold=250)` vs 新版 login.py 调用 `threshold=10` → TypeError → runner 死。自有改动仅 early_ocr_import 的 AzurPilot 预热块（BEGIN/END 标记内 9 行）。证据链：设备 login.py 与上游 master 逐字节一致 → 设备跟踪 master → 按 master 重打补丁收敛。
- **B 体验断连**：wrapper 只监管 WebUI，runner 无监管，任何崩溃都要人手再点「开始挂机」。

## 改动清单

- `rootfs/patches/module/base/base.py` + `app/app/src/main/assets/azurpilot/patches/module/base/base.py`（双源 diff 同步）：
  重打 = 上游 master 原样（`threshold=5` 新签名）+ AzurPilot 预热块。已 base64 分块法直写设备活文件并 diff 校验（DEVICE_SYNC_OK）。
- `rootfs/overlays/wrapper.py` + assets 同源副本：
  - `_runner_wanted` Event：/start 置位、/stop 复位；
  - `_runner_supervisor()`：runner 非预期死亡按 5s→60s 退避自动重拉同配置实例，活过 5 分钟退避复位（gui 监管同款形制）；main() 常驻线程；
  - `/status` 新增 `runner_wanted` / `runner_respawns`；
  - start_runner 抽 `_spawn_runner()` 共用。
- 账册：devlog 顶部、debug.md 两条新案（补丁漂移 / adb exec-out stdin 悬挂→base64 分块法）。

## 验证状态

- ✅ wrapper/base.py py_compile；APK 重装 Success；`/status` 已带新字段（wrapper 新版在岗，gui alive）。
- ✅ **端到端用户确认**（2026-09-17「可以使用了」）：挂机 + AzurPilot 重启链路恢复正常，本里程碑收官。

## 设备直读日志路径（用户导不出日志时自用）

```
run-as com.aliothmoon.azurpilot ls -lt files/rootfs/opt/azurpilot/log/
tail -c N files/rootfs/opt/azurpilot/log/<最新>.txt
```
挂机页日志板走 wrapper `/logs` 端点，应用内可直接看。

## 长期债（已记 debug.md）

- 补丁终态应从整文件覆盖改为差分补丁（git apply），否则每次 AzurPilot 热更新都在赌 API 不漂。
- adb exec-out stdin 悬挂：设备写文件统一走 base64 分块法。
