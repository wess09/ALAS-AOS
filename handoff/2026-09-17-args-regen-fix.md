# Handoff 2026-09-17 · 活动列表冻结修复（args.json 去补丁化 + 启动链现场再生）

> 上一篇：`2026-09-17-restart-crash-fix.md`（AzurPilot 重启断连修复）。本篇修用户回报的第三连问题：手机端 WebUI 活动下拉停在旧活动「沉溺于星光之城」，桌面版已是「幽影迷城」。

## 根因

与 base.py 案同病不同药：`patches/module/config/argument/{args.json,argument.yaml}` 是 m0 遗产**整文件覆盖补丁**，AzurPilotOverlay 每次启动重放把 args.json（WebUI 活动选项唯一来源）冻在补丁年代。热更新通道本身健康：设备 commit = 上游 HEAD（92c07aa）、campaign 地图资源、i18n 译名全部到位——只有选项列表被冻。全量 diff 证明补丁里 AzurPilot 真定制仅 2 行（两个 Method 各加 `azurpilot` 选项），其余全是冻结漂移。

## 改动清单

- **删**（双源共 4 文件）：`rootfs/patches/module/config/argument/{args.json,argument.yaml}` + assets 同源副本。这两个上游 git 跟踪文件今后由热更新自然带新。
- **新增** `rootfs/seeds/regen_args.py` + `app/.../assets/azurpilot/overlay/seeds/regen_args.py`（diff 同步）：
  1. `python -m module.config.config_updater` 跑 AzurPilot 完整生成链（活动列表随 `campaign/Readme.md`）；
  2. args.json 后处理：`AzurPilot.Emulator.{ScreenshotMethod,ControlMethod}.option` 补 `azurpilot`（幂等）；
  3. zh-CN.json 后处理：两节点 `azurpilot` 显示名升级「AzurPilot 桥」（**i18n 顶层是组名无任务层**，勿按 args.json 的任务层取道）。
- `ProotHost.kt`：启动链热更新块后无条件 runGuest 跑 regen（`REGEN_ARGS_TIMEOUT_MS=360_000`，失败 Timber.w 降级不阻塞）。
- `rootfs/build/build-rootfs.sh`：fail-fast 清单 + install 铺装 regen_args.py。
- 账册：devlog 顶部、debug.md「补丁冻结第二案」。

## 关键坑（再生链专属）

- 生成器必须 `python -m module.config.config_updater`：直传脚本路径时 `sys.path[0]=module/config/`，`from deploy.utils import` 炸 ModuleNotFoundError。
- runGuest 失败只 Timber.w，logcat 被 HONOR 噪音分钟级冲掉——判 regen 跑没跑看 **args.json mtime** 而不是日志。
- 手动复现 proot 一次性 exec 的配方（排障用）：`pm path` 推 nativeLibDir → `libproot.so -w /opt/azurpilot -r files/rootfs -b /dev:/dev -b /proc:/proc -b /sys:/sys` + baseEnv 同款环境变量（LD_LIBRARY_PATH/PROOT_LOADER/PROOT_TMP_DIR/HOME/PATH/LANG/MAAAL_ALAS_ROOT/MAAAL_WEBUI）。
- 手机锁屏会按住 proot 启动链（readiness 门控，设计行为）；验证前确认亮屏。

## 验证状态

- ✅ 端到端真机：装机 → 启动链自动 regen → args.json/zh-CN.json mtime 自动刷新；活动选项含 `event_20260908_cn`（幽影迷城）；`azurpilot` 选项双在；显示名「AzurPilot 桥」生效。生成链设备端 0.5s（热缓存）。
- ✅ 用户可见效果：重进 app 后 WebUI 活动下拉应显示「幽影迷城」，与桌面版一致。

## 长期债（不变，已记 debug.md）

- 补丁机制剩余文件仍是整文件覆盖（base.py 已按 master 重打收敛）；args.json/argument.yaml 两个**生成产物**已通过「现场再生」退出补丁体系，是首个范例。
