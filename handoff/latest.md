# 2026-09-25 · AzurPilot for Android

## 最新进度

- GitHub 正式签名 Secret 已配置；`main` 的 30be839 构建成功并向 Latest 发布了正式 APK。发布 APK 经本地下载校验：SHA-256 与 `latest.json` 一致，`apksigner` v2 验证通过。
- 后续修复集包含：CI 发布前签名验证；应用内更新使用按 SHA 命名的不可变 APK；运行时版本显示与 Latest 手动检查；关于页项目、许可和组件链接；切页动画误弹 WebUI 修复。
- 本地 `:app:compileReleaseKotlin`（只为代码编译排除需 rootfs 的打包验证任务）通过，中英字符串 580/580 对齐。完整正式 APK 构建需由推送后的 CI 验证。
- 本机签名密钥保存在忽略目录 `keystore/azurpilot-release.p12`，口令以当前 Windows 用户 DPAPI 保存在 `keystore/password.dpapi`。两者都不能入库，须妥善备份。

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
