# AzurPilot Android 适配交接（未完成）

## 已完成

- 用户确认独立 AzurPilot App、新包名与旧版并存、完整后台挂机、两个仓库协同改动、跟踪 fork `master` 热更新；不含 push 或发版。
- AzurPilot 工作区 `.tmp/AzurPilot-master` 基于 `origin/master` `88c4a41cea8aeaeafa7536db510d383ebba7213b`，分支 `codex/azurpilot-android`，原仓库 `dev` 检出未动。改动已导出为 `rootfs/patches/azurpilot-android.patch`；后续改动需重新导出补丁。
- Android 桥设备、配置生成、同进程控制 API、宿主运行链、独立端口/包名、构建脚本、更新和回滚脚本、文档均已初步实现。旧 ALAS 覆盖层与双进程监管从新构建链删除。
- AzurPilot 相关 85 项 Python 单测、更新 6 项离线单测及三组 OCR 模型本机 CPU 推理通过；补丁反向应用检查与 Shell 语法检查通过。

## 阻断与下一步

1. 在原生 ARM64 runner 执行 `rootfs/build/build-azurpilot.sh`，逐项修复 Python 3.14/aarch64 依赖、配置、OCR、前端和 manifest 门禁问题；目前没有成功 rootfs artifact。
2. 将构建产物装入 APK，完成 Gradle 构建；本机未找到可用 Android SDK/JDK，Docker daemon 未运行，不能声称 APK 已可安装。
3. 真机新旧双包并存及全链路验收。虚拟屏实验前后必须检查 `GestureNav|GestureSilde|NavigationBar` 均在 display 0；结束杀虚拟屏属主并确认仅剩 display 0。实际游戏任务和屏幕状态实验遵守用户现场授权边界。
4. 更新的六类离线场景已通过模拟测试，但尚需在 ARM64/proot 上验证下载、切换与恢复。`latest.json` 默认 URL 的运行时资产尚未发布；发版与 push 必须等用户明确指令。

## 注意

- 项目 `AGENTS.md` 明确不做 Git 提交、push、发版；当前两个工作区的改动均未提交。
- 临时文件只放本仓 `.tmp/`。不要修改历史 `docs/roadmap-v3.md` 或 `m0-archive/`。
- 根目录 `README.md` 已明确标记本版本未通过验收，切勿将能打开 WebUI 等同完整后台挂机完成。
