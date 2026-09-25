# AzurPilot Android 适配交接（未完成）

## 已完成

- Android 适配已直接进入 `C:\Users\AzurLane\Desktop\Projects\AzurLaneAutoScript` 的 `dev` 分支。基础适配提交为 `b8f91d885`；移动端侧边栏修复和 Android 更新器分流已进入当前基线 `51d6a60f89e46fe5933e4e31dd0c75f8bab69f23`，均已推送到 `origin/dev`。
- Android 桥设备、配置生成、同进程控制 API、宿主运行链、独立端口/包名、构建脚本、更新和回滚脚本已实现。旧 AzurPilot 覆盖层与双进程监管已从新构建链删除。
- GitHub Actions ARM64 rootfs run `35948884651` 成功；复用该产物的 APK run `35950995233` 成功。验证分支 `codex/azurpilot-android-verify` 已推送，未做 release。
- 真机已完成安装、首启部署、热更新和 WebUI 拉起。首轮日志发现 `/proc/stat` 权限故障；适配层已用 `/proc/<pid>/stat` starttime tick 完成进程身份后备，并覆盖进程登记和清场链。针对性测试与原设备/API/OCR 测试共 87 项通过。
- 新增 `tools/watch-android-logs.ps1` 实时查看 App logcat 或 proot session 日志。对只提供 x86_64 原生 ABI、依赖 ARM 转译的模拟器给出明确失败原因。
- 用户已确认 Root 模式能够运行。Shizuku 日志中的 `BIND_DENIED` 已在宿主源码修复：启动环境和任务前会走授权入口并等待虚拟屏就绪。同机 Chrome 正常而 System WebView 异常；UA/CSS 尝试又导致侧栏被裁掉，现已从 AP 完整回滚，Android 改用浏览器 Custom Tab 打开 WebUI。
- AP 内置 Git 更新器不适用于已移除 `.git` 的 Android 运行时，Android 环境已改为显示 App 整包更新说明并停止 Git 检查。用户随后确定采用 APK 级更新：AOS 每 15 分钟轮询 AP `dev`，CI 预构建完整 APK 并更新固定 release，App 下载校验后交给系统覆盖安装；AP 仓库不再承担跨仓库触发。
- CI run `36002470839` 已用 AP `266f4222b` 完整通过 ARM64 rootfs 与 debug APK 构建；更新器收口后的 APK run `36004677222` 也已通过。当前 Custom Tab 改动可复用该 rootfs 做 APK 编译门禁，因为新宿主不再发送激活旧 WebView CSS 的 UA。
- 截图链已用 USB 真机定位：display 16 空闲时 SurfaceFlinger 自己截该虚拟屏也是纯黑，bridge 返回 `no frame available`；把碧蓝航线 MainActivity 启动到 display 16 并产生真实内容后，两条截图链同时恢复，bridge 实测返回 1280×720×3 非黑帧。根因不是 GPU/CPU usage，而是 AP `Restart` 在 `app_restart()` 前先截图，空虚拟屏无首帧时形成恢复死锁。AP `dev` 已推送 `1841cb194` 跳过 Restart 前置截图，连同既有 Android 适配共 7 项针对性测试通过。AOS native 诊断会继续保留。

## 阻断与下一步

1. 以 AP `1841cb194` 重建完整 rootfs 和包含 native 截图诊断的 APK；通过后安装到当前 USB 真机。
2. 从“游戏未运行/虚拟屏无首帧”状态触发 Restart，确认能先启动游戏、等待首帧后正常截图；随后复测颜色、前台检查、WebUI、调度任务和停止清场。通过后再处理验证分支合入。
3. 完成虚拟屏、截图、触控、挂机和工具任务真机验收。虚拟屏实验前后检查 `GestureNav|GestureSilde|NavigationBar` 均在 display 0；结束杀虚拟屏属主并确认仅剩 display 0。实际游戏任务和屏幕状态实验遵守用户现场授权边界。
4. 多架构支持仍需分别构建 Python、原生依赖、OCR 和 rootfs，并按 ABI 打包选择；当前成品链是计划基线规定的 ARM64，x86_64 模拟器转译不能作为真机替代。
5. 自动发布需要在 `wess09/AzurPilot` 配置四个稳定签名 secrets。检测与构建完全由 AOS workflow 负责，不需要 AP 仓库 token。

## 注意

- 后续 AzurPilot Android 源码改动直接在其 `dev` 分支提交；AOS 只更新钉住的 commit 和兼容清单。
- 临时文件只放本仓 `.tmp/`。不要修改历史 `docs/roadmap-v3.md` 或 `m0-archive/`。
- 完整后台挂机未通过前不能视为适配完成。
