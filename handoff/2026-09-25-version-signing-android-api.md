# 版本号/签名标准化 + Android 控制 API 适配（2026-09-25）

## 已完成

### 1. 版本号标准化

`app/build-logic/convention/.../GitVersion.kt` 改为：

- `versionName = 1.0.<本仓提交数>[.<内置运行时的上游短 SHA>]`，例：`1.0.84.1841cb1941`
- `versionCode = max(本仓 HEAD 提交时间, 上游 commit 提交时间)`

三个输入全部是**提交数据**，与构建时刻无关 → 同一份「外壳 + 运行时」在任何机器、任何时刻构建结果一致，CI 与本地不再打架。原先 CI 用 `date +%s` 覆盖 versionCode、用 `git describe` 拼 versionName，同一份代码每次构建版本都不同。

上游那半由构建环境注入：`APP_RUNTIME_COMMIT`（短 SHA）与 `APP_RUNTIME_COMMIT_TIME`（秒）。CI 的 `resolve` job 用 GitHub API 取回上游 commit 的提交时间；`apk` job 构建后从 `output-metadata.json` 读回真实版本号给 `publish` 用。`APP_VERSION_CODE`/`APP_VERSION_NAME` 仍是手工钉版的逃生口。

### 2. 签名一致

新增 `app/app/debug.keystore`（Android 通用公开调试密钥，`androiddebugkey`/`android`/30 年），约定插件里 debug signingConfig 指向它。原先 debug 走 AGP 默认的 `~/.android/debug.keystore`——CI runner 每次新生成，导致每个 CI debug APK 签名都不同、互相覆盖安装报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。可用 `DEBUG_KEYSTORE_PATH` / `DEBUG_KEYSTORE_PASSWORD` / `DEBUG_KEY_ALIAS` / `DEBUG_KEY_PASSWORD` 覆盖。

### 3. 修「卡在环境准备」

`ui/azurpilot/AzurPilotScreen.kt` 原先等 `ProotPhase.RUNNING`（**wrapper** 就绪）才允许并自动打开浏览器；实际 `gui.py` 一起来 WebUI 就能用，外壳却还在「环境准备中」白拦。改为以 `AzurPilotRunController.reachable`（每 4s 打 `/android/configs` 的真实探针）为准。

### 4. 修「无法停止挂机」+「工具任务不可用」

上游 `instance()` 在请求不带 `config` 时回落到**硬编码的默认实例名**再过 `configs.path()` 校验，该名字在本部署不存在 → 400 NOT_FOUND。App 原先只给 `/start`、`/tool/start` 带了 `config`。

`proot/AzurPilotRunController.kt` 改动：

- `/status`、`/logs`、`/stop`、`/tool/stop` 一律显式带 `config`；停止优先用 `/status` 回报的**在跑实例**，否则用下拉选中项
- 刷新链改为先 `GET /configs`（不解析实例，是最可靠的「WebUI 活着」探针）→ 自愈选中实例 → 再取 `/status`、`/logs`
- `/configs` 拿不到才算不可达；`/status` 失败但 `/configs` 通 → 记为「可达但无实例状态」

## 验证状态

- `:app:compileDebugKotlin` 通过，无新增告警；`check_i18n_strings.py` 563/563 零差异
- 版本号：本地 `1.0.84`、注入运行时 `1.0.84.1841cb1941` 两种取法均验过
- 签名：`./gradlew :app:signingReport` 确认 debug variant 的 Store 已指向 `app/app/debug.keystore`
- **未做真机验收**：停止/工具/WebUI 就绪三条都是行为修复，需要装机走一遍

## 5. 接入 `/api/v1/ws` 富接口（用户选定：自启 + 任务总览、运行时热更新）

### 鉴权：本机直连免密，不需要播种密码

网关 `Gateway.is_local_client()` 的判定是：来源地址与 `Host` 头都在 `{127.0.0.1, ::1, localhost}`、且 **Origin 头为空或也是本机**。OkHttp 的 WebSocket **天然不发 Origin**，因此 App 直连即被认作本机、`Session.authorized = True`——**不用 WebUI 密码，也不用 `ALAS_WEBUI_TRUST_SECRET` 那条启动器通道**（那条通道在 AzurPilot dev 上还没挂路由，见下）。`AzurPilotGateway.login()` 已按 `auth.login` 信封留好，网关哪天收紧策略直接接上即可。

### 新增文件

- `proot/AzurPilotGateway.kt` —— WS 传输层：OkHttp 长连接（20s ping）、按 `id` 关联的请求/响应信封、`overview|instances|logs|preview` 主题事件流、指数退避重连、重连后自动恢复订阅
- `proot/AzurPilotApi.kt` —— App 侧状态层：连上后 `events.subscribe{instance, topics:[overview]}` + `overview.get` 兜底、`startup.get/set`、`updater.status`，产出 Compose 可收的 `StateFlow`

DI 注册在 `di/ProotModule.kt`；`AzurPilotApp.postCreate` 启动网关，并把运行控制器持有的「当前实例」转发给 `AzurPilotApi.onInstanceSelected`（两个控制器互不依赖）。

### UI

- **挂机页**：`AzurPilotControlPanel(showGateway = true)` → 调度总览一行（下一个任务 + 时间 + 总数）+ 「启动后自动开始挂机」开关。悬浮窗不传该参数，保持原样。
- **设置页**：新增「运行时」卡 —— 显示内置运行时的上游提交（取自 `updater.status.localHead`），并给一个「检查 App 更新」按钮（走已有的 `AppUpdateManager`）。

### 一个必须知道的上游事实：Android 下运行时热更是被有意关闭的

`module/api/update_service.py:53` 的 `status()` 开头就是 `if self.android: return {... 'available': False, 'managedByAndroid': True}`。也就是说 **Android 上不存在 git 式运行时热更**——运行时随 APK 整包走，所以 App 侧没有做 `updater.fetch/apply/cancel` 按钮，而是把用户引到已有的整包更新上。要真正的运行时热更，得先在上游放开这条分支。

### 未做

- **多实例与配置管理**（`instances.*` / `config.*`）：用户这次没勾，接口已通，加 UI 即可。
- **`/api/launcher/*`**：`alas-launcher` 调的 `trusted-login` / `stream` / `report` 在本仓检出的 AzurPilot dev 上**没有 HTTP 路由**（`module/runtime/launcher_trust.py` 与 `LauncherControl` 只有纯逻辑未接线），App 无法复用启动器那条免密通道。
