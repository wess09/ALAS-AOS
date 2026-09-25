# 2026-09-26 · AzurPilot for Android

## 最新进度

- 2026-09-26 后续变更（2）：rootfs 构建接入缓存。uv 下载缓存原本在 chroot 内（`/opt/uv-cache`）随 rootfs 一起删，现改为 bind-mount 宿主 `.tmp/azurpilot-build/uv-cache` 再挂回 guest；`UV_PYTHON_INSTALL_DIR` 要随 rootfs 出厂（venv 指向它），未动。CI 缓存 uv-cache / npm-cache / Ubuntu base 包三处；11ea118 的构建实测缓存已存下（压缩后 437MB），下次生效。卸挂校验加入 `opt/uv-cache`（否则残留挂载会让后面那句 `rm -rf` 删掉宿主缓存），构建末尾把缓存 chown 回 runner 供 post-step 打包。
- 2026-09-26 后续变更：删掉 rootfs 校验里两条无谓测试（上游 `dev_tools.import_smoke_test` 扫 536 个模块含 AidLux/docker/headless 等设备上不跑的模块，实测 124s；以及 OCR gate），保留 2 秒那条原生依赖 + `android_process_compat` overlay 校验。`Build rootfs` 从 7m41s 降到 **4m36s**。
- 2026-09-26 APK 编译曾默认切到 macOS arm64，实测后回退：`macos-26` 是虚拟化 M1（`VirtualMac2,1`，3 核 / 7168MB，单核基准 7.2 Miter/s，比 ubuntu-arm64 的 9.2 还低），`Bundle runtime and build APK` 用 12m33s（其中第一次全量构建 11m34s，97/97 任务全执行），ubuntu-24.04 同一步 6m06s；apk job 整体 mac 15m39s vs ubuntu 6m27s。默认改回 `ubuntu-24.04`，`apk_runner` 输入保留 macos-26 / macos-15。其余改动保留：第二次 assemble 去掉 `clean`（实测第二次 96 个任务里 86 个 up-to-date，仅 51s）、Gradle 缓存、GNU-only 用法换可移植写法、增量包体积断言、新增 `.github/actions/runner-info`（build 与 apk 开头打印机器规格与单核基准）。
- 2026-09-26 当前变更：Runtime 缺失（轻量 APK 未内置、或已装目录不完整）时不再停在「未内置」，改为自动从 GitHub Release 下载 rootfs 部署，与内置包走同一条解压流水线；部署页新增下载源选择（直连 GitHub / ghproxy 镜像，复用设置里的同一个开关），下载途中换源立即从零重下，读超时期间换源按换源处理而不报失败。调试包在失败态也保留「跳过」入口。README 四语与中英字符串同步；`:app:compileDebugKotlin` 通过，真机与 CI 链路未验证。
- 2026-09-25 当前变更：App 启动时独立检查 APK 新版本并弹选择；语言切换保留当前标签页，避免落入 WebUI；挂机页返回时以虚拟屏实际存在为准，并在运行/恢复期间隐藏误导性的启动按钮；关于页加 App 简介，界面中的运行环境名称统一为 Runtime。
- CI 读取 Latest 清单内上次真正构建 APK 的 `appCommit`：App 源码和 APK 工作流不变时只构建、发布 Runtime，保留已发布 APK 版本与下载元数据。旧清单首次缺少 `appCommit` 时会补建一次 APK，之后按变更跳过。
- README 已按用户此前修改保留产品介绍，并把运行环境称为 Runtime。当前需要完成本地 Kotlin 编译、提交与推送，再看新 CI 首次构建及后续 Runtime 单独发布验证。
- 2026-09-25 后续变更：运行时更新改为启动时提示、确认后才下载；CI 将生成 full 与 update 两种同签名 APK，应用内更新使用轻量版；APK 版本脱离 AP 提交，rootfs 版本改用 AP 提交 + rootfs 构建输入指纹。轻量版本地无签名打包验证通过、大小约 8.2 MB，不含 rootfs。需提交推送并以 CI 验证两种正式产物。
- 2026-09-25 新增 Android 专用 psutil 子进程枚举兼容层，针对用户 15:59 的停止失败日志；文件放在本仓 `rootfs/overlays/`，构建时写入 rootfs venv 的 site-packages，AP 上游跟踪文件保持原样。后续须通过 CI 构建并真机验证 WebUI/宿主两个停止入口。
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
