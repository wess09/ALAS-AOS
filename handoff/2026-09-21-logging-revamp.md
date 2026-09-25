# 交接：设置页日志区改造（2026-09-21）

> 基线：v0.1.3 之后的工作区改动，未提交、未发版。设计共识经用户逐条确认（grilling 三轮）。

## 已完成

设置页日志卡重做为 5 行（原「错误日志/导出日志/调试模式」三行废止）：

1. **启动器日志**：列表递归整个 `files/log/`（app.log 系列 + `proot/session.log` + `crash/`），mtime 倒序；「全部清除」仍只清 app.log 系列。
2. **AzurPilot日志**：直读 `filesDir/rootfs/opt/azurpilot/log/`（`log/AzurPilotLogSource.kt`），按天 txt + error 文件夹两分区；error 详情页含 log.txt + PNG 截图查看（缩略 LazyRow + 全屏 Dialog）。
3. **导出AzurPilot日志**：`azurpilot_logs_yyyyMMdd_HHmmss.zip`，近 7 天，zip 内镜像 `log/...` 相对路径（官方 issue 格式），不附 properties.txt。
4. **导出启动器日志**：`launcher_logs_yyyyMMdd_HHmmss.zip`，维持原收集裁剪，常驻附 properties.txt；顺带清理遗留 `azurpilot_logs_` 旧 zip。
5. **自动清理开关**（`AppSettings.autoCleanLogs`，默认 true）：关闭弹确认框警告空间风险。冷启动 `LogCleaner`（IO 协程、Timber.w 留痕）删 >7 天 AzurPilot txt（文件名日期前缀）与 error 目录（毫秒时间戳）；`session.log` 截尾在 `ProotHost.cleanupStale()`（>7 天未动且 >2MB → 留尾 2MB，受同一开关门控）。

统一尾部加载查看器：`log/LogTailReader.kt`（512KB 尾块、换行对齐）+ `log/LogTailViewModel.kt` + `ui/logs/LogTailScreen.kt` + `LogLineColors.kt`（兼容 app.log `] E/W ` 与 AzurPilot `| ERROR/WARNING |`）。

调试模式整链删除：开关 UI/确认框、`debugMode` 设置字段、RestartApp 重启链（`util/Misc.kt` 整删）、休眠 logcat 链（`LogcatServiceManager`/`LogcatCaptureServiceImpl`/`ILogcatService.aidl` + `RemoteServiceManager` 调用 + proguard/R8 校验 keep）。**app.log 定格常驻全量落盘**（`LogTrees.kt` 门槛删除）。死代码清理：`AnsiAnnotatedText.kt`、`settings_log_archive_desc`/`run_log_*`/`log_outcome_*` 等死串（中英双边）。

## 验证

- `assembleRelease` 绿（3 轮修复：KDoc 嵌套注释、R8 keep 校验漏删）。
- 真机（AVAY025422002864）冒烟：装机后设置卡 5 行渲染正确；两个日志列表/详情页正常；清理留痕 `LogCleaner: 过期日志清理完成` 出现在 app.log（I/D 级行可见 = 全量落盘生效）。截图在 `.tmp/smoke/`。
- 账册：`devlog.md` 顶部 2026-09-21 条目；`docs/logging-dev.md` 已重写为新现状（9 节）。

## 遗留（下次/用户走查）

1. 导出 SAF/分享实际落盘链路未走查（adb 无法驱动 SAF），需用户手动导一次验证 zip 内容。
2. 「加载更早」前插未实测（设备无 >512KB 日志）；error 现场详情页未实测（设备无 `log/error/`，需 AzurPilot 真实报错后产生）。
3. `src/test/` 单测源集既存失修（引用 main 已删类），assembleRelease 不编译它，非本次引入；`FakeAppSettingsGateway.kt` 已同步新接口。
4. `generated/baselineProfiles/*.txt` 含已删类名（生成物无害，下次生成刷新）；`log_archive_*`/`tasks_log` 死串不在本次删除清单。

## 下一步候选

- 用户走查导出 zip 后，若官方格式有反馈再调收集范围。
- 发版时记得 CHANGELOG/README 同步（5 行日志卡、调试模式移除是用户可见变更）。
