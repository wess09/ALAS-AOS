# 设置页「日志」功能开发文档

> 面向读者：接管「设置页日志部分」后续改动的开发会话。本文是自包含事实文档，无需读其他上下文即可开工。
> 代码基线：v0.1.3 之后的日志区改造（2026-09-21 落地），仓库根 `D:\VSCodeCache\azurpilot-azurpilot`。
> 行号对应该基线；若代码已漂移，以符号名为准、行号为辅。

---

## 0. 项目一分钟背景

AzurPilot：Android App（Kotlin + Compose），在 proot 环境里跑 AzurPilot（碧蓝航线自动化脚本，Python）。
三层进程模型：

```
App 进程 (Kotlin, appId=io.github.shinarin.azurpilot)
  ├─ WebView → http://127.0.0.1:22267   (AzurPilot 原生 WebUI)
  ├─ HTTP    → http://127.0.0.1:22400   (wrapper.py，薄控制层：/status /configs /logs /start /stop /tool/*)
  └─ AIDL    → Shizuku 特权进程 (:22300 BridgeServer，截屏/点击转发)
```

- App 源码：`app/app/src/main/java/com/aliothmoon/azurpilot/`（**Java 包名刻意保留 `com.aliothmoon.azurpilot`，与 appId 解耦，勿"顺手"改**）。
- wrapper.py 及 AzurPilot 补丁：`app/app/src/main/assets/azurpilot/{overlay,patches}/`，与仓库根 `rootfs/` 目录**双源同步**（改一边必须 cp 到另一边并 cmp 验证，铁律）。
- 启动器日志文件全部落在**外部私有目录** `/sdcard/Android/data/io.github.shinarin.azurpilot/files/`（选择原因：`constant/AppFiles.kt:6-11` 注释——特权进程是 shell 身份，只有这里写得进且与 app 看到同一份；release 无 run-as，adb shell 也可读）。
- AzurPilot 日志落在**内部存储** `filesDir/rootfs/opt/azurpilot/log/`——App 进程直接可读，**adb 读不到**，查看/导出必须在 App 进程内做（不经过 wrapper HTTP：wrapper 的 /logs 只服务 mtime 最新的一个 txt，历史拿不到）。

## 1. 功能全景：设置页日志卡（5 行）

设置页日志卡 `LogCard`：`ui/settings/SettingsScreen.kt`（挂载于 DisplayCard 之后）。五行：

| 行 | 实现 | 行为 |
|---|---|---|
| 「启动器日志」 | AppNavigationRow | 导航 `Routes.APP_LOG` → `AppLogScreen`（§3） |
| 「AzurPilot日志」 | AppNavigationRow | 导航 `Routes.ALAS_LOG` → `AzurPilotLogScreen`（§4） |
| 「导出AzurPilot日志」 | AppNavigationRow | `AppRoot` 置 `exportKind=AzurPilot`，弹 `LogExportController` 底部 sheet（§6） |
| 「导出启动器日志」 | AppNavigationRow | `exportKind=LAUNCHER`，同一 sheet |
| 「自动清理日志」开关 | AppLabeledControlRow + Switch | 开直接落盘；**关弹确认框**（警告"长期使用可能占大量手机空间"）→ `SettingsIntent.SetAutoCleanLogs`（§7） |

`AppRoot` 的 sheet 状态是 `var exportKind by remember { mutableStateOf<LogExportKind?>(null) }`（可空即显隐 + 类型二合一）。

## 2. 日志文件清单（设备端真实产物）

启动器侧根：`/sdcard/Android/data/io.github.shinarin.azurpilot/files/`，路径常量 `constant/AppPaths.kt` + `constant/AppFiles.kt`。

| 文件 | 写入方 | 滚动/清理策略 |
|---|---|---|
| `log/app.log` (+`app.1~4.log`) | Timber `FileLogTree`（`log/LogTrees.kt`）→ `log/AppLogWriter.kt` | 4MB/份×5 份滚动，启动**追加不清空**；**常驻全量落盘**（无级别门槛，见 §8）；`purge()` 清空（「全部清除」的范围） |
| `log/proot/session.log` | `proot/ProotHost.kt`：`[host]` 阶段行 + `[proot-out]`/`[proot-err]` | 无滚动；**mtime 超 7 天且超 2MB 时截尾留最后 2MB**（`ProotHost.truncateSessionLogIfStale()`，cleanupStale 内、spawnSession 前，受自动清理开关门控） |
| `log/crash/crash_yyyyMMdd_HHmmss.txt` | `log/CrashHandler.kt`（install 于 `上游 fork.kt`） | 保留 10 份 |
| `debug/service_bind_debug.log` | `privileged/ServiceBootLogger.kt` | 512KB 单份滚动 |
| `debug/service_boot_debug.log` | 特权进程侧 `remote/RemoteBootTrace.kt` | 256KB 截断重写；**路径在特权进程是硬推导，挪目录要两边同改** |
| `debug/root_launch_debug.log` | `privileged/RootRemoteServiceConnector.kt` | — |
| `log/export/*.zip` | `log/LogExportService.kt` | 导出产物；**按类型各自先删旧 zip 再打新包**（§6） |

AzurPilot 侧（proot 内 `/opt/azurpilot/log`，实体 `filesDir/rootfs/opt/azurpilot/log/`，访问口 `log/AzurPilotLogSource.kt`）：

| 产物 | 形态 | 清理 |
|---|---|---|
| 按天日志 | `YYYY-MM-DD_{config}.txt`，整天 append 不换名 | **7 天前删除**（按文件名日期前缀，`LogCleaner`，§7） |
| 错误现场 | `error/<毫秒时间戳>/`（log.txt + *.png） | **过期整文件夹删**（按时间戳，`LogCleaner`） |

## 3. 启动器日志页（原「错误日志」改名扩源）

- 列表页 `ui/logs/AppLogScreen.kt` + `log/AppLogViewModel.kt`：数据源 = `log/LauncherLogScanner.scan(AppPaths.LOG_DIR)`——递归整个 `log/` 目录，收 `app*.log`、`proot/session.log`、`crash/*.txt`（export/ 的 zip 天然不匹配），**mtime 倒序**；列表项显示**相对路径**/大小/mtime。
- 「全部清除」**范围维持 app.log 系列**（`AppLogWriter.purge()`，不扩大）——session.log 是会话时间线、crash 是崩溃现场，清了等于自断排查后路；确认弹窗文案已写明这一点。
- 正文页 `ui/logs/AppLogDetailScreen.kt`：路由参数是相对路径（`Uri.encode` 编码，含 `/`），canonical 校验防越界后交给尾部加载查看器（§5）。

## 4. AzurPilot 日志页

- 列表页 `ui/logs/AzurPilotLogScreen.kt` + `log/AzurPilotLogViewModel.kt`：两个分区（LazyColumn 普通 header item）——
  - 「错误记录」= `error/<毫秒时间戳>/`（纯数字名校验），ts 倒序，显示格式化时间 + 内含文件数；
  - 「按天日志」= `*.txt`，mtime 倒序，显示文件名/大小/mtime。
- txt 详情 `ui/logs/AzurPilotLogDetailScreen.kt`：`AzurPilotLogSource.dailyFile(name)` 解析（拒路径段）→ 尾部加载查看器。
- 错误现场详情 `ui/logs/AzurPilotErrorDetailScreen.kt` + `log/AzurPilotErrorDetailViewModel.kt`：log.txt 用同款尾部加载查看器（`LogTailContent`，weight 1f）+ 下方 PNG 缩略图 LazyRow（inSampleSize 采样解码，`rememberDecodedBitmap`）；点缩略图全屏 Dialog 查看（fit-center，点按关闭，**无捏合缩放**——规格明确不要）。
- 路由全部注册在 `ui/navigation/Routes.kt` 与 `ui/AppRoot.kt` NavHost；VM 注册在 `di/ViewModelModule.kt`。

## 5. 尾部加载查看器（两源共用）

- 读取：`log/LogTailReader.kt`——RandomAccessFile seek 读末尾 **512KB** 窗口；切口从第一个换行后开始（UTF-8 半字符与半行一次砍掉）；`hasMore = 起点 > 0`。
- 会话：`log/LogTailViewModel.kt`——打开读尾块，「加载更早」每次再往前 512KB 并**前插**；`load(path)` 幂等（重组重放不重读），`loadingEarlier` 防连点。
- UI：`ui/logs/LogTailScreen.kt`（`LogTailScreen` 带顶栏整页 / `LogTailContent` 无顶栏正文，供错误现场页内嵌）。
- 级别上色 `ui/logs/LogLineColors.kt`，兼容两种格式：
  - app.log `] E `/`] W `（单字母；E/A→error，W→warning；不照抄 参考实现 全词判据）；
  - AzurPilot `YYYY-MM-DD HH:MM:SS.mmm | LEVEL | msg` 的 `| ERROR |`/`| WARNING |`。
- 治的就是旧 `AppLogDetailViewModel` 整份 `readLines()` 的 OOM/卡顿隐患（4MB×5 + 无上限 session.log）。

## 6. 导出拆两条

UI：`ui/logs/LogExportController.kt`（底部 sheet，`kind: LogExportKind?` 为空即隐藏；SAF launcher 无条件注册——回调回来时 sheet 已关，`pendingKind` 另存发起时的类型）。两个动作沿用：分享（FileProvider）/ 保存到设备（SAF `CreateDocument`）。

核心：`log/LogExportService.kt` + `LogExportKind { AzurPilot, LAUNCHER }`（`di/LogModule.kt` 注入 `baseDir=AppPaths.ROOT`、`launcherRoots=[LOG_DIR, DEBUG_DIR]`、`logDir=AzurPilotLogSource.logDir()`）：

| 类型 | 产物名 | 收集 | zip 条目 | properties.txt |
|---|---|---|---|---|
| AzurPilot | `azurpilot_logs_yyyyMMdd_HHmmss.zip` | `LogExportCollector.collectAzurPilot()`：txt 按文件名 `yyyy-MM-dd` 前缀、error 文件夹按毫秒 ts，**只收近 7 天**；前缀对不上的 txt 保留（多带无伤，漏现场误事） | 镜像官方 issue 格式 `log/xxx`（`log/` + 相对 AzurPilot 日志目录路径） | **不附** |
| LAUNCHER | `launcher_logs_yyyyMMdd_HHmmss.zip` | 沿用 `LogExportCollector.collect()`（跳过 /export/ 自身；`/run/ /focus/ /logcat/ /crash/` 只留近 7 天） | 相对 `AppPaths.ROOT` | **常驻附**（getprop，已去掉 debugMode 门槛） |

- 旧 zip 清理按类型前缀各自进行；启动器导出连带清 v0.1.3 前的 `azurpilot_logs_*` 遗留命名。
- FileProvider 配置不动（`AndroidManifest.xml` + `res/xml/provider_paths.xml`，zip 落 external-files 已覆盖）。

## 7. 自动清理（7 天固定，无配置项；开关 = `AppSettings.autoCleanLogs`，默认开）

- **AzurPilot 部分**：`log/LogCleaner.kt`——每次冷启动在 `上游 fork` 的 `settings.loaded` 门控协程内（IO dispatcher）静默执行，`runCatching` 包裹，完成后 `Timber.w` 一行汇总（删了几项/释放多少）。判定基准 = 文件日期 < 今天-7 天：txt 按文件名前缀，error 文件夹按毫秒 ts（日粒度对齐）。
- **session.log 部分**：`ProotHost.truncateSessionLogIfStale()`（在 `cleanupStale()` 内）——mtime 超 7 天且超 2MB 则截尾留 2MB。放这里是因为 startLocked 每次启动必经且早于 spawnSession（无 drain 线程写入竞争）；截断刷新 mtime，崩溃重拉循环不会重复截。设置读盘最多等 2s（`SETTINGS_LOADED_WAIT_MS`），等不到本次跳过。
- crash/（10 份上限）、app.log（4MB×5 滚动）自带天花板，不归清理管。
- 设置链：`AppSettings.autoCleanLogs`（String 存布尔，KSP 生成 key）→ `AppSettingsManager` StateFlow+setter（fan-out collect 同步、`_loaded` 保持最后赋值）→ `AppSettingsGateway` → `SettingsContracts.SetAutoCleanLogs` → `SettingsViewModel`。

## 8. 已删除的历史链条（排障时别再去翻）

- **调试模式整链**（2026-09-21 删）：`AppSettings.debugMode`、确认框、`SettingsEvent.RestartApp`、`util/Misc.kt`、app.log 落盘级别门槛（`LogTrees.kt`）。它名不副实：注释称"给特权进程传 isDebug"，实际 `service/HostState.kt:105` 传的是 `BuildConfig.DEBUG`；确认框承诺的 logcat 抓取链整条休眠。**app.log 自此常驻全量落盘**（4MB×5 滚动自带总量上限）。
- **logcat 抓取链**（同日删）：`privileged/LogcatServiceManager.kt`、`remote/LogcatCaptureServiceImpl.kt`、`aidl/ILogcatService.aidl`、`RemoteServiceManager.initialize()` 里的调用、`AppFiles.LOGCAT_*` 常量。生前只有 `initialize()` 被调，`bind()/startCapture()` 从无调用方。
- **死代码/死串**：`ui/components/AnsiAnnotatedText.kt`（ANSI 上色，run_log 界面配套）、字符串 `settings_log_archive_desc`、`run_log_*` 组、`log_outcome_*` 组、`settings_debug_mode*`、`dialog_enable_debug_*`、`common_restart`（中英两边同步删）。
- 导出 zip 旧名 `azurpilot_logs_*` → 现按类型 `azurpilot_logs_*` / `launcher_logs_*`（分享 subject 串不变）。
- 注意：`src/test/` 单测源集在改造前已失修（FakeAppSettingsGateway 等实现了 main 里已不存在的接口成员，`runner/` 包测试对不上 main），assembleRelease 不编译测试源集，不受影响；本次仅同步了 Fake 的 debugMode→autoCleanLogs。

## 9. 坑点与验证方式

1. **关键失败必须走 `Timber.w` 才落盘**：release 下 logcat 只放 W+（`ReleaseTree`），但 FileLogTree 全量落盘后 app.log 里 i/d 也可见；Timber.w 仍是"必须看见"的语义标记（LogCleaner 汇总、session.log 截尾都用 w）。
2. **debug/ 路径双端硬推导**：`RemoteBootTrace.kt:36-41` 在特权进程侧自行拼 `/sdcard/Android/data/{pkg}/files/debug/`，挪目录必须两边同改。
3. **路由参数带 `/` 必须 `Uri.encode`**：启动器日志详情是相对路径（`proot/session.log`），不编码会被路由拆段。
4. **AzurPilot 日志 adb 不可读**：内部存储，release 无 run-as；取证走 App 内导出（§6 AzurPilot zip）或 debug 包 run-as。
5. **wrapper /logs 只服务 mtime 最新的一个 txt**：历史 txt 与 error 现场经 HTTP 拿不到，这是 AzurPilot 日志页直读文件系统的原因。
6. App 日志取证（release 无 run-as，adb shell 直接可读）：
   `adb shell "tail -N /sdcard/Android/data/io.github.shinarin.azurpilot/files/log/proot/session.log"`
   LogCleaner 留痕：`adb shell "grep LogCleaner /sdcard/Android/data/io.github.shinarin.azurpilot/files/log/app.log"`
7. wrapper 日志取证：`adb forward tcp:22400 tcp:22400` → `curl "127.0.0.1:22400/logs?tail=200"`
8. 构建：`cd app && JAVA_HOME='D:\VSCodeCache\shizku-m\build-env\jdk-17.0.2' GRADLE_USER_HOME='D:\VSCodeCache\azurpilot-azurpilot\.tmp\gradle-home' cmd //c 'gradlew.bat assembleRelease --console=plain'`
9. 账册规则：完成可验证步骤后更新 `devlog.md`（倒序，最新在上）；红线：不改 AzurPilot 上游源码（`assets/azurpilot/patches/` 之外）、不私自锁屏测验、push/发版需逐次授权。
