# Devlog

> 倒序排列，最新在上；按发版版本号分段。
> 说明：本文件的历史条目中，指代本产品的名称已统一为当前命名（AzurPilot）；各代旧名见 Git 历史与 release 记录。
>
### 2026-09-25 · 未发版：正式签名与 CI 依赖修复
- 创建独立的 Android 正式签名密钥并将四项签名参数写入新仓库的 GitHub Actions Secrets；密钥和本地加密口令保存在忽略目录 `keystore/`，未入库。
- 首次正式版构建因阿里云 Maven 镜像返回 502 中断；调整依赖解析顺序，优先使用 Google Maven 与 Maven Central。

### 2026-09-25 · 未发版：新仓库、身份与 Logo；Latest 自动构建
- 仓库迁至 `wess09/AzurPilot-for-Android`，更新应用内 APK/rootfs 检查地址与本地 `origin`；App 显示名改为 AzurPilot，applicationId、namespace、源码/AIDL、JNI 和混淆规则统一为 `com.azurpilot.ghio`。
- 参考 AzurPilot Logo 设计 Android 版品牌图与简化图标，接入 README、传统及自适应启动图标；重写 README 的安装、使用条件、更新与构建说明。
- CI 增加 `main` push 与每小时定时触发，完整构建 rootfs 和 APK，并将 rootfs 发布到正式 Latest。发布签名 Secret 未配置时，APK 仅作为调试构建产物，Latest 先提供 rootfs；发布签名配置齐全后同时提供正式 APK。
- rootfs 清单版本同时包含上游提交与本仓提交，避免宿主 overlay/seeds 改动后只凭上游 SHA 判定「无需更新」。
- 核对仓库清理结果，保留用户移除的历史资料；恢复项目规则仍要求维护的 `AGENTS.md`、`development.md`、`devlog.md`、`debug.md`、`CHANGELOG.md` 和 `docs/roadmap-v3.md`。

### 2026-09-25 · 未发版：运行环境独立自动更新
- 启动时从 GitHub `azurpilot-android-dev` 发布通道检查 rootfs；下载完整归档，按清单大小与 SHA-256 校验，在启动 proot 前解包并原子替换。保留用户实例配置与日志；网络或更新失败则继续使用已安装版本。
- 发布流程新增 `rootfs.tar.xz`、`BUILD_MANIFEST` 资产及 `latest.json` 的 rootfs 版本、地址、校验值字段。旧清单缺字段时跳过运行环境更新。
- 安装包更新后的内置 rootfs 仍可覆盖较旧的独立更新版本；设置页说明同步改为独立更新流程。

### 2026-09-25 · 未发版：接入 AzurPilot 富接口（自启 / 任务总览 / 运行时）
- **接入 AzurPilot 富接口 `/api/v1/ws`**（用户选定「自启 + 任务总览」「运行时热更新」）：新增 WS 网关客户端与 App 侧状态层，挂机页加「调度总览 + 启动后自动开始挂机」，设置页加「运行时」卡。鉴权走**本机直连免密**——网关的 `is_local_client()` 只要求来源与 Host 是回环且无 Origin 头，而 OkHttp 的 WebSocket 天然不带 Origin，故无需 WebUI 密码。
- **运行时热更在 Android 下被上游有意关闭**：`update_service.py` 的 `status()` 开头即 `if self.android: ... managedByAndroid=True, available=False`，运行时随 APK 整包走。因此设置页那张卡只展示运行时提交并提供「检查 App 更新」（走已有整包更新器），未做 git 式热更按钮；要真热更需先在上游放开该分支。
### 2026-09-25 · 未发版：版本号/签名标准化，修 WebUI 就绪判定、停止挂机与工具任务

- **版本号标准化**：改为 `1.0.<本仓提交数>[.<内置运行时的上游短 SHA>]`，例如 `1.0.84.1841cb1941`。三个输入（提交数、上游 commit）都与构建时刻无关，因此同一份「外壳 + 运行时」在任何机器、任何时刻构建都得到同一个版本号，CI 与本地一致。原先 CI 用 `date +%s` 覆盖 versionCode、用 `git describe` 拼 versionName，导致同一份代码每次构建版本都不同、且与本地不一致。
- **签名一致**：新增仓内固定的 `app/app/debug.keystore` 并让 debug 构建走它。AGP 默认取 `~/.android/debug.keystore`——本地由 SDK 生成、CI runner 上每次都是新的，于是每个 CI debug APK 签名都不同、互相覆盖安装报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。该文件是 Android 通用的公开调试密钥（`androiddebugkey`/`android`），提交它正是为了让所有产物签名一致；可用 `DEBUG_KEYSTORE_PATH` 等环境变量覆盖。
- **修「卡在环境准备」**：WebUI 页原先等 `ProotPhase.RUNNING` 才允许/自动打开浏览器，而那是 **wrapper** 就绪的判据；实践上 `gui.py` 一起来、浏览器打开 `127.0.0.1:25548` 就能用，外壳却还在「环境准备中」白拦一道。改为以真实可达为准（复用运行控制器每 4s 的探针）。
- **修「无法停止挂机」与「工具任务不可用」**：上游 `instance()` 在请求未显式带 `config` 时会**回落到硬编码的某个实例名**再经 `configs.path()` 校验，该名字在本部署里不存在 → 400 NOT_FOUND。App 原先只给 `/start`、`/tool/start` 带了 `config`，`/status`、`/logs`、`/stop`、`/tool/stop` 都裸调，于是停止与工具停在「点了没反应」。现在四者一律显式带 `config`（停止时优先用 `/status` 回报的在跑实例），并把 `/configs` 挪到刷新链最前——它不解析实例，是「WebUI 进程活着」最可靠的探针，也让实例列表在调度器没起时照样可读。
- **验证**：`:app:compileDebugKotlin` 通过；`check_i18n_strings.py` 563/563 零差异；版本号本地与注入运行时两种取法都验过；`signingReport` 确认 debug variant 已指向仓内 keystore。


### 2026-09-25 · 未发版：宿主源码全量去掉历史品牌命名，统一为 AzurPilot

- **根包名**由 fork 时代的旧域名改为 `com.aliothmoon.azurpilot`：`main`/`test`/`androidTest`/`aidl` 与 `build-logic/convention` 的源码目录整体迁移，`namespace`、`AndroidManifest` 的应用类名、无障碍服务的 `settingsActivity` 同步；Gradle 约定插件 id 亦随之更名，`annotation-api`/`hidden-api`/`ksp-processor` 三个模块的 `id(...)` 一并更新。
- **类名与文件名**：主题入口、Application、协程调度器、设计 token、调色板与全部 UI 组件统一为 `AzurPilot*` / `App*` 前缀；业务侧运行控制器、运行态、控制面板、日志源与日志/错误现场各 VM 与 Screen 统一为 `AzurPilot*`；AIDL 接口与其中的版本查询方法改为通用名。对应文件名、`ui/azurpilot/` 等目录名同步更名——源码树已无任何文件名带历史品牌前缀。
- **与 native 及 R8 的契约同步**（漏改会在 release 下静默失效）：`native/bridge.cpp` 里 JNI 查找的类路径字符串、`proguard-rules.pro` 的 12 条 keep 规则。
- **资源名**：导航项、悬浮面板、日志页、WebUI 页与设置页说明等 27 处资源名统一改名，Kotlin 侧 `R.string.*` 引用同步；中英 563 键仍对齐。
- **运行时字面量**：跨进程标志文件名、悬浮窗 window tag、唤醒原因、日志 tag、虚拟屏显示名、桥线程名、FileProvider 路径名全部换前缀。
- **代码内注释与局部名**：业务代码里的局部变量/形参改用中性名；注释中作为产品名的旧名统一为 AzurPilot；指向已删除代码的过期引用改写为描述性说法；注释里提到的第三方项目名改为「上游 fork / 运行框架 / 参考实现」。
- **死配置与自指字面量**：删除 `.prettierrc.mjs` 与 `.prettierignore`（本仓无 `package.json`，那两个 npm 插件跑不起来；`.prettierignore` 的 5 条路径在本仓全部不存在）；`.gitignore` 去掉 `create-azurpilot-project` 标记块；`.github/workflows/rootfs.yml` 里硬编码的自仓 release 下载地址改为 `${{ github.repository }}` 表达式。活动文档里指代「本仓库」的旧项目名改为「本仓库 / 原有宿主」，两个历史文档文件名同步更名并修好交叉引用。
- **合并进来的 MD3 改造**（同日）：主题收口到官方 token（动态取色 + 基线色板）、组件全部换成现成 M3 件、自绘轮子与死代码清理，详见下一条。
- **历史文档与实验工程收尾**：`devlog`/`debug`/`CHANGELOG`/`handoff`/`docs`/`spike` 报告里的产品名、旧类名、旧包名按当前命名统一（`spike/` 里误入库的原始 logcat 抓取已删，只留 REPORT）；`app/README.md` 由上游 README 存档改为本模块说明 + 一行来源声明；三份账册文首加了「历史条目中的产品名已按当前命名统一」的说明。`docs/roadmap-v3.md` 同样只统一命名——**13 条决策一行未动，框图按原宽度重排**；不认可可 `git checkout -- docs/roadmap-v3.md` 还原。
- **收尾时揪出一处真 bug**：`seed_azurpilot.py` 播种的实例名与上游默认实例名、App 侧默认选中项三者互不相同。根因是上游 config 工具模块里 `filepath_config()` 的 `mod_name` 参数是**模块名**（基础模块 vs 附加模块），而实例名是同一模块里另一个独立常量——原脚本照着函数默认参数去拼文件名，实例名从一开始就错了。修正三处：seed 的 `INSTANCE_FILE` 改用上游默认实例名；原先写死的全局段名改为按结构定位（含 `Emulator` + `Optimization` 的段唯一，带断言，上游改结构会当场报错而非静默写错）；`AzurPilotRunController.DEFAULT_CONFIG` 同步对齐。细节见 `debug.md` 同日条目。
- **终态**：文件名、源码标识符、活动配置与资源名、入库文件字符串**全部为 0**。
- **验证**：`:app:compileDebugKotlin`、`:app:compileDebugAidl`、`:app:processDebugMainManifest`、`:app:mergeDebugResources`、`:app:kspDebugKotlin` 全绿；`check_i18n_strings.py` 563/563 零差异；工作树内文件名与源码标识符的历史品牌前缀残留为 0。


### 2026-09-25 · 未发版：宿主 UI 全量改为标准 Material 3

- 主题收口到 M3 官方 token：色板改为 Android 12+ 取系统动态色（Material You）、其余版本回落 M3 基线色板，删除自绘的暖石蓝与 Semi Design 两套色盘；形状、字阶、动效一律用 `MaterialTheme` 默认的 `Shapes` / `Typography`，不再按自定义圆角与字阶逐处覆写。
- 主题风格设置项整体移除（`ThemeStyle`、`AppSettings.themeStyle`、`SetThemeStyle`、`settings_theme_style*` 文案、`AppTokens` / `AppIconBadge` 等自定义圆角体系），设置页「显示」卡只剩主题模式与语言。
- 组件全部换成 M3 现成件：底栏由手写 56dp 行改为 `NavigationBar` + `NavigationBarItem`；卡片改 `Card`（分组）/ `OutlinedCard`（列表行）；按钮、开关、chip、对话框分别回到 `Button` / `OutlinedButton` / `Switch` / `FilterChip` / `AlertDialog`；底部弹层由自绘 Dialog 改 `ModalBottomSheet`；日志列表行改 `ListItem`；运行配置下拉由「按钮 + DropdownMenu」改 `ExposedDropdownMenuBox`。
- 删除自绘轮子：点击反馈 `clickable`（按压缩放 + 可关涟漪）改回 `Modifier.clickable` 标准涟漪；`MaterialIcons`（Material/Semi 双套图标切换）与整个 `:semi-icons` 模块（590 个 vector drawable）删除；`Type.kt`（自定义字阶）与 `Motion.kt`（自定义动效 token）删除。
- 顺带清掉已无调用方的死代码：`AdaptiveTextField` / `FloatWindowEditText`（自绘文本输入）、`InputFocus` / `ClearFocus`、`ExpandableTip` / `AppDescriptionPanel`、`AppEmptyState` / `AppSheet` / `clickable` / `MaterialIcons`，以及约束「按钮必须走 Button」的 `ButtonPrimitiveBoundaryTest`（该规则连同自定义圆角一起作废）。
- 顶栏不再逐页写死 `background` 底色与 `headlineMedium` 字号，改用 M3 默认的 `surface` + `titleLarge`。
- `:app:compileDebugKotlin` 通过，无编译告警；i18n 校验 563 键中英对齐。

### 2026-09-24 · 未发版：AzurPilot Android 独立版适配进行中

- 真机已定位 `no frame available`：display 16 空闲时 SurfaceFlinger 自身截图也是纯黑；把碧蓝航线 MainActivity 启动到 display 16 并实际出帧后，SurfaceFlinger 与 AOS bridge 同时恢复正常，bridge 返回 1280×720×3 非黑帧。根因是 AP `Restart` 在 `app_restart()` 前先截图，空虚拟屏无首帧导致恢复死锁。AP `1841cb194` 已让 Restart 跳过前置截图并加入回归测试；AOS 保留 native 帧链诊断用于后续定位。
- 用户确认不再使用 System WebView 承载 AP：AzurPilot 页改为浏览器 Custom Tab，环境 RUNNING 后自动打开 `127.0.0.1:25548`，浏览器不支持 Custom Tabs 时退回普通 ACTION_VIEW；关闭标签页回到 App。AP 中先前加入的 UA/CSS WebView 兼容提交已完整回滚，Chrome 与 AP 源码不再承担宿主兼容。
- 自动更新触发按用户确认收敛到 AOS 单侧：删除 AP 的跨仓库派发 workflow，AOS 默认分支每 15 分钟检查 AP `dev`。App 更新器补齐 HTTP 状态/响应大小检查，把系统安装器启动纳入异常处理，失败时删除残包并在更新弹窗显示原因，避免界面永久卡在“正在下载”。
- 锁定 AP `266f4222b` 的完整 ARM64 rootfs + debug APK CI（run `36002470839`）全绿，证明 System WebView 修复已进入可安装整包；AP 后续业务提交由轮询链自动取得。
- 首次包含 System WebView 修复的完整 CI（run `36001338936`）在 React 类型检查阶段失败，原因是 AP 新增 `dashboard.dogIcon` 后英文、日文和繁中翻译未同步；AP `dev` 随后以 `266f4222b` 补齐字段，本地 `npm run build --prefix frontend` 已通过，AOS 默认源码基线同步更新到该提交。
- 真机 Root 模式已能启动设备桥与虚拟屏。修复 Shizuku 路径的静默失败：挂机页和工具任务在启动前统一调用权限入口，未授权时触发授权流程，并在虚拟屏环境就绪后才向 AzurPilot 发起任务。
- 用户确认同机 Chrome 手机版正常，问题只在 App 内 System WebView。曾尝试的 UA 定向 CSS 虽消除了正文穿透，却因 `contain: layout paint` 与抽屉 transform 冲突导致侧栏完全不显示；该方案已撤回，最终改用浏览器 Custom Tab。
- 手机“自动更新失败”确认为内置 Git 更新器在无 `.git` 的 Android 运行时执行 `git fetch`。按用户后续决定改为 APK 级自动更新：AOS 每 15 分钟轮询 AzurPilot `dev`，CI 预构建前端/rootfs/OCR 后发布固定签名 APK 和校验清单；App 启动时提示下载、校验并覆盖安装。设备端不再执行 Git/npm/uv 更新，AP 仓库也不承担跨仓库触发。
- 按用户指令取消 AOS 内维护 AzurPilot patch 的方案：23 个 Android 后端、控制 API、配置生成、进程管理与测试文件已直接移植到 `C:\Users\AzurLane\Desktop\Projects\AzurLaneAutoScript` 的 `dev`，提交并推送 `b8f91d885 feat(android): 集成 AzurPilot Android 宿主适配`；该工作区原有公告功能未提交改动未被纳入。AOS 构建改为直接检出此 commit，删除源码补丁及 `adapter_sha256`，兼容清单改用 `android_api_version`。
- 真机首启已越过部署和热更新并启动 WebUI；用户日志暴露 Android/proot 禁止读取全局 `/proc/stat`，导致 `psutil.Process.create_time()` 在 worker 所有权认领阶段抛 `AccessDenied`。适配层现统一使用 `/proc/<pid>/stat` 的 starttime tick 作为 Android 进程身份后备，并覆盖登记、身份校验、子树枚举与强制清场，避免启动修好后在启停任务时再次失败；新增 2 项针对性回归，连同设备/API/OCR 相关 87 项测试通过。
- 增加 `tools/watch-android-logs.ps1`，可通过 adb 实时追踪 App logcat 或 proot `session.log`。模拟器 ABI 检查会明确提示 ARM 转译环境不能运行当前原生 ARM64 rootfs，避免继续以“正在热更新”掩盖 proot 卡死。
- GitHub Actions 已完成 ARM64 rootfs（run `35948884651`）和包含该 rootfs 的 debug APK（run `35950995233`）构建；验证分支为 `codex/azurpilot-android-verify`。本次 `/proc` 修复需要重新烘焙 rootfs 和 APK。
- 新增虚拟屏设备后端、配置源与生成物、同进程且带回环/token 校验的 Android 控制 API；`ProcessManager` 统一管理挂机与工具任务。
- Android 宿主改为独立包名、端口 `25548/22301`、单 WebUI 进程、首启配置种子、进程清场与更新失败回滚；去掉旧 AzurPilot `wrapper/runner` 和覆盖补丁。更换名称、图标、数据与日志路径。
- 新 ARM64 构建脚本安装 Python 3.14.6、锁定依赖、OCR 模型和预编译 React 静态资源；写入构建清单并生成同源运行时更新包。更新仅在适配层、锁文件和 Python 版本兼容时切换；旧版本可回滚。
- 本地 AzurPilot 相关 Python 单测、更新离线单测和 OCR 三组模型 CPU 推理通过；Shell 语法和补丁反向应用检查通过。ARM64 rootfs 与 APK 构建门禁已通过，真机完整后台挂机尚未通过验收；未做 release。

### 2026-09-21 · 发版 🚀：v0.1.4「日志中心重做」（用户授权 push + release）

- **提交**：`70fc569 feat(logs): 设置页日志区重构——日志中心 + 双导出 + 自动清理 + v0.1.4 发版准备`（51 文件，+1599/-833）；tag `v0.1.4` 打在发版 commit 上（versionName 由 git describe 导出）。
- **APK**：tag 上重建 1m35s 全绿（R8 keeps verified: 5）；badging package=`io.github.shinarin.azurpilot` / versionCode=66 / versionName=0.1.4；发版资产 `AzurPilot-v0.1.4-android-arm64.apk`（327,689,385 B，远端尺寸与本地逐字节一致）。
- **发布**：[v0.1.4](https://github.com/Shinarin/AzurPilot/releases/tag/v0.1.4) 已建（非 draft），notes 置顶安装说明（v0.1.3 直接覆盖；更早的旧名 旧版走 v0.1.3 迁移说明）。
- **网络坑**：直连 github.com 超时（curl 000）；探测本机常用代理端口发现 127.0.0.1:7897 可用。git 走命令级 `git -c http.proxy -c https.proxy`（未改任何 git 配置），gh 走 `HTTPS_PROXY` 环境变量。首次 `gh release create`（连资产一把梭）在 PATCH 阶段 EOF 且服务端未残留 release，改「先建空 release → `gh release upload` 传资产」两段式成功。
- **CHANGELOG/README**：CHANGELOG v0.1.4 段（日志中心/双导出/自动清理/调试模式移除，面向用户重写）；README 无设置页日志与调试模式相关描述，核对无需改动。
- **用户走查**：两个导出 zip 已在真机导出并核验格式——AzurPilot zip 镜像 `log/` 相对路径、近 7 天 txt、无 properties.txt；启动器 zip 含 app.log/session.log/debug/properties.txt。附带说明：无日期前缀的 `env_fix.txt` 会被 AzurPilot zip 收录（`LogExportCollector.kt:57` 无法判定日期→保留），用户已知情，倾向保留。设备上重复导出的旧 zip 与用户确认后已删。

### 2026-09-21 · 改造 🔧：设置页日志区重构（规格 A–H，用户确认稿）

- **A 日志卡 5 行**：启动器日志 / AzurPilot日志 / 导出AzurPilot日志 / 导出启动器日志 / 自动清理开关（关闭弹确认框）；调试模式行+确认框整删（`SettingsScreen.kt`）。
- **B 启动器日志页扩源**：新增 `LauncherLogScanner`（递归 `files/log/`，收 `app(\.\d+)?\.log`、`proot/session.log`、`crash/*.txt`，mtime 倒序，相对路径展示）；`AppLogViewModel` 换源，purge 仍只清 app.log 系列；详情页改统一尾部查看器 + canonical 校验。
- **C 尾部加载查看器**：新增 `LogTailReader`（512KB 尾块、切口对齐换行）+ `LogTailViewModel`（幂等 load / loadEarlier 前插）+ `LogTailScreen`/`LogTailContent` + `LogLineColors`（app.log `] E/W `、AzurPilot `| ERROR/WARNING |` 上色）；替代旧详情页整份读入。
- **D AzurPilot日志页**：新增 `AzurPilotLogSource`（`rootfs/opt/azurpilot/log`，daily/error 双源带纯数字与路径段校验）+ 3 个 VM + `AzurPilotLogScreen`（按天日志/错误记录两分区）+ `AzurPilotLogDetailScreen` + `AzurPilotErrorDetailScreen`（log.txt 查看器 + PNG 缩略图 LazyRow + 全屏 Dialog）；3 条路由 + 4 个 VM 注册。
- **E 导出拆两条**：`LogExportService` 重写（`enum LogExportKind`：AzurPilot=官方 issue 格式 zip、不附 properties.txt；LAUNCHER 维持 collector、常驻附 properties.txt；按前缀清旧 zip，LAUNCHER 兼清遗留 `azurpilot_logs_`）；`LogExportCollector.collectAzurPilot()`（近 7 天 txt+error）；Controller/AppRoot 按 kind 走。
- **F 自动清理**：新增 `LogCleaner`（删 <今天-7 天的 AzurPilot txt/error，Timber.w 汇总留痕）；`ProotHost` 注入 SettingsManager，`cleanupStale()` 加 session.log 截尾（>7 天未动且 >2MB 截留尾 2MB）；`上游 fork` 在 loaded 门控协程内 IO 调清理；`AppSettings` 换轴 debugMode→autoCleanLogs（Manager/Gateway/Contracts/VM 全链），`SettingsEvent.RestartApp` 整删。
- **G 删历史链条**：LogcatServiceManager / LogcatCaptureServiceImpl / ILogcatService.aidl / Misc.kt / AppLogDetailViewModel / AnsiAnnotatedText；RemoteServiceManager 的 logcat initialize 调用；AppFiles.LOGCAT_*；LogTrees verbose 门槛（app.log 常驻全量落盘）；proguard-rules 与 VerifyR8KeepsTask 同步删 keep。
- **H 字符串**：zh/en 双边同步；删 settings_log_archive_desc、run_log_*、log_outcome_*、settings_debug_mode*、dialog_enable_debug_*、common_restart、log_export_title 等；新增 settings_log_launcher_desc、azurpilot_log_*、log_tail_*、log_export_(azurpilot|launcher)_*、settings_auto_clean_logs*、dialog_disable_auto_clean_*。
- **构建**：assembleRelease 共 3 轮——① KDoc 里写 `log/*.txt` 触发 Kotlin 嵌套块注释 unclosed comment（LauncherLogScanner/LogCleaner 改写措辞修复）；② `VerifyR8KeepsTask` 漏删 LogcatCaptureServiceImpl 致 R8 keep 校验失败（删之）；③ 1m38s 全绿，"R8 keeps verified: 5 classes kept"。APK 327,689,337 B 已装机（AVAY025422002864）。
- **真机冒烟**（截图 `.tmp/smoke/`）：挂机页正常；设置页 5 行卡渲染正确、调试模式行消失、自动清理开关开；启动器日志列表=app.log+proot/session.log 相对路径正确；session.log 尾部查看器等宽渲染正常；AzurPilot 列表 7 个 txt mtime 倒序、无 error 目录时该分区正确隐藏；AzurPilot txt 详情 `| INFO |` 渲染正常；导出 sheet 标题按 kind 切换正确（导出AzurPilot日志 + 分享/保存到设备）；app.log grep 命中 LogCleaner 留痕「过期日志清理完成，删除 0 项，释放 0 B」，且 I/D 级行可见（常驻全量落盘生效）；session.log mtime 新，截尾未触发（预期）。
- **未实测项**：「加载更早」前插（设备无 >512KB 日志）；error 现场详情页（设备无 `error/` 目录）；导出 SAF/分享实际落盘链路（留用户走查）。`src/test/` 单测源集既存失修（引用 main 早已不存在的 runner/ 等类），assembleRelease 不编译它，非本次引入。
- **文档**：`docs/logging-dev.md` 已重写为 9 节现状文档；`development.md` 核对无需更新。git 未提交（等授权）。

### 2026-09-20 · 文档 📝：新增 `docs/logging-dev.md`（设置页日志功能开发文档，用户指令）

- 供另一会话接手「设置页日志部分改造」用的自包含事实文档：日志卡三条目现状（SettingsScreen.kt:170-227）、调试模式真实生效范围（仅 app.log 级别 + 导出附 properties.txt）、六份日志产物清单与滚动策略、查看页/导出链实现、AzurPilot /logs 链路、7 项已知坑（session.log 无界增长、debugMode 名不副实、logcat 抓取链整条休眠、死串 run_log_*/settings_log_archive_desc、详情页整份读入等）、四类改造切入点与验证命令。事实由 explore 子代理全仓摸底（文件:行号级），未改任何代码。

### 2026-09-20 · 收尾 ✅：旧版 更早的旧名 已由用户卸载

- 用户确认卸载，`pm list` 复核设备仅余 `io.github.shinarin.azurpilot`；新包冷启动 2s 到 RUNNING（22:25:56），双装端口占位隐患彻底关闭。v0.1.3 发版链全部收尾。

### 2026-09-20 · 发版 🚀：v0.1.3「AzurPilot 断代」（用户授权 commit/push/release）

- **提交**：`fc807ab feat(rebrand): AzurPilot 全量断代更名 + 启动链双加固 + v0.1.3 发版准备`（121 文件，+770/-8147）；tag `v0.1.3` 打在发版 commit 上（versionName 由 git describe 导出，确保 APK=0.1.3 精确版）。
- **APK**：tag 上重建 1m15s 全绿，badging package=`io.github.shinarin.azurpilot` / versionCode=63 / versionName=0.1.3 / label=AzurPilot；发版命名 `AzurPilot-v0.1.3-android-arm64.apk`（327,667,485 B）。
- **发布**：[v0.1.3](https://github.com/Shinarin/AzurPilot/releases/tag/v0.1.3) 已建，notes 置顶「安装前必读」（卸载旧版→装新版→重授 Shizuku→勿双装），资产尺寸与本地逐字节一致。push main + tag 均已上 origin。
- **更名整改闭环**：R1-R3 三轮 + build 补漏 + 双加固（STALE_FILES 删除 / supervise 熔断）+ 真机复验（旧包端口占位事故始末见前条）全部入账，handoff 见 `2026-09-20-release-v013.md`。

### 2026-09-20 · 文档 📝：README 增名字由来 + shizuku-m 提示（用户指令）

- **名字由来**：开头引言区加 `AOS = AzurPilot on Android OS——AzurPilot 的 Android 系统版`。
- **shizuku-m 提示**：第 1 步激活方式后加提示块——嫌每次启动 Shizuku 都要连 WiFi 走无线调试，可参考社区 fork [Shinarin/shizuku-m](https://github.com/Shinarin/shizuku-m)（端口重新挂载，重启免无线调试、无 WLAN 可启动；按实加 caveat：手机重启后首次激活仍需官方方式一次，与 devlog 2026-09-15 事实核查一致）。

### 2026-09-20 · 扫尾 🔎：更名全仓再梳理（应用户要求复核"能改但没改"）

- **本轮修正（活文档/活代码的事实性残留）**：① `debug.md:100` 故障排查 tip 的状态文件名 `.azurpilot_commit`→`.azurpilot_commit`（运行时早已改名，tip 断链）；② `docs/roadmap-v3.md` 4 处 `azurpilot.py`→`azurpilot.py`（决策表/架构图/数据面/附录A——开发宪法是活文档，**推翻此前"roadmap-v3 按 D 类历史文档不动"的处理**；`m0-archive/docs/azurpilot-consensus.md` 引用保留——归档文件真叫这个名）；③ `strings.xml:91` 注释 `参考实现`→"上游 上游 fork 系"（唯一用户可见 res 里的 azurpilot 字样，纯注释）。架构图框线宽度复核与原文一致（原图本就 79/80 混排）。
- **复核确认干净**：README/AGENTS/CHANGELOG/development.md、strings（zh+en）、通知渠道文案、仓库 description/badges、`.github/workflows/rootfs.yml`、rootfs 运行时状态文件名（`.azurpilot_commit`/`.azurpilot_update_fail_date`）、in-app URL（仅 scrcpy 上游 issue 链接）、活跃区无 azurpilot 命名文件。
- **刻意保留（复核无误）**：Java 包名 `com.aliothmoon.azurpilot`（内部 namespace）；`keystore/azurpilot-release.jks`（历史签名留档）；`m0-archive/`、`spike/`、devlog/handoff 历史条目、`debug.md` 历史条目（`com.azurpilot.spikea` 等是当时事实）；工作目录名 `D:\VSCodeCache\azurpilot-azurpilot`（local.properties keystore 绝对路径依赖）；`app/README.md` 上游存档。
- **`.tmp/` 说明**：含旧 azurpilot 探索副本（azurpilot-explore/m5-netresil/upd 等），gitignore 内 scratch 不碍事；**不可整目录清**——`.tmp/gradle-home` 是 GRADLE_USER_HOME，清了要重下整套 Gradle 分发与依赖。

### 2026-09-20 · 断代 ⚡③：桥接标识符按新命名全量整改 + 真机复验（旧包端口占位事故，双修复）

- **整改范围**：R1 `MAAAL_`→`ALASAOS_`（全 rootfs Python/shell + ProotHost.kt setter）、R2 `azurpilot`→`azurpilot`（含文件改名 `method/azurpilot.py`、`seeds/azurpilot_update.sh`）、R3 `AzurPilot`→`AzurPilot`（22 文件，含 build/ 两脚本）；双源（rootfs/↔app assets）cmp 全一致；残留扫描零命中。本轮补抓 R1 漏网：`rootfs/build/` 两处——build-rootfs.sh 两行注释、spike-f-ocr-gate.py 四个环境变量引用；其中 gate 设 `MAAAL_OCR_MODEL_DIR` 而 rpc.py 读 `ALASAOS_OCR_MODEL_DIR` 是**功能性断链**（--model-dir 会被 rpc 无视），非仅命名残留。
- **真机复验事故（21:14~21:43）**：新包首启后 wrapper 以 3s 一轮崩溃重拉 500+ 轮（session.log 311KB）。根因链：旧包 `com.aliothmoon.azurpilot`（更早的旧名 v0.1.2）仍装机且其 proot 会话存活（20:14 起，u0_a270）→ 占死 22300/22267/22400 三端口 → 新包 wrapper bind(22400) EADDRINUSE 崩溃 → `awaitServices` 被旧包 wrapper 喂成**假 RUNNING** → 退避计数被重置 → 无限重拉。**期间一切"设备跑旧码"证据（'azurpilot' WARNING、旧配置值）均来自旧包 rootfs 的日志——新包栈实际健康**，此前"烘焙旧码未覆盖"的怀疑不成立。
- **修复①**（AzurPilotOverlay.kt）：新增 `STALE_FILES` 删除列表（`module/device/method/azurpilot.py`、`seeds/update.sh`），每次启动覆盖后删除——烘焙 tar 仍带旧名文件，而 apply() 只加不删，防旧码被误加载/误选。
- **修复②**（ProotHost.kt）：supervise 加速死熔断——会话存活 <10s（QUICK_DEATH_MS）记一次 rapidDeath，连续 5 次（MAX_RAPID_DEATHS）→ `fail("会话连续 5 次秒退（端口被占用？），已停止重拉")`；长活后退出计数清零，正常崩溃退避行为不变。
- **止血与验证**：force-stop 双包 → 端口全释放（旧会话 stdin 看门狗自尽正常）→ 重装新 APK → 21:43:28 干净 RUNNING（env_fix 幂等快路径仅 1s）；`/configs`=["azurpilot"]；挂机冒烟：AzurPilot 日志桥值全 `azurpilot`、零 `azurpilot`，黑帧 WARNING 为游戏冷启动瞬态（上游通用文案，值随配置走）；游戏重启→登录成功→scheduler pending tasks 正常排队→`/stop` 复位。assembleRelease 41s 全绿。
- **配置兼容结论**：原"method 值 `azurpilot` 存在用户配置里故保留"的决策随 appId 断代自然消解——新包=全新安装=全新播种 `azurpilot` 配置；旧包配置在其私有区，新包永不触碰，无需迁移代码。
- **遗留**：旧包 更早的旧名 仍装机（已 force-stop），建议择期卸载（卸载即清其 AzurPilot 配置，操作在用户）；下版 release notes 卸载提示照旧。全部改动未 commit/push（等授权）。

### 2026-09-20 · 跟进 ✅：上游 #6000 证据已上传回复（log+截图打包件 + 三张对比图）

- **回复落地**：2026-09-20 12:04 UTC 以 Shinarin 账号在 [#6000](https://github.com/LmeSzinc/AzurLaneAutoScript/issues/6000) 提交证据评论（1958 字符，与填充内容逐字一致）。附件全部为 GitHub user-attachments 原生上传：`issue-6000-log-and-screenshots.zip`（2026-09-19 完整卡死会话日志 261KB + 三张截图）+ `material-enter-loop-log.txt`（分段节选）+ 新 UI 实拍/2022 旧模板/左下锚点放大三张图。正文要点：环境一句话披露（Android 上跑上游同源码）、空点循环事实（~3.3s/次、45 分钟 45+ 次、`[Total_Disassemble]` 始终 0/15）、日志后段 AzurPilot proxy 报错免责一句、ccoeff 0.04 vs 0.85、重申愿提 PR（黑底+稳定锚点，验收材料页 ≥0.9 / 邻近页 ≤0.3）。
- **上传渠道**：webbridge 驱动用户 Edge（GitHub 已登录），页内构造 `ClipboardEvent(paste)` 直传 user-attachments——绕开 gh 不支持附件 + 新版 composer 懒加载 file input 两个坑；期间撞一次 GitHub 上传限流（"You can't perform that action at this time."），冷却 60s 重试成功。评论框 React 受控，用 webbridge `fill`（触发原生 input 事件）写入。
- **事实核查（按用户要求，上传前逐项过）**：①45 次空点 grep 计数实证、45 分钟跨度（09:57:50→10:42:19）实证；②上游 12 次点击保护阈值实读桌面 `module/device/device.py:299` 确认；③发现 09-19 会话日志中每段循环在第 ~9 次点击后 runner 即被重拉、**全文无 GameTooManyClickError traceback**（该词为 issue 正文基于桌面端代码的推断；回复正文照实只写「空点循环 + 任务反复失败重拉」，未把 GameTooManyClickError 说成日志实见）；④zip 装包后逐项核对文件清单与大小。
- **真机 error 目录取证结论**：真实 `log/error/<ts>/` 在 App 私有区（release 无 run-as、shell uid 无权读私有目录、wrapper 只有 /logs 尾行端点、桥 22300 shell 同为 shell uid）——唯一通道是 `adb backup` 全量拉私有目录（估 0.5~1GB、10~40 分钟、新版 Android 有抽风风险）。与用户确认后采用等效重打包：完整主日志 ⊇ error 目录 log.txt 内容、实帧截图 ≈ error.png。若上游坚持要 error 目录，再跑 backup 补传。
- **下一步**：盯上游回应。自修 → 删双源补丁资产发新版 + 删桌面端救场文件；要 PR → 按 handoff §7-B 做黑底+稳定锚点版（锚点取底部页签栏或左下提示区）；搁置 → 维持现状补丁 + 定期巡检（同类 #4815 open 16 个月先例）。
- **git 未提交改动**：本条目 + handoff 状态更新。

### 2026-09-20 · 决策 🛑：roadmap-v3 主线 goal 用户取消

- 用户指令「goal 模式可以取消了」。roadmap-v3 主线 goal（阶段一→五连续开发）此前为 blocked 状态，现经用户明确取消，**本窗口不再 resume**；后续工作一律按用户当次指令驱动。历史账册（devlog/handoff/roadmap-v3）保留不删。

### 2026-09-20 · 清理 🧹：.vscode 瘦身（留通用配置，删 m0 死配置）

- **判定保留**：prettier/markdownlint 扩展推荐、prettier 格式化 + eol + formatOnSave 设置。
- **判定删除**：`nekosu.azurpilot-support`/`windsland52.azurpilot-log-analyzer` 扩展推荐（运行框架 m0 遗产）；`settings.json` 的 `json.schemas`（指向已不存在的 `tools/schema/`，fileMatch 是 运行框架 资源布局）；`tasks.json` 整文件（pnpm check/lint/format，根目录已无 pnpm 工程）。
- **附带发现（未动）**：`.prettierignore` 里仍有 azurpilot 时代条目（`.create-azurpilot-project`、`上游公共资源目录`、`resource/base/model/ocr/`、`tools/schema`），不影响功能，待顺手时清理。
- **git 未提交改动**：.vscode 两文件 + tasks.json 删除（未授权 commit/push）。

### 2026-09-20 · 断代 ⚡：App 整体换身份（appId `io.github.shinarin.azurpilot` + 新签名 CN=AzurPilot），构建验证全绿

- **用户指令**：app 整体改名，第一次更新提示无法覆盖更新可接受。
- **appId**：`com.aliothmoon.azurpilot` → `io.github.shinarin.azurpilot`（build-logic BASE_APPLICATION_ID 单点；manifest providers `${applicationId}` 占位自动跟随；运行时全走 context.packageName，无硬编码引用；Java 源码包名保留为内部 namespace）。
- **签名**：新 keystore `keystore/azurpilot-aos-release.jks`（CN=AzurPilot，RSA 4096/10000 天，SHA-256 f52207ec…），local.properties 已切换；旧 azurpilot 证书退役留档不删。
- **断代影响**：新旧包名+签名均不同 → 新版=全新应用，老版本须卸载重装（配置随卸载清除，需先导出）；首启重新授 Shizuku。下版 release notes 必须带此提示。
- **验证**：assembleRelease 2m04s 全绿（R8 keeps 6 类过）；aapt badging package=io.github.shinarin.azurpilot / label=AzurPilot / arm64-v8a / versionCode 62 / versionName 0.1.3-alpha.1；apksigner V2 CN=AzurPilot。
- **git 未提交改动**：build-logic 插件、local.properties（gitignore 内，仅本机）、keystore（gitignore 内）、前述全部品牌改动（未授权 commit/push/发版）。

### 2026-09-20 · 更名 🏷②：仓库 `Shinarin/AzurPilot` + App `AzurPilot` + azurpilot 残留大扫除

- **用户指令**：仓库名和 app 也改名；项目内残留 azurpilot 部分（如 .kimi-code）删去。
- **仓库**：`gh repo rename AzurPilot` 已执行（view 验证 `Shinarin/AzurPilot`），本地 origin 已 set-url；旧链接 GitHub 自动重定向。
- **App**：strings.xml（zh+en）app_name/log_export_subject/notification_test_message 改 AzurPilot，XML 校验过；**签名 keystore（文件名/别名/CN=azurpilot）与 appId `com.aliothmoon.azurpilot` 刻意不动**（改了老用户无法覆盖更新），local.properties 注释已注明；APK 文件名是发版手工改名步骤，下版用 `AzurPilot-v*-android-arm64.apk`，README 下载行已同步。
- **.kimi-code 大扫除**：13 个 运行框架 通用技能全删；`azurpilot-azurpilot-phone-debug` 审阅确认内容为 m0 时代 pipeline 调试闭环（与 v3 架构不符）整体删除；mcp.json 删 azurpilot-mcp/create-azurpilot-project（留 playwright）。
- **配方残留**：pi-profile.m0.yaml、app/pi-profile.sample.yaml、app/INTEGRATION.md 删除（BuildProfile.kt dormant 能力保留，无品牌暴露）；app/README.md（上游存档）两处死链划线标注。
- **用户可见文案修正**：run_log_empty/settings_debug_mode_desc/dialog_enable_debug_message（zh+en 6 条）"运行框架 日志"→实际（AzurPilot 输出/特权进程详细日志）；provider_paths.xml/UiText.kt/AppSettings.kt 注释同步；regen_args.py 的 WebUI 桥接选项显示名 `AzurPilot 桥`→`AzurPilot 桥`（双源 cmp 一致）。
- **保留项**（详见 handoff/2026-09-20-rebrand-azurpilot-aos.md）：appId/签名/azurpilot method 值与 Python 日志前缀/fork 内部类名/app README 存档/m0-archive/本地目录名。
- **git 未提交改动**：上述全部（未授权 commit/push/发版）。

### 2026-09-20 · 更名 🏷：项目更名为当前命名（各代旧名见 Git 历史），README 去除上游致敬段

- **用户指令**：项目改名 AzurPilot；README 删除 azurpilot 部分，只保留 上游 fork 架构参考。
- **改动**：README.md 全篇更名 + 删 上游组织/运行框架 致谢行（上游 fork 上游基座条目保留）；AGENTS.md（标题+红线条）、debug.md（6 处）、docs/rom-matrix.md、docs/stage2-azurpilotapp-inventory.md 同步更名。
- **未动**（事实性/功能性标识，非品牌）：App 名称与 APK 文件名仍为 更早的旧名（签名 CN、appId `com.aliothmoon.azurpilot`、代码内 `azurpilot` 方法名均不动）；GitHub 仓库仍 `Shinarin/AzurPilot`（远端改名为授权项，README 链接暂保留原名，改名后 GitHub 会自动重定向）；devlog/handoff 历史条目不改写。
- **git 未提交改动**：README/AGENTS/debug/rom-matrix/stage2-inventory/本条目 + 前条 handoff 改动（未授权 push）。

### 2026-09-20 · 动态 ⚠️ + 交接 📄：上游 #6000 已回复（要 log+截图），跟进手册落 handoff

- **上游动态**：LmeSzinc 于 2026-09-20 02:53 UTC 回复 [#6000](https://github.com/LmeSzinc/AzurLaneAutoScript/issues/6000)：「上传log和截图」。下一步为整理证据（现成素材在 `.tmp/`：卡死日志 `azurpilot-log-0919.txt`、新 UI 实拍/旧模板对比图等）上传并回复。
- **交接文档**：`handoff/2026-09-20-issue-6000-followup.md`（应用户要求，供其他会话窗口接管跟进）——issue 档案 + 证据清单 + 真机补拍流程 + 修复现状 + 情景预案（上游自修/要 PR 则做黑底+稳定锚点版/搁置/质疑备答）+ 执行检查单。
- **移交确认（09-20 晚）**：用户明确 #6000 移交其他会话窗口处理，**本窗口不再跟踪**；`handoff/2026-09-19-release-v012.md` 未决事项相应销项。
- **git 未提交改动**：本条目 + handoff 新文档/销项（用户要求只写文档，未授权 push）。

## v0.1.2（2026-09-19 发布）

### 2026-09-19 · 发布 ✅：v0.1.2 热修复迭代（科研卡仓库 + 时区 + 孤儿治理）+ 上游 issue #6000 提交

- **产物**：`更早的旧名-v0.1.2-android-arm64.apk`（312MB），GitHub Release [`v0.1.2`](https://github.com/Shinarin/AzurPilot/releases/tag/v0.1.2)（notes `.tmp/release-notes-v0.1.2.md`）；tag 钉在 `4c4f50e`（versionName 0.1.2 / versionCode 61）。
- **内容**（v0.1.1 之后共 4 提交）：MATERIAL_CHECK 重校准（`902a6a4`）、proot TZ=CST-8（`e77a66c`）、runner 黑帧孤儿治理（`41a1cbd`）、发版 docs（`4c4f50e`）；本日午后 CDN 全量同源复验 + E 触发面考证随 docs 提交入账。
- **验证**：apksigner CN=更早的旧名 与历版一致；aapt badging 0.1.2/61/arm64-v8a/label 更早的旧名；真机覆盖安装（0.1.2-alpha.1→0.1.2，13s）冒烟：dumpsys 实证版本，wrapper/gui 在岗，`gui_started_at` 21:31:07 与设备时钟一致（TZ 修复在 release 链路复证）；runner 待用户手动开启（冒烟顶掉了 alpha.1 在岗 runner）。
- **上游 issue**：[LmeSzinc/AzurLaneAutoScript#6000](https://github.com/LmeSzinc/AzurLaneAutoScript/issues/6000) 已提交（CN MATERIAL_CHECK 失效全案：ccoeff 0.04 实测 / E 科研+仓库开箱影响面 / 04-26→09-17 时间线 / 临时修复 / 愿提 PR——若提则做黑底+稳定锚点版而非整屏实帧版）；release notes 与 CHANGELOG 互相引用。
- **push**：main `5566b83..4c4f50e` + tag v0.1.2 已推（代理 7897）。
- **git 未提交改动**：无（收官 docs 即本条目与 handoff，随后即提交推送）。

### 2026-09-19 · 修复 ✅：科研卡仓库死循环（游戏 UI 改版打废 MATERIAL_CHECK 模板）+ proot 时区 UTC→CST + 孤儿游戏开局死循环

- **用户报障**：①挂机科研任务「自己识别到进入仓库」后卡住；②AzurPilot 日志时间与现实对不上。
- **根因 1（科研卡仓库）**：E 系科研项目（用户预设 series_9_blueprint_ta152）启动后走 `storage_disassemble_equipment`（research.py:270）→ `_storage_enter_material` 循环确认材料页。游戏仓库页 UI 改版（旧版左下独立「素材」标签 → 新版左下「请选择要操作的素材」提示），上游 `assets/cn/storage/MATERIAL_CHECK.png` 模板匹配 ccoeff 实测 **0.04**（阈值 0.85）→ 永不确认 → 每 3s 空点 MATERIAL_ENTER（已在材料页，点激活页签=无操作）→ GameTooManyClickError → runner exit(1) → wrapper 重拉 → 再卡——**session.log 累计 87 次 runner exited/respawn**。模板年龄考证（回应「上游早该修了」质疑）：GitHub API 实证该文件 **2022-08-21 创建后从未改动**，桌面仓库与 GitHub master **逐字节一致**（桌面 git log 里的「2026-03-27」是 lyoko CDN 镜像仓库历史重写的假象，非真实编辑日期）——上游确实四年没碰过它，因为仓库页那个角落四年没变，直到最近游戏改版。同类事故史：#4276（2024 JP 同位置同症状「卡在 _storage_enter_material 出不去」，已修 JP 资产）、#4815（2025-05 CN 开箱数量选择卡死，**至今 open**）、#4934（2025-07 CN 拆 15 件卡死）。同服同版本游戏 = 同一 UI 布局（提示条是内容数据非渲染差异），桌面端跑到此步同样必卡。「桌面为何没触发」实证：桌面那份上游配置 同为 ta152 预设，但日志考古显示拆解流程**上次实际触发是 2026-04-26**（当日 2022 旧模板秒过：enter material→USE BOX 9ms、0/15→15/15 八秒完成，证明 4 月时游戏还是旧 UI）；近五个月桌面未再跑过此路径（今日 20:11 桌面 Research 槽位 waiting、3 秒结束未进仓库）——不是不会卡，是游戏改版后桌面还没轮到卡。排查手段：release 无 run-as，改走 wrapper HTTP（`adb forward 22400`，POST /start 解锁 runner 日志为 latest.txt 后 /logs 拉取）+ 桥 22300 行 JSON 协议手驱动截屏/点击（`.tmp/vd_probe.py`），对 14 个 storage 资产逐一离线算 ccoeff+色差（`.tmp/asset_score.py`）。
- **修复 1（不动上游源码，走既有 patches/assets 校准通道）**：真机材料页实帧重制 `MATERIAL_CHECK.png`（新帧 ccoeff=1.0，对装备/设计/拆解页 ≤0.27，色差 6.4<30 免改 assets.py），双源落 `rootfs/patches/assets/cn/storage/` + `app/assets/azurpilot/patches/assets/cn/storage/`（cmp 一致，第 8 个真机校准资产）。连带验证全拆解链资产：MATERIAL_STABLE_CHECK 模板陈旧但 wait_until_stable 只吃实时帧无害；METERIAL_SCROLL 模板分低但 Scroll 类只吃构造色 (247,211,66)、实采滚动条金像素 401 个在位；DISASSEMBLE(_CANCEL/_CONFIRM)/EQUIPMENT_ENTER/STORAGE_CHECK/TEMPLATE_BOX_T1/BOX_USE/BOX_AMOUNT_OCR 全部在位。
- **根因 2（时间差 8h）**：rootfs 未装 tzdata 且环境无 TZ → proot 全环境 UTC（wrapper `gui_started_at` 10:47 vs 设备 18:47 实证）。修复：`ProotHost.baseEnv` 加 `TZ=CST-8`（POSIX 形式，UTC+8 无 DST，不依赖 zoneinfo 文件）——一行覆盖 proot 全部进程（wrapper/runner/gui/pip/env_fix）。副作用可自愈：config 里 UTC 时代写入的 NextRun 首次全部到期 → 任务集中补跑一轮后重排正常。
- **根因 3（顺带实锤的开局死循环）**：App 重装/重启拆 VD 后游戏成**无窗孤儿**（pid 活、窗口随旧 VD 销毁、新 VD 纯黑帧）→ AzurPilot 只按 pid 判活 → ui_ensure 拿黑帧 → GamePageUnknownError → `checker` 确认服务器在线 → exit(1) → wrapper 重拉孤儿依旧 → 无限 respawn（本次实测 respawns 连涨 3 次不回血）。为何以前能自愈：孤儿被系统隔夜回收后走 GameNotRunningError→Restart 链路；本次孤儿新鲜所以锁死。修复：`runner.py` 进 `azurpilot.loop()` 前加 daemon 同款黑帧判定（全屏通道均值和 <1）→ 孤儿即 `app_stop` → 调度器走 GameNotRunningError → `task_call('Restart')` 官方任务重新拉起到 VD（画面健在则绝不动，同 daemon 语义）。双源同步 runner.py。
- **真机验证（0.1.1+三修复装机，19:32 会话）**：①TZ——wrapper `gui_started_at` = 19:22:46 与设备时钟一致（修复前 10:47）；②孤儿链——runner 启动 → 判黑清孤儿 → Commission → GameNotRunningError → `Task call: Restart` → 35s 后 `[UI] page_main`，respawns=0 稳定在岗；③科研拆解——`DISASSEMBLE EQUIPMENT → Goto page_storage → storage enter material` **41ms 一次通过**（修复前 30s 空点死亡），`USE BOX → TEMPLATE_BOX_T1 → BOX_USE → BOX_AMOUNT_OCR 1→15` 全链在位，`[Total_Disassemble] 0/15 → 15/15` 9 秒完成，调度器随后正常推进 Commission/Dorm/Guild。
- **git 未提交改动**：patches/assets/cn/storage/MATERIAL_CHECK.png×2（新增）、ProotHost.kt（TZ）、runner.py×2（孤儿清理）、账册。

### 2026-09-19 · 复验 ✅：E 科研触发面 + CDN 全量三渠道同源（回应「上游不可能不修」）

- **用户追问**：E 科研是否碰到必炸？这么多人用，上游（含 fullcn 的 CDN 渠道）会不会早已修好、只是我们没拉到？→ 拉全量实证。
- **三渠道同源（无任何隐藏修复）**：123clouddisk `latest.json` = `git ls-remote git://git.lyoko.io` = GitHub master = `74e8231`（2026-09-18，PR #5997 只动 island/shop/i18n）；`git clone` 全量 9437 文件落 `.tmp/cdn_check/repo`，与桌面端对整个 `assets/cn/`、`module/` `diff -r` **全空**（仅 `__pycache__`）；`MATERIAL_CHECK.png` 三处 sha256 全同（`1e87852a…`，即 2022 版）。GitHub API 文件史：仅 `faf5a74e` **2022-08-17** 一个提交（订正前文 08-21）。
- **E 触发链代码实证**（research.py:265-271）：8 种 genre（B/C/D/E/G/H/Q/T）中**唯一** `genre=='E' and equipment_amount>0` 分支调 `storage_disassemble_equipment` → 必经 `_storage_enter_material` → 必过 MATERIAL_CHECK——模板失效则 E 科研**碰到必炸**，其余 7 种不碰仓库。
- **「这么多人用为什么没人修」四因**：①触发面窄——ta152 预设里 E 排第 13 位之后、默认自定义过滤器 E2 也在尾部（均「含 E」但垫底）；实证桌面 5 个月 50 天 Research、**0 次 E 选中**（"Going to start an E series" 仅 04-10/04-26 两条日志）；②bug 年轻——04-26 桌面同链跑通（`Used 15 box(es)` 干净收尾），UI 改版是 4 月底之后某版本的 silent 改动（9-8 幽影迷城大更、9-17 公告正文均无仓库 UI 条目）；③selector.py:246 有「仓库无箱忽略 E」保护（萌新不踩、箱多的老玩家才踩）；④AzurPilot 卡死自愈重试把症状粉饰成「科研偶尔犯病」，带日志上报者寥寥——同链 #4815 开 16 个月未修，此链本就低维护区。
- **结论**：桌面/CDN/上游三方同款过期资产+同款代码，跑 E 必卡；我们的补丁是当前唯一真机验证过的修复。上游 issue/PR 是否提交待用户决策（若提，建议把模板改成旧版风格的黑底+稳定锚点，而非整屏实帧，避免格子内容差异拖垮整屏相关度）。
- **git 未提交改动**：账册。

## v0.1.1（2026-09-18 发布）

### 2026-09-18 · 发布 ✅：v0.1.1 首个功能迭代（启动提速 + 活动图修复 + OCR 升级）

- **产物**：`更早的旧名-v0.1.1-android-arm64.apk`（311MB），GitHub Release `v0.1.1`（notes `.tmp/release-notes-v0.1.1.md`）；tag 钉在 `976da2e`（versionName 0.1.1 / versionCode 56）。
- **内容**：CDN 热更新双通道、启动静默期治理、azur_lane 字体 OCR 上机、D3 双根因修复、保屏唤醒 + env_fix 日志修复（6e08c23/9e1a6b3）、README 重写；**CHANGELOG.md 新建**（用户向，AGENTS.md 第四节首次落地，含 v0.1.0 追溯段）。
- **验证**：apksigner 签名 CN=更早的旧名 与 v0.1.0 一致；aapt badging = versionName 0.1.1 / versionCode 56 / arm64-v8a / label 更早的旧名；真机覆盖安装（0.1.1-alpha.9 → 0.1.1）冒烟：开机链 20:15:33→20:15:37 **全程 4 秒**（CDN UPTODATE 路径），wrapper/gui 在岗、status API 正常（runner 待用户手动开启）。
- **流程改进**：本次把 CHANGELOG + devlog 分段**先入 tag 再构建**（v0.1.0 时收官 docs 在 tag 之后），tag 内含完整用户向账册。

### 2026-09-18 · 优化 ✅：热更新双通道——CDN pack 优先（复刻上游 git_over_cdn）+ git:// 兜底 + 失败当日退避，开机链 4m04s→5s

- **背景（用户追问定性）**：内置 fullcn 包却走 git:// 慢通道的原因=CDN 代码在 AzurPilot 内置更新器里（`deploy/git_over_cdn/client.py`，GitOverCdn 由 `Repository==lyoko && Branch==master` 自动派生），而 AzurPilot 为保护钉版+补丁重放顺序用 `AutoUpdate:false` 锁死了它，自写的 `update.sh` 当年只有 git:// 一条路。git.lyoko.io 无 443 服务（实测 HTTPS 握手失败），9418 裸 TCP 在运营商网络下 fetch 连续 5 次烧满 240s 超时。
- **落地**：新增 `rootfs/seeds/cdn_update.py`（零依赖 stdlib 复刻上游协议：`latest.json`(3s) → `{latest}/{current}.zip` 增量 pack(20s 读超时) → 落 `.git/objects/pack/` + 写 refs；四态输出 UPTODATE/PACK_READY/NO_PACK/UNAVAILABLE 供 bash 分流）；`update.sh` 改双通道——CDN 优先、NO_PACK/UNAVAILABLE 回落 git:// 原有路径、两通道皆败记当天日期当日不再试（次日自愈）、UPDATED 后补丁重放链路不变。`MAAAL_UPDATE_NO_CDN` 环境变量可关 CDN 排障。双源同步（rootfs/seeds + app assets overlay/seeds，cmp 一致）。
- **PC 端到端测试（真 CDN）**：UPTODATE（0.86s）/ PACK_READY（旧 commit 46fe341 真下载增量 pack **仅 399KB**+6KB idx，对比 git 浅树几十 MB）/ 假 sha 403→NO_PACK / 退避秒跳 / 关 CDN 回落 git 正常——全过。PC 侧唯一意外是 MSYS 路径幻觉（`/d/...` 被 Windows python 落出影子树 `D:\d\`，已清理；设备上无此问题）。
- **真机验证（alpha.9 装机）**：启动链 `[host]` 时间戳实证 **19:47:59 清理残留 → 19:48:00 UPDATING → 19:48:01 检查完 → 19:48:04 RUNNING，全程 5 秒**（今早同一链路 4 分 04 秒）；设备本已是最新 commit，CDN 检查 1 秒 UPTODATE，无重放（符合预期）。下次上游推新 commit 时 CDN UPDATED 路径将自然触发（观察点：session.log 出现「重放本地补丁」阶段行）。
- **git 未提交改动**：cdn_update.py×2（新增）、update.sh×2、账册。

### 2026-09-18 · 优化 ✅：启动静默期治理——阶段迁移落盘 session.log + 挂机页实时显示准备明细（顺带定位「开机慢」真凶=热更新）

- **用户点单**：①release 版 PREPARING 阶段日志强制落盘；②挂机页加「环境准备中」状态提示。顺带回答「为什么现在每次开 App 都要加载很久」。
- **落地 1（落盘）**：`ProotHost.setState/fail` 每步阶段迁移写 `[host] MM-dd HH:mm:ss PHASE detail` 进 `proot/session.log`（与 `[proot-out]` 交错成完整生命周期时间线；release 版 Timber 只落 W+ 的盲区由文件直写兜底）。
- **落地 2（UI）**：`AzurPilotControlPanel` 注入 ProotHost 状态——wrapper 不可达时按 proot 阶段区分显示：`环境准备中 · {阶段明细}`（PREPARING/UPDATING/STARTING）/ `启动失败：{原因}`（FAILED）/ `环境未就绪`（其余）；中英字符串各三条；悬浮窗与挂机页共用此组件，同享改进。
- **真机验证（alpha.8 装机）**：session.log 实证 `[host] 12:43:22 PREPARING 清理残留 → … → 12:47:26 RUNNING` 逐阶段留痕；截图实证挂机页状态行显示「AzurPilot 调度器：环境准备中 · 检查 AzurPilot 热更新」，按钮正确置灰。
- **「开机慢」定量（本次实测，亦回答用户疑问）**：准备链全程 4 分 04 秒，其中**热更新检查独占约 4 分钟**（12:43:23→12:47:2x，ls-remote/fetch 在本机网络下慢至超时边界）——其余步骤（overlay 同步/env_fix/播种/regen_args/proot 拉起）合计仅数秒级。「以前没这么慢」的对应关系：这条链是逐次加上去的（overlay 补丁机制、env_fix 钉版自检、regen_args、热更新），且 proot 下 syscall 密集型操作慢 5~10 倍。**后续候选（未做，交用户决策）：热更新改为异步——wrapper 先上线、更新后台跑，或给 ls-remote 加短超时档。**
- **虚拟屏 +1 疑云澄清（回答用户疑问）**：旧屏**不残留**——App 进程一死，特权进程经 linkToDeath（主）+ 5s 心跳看门狗 `/proc/<pid>`（兜底）自杀，`cleanup()` 释放 VD 后 exitProcess；即便 SIGKILL，VD 经 binder 注册在系统 DMS，进程死亡即被系统移除。+1 只是 Android 全局 display id 计数器单调递增不复用；实证：手机重启后计数清零，本次新屏为 #9（此前 #29）。
- **git 未提交改动**：ProotHost.kt、AzurPilotControlPanel.kt、strings.xml×2、AGENTS.md（新增「AzurPilot 上游代码红线」）、账册。

### 2026-09-18 · 修复 ✅：azur_lane 字体 OCR 权重搬家上机——mxnet→纯 numpy 移植，与桌面逐位一致，真机双酸试通过

- **背景**：手机端通用 PP-OCR 读不了 AL 字体（D1 徽标 '01' 毒害、dock 等级 'MRT' 崩溃、`[campaign] [D3, ai, B2]` 误读）；桌面用上游 cnocr densenet-lite-gru（39 字符 AL 字体微调）但依赖 mxnet（无 ARM64 wheel、已退役）。决策（用户拍板）：搬权重不搬环境——PC 端转储 mxnet 权重，手机端纯 numpy 重写前向。
- **转储**：`.tmp/ocr-azurlane/dump_weights.py`（桌面 toolkit python 跑，mxnet 只存在于 PC 侧）→ `weights.npz`（76 数组，3.3MB）+ `label_cn.txt`（39 类含 blank）；`dump_refs.py` 产出 13 组参考批（真徽章/名字图/噪声×单图/混批）的分层激活（emb/rnn/logits/prob）+ golden 字符串。
- **numpy 移植**：`rootfs/overlays/module/ocr/al_numpy.py`（新建，零 module.* 依赖）——densenet-lite（BN eps=1e-5、valid 池化、k(2,3) depthwise + **k(2,1)** 末段池化（符号 json 实证，非文档所载 (2,2)））→ BiGRU（cuDNN 变体：gate 序 r/z/n，n=tanh(i2h_n+r·h2h_n)，h=(1-z)n+z·h_prev，mxnet rnn_cell.py 源码实证）→ FC(39)；预处理/补齐/置信门 0.5/width//4 截尾/CTC 解码/cand_alphabet 乘法掩码全部逐字复刻上游。性能：einsum→im2col+sgemm 后 PC 单行 329ms→**18ms**。
- **对拍**：`.tmp/ocr-azurlane/{np_forward.py,test_al_numpy.py}`——13/13 批 prob max|Δ|≈2e-6~5e-6，字符串全同，OVERALL PASS。
- **rpc.py 双引擎路由**：`azur_lane` → numpy 字体模型（单行/成批/atomic 全走它，整页 ocr()=PP det+AL rec）；其余 lang → PP-OCR 不变；set_cand_alphabet 恢复上游状态语义；模型缺失自动回落 PP-OCR。路由集成测试（stub module.* 真模型）PASS。
- **双源落位**：`al_numpy.py`/`rpc.py` 进 rootfs/overlays + app assets overlay；`weights.npz`/`label_cn.txt` 进 rootfs/overlays/models/ocr/azur_lane/ + app assets 对应路径（overlay 机制每次启动幂等铺到 /opt/azurpilot，免重烘 rootfs）。
- **真机验证（2026-09-18 晨，0.1.1-alpha.7 (52) 装机）**：①AzurPilot 日志 `AzurPilot OCR: loading azur_lane numpy model from ./models/ocr/azur_lane` → `azur_lane numpy model loaded (39 classes)`（16ms 加载，无 fallback 警告，proot numpy import 兼容）；②酸试 1 活动图——`[campaign 0.013s] ['D3', 'D1', 'B2']`（**D1 读准**，PP-OCR 时代误读 'ai' 消失），顺利进图，D3 连刷两轮（BATTLE_0~7 × 2），油读数 8930→8653→8618 连续合理递减（「只刷一遍就停」的油量误读同步消除），全程 0 ERROR；③耗时 `[campaign 0.013s]`/`[OCR_OIL 0.02~0.07s]`，ARM 端与 PC（18ms/行）同量级；④酸试 2 GemsFarming（'MRT' 崩溃源）未到 NextRun（昨日崩溃后 failure 延迟，当日日志窗口无调度记录）——OCR 栈同源已证，留待自然调度复验。
- **插曲·「App 自体死亡」第三次疑似复发后澄清**：装机后 monkey 拉起 App 一度 wrapper 不应答、ps 查无 alioth 进程；本次实为启动链静默 PREPARING 段（overlay 同步→env_fix→热更新 ls-remote，release 版 Timber 只落 W+，logcat/文件日志全静默）+ 系统侧杀进程叠加，**无 crash 文件、无新 tombstone**（排除 App 崩溃）；第二次拉起后 ~4 分钟自愈，wrapper/gui/runner 全部上线。悬案收窄：死因非代码崩溃，方向=系统后台管理/装机 force-stop 余波。
- **现场交还**：runner 运行中（Event D3 连刷，用户挂机意图），油 ~8600。

### 2026-09-18 · 定性 ✅：「活动图只刷一遍就进队列」非 bug——心情控制延迟 + 任务到点抢占

- **用户报告**：活动图（Event D3）只刷一遍就被算作完成、放回队列，油量充足理应能连刷。
- **取证**（wrapper `/logs`，2026-09-17 21:59~22:07 窗口）：一轮完整 D3（BATTLE_1~7）于 22:01:45 `<<< CAMPAIGN END >>>` 正常收官，油 10527、Count: 0（不限次），**无任何停止条件触发**。
- **根因 1·抢占**：`Guild` 到预定时间 22:00:00 → `Switch task Event → Guild`（AzurPilot 标准任务抢占，Event 即刻回队列；Guild 15 秒跑完后 Event 于 22:02:00 自动恢复）。
- **根因 2·心情保护（关键）**：Event 恢复后进 D3 战前检查，1 队心情 43、预计单战 -12 → `Delay current task to prevent emotion control in the future` → `Event.Scheduler.NextRun=22:30:00` → `SCRIPT END: Emotion control`。**这是上游 AzurPilot 心情控制（Emotion Control）的标准保护行为**：再刷会跌破红脸线（掉好感），主动延迟 ~28 分钟等心情自然恢复，到点自动继续刷。
- **结论**：环境链路完全正常，无需任何代码改动。想无视心情连刷可在 WebUI 的 Event 任务里把心情控制模式改为「无视」（不推荐）；后宅可加速心情恢复。
- **顺带目击**（另案，不入本次）：GemsFarming 选旗舰崩溃 `ValueError: invalid literal for int() with base 10: 'MRT'`（dock 找旗舰时 OCR 把等级读成 'MRT'）——与既有「紧急委托选关报错」悬案同源方向（通用 PP-OCR 读不准 AL 字体），证据已留 `.tmp/q-once/full.txt`。

### 2026-09-18 · 文档 ✅：README 全面重写（用户点单）

- **用户要求**：针对本项目写一份完整详细、重点讲使用的 README；简单易懂、可用表情符号；Shizuku 部分引导到官方仓库、不提 shizuku-m；末尾致谢借用的仓库。
- **落地**：全文重写 `README.md`——特性/一图流/安装/使用/机型/FAQ/构建/致谢/许可 九段式；使用章扩为「三个界面分工 + 第一次使用 + 日常操作（全屏触摸/小工具/热更新）+ 两个重要提醒（WebUI 启停锁定、重启恢复链）」；Shizuku 引导改指 [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) 及官方激活指南（无线调试/adb/root 三方式），shizuku-m 及「官方版冲突」FAQ、顶部 TODO 注、「仓库」段全部移除；致谢段新增 AzurPilot / 上游 fork / Shizuku / proot / PaddleOCR / 运行框架 / Ubuntu 七家。
- **注意**：rom-matrix.md 与 roadmap 等内部文档仍含 shizuku-m 记述（开发者向账本，未动）；本次仅改用户向 README。

### 2026-09-18 · 修复 ✅：D3 崩溃循环真根因双破案（TITLE 模板失配 + D1 徽标名 OCR '01' 毒害）——终验 BATTLE_1~6+boss 零错误

- **推翻旧定论**：此前"活动图崩溃=上游缺陷×未 3 星手动模式，环境层无解"的结论被用户一句"桌面端可以刷 D3"推翻。桌面与手机同版本 AzurPilot、同一张图，差异只能出在**识别层**——顺此查出两个互相独立的断点，全部有像素/模型级实证，均已修复并端到端验证。
- **断点 1（已修）·周回/自律开关 TITLE 模板手机渲染失配**：手机 D3 面板 visibly 有「周回模式 ON」「自律寻敌」开关，但上游模板（为 MuMu 锐化滤镜调校）在手机渲染下 TM_CCOEFF_NORMED 仅 **0.829/0.783 < 0.85 阈值** → `SwitchClearMode.get` 返回 'unknown' → clear_mode 丢失 → MAP_HAS_AMBUSH 保持 True → 手动模式踩潜艇伏击 → MAP INIT 死等 → MapDetectionError 崩溃循环。桌面能刷正是因为桌面认出了开关（伏击被周回模式关闭）。**修复**：用手机实拍帧（整帧 1280x720，与上游同约定）自制 `CLEAR_MODE_TITLE.png`/`AUTO_SEARCH_TITLE.png`，走 AzurPilot 既有 assets 补丁机制双源部署（`rootfs/patches/assets/cn/handler/` + `app/app/src/main/assets/azurpilot/patches/assets/cn/handler/`，cmp 验证一致），重打包 release APK 装机——overlay 每次启动幂等铺到 /opt/azurpilot，AzurPilot 代码零改动（合规）。离线验证：新模板对新帧 sim=1.0 位置正中，旧模板复测 0.829/0.783（确定性失败非噪声）。
- **断点 2（已绕）·D1 未通关徽标名字图 OCR 出 '01' 毒害章节识别**：断点 1 修好后 runner 仍死在更前面——`ScriptEnd: Campaign name error`（campaign_ui.py:418），根本没进图。机制：ensure_campaign_ui 的 `_get_stage_name` OCR 读到 `['01','D3','B2']`，'01' 经处理变 '0-1' → chapter='0' → Counter 平票取首见 → `campaign_chapter=='0'` raise CampaignNameError。'01' 来源=D1 徽标（0% 未通关红签小字）名字图里 **D 字形二值化后糊成实心团**（`.tmp/d3cap/nameimg_HALF_0.png` 铁证；D3/B2 的 Clear! 大签名字图字母内孔清晰）。**决定性对质**：桌面 AzurPilot 的 azur_lane cnocr 模型（AL 字体微调，charset 39 字符）对**同一手机帧同三个名字区**读出 `['D1','D3-','B2']`——像素无罪，是手机端 AzurPilot 换用的通用 PP-OCR 读不了 AL 字体的糊 D。候选通用模型 A/B 全灭：v5_ch_mobile 'ս'、v4_ch_mobile 'սս'、v4_en_mobile 空串、v5_en_mobile 字典不匹配。**绕法（本次执行）**：手动把 D1 打通（桥点击作战：5 杀出 boss、boss S 胜、威胁排除 100%），徽标变 Clear! 大签样式 → 名字 OCR 不再糊 → 毒害本活动内消除。通用修法（azur_lane 模型上机）留作后续项。
- **终验（runner 端到端，日志+图像双证）**：重启 runner → 登录链 → `[Stage] d3, ai, b2`（d3 正确读出并选中，无 '01' 无 CampaignNameError）→ `[Map_info] 99%, star_1, star_2, 100_percent_clear, clear_mode`（**clear_mode 出现=断点 1 补丁生效的在线证据**）→ `Clear_Mode on` / `Auto_Search on` / `[Auto_Search_Setting] fleet1_mob_fleet2_boss, sub_standby` → BATTLE_1~6 连续 `Auto search moving → Combat end` → boss 战（Lv.105 幽影迷城-驱逐，截图 `final_proof.png` 自律战斗中）→ 最近 200 行日志 **0 ERROR / 0 MapDetectionError / 0 CampaignNameError**。
- **悬案入账**：App 本体死亡 2 次（均伴随 runner 崩溃循环后发生，死时游戏被杀、VD 重建换号 16→18；monkey 拉起后 proot ~15s 自愈），原因未查明，需下轮查 logcat/墓碑。
- **局限与后续项**：①手动清 D1 的绕法只治本活动——下次新活动 0% 徽标复发风险在，通用修法=azur_lane 字体模型上机（MXNet→ONNX 转换或纯 numpy 手写 densenet-lite-gru 前向，集成进 rpc.py 按 lang 路由）；②紧急委托选关报错（三件套 #2 另一半）流程不同、证据未收集，用户复现再查；③桌面 AzurPilot 全程只读未改（其 toolkit python 跑 OCR 对质属合规用法）。
- **git 未提交改动**：断点 1 模板双源 4 文件（rootfs/patches 2 + app assets 2）。

### 2026-09-17 · 修复 ✅：adb 转发占用 22267 撞车桌面版 AzurPilot WebUI（桌面打开显示手机内容）

- **用户报告**：打开桌面版 AzurPilot 时，WebUI 显示的是项目（手机）里 AzurPilot 的内容；并质疑桌面 AzurPilot 是否被我改动。
- **桌面 AzurPilot 未被我改动（证据链）**：本会话完整命令记录对 `C:\other\AzurLaneAutoScript` 零引用；其 git 已跟踪文件零修改（HEAD 92c07aa 是其自带热更新所升）；toolkit imageio=2.27.0（上游钉版，site-packages 最新文件 2023-06）；上游配置文件 mtime 9-12。PC 对照实验全部在项目内 `.tmp/venv-gif*`（系统 Python 3.14 venv）跑，素材取项目内 `.tmp/azurpilot` 副本。用户 20:17 看到的"电脑上操作 AzurPilot"=我 `start http://127.0.0.1:22267` 用浏览器遥控**手机** WebUI。
- **撞车根因**：`adb forward tcp:22267 tcp:22267` 把手机 WebUI 绑到 PC 的 22267 且调完未拆，与桌面版 WebUI 默认端口撞车——桌面 webui bind 失败、浏览器连到转发显示手机内容（adb.exe PID 3828 占用实锤）。
- **修复**：拆 `tcp:22267` 转发，改挂 **`tcp:32267 → tcp:22267`**（手机 WebUI 的 PC 侧入口固定 32267），netstat 实证 22267 已释放、32267 HTTP 200。新规则入 debug.md。
- **尾款提示**：撞车窗口期若用户在劫持页改过配置，实际写入的是**手机端**——需用户核对两边配置。

### 2026-09-17 · 调查 ✅：活动图崩溃循环=上游缺陷×D3 未 3 星（环境层无解，方案交用户决策）；另修 env_fix 60s 装机事故

- **用户报告**：刷活动图（幽影迷城 D3）自动退出挂机，日志刷 `WARNING | Image to detect is not in_map`；截图=游戏停在「敌方潜艇出现中」伏击战画面，调度器仍"运行中"。
- **事故链插曲（已修，失误认领）**：设备实际在跑 T2 验证时装的 0.1.1-alpha.1 (46)——`ENV_FIX_TIMEOUT_MS` 实为 60s（300s 版改了源码没装机）→ proot 下 pip 永远跑不完被杀 → imageio 半装 → runner 14-37s 连环崩（respawn #1-#27）。修复：`ProotHost.kt` env_fix 失败输出改 W 级落盘（FileLogTree 只收 W+，i 级逐行 release 不可见），构建装机 **0.1.1-alpha.2 (47)** 后实证 runner 零进程级崩溃、env_fix exit=0。**教训入账 debug.md：HEAD≠装机版，验证修复前先核 versionCode。**
- **崩溃循环根因（100% 复现，证据链闭合）**：Event(d3) → 选关 → 进图 loading 9% `Entered map with is_combat_loading appeared`（enter_map 设计内出口）→ 直接 `MAP INIT`（上游 campaign_base 对伏击战零处理）→ 潜艇伏击战（40-90s）画面无地图元素 → `not in_map` ×11~28 → ~18s 容错耗尽 `MapDetectionError` 穿透 run.py 顶层（只捕 ScriptEnd）→ `Saving error` → 进程连崩退出 → wrapper 重拉 → `Already in map, retreating` 撤退重进 → 再踩伏击 → **死循环**。
- **触发条件=D3 未 3 星**：日志 `Map_info 99%, star_1, star_2, 100_percent_clear`（无 star_3/clear_mode）→ MAP_PREPARATION `No auto search option.` → 只能手动模式 → 必踩进图伏击；D3 spawn_data 实锤 battle 0 双 siren + `MOVABLE_ENEMY_TURN=(2,)`。
- **上游实锤**：issue [#5969](https://github.com/LmeSzinc/AzurLaneAutoScript/issues/5969)/[#5970](https://github.com/LmeSzinc/AzurLaneAutoScript/issues/5970)（2026-09-11，同活动 B3，**桌面雷电模拟器同款崩溃**——排除 proot 环境）；修复 commit 46fe341 只加 AUTO_SEARCH_TITLE2（自律寻敌开关 JP 模板），**不覆盖手动模式进图伏击**；设备 AzurPilot 经热更新 ≥46fe341 仍崩=佐证。
- **观察到的实质**：崩溃循环≠完全卡死——游戏自律在 AzurPilot 崩溃间隙实际有推进（柴郡 boss S 胜、掉落富特/伦敦、油 13237→12884），但每张新图必崩一次、ERROR 刷屏，体验不可接受。
- **方案（交用户决策，环境层无解，红线不改 AzurPilot）**：A 换已 3 星图挂 / 先手动把 D3 打到 3 星；B AzurPilot 层崩溃签名检测+退避+UI 明示；C 报上游 issue 等治本。
- **现场交还**：runner 已停（/stop 实证 runner_wanted=false）、游戏 force-stop、油 ~12884。另补录次要异常：14:22:42 `GameTooManyClickError: SWITCH_20241219_COMBAT`（图内切模式按钮连点 12 次无效）。

### 2026-09-17 · 修复 ✅：三项真机问题（T1 保屏 / T2 选关崩溃 / T3 半自动点击）——T2 走"环境钉版"路线

- **用户三条修复要求**：①挂机/半自动点击/活动剧情运行时保持屏幕唤醒；②刷紧急委托（GemsFarming）选关卡步骤崩溃退出挂机；③半自动点击只把游戏拉回主界面、不触发盯屏功能。**用户拍板约束：不许改 AzurPilot 代码——"本软件要做的就是为 azurpilot 构筑好环境可以跑通功能，不要越俎代庖"。**
- **T1 保屏 ✅**：`MainActivity.kt` 注入 `AzurPilotRunController`，`runnerAlive || toolAlive` 时 `window.addFlags(FLAG_KEEP_SCREEN_ON)`，停止即清。真机验证：runner 存活 `dumpsys window` 见 `fl=KEEP_SCREEN_ON`，停后消失。
- **T2 选关崩溃 ✅（根因=环境未钉版，非 AzurPilot 代码）**：`cv2.error: (depth == CV_8U || depth == CV_32F) && type == _templ.type()` 崩在 `campaign_ocr.py:266 matchTemplate`——rootfs 烘焙时 `build-rootfs.sh` 装了不钉版 imageio 2.37.x，2.35+ 把 P 模式 GIF 模板（`TEMPLATE_STAGE_CLEAR_20240725.gif` 等）解码成 RGB 3 通道，而上游 `requirements.txt` 钉的 2.27.0 首帧给 2D 调色板索引；灰度图 match 3 通道模板即触发断言。**路线转变**：先做了一版 template.py 通道归一补丁（未提交），用户拍板"不动 azurpilot 代码"后**补丁双源删除无痕**，改走环境钉版：
  - `build-rootfs.sh` 钉 `imageio==2.27.0`（对齐上游，含注释）；
  - 新增 `rootfs/seeds/env_fix.sh`（双源同步 app assets）：每次启动自检 imageio≠2.27.0 则 pip 钉回（aliyun 镜像，断网/失败不阻塞）；随后**白名单单文件** `git checkout -- module/base/template.py` 还原旧补丁遗留（只此一路径，绝不整树 checkout——overlay 合法补丁每启动重铺）；mark 写 stdout+`log/env_fix.txt`；永远 exit 0。
  - `ProotHost.kt` 启动链在 overlay 之后插入 env_fix 调用，专用 `ENV_FIX_TIMEOUT_MS=300_000`（**坑：proot 下 pip 比原生慢一个量级，首次 60s 超时被杀**）。
- **T3 半自动点击 ✅（黑帧孤儿补判）**：此前修复（daemon 先 `azurpilot.run('start')`）在"游戏进程活着但 VD 画面全黑"（孤儿窗，App 重装/VD 重建后）场景失效——游戏在跑就不 start，对着黑帧空转。runner.py daemon 分支加判：游戏在跑**且** VD 画面非黑帧（`float(image.mean())*3 >= 1.0`，对齐 AzurPilot check_screen_black）才跳过 pre-start；黑帧则照常 app_start 救回。AzurPilot 代码零改动。
- **验证（全部真机/PC 实证）**：PC 对照实验——imageio 2.37 组复现设备同款 cv2 断言，2.27 组模板解码 (20,30) 2D、上游形态 matchTemplate 命中 3 个 Clear! 徽章；真机 E2E——GemsFarming 选关 OCR `Click @ d3` 两次点中（原崩溃点通过）→ MAP_PREPARATION → 进图开战，全程无 cv2 error；T3 热路径（skip pre-start 零 GOTO_MAIN）与冷孤儿路径（黑帧警告→app_start→Login success）双分支验证。
- **未直接验证项**：env_fix 的 template.py 还原行设备侧输出不可读（release 版 Timber 只落 W+；/logs 只服务 mtime 最新 txt，gui.txt 永远压过 env_fix.txt）——但 .git 必在、checkout 纯本地、脚本无 WARN 即完成，收敛性由设计保证。
- **失误认领**：用户说"先暂停"时回复了"已发 /stop"但**实际没发**，GemsFarming 崩溃重试循环又跑十几分钟（wrapper respawn 到 #27）才发现真停——回复前必须核实动作已执行。另：T2 第一轮 E2E 的 Unknown ui page 风暴/B1 自律连锁源于 T3 验证把游戏留在 B1 遭遇提示处（非用户现场 bug）。
- **现场交还**：GemsFarming 已关回（`GemsFarming.Scheduler.Enable=False` 实证）、游戏已 force-stop（停在 D3 半途，重进会出"继续作战"提示，推进奖励归用户）、油 13775、**用户船坞已满需自行整理**（否则刷图必卡）。
- **git 未提交改动**：runner.py×2（双源）、MainActivity.kt、ProotHost.kt、build-rootfs.sh、env_fix.sh×2（新增）。

## v0.1.0（2026-09-17 发布）

### 2026-09-17 · 发布 ✅：v0.1.0 首个公开发布（release 打包 + 真机全新安装冒烟 + 发仓）

- **决策落定（用户指令）**：候选 1「崩溃自动恢复」正式放弃——用户质疑成立：分辨不了主动退出时，后台自拉 proot 环境就是隐形性能消耗；现语义（点开 App=明确意图才恢复）即为正确。阶段五 -1 多 ROM / -2 长稳 / -5 shizuku-m 产品化用户另有安排跳过；本次只做 -6 Release。
- **打包配置**：release ABI 收窄为仅 arm64-v8a（内置 rootfs 是 ARM64 Ubuntu，proot 不模拟指令集，x86_64 装上也是坏的；`AndroidApplicationConventionPlugin.kt` SHIPPED_ABIS + INTEGRATION.md 同步）。版本号走既有 git 派生：tag `v0.1.0` → versionName `0.1.0`、versionCode=commit 数（45）。
- **签名**：生成正式发布 keystore（RSA 4096 / 30 年，`keystore/azurpilot-release.jks`，gitignored），凭据走 `app/local.properties`（gitignored，KEYSTORE_* 四键）。**坑**：JDK 默认 PKCS12 不支持 store/key 异密码（keytool -list 验不出，签名才炸 `Given final block not properly padded`）→ 重建为同密码。
- **构建坑 2（semi-icons AAPT）**：`:semi-icons:verifyReleaseResources`（release 独有的库模块独立资源链接）报 `attr/colorControlNormal not found`——compileOnly appcompat 进了 classpath 也不吃（该任务不走编译 classpath 符号表）；终案=模块内 `values/attrs.xml` 按 appcompat 同 format 本地声明（app 打包同 format 合并无冲突，运行时仍由 app 主题解析）。上游 上游 fork 同文件同坑（未发 release 所以没踩过）。
- **真机全新安装冒烟（=阶段五「一键安装」DoD 实测）全过**：卸载 debug（配置已先备份 `.tmp/azurpilot-config-backup-v010.tgz`）→ 装 307MB release（28s 流式）→ 冷启弹 Shizuku 授权（显示名 更早的旧名 ✓）→ 首启解压 315MB 进度条 → 热更新 → wrapper/gui 在岗（/status + WebUI 200）→ 挂机页全 UI（配置下拉/工具按钮/状态行/日志板）→ 点「开始挂机」：调度器拉起游戏（健康游戏忠告画面上预览卡）、登录链 APP RESTART→APP LOGIN ✓ → 「停止挂机」runner 归零环境保留 ✓。
- **发仓**：`更早的旧名-v0.1.0-android-arm64.apk`（307MB，V2 签名 CN=更早的旧名）传 GitHub Release v0.1.0（notes 见 `.tmp/release-notes-v0.1.0.md`）；main + tag 已 push。
- **交付说明**：用户 AzurPilot 个性化配置随卸载清空（release 非 debuggable 无 run-as 恢复通道），备份在 PC `.tmp/`，需在 WebUI 重配；keystore 务必离线备份（丢失=永远无法同签名升级）。

## v0.1.0 之前（开发期）

### 2026-09-17 · 用户终验 ✅：工具独立开启 + 触摸转发全案收官

- **用户实测确认「功能完好，可以照常运行」**——工具栏独立开启全案最后两项遗留验收全部通过：
  - **D10 半自动点击手动终验**：daemon 预启动链（runner 先 `azurpilot.run('start')` 拉游戏过登录再进盯屏）实战有效，手动配合半自动点击正常。
  - **D2/D6 互斥实弹**：工具运行时按「开始挂机」自动停工具开挂机、启动工具自动停挂机——双向互斥 wrapper 临界区语义实战符合预期。
- **至此本线（f0b524e→6724ea2 共 5 提交）全部闭环**：工具独立开启、全屏触摸转发、UI 双模块行 v2/v3、daemon 预启动、悬浮窗样式适配、退出残留审查，无遗留项。
- **账册**：handoff `2026-09-17-tools-touch.md` 同步收官状态（「未实弹」改「已实弹」）。

### 2026-09-17 · 悬浮窗工具按钮样式适配 ✅ + app 退出进程残留审查 ✅（免新措施）

- **样式适配**：悬浮窗操作面板工具区原为描边按钮 + 「X 运行中+停止」两行式——换成与挂机页同款实心槽位按钮。`ToolSlotButton` 从 HangarScreen 提升为共享组件（移入 AzurPilotControlPanel.kt），挂机页/悬浮窗同一份槽位语义：本槽在跑变「停止」，另一槽可点=换工具。`toolDisplayName` 随旧行式退役。实机验证：面板两实心按钮与「开始挂机」同形制；点「半自动点击」左槽变「停止」，日志板同步实证 daemon 预启动链（APP START→handle_app_login→APP LOGIN）。
- **退出残留审查（代码清点 + 两种杀法实测）**：
  - 机制清点：proot 树 = wrapper stdin 管道 EOF 看门狗（ProcessBuilder 默认 PIPE，挂得上）+ `_cleanup` 四路（atexit/SIGTERM/SIGINT/stdin-watchdog）+ 单实例锁；特权进程 = linkToDeath 主路径 + 5s `/proc/<appPid>` 心跳兜底 → `destroy()` 逐项 cleanup（桥/电源/主屏/**VD**）→ exitProcess；一次性 proot 执行 = 超时 destroyForcibly。
  - 实测 1（`am force-stop`）：t+2s 我方进程全灭（含 shell-uid 特权进程）、22400/22267/22300 全释放、VD 撤除。
  - 实测 2（`kill -9` 仅 app 本体，模拟崩溃/划卡）：t+3s proot 树全灭（stdin 看门狗 EOF 自尽链路实证）、旧特权死；随后 **sticky 前台服务让 app 复活**（Android 设计行为）——新特权/桥/VD 自动重连，但 proot 环境（wrapper/gui/runner）**不自动拉起**，用户点开 app 才恢复。
  - **结论：无进程残留，无需新增自动停止措施。** 已知缺口（恢复力，非残留）：崩溃复活后挂机不自愈——留作后续决策。

### 2026-09-17 · 修复 ✅：半自动点击不拉游戏（runner 预启动）+ 运行配置标签下移

- **现象**：点「半自动点击」后游戏不被拉起，工具对黑帧原地空转；「开始挂机」却能正常拉游戏。
- **根因（代码+运行双实锤）**：AzurPilot 上游 daemon 任务设计上就不含 app_start/login——`AzurLaneDaemon.run()` 进来就是 `while 1: screenshot()` 盯当前屏（官方前提是游戏已在跑，桌面版用户自己先开游戏）。对照：event_story 自带 `app_start()`（eventstory.py:214），调度器有 GameNotRunningError→`task_call('Restart')` 兜底。运行实锤：runner.txt 里 daemon 起来后纯黑帧 WARNING 每 0.3s 刷屏、全程无 App start。
- **修复**（runner.py 双源同步，AzurPilot 代码零改动）：`task=='daemon'` 时先 `azurpilot.run('start')`（LoginHandler.app_start+handle_app_login；游戏已在跑=无害置前台收弹窗），失败则不裸进盯屏循环（exit 1 留墓碑）。
- **验证**：装机后 HTTP 起 daemon——日志链 `APP START → App start: com.bilibili.azurlane → handle_app_login → Login success → GUILD_POPUP_CANCEL → Bind task Daemon`；daemon 绑定后**零黑帧 WARNING**；预览卡实见游戏主界面（舰娘看板）。
- **UI 微调**：左卡 contentPadding 上 sm/下 xs（原上下 xs）——卡被行高拉伸后内容靠顶，上 padding 多给一档让「运行配置」标签下移、上下留白趋均；PaddingValues 无 (horizontal,top,bottom) 重载，用 (start,top,end,bottom)。

### 2026-09-17 · UI v3 ✅：双模块行压高对齐 + 配置下拉弹层宽度适配（用户三轮反馈收口）

- **反馈**：①「模块上下高度大，缩 30%」；②「左边再缩、标签与下拉间距更小、右两按钮与左卡上下对齐」；③「下拉框的下拉（弹层）也做好大小适配」。
- **根因（两层）**：a) 右列按钮被 M3 48dp 最小交互尺寸强制（`minimumInteractiveComponentSize` 吃掉显式 `height()`）——`CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp)` 关掉（**M3 1.4 该 local 是非空 Dp，`provides null` 编译不过**）；b) 0.dp 只撤外层强制，M3 Button 内层 `defaultMinSize(minHeight=40.dp)` 仍在——右列固有高 2×40+4=84dp 仍是行高驱动方（实测 250px≈83dp，按钮各 39.7dp）。
- **对齐方案**：左卡 `fillMaxHeight()` 拉伸至行高——实测左/右顶 1018=1018、底 1269≈1268（像素级对齐）；左卡内容自然高 ~75dp（AppCard 内建 `spacedBy(sm=8)` 叠加 Spacer(xxs=2)，标签-下拉实距 10dp），卡内底部留白 ~8dp 目视不可见。
- **弹层适配**：`DropdownMenu` 默认按内容包宽且偏移——锚按钮挂 `onGloballyPositioned` 量宽，`Modifier.width(menuWidthPx.toDp())` 喂给弹层。实测弹层与锚按钮同宽 475px、左右缘重合（x≈95/570）。
- **构建插曲**：外包 CompositionLocalProvider 时 `ConfigToolRow` 少一个闭括号（`ToolSlotButton` 变局部函数报 private 不可用 + Unresolved reference）+ `provides null` 类型错误，一次修齐。
- **验证**：BUILD SUCCESSFUL + 装机；截屏量测对齐；adb 点开下拉实证弹层宽度。

### 2026-09-17 · UI ✅：挂机页运行配置/工具合并为左右双模块行（用户点单）

- **布局**：原「运行配置」单行卡 + 面板底部「工具」区（含标签）→ 合并为一行双模块：左卡上「运行配置」标签、下全宽配置下拉（`Button` + SpaceBetween 文字/箭头）；右列「半自动点击」「活动剧情」两按钮上下排，与开始挂机同款实心 `Button`。行高 `IntrinsicSize.Max` 取左右最大固有高，右列两按钮 weight 均分填满——大小随模块自适应。「工具」字段移除。
- **状态**：工具在跑时对应槽位按钮变「停止」（槽位即归属），另一槽不变（可点=换工具，互斥归 wrapper）；下拉锁定逻辑（runnerAlive 禁切、runningConfig 回显）不变。
- **共享面板**：`AzurPilotControlPanel` 加 `showTools` 开关（默认 true）——悬浮窗保留原工具区（含标签），挂机页传 false 用新行。
- **验证**：BUILD SUCCESSFUL + 装机；截屏实证闲置态（左卡标签+下拉、右列两实心按钮等高自适应）与运行态（半自动槽位变「停止」）双形态。
- **插曲**：60s 无操作自动锁屏（`screen_off_timeout` 老设定），唤醒+上滑解锁继续（非锁屏测验）。

### 2026-09-17 · 交付 ✅：AzurPilot 工具栏 App 独立开启（半自动点击/活动剧情）+ 全屏触摸转发复活

- **功能落地**：挂机页新增「工具」区——「半自动点击」「活动剧情」两按钮，工具运行时变「<名字> 运行中」+「停止」；悬浮窗面板复用同一 `AzurPilotControlPanel` 自动获得。全屏态复活（点预览卡进横屏全屏、X/返回键退出），**全屏画面可点可拖、注入虚拟屏**——半自动点击的「手动」半边因此真正可用；内嵌小画面保持只读防误触。
- **wrapper 工具通道**（双源同步：`assets/azurpilot/overlay/` ↔ `rootfs/overlays/`，wrapper.py 471→617 行、runner.py 51→70 行）：runner.py 支持 `runner.py <config> [task]`（task∈{daemon,event_story}→`AzurLaneAutoScript(config).run(task, skip_first_screenshot=True)`；无 task 原 loop 不变；非法值退出码 2）。wrapper 新增 `POST /tool/start?name=&config=`、`POST /tool/stop`、`/status` 追加 `tool_alive/tool_name/tool_pid`。互斥=「停对方+拉自己」整个放 `_tool_lock` 临界区（全局锁序 `_tool_lock→_runner_lock`，无反向嵌套）；`start_tool` 无条件 `stop_runner()`（兼清 wanted 防退避期重拉破互斥）；工具自然退出留墓碑（/status 依 `poll()` 实时判死），**不重拉工具、不自动恢复 runner**（D3）；`_cleanup` 四路（atexit/SIGTERM/SIGINT/stdin-watchdog）杀工具防孤儿。
- **Kotlin**：`AzurPilotRunController` 加 toolAlive/toolName 解析 + startTool/stopTool（零门控，互斥全归 wrapper）；`AzurPilotControlPanel` 加 `AzurPilotToolSection`（共享组件）；新文件 `ui/hangar/HangarPreview.kt`——m0 原版移植（`FullscreenPreview`/`previewTouchInput` contain 换算+黑边丢弃/`rememberMovablePreview` movableContentOf/`PreviewTouchAction`）；`HostState` 加 touchDown/Move/Up 直通现存 AIDL（`RemoteServiceImpl.kt:215-219` → `InputControlUtils` 带 VD displayId 注入）；`AppRoot` 顶层挂全屏浮层盖住底部 tab 栏。
- **真机验收（全过）**：① /status 三字段在岗；daemon 启动→截图循环实跑（日志黑帧 WARNING=无 VD 空跑，无害）→停止 exit -15；幂等 `started_now=false`；非法名 400。② **UI 点「活动剧情」全链**：tool_alive=event_story → AzurPilot app_start 游戏 → 登录处理 → page_event → Mode_switch story → **finish 自退**（用户剧情已清，43s 全程）→ tool_alive 准时回落 False、不恢复（D3 墓碑语义实证）。③ **触摸转发 E2E**：预览卡→横屏全屏（隐系统栏、X 右上）；点游戏返回键→页面真切（幽影迷城→12章地图）；拖拽→地图平移；X→退出回竖屏挂机页，镜像无损回卡。
- **未实弹**：互斥 D2/D6 需挂机会话（不私按开始挂机）——代码路径已审，用户下次挂机自然验证；半自动点击完整验收留用户手动配合（D10）。
- **坑（已入 debug.md）**：adb 遥控点击必须当帧截屏取坐标——日志板高度漂移让工具区下移 ~100px，旧坐标首点落空；全屏 X 触摸目标小，偏 14px 落黑边被 previewTouchInput 边界检查丢弃（设计行为）。
- **账册**：handoff `2026-09-17-tools-touch.md`；debug.md 新增「adb 遥控 UI 两坑」。

### 2026-09-17 · 定案 ⏳：工具栏独立开启 + 触摸转发——grilling 访谈 10 项决策锁定（余 1 问确认中）

- **Round 1（用户答 1a/2a/3b/4a/5a）**：触摸转发常驻可点；启动工具=自动停挂机（正向互斥）；工具结束**不自动恢复**挂机（保持停止，要挂机自己再点）；入口=挂机页操作面板「工具」区+悬浮窗面板复用同一控制组件；实现通道=wrapper.py 加 `/tool/start`、`/tool/stop`、`/status` 扩展 tool 字段（webui 锁定补丁不动）。
- **Round 2（用户答 6a/7原版描述/8a/9a/10a）**：反向互斥对称（工具运行时按开始挂机=自动停工具开挂机）；手势=单指点+拖拽；工具参数（`Daemon.EnterMap`/`EventStory.SkipBattle`）用 config 现值、要改去 webui 工具页（已实证参数保存走 `_save_config`→`write_file` 直写 config JSON，不经被锁的 ProcessManager）；验收=我做构建/触摸转发/活动剧情无头验证，半自动点击留用户手动配合几分钟。
- **第 7 条关键事实（m0-archive 里的 fork 基线 原版实读）**：用户描述的「悬浮窗点击→横屏 720p 全屏态→右上角 X 退出回 app」= 原版 `LivePreview`（内嵌预览卡，`clickable(onEnterFullscreen)`）→ `FullscreenPreview`（`TasksPreviewSection.kt:178-229`：隐藏系统栏、SENSOR_LANDSCAPE、黑底 contain 缩放、Close X、BackHandler 退出）→ `previewTouchInput`（`:237-267`：contain 换算 VD 坐标、黑边丢弃、Down/Move/Up）→ `SessionIntent.PreviewTouch` → `previewPort.touchDown/Move/Up`（`SessionViewModel.kt:517-521`）→ AIDL。**全屏态就是原版原生的触摸转发家**；现 fork 的 AIDL `touchDown/Move/Up`（`RemoteServiceImpl.kt:215-219`，`InputControlUtils.injectInputEvent` 带 VD displayId）仍在、只是调用方在挂机页重构时丢了——复活=移植原版 UI 链 + 接到现存 AIDL。
- **Q11（用户答 a）**：挂机页内嵌卡复刻原版——点内嵌卡=进全屏态，触摸转发只在全屏态生效（内嵌小画面永不误触进游戏）。**设计树走完，11 项决策全部锁定，待用户确认共识后开工**。

### 2026-09-17 · 调研 ✅：工具栏「半自动点击 / 活动剧情」App 单独开启——事实摸底（grilling 访谈中）

- **用户诉求**：AzurPilot 工具栏的半自动点击、活动剧情不参与挂机队列、webui 里也不能单独开启，想在 App 里单独启动这两个工具。
- **事实（双探索代理：设备 /opt/azurpilot 实读 + 仓库实读）**：
  1. webui 工具页本来有专属 Start/Stop（`module/webui/app.py:685-694` → `ProcessManager.start(task)` spawn 独立进程跑 `AzurLaneAutoScript(config).run('daemon'/'event_story')`）；但 fork 锁定补丁 `patch_scheduler_lock` 把 `ProcessManager.start/stop` 整个封死（总览页+工具页同一 funnel）——**webui 里确实开不了，且是 M4-a 有意锁的**（wrapper 薄 HTTP 22400 是唯一控制面，防第二 runner 抢屏）。
  2. 半自动点击 = 任务 `Daemon`（`module/daemon/daemon.py:8-67`），常驻 `while 1` 循环**无自停条件**（源码注释原话），停止=杀进程；活动剧情 = 任务 `EventStory`（`module/eventstory/eventstory.py:203-216`），**一次性**约 3 分钟跑完自退（遇剧情战斗杀游戏跳过）。两者无 Scheduler 参数组 → 永远不能进挂机队列，只能工具页按钮或无头启动。
  3. **关键坑**：游戏跑在 shizuku 虚拟屏上，App 挂机页画面是**只读镜像、无触摸转发**（AIDL `touchDown/Move/Up` + `InputControlUtils.injectInputEvent` 带 displayId 的注入通道存在，但全仓无调用方）。半自动点击要用户手动进图/开战斗/点地图 → **不做触摸转发它就不可用**。
  4. 工具与挂机 runner 共用同一虚拟屏/azurpilot 桥（22300），并发会抢屏；webui 内 ProcessManager 槽位互斥对本 fork 失效（runner 不经 ProcessManager）→ 互斥必须由 wrapper 控制面集中执行。
- **待定决策（已发用户，等回答）**：触摸转发做不做（半自动的前提）、互斥/结束后恢复策略、入口位置（挂机页/悬浮窗/新 tab）、实现通道（推荐 wrapper.py 加 `/tool/*` 端点，与现有控制面同源）。

### 2026-09-17 · 修复 ✅：WebUI「闲置」状态环不停转圈 + 运行配置卡压缩为一行

- **问题 1（闲置转圈）**：真实根因（CDP 实证，首版推断翻车）——pywebio 的 `.style()` 把 `--loading-border-fill--` 标记写到 put_html 的**外包装 div**（spinner 父级）上，AzurPilot 的 fill 定制（`azurpilot.css` 的 `*[style*="--loading-border-fill--"]`）全部落在 wrapper：画出无圆角静态方框（= 用户截图里「状态行神秘方框」本体），而真正的 `.spinner-border.text-secondary` 保持 Bootstrap 默认——0.75s 旋转 + border-right 透明缺口。首版修复（4e2178e 往 azurpilot.css fill 规则补 `animation:none`）打在 wrapper 上对 spinner **完全无效**，真机两帧对比（缺口弧帧间移动）现形。
- **修复 1（终版，遵用户约束：不动 AzurPilot 代码防热更新冲突）**：WebView 层注入（`AzurPilotScreen.kt` 的 `IDLE_SPINNER_FIX_JS`，onPageFinished 幂等注入 `<style>`）——`.spinner-border.text-secondary{animation:none !important;border-right-color:currentColor !important}` 停转+补缺成完整圆（仅 secondary 命中：闲置/UpToDate/RemoteNotRunning 三个 fill 态，Running/Warning 颜色不同照常旋转）；`div[style*="--loading-border-fill--"]{border:none !important;width:auto !important;height:auto !important}` 剥掉 wrapper 方框 artifact。设备 azurpilot.css 已还原上游 pristine（exec-out 拉回 diff=空），仓内双源补丁 git rm。
- **验证 1**：CDP（`webview_devtools_remote_<pid>` + Runtime.evaluate）计算样式实证——spinner `animName:none`、`borderRight==borderLeft`、wrapper `borderTopStyle:none`；两帧截图像素级零差异；目视「AzurPilot ◯ 闲置」完整静态灰圆、方框消失。**用户目视终验通过**。
- **问题 2（配置卡两行→一行）**：`HangarScreen.kt` 的 `ConfigCard` 重构——去掉 AppCard 标题行与配置名独立 Column/running hint 小字，改为单行 Row：左「运行配置」标签，右下拉按钮**直接显示当前配置名**（runnerAlive 时显 runningConfig），点击展开切换；锁定逻辑（runnerAlive 禁切）不变。`hangar_config_switch`/`hangar_config_running_hint` 资源不再被引用（保留无妨）。
- **验证**：BUILD SUCCESSFUL + 装机 Success；锁屏通知栏实证「更早的旧名」新名与新图标系统级生效、前台守护在岗（未建虚拟屏 = 锁屏门控设计行为）。配置卡一行化截图验证通过；闲置环修复经 CDP + 两帧对比实证（见上，首版修复曾翻车重做）。

### 2026-09-17 · 交付 ✅：应用更名 更早的旧名 + m0 大凤 logo 入主 launcher 图标

- **改名**：`values/strings.xml` 与 `values-en/strings.xml` 的 `app_name`（上游 fork → **更早的旧名**），`log_export_subject` / `notification_test_message` 的产品名引用同步统一。aapt dump badging 实证 `application-label: '更早的旧名'`。**applicationId 未动**（仍 com.aliothmoon.azurpilot；appId 改名是 roadmap 长期债，Release 前零成本窗口另议——届时 run-as 路径/账册/脚本里的包名引用要全量换）。
- **图标**：用户指定旧文件夹图标 = `m0-archive/logo.png`（512² 大凤 Q 版蓝底，m0 项目 logo）。PIL 生成脚本（`.tmp/ev/make_icons.py`）产出全套入库图标：5 密度 × {`ic_launcher` 满幅方图、`ic_launcher_round` 圆形 alpha、`ic_launcher_foreground` = #16243c 画布 + logo 72% 居中（角色全进 adaptive 安全区，防裁头饰）}；`ic_launcher_background` 色 #beaaa0 → **#16243c**（取样自 logo 渐变底四角均值，splash 背景同源）。
- **验证**：BUILD SUCCESSFUL；aapt `application: label='更早的旧名' icon='res/mipmap-anydpi-v26/ic_launcher.xml'`（adaptive 链引用正确）；webp 产物目视抽查（角色居中、圆版裁切干净）；装机 Success。桌面视觉确认留用户目视（两轮截图尝试均撞手机锁屏，不越纪律）。

### 2026-09-17 · 修复 ✅：活动列表冻结——args.json 整文件补丁把 WebUI 冻在烘焙日（幽影迷城不可见）

- **用户现象**：桌面版 AzurPilot 已是「幽影迷城」（event_20260908_cn），手机端 WebUI 活动下拉仍停在「沉溺于星光之城」（event_20260813_cn）。
- **排查链（全部设备实据）**：设备 AzurPilot commit = 上游 master HEAD（92c07aa）✅、热更新通道健康；`campaign/event_20260908_cn/` 地图资源、i18n「幽影迷城」译名已到位——只有 args.json（WebUI 活动选项唯一来源）旧：设备版与 m0 遗产补丁版逐字节等大，活动选项双双冻在 `[event_20260625_cn, event_20260813_cn]`。全量 diff（补丁版 vs 上游 92c07aa）：16 项差异中 AzurPilot 真定制仅 2 行（ScreenshotMethod/ControlMethod 各加 `azurpilot`），其余全是冻结漂移——与 base.py 案同病（cp -rf 整文件覆盖）。
- **修复（生成产物不补丁化，现场再生）**：① 删双源 `patches/module/config/argument/{args.json,argument.yaml}`（4 文件）；② 新增 `seeds/regen_args.py`（双源同步）：`python -m module.config.config_updater` 跑完整生成链（活动列表随 `campaign/Readme.md` 走），后处理补 `azurpilot` 选项 + zh-CN 显示名「AzurPilot 桥」（幂等，占位也升级）；③ ProotHost 启动链热更新后无条件跑（`REGEN_ARGS_TIMEOUT_MS=360s`，失败降级警告不阻塞）；④ build-rootfs.sh 铺装 + fail-fast 清单同步。
- **坑中坑（两轮静默失败）**：ⓐ 生成器直传脚本路径炸 `ModuleNotFoundError: deploy`（sys.path[0]=module/config/），必须 `-m` 模块方式跑；runGuest 失败只 Timber.w，logcat 被 HONOR 噪音分钟级冲掉——靠「args.json mtime 停在装机前」现形，手动复现 proot 一次性 exec（pm path 推 nativeLibDir + baseEnv 同款配方）拿到完整 traceback 后修掉。ⓑ i18n 顶层是**组名、无任务层**（`zh['Emulator']['ScreenshotMethod']`），第一版后处理按 args.json 的任务层取道静默落空。ⓒ 一轮验证撞上手机锁屏，proot 启动链被 readiness 门控按住（设计行为，亮屏自愈）。
- **真机验证（端到端，非手动）**：装机 → 启动链自动 regen → args.json / zh-CN.json mtime 自动刷新；`Event.Campaign.Event.option = ['event_20260813_cn','event_20260908_cn']`（幽影迷城回归）；`azurpilot` 选项双在；`"azurpilot": "AzurPilot 桥"` 显示名生效。生成链设备端耗时 0.5s（热缓存），启动链成本可忽略。
- **锁屏插曲**：用户问「设了充电保持唤醒为何锁屏」——实测 `stay_on_while_plugged_in=15`（全选）、`mStayOn=true` 当前生效中；stay_awake 只防**自动**息屏，不防手动电源键锁屏，也不防 plugged 状态一过性丢失（`screen_off_timeout=60s` 极短，失保 1 分钟即锁）。
- **账册**：debug.md 新增「补丁冻结第二案」；handoff `2026-09-17-args-regen-fix.md`。

### 2026-09-17 · 修复 ✅：「AzurPilot 重启即断连」——base.py 全量补丁冻结漂移 + runner 崩溃自拉起

- **用户现象**：挂机中 AzurPilot 一旦发起游戏重启（Restart 任务），任务断开、要重新手点「开始挂机」。日志用户导不出，我经 run-as 直读设备 `/opt/azurpilot/log/` 定位。
- **根因 A（致命崩溃）**：Research 任务异常 → AzurPilot `Task call: Restart` → `App stop/start` → 登录处理器 `handle_cn_user_agreement` 调 `image_color_button(threshold=10)` → **TypeError**（运行态 base.py 签名是 `color_threshold`）→ runner 死。漂移机制：补丁施加是 `cp -rf` **整文件覆盖**（build-rootfs.sh:175），我们的 `patches/module/base/base.py` 是 m0 时代全量拷贝，把热更新到上游 master 的 base.py 冻回旧版；login.py 已热更新到新版（与上游 master 逐字节一致，实测 diff 空），新旧 API 撞车。补丁里真正的自有改动只有 `early_ocr_import` 的 AzurPilot BEGIN/END 预热块（9 行）。
- **修复 A**：base.py 补丁**重打** = 上游 master 原样 + AzurPilot 块（本地 python 脚本锚点替换生成，diff 只剩 9 行）。双源同步（rootfs/patches ↔ assets/azurpilot/patches），base64 分块法直写设备活文件并 diff 校验一致（DEVICE_SYNC_OK），设备 login.py 与上游 master 逐字节一致佐证重打目标正确。
- **根因 B（体验断连）**：wrapper 只监管 WebUI（gui 崩溃重拉），**runner 无监管**——任何崩溃都要人手再点。
- **修复 B**（wrapper.py，双源同步）：`_runner_wanted` Event（/start 置位、/stop 复位）+ `_runner_supervisor` 监管循环——runner 非预期死亡按 5s→60s 退避自动重拉同配置实例，活过 5 分钟退避复位；与 gui 监管同款形制。`/status` 新增 `runner_wanted`/`runner_respawns` 字段。AzurPilot 重跑会自处理 pending 队列，挂机无感续跑。
- **验证（不越纪律，未代按开始挂机）**：wrapper py_compile ✅；base.py py_compile ✅；APK 重装后 `/status` 已带新字段（wrapper 新版在岗，gui 活着）✅。**端到端已由用户确认**：「可以使用了」（2026-09-17）——开始挂机 → AzurPilot 重启流程不再断连。
- **坑入库**：adb exec-out stdin 不递 EOF（`cat >` 悬挂 10 分钟）；写设备文件改用 base64 分块 echo -n 追加 + base64 -d。

### 2026-09-17 · 修复 ✅：「开始挂机 touch down failed」——桥侧 down 注入有界重试吸收窗注册竞态

- **症状**：用户按「开始挂机」，调度器识别到 GET_SHIP 后首击 `Click (1009,643)` → `ScriptError: AzurPilot proxy error: touch down failed` → CRITICAL 死（runner2/3 同死法；runner1 之死系 install -r 重装箱误杀，非 bug）。
- **根因（真机决定性实验坐实）**：`InputControlUtils.injectInputEvent` 走 WAIT_FOR_FINISH，VD 上无可触摸 input 窗时框架原生返 false（debug.md 旧案升级版）。游戏被 `am start --display` 拉上 VD 后 **SurfaceFlinger 先出帧（screencap/OCR 可见）但 input 窗注册滞后 ~1s**——竞态窗口实测：am start 后第 1 次轮询 click 必败，~1s 后窗注册完成 click 恢复。AzurPilot 的链路恰是 am start → 识别 → 立即点，单击失败即抛 ScriptError 死调度器。空 VD（游戏未起/崩溃）同款 false。
- **修复**（`BridgeServer.kt`）：新增 `downWithRetry()`——down 失败后在 **3s 预算 / 200ms 间隔**内重试（注入失败事件未投递、无悬挂 DOWN，重试安全），handleClick/handleSwipe 的 down 都换走它；重试成功记 `Ln.w`（race absorbed）、耗尽记 `Ln.e`；最终报错带诊断 `touch down failed (no touchable window on display N within 3000ms)`。真空 VD 行为不变（仍报错，只是晚 ~3s），瞬态竞态对 AzurPilot 隐身。
- **真机回归**（VD #23，HONOR PPG-AN00）：① force-stop 游戏→am start 上 VD→**立刻 click 落竞态窗口 → ok:true**（修复前 100% 败）；② 空 VD click → 3.18s 后带诊断报错（预算有界）✅；③ 游戏窗稳定后 click×3 + swipe 全 ok ✅。游戏已 force-stop 清场。
- **结论给用户**：可重新按「开始挂机」验证；若游戏真未启动/崩溃，报错文案会直接说明「无窗」，不再是裸 failed。

### 2026-09-16 · 挂机页落地 ✅：应用内「游戏画面 + 运行配置 + 操作面板」真机实证

- **起因**：用户提出把悬浮窗操作面板做进应用内（上游 上游 fork 游戏窗口页的形制）。可行性分析（`handoff/2026-09-16-gamepage-analysis.md`）发现预览通道在阶段二减法中幸存（AIDL setMonitorSurface / RemoteServiceImpl / native bridge_preview 全在现役包），用户拍板开工；并明确「任务概览」= AzurPilot 配置文件里的**配置名下拉框**（不显示具体内容）。
- **wrapper.py**（rootfs/overlays + assets/azurpilot/overlay 同源同步）：新增 `GET /configs`（config/*.json 去 template*，'azurpilot' 排最前）；`POST /start?config=N` 透传实例名给 runner argv[1]（白名单字符校验）；`/status` 新增 `config` 字段（在跑实例名）。
- **App 侧**：`AzurPilotRunController` 加配置态（configs/selectedConfig/runningConfig，SharedPreferences 持久化，列表非空且选择失效时自愈回第一项）；`HostState` 加 attach/detachPreviewSurface（AIDL setMonitorSurface）；抽出共享组合件 `ui/components/AzurPilotControlPanel.kt`（状态行+日志板+启停），`OverlayPanel` 重构复用；新 `ui/hangar/HangarScreen.kt`（虚拟屏 16:9 预览 + 运行配置卡 + 面板）；`AppRoot` TopDestination 加「挂机」为**第一主 tab（默认首页）**，Routes/i18n（zh/en）同步。
- **真机验证**（HONOR PPG-AN00，版本 22 装机）：GET /configs → `["azurpilot"]` ✅；/status 含 config ✅；页面结构全渲染 ✅；下拉点开列出 azurpilot ✅；**预览通道像素级实证**——VD 空载时桥帧全零、app 预览同黑；往 VD 推 Settings 后桥帧白+左黑条、app 预览同构图，两图一致（证据 `.tmp/hangar-2.png` vs `.tmp/vd-frame2.png`）。「开始挂机」未按（纪律，留给用户在场演示）；Settings 已 force-stop 清场。
- **坑**：MagicOS 无线调试又休眠掉线一次（install 空报错→device offline→重连恢复）；构建漏 fillMaxSize import 一次（13s 快败即修）。
- README 工作原理图与「使用」段同步为「挂机页/悬浮窗 = 控制面」。

### 2026-09-16 · 可行性分析 🟢：应用内「挂机页」（游戏画面+操作面板+AzurPilot 任务概览）

- **用户新想法**（只分析未实施）：把悬浮窗操作面板做进应用内页面——保留游戏画面在上、下面改操作面板、原任务配置框改显 AzurPilot 任务管理配置。
- **核心发现：预览通道在阶段二减法中幸存**。减法只删 UI 层（ui/home、ui/tasks、runner/PreviewPort），底层整段还在现役 build：AIDL `RemoteService.aidl:62` setMonitorSurface（+触摸注入三连）、特权端 `RemoteServiceImpl.kt:203-207`、native bridge_preview/bridge_capture/bridge_frame_buffer 仍在 CMake 编译；帧源被桥 screencap p50=30ms 实证活着。缺口只剩 app 侧一个 SurfaceView 组合件（上游 PreviewSurface.kt 91 行可照抄）+ 一次 service 调用，**零 native/AIDL 工作量**。
- **分层结论**：A 游戏画面=小~中（唯一未知=bridge_preview 真机首验）；B 操作面板=小（OverlayPanel/AzurPilotRunController/HostState 全可复用）；C 任务概览=小~中只读（wrapper 加 /tasks 解析上游配置 带 Scheduler 节的任务组；**不做原生编辑**，守双头纪律）。结构=AppRoot TopDestination 加第三主 tab。合计约 2 天，A 先行排雷。
- 全账：`handoff/2026-09-16-gamepage-analysis.md`。**待用户拍板做不做/分期/命名**。

### 2026-09-16 · 装机验证收官 ✅：WebUI 锁定补丁真机实证 LOCKED-OK + 全链体检上岗

- **补丁真机静态验证（adb 恢复后）**：新 APK `install -r` Success → 冷启环境全起 → proot 一次性 python 在 guest 内 import `module.webui.patch` 后核 `ProcessManager.start/stop`——**均为 `patch_scheduler_lock.<locals>._locked`，LOCKED-OK**。锁壳在真实 guest 环境按设计生效；剩演示日用户点 WebUI Start 的行为确认（应只留 warning）。
- **全链体检脚本上岗**（`.tmp/device-healthcheck.sh`，8 项）：首跑 9 PASS / 1 异常——screencap 无帧，定位为**手机待机**（物理屏 OFF → 无线调试掉线 + VD 未建，唤醒自愈，非故障；已入 debug.md）。脚本已修正：screencap 项区分「待机无帧（WARN）」与「真故障（FAIL）」。
- **排障技法入库**：nativeLibraryDir 从 `/proc/<prootPid>/cmdline` argv[0] 拿（/data/app 对 app uid 不可 glob）；guest 内落 `lock_check.py` 跑 proot 一次性执行、用完即删。

### 2026-09-16 · 阶段五硬化 · WebUI 启停通道锁定（双头管理决策收尾，补丁+构建绿，装机验证待 adb 恢复）

- **评估结论（M4-a 推迟项）**：WebUI 有两处 Start/Stop 按钮（overview 页 + daemon 页，`app.py:456/688`），都汇入 `ProcessManager.start/stop`（gui 进程内）——用户误点会 spawn **第二个 runner** 与 wrapper 管理的 runner 抢设备输入。双头管理决策「悬浮窗=唯一控制面」需要的不只是文档警告，要通道级锁死。
- **实现（漂移最小）**：`rootfs/patches/module/webui/patch.py`（m0 起就在维护的 fork 文件，`app.py` 启动时模块级 import）尾部自执行 `patch_scheduler_lock()`——`MAAAL_SCHEDULER_LOCK=0` 留调试逃生口；`ProcessManager.start/stop` 换成只记 warning 的空壳（overview 页 `alive`/renderables 读路径不动；updater 早已被 deploy.yaml 锁死；gui 退出时的 `azurpilot.stop()` 变无害空操作）。**不整文件 fork `process_manager.py`**，热更新上游漂移面零新增。assets/azurpilot 同源两份已同步。
- **验证**：语法 OK；BUILD SUCCESSFUL。**PC 侧静态审计补完（adb 掉线期间）**：循环导入排除——`process_manager` 依赖链（setting/logger/submodule→stdlib+rich）无任何回指 `webui.patch/app`；无 ProcessManager 子类覆盖；`*args/**kwargs` 签名兼容 `start(func,ev)`/`stop()`。**装机与真机静态验证（proot 一次性 python 核 `ProcessManager.start` 为锁壳 + gui 日志干净）未完成——真机 adb 掉线**（ping 通但 5555 拒连，MagicOS 无线调试休眠掉线老毛病，需用户唤醒/重开）。演示 checklist 增补：用户在场时点一下 WebUI Start 确认只留警告不起 runner。
- **README**：「不要用 WebUI 启停按钮」警告改写为「已锁定」说明。

### 2026-09-16 · 阶段五-6/-1 起草 ✅：根 README 初稿 + 多 ROM 矩阵骨架

- **根 `README.md` 新立**（仓库此前无产品 README）：特性/架构图/安装（shizuku-m 前置 + 每次重启 30 秒手动链预期管理）/使用（悬浮窗=唯一控制面 + WebUI 启停按钮禁用警告）/FAQ（官方版冲突）/许可。三处占位 `TODO-阶段五`：机型清单、仓库地址、shizuku-m 获取方式——随 -1 实测与决策 #12 回填。
- **`docs/rom-matrix.md` 新立**：实测矩阵（基线行 HONOR 已填，GT Pro / SM_S9080 候选待用户确认）+ roadmap 登记的三条特例（MIUI/HyperOS 通知样式、ColorOS 权限监控、HarmonyOS 无无线调试入口=shizuku-m 离线自连的目标场景）+ 官方版冲突演练并入 + 每 ROM 六步测试规程。
- **`app/README.md`**（上游 上游 fork README）头部标注「上游存档，不描述本仓库产品」——它还写着「分辨率可选 720P/1080P」这类 fork 已删功能，防误导。

### 2026-09-16 · 阶段五开锣 ✅：断网容灾实证（-3）+ 桥截图耗时定档（-4）

- **阶段五-3 断网/弱网容灾（Gitee 超时降级）**：三层验证全绿。
  - **代码审计**：`update.sh` 三态协议（末行 UPDATED/UNCHANGED/FAILED）+ `AzurPilotUpdater` 永不抛异常全折叠 SKIPPED（300s 兜底 > 脚本内 240s）+ `ProotHost` 启动链失败续走、UPDATED 后重放补丁——设计无缺陷。锁清理位置实测确认：UNCHANGED 快进路径不清锁但无害（会挡操作的锁只在 fetch 前必清），App 侧 `cleanupStale` 每次启动双保险。
  - **PC 七场景**（`.tmp/m5-netresil`，file:// 裸仓当远端）：UNCHANGED 479ms / UPDATED 1051ms（文件内容核对一致）/ 首跑 DEPTH=1 路径 1009ms / 连接拒绝 2.4s FAILED / 无路由黑洞 30.8s FAILED（<60s ls-remote 兜底）/ DNS 不存在 1s FAILED / fetch 路径假锁全清 UPDATED。
  - **真机 E2E**（guest `.git/config` 临时指向 192.0.2.1 冷启，可逆已还原）：9s FAILED ls-remote → App 降级 SKIPPED → 启动链续走 → wrapper/gui 全起；恢复正确 URL 再冷启 → UNCHANGED 快进（state 重写、FETCH_HEAD 零下载）。另发现 03:46 真实网络抖动已在生产链留下过一次 FAILED fetch 自然降级——两种失败模式真机均实证。判读注意：FileLogTree 只记 WARN+，UNCHANGED 是 INFO 级文件无行，要看 state mtime（已入 debug.md）。
- **阶段五-4 最低配置定档（桥 screencap 耗时）**：shell 域 nc 直打 127.0.0.1:22300（绕 run-as 的 runas_app 域禁 socket 老坑），30 轮 ping + 30 轮 screencap（1280×720×3=2.76MB 裸帧）：**ping p50=29ms，screencap p50=30ms / p95=42ms / max=45ms**——受 stat 轮询地板（~28ms）压制真值更小。对照 AzurPilot 官方 >1s 不可用线富余 33 倍+，对照 m0 兜底 p50 0.109s 更快。**桥方案在 HONOR PPG-AN00（Android 16）定档远超需求**；多 ROM 矩阵实测留阶段五-1。方法已入 debug.md。
- **插曲**：压测中途 adb 多出一台 127.0.0.1:16385（SM_S9080，非本链设备）致 `adb shell` ambiguous——今后 adb 一律 `-s 192.168.50.190:5555` 显式指定（已入 debug.md）。

### 2026-09-16 · M4-d 收官 ✅：设置/构建死账清仓（死 pref×7 + 分辨率安慰剂 + runner 包 + toml/baselineprofile/okhttp/tracing）

- **M2-b 遗留②③④⑤ 一次清完**（阶段五前的卫生面，账本全核销）：
  - **死 pref×7 删除**（`AppSettings`/`AppSettingsManager`/`AppSettingsGateway` 三层）：`shizukuShortcutEnabled`（首页已删）/`closeAppAfterTask`+`touchPreviewEnabled`（消费者随 Runner 删）/`eventNotificationLevel`（通知管线删，domain 枚举连坐删）/`wakeUnlockEnabled`+`wakeCredential`（定时任务删）/`telemetryEnabled`（telemetry 删）。DataStore 里的旧值变孤儿键，无害。
  - **分辨率安慰剂连根拔**：排查发现 `resolutionPreference` 纯设置页自循环——`setVirtualDisplayResolution`（AIDL 端点）无人调用、VD 恒走 `DefaultDisplayConfig` 1280×720（桥 screencap 协议钉死）。设置页 720P/1080P 卡是** placebo UI**，连 pref/gateway/manager/SettingsContracts/SettingsViewModel/SettingsScreen 卡/字符串三键全删；`DefaultDisplayConfig` 注释改明「改分辨率要连桥协议与 guest 校验一起动，不做用户可选项」。
  - **`runner/` 包连包删**：`DisplayResolution.kt`（`screenSize()` 零调用、enum 只服务安慰剂）整文件删，包目录移除；`ScreenSize`/`DisplaySizeGateway` 两处注释摘掉对它的引用。
  - **依赖/构建死账**：okhttp、androidx.tracing.ktx 全码零 import（M2-c 桥走的是裸 socket，"留待桥用"不成立）连 dep+toml 删；debug 的 compose-runtime-tracing/perfetto 无 compiler flag 配套同删；**baselineprofile 插件+profileinstaller 连根拔**（macrobenchmark 模块 M2-b 已删，插件空转且是 AGP 9 兼容风险面，toml 里还留着"1.4.1 不认 AGP 9"的排雷注释）；`azurpilot.android.benchmark` 约定插件注册+实现类删；toml 死版本/死库/死插件条目（reorderable/markwon×6/jna/sentry/angus×2/jakarta/uiautomator/benchmark-macro 等）一次清。
- **验证**：BUILD SUCCESSFUL；装包重开设置页实机截图——「其他设置」只剩启动模式（Shizuku/Root），显示/日志/关于卡无损，环境自动起球出。过程中两处手滑（AppSettings 与 Manager 各重复一次 shizukuLaunchPackage 声明/ setter）编译器当场抓获即修。
- **边界**：junit/mockk/espresso 等 test 依赖虽暂无测试但保留（阶段五可能补）；`UserConfiguration` 侧未动。

### 2026-09-16 · M4-c 收官 ✅：载入开屏淡出（不再闪错误脸）+ 悬浮窗分工文案

- **开屏无感（roadmap 阶段四第 1 条补完）**：AzurPilotScreen 原逻辑是首帧 `loadUrl` 撞服务未起 → `onReceivedError` → 直接上错误面板（「重试连接」按钮脸），启动链走完再自动重载——用户每次开 App 先吃一张错误脸。改为 **`pageReady` 语义**：`onPageStarted` 归零、`onPageFinished && !loadFailed` 置位；`!pageReady` 期间盖全屏 overlay（`AnimatedVisibility` + `fadeOut` 淡出），overlay 分两档——真失败（phase==FAILED，或服务该活而页进不来）才给错误面板+重试，启动链还在走（PREPARING/UPDATING/STARTING，含首帧必然失败的 loadUrl）一律给 `CircularProgressIndicator` + 阶段文案的**载入开屏**。真机连拍实证：t+4s 帧抓到淡出中段（控制台已渲染、载入层半透明渐隐），t+16s 完整控制台。
- **分工文案（roadmap 阶段四第 5 条应用内侧）**：悬浮窗「开始挂机」下加一行小字 caption「高频操作在此面板；完整配置请回应用内的 AzurPilot 控制台」（EN 同步），面板布局实机截图核验。README 侧的分工说明随发版前 README 一起写。

### 2026-09-16 · M4-b 收官 ✅：官方版 Shizuku 冲突引导（flavor 检测 + 去卸载闭环）

- **背景事实**（SHIZUKU-M.md）：shizuku-m 与官方版**同包名** `moe.shizuku.privileged.api`、签名不同——装了官方版则 shizuku-m 覆盖安装必失败；且 binder 层两者不可分，`isShizukuAvailable()` 认不出谁是谁。判别口=包 label：官方版 `Shizuku`，shizuku-m 的 `app_name` 改成了 `Shizuku-m`。
- **代码**：`ShizukuReadinessStage.OfficialConflict`（新档）+ `ShizukuFlavor`（NONE/OFFICIAL/MOD）；`PermissionManager.probeShizukuStage` 重构——未授权时先查 flavor：**OFFICIAL 一律劝换**（哪怕官方版正在跑且可授权，授权投资前劝退；逃生口=「跳过检查」），MOD 才走原有 NeedAuth/NotRunning 分级。`uninstallShizuku()`= `ACTION_DELETE` 系统卸载框，卸完回来 onResume 自动 refresh → NotInstalled 档接着引导装 shizuku-m，闭环。
- **弹窗**：「检测到官方版 Shizuku」——说明同包异签不可覆盖装、官方版每次重启要 WLAN 配对而 shizuku-m 可离线自连、卸载不影响本应用数据；确认键「去卸载官方版」，中性键切 Root，dismiss 跳过检查。字符串三键 ZH/EN 同步。
- **验证限制**：真机装着已授权的 shizuku-m，冲突分支只能逻辑评审 + 构建 + 烟测（装包重开 readiness 仍 Ready、环境自动起、无回归）；真机演练需卸 shizuku-m 装官方版，破坏性操作留阶段五多 ROM 矩阵一起做。

### 2026-09-16 · M4-a 收官 ✅：悬浮窗直连 wrapper 薄 HTTP —— 调度器状态行 + 开始/停止挂机 + 半透明日志板

- **代码**：`proot/AzurPilotRunController.kt`（新）——4s 轮询 wrapper 薄 HTTP（GET /status → reachable/runnerAlive/pid/guiAlive/logLines；POST /start、/stop（busy 置位，读超时 12s 覆盖 SIGTERM→3s→SIGKILL）；GET /logs?tail=80 纯文本），Koin 单例挂 `postCreate` 全程轮询，Mutex 互斥刷新。`OverlayPanel` 改版：三行环境状态 + 「AzurPilot 调度器」状态行（环境未就绪/运行中·pid/已停止）+ 半透明黑底日志板（Monospace 10sp/行高 13，`LaunchedEffect(linesCount, lines.size)` 自动沉底，空显「暂无日志」，weight(1f) 吃满剩余）+ 全宽「开始挂机/停止挂机」（enabled=reachable && !busy，按 runnerAlive 切换）+ 原「回到应用/环境启停」行。字符串七键 ZH/EN 同步；DI：prootModule 注册 controller、OverlayModule 注入、上游 fork.postCreate `start()`。
- **双头管理决策（roadmap 阶段四遗留）定案**：**不做 Spike D 的 in-proc uvicorn 重构**——gui.py 独立 + wrapper runner 的链路已全绿，重构改动已验证链路风险大于收益。悬浮窗=唯一控制面；WebUI 启停按钮（走 ProcessManager，与本通道并存双跑会抢设备）「不要用」，先写文档警告（AzurPilotRunController 头注，README 待写），阶段五再评硬化（如 WebUI 按钮屏蔽补丁）。
- **真机验证（HONOR PPG-AN00）**：装包重开 → 环境自动起（VD #15、桥通、球出）→ 点球面板渲染全对——调度器「已停止」与 curl /status（runner_alive=false）一致；日志板从「暂无日志」（首版策略=runner 活着才拉）改为**可达即拉**后真实日志上板（gui 日志 `<<< RESTART AzurPilot >>>`、`Start azurpilot complete`、`[Server] cn` 等，与 /status 的 log_file 相符）；关面板球复活。**「开始挂机」未按**（会真拉起 AzurPilot 操作游戏，端到端留用户在场演示）。
- **新坑入 debug.md**：wrapper /status 时间戳是 guest 本地时（proot 无 TZ=UTC），比设备 CST 慢 8h——首读 gui_started_at 误判「旧会话逃过重装」，ps 进程树（proot 父=新 app pid）才定案。

### 2026-09-16 · M3-c 收官 ✅：生命周期防护压测全绿（杀 Java 进程 ≤3s 全树自尽）+ 重启引导文案对齐决策 #11

- **划卡归零正赛（DoD 核心项）**：`run-as kill <appPid>` 只杀 Java 进程（模拟最近任务划卡的最坏面——native 子树不随包名被杀），**≤3 秒内 proot/wrapper/gui 全灭**——stdin 管道 EOF → wrapper 监控线程 `_cleanup` → 杀 runner+gui 双进程组 → proot tracee 尽失退出。叠加 M3-b 的 `am force-stop` 零残留（uid 级全杀），两条死亡路径都闭环。
- **重启引导（roadmap 阶段三第 5 条）**：机制沿用 M2-d readiness 弹窗（NotRunning→打开 shizuku-m→NeedAuth→请求授权→自动 bind 建屏，当时 DoD 已实证）；本次把 NotRunning 文案从通用「打开 Shizuku 启动服务」升级为**决策 #11 的每次开机 30 秒手动链**（「每次重启手机后都需要重新激活：打开 shizuku-m 点『启动』（无需连接 WLAN 或电脑），启动成功后返回本应用即可自动继续」，EN 同步）。**真机重启端到端验证待用户授权**（不私自私下重启手机）。
- **结论**：阶段三 DoD 中「划掉 App 无残留 Python/proot/桥进程」✅；「开屏热更新→外部浏览器开 22267」✅（M3-b）；「重启手机经引导恢复可挂机状态」=机制+文案落地，实测待用户。

### 2026-09-16 · M3-b 收官 ✅：FGS 拉 proot + 自愈清锁 + 热更新（快进路径）+ wrapper 监管 WebUI 真机全绿

- **链路（roadmap 阶段三第 3 条全落地）**：AppRoot 侦测 Provision Ready → `proot/ProotHost`（新包）→ 自愈清锁（proot-tmp 整目录重来 + .git/*.lock + 上游重载哨兵）→ 写死 DNS（`etc/resolv.conf` 烘焙是悬空软链，删链写 AliDNS）→ `AzurPilotOverlay`（资产 `azurpilot/` 按字节幂等铺 /opt/azurpilot）→ seed_config（azurpilot 桥配置播种上游配置）→ `AzurPilotUpdater`（proot 内跑 `seeds/update.sh`）→ ProcessBuilder 拉起 `libproot.so … python3 wrapper.py` 长跑会话。
- **热更新设计定型**：`/opt/azurpilot` 烘焙时 `.git` 被剔除（build-rootfs.sh:277），首次更新=git init + fetch；脚本协议 `UPDATED/UNCHANGED/FAILED` 三态单行，App 降级不阻塞。**ls-remote 快进路径**（先 60s `git ls-remote` 取远端 HEAD，一致则零下载）——真机二启 **5 秒** 到 wrapper 就绪（对比首跑深度 fetch 83KB/s 撞 240s 超时，见 debug.md 新坑）；首次真更新降级为 `--depth 1` 单提交树。UPDATED 后 App 重放 overlay（patches/module + patches/assets + rpc.py）+ 重跑 assets_fix（幂等 + 漂移自检，Button 找不到非零退出→警告"建议重下整包"不阻塞）。
- **wrapper.py 升格为 WebUI 监管者**：spawn gui.py 子进程（独立进程组，输出 → `log/gui.out`），崩溃自动重拉（退避 5s→60s，活过 5 分钟复位），`_cleanup` 先置 `_closing` 再杀 runner+gui 双进程组；`/status` 增 gui_alive/gui_pid。stdin 管道破裂自尽链路不变（M3-c 正赛）。
- **FGS 语义扩展**：RunForegroundService 观察 HostState.snapshot **combine** ProotHost.state——虚拟屏在**或** proot 会话活跃（PREPARING/UPDATING/STARTING/RUNNING）即钉前台；新增通知文案「内置 AzurPilot 环境运行中（未建虚拟屏）」。
- **真机验证（HONOR PPG-AN00）**：首跑热更新超时降级→会话照常起；二启 5s 快进（`.azurpilot_commit`=92c07aa 与远端一致）；wrapper `/status` gui_alive=true；WebUI 22267 HTTP 200（PyWebIO 页）；上游配置文件 桥配置五键正确；FGS id=1001 在岗；**`am force-stop` 后 proot/python/gui 零残留**（DoD 划卡项提前实证）；杀掉 gui 后 wrapper 10s 退避重拉成功（新 pid，WebUI 复 200）；**WebView 开屏自动载入 AzurPilot 控制台**（截图 `.tmp/m3b-screen2.png`）。
- **工程修正三则（均入 debug.md）**：① RUNNING 语义必须=wrapper+WebUI 双端口可达，否则 WebView 自动重载抢在 uvicorn import 前几秒吃 connection refused 卡死错误页；② jniLibs 被 app/.gitignore 排除（运行框架 拉取件规则）→ proot 九件套移 `src/main/prootLibs/` + sourceSets srcDir 入库；③ adb 安装链 `install | tail && …` 的退出码是 tail 的（失败被吞）+ adb 只吃 Windows 路径。
- **jniLibs 九件套入库**（`app/app/src/main/prootLibs/arm64-v8a/`，3.9MB，Spike A 钉版：libproot/libproot-loader/libtalloc/libbusybox(+_app)/libspike_shim/libandroid-selinux/libandroid-shmem/libpcre2-8）。
- **双头管理（WebUI 启停按钮 vs wrapper/悬浮窗）按 Spike D 建议留阶段四决策**，devlog 与 wrapper docstring 均已标注；M1-d harness 后台任务已停（22267 让位生产会话），油数验收改走生产链。

### 2026-09-16 · M3-a 收官 ✅：首启 rootfs 解压流水线（进度条门）真机全绿 + 幻影键修正

- **代码（`f8d8cbe`）**：`provision/RootfsProvisioner`（状态机 Checking/NotBundled/LowDisk/Extracting/Ready/Failed）+ `ui/setup/ProvisionScreen` + AppRoot 门（未 Ready 整屏接管，开发包未内置可跳过）+ `di/ProvisionModule`。真机：~90s 解出 971MB（315MB 压缩包），进度条平滑，二启秒过门。
- **险些出货的大坑（已入 debug.md）**：解压目标最初写 AppPaths.ROOT（getExternalFilesDir=/sdcard）——/sdcard 模拟存储**不支持符号链接**（ubuntu-base 740 个）且 noexec，Spike A 实证 proot 可用的位置是**内部 filesDir**。提交前自查拦下。
- **解压技术选型**：busybox tar 解 ubuntu-base 硬链接前向引用必炸（M1-d 坑②）→ commons-compress + tukaani xz 纯 Java 流式解；符号链接 `Os.symlink`（实测 740 全数落地）、硬链接物化副本（前向引用解完兜底）、可执行位保留（python3→3.12 `-rwx--x--x`）、zip-slip 防护、256KB 节流进度（openFd 读真实长度，noCompress+xz 已配）、≥2GB 磁盘校验。
- **版本闸门**：assets 侧 `rootfs/BUILD_MANIFEST`（入库，678B）vs marker `.provisioned`（实测写出 0.1.0）；升级=换新包重解。rootfs.tar.xz 随包内置（gitignore，APK 392MB）——一键安装符合决策 #3；Gitee Release 100MB 附件上限放不下，分发走 GitHub Release。
- **幻影键修正**：`PermissionGrantHelper.disablePhantomProcessKiller` 原来是 m0 时代两旧键（Spike C 实测**无效**：`settings_config_disable_monitor_phantom_procs`/`phantom_process_killer_enable`）+ 有效副手段；已换成 Spike C 实证对（`settings_enable_monitor_phantom_procs false` 主 + `device_config max_phantom_processes 2147483647` 副），设备侧 `settings get` 双双确认写入。
- **遗留**：部署页期间 HostState 并行起 VD/球（可接受，阶段四再评）；M3-b = FGS 拉 proot（libproot.so 进 jniLibs）+ 自愈清锁 + Gitee 热更新 + wrapper。

### 2026-09-16 · M2-d 收官 ✅：HostState 脱钩 + DoD 真机验证 6/6 + 悬浮球消失之谜定案

- **代码（`8c02157`）**：新建 `service/HostState.kt`（快照=特权连接+桥可达+vdDisplayId；environmentUp=桥通且屏在；4s 裸 socket ping 22300 探测；ensureEnvironmentStarted=bind→setup→startVD→FGS 一键链；stopEnvironment=stopVD）+ `di/HostModule.kt`；OverlayController/OverlayPanel/FloatBall/RunForegroundService 全脱钩 RunnerPort（StubRunnerPort/RunnerContracts 删除）；AzurPilotScreen 错误页改「重试连接」；NotInstalled 档改「我已安装，重新检测」+ shizuku-m 自装引导文案。
- **DoD 真机验证 6/6（HONOR PPG-AN00，竖屏 1264×2800，shizuku-m 在线）**：
  1. ✅ Shizuku 权限链路：NotRunning→点 shizuku-m「启动」→NeedAuth「请求权限」→系统弹窗「始终允许」→全自动 bind→setup→VD→FGS→桥→悬浮球。
  2. ✅ VD 创建：display 动态分配，flags=PRESENTATION|OWN_CONTENT_ONLY|DESTROY_CONTENT_ON_REMOVAL|TRUSTED|OWN_DISPLAY_GROUP|ALWAYS_UNLOCKED|TOUCH_FEEDBACK_DISABLED|OWN_FOCUS|STEAL_TOP_FOCUS_DISABLED（**无** SHOULD_SHOW_SYSTEM_DECORATIONS），owner shell uid2000；**手势三窗口全程 displayId=0**。
  3. ✅ 桥五端点（adb forward + PC python 冻结协议客户端）：PING/SHELL(uid=2000)/SCREENCAP(恰 1280×720×3=2764800B)/UNKNOWN 错误帧全对。
  4. ✅ WebView 渲染**真实 AzurPilot PyWebIO GUI**（harness WebUI 22267 供）。
  5. ✅ 悬浮球自动出现（绿球呼吸）+ FGS 通知在岗（ONGOING|PROMOTED_ONGOING）。
  6. ✅ 面板全生命周期：点球→面板开（球隐）；「停止环境」→ VD 毁+球隐+面板转「启动环境」；「启动环境」→ VD 重建；关面板（X）→ 球复活。
- **悬浮球消失之谜定案（非 bug，系统机制）**：当 `android.settings.SETTINGS` 开在 **VD** 上时，HONOR ROM 把 `hideOverlayWindows` **全局**应用于所有 display——主屏悬浮窗被策略性强隐（`mPolicyVisibility=false mForceHideNonSystemOverlayWindow=true`，视图仍 VISIBLE）。且 flag **粘性**：Settings 死后不自动复评，需一次前台应用切换（home→回 app）触发重估才恢复。游戏无此属性（游戏前台时球实测存活 ✅），生产无影响。已入 debug.md。
- **顺带修复（`728dff6`）**：面板经「启动环境」重建 VD 后球会压在面板上——observeHost 在 showControl 前查面板在屏则跳过。真机回归：停止→启动→球保持隐 ✅ → 关面板→球复活 ✅。
- **事故披露**：验证"游戏前台球存活"时 monkey 把游戏起到主屏（`--display` 被忽略）→ 主屏转横屏；后续两次按竖屏坐标的 tap 越界被钳到屏幕边缘（未触达有效 UI）；游戏进程随后被发现已退出（登录页，无进度损失；死因未能归属到具体操作，倾向系统回收/游戏自身）。教训入 debug.md（点击前先核旋转与坐标）。
- **空 VD 注入语义（留档）**：空 VD（无窗口消费）上 click/swipe 回 `touch down failed` 非链路坏——WAIT_FOR_FINISH 模式无消费者 natively 返 false（`input -d 2 tap` 同静默 false）；VD 上有窗口（Settings）后 CLICK/SWIPE 均 ok。生产语义正确（游戏常驻 VD）。
- **清场 ✅**：面板停环境 → force-stop → 只剩 display 0、azurpilot 窗口 0、手势三窗口 displayId=0、无活动通知。
- **遗留**：① M1-d 油数验收仍等用户把游戏点到出击菜单页（游戏现已退出，需重开）；② appId 身份（`com.aliothmoon.azurpilot`→AzurPilot 命名）待用户定夺；③ AppSettings 死 pref、`runner/DisplayResolution.kt` 包名不副实，留阶段三。

### 2026-09-16 · M2-c 收官 ✅：特权进程内 Kotlin 重写 m0 桥（TCP 22300，5 端点）

- **`BridgeServer.kt`（368 行新增）+ JNI 裸帧出口**，commit `05164f8` 已 push。m0 Python Agent 桥（`m0-archive/spike/m0/agent/main.py`）由特权进程内 Kotlin 服务整体替代，协议与冻结客户端 `rootfs/patches/module/device/method/azurpilot.py` 逐点兼容。
- **协议对照**：行分隔 JSON + screencap 响应行后随裸帧；每回复（含错误帧）echo 请求 id；ping/screencap/click/swipe/shell 五端点（ocr 按 roadmap 剔除，AzurPilot 改走 in-proc PP-OCR）；shell 剥 `LD_LIBRARY_PATH` + PATH 前缀 + stdout/stderr 各 64KB 上限（超限照读照丢防管道死锁）+ 超时 `destroyForcibly`；请求行 64KB 防呆；per-client daemon 线程。
- **screencap 直通 native**：新 JNI `getFrameBufferBytes()` 走 `GetLockedPixels/UnlockPixels` 读者锁（持锁压到一次 memcpy）；帧缓冲原生 **BGR 3ch** 正是 m0 线上格式（客户端 `[:3][::-1]` 翻 RGB）；尺寸经 `VirtualDisplayManager.getConfig()` 交叉校验，防 VD 重启半途发错尺寸帧。
- **注入**：`InputControlUtils.down/move/up(contact 0, displayId)`，displayId 每请求现取（VD 重建不僵）；click=down→50ms→up；swipe 绝对时间轴线性插值（~16ms/步，步数 max(1,duration/16)），x1==x2 自然退化长按；**move 失败也补 up**——悬着的 ACTION_DOWN 会劫持 VD 触摸直到 VD 重启（直注路径必须自己兜，m0 由 controller 内部兜底）。`setContactSupport(false)` 在 start() 防御性调用（原调用点 Runner.prepare 已删）。
- **生命周期**：`RemoteServiceImpl.init` 启动（ctor 不抛铁律→runCatching）、`cleanup()` 停止；`isRunning()` 预留 M2-d FGS 状态源。
- **评审记录**：DEVICE_LOCK 有意扩到整段（含 2.7MB 发帧，loopback <10ms；m0 只锁 controller 段——soak 见卡顿再议）；半帧 desync 与 m0 同疾（客户端 reconnect-retry 重同步）；畸形请求错误文本与 m0 有出入（冻结客户端永不产生，形状一致）。
- 构建绿（BUILD SUCCESSFUL，CMake 重编过）。真机 ping/screencap/建屏验证属 M2-d DoD。

### 2026-09-16 · M2-b 减法收官 ✅：剔除 上游业务引擎/PI/定时任务/推送业务管线，宿主外壳收敛，构建绿

- **终验 BUILD SUCCESSFUL**（3s，103 tasks）。APK `app-debug.apk` 80,972,217 → **80,958,388 B**（−14KB；debug dex 未压缩 + fork clone 基线本就不含 jniLibs，包体大头是三方库不是业务码，降幅小属预期）。全 dex 抽查：16 个被删类 0 命中，保留类（StubRunnerPort/RemoteServiceImpl/RunForegroundService 等）在。
- **刀①构建系统**：删 `app/macrobenchmark/`；build-logic 删 PiAssets/AgentRuntime 两 Convention 插件；app 模块去两插件 id + baselineProfile + jna/sentry/markwon/angus.mail/jakarta.activation/reorderable 依赖（okhttp/tracing 按底稿 §七保留）；proguard 删 JNA/markwon/SMTP 段，R8 关键类清单删 azurpilot 两条。
- **刀②业务包整删**：`azurpilot/ project/ schedule/ notification/ telemetry/ session/`；remote 删 Runner/ExecAgentHost/AgentInstaller/AgentRuntimeDescriptor（AgentHost 连锁）；log 删 RunLogArchive/Detail；di 删 Notification/Project/ScheduleModule；`assets/shizuku.apk` + ShizukuInstallHelper + 两个构建脚本（setup_framework/build_agent_bundle）删。
- **刀③留壳改造**：RunnerPort 接口原样保留（RunnerContracts.kt 并入 RunnerEvent.toLogText），DI 改绑 **StubRunnerPort**；**RemoteServiceImpl 空实现**（setup 留 phantom killer 禁用，run/nativeVersion 返 false/null，AIDL 四文件全保留）；RunForegroundService 重写为只观察 runnerPort.state，RunProgressSnapshot 迁 `service/` 包；PermissionManager 内化 Shizuku 探测（installShizuku 删，openShizuku 保留）。
- **刀④UI**：删 tasks/schedule/home/notification/options 五页 + 9 个业务组件；**AppRoot 重写为 2 tab（AzurPilot+Settings）**；Routes 只剩 AzurPilot/SETTINGS/APP_LOG(_DETAIL)；SettingsScreen 重写为 Display/Log/Other/About 四卡（SettingsViewModel 扩三参 + themeMode/language/resolution 意图 + RestartApp 事件）。
- **刀⑤杂项**：Manifest 删 boot/alarm 两权限 + schedule 三组件 + Sentry meta-data；VirtualDisplayManager 删死常量 ROTATES_WITH_CONTENT；ShellDirs/AppPaths/AppFiles 删 AGENT_DIR/JNA_TMPDIR/FOCUS_DIR/PI_DIR。
- **遗留**：① readiness 弹窗 NotInstalled 档 onInstall 无真实安装能力（决策=不内置 APK），现接 openShizuku 语义降级，文案待用户决策；② AppSettings 里 wakeUnlock/telemetryEnabled 等成死 pref（未清 schema）；③ okhttp/tracing 已无引用但保留（M2-c 桥可能用上）；④ toml 未用条目未清；⑤ Koin 图仅编译期核对，真机运行验证留后续里程碑。

### 2026-09-16 · M2-a 基线构建绿 ✅（三跑迭代：Aliyun 镜像 + floatingx-compose 补齐）

- **基线 assembleDebug 第三跑 BUILD SUCCESSFUL**（1m48s，105 tasks；`app/app/build/outputs/apk/debug/app-debug.apk` 81MB）。本机可构建实证，M2-b 减法对照基准就位。APK 缺 运行框架 jniLibs（拷贝时已排除）属预期，M2-b 连引用一起剔除。
- **跑①红**：`bundletool:1.18.3` 解析挂 TLS 握手中断（dl.google.com 被中间盒 RST；暖缓存只有 1.18.0）。curl 复测 dl.google.com 通=间歇性，仍决定根治：`app/settings.gradle.kts` 两个 repositories 块加 Aliyun google/central 镜像（官方源+jitpack 兜底）。已入 debug.md。
- **跑②红**：`OverlayController.kt:32` `Unresolved reference com.petterp.floatingx.compose.enableComposeSupport`。三方 hash 比对 + m0 用户 Gradle 缓存考古定位：`floatingx:2.3.7` 在中央仓是**无 compose 包的瘦 aar**；m0 当年靠仓库序 jitpack 优先拿到**聚合空 jar** → 传递依赖 `io.github.petterpx.floatingx:floatingx-compose`（jitpack 构建）才编过；加镜像时 jitpack 被挪到队尾 → 中央瘦 aar 截胡。修法：toml + `app/build.gradle.kts` **显式声明 `floatingx-compose:2.3.7`**（中央/aliyun 直达，CN 友好），不恢复 jitpack 优先序。已入 debug.md。
- **fork 源考古**：`git ls-files -v` 全 H 无 skip-worktree 隐藏改动；compose import 是 b2b0f54 提交自带（fork 作者本地靠 jitpack 序巧合可编，上游 CI 未覆盖该坐标陷阱）。
- **seed_config.py 键兼容 ✅**（未决项关闭）：AzurPilot master HEAD=`92c07aa28ba8515b7709542ae8c14f6a7f3a08bd` 的 `config/template.json` 五键（Serial/PackageName/ScreenshotMethod/ControlMethod/ScreenshotDedithering）全在。附带发现：rootfs 构建 `ALAS_REF` 默认 master **浮动不钉**（BUILD_MANIFEST 记录解析后 commit），可复现性改进留阶段三评估。
- **游戏仍在登录页**（只读 screencap `.tmp/game-now3.png`）——M1-d 油数 100 帧验收继续等用户把游戏点到出击菜单页。

### 2026-09-15 · M2-a 开工：fork 复活为 `app/`（25MB 干净副本）+ 基线构建在跑

- **v4 artifact 核验 ✅**：`.tmp/rootfs-dist-v4/rootfs.tar.xz` sha256 `b506a62e…745a` 与 CI 日志一致，cached-property 2.0.1 在列、ALL_IMPORTS_OK、OCR_GATE PASS——**交付基准定型**。
- **fork 复活**：`m0-archive 里的 fork 基线`（只读）→ 仓内 `app/`（tar 管道拷贝，排除 `.git/app/build/.cxx/.gradle/.kotlin/.azurpilot/.azurpilot-cache/build*/jniLibs`），25MB 源码级副本，**6 处 m0 WebView 未提交改动随之固化进仓**（AzurPilotScreen/network_security_config/AppRoot/Routes/strings/manifest）。
- **修构建死路径**：`app/local.properties` 删 `pi.profile`（原指向已归档的 m0 yaml，`BuildProfile.kt:91-92` 硬失败）。
- **构建环境**：JDK17 @ `D:\VSCodeCache\shizku-m\build-env\jdk-17.0.2`；SDK 用 `C:\Users\da270\AppData\Local\Android\Sdk`（cmake 3.22.1 + ndk 28.2 齐）；`GRADLE_USER_HOME` = 本仓 `.tmp/gradle-home`（从 build-env 拷 1.1GB 暖缓存，不污染共享目录）。
- **基线 assembleDebug 在跑**（`.tmp/app-build-baseline.log`）——减法前先证本机可构建，之后减法每刀都有对照。

### 2026-09-15 · 阶段二备战：上游 fork fork 减法盘点落地（`docs/stage2-azurpilotapp-inventory.md`）

- explore 子代理对 fork @ b2b0f54 做全量只读摸底，盘点固化成工作底稿。**三条改变任务理解的发现**：
  1. **桥不在 fork 里**——m0 的 6 端点桥是 Python 运行框架 Agent（`m0-archive/spike/m0/agent/main.py`，TCP 22300 行分隔 JSON+裸帧，非 HTTP）；删 上游业务引擎 会连桥一起删 → 阶段二的"保留 5 端点"= **在特权进程内用 Kotlin 重写**（去 ocr 端点）。
  2. **桥底层设施（libbridge.so + InputControlUtils + DriverClass）与业务解耦可原地留用**，唯一缺口是帧数据无 Java 裸字节出口（需加 JNI）。
  3. **fork 工作树带 6 处未提交改动 = m0 WebView 资产**（AzurPilotScreen 等），复活前必须先固化。
- VD flag 现状核查**合规**（`SHOULD_SHOW_SYSTEM_DECORATIONS` 被 `VD_SYSTEM_DECORATIONS=false` 代码级挡住，`ROTATES_WITH_CONTENT` 是死常量）。
- 构建前提：`local.properties` 的 `pi.profile` 是死路径须先删；`libc++_shared.so` 不能删（断 libbridge）；13 个 运行框架/PP-OCR so 剔除后包体 200MB+ → <20MB。
- 游戏仍停在登录页（等用户点到出击菜单页）；v4 artifact 仍在下载。

### 2026-09-15 · M1-d（中）：油数探针链路全通，GHA 第四跑绿（cached-property 入正）✅

- **GHA run 34997038262（`e233630`）completed/success**——`cached-property` 正式进构建；artifact 后台下载中（`.tmp/rootfs-dist-v4`），作为交付基准。
- **油数探针机械链路真机验证通过**：宿主 `/system/bin/screencap` 抓帧（2800×1264）→ proot `-b frames:/frames` → `Digit.ocr` → in-proc PP-OCR 出文本。登录页错误区域读出 `'NA'` → `Digit.after_process` `int('NA')` ValueError——**正是"区域无数字"的标准失败形态**，证明 import 链/图像加载/CTC 解码/Digit 调用形状全对。
- 口径说明：m0 的 `OIL_AREA=(632,22,712,52)` 是 **1280×720 VD 原生帧**坐标；物理屏 2800×1264 宽屏 UI 锚定真实边缘，不能等比映射。M1-d 油数测试改用**实帧目测的原生分辨率油区**（游戏到出击页后定），m0 原生 720p 口径留阶段二 VD 上线后回归。
- 100 帧连拍与探针脚本（`.tmp/m1d-oil-probe.py`，已在 rootfs `/opt/azurpilot/` 就位）备好，**只等用户把游戏点到出击菜单页**。
- WebUI 仍保活（bash-r6h84em7），PC 浏览器 127.0.0.1:22267 可看。

### 2026-09-15 · M1-d（上）：rootfs 真机拉起成功，WebUI 200 ✅，踩坑 4 连

- **验收 ③ BUILD_MANIFEST 可读 ✅**（设备上 cat 出完整 JSON：rootfs 0.1.0 / azurpilot@92c07aa / py3.12.3 / ort 1.30.0 / cv2 5.0.0）。
- **验收 ① WebUI ✅**：shell 域 proot 单命令 `gui.py` → uvicorn `0.0.0.0:22267` startup complete，adb forward 后 PC `curl 127.0.0.1:22267` = **200**（PyWebIO Application，6055B）。
- **坑①**：解包目标非空场（Spike A 旧 rootfs）→ `./bin` 软链覆盖失败。修：解包脚本先 `rm -rf files/rootfs`。
- **坑②**：busybox tar 解 ubuntu-base 硬链接前向引用必炸（`uncompress→gunzip`）。修：PC 侧 `.tmp/repack-linkfree.py` 重打包去硬链接（reg 21359/hard 3 全物化/sym 740/dir 2380，977MB 未压缩 tar，WiFi push 43MB/s）。已入 debug.md。
- **坑③**：run-as（runas_app 域）**禁 socket**（`socket()` EPERM，虽有 inet gid）→ 改 shell 域跑 harness：shell 执行 /data/local/tmp 内 proot 可行；guest PATH 要显式 export（宿主 Android PATH 无 /usr/bin）；mksh heredoc 在 run-as 下建临时文件失败（禁用 heredoc）。
- **坑④**：shell 域 SELinux 禁**路径式 AF_UNIX**（shell_data_file 上建 socket 文件 EPERM），但 AF_INET/abstract AF_UNIX 通 → harness 用 sitecustomize 把 `BaseManager` 默认地址换 127.0.0.1:0（同 AzurPilot Windows 路径），**仅 harness 不进构建**（生产 untrusted_app 域 + app 私有 TMPDIR，m0 已实证无碍）。
- **依赖缺口实锤 1 个**：`cached-property`（`config_updater.py`/`azurpilot.py` 顶层 import；老 uiautomator2 传递依赖，现代 3.x 不再传递；m0 清单同样缺但当时未踩到）。静态全扫其余 10 项均惰性/平台限定可忽略（cnocr/av/lz4/psutil 等）。**构建修复待做**：build-rootfs.sh pip 集 + cached-property 并重跑 GHA。
- 现状：WebUI 进程在后台任务保活（bash-r6h84em7）；shell 侧 rootfs @ `/data/local/tmp/rootfs`；**验收 ② 油数 100 次待跑——需用户把游戏点到出击菜单页**。

### 2026-09-15 · M1-c 收官：GHA 第三跑全绿 ✅

- **run 34989709297（commit `a1c9e96`）completed/success**，全程仅 ~4.5 分钟（ubuntu-24.04-arm 原生 + `XZ_OPT=-T0`）。七个 step 全绿，含 Spike F OCR gate。
- **pip 宽松集 aarch64 解析实录**（`.tmp/gh-run3-full.log`）：numpy 2.5.3 / scipy 1.18.1 / opencv-python-headless 5.0.0.93 / onnxruntime 1.30.0 / pydantic 1.10.26 / pillow 12.3.0 + AzurPilot 全套（adbutils 2.12.0、uiautomator2 3.7.0、pywebio 1.8.4、fastapi 0.125.0、uvicorn 0.53.0 裸版等 46 包）——**m0 实证集在 ubuntu-base 24.04 + py3.12.3 上一次装全，零失败**。
- **chroot import 硬门禁**：`ALL_IMPORTS_OK`；assets_fix 补丁生效（DAILY_SKIP @ module/daily/assets.py:19）。
- **CI Spike F 门禁**：model_load PASS / synthetic_digits 13/13 PASS / gray2d_stacking 2/2 PASS → **OCR_GATE PASS（3 PASS 0 FAIL 0 SKIP）**——合成数字用默认字体即可渲染，此前"无 CJK 字体会 SKIP"的预判未发生。
- **产物**：`rootfs.tar.xz` sha256 `b27148c6859ecbbaad0fb896876e2d13b36629cd987088a7b72c1c82550546d7` + BUILD_MANIFEST（artifact 14 天）。
- 结论：**M1-c 完成，rootfs 烘焙链定型**。进入 M1-d 真机复验（runbook 见 handoff）。

### 2026-09-15 · M1-c：GHA 首跑 404 秒修，第二跑在飞

- **（当日续②）用户新授权**：**本次长任务期间 git 操作全部预授权**（含 push；发版 release/tag 仍需逐次授权），已记 handoff。
- **（当日续③）M1-d 设备侧预踩点完成**：spikea 在机、nld 四件套齐（proot/loader/shmem/busybox）、/data 余量 91G；发现新坑——`libbusybox.so` 直接调报 `applet not found`（多合一认 basename(argv[0])，须软链成裸名 `busybox`），已记 debug.md；完整 M1-d runbook（推包→run-as 解包→proot 拉起→验收三件套）写进 handoff。
- **（当日续）第二跑 34989211076 又红**：ubuntu-base/apt/pip 基座全过（`libglib2.0-0t64` 改名修复生效、py3.12.3、git 2.43 装好），死在 **AzurPilot 克隆**：`fatal: could not read Username for 'https://gitee.com'`——gitee 同名镜像对匿名克隆返回 401 索取凭证（本机 `git ls-remote` 复现：挂凭证管理器提示）。修复：`ALAS_REPO` 默认改 **GitHub 原生上游**（`ls-remote` 实测 HEAD=92c07aa 可达；GHA runner 在海外本就应走 GitHub；runtime 的 fullcn 更新镜像由 deploy.yaml 管，与构建源无关），`chroot_run` env 白名单补 `GIT_TERMINAL_PROMPT=0` 让凭证提示 fail-fast。commit `a1c9e96` 已 push。**第三跑 34989709297 在飞**，改 2min 轮询盯梢（`.tmp/gh-run-watch3.log`，`gh run watch` 长连接两次被本机网络 EOF 打断，不可用于盯梢）。
- **push 授权到位**：用户"可以push，手机可用于调试"→ commit `b263dfb`（59 文件 +37100 行，M1 全量 + Spike E 报告 + 手势劫持硬约束）推 `Shinarin/AzurPilot` main，触发 rootfs.yml 首跑（run 34988699866）。
- **首跑 12s 失败**：`Build rootfs` step 下载 ubuntu-base 404——脚本写的是 `ubuntu-base-24.04-arm64.tar.gz`，cdimage 实际命名为 `ubuntu-base-<点版本>-base-arm64.tar.gz`（当前 24.04.3/4/5 并存）。
- **修复**：`build-rootfs.sh` 两处 URL 钉 `ubuntu-base-24.04.5-base-arm64.tar.gz`（旧点版本 cdimage 保留，可复现；无 sha256 钉版故无连带改动）；`curl -sI` 200 ✓、`bash -n` ✓、全仓 URL 重扫无其他 404 风险。commit `e85255d` 已 push（构建迭代属本次授权范围，已报备）。
- **第二跑**：run 34989211076（`e85255d`）in_progress，后台任务盯梢（`.tmp/gh-run-watch2.log`）。重点盯：pip 宽松集 aarch64 实际解析、import 硬门禁输出、Spike F 门禁 chroot（中文字体缺省 → 合成图 SKIP 属预期）。
- **游戏侧**：`am start` 拉起成功，截屏确认停在**登录页**（服务器：奥林匹克行动）——M1-d 油数复验需要游戏停在**出击菜单页**，届时请用户手动点过去（我不代点游戏界面）。
- 注：盯梢任务 bash-ash579qm 因 GitHub API 瞬时 EOF 提前退出（`failed to get run: EOF`），与构建本身无关；已改直接 `gh run view` 查状态。

### 2026-09-15 · 阶段一 M1-c 前置：push 前主代理终审（4 文件 7 处修）

- 终审动机：push 触发 GHA 首跑前，主代理对子代理交付物逐行把关（不依赖子代理自查）。
- **rpc.py**（360 行全文审）：**通过零修改**——rec 主路径（3ch 堆叠/动态宽/CTC+tail 自适应）、锁、负路径、接口形状全部正确；整屏 3ch 彩图的通道序约定（BGR/RGB 未显式定义）留 M1-d 真机定案。
- **wrapper.py 修 1 个真 bug**：`_arm_stdin_watchdog` 原逻辑对一切非 tty stdin 挂监控，**stdin=/dev/null（如 Java Redirect.DISCARD）时 read 立即 EOF → wrapper 启动即自尽**；改为仅 `stat.S_ISFIFO`（管道）才挂。其余（killpg 3s 优雅窗、flock 单实例、atexit/signal 幂等）通过。
- **build-rootfs.sh 修 4 处**：① mount 前 `mkdir -p dev/pts proc sys`（ubuntu-base 的 /dev 可能无 pts 子目录，mount --bind 要求挂载点已存在——首跑必炸点）；② `libglib2.0-0` → `libglib2.0-0t64`（Ubuntu 24.04 t64 过渡改名，旧名无安装候选）；③ PYPI_MIRROR 默认 aliyun → **PyPI 官方**（GHA 海外直连最快最稳，与运行时无关——InstallDependencies:false 已锁），env 可覆盖回 aliyun；④ `XZ_OPT=-T0` 多线程压缩（单线程 xz 压 ~600MB 要几分钟）。
- **rootfs.yml 修 1 处**：Spike F 门禁 step 前补 `mount --bind /dev /proc /sys` + EXIT trap 卸载（构建脚本打包前已全卸载；onnxruntime CPU 拓扑探测读 /sys，chroot 裸跑行为不确定）。
- 验证：`bash -n` ✓、`py_compile` ✓、YAML safe_load ✓（`on` 键被 PyYAML 1.1 解析为 True 属预期，GHA 用 YAML 1.2 无此问题）。
- 状态：**仍等用户授权 commit + push**（M1 产物含本终审修改已全部就绪）。

### 2026-09-15 · 阶段一 M1-a：rootfs 资产 curated 与构建链骨架

- **pip 层修正（同日补充指令，覆盖下方"关键决策①"旧方案与"未决项①③"）**：原方案"以 AzurPilot `deploy/headless/requirements.txt` 钉版清单为底 + 黑名单过滤 + 逐包 best-effort"**作废**——核实该清单钉版在 aarch64 + Python 3.12 下大面积死链（`numpy==1.17.4`/`scipy==1.4.1`/`pillow==9.5.0`/`opencv-python-headless==4.7.0.72`/`lz4==4.3.2`/`av==10.0.0`/`psutil==5.9.3`/`pycryptodome==3.9.9`/`pydantic==1.10.9`/`uvicorn[standard]==0.17.6` 等全无 py3.12 wheel），逐包按钉版装照样失败。新策略 = **m0 `termux/setup_env.sh` 真机实证现代化宽松集**（单条 install：`numpy>=2` scipy pillow lxml opencv-python-headless onnxruntime + 纯 Python 16 包；uvicorn 裸版不带 `[standard]`——standard extra 拉 uvloop/httptools 死链，m0 同款；不装 jellyfish/cnocr/mxnet/zerorpc/pyzmq/av，原因留在脚本注释）；装后 **import 硬门禁**（fail-fast）：m0 第 4 节同款校验 + onnxruntime，chroot 内打印各版本号 + `ALL_IMPORTS_OK`，任一 ImportError → `::error::` 退出码 1 中止构建（校验顺序在 jellyfish shim 安装之后）。`dist/pip-failures.txt` 逻辑随之全删（产物注释、dist 拷贝、瘦身清单三处）；`seed_config.py` 一并装入 `/opt/azurpilot/seeds/seed_config.py`（运行时实例播种由阶段三调用，`MAAAL_ALAS_ROOT=/opt/azurpilot`）。

- **产物（全部新建进仓）**：`rootfs/patches/`（m0 补丁集 module/+assets/ 子树 + assets_fix.py，剔除全部 __pycache__ 与 `module/ocr/rpc.py`——后者被 M1-b 的 in-proc 版取代）、`rootfs/seeds/{deploy.yaml,seed_config.py}`、`rootfs/shims/jellyfish.py`、`rootfs/models/ocr/`（PP-OCR 三件套，字节数与源逐一核对一致：9893172/21146753/74947）、`rootfs/build/build-rootfs.sh`、`.github/workflows/rootfs.yml`。
- **deploy.yaml（新写，非照抄 m0）**：行结构与 AzurPilot `config/deploy.template-linux-cn.yaml` 完全一致（已 diff 校验键序）；fullcn 同款镜像（`git.lyoko.io` + aliyun pypi——不能写 gitee/tuna，会被 `config_redirect()` 静默改写）；**更新器七键全锁**（AutoUpdate/InstallDependencies/EnableReload/CheckUpdateInterval/AutoRestartTime/ReplaceAdb/AutoConnect），`AutoUpdate:false` 是保住钉版 commit 的唯一闸门；OCR 键保留（`OcrClientAddress: 127.0.0.1:22300`，in-proc rpc.py 读而不连）；`WebuiPort: 22267`（非 AzurPilotApp 的 22367）。
- **build-rootfs.sh（GHA `ubuntu-24.04-arm` 上执行，本机只 `bash -n` 通过）**：ubuntu-base 24.04 arm64 + chroot 内 apt/pip/git 钉版克隆；挂载用 MOUNTED 数组 + EXIT trap 兜底 + 打包前显式卸载并 `mountpoint` 校验 + tar `--one-file-system` 三重防线（防把宿主 /dev /proc /sys 打进包）；DNS 用静态 resolv.conf（223.5.5.5+1.1.1.1）bind 进去——宿主 stub 127.0.0.53 在 chroot 内必挂。
- **关键决策**：① pip 依赖层最终 = m0 实证现代化宽松集 + import 硬门禁（原"黑名单过滤 requirements 钉版清单"方案已作废，详见上方"pip 层修正"）；② jellyfish 用纯 Python shim 顶替模块名，site-packages 路径在 chroot 内 `sysconfig` 实查不猜前缀；③ 模型装 `/opt/azurpilot/models/ocr/`（v3 自定义路径，与 M1-b rpc.py 的默认 `./models/ocr/` 约定对齐，已核实）；④ `assets_fix.py` 确认是改 **AzurPilot 树内**文件（argv[1]=AzurPilot 根），宿主侧直跑；⑤ BUILD_MANIFEST（JSON）字段：rootfs_version/build_time_utc/upstream_repo/upstream_commit/patches_source(m0-archive 路径@构建时仓 commit)/ocr_models 三件套 sha256/python/onnxruntime/opencv 版本，镜像内 `/opt/azurpilot/BUILD_MANIFEST` + `dist/` 双份（决策 #10，App 可读）。
- **workflow `rootfs.yml`**：workflow_dispatch + push(paths: rootfs/** 或自身）触发；concurrency 按 ref 取消在途；checkout→runner 信息→构建→Spike F 门禁→upload-artifact（rootfs.tar.xz+BUILD_MANIFEST，14 天）。门禁脚本实际 CLI 无 `--chroot`（已核实 argparse：--model-dir/--rpc-path/...），故采用任务许可的 chroot 等价写法：门禁随构建装入 `/opt/azurpilot/`，workflow 里 `sudo chroot work/rootfs /usr/bin/python3 /opt/azurpilot/spike-f-ocr-gate.py --model-dir ... --rpc-path ...`。
- **未决项**：① 构建未在本机执行（Windows 无 chroot），也未在 GHA 实际跑过——首次 workflow 运行才是真正的验证，重点盯 pip 宽松集在 aarch64 的实际解析结果与 import 硬门禁输出；② rootfs 内无中文字体，门禁合成图用例将记 SKIP（不影响 PASS 判定），如需 CI 全量断言要另议字体方案；③ ~~`seeds/seed_config.py` 未装入镜像~~ **已关闭**：随 pip 层修正装入 `/opt/azurpilot/seeds/seed_config.py`；④ `.gitignore` 通用规则 `build/`、`config/` 会误伤 `rootfs/build/` 与 `rootfs/patches/module/config/`，已加否定规则解除（check-ignore 验证通过）。

### 2026-09-15 · 阶段一 M1-b：rpc.py in-proc OCR + wrapper/runner + Spike F 门禁

- **产物（全部新建进仓）**：`rootfs/overlays/module/ocr/rpc.py`（in-proc onnxruntime PP-OCR，替换 m0 TCP 桥版，对外接口逐字保留）、`rootfs/overlays/wrapper.py`（薄 HTTP 127.0.0.1:22400 + 进程组管理）、`rootfs/overlays/runner.py`（薄 runner：`from azurpilot import AzurLaneAutoScript` → `loop()`；注意官方类在 `azurpilot.py`，仓内**没有** `AzurLaneAutoScript.py`）、`rootfs/build/spike-f-ocr-gate.py`（Spike F 精度门禁，CI/真机两用）。
- **rpc.py 要点**：模型惰性加载（`MAAAL_OCR_MODEL_DIR` 可覆盖，默认 `./models/ocr/`）；一把 RLock 串行加载与 session.run（m0 并发教训）；2D 灰度堆叠 3ch（m0 硬坑）；CTC blank 映射按输出维数反推（实测 rec 输出 18710 = keys 18708 + blank + 尾部 ' '）；cand_alphabet 不接（Digit 系 after_process 自清洗，同 m0）；import 失败 → alive() False、OCR raise RequestHumanTakeover；det 后处理对应 PaddleOCR DBPostProcess（pyclipper unclip 用 minAreaRect 等比外扩近似，注释标明）。
- **本机真测**（`.tmp/venv-ocr/`，onnxruntime 1.30.0 + numpy 2.5.3 + cv2 5.0.0 + pillow 12.3.0，输出存 `.tmp/venv-ocr/test-output.txt`）：
  - 模型加载 PASS（det 动态 H/W、rec [B,3,48,动态W]，均 CPUExecutionProvider）。
  - **合成数字行图 rec：13/13 完全正确**（10 组白底黑字含 `/`、`:`，另 3 组反相浅字深底全对）；2D 灰度单通道 2/2（堆叠分支工作）。
  - 真实截图 `m0_game_login.png`（1280×720）det+rec：23 框，'屏幕执行权限认证'、'CADPA'、审批号行等真实 UI 文本命中；游戏美术字/logo 部分空或误读（'auy Jozy' 等），符合该模型一贯表现——**生产主路径是 rec-only**（AzurPilot extract_letters 行图），整屏 det+rec 非生产路径。
  - 生产调用形状直测：`atomic_ocr_for_single_lines(img_list, alphabet)` → `list[list[str]]`，`''.join` 消费精确匹配；`close()` 可重入且关后能惰性重载；8 线程 × 20 次并发 rec 零错误；模型目录缺失 → alive() False + RequestHumanTakeover，`close()` 后恢复。
  - 门禁阈值逻辑自验：4/4=100% → exit 0 PASS；3/4=75% <98% → exit 1 FAIL。
- **未覆盖**：wrapper/runner 无法本机真测（需 AzurPilot + rootfs 环境），仅 `py_compile` 通过（四文件全过）；油数真机图集（m0 DoD ≥98% 口径）待补进 `rootfs/tests/ocr/` 后在 rootfs 内跑 `--real-dir`。
- **留阶段三决策**：WebUI 启停按钮与悬浮窗并存（wrapper 不碰 ProcessManager，双头管理风险；候选：wrapper 同进程 uvicorn 直调 `ProcessManager.get_manager()`）。

### 2026-09-15 · Spike E 收口 + 主屏手势劫持事件彻查（硬约束落地，不能再有第二次）

- **事件**：Spike E 实验窗口（22:15–22:33）内，scrcpy-server `--new-display` 建的虚拟屏带 `FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS` → SystemUI 把 `GestureNavAnim`/`GestureSildeOut`/`NavigationBar0` 建到 VD 上（物证 `spike/e-adb-virtual-display/logs/e3c-power-reset.txt:19-32`）→ **用户主屏手势导航失效**。22:41 停掉实验子代理并杀掉 scrcpy-server（PID 32244/32246），VD 销毁、手势窗口回主屏恢复；22:44 设备侧临时物全清场（`get-displays` 只剩 0、无 app_process 残留），证据图 6 张已留本地 `spike/e-adb-virtual-display/logs/`。
- **根因**：AOSP 原文"virtual displays without this flag shouldn't show home, navigation bar or wallpaper"——scrcpy 默认设该 flag；m0 代码显式不设（`VirtualDisplayManager.kt:39,187-189`），这是 m0 不踩坑的代码级原因。**如实修正**：m0 项目此后长期暂停未用，"数月无事故"不成立，以代码证据为准。
- **硬约束落地（不能再有第二次）**：① REPORT 修正两处"flag 集等价"错误表述并补记事件节（§12）；② `debug.md` 新增 4 条坑（手势劫持生死线、screencap/input 双命名空间、VD 熄灭 shell 点不亮、scrcpy 生命周期两陷阱）；③ `AGENTS.md` 新增「虚拟屏实验纪律」（禁 SYSTEM_DECORATIONS / 实验前后查手势窗口归属 / 清场到 get-displays 只剩 0）；④ `docs/roadmap-v3.md` 阶段二新增「VD flag 硬约束」护栏、Spike E 行补结果。
- **通道状态**：USB 传输在事件中消失（用户拔线），WiFi `192.168.50.190:5555` 已重连恢复调试闭环。
- Spike E 实验本身的结论见下一条（子代理入账）。

### 2026-09-15 · 阶段〇 Spike E：adb 直控虚拟显示屏（B′ + E，PASS 但推翻"甩掉 m0 桥"的预期）

- 结论：**shell 域（uid 2000，无 root）能建虚拟屏、能截屏、能注入输入、能取视频流——能力全通**；但 **不能替代 m0 桥**。
  - 建屏：scrcpy-server **v4.1**（sha256 `deacb991…0cae`）+ `new_display=1280x720/160`，owner=`com.android.shell (uid 2000)`，逐字配方见 `spike/e-adb-virtual-display/REPORT.md` §3.2。
  - 截屏：**`screencap -d` 必须用 `dumpsys SurfaceFlinger --display-id` 的 SF physical id**；用 logical id 就是 m0 §41 那句 `Display Id 'N' is not valid`。用对 id 后拿到真实 1280×720 画面（Settings UI / 壁纸 / 自研 App UI 三张证据图）。
  - 注入：**`input -d` 吃 logical id**（给 physical id 直接 `IllegalArgumentException`），实测 BACK 令 App 从 VD 任务栈消失、滑动令设置列表滚动（前后截图 + 任务栈为证）。
  - 视频：scrcpy h264 1280×720 流实测（IDR + ~116B P 帧），保活客户端落盘取证。
- **推翻 m0 §41 两条负面记录**：`screencap -d` 失败是 ID 命名空间错用；`input -d` 在"VD 可见且窗口 resumed"时确实生效（当年更像"VD 处于熄灭/无焦点窗口"态）。
- **真正的硬约束（新发现）**：VD 因 `FLAG_OWN_DISPLAY_GROUP` 自成 display group，其电源请求由 **WindowManager** 提供；被置 OFF 时 **ColorFade 层**盖住整屏 → 截屏纯黑、任务 STOPPED、窗口无 surface。**shell 域无解**（`cmd display power-reset` 无效；`requestDisplayPower(id, ON)` 只恢复窗口 surface，ColorFade 仍在；`am start` 也不点亮）。实测：设备 `mWakefulness=Awake` 时建屏即 ON（正常渲染），Dozing 时建屏即 OFF；且 VD 创建后 1~2 分钟内会被 framework 自己翻成 OFF（设备仍 Awake）。
- 第二个硬约束：**VD 从不独立存活**——`app_process` 由 `adb shell` 启动，三块 VD 的收场分别是 PC 后台任务超时杀 shell / server 运行 138s 后自灭（死因未取证）/ adb 传输掉线；且实验末段设备 adb 掉线（TCP offline / USB 消失 / `connect` 被拒 10061，ping 正常），需用户在 shizuku-m 点"离线自连" → **设备侧清场 BLOCKED**（待删清单见 REPORT §8）。
- 交付：`spike/e-adb-virtual-display/`（REPORT.md + `logs/` 全量证据含 5 张截图 + `tools/` 保活客户端与 VDLab 反射探针源码/jar）；`handoff/2026-09-15-spike-e.md`。
- 新坑入 `debug.md` 5 条：scrcpy `cleanup` 自删 jar、server 必须有人连、`screencap`/`input` 的 `-d` 命名空间分裂、VD 变黑=display-group 电源请求 OFF（ColorFade）、HONOR 显示栈缺 `requestDisplayPower(int,boolean)`/`getPhysicalDisplayIds`。
- 立场：控制面维持"**桥为主**"，本通路登记为**诊断/备用**；若要生产化需另设计"常驻宿主 + 断线自愈 + VD 重建保活"。

### 2026-09-15 · 建仓：公开仓 AzurPilot 首次 push（用户指令授权）

- 仓库：**https://github.com/Shinarin/AzurPilot**（PUBLIC，默认分支 `main`，首提交 `f89a43f`，194 文件）。描述：AzurPilot 一体化 Android APK：proot rootfs 运行时 + Shizuku 虚拟屏后台挂机。
- 用户拍板：阶段一构建走**路径 1（GHA ARM64 runner 构建 rootfs）**（零本地负担；WSL 方案作废）；仓名定 **AzurPilot**。本次 push 为用户明确指令，属红线六授权范围；后续 push/发版仍需单独授权。
- 入库口径：
  - `m0-archive/`（1.3G，上游 fork fork + AzurPilot 副本）**留本地不入仓**，已加 `.gitignore`。
  - spike 的 APK 二进制（`spike/**/dist/*.apk`，可重建）不入库；`dist/logs/` 全量文本证据**保留入库**（`.gitignore` 否定规则实现）。
  - Gradle 构建产物（`.gradle/`、`build/`、`.kotlin/`、`local.properties`、`*.iml`、`.idea/`）忽略。
  - `.kimi-code/`（项目技能 + mcp.json，无密钥）与 `.vscode/` 入库。
- License：主仓 **AGPL-3.0**（新建根 `LICENSE`，全文取自 m0-archive 同款；v3 合规线决策）。
- 环境事实：gh CLI 已登录 `Shinarin`（repo scope）；git 身份 Elysia \<da2701076760@gmail.com\>；仓库从无提交，分支 `master`→`main`。
- 注意：GitHub 侧 license 识别有索引延迟（push 后 `licenseInfo` 暂为 null，非缺失）；README 按约定发版前再整理。
- 交接：`handoff/2026-09-15-repo.md`。

### 2026-09-15 · 阶段〇 Spike C：幻影进程查杀缓解真机复验（PASS）

- 结论：m0 产品化的两条命令在 Android 16 / MagicOS 10 上**仍然有效，且任一条单独生效**；详见 `spike/a-proot-exec/REPORT-C.md`。
  - `settings put global settings_enable_monitor_phantom_procs false` —— 关掉"扫描+记帐+裁剪"整条链（AMS 连幻影记录都不建），**推荐主手段**。
  - `device_config put activity_manager max_phantom_processes 2147483647` —— 只把裁剪阈值顶到天花板，**副手段**。
  - 两条命令本机均可写、无告警（`exit=0` 无输出），`dumpsys activity settings` 立即生效。
- 真机证据（HONOR PPG-AN00 / Android 16 / SELinux Enforcing）：
  - **对照（默认配置，3 轮）**：App 自己 fork 的 proot 树 51 个进程，被 AMS 成批 SIGKILL 掉 20 个（`Trimming phantom processes`，19 busybox + 1 `libproot.so`），压回 31；proot tracer 一死，guest 存活上报中断（`PROOT_EXIT=137`）。三轮命中时刻 **+29s / +43s / +267s**。
  - **缓解（两条都开，35 分钟）**：48 个 guest 长命进程 + proot + sh 全程零伤亡、`Trimming phantom` 零命中（跨 ~7 个节拍）。
  - **可分性**：只开 flag（B1）或只开 cap（B2）各 420s（跨 ≥1 节拍）均全存活；m0 时代两个旧键（`settings_config_disable_monitor_phantom_procs` / `phantom_process_killer_enable`）**无效**——开跑 2 秒即被裁。
- 机制发现（写进 REPORT-C §2，供阶段三参考）：
  - 裁触发点是 **AMS 的 5 分钟节拍**（`CHECK_EXCESSIVE_POWER_USE_MSG` / `POWER_CHECK_INTERVAL`）→ `AppProfiler.updateCpuStatsNow()` → `PhantomProcessList.updateProcessCpuStatesLocked()`；超额子进程最长能活 ~5 分钟才被**整批**收割（**存活实验窗口必须 ≥1 个节拍**，否则把"还没到节拍"误判成"没被杀"）。
  - 配额是**全系统**的（A2 轮实测连坐杀掉 `com.hypergryph.skland` 的幻影，52 − 20 = 32 与配额吻合）。
  - 「幻影」判定 = 读 **App 主进程的 cgroup**（`/sys/fs/cgroup/apps/uid_<uid>/pid_<pid>/cgroup.procs`）；App 自 fork 的树必然计入，Shizuku/shell 域拉起的树不进该 cgroup（同 uid 也不算）。
- 交付：`spike/a-proot-exec/`（MainActivity 新增 PHANTOM 模式 + `run-phantom-ab.sh` 逐轮编排 + `dist/spikea-phantom-target35-debug.apk` + `dist/logs/phantom-*` 全量证据 + REPORT-C.md）。
- 新坑入 `debug.md` 5 条：guest rootfs 缺 `/dev/null` 导致后台作业全灭（且脚本里的 `2>/dev/null` 会造出伪 null 掩盖问题）、toybox grep 不认 `\|`、cgroup.procs 是幻影计数权威口径、proot tracer 被杀后的孤儿 `am force-stop` 收不掉、5 分钟收割节拍陷阱。
- 结束状态：两条缓解命令保持开启（生产态）；实验前见到的两个旧键已恢复原值；`stay_on_while_plugged_in` 已还原为 0。

### 2026-09-15 · 阶段〇 Spike D：AzurPilot 进程管理 import 面探查（PASS，报告落库）

- 报告全文：`docs/spike-d-wrapper-surface.md`（基线 `.tmp/azurpilot` @ d816310，官方 master）。
- wrapper 主线定型：**不碰 WebUI 的 `ProcessManager`**（multiprocessing 内嵌、state 靠日志文本判定、漂移风险高），采用「薄 runner.py 子进程 import `AzurLaneAutoScript.loop()` + wrapper 管进程组（killpg；AzurPilot 无 SIGTERM handler，`ProcessManager.stop()` 本身就是 kill）」；兜底=完全不 import（`python azurpilot.py` / `-c` 单行 + 原子写 `./config/<name>.json` + killpg），已证实可行、所需信息齐全。
- 衍生设计点（留阶段三）：**调度器双头管理风险**——WebUI 启停按钮走 ProcessManager，悬浮窗走 wrapper，双跑会抢设备。候选解：wrapper 同进程起 uvicorn(WebUI) + sidecar 薄 HTTP 直调 `ProcessManager.get_manager(name)`（与 WebUI 按钮同对象、状态一致）；兜底接受 WebUI 启停按钮失效并文档警告。
- 更新器锁定七键（`AutoUpdate:false`/`InstallDependencies:false`/`EnableReload:false`/`CheckUpdateInterval:0`/`AutoRestartTime:null`/adb 三键 false）+ 暗路径清单：`KeepLocalChanges` 键在 master **不存在**；唯一 pip 执行点 `deploy/pip.py:153`；`git reset --hard` 会丢本地补丁 → `AutoUpdate:false` 是唯一闸门；`updater.schedule_update()` 无条件挂载，须 `AutoRestartTime:null` 自删。阶段一 rootfs 构建直接照用。
- 环境事实：无头跑也必须能 import pywebio（`config.py` 模块级 import 并猴补丁）；Python 3.14 默认 forkserver 对 WebUI multiprocessing 路径有中风险，runner 方案天然绕开；AzurPilot 全仓无 Python 版本断言。

### 2026-09-15 · 阶段〇 Spike A：APK 内 proot exec 真机实证（PASS，唯一生死线探针）

- 结论：**targetSdk 35 上可行，无需降级**；targetSdk 28 变体全绿保留为后备。详见 `spike/a-proot-exec/REPORT.md`。
- 设备实证（HONOR PPG-AN00 / Android 16 / 4KB 页 / SELinux Enforcing）：
  - `libproot.so`、busybox stub、proot loader、静态 shim 全部可从 `nativeLibraryDir` 直接 exec；proot ptrace 引擎正常（`PROOT_PTRACE_OK`）。
  - `proot -r <filesDir>/rootfs` 可运行 rootfs 内 busybox（多合一）、静态 ELF、动态 ELF，**含客户机内再 `execve` 子进程**——targetSdk 35 全部 PASS（debug 与 release 构建均复现）。
  - 唯一被拦的是 app 进程直接 `execve(filesDir/...)`：35 报 `error=13 Permission denied`，28 允许（预期语义，非缺陷）。
- 工程发现（已固化到 spike 工程与 debug.md）：
  - AGP 与安装器都只认 `lib*.so` 命名；Termux 二进制的版本化 SONAME（`libtalloc.so.2`、`libbusybox.so.1.38.0`）必须做 dynstr 原地改写（新工具 `spike/a-proot-exec/tools/patch-dynstr.py`）。
  - Termux busybox 1.38 是 4KB stub + `libbusybox.so.1.38.0` 载荷；applet 由 `argv[0]` 选择，从 nld 执行需 argv0 shim（`libspike_shim.so`）。
  - `proot -r` 的 rootfs 需自包含：解释器副本必须 +x（否则报 `execve: Permission denied` 极易误判为 SELinux 拦截）；客户机动态库需完整传递闭包。
  - 16KB 页：本机 4KB；全部随包 ELF 的 `PT_LOAD` 对齐 0x4000，`zipalign -c -P 16` 三份 APK 全通过。
- 交付：`spike/a-proot-exec/`（工程 + `dist/` 三份 APK + `dist/logs/` 全量原始证据 + REPORT.md）。
- 未覆盖（留阶段一/五）：真实 Ubuntu rootfs（glibc 解释器分支）复验、长时稳定性、多 ROM 抽检、正式签名后 nld 清单复核。

### 2026-09-15 · 路线图 v3 定稿（三轮设计拷问）

- 产出 `docs/roadmap-v3.md`（v2 的修订版，13 项已确认决策见该文第 0 节）。核心变更：
  - **后台挂机 = 硬需求** → 控制面保留 m0 桥（adb 无法触达虚拟屏，m0 实证 `m0-archive/docs/debug.md:281-285`）；v2 的 adb 四步流水线整体降级为 Spike E 探索项。
  - Spike 清单重排为 A/B′/C/D/E/F，唯一生死线 = Spike A（APK 内 proot exec，有 targetSdk 28 兜底）。
  - OCR 走 m0 备案"C 路线"转正：rootfs 内 onnxruntime + PP-OCR，上游业务引擎 业务引擎正式剔除。
- 关键事实核查：
  - shizuku-m = 官方 Shizuku v13.6.0 fork（3 commits），机制为 `tcpip:5555` 通道 + RSA 预授权；**跨开机仍需一次在线激活**（persist 属性被 SELinux 拒），同一开机周期内可离线秒拉起。
  - "fullcn 版 AzurPilot" = 官方 Releases 的大陆变体（`AzurPilotApp_0.4.10_fullcn.7z`），与官方 master 同架构，差异仅国内镜像源配置；rootfs 取"官方 master 钉 commit（Gitee 镜像）+ fullcn 同款镜像 deploy 配置"。
- 用户决策：shizuku-m 不内置进 APK，README + 应用内引导自装；仓库公开后置（例外：阶段一主仓单独公开，GHA ARM64 runner 免费仅限公共仓）。
- 状态：**等用户确认 v3**，确认后进入阶段〇。
