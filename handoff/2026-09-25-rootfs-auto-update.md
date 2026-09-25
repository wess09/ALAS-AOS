# rootfs 独立自动更新（2026-09-25）

## 已完成

- CI 发布 `azurpilot-android-dev` 时，除 APK 外上传 `rootfs.tar.xz`、`BUILD_MANIFEST`，并在 `latest.json` 中写入 rootfs 版本、URL、SHA-256 与大小。
- App 冷启动部署内置 rootfs 后检查该清单。新版归档下载并校验通过后，复用原有流式解包和目录替换流程；保留实例配置及日志。准备结束后才启动 proot。
- 已安装 rootfs 的网络检查、下载或解包失败时继续使用原版本。旧版索引无 rootfs 字段时跳过。APK 内置版本较新时仍能覆盖旧的独立更新。
- 部署页增加下载进度；设置页说明及 `development.md`、`devlog.md` 已更新。

## 验证与待办

- 中英资源键 572/572 对齐，`git diff --check` 通过。
- `:app:compileDebugKotlin` 在线构建通过（1m34s，26 个任务）；首次离线尝试因项目缓存缺少 Kotlin stdlib 失败，在线构建补齐后通过。
- 未推送或发布，因此当前 GitHub release 尚无新增 rootfs 资产；需用户明确授权发布后才能通过真机完整验证 GitHub 下载链路。
- 未做真机安装或运行中的会话迁移实验。该方案只在冷启动、proot 启动前替换，不热切正在运行的会话。
