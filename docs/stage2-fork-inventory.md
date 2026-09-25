# 阶段二 · 上游 fork fork 宿主外壳减法盘点

> 来源：2026-09-15 explore 子代理只读调研（fork 钉版 `m0-archive 里的 fork 基线` @ b2b0f54 实测）。
> 用途：阶段二执行的工作底稿。`F` = `m0-archive 里的 fork 基线`（只读档案，复活另建工作副本）。

## 〇、三条改变任务理解的发现

1. **桥不在 fork 里，"保留 5 端点"是重写不是删代码。** m0 的桥 = Python 写的 运行框架 Agent（`m0-archive/spike/m0/agent/main.py`），跑在特权进程 fork 的 agent child 里，协议 = 行分隔 JSON + 二进制帧的裸 TCP 127.0.0.1:22300（**非 HTTP**）。端点实现：`ping/screencap/click/swipe/ocr/shell` 共 6 个（`main.py:148-232`），`ocr` 走 运行框架 PP-OCR（剔除项），其余经 `context.tasker.controller` → libMaaFramework → libMaaAndroidNativeControlUnit → dlopen `libbridge.so`。删 libMaaCore 会连桥本体一起删掉。
2. **桥的取帧/注入设施与业务解耦，可原地留用。** `libbridge.so`（C API：`GetLockedPixels/UnlockPixels/DispatchInputMessage`，`F/app/src/main/native/bridge.h:71-75`）+ `InputControlUtils`（隐藏 InputManager 反射注入，`F/.../bridge/InputControlUtils.java:164-216`）+ `DriverClass`（native upcall）。新桥在特权进程内直接调 `NativeBridgeLib`/`InputControlUtils`。**唯一缺口：帧数据无 Java 裸字节出口**（现 JNI 只有 `getFrameBufferBitmap()` 与 `getFrameCount()`，`bridge.cpp:22-38`）——需新增 JNI 或 `Bitmap.copyPixelsToBuffer`。
3. **钉版点带 6 处未提交改动 = m0 WebView 资产，复活前必须固化。** 新增 `ui/azurpilot/AzurPilotScreen.kt`、`res/xml/network_security_config.xml`；改 `AndroidManifest.xml`、`ui/AppRoot.kt`、`ui/navigation/Routes.kt`、`res/values{,-en}/strings.xml`。无 commit/branch/tag，`git clean` 即丢。

## 一、阶段二宪法口径（roadmap-v3.md:65-72 摘要）

- 保留：特权进程（虚拟屏+截屏/注入）、桥代理（5 端点）、全屏 WebView 容器、Shizuku 权限与配对辅助、前台保活 + 电池优化引导。
- 剔除：libMaaCore.so 业务引擎（Pipeline/资源加载）、OCR 桥端点。
- VD flag 硬约束：禁 `SHOULD_SHOW_SYSTEM_DECORATIONS` 与 `ROTATES_WITH_CONTENT`，flag 集照 `VirtualDisplayManager.kt:178-202`（含 `STEAL_TOP_FOCUS_DISABLED`）；建屏后回归断言手势三窗口在 display 0；保活照 m0 每 4s `userActivity(displayId)`。
- DoD：APK 可请求 Shizuku 权限、建虚拟屏、桥 ping 通、WebView 显示空白页、呼出悬浮球。

## 二、模块与源码归类

### 模块（F/settings.gradle.kts:26-37）
| 模块 | 处置 |
|---|---|
| `:app` | 主战场 |
| `:hidden-api` | 保留（特权进程隐藏 API 存根） |
| `:annotation-api` + `:ksp-processor` | 保留（AppSettings 依赖） |
| `:semi-icons` | 保留（UI 图标） |
| `:macrobenchmark` | 可选剔除 |
| `:build-logic/convention` | 保留，拆掉 `azurpilot.pi.assets` 与 `azurpilot.agent.runtime` 两个插件 |

### app 包（com.aliothmoon.azurpilot）
| 包 | 职责 | 归类 |
|---|---|---|
| `bridge/`（4 文件） | JNI 入口/DriverClass/InputControlUtils/TouchPointerSequence | **保留** |
| `azurpilot/`（4） | JNA 声明 运行框架 C API | **剔除** |
| `remote/`（20） | 特权进程侧 | **混合**：`internal/` 与 AIDL 面保留；`Runner`/`ExecAgentHost`/`AgentInstaller`/`AgentRuntimeDescriptor` 剔除 |
| `privileged/`（20） | Shizuku/Root 授权绑定、权限代授、安装辅助 | **保留** |
| `root/`（6） | Root 后端 binder 回投 | 保留（可选删） |
| `runner/`（29） | 运行框架 业务管线 | **剔除主体**；`RunnerPort` 接口被 UI/悬浮窗/前台服务依赖，需留壳或退化 |
| `project/`（7） | PI 解包加载 | **剔除** |
| `schedule/`（12） | 定时任务 | **剔除**（含 manifest 的 3 个声明与 2 个权限） |
| `notification/`（22） | 通知中心 + 11 推送渠道 | 渠道层与业务无关，可留可删（建议阶段二先删，需要再捡） |
| `overlay/`（9） | 悬浮球/面板/边框/屏保 | **保留**（`OverlayController` 强依赖 RunnerPort，需改造） |
| `service/`（3） | RunForegroundService 保活 FGS / AccessibilityHelperService | **保留**（FGS 需换状态源） |
| `third/`（16） | 隐藏 API 反射壳 | **保留** |
| `app/src/main/native/` | bridge.cpp 等 + launcher.c | **保留**（产 libbridge.so/liblauncher.so） |
| `assets/shizuku.apk` | 随包 Shizuku 安装源 | 决策 #4 不内置 APK → 删（连 `ShizukuInstallHelper`） |
| `ui/`、`settings/`、`log/`、`i18n/`、`theme/`、`constant/`、`di/`、`util/` | 壳/UI 基础设施 | 保留（ui 砍 Tasks/Schedule/Home 等页） |
| `session/`、`telemetry/` | PI 会话状态机 / Sentry | **剔除** |

### jniLibs（~192 MiB，gitignore 内、新 clone 为空）
- 剔除 13 个：`libMaaFramework/libMaaUtils/libMaaToolkit/libMaa*ControlUnit(×5)/libMaaAgentClient/libMaaAgentServer/libfastdeploy_ppocr/libonnxruntime/libopencv_world4` → 包体从 200MB+ 打到 <20MB。
- 保留：`libbridge.so`（56KB）、`liblauncher.so`（10KB）、`libc++_shared.so`（9.2MB，CMake c++_shared 自建，**删它断 libbridge**）。

## 三、新桥（阶段二核心工程量）

特权进程内 TCP 22300 服务（Kotlin，`RemoteServiceImpl` 或新 `BridgeServer.kt`），端点 `ping/screencap/click/swipe/shell`（去 ocr）：
- 截屏：`NativeBridgeLib` 帧缓冲 + 新增取裸字节 JNI；坐标/尺寸口径照抄 `VirtualDisplayManager.getConfig()`（原 `Runner.buildControllerConfig` `remote/Runner.kt:456-477` 的注释）。
- 注入：`InputControlUtils.down/move/up`（`RemoteServiceImpl.kt:249-253` 已有带 displayId 的同款调用）。**`setContactSupport()` 须在新桥初始化时显式调用**（原在 `Runner.prepare()` `remote/Runner.kt:253-260`，删业务后没人调，多指 contact 走 0 分支）。
- shell：`ProcessBuilder`（特权进程天然 shell uid）+ 剥 `LD_LIBRARY_PATH`（防 agent 库污染系统二进制，m0 `main.py:216-218` 经验）。
- 协议照 m0：行分隔 JSON + screencap 响应后随裸字节；全局串行锁保留（m0 ZMQ 非线程安全教训）；不再需要 10s 心跳（无 Agent socket），但要替代性"桥活着"判据。
- AzurPilot 侧客户端不动：`rootfs/patches/module/device/method/azurpilot.py`（截图:107/点击:117/长按:120/滑动:128/shell:138/VD id 探测:148-156/app 启停:160-191）。

## 四、Shizuku 链路（保留现状）

- 权限声明 `AndroidManifest.xml:8`（`moe.shizuku.manager.permission.API_V23`）+ queries :174-176 + provider :146-152。
- 初始化 `privileged/RemoteServiceManager.kt:142-151`；权限请求 `ShizukuManager.kt:51-118`；绑定 `ShizukuRemoteServiceConnector.kt:82-92`（`processNameSuffix("service")`、`daemon(false)`、tag+version）。
- AIDL 面 `RemoteService.aidl`（显式 transaction id，destroy=16777114 Shizuku 保留）。
- 特权进程入口 `remote/RemoteServiceImpl.kt:36`（`Workarounds.apply()` + 看门狗 :358-378）。
- 特权操作面：虚拟屏 `:151-191`、亮屏解锁 `:89-98,213`、主屏分辨率 `:221-233`、目标 app 控制 `:100-113,194-211`、权限代授 `:290-326`（幻影查杀 `PermissionGrantHelper.kt:131`）、预览 surface/手动触摸 `:237-259`、**业务执行 `:263-284`（剔除）**。

## 五、WebView 壳（保留，两处小改）

- `ui/azurpilot/AzurPilotScreen.kt`：URL `127.0.0.1:22267`（`:40`）；vh 塌缩修复注入（`:49-61,116`）；JS/domStorage（`:96-97`）；错误页拉起按钮当前指向 `com.termux`（`:169-172`）→ **改拉起 rootfs 侧服务**。
- 明文放行仅 127.0.0.1/localhost（`network_security_config.xml`）。
- WebView 与桥解耦（只连 22267），是阶段二最省力的一点。

## 六、保活机制（保留 + 换状态源）

- FGS `service/RunForegroundService.kt:53`（specialUse，子类型文案改外壳语义 `:101-104`）；**观察源绑 RunnerPort/FocusDispatcher/RunLogRecorder（:54-57,109-131），剔业务后换"桥/rootfs 是否活着"或直接常驻**——注意 MIUI Adj=905 被 force-stop 的实测注释，保活层不许降级。
- 无 WakeLock（全仓无）；亮屏 = 特权侧 `PowerController.kt:85-109` 每 4s `userActivity(displayId)`（建屏成功即启动 `RemoteServiceImpl.kt:171-177`）。
- 双向看门狗：特权→app `/proc/<pid>` 5s（`RemoteServiceImpl.kt:358-378`）；app→特权 binder linkToDeath（`RemoteServiceManager.kt:71-105,153-166`）。
- `remote/internal/AppWatchdog.kt` 目标 app 看门狗保留。
- 电池优化引导 `privileged/PermissionManager.kt:196-207` + 特权代授 `PermissionGrantHelper.kt:91,103`。

## 七、构建系统

- Gradle 9.4.1 / AGP 9.2.1 / Kotlin 2.3.21 / KSP 2.3.9；compileSdk 37 / target 36 / min 28 / Java 17；CMake 3.22.1（写死）+ NDK。
- **坑①：`F/local.properties:2` `pi.profile=D:/VSCodeCache/azurpilot-azurpilot/pi-profile.m0.yaml` 是死路径**（文件已归档 `m0-archive/`），`BuildProfile.kt:91-92` 硬失败 → 构建前先删该行（无 PI 仅 warn）或指新路径。
- 坑②：jniLibs 新 clone 为空（原流程 `scripts/setup_framework.py` 拉 v5.12.3）——减法后不需要。
- 坑③：`gradle.properties` 要求 `-Xmx4096m`（AGP9+KSP+Compose 2048m 不够）；未开 configuration-cache；Windows 用 `gradlew.bat`；`build.debugAbi=arm64-v8a` 保留省时。
- 本机工具链：JDK17/21 + SDK @ `D:\VSCodeCache\shizku-m\build-env\`（只读复用），`GRADLE_USER_HOME` 指本仓 `.tmp/`；`F/app/build/`（893MB）证明本机可构建。
- 随剔除删依赖：`jna`、`sentry`、`markwon×6`（可选 `angus.mail`、`reorderable`）；保留 shizuku api/provider、libsu、xx-permissions、floatingx、compose/navigation/datastore/window、koin、timber、kotlinx-serialization、okhttp、tracing。

## 八、需改造项（真正工程量）与坑

1. **新桥**（见第三节）——阶段二最大件。
2. **FGS 状态源**（见第六节）。
3. **悬浮球脱钩**：`OverlayController` 依赖 `RunnerPort.state`（`:49,109-110,196-214,315`）与 `RunMode` → 引入外壳级 `HostState` 让 RunnerPort 退化成薄接口。
4. **AppRoot tab 结构**：5 tab（AzurPilot/Home/Tasks/Schedule/Settings，`AppRoot.kt:136-148`）→ 剩 AzurPilot + 设置（+日志）；pager/NavHost/`Routes.mainTabs`（`Routes.kt:17`）同步；`TopDestination.entries[page]` 索引耦合，删枚举项注意顺序。
5. **R8/清单收尾**：`build-logic/.../VerifyR8KeepsTask.kt:20-29` 的 `R8_CRITICAL_CLASSES` 含 azurpilot.MaaFrameworkLibrary/MaaAgentClientLibrary → release 必挂，删类同步删；`proguard-rules.pro` 删 JNA 段、**必须保留** `bridge.NativeBridgeLib`/`DriverClass`（native 字面名 upcall）与 `remote.**`/`root.**`/`third.**`/AIDL 段；manifest FGS 子类型文案。
6. **`ShellDirs.AGENT_DIR`/`JNA_TMPDIR`/`AppPaths.FOCUS_DIR`** 随剔除消失。
7. **包体/命名**：appId `com.aliothmoon.azurpilot` + 配方后缀 → 建议去配方机制，硬编码 AzurPilot 身份（AGPL LICENSE 与署名保留）。
8. 坑：`interface.json` 的 agent 声明/`agent-runtime.json` 会让旧包启动报错（`ExecAgentHost.kt:27-45`）；m0-archive 只读，复活另建工作副本，勿把 `app/build/`（893MB）/`.azurpilot`/`.azurpilot-cache` 提交进 git。

## 九、VD flag 现状核查（硬约束）

**合规**：唯一建 VD 路径 `remote/internal/VirtualDisplayManager.kt:178-202`（`buildDisplayFlags()`）：
- 基线 `PUBLIC|PRESENTATION|OWN_CONTENT_ONLY|SUPPORTS_TOUCH`（:179-182）+ `DESTROY_CONTENT_ON_REMOVAL`（:184-186）。
- `SHOULD_SHOW_SYSTEM_DECORATIONS` 被 `VD_SYSTEM_DECORATIONS=false`（`:39`）挡住，**未启用**。
- API33+：`TRUSTED|OWN_DISPLAY_GROUP|ALWAYS_UNLOCKED|TOUCH_FEEDBACK_DISABLED`；API34+：加 `OWN_FOCUS|DEVICE_DISPLAY_GROUP|STEAL_TOP_FOCUS_DISABLED`。
- `ROTATES_WITH_CONTENT`（`:28`）是死常量（无引用），删以正视听。
- 建屏：`createNewVirtualDisplay("AzurPilotVirtualDisplay",1280,720,160,...)`（`third/wrappers/DisplayManager.java:170-176` 反射；`constant/DefaultDisplayConfig.kt:13,17-19`）。`PrimaryDisplayManager.kt:117-131` 是主屏镜像采集，非新 VD。
