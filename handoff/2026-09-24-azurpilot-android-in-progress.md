# AzurPilot Android 适配交接（未完成）

## 已完成

- Android 适配已直接进入 `C:\Users\AzurLane\Desktop\Projects\AzurLaneAutoScript` 的 `dev` 分支，提交 `b8f91d885e6d725aa800265178fdc2df631a6154` 已推送到 `origin/dev`。该工作区原有公告功能未提交改动保持原样，未进入本提交。
- Android 桥设备、配置生成、同进程控制 API、宿主运行链、独立端口/包名、构建脚本、更新和回滚脚本已实现。旧 ALAS 覆盖层与双进程监管已从新构建链删除。
- GitHub Actions ARM64 rootfs run `35948884651` 成功；复用该产物的 APK run `35950995233` 成功。验证分支 `codex/azurpilot-android-verify` 已推送，未做 release。
- 真机已完成安装、首启部署、热更新和 WebUI 拉起。首轮日志发现 `/proc/stat` 权限故障；适配层已用 `/proc/<pid>/stat` starttime tick 完成进程身份后备，并覆盖进程登记和清场链。针对性测试与原设备/API/OCR 测试共 87 项通过。
- 新增 `tools/watch-android-logs.ps1` 实时查看 App logcat 或 proot session 日志。对只提供 x86_64 原生 ABI、依赖 ARM 转译的模拟器给出明确失败原因。

## 阻断与下一步

1. AOS 构建已改为直接检出 AzurPilot `dev` 提交，需重新执行完整 ARM64 rootfs 构建；旧 rootfs 不含真机 `/proc` 修复，不能复用。
2. 用新 rootfs 构建 APK，交给用户覆盖安装后复测 WebUI 启动、实例列表、调度任务和停止清场。
3. 完成虚拟屏、截图、触控、挂机和工具任务真机验收。虚拟屏实验前后检查 `GestureNav|GestureSilde|NavigationBar` 均在 display 0；结束杀虚拟屏属主并确认仅剩 display 0。实际游戏任务和屏幕状态实验遵守用户现场授权边界。
4. 多架构支持仍需分别构建 Python、原生依赖、OCR 和 rootfs，并按 ABI 打包选择；当前成品链是计划基线规定的 ARM64，x86_64 模拟器转译不能作为真机替代。

## 注意

- 后续 AzurPilot Android 源码改动直接在其 `dev` 分支提交；AOS 只更新钉住的 commit 和兼容清单。
- 临时文件只放本仓 `.tmp/`。不要修改历史 `docs/roadmap-v3.md` 或 `m0-archive/`。
- 完整后台挂机未通过前不能视为适配完成。
