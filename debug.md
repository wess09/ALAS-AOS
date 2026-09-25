# Debug · 坑点记录

> 本文件记录新阶段踩过的坑（现象 / 根本原因 / 解决方案）。
> 说明：本文件的历史条目中，指代本产品的名称已统一为当前命名（AzurPilot）；各代旧名见 Git 历史与 release 记录。
>
> **历史坑点（m0 阶段，全真机实证）见 `m0-archive/docs/debug.md` 与 `m0-archive/docs/devlog/`。** 高频索引：
> WebView `vh` 塌缩（注入 innerHeight 修复）｜幻影进程查杀（`max_phantom_processes` / `settings_enable_monitor_phantom_procs`）｜mDNS `_adb-tls-connect` 端口过期但广播残留｜AzurPilot PP-OCR 对 2D 单通道静默返空（堆叠 3ch）｜AzurPilot 截图 BGR↔AzurPilot RGB 翻转｜RUN_COMMAND 权限只授清单声明方｜`am force-stop` 杀不掉 shell uid 残留（须显式 kill）｜桥 30s 无流量判死（10s 心跳）。

## [2026-09-25] 包名重命名漏改 native JNI 类名与 R8 keep 规则：编译通过但 release 静默失效

- **现象**：把 `com.aliothmoon.azurpilot` 全量改成 `com.aliothmoon.azurpilot` 后 `compileDebugKotlin`/`compileDebugAidl` 全绿，看起来万事大吉。
- **根本原因**：有两处包名不是 Kotlin 引用，grep `.kt/.java` 抓不到——① `app/src/main/native/bridge.cpp` 里 `static constexpr char kNativeBridgeClass[] = "com/aliothmoon/azurpilot/bridge/NativeBridgeLib"` 这种 JNI `FindClass` 用的斜杠路径字符串；② `app/proguard-rules.pro` 里 12 条 `-keep class com.aliothmoon.azurpilot.**` 与 `IRunnerCallback` 的 keep 规则。漏改后 debug 构建照样能跑（不混淆、JNI 类名错则桥整体不可用），release 下 R8 会把桥与特权服务剪掉。
- **解决方案**：重命名包名时固定做一次「非源码扩展名」扫描：`grep -rn "<旧包名>" --include=*.cpp --include=*.h --include=*.pro --include=*.xml --include=*.aidl --include=*.json .`，其中 native 侧记得用斜杠形式 `com/<旧包名>/` 再扫一遍。**判据：凡是被 `FindClass`/反射/混淆规则按字符串引用的类名，都不受编译器保护。**

## [2026-09-25] 从上游 fork 继承下来的根配置是死配置：`.prettierrc.mjs` 在本仓根本跑不起来

- **现象**：仓库根有 `.prettierrc.mjs` + `.prettierignore`（还入库了），但本仓**没有 `package.json`**，也没有 `node_modules`——那两个文件里 `import` 的 `prettier-plugin-multiline-arrays` 与 `@nekosu/prettier-plugin-azurpilot-sort` 无从安装，配置里的 `pipelinePatterns` / `interfacePatterns` 也无处生效。
- **根本原因**：这两份配置是 fork 上游仓库根目录的遗产，服务于上游的 PI/pipeline JSON 与前端格式化流程；本仓「做减法」时删掉了 JS 工具链，配置却留了下来。`.prettierignore` 里列的 `上游公共资源目录/`、`resource/base/model/ocr/`、`tools/schema/*.schema.json`、`.create-azurpilot-project/` 在本仓**一个都不存在**，是判断它已死的直接证据。
- **解决方案**：直接删除这两个文件。**判据：拿到一份继承来的配置文件，先把里面每条路径与每个依赖在本仓实际验证一遍——路径全不存在、依赖装不上，就是死配置，该删而不是该改。**

## [2026-09-25] 机械重命名的两个坑：前缀组合名与二次替换

- **现象**：用词边界正则批量替换旧标识符时，**带前缀的组合名**（`Light`/`Dark` 这类前缀 + 被替换名）一个都没匹配上；而第一轮已经改好的路由常量，在第二轮「注释里产品名改新名」时又被改了一次，与调用点引用的大小写形式对不上，编译才报 `Unresolved reference`。
- **根本原因**：`` 词边界要求前一个字符是非单词字符，`LightPalette` 里 `AppPalette` 前面是字母 `t`，所以匹配不上；而分轮次替换时，后一轮的规则会命中前一轮刚生成的名字。
- **解决方案**：机械重命名分两轮做且每轮只用**词内不含歧义**的模式——第一轮替换全名（含 `Light`/`Dark` 这类组合前缀）；第二轮改注释与产品名时先跑一遍「当前残留清单」（`grep -rhoE` 出所有含目标词的标识符再决定），并对已确定的常量名做精确串替换而不是词边界替换。改完必须编译一次，别信 grep 结果。

## [2026-09-25] Windows 下 `mv` 目录报 "Device or resource busy"

- **现象**：迁移源码目录 `com/aliothmoon/azurpilot` → `com/aliothmoon/azurpilot` 时 `mv` 失败，报 `Device or resource busy`，同一批其它目录却都成功。
- **根本原因**：Windows 不允许重命名仍被进程持有句柄的目录——当时 shell 的工作目录（以及 IDE 的索引）正落在该目录里。
- **解决方案**：先把 shell 的 cwd 切到仓库根再执行 `mv`，或对该目录单独重试（句柄随上一条命令退出而释放）：`for i in 1 2 3; do mv ... && break; sleep 2; done`。批量脚本里不要用 `set -e` 掩盖成「部分成功」——失败后先 `ls` 确认真实状态再补做，避免漏改。

## [2026-09-25] 裸调 `/android/*` 会 400：上游实例解析有一个必然落空的硬编码回落

- **现象**：App 里「停止挂机」点了没反应、工具任务（半自动点击/活动剧情）点了没反应；调度器没跑时整个控制面板还显示「环境未就绪」。同一个后端，用浏览器打开 WebUI 一切正常。
- **根本原因**：上游 `module/api/android.py` 的 `instance(request)` 在请求没带 `config` 查询参数时，会 `next((key for key, proc in _processes.items() if proc.alive), '<硬编码的实例名>')`，再拿结果过一次 `configs.path(name)`（文件不存在即抛 `NOT_FOUND`）。那个硬编码名字是上游基础模块的默认实例名，而本部署播种的实例名是另一个（见同日另一条坑），因此**只要走到回落分支就必然 400**。哪些调用会走到：`/status`、`/logs`、`/stop`、`/tool/stop`——App 原先只给 `/start` 与 `/tool/start` 带了 `config`，其余全裸调。调度器在跑时 `/stop` 恰好能借「取第一个存活进程」蒙对，一旦那一刻进程状态没读到就落回硬编码名 → 400 → 界面停在原状，表现就是「停止没反应」。
- **解决方案**：把 `config` 当作必填——`/status`、`/logs`、`/stop`、`/tool/stop` 全部显式带上（停止优先用 `/status` 回报的在跑实例名，否则用下拉选中项）。另外把 `GET /configs` 提到刷新链最前：它**不**解析实例，是「WebUI 进程活着」最可靠的探针，还能在调度器没起时就把实例列表读出来。**判据：调用带「可选参数」的接口时，先去看它在参数缺省时回落到什么——回落值若是对方的默认值，跨部署就会变成一个必然失败的分支。**

## [2026-09-25] AGP 默认 debug keystore 是每次构建现生成的：CI 出的 debug APK 互相装不上

- **现象**：CI 产出的 debug APK 覆盖安装到已装同版本 APK 的设备上，报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（签名不一致）；每次 CI 构建的 APK 签名都不同。
- **根本原因**：构建脚本只给 release 配了签名；debug 走 AGP 默认的 `~/.android/debug.keystore`。本地这份由 Android SDK 生成、CI runner 上每次都是**新生成**的（`~/.android` 不在缓存里），于是每个 runner、每次构建的 debug 签名都不一样。
- **解决方案**：仓库内固定一份 `app/app/debug.keystore`（Android 通用的公开调试密钥，`androiddebugkey`/`android`/有效期 30 年），在约定插件里让 debug signingConfig 指向它，路径与口令可用 `DEBUG_KEYSTORE_PATH` / `DEBUG_KEYSTORE_PASSWORD` 等覆盖。提交调试密钥是标准做法——它本就是公开的，为的正是让所有产物签名一致。用 `./gradlew :app:signingReport` 确认 debug variant 的 Store 已指向仓内文件。

## [2026-09-25] seed 把「模块名」当成「实例名」：首启播种的配置名整个是错的

- **现象**：首启播种出的实例配置文件名、上游默认实例名、App 侧默认选中项，三者互不相同，谁都不是上游认的那一个。
- **根本原因**：上游 config 工具模块里有两个极易混淆的名字。`filepath_config(filename, mod_name=...)` 的 `mod_name` 是**模块名**（基础模块与附加模块之分，附加模块会拼进文件名的后缀里），而**实例名**是同一模块里另一个独立常量，由 `filepath_config()` 拼成 `./config/<实例名>.json`。原脚本照着函数默认参数里那个模块名去拼文件名，于是实例名从一开始就是错的。危害很隐性：枚举实例的函数会把 `config/` 下任何不带模块后缀的 `*.json` 都当成实例，所以错名字照样被列出来、照样能跑，只是与部署约定不一致，且 App 的默认选中项指向一个并不存在的实例，靠 `/configs` 列表兜底选中第一个才自愈。
- **解决方案**：播种用的文件名改为上游默认实例名对应的值，App 侧默认选中项同步对齐；顺带把原先写死的「全局段」名改为按结构定位（含 `Emulator` + `Optimization` 的段在上游 schema 里唯一，带断言，上游改结构会当场退出报错而不是静默写错）。**判据：同一个函数签名里的 `mod_name` 与实例名是两回事——拼文件名前先确认用的是哪一个常量，别照着函数默认参数抄。**

## [2026-09-25] material3 1.4.0 的 `MotionScheme` 是 internal：主题里取不到 `MaterialTheme.motionScheme`

- **现象**：按 M3 官方写法给 `MaterialTheme(..., motionScheme = MotionScheme.standard())` 传参，编译报 `Cannot access 'interface MotionScheme': it is internal in file`；`MaterialTheme.motionScheme` 同样不可访问。
- **根本原因**：`javap` 看得到 `MotionScheme` / `MotionScheme$Companion.standard$material3()`，但 `$material3` 后缀就是 Kotlin 的 internal 名字改写——该 API 在 1.4.0 尚未对库外开放，只有 `MaterialExpressiveTheme` 内部在用。
- **解决方案**：不要在应用主题里显式传 `motionScheme`。`MaterialTheme` 的默认值已经是 standard 动效方案，写不写都一样；组件里需要动效时用 `AnimatedVisibility` / `animate*AsState` 的默认 spec，别去取 `MaterialTheme.motionScheme`。**判断某个 M3 API 能不能用，直接看 javap 名字里有没有 `$<模块名>` 后缀，别只看文档。**

## [2026-09-25] `:app:compileDebugKotlin` 被 rootfs 校验任务挡住

- **现象**：只想编译 Kotlin 验证改动，却在 `:app:verifyBundledAzurPilotRuntime` 上失败：`缺少 AzurPilot ARM64 rootfs.tar.xz`。
- **根本原因**：`app/build.gradle.kts` 用 `tasks.matching { it.name.startsWith("package") || it.name.startsWith("assemble") }` 挂依赖，而 AGP 的资源任务 `packageDebugResources` 正好以 `package` 开头，于是它连同 `compileDebugKotlin` 一并被拖进依赖图。
- **解决方案**：本地纯编译验证加 `-x verifyBundledAzurPilotRuntime`：`./gradlew :app:compileDebugKotlin --offline -x verifyBundledAzurPilotRuntime`（`JAVA_HOME` 指向 Android Studio 的 jbr）。发布/打包不受影响，缺资产仍会被门禁拦下。

## [2026-09-25] `gradlew ... | tail` 会把构建失败伪装成成功

- **现象**：`./gradlew :app:compileDebugKotlin -q 2>&1 | tail -30` 退出码 0、无输出，据此判定“基线编译通过”，随后才发现那次构建其实根本没验证到东西。
- **根本原因**：管道的退出码取的是最后一个命令（`tail`）的，gradle 的非零码被吞掉；`-q` 又同时压掉了 lifecycle 日志，看起来就像“静默成功”。
- **解决方案**：要拿真实结果就重定向到文件再自取退出码——`./gradlew ... > .tmp/xxx.log 2>&1; echo "EXIT=$?"`，然后 grep 日志里的 `^e: ` / `BUILD FAILED`。**任何“构建通过”的结论都要能看到 gradle 自己的退出码。**

## [2026-09-25] `app/src/test` 大量孤儿测试：自 上游 fork 做减法起就没再编译过

- **现象**：`./gradlew :app:testDebugUnitTest` 在编译测试源集阶段就失败，报 `Unresolved reference 'NativeVersion'` / `provider` / `ansiAnnotated` / `ResolutionPreference` 等一大批符号不存在。
- **根本原因**：`project/`、`runner/`、`session/`、`schedule/`、`telemetry/`、`notification/`、`azurpilot/`、`remote/` 下的测试与 `FakeAppSettingsGateway` 是 上游 fork 时期的产物，对应的主源码在阶段二「复活做减法」时已被整包删除；CI 只跑 `:app:assembleRelease/Debug`，从不编译测试，所以一直没有暴露。
- **解决方案**：本次 MD3 改造未新增破坏（`FakeAppSettingsGateway` 原本就编不过，`ButtonPrimitiveBoundaryTest` 已随自定义按钮体系一并删除）。**这些孤儿测试尚未清理**——要么按当前主源码重写，要么整体删除，改动前需用户确认。在此之前不要用 `testDebugUnitTest` 作为门禁，UI 改动只能靠 `compileDebugKotlin` 与真机走查验证。

## [2026-09-24] 同机 Chrome 正常但 System WebView 抽屉异常：改用浏览器 Custom Tab

- **现象**：AP WebUI 在手机 Chrome 中正常；App 内 WebView 打开左侧抽屉后，运行总览正文文字被错误绘制到侧边栏上。
- **根本原因**：页面结构和移动断点可由 Chrome 对照排除；异常集中在 System WebView 对 fixed 抽屉、transform 和层叠上下文的合成路径。随后给抽屉加入 `contain: layout paint` 会与其 transform/visibility 动画冲突，把整个侧栏裁掉，证明继续在 AP 页面上补 WebView CSS 风险更大。
- **解决方案**：回滚 AP 的 WebView 专用 UA/CSS，Android 宿主不再嵌入 System WebView。AzurPilot 页在运行环境就绪后用 Chrome Custom Tab 打开回环 WebUI，不支持 Custom Tabs 时退回普通浏览器 Intent；返回键仍能回到 App。

## [2026-09-24] Android 运行时删掉 `.git` 后不能复用 WebUI Git 更新器

- **现象**：手机更新页显示 `UPDATE FAILED`，日志为 `git fetch origin master` 后报 `fatal: not a git repository`。
- **根本原因**：Android 构建有意移除 `.git`，并要求源码、预构建前端和兼容清单作为同一运行时包原子切换；桌面端 Git 更新器仍被前端暴露，调用路径与 Android 的整包更新机制冲突。
- **解决方案**：AzurPilot 在 `AZURPILOT_ANDROID=1` 时停止后台 Git 检查，更新页读取 `BUILD_MANIFEST` 展示当前提交并说明由 Android App 管理；AOS 仅在 App 进程启动前下载、校验、切换和回滚整包。运行时 release 资产发布前，更新检查保留当前版本。

## [2026-09-24] Shizuku 未授权时直接 bind 只会静默失败，启动按钮必须走权限入口

- **现象**：Root 模式能正常建立虚拟屏，Shizuku 模式点击“启动环境”无画面；日志反复出现 `BIND_DENIED backend=SHIZUKU not granted`，设备桥端口随后持续拒绝连接。
- **根本原因**：`HostState.ensureEnvironmentStarted()` 直接调用底层 `servicePort.bind()`，该调用不会发起授权请求；UI 又继续向尚未存在的 AzurPilot 控制服务发送启动命令，造成多个表象叠加。
- **解决方案**：启动环境、调度器和工具任务统一先经过 `PermissionGateway.bindService()`，等待特权服务连接并成功创建虚拟屏后才发送任务启动请求；拒绝或超时立即停止该次启动链。

## [2026-09-18] git:// 9418 裸 TCP 在运营商网络下不可靠：ping 通≠端口快，大陆正解是 CDN pack（443）

- **现象**：手机热更新连续 5 次 `FAILED fetch`，每次白烧 240s 超时（开机链 4 分钟）；但 ping git.lyoko.io 正常（24~48ms），ls-remote（小包）也能成。
- **根本原因**：git.lyoko.io 只有 git:// 协议（9418 端口裸 TCP，实测无 443/HTTPS 服务）；运营商对非常用端口的 QoS/限速让大传输（depth-50 浅树几十 MB）永远跑不完，小包（ls-remote、ICMP）却不受影响——**「ping 通」「小包通」都不能推断「大传输通」**。PC 侧同网络复现同款（git clone 被 reset/超时），且 GitHub 也是小包通大包断。
- **解决方案**：更新通道改走上游自带的 CDN pack（`deploy/git_over_cdn`：443 HTTPS，`latest.json`(3s) → `{latest}/{current}.zip` 增量 pack 仅 ~400KB，超时都是秒级），git:// 仅作兜底；两通道皆败记日期当日退避。**教训：给 AzurPilot 类大陆分发场景设计网络链路，优先复用上游已验证的 CDN 通道，别假设 git 协议端口在任何网络下都可用。**

## [2026-09-18] 「App 死了」先三分支再立案：release 启动链有数分钟全静默 PREPARING，ps 查无进程≠代码崩溃

- **现象**：装机重启后 wrapper 长时间不应答、`ps -A | grep alioth` 一度全空，像 App 自体死亡复发；但数分钟后 wrapper/gui/runner 全部自愈上线。
- **根本原因**：App 启动链 PREPARING 段（overlay 同步 → env_fix pip → 热更新 ls-remote）在 **release 版完全静默**——Timber FileLogTree 只落 W+、logcat 无 plant，进程活着却无任何日志痕迹；叠加 install force-stop 余波/系统后台管理杀进程，呈现"死了又活"。本次实证排除代码崩溃：`log/crash/` 无文件、`/data/tombstones/` 无新件（最新是旧日期）、app.log 两条 Startup 横幅间零 W+ 行。
- **解决方案**：判「App 自体死亡」立案前固定三分支——①`log/crash/` 有没有新 crash 文件（CrashHandler 必留）；②`/data/tombstones/` 有没有新日期件（native 崩溃必留）；③`ps -A | grep libproot` + session.log 尾部（proot 有没有在起/在跑）。三者皆空且进程在=静默 PREPARING，等 5 分钟再下结论；进程真无且有墓碑才按崩溃立案。

## [2026-09-18] mxnet GRU 是 cuDNN 变体不是教科书公式：手写前向必须逐字复刻 gate 序与耦合方式

- **现象**：纯 numpy 重写 densenet-lite-gru 前向时，按教科书 GRU 公式（h=z·h_prev+(1-z)·n，n=tanh(i2h_n + r·h2h_n)）实现对拍全错。
- **根本原因**：mxnet `rnn_cell.py` 的 GRU 实现是 cuDNN 变体——gate 序为 r/z/n，且耦合方式是 **h=(1-z)·n+z·h_prev**（z 的语义与部分教科书相反）；BN eps=1e-5（gluon 默认，非 PyTorch 1e-3/1e-5 之混）；densenet-lite 末段 stage3 池化是 **k(2,1) s(2,1)**（seq_len=floor(W/4)），以符号 json 实证为准而非论文/文档所载 (2,2)。**教训：移植模型前向时，论文和文档都不可信，唯一事实来源是推理框架的源码与符号定义；对拍要分层（emb/rnn/logits/prob）定位第一处分叉，不要只看最终字符串。**
- **解决方案**：`module/ocr/al_numpy.py` 按 mxnet 源码逐字复刻（cuDNN gate 序/耦合、BN eps=1e-5、k(2,1) 末段池化、CTC blank=0 + 0.5 置信门 + width//4 截尾 + cand_alphabet 乘法掩码）；13 组参考批（含真徽章/名字图/噪声、单图/混批）分层对拍 prob max|Δ|≈2e-6、字符串全同才算过。性能：einsum 逐样本卷积 329ms/行 → im2col+sgemm 18ms/行（ARM 真机 13~70ms 实测同量级）。

## [2026-09-18] 上游模板是为模拟器锐化滤镜调的手机渲染相似度跌破阈值：开关识别静默 'unknown' 引发连锁崩溃

- **现象**：手机刷活动图 D3 崩溃循环（MapDetectionError×伏击战），桌面端同版本 AzurPilot 同图却能正常刷——排除 AzurPilot 代码与图机制差异。
- **根本原因**：AzurPilot 的 `CLEAR_MODE_TITLE`/`AUTO_SEARCH_TITLE` 模板是为 MuMu 等桌面模拟器的锐化渲染调校的；同画面在手机 GPU 渲染下抗锯齿/字体描边不同，TM_CCOEFF_NORMED 仅 0.829/0.783，**恰好跌破 0.85 阈值**。模板匹配失败不会报错，而是 `SwitchClearMode.get` 静默返回 'unknown' → 周回模式状态丢失 → MAP_HAS_AMBUSH=True → 手动模式踩进图潜艇伏击 → map_init 死等 → MapDetectionError。连锁极深：表象（伏击战崩溃）离根因（模板相似度）隔了 4 层因果，且"未 3 星"的表象会误导归因（旧结论就误判成"上游缺陷×图状态，环境层无解"）。**教训：桌面能用手机不能用时，优先怀疑识别层（模板/OCR/颜色阈值）的渲染差异，不要怀疑游戏逻辑；"静默 unknown"类失败要在链路每一环打印实际识别值。**
- **解决方案**：用**本机实拍帧**自制模板（整帧 1280x720 与上游同约定），走 assets 补丁机制铺到 `assets/cn/handler/`（不改 AzurPilot 代码）。验证法：离线对新帧求 sim（应 ≈1.0）+ 对旧模板复测（应稳定 <0.85，确认确定性失败而非噪声）；在线证据=日志 `Map_info` 行出现 `clear_mode` 字样 + `Clear_Mode on`。

## [2026-09-18] 通用 PP-OCR 读不了游戏字体：名字区糊字 OCR 出 '01' 毒害章节识别（chapter '0' → CampaignNameError）

- **现象**：runner 进活动图前死于 `ScriptEnd: Campaign name error`，连图都进不去；ensure_campaign_ui 循环 20 次全挂。
- **根本原因**：选关徽章名字区 OCR 读出 `['01','D3','B2']`——D1 未通关时是 0% 红签小字样式，其名字图的 **D 字形经 extract_letters 二值化后糊成实心团**（内孔消失），AzurPilot 手机端换用的通用 PP-OCR 模型把它读成 '01' → 后处理变 '0-1' → chapter='0' → Counter 平票取首见 → `campaign_chapter=='0'` 触发 raise。**桌面 AzurPilot 不犯此错是因为它用 azur_lane 专用 cnocr 模型（AL 字体微调，charset 仅 39 字符）**——对同一手机帧同一名字区实测读出 `['D1','D3-','B2']`，像素无罪，纯模型差距。通用候选全灭：v5_ch_mobile 读 'ս'、v4_ch_mobile 'սս'、v4_en_mobile 空串、v5_en_mobile 字典维度不匹配。**教训：游戏专用字体（尤其小字号+描边+二值化后糊掉的字形）不要指望通用 OCR；上游用什么模型就用什么模型。排查 OCR 错误先拿上游模型对同一输入对质，立刻区分"像素问题"还是"模型问题"。**
- **解决方案**：本次走"消除输入"绕法——把 D1 手动通关，徽标变 Clear! 大签样式（大字号白字，字母内孔清晰），名字 OCR 恢复正常。通用修法（后续项）：azur_lane cnocr 模型（MXNet）转 ONNX 上机，或纯 numpy 手写 densenet-lite-gru 前向，集成进 rpc.py 按 lang='azur_lane' 路由+39 字符 keys。注意 OCR 后处理的平票逻辑会放大单次误读（Counter 取首见），一个糊字足以全盘皆输。

## [2026-09-17] adb forward 直接占 127.0.0.1:22267 与桌面版 AzurPilot WebUI 撞车：桌面打开显示的是手机内容

- **现象**：用户打开桌面版 AzurPilot，浏览器里显示的却是项目（手机/AzurPilot）的 AzurPilot WebUI 内容。
- **根本原因**：调试时 `adb forward tcp:22267 tcp:22267` 把手机 WebUI 绑到 PC 的 127.0.0.1:22267 且**调完没拆**；桌面版 AzurPilot WebUI 默认也用 22267（其 deploy.yaml 未自定义 WebuiPort）。后果：①桌面 webui 启动 bind 失败（端口被 adb.exe 占，netstat 实锤 PID 3828）；②浏览器访问 127.0.0.1:22267 实际连到 adb 转发 → 手机内容。撞车窗口期在该页面改的配置**全部写进了手机端**（桌面 上游配置文件 mtime 停在 9-12，未被写入——可用 mtime 自证）。
- **解决方案**：**PC 侧转发端口永远不与设备服务端口同号**——手机 WebUI 固定转发 `adb forward tcp:32267 tcp:22267`，PC 浏览器用 127.0.0.1:32267；调试结束顺手 `adb forward --remove tcp:32267`。排查"显示内容不对"先 `adb forward --list` + `netstat -ano | grep <port>` 看端口在谁手里（adb.exe=转发，python=本地服务）。

## [2026-09-17] 进图伏击战 × map_init 死等：未 3 星活动图手动模式必崩 MapDetectionError（上游缺陷）

- **现象**：活动图（event_20260908_cn D3）每进一张新图必崩一次——loading 9% 时 `WARNING | Entered map with is_combat_loading appeared`，随后 `Image to detect is not in_map` 刷 11~28 条，~18s 后 `MapDetectionError` 穿透顶层 `Saving error`；调度器显示"运行中"但在报错循环，连崩后进程退出、wrapper 重拉时 `CRITICAL | Game page unknown`。
- **根本原因**：**图机制 × 上游缺陷 × 用户图状态三重叠加**。①D3 进图必发潜艇伏击战（spawn_data battle 0 双 siren + `MOVABLE_ENEMY_TURN=(2,)`，伏击在 loading 未完即排队）；②上游 `campaign_base.run()` 在 enter_map 的 is_combat_loading 出口后**直接 map_init，无伏击战处理**，而 map_init 容错仅 ~18s（`error_confirm=Timer(5,count=10)`）< 伏击战时长 40-90s；③用户图**未 3 星**（日志 `Map_info 99%, star_1, star_2, 100_percent_clear`，无 star_3/clear_mode → MAP_PREPARATION `No auto search option.`）→ 只能手动模式 → 必踩伏击窗口。有自律寻敌（3 星图）时进图直接索敌不触发伏击遭遇战，故 3 星图不崩。上游 issue #5969/#5970（2026-09-11，同活动 B3，**桌面雷电模拟器同款**——可排除 proot 环境）；修复 commit 46fe341 只加 AUTO_SEARCH_TITLE2（自律开关 JP 模板），**不覆盖手动模式进图伏击**。
- **解决方案**：环境层无解（红线：不改 AzurPilot）。用户侧：换已 3 星的图挂（B3/C3 等）或先手动把图打到 3 星再挂。AzurPilot 层可缓解（未做，候选）：检测 `MapDetectionError+is_combat_loading` 崩溃签名 → 延长退避 + UI 明示"该图未 3 星，手动模式与进图伏击冲突"。治本需上游在 map_init 前等伏击战结束。**加重坑：崩溃重拉时 `Already in map, retreating` 会撤退重进，对"进图必发伏击"的图=再踩一次伏击，重拉≠安全，构成死循环**——看到 MapDetectionError + retreating 组合要想到这层。

## [2026-09-17] 验证修复前先核对设备实际装机版本：HEAD ≠ 装机版（env_fix 60s/300s 事故）

- **现象**：T2 修复验证后设备仍连环崩——app.log `env_fix exit=null`（61s 超时被杀），session.log runner 14-37s 启动即崩 respawn #1-#27，像 300s 超时没生效。
- **根本原因**：设备上跑的是 T2 验证时装的 0.1.1-alpha.1 (46)，其 `ENV_FIX_TIMEOUT_MS` 实为 60s——300s 版是后来改的源码，**从没装进机**。proot 下 pip 慢一个量级，60s 永远跑不完 → pip 中途被杀 → imageio 半装 → 后续 runner 全崩。
- **解决方案**：装机/重启后先 `dumpsys package <pkg> | grep versionCode` 或 app.log Startup 行确认设备版本与预期一致，再谈"修复没生效"的排查。排查 release 包日志时记住 FileLogTree 只落 W+——关键失败输出必须走 Timber.w（本轮 ProotHost.kt 已把 env_fix 失败输出改 W 级落盘）。另：adb forward 跨装机/重启会断，curl 空响应先重建 forward 再下结论。

## [2026-09-17] imageio 2.35+ 把 P 模式 GIF 解码成 RGB：AzurPilot 选关 OCR 全盘崩溃，根因是 rootfs 没按上游钉版

- **现象**：GemsFarming/活动图到选关步骤即崩——`cv2.error: OpenCV(4.x) ... (depth == CV_8U || depth == CV_32F) && type == _templ.type()`，崩点在 `campaign_ocr.py:266 cv2.matchTemplate`，任务退出挂机状态。桌面端同版本 AzurPilot 却正常。
- **根本原因**：rootfs 烘焙时 `build-rootfs.sh` 装的是**不钉版** imageio（当时 2.37.x），而 AzurPilot 上游 `requirements.txt` 钉死 `imageio==2.27.0`。imageio 2.35 起改了 GIF 解码：P 模式（调色板）GIF 首帧从 2D 调色板索引变成 RGB 3 通道。AzurPilot 的 `assets/cn/template/TEMPLATE_STAGE_CLEAR_20240725.gif` 等模板被读成 3 通道，与灰度截图 matchTemplate 时通道数不一致即断言。**桌面端正常是因为桌面部署走了上游钉版。教训：为 AzurPilot 筑环境，pip 依赖必须与上游 requirements.txt 逐条对齐钉版，"装最新"就是埋雷。**
- **解决方案**：①`build-rootfs.sh` 钉 `imageio==2.27.0`（新烘焙根治）；②存量设备靠 `seeds/env_fix.sh` 每次启动自检钉回（pip + aliyun 镜像，断网不阻塞）。验证法：PC 建两 venv（2.37 / 2.27）对照解码 `template.shape`（3 通道 vs (20,30) 2D）+ matchTemplate 实测——不必上机即可定性。另：同类问题优先查"环境与上游钉版差异"，不要先改 AzurPilot 代码（用户红线）。

## [2026-09-17] proot 里跑 pip 比原生慢一个量级：一次性执行的超时预算按 300s 起

- **现象**：`ProotHost` 调 env_fix.sh（内含 pip install），60s 超时稳定被杀，但同命令在 proot 交互 shell 里手动跑能完成。
- **根本原因**：proot 的 ptrace 拦截让 pip 这种 fork/syscall 密集型 workload 慢 5~10 倍；首次跑还要解依赖、下载 wheel。
- **解决方案**：proot 一次性执行含 pip/编译类任务时，超时常量单独定义（`ENV_FIX_TIMEOUT_MS=300_000L`），别复用普通 shell 的 30~60s 档位。脚本本身要幂等+永远 exit 0，超时杀掉下次启动重跑即可收敛。

## [2026-09-17] release 包排障双盲区：Timber 只落 W+ 且无 logcat plant；wrapper /logs 只服务 mtime 最新的一个 txt

- **现象**：想读 seeds 脚本（env_fix.sh）的 stdout 验证行为——app.log 没有（release 版 Timber FileLogTree 只落 WARN+，且无 logcat plant）；curl `/logs` 也拿不到（env_fix.txt 写完后 gui.txt 立即变 mtime 最新，/logs 只服务最新那一个文件）。
- **解决方案**：绕开 stdout 验证，改用**行为级证据**——imageio 钉版是否生效，直接跑 GemsFarming 看选关 OCR 是否还崩（2.37 下第一次模板匹配必崩，点中 d3 即证明 2.27 在岗）；文件还原是否发生，查 `git -C <azurpilot> diff --stat` 或文件 hash。要让某 txt 可被 /logs 读到：短启 runner 4 秒再 /stop 让 azurpilot.txt 变最新（只对 runner 系有效）。长期改进候选：wrapper /logs 支持 `?file=` 参数。

## [2026-09-17] AzurPilot 工具任务不都自己拉游戏：daemon 只盯屏，event_story 才自带 app_start

- **现象**：半自动点击（daemon）起来后对纯黑帧空转（每 0.3s 一条 WARNING 刷屏），游戏不被拉起；活动剧情（event_story）却能正常拉起游戏。
- **根本原因**：上游设计分工——`AzurLaneDaemon.run()` 直接 `while 1: screenshot()` 盯当前屏（桌面版官方用法=用户自己先开游戏）；`EventStory.run()` 内部有 `self.app_start()`（eventstory.py:214）；调度器路径另有 GameNotRunningError→`task_call('Restart')` 兜底。**三个入口三种拉游戏责任，给 `/tool/*` 白名单加新任务时必须逐个确认。**
- **解决方案**：runner.py 在 `task=='daemon'` 时先 `azurpilot.run('start')`（LoginHandler.app_start+handle_app_login）再进 daemon 循环；游戏已在跑时该调用无害（置前台+收登录弹窗）；start 失败则不裸进盯屏循环（exit 1 留墓碑）。

## [2026-09-17] M3 按钮最小高度是两层：关 Local 只撤外层，内层 `defaultMinSize(40dp)` 照常在

- **现象**：双模块行要把两按钮压到 ~31dp/个。外包 `CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides X)` 后：①`provides null` 直接编译失败（M3 1.4 该 local 是非空 `Dp`）；②改 `provides 0.dp` 后按钮仍实测 39.7dp 高，行高仍被右列 84dp 驱动，左卡短一截底边对不齐。
- **根本原因**：M3 `Button`/`OutlinedButton` 的最小高度分两层——外层 `minimumInteractiveComponentSize()` 读 `LocalMinimumInteractiveComponentSize`（默认 48dp，0.dp 即关闭）；**内层内容 Row 的 `defaultMinSize(minHeight = 40.dp)` 不受任何 CompositionLocal 管**，撤掉外层后它成为新的固有高下限。且 `minimumInteractiveComponentSize` 会吃掉调用方显式给的 `height()`（取 max），这就是早先 `height(32.dp)` 无效的原因。
- **解决方案**：外层关 Local 用 `provides 0.dp`（不是 null）；内层 40dp 若必须突破，只能绕开 M3 Button 自制轻量按钮（Surface+clickable）。本轮需求靠「左卡 `fillMaxHeight()` 拉伸至行高」对齐左右上下缘即可，不必突破 40dp。另：弹层宽度适配——`DropdownMenu` 默认按内容包宽，锚按钮 `onGloballyPositioned` 量宽后 `Modifier.width(px.toDp())` 显式喂给弹层（勿赌 `ExposedDropdownMenuBox.menuAnchor` 的点击行为版本差异）。

## [2026-09-16] 待机屏灭的连锁反应：无线调试掉线 + VD 无帧 + nativeLibraryDir 的正确拿法

- **现象**：手机待机（物理屏 OFF）期间——①adb 5555 拒连（无线调试休眠掉线），唤醒后 `adb connect` 即回；②桥 screencap 返回 `no frame available`，`dumpsys display` 里 VD 不在列——但 app/proot/WebUI/桥全部健在；③`run-as` 里 glob `/data/app/~~*/<pkg>-*/lib/arm64` 展开不了（目录不可遍历），proot 一次性执行拿不到 nativeLibraryDir。
- **根本原因**：①②同根因=休眠。VD 由 readiness 链按需建（前台+shizuku 就绪才 bind 建屏），待机无前台则无 VD——**不是故障**，唤醒后自动恢复。③/data/app 子目录对 app uid 不可列举（但 app 进程自己知道路径）。
- **解决方案**：体检脚本 screencap 项区分——`no frame available` + 物理屏 `state OFF` = WARN（待机，唤醒复检），其余才 FAIL。nativeLibraryDir 从运行中进程的 cmdline 拿：`tr '\0' ' ' < /proc/<prootPid>/cmdline`（argv[0] 即 libproot.so 绝对路径，proot pid 用 `ps -A | grep libproot.so`）。

## [2026-09-16] adb 操作三坑：引号两层剥离 / 多设备必须 `-s` / run-as 碰不了 /sdcard 外部私有目录

- **现象**：①`adb shell run-as <pkg> sh -c 'ls files/; ...'` 实际只裸跑了 `ls`（列的是 run-as 家目录），后面命令散落成独立 adb 命令在 `/` 下跑；②压测中途 `adb shell` 突然报 `more than one device/emulator`——设备列表多出一台 `127.0.0.1:16385`（SM_S9080，非本链设备）；③`run-as <pkg> ls /sdcard/Android/data/<pkg>/files/` Permission denied——哪怕那是该 app 自己的外部私有目录。
- **根本原因**：①Git Bash 先剥一层引号，adb shell 把剩余参数空格拼接——远端 `sh -c` 只收到第一个词；②adb 默认 ambiguous 即拒，任何时刻都可能有第二台设备上线；③run-as 上下文缺外部存储 app-op，scoped storage 下绝对路径 /sdcard 访问被挡（与 SELinux socket 限制是两码事）。
- **解决方案**：①整条远端命令再套一层双引号：`adb shell "run-as <pkg> sh -c '...'"`；②**一律** `adb -s 192.168.50.190:5555 ...` 显式指定本链设备（HONOR PPG-AN00）；③读 app 外部私有目录用 shell 域直接 `adb shell ls /sdcard/Android/data/<pkg>/files/`（shell 有 ext_data_rw 组），别走 run-as。

## [2026-09-16] FileLogTree 只记 WARN+：热更新 UNCHANGED 在 app.log 无行，别误判"没跑"

- **现象**：真机验证热更新恢复路径，`app.log` 里只有 FAILED（W 级）行，UNCHANGED 会话一条 AzurPilotUpdater 记录都没有，一度怀疑启动链没走到更新步。
- **根本原因**：`FileLogTree`（`log/LogTrees.kt`）过滤级别 WARN+，Timber.i/d 只进 logcat（tag=类名，如 `AzurPilotUpdater`）；且 HONOR 系统日志极吵，logcat 缓冲几分钟就被冲掉。
- **解决方案**：判 INFO 级事件的旁证——热更新看 `.azurpilot_commit` 的 mtime（UNCHANGED 也会重写）与 `.git/FETCH_HEAD` 是否变化；会话级判定看 ps 进程树。长期可考虑给 FileLogTree 开 INFO（评估噪音后定）。

## [2026-09-16] 真机测桥回环延迟：toybox `nc` 可用，stat 轮询地板 ~28ms

- **现象**：要测桥 screencap 真机耗时，run-as 禁 socket（见 2026-09-15 条目）；`/system/bin/sh`（mksh）无 `/dev/tcp`；响应帧 2.76MB 且连接是长连协议，nc 不知道什么时候算"收完"。
- **解决方案**：shell 域 `/system/bin/nc` 直打 127.0.0.1:22300（shell 域 AF_INET 通）；后台 `(printf 请求; sleep 4) | nc > 文件`，前台 `stat -c %s` 轮询文件长到 期望值（header 行长 + `"length"` 字段，校准轮先跑一次拿）即记录 `date +%s%N` 差值，kill 掉 nc 进入下一轮。**坑**：ping 响应只有 ~48B，期望值按 200B 等会每轮吃满 10s 超时保护；轮询每圈派生 stat ≈28ms 是测量地板，真值比测得值更小。脚本与结果见 devlog 2026-09-16 阶段五-4 条目（screencap p50=30ms，对照 AzurPilot >1s 不可用线富余 33 倍）。

## [2026-09-16] wrapper /status 时间戳是 guest 本地时（proot 无 TZ=UTC）——比设备 CST 慢 8h，别误判"旧会话复活"

- **现象**：重装包装机后 1 分钟 curl `/status`，`gui_started_at=2026-09-15T20:08:59`（昨天！），第一反应"旧 proot 会话逃过 install -r 的杀进程，划卡归零结论要翻案"。
- **根本原因**：proot 会话环境只设 `LANG=C.UTF-8` 无 `TZ`，guest 内 `datetime.fromtimestamp()` 按 UTC 格式化；设备是 CST(UTC+8)，所以"昨天 20:08"其实就是"刚才 04:08 CST"。AzurPilot 日志行时间戳同理全慢 8h。
- **解决方案**：读 guest 侧时间先换算 UTC；判会话新旧看 **ps 进程树**（proot 的父 pid = 当前 app pid 即新会话），别看绝对时间。生产无副作用（内部逻辑全部用单调时钟/时间戳差值），纯排障心智陷阱。

## [2026-09-15] run-as（runas_app 域）禁止 socket——真机 harness 要用 shell 域，不是 run-as

- **现象**：`run-as com.azurpilot.spikea` 起的 shell 里，proot 客户机进程 `socket()` 直接 `PermissionError [Errno 1]`（TCP/UDP/UNIX 全灭）；但 `id` 明明显示带 `3003(inet)` 组。
- **根本原因**：run-as 切的是 `runas_app` SELinux 域（调试域），socket 类被策略整体拒绝——gid 有 inet 也没用，LSM 检查在 capability 检查之后。对比：`shell` 域 AF_INET/abstract AF_UNIX 通、路径式 AF_UNIX 拒（shell_data_file 上建 socket 文件）；`untrusted_app`（App 自身进程树，zygote 孵化）全通——m0 Termux 与 Spike A 走的就是这条。
- **解决方案**：要网络的真机 harness 用 **shell 域**（`/data/local/tmp` 放 proot 可执行 + rootfs，shell 可执行该区域文件）；不要在 run-as 里跑任何带 socket 的东西。另外两个配套坑：run-as 下 mksh heredoc 会在 /data/local 建临时文件失败（用 `python3 -c` 替代）；proot 客户机内要显式 `export PATH=/usr/local/sbin:...:/bin`（继承的 Android PATH 无 /usr/bin）。

## [2026-09-15] busybox tar 解 ubuntu-base 必炸：硬链接前向引用 + app uid 不能 mknod

- **现象**：`busybox tar -x` 解 GHA 产的 `rootfs.tar.xz`（ubuntu-base 24.04.5 为底），报 `tar: can't create hardlink './usr/bin/uncompress' to './usr/bin/gunzip'` 中止。
- **根本原因**：ubuntu 系 tar 包里硬链接条目存在**前向引用**（`uncompress` 条目排在目标 `gunzip` 之前）；GNU tar 解包会把硬链接推迟到末尾处理，busybox tar 顺序立即 `link()` → ENOENT 即死。另有两个连环坑：① 解包目标目录必须是空场——Spike A 遗留合成 rootfs 的实体 `bin/` 目录会让 usrmerge 的 `./bin → usr/bin` 软链覆盖失败（`can't remove old file ./bin: Is a directory`）；② app uid 无 CAP_MKNOD，包内设备节点（若有）也建不了。
- **解决方案**：PC 侧 Python 预重打包（`.tmp/repack-linkfree.py`）：流式过一遍，普通文件内容落 spool，发牌时 HARDLINK 物化为目标内容副本、符号链接/目录/权限位原样保留、设备与 fifo 节点丢弃（proot `-b /dev:/dev` 提供真 /dev）。产物不压缩（~1GB），WiFi adb push ~1 分钟，设备端纯 `busybox tar -xf` 即可。

## [2026-09-15] `libbusybox.so` 直接调用报 "applet not found"——多合一二进制认 basename(argv[0])

- **现象**：`run-as com.azurpilot.spikea` 里直接执行 `$NLD/libbusybox.so tar …` → `libbusybox.so: applet not found`，连 `--list`/`--help` 都一样。
- **根本原因**：busybox 多合一二进制按 `basename(argv[0])` 查 applet 表；`libbusybox.so` 不在表里（只有裸名 `busybox` 才走"$1 当 applet 名"的分派分支）。Android jniLibs 强制 `lib*.so` 命名，故直接调永远踩这个。
- **解决方案**：在可写目录建软链 `ln -sf $NLD/libbusybox.so files/bin/busybox`，经**裸名软链**调用（`./bin/busybox xzcat … | ./bin/busybox tar -x …`）。设备端解 xz 包、跑 proot harness 都靠这一手。

## [2026-09-15] scrcpy-server 默认 `cleanup=true` 会删掉刚 push 上去的 jar

- **现象**：push `scrcpy-server-v4.1.jar` 后第一次 `app_process … com.genymobile.scrcpy.Server 4.1` 正常运行（打印 device/New display 后因无客户端退出）；**紧接着第二次运行整套 `Aborted`**，logcat 墓志铭：
  `Abort message: 'No pending exception expected: java.lang.ClassNotFoundException: com.genymobile.scrcpy.Server'`（在 `AndroidRuntime::startReg` 阶段就炸，看起来像设备坏了）；同一时刻 `ls -l /data/local/tmp/scrcpy-server-v4.1.jar` → `No such file or directory`。
- **根本原因**：`cleanup` 默认 `true` 时 server 会派生一个 `CleanUp` 辅助进程（`app_process … com.genymobile.scrcpy.CleanUp …`）来在退出时恢复 `show_touches`/亮度等设置；该辅助进程 `main()` 的**第一步**就是 `unlinkSelf()` → `new File(Server.SERVER_PATH).delete()`，把 server jar 自己删掉（scrcpy 客户端每次都重新 push，所以上游无感）。
- **解决方案**：起 server 时显式 `cleanup=false`；或每次运行前重新 push（校验 sha256）。做常驻实验必须用前者。

## [2026-09-15] scrcpy-server 必须"有人连"才活着——客户端一断，虚拟屏一起走

- **现象**：`new_display` 建好的虚拟屏，在约 2 分钟后凭空消失（`cmd display get-displays -i` 回到只有 `0`），server 侧 shell 打印 `Terminated`。
- **根本原因**：`tunnel_forward=true` 时 server 在抽象 socket（缺省 `scrcpy`）上 `accept()` 等客户端，随后整个生命周期都往这条 socket 写视频流；socket 断开（PC 侧客户端退出 / **adb 传输掉线导致 `adb shell` 会话被杀**）→ server 抛异常退出 → VD 随创建者进程消亡。
- **解决方案**：① 实验前先备一个"只 connect + recv 落盘"的保活客户端（本仓 `spike/e-adb-virtual-display/tools/hold_client.py`），并把 PC 侧任务超时设足（后台任务默认 600s 到点会杀 `adb shell`，等于自杀）；② 生产化必须做"adb 会话 → server → VD"的整链自愈/重建监督。

## [2026-09-15] `screencap -d` 与 `input -d` 的 display id 是**两个命名空间**

- **现象**：`screencap -p -d 9 /data/local/tmp/vd.png` → `Failed to take take screenshot. Display Id '9' is not valid.`（rc=1），而 `dumpsys display` 里明明有 `displayId 9`；反过来 `input -d 11529215049802775650 tap …` → `IllegalArgumentException: Error: Invalid arguments for display ID.`（rc=255）。
- **根本原因**：Android 14+ 起两个工具吃不同 ID：`screencap -d` 吃 **`dumpsys SurfaceFlinger --display-id` 列出的 int64 physical/SF id**（其 help 里 "see dumpsys SurfaceFlinger --display-id for valid display IDs" 就是线索；不给 `-d` 时的默认值 `4630947145909893267` 也是 physical id）；`input -d` 吃 **logical display id**（`InputShellCommand.getDisplayId()` 按 int 解析，装不下 int64 physical id）。
- **解决方案**：诊断脚本里两个 ID 都取：logical 从 `dumpsys display`（`DisplayInfo{"<name>", displayId N`）或 server 日志的 `New display: … (id=N)`；physical 从 `dumpsys SurfaceFlinger --display-id`（按 displayName/uniqueId 匹配）。`screencap` 用 physical、`input` 用 logical。

## [2026-09-15] 虚拟屏"变黑"= display group 电源请求 OFF（ColorFade 盖屏），shell 域点不亮

- **现象**：`screencap -d <VD physical id>` 成功但整帧纯黑（1280×720 PNG 恒 5336 字节）；`dumpsys window` 里该屏所有窗口 `mHasSurface=false isOnScreen=false`、任务 `state=STOPPED / isSleeping=true`。
- **根本原因**：VD 带 `FLAG_OWN_DISPLAY_GROUP`（自成一 display group，dumpsys 里 `displayGroupId=1`），其电源状态由 `DisplayManagerService.requestPowerState(groupId, …)` 决定，而请求方是 **WindowManager**。一旦该 group 的请求是 OFF：DPC `mScreenState=OFF` → 屏幕被 **ColorFade 层**覆盖（`dumpsys SurfaceFlinger` 里该屏合成只剩 `1 Layers / Output Layer (ColorFade#…)`）→ 任何截屏路径（含 scrcpy 编码器）都拿黑帧；WM 也不再给窗口 surface，注入没有可投递的焦点窗口。
- **解决方案（诊断 + 规避）**：shell 域**没有**等价入口——`cmd display power-reset <id>` 无效（`STATE_UNKNOWN` 解析到"上次状态"）、`DisplayManagerGlobal.requestDisplayPower(id, STATE_ON)` 只让 SF `powerMode=On` + 恢复窗口 surface，**ColorFade 仍盖黑**、`am start --display <id>` 也不点亮。规避只有两条：① 在设备处于交互态（`mWakefulness=Awake`，真屏亮）时建 VD（实测创建即 On，立刻可截真实画面）；② 熄灭后**重建** VD（本次两次运行均在 1~2 分钟后被 framework 翻成 OFF）。诊断口径：`dumpsys SurfaceFlinger | grep -A2 'Virtual Display <physicalId>'` 看 `powerMode`；`dumpsys display` 里该 displayId 的 DPC `mPowerRequest=policy=…`。

## [2026-09-15] HONOR ROM 的显示栈缺 scrcpy 依赖的两个隐藏方法

- **现象**：`DisplayManagerGlobal.requestDisplayPower(int, boolean)` → `NoSuchMethodException`（只有 `requestDisplayPower(int, int)`）；`SurfaceControl.getPhysicalDisplayIds()` → `NoSuchMethodException`（只有 `getPhysicalDisplayToken(long)`）。
- **根本原因**：OEM（HONOR/MagicOS）改过 `framework` 的隐藏 API 形状——scrcpy 的 `Device.setDisplayPower()` 在 Android 15+ 走 `requestDisplayPower(int, boolean)`，在其 `Honor` workaround 分支里又用 `getPhysicalDisplayIds()`，两条在本机都会静默失败/降级。
- **解决方案**：写反射探针先**枚举**再调用（本仓 `spike/e-adb-virtual-display/tools/VDLab.java` 的 `methods` 模式，`app_process` 跑在 shell 域可直接读隐藏 API）；对外报告里区分"API 存在但被 OEM 改形"与"权限不足"。

## [2026-09-15] jniLibs 里非 `lib*.so` 命名的文件被双重丢弃

- **现象**：`jniLibs/arm64-v8a/libtalloc.so.2`、`libbusybox.so.1.38.0` 放进工程后，构建成功，但 APK 的 `lib/` 里查无此文件；`nativeLibraryDir` 自然也没有。
- **根本原因**：两层过滤。① AGP 打包阶段丢弃不匹配 `lib*.so` 的名字（静默）；② 即使进了 APK，AOSP 安装器（`ApkParsing.cpp::ValidLibraryPathLastSlash`）对 **非 debuggable** 应用也只解包 `lib` 前缀 + `.so` 后缀的文件（debuggable 应用是例外，所以 debug 构建容易"看起来正常"）。
- **解决方案**：把 DT_NEEDED 字符串原地改写成短名并同步改文件名，例：
  - `libproot.so`：`libtalloc.so.2` → `libtalloc.so`
  - `libbusybox.so`（stub）：`libbusybox.so.1.38.0` → `libbusybox_app.so`
  工具：`spike/a-proot-exec/tools/patch-dynstr.py <elf> <old> <new>`（dynstr 原地缩短 + NUL 填充，其他偏移不受影响），改完 `llvm-readelf -d` 复核。新命名不得长于原名。
  另注意：AGP 新默认 `extractNativeLibs=false`，必须显式 `packaging { jniLibs { useLegacyPackaging = true } }` 才会解包到 nativeLibraryDir。

## [2026-09-15] Termux busybox 是 stub + 载荷，applet 由 argv[0] 决定

- **现象**：exec 改名后的 `libbusybox.so uname -m` → `libbusybox.so: applet not found`，退出码 127（看起来像 exec/动态库失败）。
- **根本原因**：Termux 1.38 的 `bin/busybox` 只有 4320 字节，是启动器 stub；applet 代码在 `lib/libbusybox.so.1.38.0`（876KB 共享对象）。stub 用 `argv[0]` 基名选 applet：名为 `libbusybox.so` 时它把整个名字当 applet 名 → not found。
- **解决方案**：从 nativeLibraryDir 执行时用 argv0 覆写 shim：`libspike_shim.so <argv0> <path> [args...]`（源码：静态 C 版 execve 包装，见 `spike/a-proot-exec/REPORT.md` §4.2）；或 symlink 命名（但 symlink 需落在可 exec 的目录，35 上私有目录不可行，nld 只读）。

## [2026-09-15] `proot -r` 报 `execve(...): Permission denied` 的真凶是 rootfs 内解释器缺 +x

- **现象**：`proot -r <filesDir>/rootfs /bin/busybox ...` → `proot error: execve("/bin/busybox"): Permission denied`，与 targetSdk/SELinux 拦截症状一模一样。
- **根本原因**：guest ELF 的 `PT_INTERP=/system/bin/linker64` 在 rootfs 中被解析为我们的副本 `rootfs/system/bin/linker64`；该副本是用"复制文件"方式从 assets/宿主拷来的，未 `chmod +x`（Kotlin `File.copyTo` 不保留权限）→ 内核/loader 对解释器报 EACCES。
- **解决方案**：rootfs 内**所有**需执行文件（解释器、可执行、脚本）显式 `setExecutable(true)`；探测时先 `ls -l` 打印模式（本 spike 的 `P0-rootfs-modes` 探针）。

## [2026-09-15] proot rootfs 客户机动态库闭包不全 → `CANNOT LINK EXECUTABLE ... libc++.so not found`

- **现象**：修好解释器权限后，busybox 在 rootfs 里继续报 `library "libc++.so" not found: needed by /system/lib64/liblog.so in namespace (default)`。
- **根本原因**：合成迷你 rootfs 只借放了 `liblog.so`，漏了它的依赖 `libc++.so`（逐个症状式补库会反复踩）。
- **解决方案**：用 `llvm-readelf -d` 递归求 `DT_NEEDED` 传递闭包再整体拷贝（本 spike 闭包：`libc/libm/libdl/liblog/libc++ + ld-android.so + linker64`）。真实 Ubuntu rootfs 自带 glibc 不需要这套借用，但同样要保证完整性。

## [2026-09-15] targetSdk 35 下 app 不能直接 exec 私有目录文件（error=13）——预期行为

- **现象**：`ProcessBuilder` 执行 `<filesDir>/rootfs/usr/bin/xxx` → `IOException: ... error=13, Permission denied`；同一用户/同一文件在 targetSdk 28 构建下可执行；文件 `canExecute=true`、模式 `-rwx--x--x` 也救不了。
- **根本原因**：Android 10+ 对 targetSdk ≥ 29 的应用禁止执行 app data 目录中的文件（SELinux 域差异：28 → `untrusted_app_27` 有 execute 权限，35 → `untrusted_app` 无）。
- **解决方案**：不要直接 exec 私有目录文件。可执行体放 jniLibs（nativeLibraryDir，命名 `lib*.so`）执行；rootfs 客户机由 proot 走 loader/解释器映射路径运行（本 spike 实测 proot 在 35 上完整可用，含客户机内嵌套 execve）。私有目录仅放数据。28 兜底仅用于无法改造的老场景。

## [2026-09-15] `MSYS_NO_PATHCONV=1` 下 `adb install` 的主机路径必须转 Windows 形式

- **现象**：`MSYS_NO_PATHCONV=1` 后 `adb install -r /d/VSCodeCache/.../x.apk` → `adb.exe: failed to stat ...: No such file or directory`。
- **根本原因**：该环境变量（为 `adb shell` 里 `/data/...` 等设备路径不被 MSYS 改写而必需）同时关掉了 adb.exe 参数路径自动转换；Windows 侧 adb 只认 `D:/...`。
- **解决方案**：脚本里对主机侧路径显式 `cygpath -m "$apk"` 后再传给 adb（`run-device-ladder.sh` 已内置）。

## [2026-09-15] local.properties 指向不存在的 SDK：AGP 仅告警并静默回退 ANDROID_HOME

- **现象**：`local.properties` 里 `sdk.dir` 写错（不存在的目录）时，构建仍成功，只在输出里带一行 `WARNING: ... Directory does not exist`。
- **根本原因**：AGP 找不到 `sdk.dir` 时会回退环境变量 `ANDROID_HOME`（本机指向系统 SDK），于是实际用的是另一套 SDK，排查构建差异时极易误判。
- **解决方案**：构建日志里出现该 WARNING 必须当错误处理；`local.properties` 用 `sdk.dir=D:/VSCodeCache/shizku-m/build-env/android-sdk`（便携工具链，已由 shizuku-m 验证 AGP 8.10 + compileSdk 36 可用）。


## [2026-09-15] 迷你 rootfs 缺 /dev/null：busybox 后台作业全部 `can't open /dev/null` 静默死光

- **现象**：`proot -r rootfs /bin/busybox sh -c '<脚本>'`，脚本里用 `cmd &` 派生后台进程时子进程立刻全死，guest stderr 每行一条 `sh: line 0: can't open /dev/null: no such file`；同一脚本里的前台命令、`$(...)` 替换全部正常——极易误判成"`$!` 拿到的 pid 不对"或"proot 不支多进程"。
- **根本原因**：POSIX 语义：作业控制关闭时异步列表（`&`）的标准输入被指派为 `/dev/null`；busybox ash 在 fork 后以 `O_RDONLY` 打开 `/dev/null`（**不带 `O_CREAT`**），而我们的合成 rootfs 的 `dev/` 是空目录 → ENOENT → 子进程带着这个 errno 退出。
- **二次陷阱**：脚本里任何一次 `2>/dev/null`（ash 的 `file` 重定向是 `O_WRONLY|O_CREAT`）都会在 rootfs 里**创建一个普通文件 `dev/null`**，于是"第二次跑就正常了"。表现为偶发玄学，实际是上一轮把自己的坑填了。
- **解决方案**：proot 启动参数加 `-b /dev:/dev`（最小可用为 `-b /dev/null`），把宿主 /dev 绑进 guest；实测 `-b /dev:/dev` 后同一脚本 8/8、48/48 全存活。生产 rootfs 同理：不能拿普通文件冒充 `/dev/null`（写入不会丢弃，见 Spike C 里那个 3KB 的"null"）。

## [2026-09-15] 设备侧 toybox grep 不认 `\|`（BRE 交替），静默零命中

- **现象**：`run-as <pkg> sh -c 'grep "A\|B" file'` 在真机上没有任何输出也不报错；同一条命令在 PC 的 GNU grep 下正常。
- **根本原因**：`\|` 是 GNU 的 BRE 扩展，Android toybox grep 不支持，模式被当字面量 `A|B` 匹配。
- **解决方案**：设备侧 grep 一律写 `grep -E 'A|B'`（或 `grep -e A -e B`）。

## [2026-09-15] 数「幻影进程」的正确姿势：直读 app cgroup 的 cgroup.procs

- **现象/需求**：需要精确知道 PhantomProcessKiller 眼里的"app 子进程数"：`dumpsys activity processes | grep PhantomProcessRecord` 要等 AMS 周期刷新（CPU tracker 节拍）才准；`ps -A -o USER | grep -c <user>` 会把 adb `run-as` 自己拉起的同 uid 进程算进去，偏高。
- **机制**：`PhantomProcessList.lookForPhantomProcessesLocked()` 读的就是 AOSP `nativeGetCgroupProcsPath(uid, pid)` 指的文件；本机（cgroup v2）布局为 `/sys/fs/cgroup/apps/uid_<uid>/pid_<appPid>/cgroup.procs`，shell 可读，内容 = app 主进程 + cgroup 内全部后代。
- **解决方案**：采样用 `wc -l < /sys/fs/cgroup/apps/uid_<uid>/pid_<appPid>/cgroup.procs` 再减 1（app 自身），与 `ps` 计数互相校验；两者一致即说明读数可靠。

## [2026-09-15] 被 SIGKILL 掉 tracer 的 proot 留下 PPID=1 孤儿，`am force-stop` 收不掉

- **现象**：proot 被幻影杀手 kill 后，guest 的 `sleep`/`sh` 变孤儿（PPID=1），再执行 `am force-stop <pkg>` 也不消失，污染后续实验的进程计数。
- **根本原因**：tracer 死后子进程被 reparent 到 init；实测这些孤儿与 app 主进程不在同一 memory cgroup（`memory:/` vs `memory:/apps/<pkg>`），而 `am force-stop` 的 cgroup 清理只覆盖 app 自己的组，AMS 也没把它们记在 app 名下。
- **解决方案**：同 uid 才能动它们——`adb shell run-as <pkg> sh -c 'M=$$; for p in $(ps -A -o PID,USER | awk "\$2==\"u0_a301\"{print \$1}"); do [ "$p" != "$M" ] && kill -9 $p; done'`。跑任何以"进程计数"为判据的实验前，先跑一遍清理并确认计数归零。

## [2026-09-15] 真机上"app 自己 fork 的进程"才算幻影：Shizuku/shell 域拉起的树不在 app cgroup

- **现象**：用 `run-as`（uid 变成 app 但 SELinux 域仍为 shell）拉起的 proot 树，`am force-stop <pkg>` 杀不掉，`ps` 里也归属 app uid。
- **机制**：AMS 的幻影进程判定 = 读 **app 主进程的 cgroup**（`/sys/fs/cgroup/apps/uid_<uid>/pid_<appPid>/cgroup.procs`）；进程进了哪个 cgroup 取决于 fork 者，而不是 uid。
- **含义**：v3 架构里由 App（untrusted_app）自己 fork 的 proot 整树**必然**计入幻影配额（这正是 Spike C 要缓解的场景）；若将来改由 Shizuku/shell uid 侧拉起，则压根不进 app cgroup，配额问题消失——但 adb 侧无法触达虚拟屏（见 roadmap-v3 决策），该路线不在主线上。

## [2026-09-15] 幻影收割挂在 AMS 的 5 分钟节拍上——存活实验窗口小于一个节拍就会误判

- **现象**：同一份默认配置的对照实验，三轮的"被杀时刻"分别是 spawn 后 **+29s / +43s / +267s**（第三轮一度被当成"没被收割"，因为前 4 分钟一直全存活）。
- **根本原因**：裁触发点不是"超编即杀"，而是 `ActivityManagerService` 的 `CHECK_EXCESSIVE_POWER_USE_MSG`（`POWER_CHECK_INTERVAL` 默认 **5 分钟**）→ `checkExcessivePowerUsage()` → `AppProfiler.updateCpuStatsNow()` → `PhantomProcessList.updateProcessCpuStatesLocked()`（只有此时才扫 app cgroup、建幻影记录并 `scheduleTrimPhantomProcessesLocked()`）。另外 `AppProfiler.updateCpuStatsNow()` 里 phantom 监控 flag 为假时**整个扫描都不做**（不只是跳过 kill）——所以关掉 flag 后 `dumpsys activity processes | grep PhantomProcessRecord` 会恒为 0。
- **解决方案**：任何"子进程是否被收割"的 A/B 观察窗 **≥1 个节拍（≥420s）**；判定"没被杀"必须覆盖至少一次节拍。计数用 `dumpsys activity settings | grep max_phantom_processes` 读生效阈值，用 cgroup.procs 读当前幻影集合。

## [2026-09-15] 别把 `ps -A | grep <user> | wc -l` 当成"幻影进程数"的权威口径

- **现象**：采样里 `ps` 计数偶尔比 cgroup 计数少几十（如 `ps=17` 而 cgroup=50），或把 adb `run-as` 自己拉起的同 uid 进程算进来。
- **根本原因**：`ps` 是快照式遍历 + 文本过滤，进程频繁生死时会漏/多；`run-as` 的 shell 自身也在 app uid 下。
- **解决方案**：以 `/sys/fs/cgroup/apps/uid_<uid>/pid_<appPid>/cgroup.procs` 行数 −1（App 自身）为权威值（与 AMS 同源）；`ps` 仅作交叉校验；App 侧另用 `kill(pid,0)` 做独立心跳复核。

## [2026-09-15] scrcpy `--new-display` 虚拟屏劫持主屏手势导航——`FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS` 是生死线

- **现象**：Spike E 实验（scrcpy-server `new_display=1280x720/160` 建虚拟屏）后，用户报告**主屏手势导航全部失效**（边缘侧滑/上滑无响应）；dumpsys 显示 `GestureNavAnim`、`GestureSildeOut`、`NavigationBar0` 三个 SystemUI 窗口的 `mDisplayId` 全部指向虚拟屏（9）而非主屏（0）（物证：`spike/e-adb-virtual-display/logs/e3c-power-reset.txt:19-32`）。
- **根本原因**：scrcpy `--new-display` 默认带 `FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS`（+`OWN_FOCUS`/`OWN_DISPLAY_GROUP`/`TRUSTED`）。AOSP 对该 flag 的原文："virtual displays without this flag shouldn't show home, navigation bar or wallpaper"——SystemUI 会为带此 flag 的可信虚拟屏**创建一套导航栏/手势窗口**；MagicOS 10 的手势输入路由跟着这些窗口走，主屏物理手势被投递到 1280×720 虚拟空间，表现为主屏手势"失灵"。对照：m0 的 `VirtualDisplayManager.kt:39` 显式 `VD_SYSTEM_DECORATIONS = false`（`:187-189` 分支不执行），代码级规避了此坑（注意：m0 项目此后长期暂停未用，"无事故"以代码证据为准，非连续运行实证）。
- **解决方案**：杀掉 VD 属主进程（scrcpy-server）→ 虚拟屏销毁 → 手势窗口自动回主屏（验证：`cmd display get-displays -i` 只剩 0、三手势窗口在列表、无 scrcpy 进程）。
- **防范（不能再有第二次）**：① 任何建虚拟屏的代码/实验**禁止** `SHOULD_SHOW_SYSTEM_DECORATIONS`（scrcpy 侧即使 `--no-vd-system-decorations` 也有被 ROM 忽略的公开记录 scrcpy#6684，不可依赖）；② 实验前后各查一次手势窗口归属（`dumpsys window windows | grep -E 'GestureNav|GestureSilde|NavigationBar'` 必须在 display 0）；③ VD 属主进程必须可一键杀死、实验结束必须清场到 `get-displays` 只剩 0。已写入 `AGENTS.md`「虚拟屏实验纪律」与 roadmap 阶段二「VD flag 硬约束」。

## [2026-09-15] `screencap -d` 与 `input -d` 是两个 ID 命名空间（m0 §41 翻案的关键）

- **现象**：m0 §41 记录"screencap -d 报 Display Id not valid、input -d 静默无效"；Spike E 实测两者都可工作，前提是 ID 用对命名空间。
- **根本原因**：`screencap -d` 吃 `dumpsys SurfaceFlinger --display-id` 的 int64 **physical id**（用 logical id 报 not valid）；`input -d` 吃 int32 **logical display id**（给 physical id 直接 IllegalArgumentException）。m0 当年用 logical id 调 screencap、在 VD 熄灭/无焦点窗口态调 input。
- **解决方案**：建 VD 后同时记录两个 ID；注入前确认 VD 处于"可见且有 resumed 焦点窗口"状态。证据：`spike/e-adb-virtual-display/REPORT.md` §4。

## [2026-09-15] 虚拟屏被 framework 熄灭后 shell 域点不亮（ColorFade 盖屏）

- **现象**：VD 创建 1~2 分钟后被 framework 翻成 OFF，截屏全黑、注入无效；`cmd display power-reset <id>` 无效，反射 `requestDisplayPower(id, ON)` 只恢复窗口 surface、ColorFade 层仍盖黑。
- **根本原因**：per-display-group 电源请求由 WindowManager 发起（AOSP `DisplayManagerService.java:5613`），shell 域无等价入口；DPC 置 OFF 后 SF 合成列表只剩 ColorFade 层。
- **解决方案**：m0 的做法是属主进程每 4s `PowerManager.userActivity(displayId)` 保活（`PowerController.kt`）——阶段二必须继承；纯 adb 通道对此无解（VD 只能重建）。另：HONOR 显示栈私货——`DisplayManagerGlobal.requestDisplayPower(int,boolean)` 与 `SurfaceControl.getPhysicalDisplayIds()` 均不存在，scrcpy 的 Android 15+ 电源路径在本机静默走空。

## [2026-09-15] scrcpy-server 两个生命周期陷阱：`cleanup=true` 自删 jar；无人连接即退出

- **现象**：默认 `cleanup=true` 时 server 派生 CleanUp 进程 `unlinkSelf()` 删掉刚 push 上去的 jar，第二次启动报 `ClassNotFoundException`（像设备坏了）；`tunnel_forward=true` 时 server 在 accept() 阻塞，客户端断开/从未连接 → server 退出、VD 即亡。
- **解决方案**：实验配方固定 `cleanup=false`；必须配"只 connect+recv"的保活客户端（`.tmp/spike-e/hold_client.py`）；生产上 VD 属主必须是 App 自己的常驻特权进程，不能悬在 adb 会话上。

## [2026-09-16] 构建期 dl.google.com TLS 握手中断（间歇）→ settings 加 Aliyun 镜像根治

- **现象**：Gradle 配置阶段解析 `com.android.tools.build:bundletool:1.18.3` 失败，`Remote host terminated the handshake`；curl 复测同一 URL 却 200——中间盒 RST 注入是概率性的，重跑可能恰好又过。
- **根本原因**：本机到 dl.google.com 链路被 SNI 干扰；暖缓存（拷自 shizku-m/build-env）只有 bundletool 1.18.0（m0 时代 AGP 依赖），帮不上 AGP 9.2.1。
- **解决方案**：`app/settings.gradle.kts` 的 `pluginManagement` 与 `dependencyResolutionManagement` 各加 `https://maven.aliyun.com/repository/google` 与 `.../central`（官方源、jitpack 留兜底）。google 块保持 content 过滤（com.android/com.google/androidx），镜像块同样过滤防误伤插件门户解析。

## [2026-09-17] AzurPilot 全量补丁冻结漂移：cp 整文件覆盖会把文件冻在补丁年代

- **现象**：挂机中 AzurPilot 重启游戏后登录流程炸 `TypeError: ModuleBase.image_color_button() got an unexpected keyword argument 'threshold'`，runner 死、任务断连。
- **根本原因**：补丁施加机制是 `cp -rf patches/module/. → /opt/azurpilot/module/`（build-rootfs.sh:175）**整文件覆盖**。`patches/module/base/base.py` 是 m0 时代全量拷贝（自有改动仅 early_ocr_import 的 AzurPilot 预热块 9 行），热更新把 AzurPilot 推到上游 master 后，base.py 被补丁冻回旧版（`color_threshold`），而同树 login.py 已是新版（`threshold`），API 撞车。排查锚点：设备 login.py 与上游 master 逐字节 diff 为空 → 设备跟踪 master → 补丁按 master 重打即收敛。
- **解决方案**：全量补丁**重打** = 上游 master 原样 + AzurPilot 块（脚本锚点替换，diff 应只剩自有改动）；直写设备活文件 + diff 校验。长期教训：① 补丁文件里的自有改动必须压缩到最小并 BEGIN/END 标记（本次正是靠标记确认只有 9 行）；② AzurPilot 热更新后任意 API 型 TypeError，先怀疑补丁漂移，diff 设备文件与上游 master 即现形；③ 理想终态是差分补丁（git apply）替代整文件覆盖。

## [2026-09-17] 补丁冻结第二案：args.json 整文件补丁把活动列表冻在补丁年代（幽影迷城不可见）

- **现象**：桌面版 AzurPilot 已是「幽影迷城」（event_20260908_cn），手机端 WebUI 活动下拉仍停在「沉溺于星光之城」（event_20260813_cn）；但设备 AzurPilot commit 与上游 master HEAD 逐字一致（92c07aa），`campaign/event_20260908_cn/` 地图资源、i18n 译名全部到位——只有活动**选项列表**旧。
- **根本原因**：与 base.py 案同源——`patches/module/config/argument/{args.json,argument.yaml}` 是整文件覆盖补丁，每次启动 AzurPilotOverlay 重放把 args.json 冻回补丁年代。args.json 是 WebUI 活动选项的唯一来源（`campaign/Readme.md` → config_updater.py 生成链 → args.json），上游 git 跟踪它、热更新本可带新，补丁重放又打回。全量 diff（补丁版 vs 上游 92c07aa 版）：16 项差异里 AzurPilot 真定制只有 2 行（ScreenshotMethod/ControlMethod 各追加 `azurpilot` 选项），其余全是冻结漂移。
- **解决方案**：**生成产物不补丁化，现场再生**——① 删双源 args.json/argument.yaml 补丁（共 4 文件）；② 新增 `seeds/regen_args.py`：跑 AzurPilot 完整生成链（活动列表随 `campaign/Readme.md` 走），再后处理补 `azurpilot` 选项与 zh-CN 显示名（全幂等）；③ ProotHost 启动链热更新后无条件跑（失败降级警告不阻塞）。**坑中坑**：生成器必须用 `python -m module.config.config_updater` 模块方式跑——直传脚本路径时 `sys.path[0]=module/config/`，`from deploy.utils import` 直接 ModuleNotFoundError；且 ProotHost 对 runGuest 失败只 Timber.w（logcat 被 HONOR 噪音分钟级冲掉），首装静默失败一轮，靠「args.json mtime 停在装机前 + 内容与补丁版逐字节等大」才现形。验证锚点：args.json mtime 刷新 + `Event.Campaign.Event.option` 含 `event_20260908_cn` + `azurpilot` 选项仍在。

## [2026-09-17] WebUI「闲置」状态环转圈 + 神秘方框——pywebio `.style()` 打在 wrapper 上，fill 定制全程没碰到 spinner

- **现象**：手机端 WebUI 任务状态「AzurPilot □ ◎ 闲置」：圆环不停旋转（像一直在加载）+ 环旁一个粗边空心方框（静止）；桌面 gooey 客户端同状态是静止圆。首版修复（往 `azurpilot.css` fill 规则补 `animation:none`）真机两帧对比翻车——缺口弧帧间移动，环照转。
- **根本原因**：CDP（`webview_devtools_remote_<pid>`，DEBUG 包已开 `setWebContentsDebuggingEnabled`）直查活 DOM 计算样式一锤定音：`--loading-border-fill--` 标记元素**真实存在**且 `markerIsSpinner=false / markerIsSpinnerParent=true`——pywebio 的 `.style()` 由 webiojs `getWidgetElement` 以 `attr({style: n+";"+style})` 写在 **put_html 外包装 div**（spinner 父级）上；`setAttribute` 原样保留字符串，属性选择器命中的是 wrapper。于是 `azurpilot.css` 的 fill 规则（1.5rem + 四边 `.2em solid currentColor`，**无圆角**）把 wrapper 画成静态方框（= 神秘方框本体，`borderTopColor=rgb(33,37,41)`+`borderRadius=0`），而内层真正的 `.spinner-border.text-secondary` 完全没被定制，保持 Bootstrap 默认：`animName=spinner-border` 0.75s 旋转 + `borderRight=transparent` 缺口。首版 `animation:none` 补在 fill 规则里 = 打在 wrapper 上，对 spinner 天然无效——**推断修复必须验证，且要验证到真正的目标元素上**。
- **解决方案**：用户约束「不动 AzurPilot 代码」（补丁会被热更新 `git reset --hard` 冲掉）→ WebView 层注入（`AzurPilotScreen.kt` `IDLE_SPINNER_FIX_JS`，`onPageFinished` 幂等注入 `<style>`，`!important` 碾压不在意加载序）：① `.spinner-border.text-secondary{animation:none !important;border-right-color:currentColor !important}`——按**类名**直打真 spinner，停转+补缺成完整圆；仅 secondary 命中（闲置/UpToDate/RemoteNotRunning 三个 fill 态），Running(success)/Warning 等动态态照常旋转。② `div[style*="--loading-border-fill--"]{border:none !important;width:auto !important;height:auto !important}`——剥掉 wrapper 方框 artifact。设备 azurpilot.css 还原上游 pristine（exec-out 拉回 diff=空），仓内双源补丁 git rm。**方法资产**：① 验证 CSS 修复用 CDP 读**计算样式**（`animName/borderRightColor/borderTopStyle`），比截图判读快且不留辩经空间——注意 `border-style:none` 时 `border-*-color` 仍算出 currentColor，判「画没画」要看 `borderTopStyle`；② 两帧对比（间隔 ~1.2s 同区域 crop diff）判「动没动」，像素级零差异=静止；③ 桌面端正常 ≠ WebUI 正常——gooey 与 WebUI 是两个前端。

## [2026-09-17] adb exec-out 不递 stdin EOF——设备写文件用 base64 分块法

- **现象**：`adb exec-out sh -c "run-as app sh -c 'cat > file'" < local` 永久悬挂（本地 stdin 文件读完，EOF 不过 adb 通道），任务挂 10 分钟零输出。
- **解决方案**：写设备文件改用 base64 分块——本地 `base64 -w0 file`，按 8KB 分块，`run-as sh -c 'echo -n <chunk> >> tmp.b64'` 逐块追加，末块后 `base64 -d tmp.b64 > target`；再 `exec-out cat target` 拉回 diff 校验。读方向（cat/pull）exec-out 正常，只有写方向的 stdin 悬挂。

## [2026-09-16] floatingx 坐标陷阱：jitpack 聚合是空 jar、中央本体无 compose 包

- **现象**：fork 原样代码编译报 `Unresolved reference com.petterp.floatingx.compose.enableComposeSupport`（OverlayController），而 m0 当年同源码能编过。
- **根本原因**：`io.github.petterpx:floatingx:2.3.7` 在**中央仓是无 compose 包的瘦 aar**（hash 与 aliyun 完全一致，排除镜像污染）；compose 支持在独立构件 `floatingx-compose`。m0 能编过纯属仓库序巧合：fork 原来 jitpack 排在 mavenCentral 前，jitpack 按 GitHub tag 现场构建出**聚合空 jar**（只含 MANIFEST），其 pom 传递出 `io.github.petterpx.floatingx:floatingx-compose`（jitpack 多模块坐标）——真货来自传递依赖。给 settings 加镜像时把 jitpack 挪到队尾，中央瘦 aar 截胡且没有传递依赖，compose 包整个消失。
- **解决方案**：不恢复 jitpack 优先序（CN 不稳定+现场构建慢），改为 toml/build.gradle.kts **显式声明 `io.github.petterpx:floatingx-compose:2.3.7`**（中央/aliyun 直达）。教训：改仓库顺序属于依赖图变更，同坐标在不同仓库的构件内容可能完全不同（jitpack 构建产物 ≠ 作者发布产物）。

## [2026-09-16] `hideOverlayWindows` 在 HONOR ROM 上全局生效且粘性——悬浮窗"消失"不是 app bug

- **现象**：悬浮球点了一下（想开面板）后球与面板双双"消失"；dumpsys 显示球窗口仍在、视图 VISIBLE，但 `mPolicyVisibility=false mForceHideNonSystemOverlayWindow=true shown=false`——是系统策略强隐，不是 app 侧隐藏（app 侧 hide 的特征是 `mViewVisibility=0x8`）。后续该窗口输入通道不消费触摸，点它落空。
- **根本原因**：`android.settings.SETTINGS`（HWSettings，设了 `hideOverlayWindows` 防点击劫持）被起到**虚拟屏**上做注入测试时，MagicOS 把"隐藏非系统悬浮窗"**全局**应用到所有 display——主屏 overlay 全被强隐。且 flag **粘性**：Settings 进程死后不自动复评，直到一次前台应用切换（home→回 app）才重估恢复。游戏类 app 不设此属性（实测游戏前台时球存活），生产无影响；**测试纪律：别把 Settings 类 app 起到 VD 上**。
- **解决方案**：前台切换一次（`input keyevent KEYCODE_HOME` → 重进 app）触发复评即恢复。排查口诀：悬浮窗"消失"先看 `mViewVisibility`——`0x8`=app 自己藏的（查代码路径），`0x0`+`mPolicyVisibility=false`=系统策略藏（查当前谁在前台/谁设了 hideOverlayWindows）。

## [2026-09-16] 空虚拟屏上注入"失败"是消费语义，不是链路坏（WAIT_FOR_FINISH）

- **现象**：桥 CLICK/SWIPE 端点在新建空 VD 上回 `touch down failed`，疑似注入链路坏。
- **根本原因**：`InputControlUtils`（同 `input -d <id> tap`）走 WAIT_FOR_FINISH 模式；**无窗口消费触摸的屏**上 framework natively 返 false（`input -d 2 tap` 同样静默 false 但 exit=0）。VD 上 `am start` 任意窗口后，同链路 CLICK/SWIPE 全 `ok:true`。
- **解决方案**：判故障时先给 VD 放个窗口再注入；生产语义本就正确（游戏常驻 VD，必有消费者）。
- **[2026-09-17 升级] 窗注册竞态**：窗"可见"≠"可点"——`am start --display N` 把游戏拉上 VD 后 SurfaceFlinger 先出帧（screencap/OCR 已能识别），但 input 窗注册滞后 **~1s**，此间注入照样 false。实测：am start 后首次轮询 click 100% 败，~1s 后恢复。AzurPilot 链路（am start→识别→立即点）首击必踩。已在桥侧修：`BridgeServer.downWithRetry()` 3s 预算/200ms 间隔重试 down，handleClick/handleSwipe 共用；耗尽后报错带诊断 `touch down failed (no touchable window on display N within 3000ms)`——裸 failed=竞态，带"no touchable window"=真空 VD（游戏没起/崩了）。

## [2026-09-16] values-en 字符串带裸撇号炸 aapt（`app's` → `app\'s`）

- **现象**：M2-d 构建 aapt 报错，指向 `values-en/strings.xml` 新文案。
- **根本原因**：英文资源里的 `app's` 撇号未转义——aapt 字符串解析把 `'` 当引号边界。
- **解决方案**：values-en 文本统一写 `app\'s`；新增英文文案时 grep 一遍裸 `'`。

## [2026-09-16] 屏幕旋转会换掉 input 坐标空间——点击前先核旋转与目标帧

- **现象**：游戏被起到主屏后主屏转横屏（2800×1264），按竖屏（1264×2800）坐标发的两次 tap 越界被钳到屏幕边缘（未触达有效 UI，但属意外输入）。
- **根本原因**：`input tap` 坐标空间跟随**当前旋转**；被测机旋转可被前台 app（游戏=横屏）随时改变。
- **解决方案**：自动化点击纪律——每次 tap 前先 `dumpsys input | grep orientation`（或 screencap 尺寸）核坐标空间，再从 dumpsys/screencap 取目标当前帧坐标；禁止复用上一次截图的坐标。

## [2026-09-16] rootfs 解压目标绝不能是 getExternalFilesDir：/sdcard 无符号链接且 noexec

- **现象**（提交前自查拦下，未出货）：M3-a 解压流水线初版把 rootfs 解到 `AppPaths.ROOT`（`getExternalFilesDir(null)` = /sdcard/Android/data/...）。
- **根本原因**：① /sdcard 是 FUSE 模拟存储，**不支持 `Os.symlink`**——ubuntu-base 有 740 个符号链接，第一个就炸（EPERM）；② /sdcard 挂载 noexec，guest 二进制无处可跑；③ Spike A 已实证：targetSdk 35 上 app 直接 `execve(filesDir/...)` 被拒，但 **proot 从内部 filesDir 跑 guest 全 PASS**——内部 filesDir（/data/data/<pkg>/files）才是合法位置。
- **解决方案**：解压目标改 `context.filesDir/rootfs`；日志/导出继续用外部私有目录（AppPaths.ROOT）不受影响。判据速查：凡是"要 exec 或要 symlink"的数据，一律内部 filesDir；外部存储只放纯数据。

## [2026-09-16] 「服务就绪」判据必须打到真实服务端口：wrapper 活着 ≠ WebUI 能服务

- **现象**：M3-b 首版 ProotHost 以 wrapper(22400) 可达即置 RUNNING → AzurPilotScreen 自动重载 WebView → 卡进错误页；彼时 gui.py 进程虽在但 uvicorn 还在 import（需数秒），22267 connection refused。
- **根本原因**：wrapper 先于 gui 就绪；`gui_alive=true` 只表示子进程活着，不代表端口在听。
- **解决方案**：RUNNING 语义改为 **wrapper /status 与 WebUI 首页双 200**（`awaitServices` 双探）；状态机里的"就绪"永远锚定最终用户打的那个端口。同类教训通用：任何"依赖服务就绪"判定，探针必须打到最后一环。

## [2026-09-16] git.lyoko.io 慢网实测 83KB/s：深度 shallow fetch 不能当启动阻塞，快进路径必须零下载

- **现象**：M3-b 首跑热更新 `git fetch --depth 50`（9675 objects）在设备 WiFi 下 ~83KB/s，撞 240s 超时被杀；浅克隆 fetch **不可续传**，每次重试从零开始——慢网下永远更新不完。
- **根本原因**：AzurPilot 树大（资产多），depth 50 首包百 MB 级；阻塞式热更新在弱网退化成"每次启动白等 4 分钟"。
- **解决方案**：`update.sh` 加 **ls-remote 快进路径**——先 `git ls-remote`（秒级）比对远端 HEAD 与本地 commit（state 文件/BUILD_MANIFEST 钉版），一致直接 UNCHANGED 零下载（真机二启 5s 到 wrapper 就绪）；首次真更新降级 `--depth 1` 单提交树，后续 fetch 按需加深。断网/超时照旧降级不阻塞。弱网大更新续传/后台化留阶段五容灾课题。

## [2026-09-16] app/.gitignore 的 jniLibs 排除会误伤自建 native 库：proot 件移 prootLibs + srcDir

- **现象**：proot 九件套拷入 `app/src/main/jniLibs/` 后 git 完全看不到（`git check-ignore` 命中 `app/.gitignore:41`）。
- **根本原因**：该规则为 运行框架 拉取件（`scripts/setup_framework.py` 产物）而设，按目录整棵排除；目录级排除无法用 `!` 反向包含其子项。
- **解决方案**：自建钉版产物移 `app/app/src/main/prootLibs/`，`build.gradle.kts` 加 `jniLibs.srcDir("src/main/prootLibs")` 并入打包（APK 内 `lib/arm64-v8a/libproot.so` 已核）；规则边界=拉取件 jniLibs 不入库、构建输入 prootLibs 必入库。

## [2026-09-16] adb 安装链两坑：管道退出码被 tail 吞掉 + adb 不吃 MSYS 路径

- **现象**：① `adb install -r <apk> | tail -1 && am start …`——install 失败（stat 不到文件）但管道退出码是 tail 的 0，`am start` 照跑，旧包被重启造成"新代码已上机"假象；② `adb install /d/VSCodeCache/...` 报 `failed to stat`——Windows adb.exe 不解析 MSYS 挂载路径。
- **解决方案**：安装命令独立成行、不看管道退出码（判据=输出含 `Success`）；给 Windows 侧工具（adb/python/gradle）一律传 `D:\...` 或 `D:/...` 形式路径（AGENTS.md"只吃 Windows 路径"的又一实例）；`cd app` 后相对路径会叠成 `app/app/app/...`，跨目录操作用绝对路径。

## [2026-09-17] adb 遥控 UI 两坑：布局漂移让旧坐标落空 + 全屏小目标偏 14px 被黑边丢弃

- **现象**：① 按几十秒前截屏的坐标 `input tap` 工具区按钮，什么都没发生（wrapper 无日志无进程）——期间日志板高度变化把工具区下移 ~100px，tap 落在标签与按钮间的空白；② 全屏态点右上角 X，偏 14px 落到游戏画面外的黑边，被 `previewTouchInput` 边界检查丢弃（设计行为：黑边/越界坐标不注入），全屏没退出。
- **解决方案**：adb 遥控点击必须**当帧截屏、当帧取坐标**（动态布局高度随时在变）；全屏 X 这类小目标从原图 region crop 量准中心再点（IconButton 实际触摸目标远小于直觉）。通用判据：凡是「点了没反应」，先假设坐标漂移，拿最新帧重算。
# [2026-09-24] Android proot 可读 `/proc/<pid>/stat` 却拒绝 `/proc/stat`：psutil 创建时间让 WebUI 启动中止

- **现象**：真机首启部署、热更新和 WebUI 入口均已运行，随后在 `claim_owner(os.getpid())` 中失败；堆栈最终是 `PermissionError: [Errno 13] Permission denied: '/proc/stat'`，UI 长时间停在启动阶段。
- **根本原因**：Linux 版 `psutil.Process.create_time()` 先从 `/proc/<pid>/stat` 读取进程 starttime，再从全局 `/proc/stat` 读取 `btime` 换算 Unix 时间。Android 应用沙箱下前者对同 UID 进程可读，后者在 proot 绑定后被拒绝。worker 登记、PID 复用保护和子树清场多处直接依赖 `create_time()`，因此只绕过首个调用会把故障推迟到任务启停。
- **解决方案**：统一封装进程身份读取。正常平台继续用 psutil 创建时间；遇到 `AccessDenied` 时解析 `/proc/<pid>/stat` 第 22 字段，以负的启动 tick 保存，既稳定又不会与 Unix 时间戳混淆。所有身份比较和子进程登记复用该接口；负身份在 POSIX 清场时通过已验证 PID 直接发 `SIGKILL`，避免 psutil 发信号前再次读取 `/proc/stat`。加入含括号进程名的 stat 解析测试及权限拒绝回退测试。
# [2026-09-24] Android 空虚拟屏无首帧导致 Restart 在启动游戏前死锁

- **现象与实证**：Redmi K60 Ultra 的 AzurPilotVirtualDisplay 已正常创建为 display 16（1280×720、Surface 已绑定），但游戏尚未真正渲染时，SurfaceFlinger 对该虚拟显示直接 `screencap -d` 得到纯黑图，AOS bridge 同时返回 `no frame available`。将 `com.bilibili.azurlane/com.manjuu.azurlane.MainActivity` 启动到 display 16 并等待真实内容提交后，SurfaceFlinger 截图立刻恢复为多色有效画面，bridge 同时成功返回 1280×720×3 的非黑帧。因此 ImageReader/plane/BGR 传输链在有真实内容时可正常工作，先前对 Mali/CPU usage 的推断不成立。
- **根本原因**：AzurPilot `run()` 对所有任务都会先执行 `device.screenshot()`，而 `Restart` 的真正启动逻辑 `restart() -> LoginHandler.app_restart()` 在它之后。普通模拟器的主显示即使游戏未运行也总有系统画面可截；Android 独立版的 OWN_CONTENT_ONLY 虚拟屏在游戏首帧前可能没有可读内容，于是 Restart 先截图失败、被再次调度 Restart，永远执行不到启动游戏。
- **解决方案**：AP `1841cb194` 让 `Restart` 跳过任务前置截图，先进入 `app_restart()`；其它任务仍保留原有前置截图。新增回归测试保证 Restart 在无首帧场景不会调用 screenshot。AOS 保留 reader/callback/acquire/write/plane 诊断并把状态附加到 ping/no-frame 错误，后续若再出现首帧问题可直接从日志判断失败阶段，不再返回初始化伪黑帧。
