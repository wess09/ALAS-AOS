# 2026-09-25 · AzurPilot for Android

## 已完成

- 仓库地址与 App 更新地址迁至 `wess09/AzurPilot-for-Android`；本地 `origin` 已更新。
- App 显示名为 AzurPilot，默认 applicationId、namespace 和源码根包为 `com.azurpilot.ghio`；AIDL/JNI/混淆规则同步迁移。
- 依据 AP Logo 绘制了 Android 品牌图与简化启动图标，重写 README。
- CI 增加 `main` push 和每小时第 7 分钟触发；使用 Ubuntu ARM64 构建 rootfs，并发布到正式 Latest。签名 Secret 齐备时发布正式 APK，否则调试 APK 仅留在 Actions 构建产物。
- 仓库清理中的历史资料删除保留；项目规则仍要求的开发账册和 roadmap 已恢复。

## 验证与后续

- `:app:compileDebugKotlin`、Java、Manifest 与 ARM64 CMake 编译通过；中英资源 572/572 对齐，工作流 YAML 可解析。完整 release 与真机链路未运行。
- rootfs 版本号包含上游提交与宿主提交；宿主的 overlay/seeds 变化也会使应用识别到新 rootfs。
- 当前仓库尚未配置发布签名 Secret；自动生成并上传签名密钥的命令被执行环境自动审批拒绝，正式 APK 发布需要外部完成 Secret 配置。
- 未在真机安装新包，也未执行需要屏幕状态或虚拟屏实验的步骤。
- 工作区还包含用户主动清理的文件删除；本次不替用户提交或推送。
