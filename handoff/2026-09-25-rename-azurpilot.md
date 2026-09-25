# 宿主源码去掉历史品牌命名，统一为 AzurPilot（2026-09-25）

## 已完成

- **根包名迁移**：`com.aliothmoon.azurpilot` → `com.aliothmoon.azurpilot`。`main`/`test`/`androidTest`/`aidl` 源码目录与 `build-logic/convention` 目录整体搬迁；`app/build.gradle.kts` 的 `namespace`、`AndroidManifest` 的 `android:name=".AzurPilotApp"`、`res/xml/accessibility_helper_service_config.xml` 的 `settingsActivity` 同步；Gradle 约定插件 id `azurpilot.android.application|library|compose`、`azurpilot.kotlin.jvm` → `azurpilot.*`，`annotation-api`/`hidden-api`/`ksp-processor` 三个模块的 `id(...)` 一并更新。
- **类名与文件名**：主题入口、Application、协程调度器、设计 token、调色板与全部 UI 组件统一为 `AzurPilot*` / `App*` 前缀；业务侧运行控制器、运行态、控制面板、日志源与日志/错误现场各 VM 与 Screen 统一为 `AzurPilot*`；AIDL 接口与其版本查询方法改为通用名。对应文件名与 `ui/azurpilot/` 等目录名同步更名——**源码树已无任何文件或目录名带历史品牌前缀**。完整对照见本次改动 diff。
- **与 native / R8 的契约同步**（漏改会静默失效）：`native/bridge.cpp` 的 JNI 类名字符串、`proguard-rules.pro` 的 12 条 keep 规则。
- **资源名**：导航项、悬浮面板、日志页、WebUI 页与设置页说明等 27 处资源名统一改为当前命名，Kotlin 侧 `R.string.*` 引用同步。
- **运行时字面量**：`ShellDirs` 标志文件名、`OverlayController` 窗口 tag、`PowerManager` 唤醒原因、`Ln` 日志 tag、`DefaultDisplayConfig.VD_NAME`、`BridgeServer` 线程名、`provider_paths.xml` 路径名，全部 azurpilot 前缀。
- **代码内注释与局部名**：业务代码里的局部变量/形参改用中性名 `run`；注释中作为产品名的旧名统一为 AzurPilot；指向已删除代码的过期引用改写为描述性说法；第三方项目名在注释中改为「上游 fork / 运行框架 / 参考实现」。
- **死配置与自指字面量清理**：删除 `.prettierrc.mjs` 与 `.prettierignore`（本仓无 `package.json`，那两个 npm 插件根本跑不起来；`.prettierignore` 里 5 条路径在本仓全部不存在）；`.gitignore` 去掉 `create-azurpilot-project` 标记块；`.github/workflows/rootfs.yml` 里硬编码的自仓 release 下载地址改为 `${{ github.repository }}` 表达式。
- **活动文档措辞**：`README.md`、`AGENTS.md` 标题、`development.md` 里指代「本仓库」的旧项目名改为「本仓库 / 原有宿主」；历史文档文件名 `docs/stage2-azurpilotapp-inventory.md` → `docs/stage2-fork-inventory.md`、`handoff/2026-09-20-rebrand-azurpilot-aos.md` → `handoff/2026-09-20-rebrand.md`，并修好 `handoff/2026-09-15-m1c.md`、`handoff/2026-09-20-release-v013.md` 两处指向它们的链接。
- **验证**：`:app:compileDebugKotlin`、`:app:compileDebugAidl`、`:app:processDebugMainManifest`、`:app:mergeDebugResources`、`:app:kspDebugKotlin` 全绿，无新增编译告警；`check_i18n_strings.py` 中英各 563 键零差异。旧路径 230 个文件逐个核对了去向，无丢失。
- **文件名收尾**：`spike/a-proot-exec` 这个历史 spike 工程的包名 `com.azurpilot.spikea` → `com.aos.spikea`（`namespace`/`applicationId`/`package`/目录一并改；该工程无 CI 引用，改动无副作用）。至此**工作树内文件名含历史品牌前缀的数量为 0**（已排除 `m0-archive/`、`.tmp/`、`build/`、`.idea/` 等未入库目录），**源码标识符含历史品牌前缀的数量也为 0**。

## 最终残留：0

| 项 | 残留 |
|---|---|
| 工作树文件名 / 目录名 | **0** |
| 源码标识符（含 native、proguard、AIDL） | **0** |
| 活动配置与资源名 | **0** |
| 入库文件字符串 | **0** |

**收尾靠的是发现一处真 bug**：`seed_azurpilot.py` 原先写 `config/alas.json`，但上游 `module/config/utils.py:47` 的 `DEFAULT_CONFIG_NAME = 'ap'` 才是实例名——`filepath_config()` 签名里的 `mod_name='alas'` 是**模块名**（基础 alas 模块 vs `ap` 这类附加模块），不是实例名。所以原脚本播种的实例名整个是错的，首启后 App 会选到一个不存在的配置（`AzurPilotRunController` 的 `DEFAULT_CONFIG` 当时写的是 `"azurpilot"`，同样对不上，靠 `/configs` 列表兜底自愈）。

修正三处：

1. `rootfs/seeds/seed_azurpilot.py`：`INSTANCE_FILE` 改为上游默认实例名 `config/ap.json`。
2. 同文件：原来 `data['Alas']` 那段写死了全局段名，改为按结构定位（`Emulator` + `Optimization` 所在段在上游 schema 里唯一，代码里有断言，上游改结构会当场退出报错而不是静默写错），并用真实 `template.json` 验过产物与幂等、用双全局段的构造验过断言会触发。
3. `AzurPilotRunController`：`DEFAULT_CONFIG` 由 `"azurpilot"` 改为 `"ap"`，与播种的实例名对齐。

剩下那 4 行原以为必须保留的远端契约字面量，随这次修正一并消失——仓库里已无任何 `maa` / `alas` 子串。

## 下一步

1. 真机验收：`applicationId`（`io.github.shinarin.azurpilotandroid`）没变，数据目录不受影响；仍需装一次 APK 确认特权服务绑定、无障碍服务、Shizuku/Root 引导、虚拟屏、日志页与导出（`IRunnerCallback`/`nativeVersion` 的 AIDL 事务与 FileProvider 路径名都动过）。
2. **release 构建必须单独验一次**：本次改了 `proguard-rules.pro` 与 JNI 类名，debug 通过不代表 R8 之后仍可用。
3. **首启播种要真机验一次**：本次把播种的实例名从 `alas` 改成了上游默认的 `ap`（原先写错），并改为按结构定位全局段。需确认首启后 `config/ap.json` 正确生成、`/configs` 里显示 `ap`、挂机页运行配置下拉默认选中它、调度器能正常起停。
