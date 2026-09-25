# AzurPilot Android 一体化 APK · 路线图 v3

> 修订基础：v2（`AzurPilot-Android-修订版路线图-v2.md`）+ 2026-09-15 三轮设计拷问结论 + m0 归档事实核查 + shizuku-m 实现核查。
> 与 v2 的根本差异：**后台挂机是硬需求**（m0 用户拍板"手机要正常用"，`m0-archive/docs/devlog/2026-08-29.md:142`）。原依据 m0 §41「adb 无法触达虚拟屏」（`m0-archive/docs/debug.md:281-285`）——2026-09-15 Spike E 已将其推翻（`screencap` 吃 SF physical id、`input` 吃 logical id 后均可达，见 `spike/e-adb-virtual-display/REPORT.md`），但 VD 保活是 shell 域死穴、链路悬在 adb 会话上，**结论不变**：v2 的"adb 自连为主控"降级为探索项，**m0 桥控制面保留**，v2 的 adb 四步流水线整体移出主线。

## 0. 已确认的技术决策

| # | 决策 | 结论 | 依据 |
|---|------|------|------|
| 1 | 后台挂机 | **硬需求**。游戏跑虚拟屏，真机正常他用 | m0 产品定位与用户拍板 |
| 2 | 总体架构 | **混合基线**：proot rootfs 运行时（取代 Termux）+ m0 桥控制面（保留）；Spike E 探索 adb 直控虚拟屏作上行 | 拷问 Q1 |
| 3 | 目标用户/分发 | GitHub 倒腾用户，流程尽可能简化；侧载（GitHub Release + Gitee 镜像），不上任何商店 | 拷问 Q2 |
| 4 | Shizuku | 用 shizuku-m（官方 v13.6.0 fork，3 commits，+214/−13，API 全兼容）。**不内置 APK**，README + 应用内图文引导自装；与官方版互斥（同包名自签名）需在引导写明 | 拷问 Q10；`shizuku-m/SHIZUKU-M.md` |
| 5 | adb 自连 | 降级为 Spike E 的前置：经 shizuku-m 的 `tcpip:5555` 通道 connect + 一次性 RSA 授权；**砍掉** TLS 配对与 mDNS 发现（m0 唯二负面记录所在） | 拷问 Q9 |
| 6 | OCR | rootfs 内 onnxruntime + PP-OCR（m0 备案"C 路线"转正）。模型复用 `m0-archive/spike/m0/resource/base/model/ocr/`（det 9.4MB / rec 20.2MB），m0 实测油数 100/100、单次 0.05s；`rpc.py` shim 接口保留 | 拷问 Q6/Q9 |
| 7 | 上游业务引擎 | 剔除业务引擎（Pipeline/资源加载）与 OCR 桥端点；**保留**特权进程（虚拟屏、截屏/注入）、桥代理（TCP 22300，六端点减为五端点）、AzurPilot 侧 `设备方法补丁` | 拷问 Q6 |
| 8 | 开发基线 | 复活 `m0-archive/vendor/上游 fork` @ b2b0f54 做减法；不从上游重 fork | 拷问 Q7 |
| 9 | rootfs | GHA ARM64 runner 原生构建（公共仓免费）。AzurPilot = 官方 master（Gitee 镜像，钉 commit）+ fullcn 同款国内镜像 deploy 配置（fullcn 是官方 Release 的大陆变体，同架构，7z 为 Windows 整包不直接采用） | 拷问 Q8/Q13 |
| 10 | 构建清单 | rootfs 内附 BUILD_MANIFEST：AzurPilot 上游 commit、补丁集版本、PP-OCR 模型版本、rootfs 版本号，App 可读 | 拷问 Q8（用户硬要求） |
| 11 | 开机引导 | 每次开机不可约手动步骤 = 开无线调试 → shizuku-m 点"启动"（首次使用另加一次性配对输码）。跨开机周期无法免（`persist.adb.tcp.port` 被 SELinux 拒）。App 检测 Shizuku 不活 → 跳图文引导 → 回来续跑 | 拷问 Q11 |
| 12 | 仓库公开 | 后置到项目验证可行后。例外：阶段一开工时主仓单独公开（GHA ARM64 runner 免费仅限公共仓；主仓全新无历史包袱） | 拷问 Q12 |
| 13 | Spike 红线 | 每个咽喉至少一条已验证路径即可进入下一阶段；Spike A 允许降级 targetSdk 28（侧载场景） | 拷问 Q3 |

## 1. 架构总览（v3）

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  主 Activity: 全屏 WebView ──► http://127.0.0.1:22267（AzurPilot WebUI 原版）│
│  悬浮窗: start/stop/日志 ──► wrapper 薄 HTTP 接口                            │
│  Shizuku（shizuku-m，用户自装）: 提权 shell ──► 关幻影进程查杀 / 建虚拟屏    │
│  特权进程: AzurPilotVirtualDisplay（1280×720）+ 截屏/注入 + 桥代理 TCP 22300 │
│       （5 端点：ping/screencap/click/swipe/shell；10s 心跳；全局串行锁）     │
│  前台服务: proot（libproot.so，jniLibs）──► Ubuntu ARM64 rootfs              │
│       └─► AzurPilot（官方 master 钉 commit）+ 设备方法补丁                   │
│       └─► wrapper.py ──► AzurPilot 调度器子进程                              │
│       └─► onnxruntime + PP-OCR（in-proc，经 rpc.py shim 对接 AzurPilot OCR）│
│       └─► 静态 aarch64 adb（备用，Spike E 成功则启用）                       │
└──────────────────────────────────────────────────────────────────────────────┘
数据面：AzurPilot 截图/点击 → 设备方法补丁 → TCP 22300 → 特权进程 → 虚拟屏（m0 实测 p50=0.109s，1800/1800 零失败）
OCR 面：AzurPilot → rpc.py shim → in-proc PP-OCR（2D 单通道需堆叠 3ch；BGR↔RGB 翻转——m0 硬坑记录）
```

## 2. 阶段〇：技术 Spike（重排）

| Spike | 内容 | 性质 | 判负处置 |
|-------|------|------|----------|
| A | APK 内 proot exec：`libproot.so` 放 jniLibs 走 nativeLibraryDir；检查 Android 15+ 16KB 页对齐 | 🔴 **唯一生死线** | 失败降级 targetSdk 28（仅侧载），继续 |
| B' | 经 shizuku-m `tcpip:5555` connect + 一次性 RSA 授权弹窗 | 探索前置 | 仅影响 Spike E |
| C | 幻影进程查杀关闭（`device_config put activity_manager max_phantom_processes` / `settings_enable_monitor_phantom_procs false`） | 🟢 走查（m0 已产品化） | 真机复验必须过 |
| D | AzurPilot 进程管理模块 import 面探查，确定 wrapper.py 接口 | 源码阅读+小实验 | 兜底=写配置 JSON+进程组 |
| E | adb 直控虚拟屏：先重试 `screencap -d`/`input -d` 不同 display flags 组合，再试 scrcpy-server `--new-display` | 上行探索 | 失败回落 m0 桥，不阻塞主线。**→ 已执行（2026-09-15）：能力面推翻 §41（截屏/注入均可达），但 VD 保活死穴 + 手势劫持事件 → 维持桥为主、登记诊断/备用，见 `spike/e-adb-virtual-display/REPORT.md`** |
| F | rootfs 内 onnxruntime + PP-OCR 精度复测：油数 100 次 ≥98%（沿用 m0 DoD） | 验收前置 | 不达标则排查 onnxruntime/模型，不过不进阶段一 |

**进入阶段一条件**：A（主或备）、C、F 通过；B'/E 结果只决定控制面未来是否可切换到 adb，记录归档。

## 3. 阶段一：rootfs 烘焙

- **前置**：主仓公开（GHA ARM64 runner 免费硬要求）。
- GHA ARM64 runner 原生构建：Ubuntu ARM64 最小化 + Python 3 + opencv-headless + onnxruntime + 系统库。
- 植入 AzurPilot 官方 master（Gitee 镜像，**钉 commit**）+ fullcn 同款国内镜像 deploy 配置；热更新只拉源码不动依赖（漂移→提示重下整包，稀有事件）。
- 平移 m0 补丁集与资产（附录 A）；内置静态 aarch64 adb + wrapper.py + PP-OCR 模型；素材图库不内置，首启从 Gitee 拉。
- 内置 BUILD_MANIFEST（决策 #10）；瘦身打包 `rootfs.tar.xz`（目标 ~250MB±）。
- **DoD**：Termux/侧载环境手动解压，离线单命令拉起 AzurPilot WebUI；版本号与 BUILD_MANIFEST 可被 App 读取。

## 4. 阶段二：宿主外壳减法整理

- 基线 = `m0-archive/vendor/上游 fork` @ b2b0f54 复活（含 WebView `vh` 塌缩修复、桥、虚拟屏管理、Shizuku 安装辅助——全部真机验证过）。
- **保留**：特权进程（虚拟屏 + 截屏/注入）、桥代理（5 端点）、全屏 WebView 容器、Shizuku 权限与配对辅助、前台保活 + 电池优化引导。
- **VD flag 硬约束（2026-09-15 手势劫持事件后立）**：建虚拟屏**禁止** `SHOULD_SHOW_SYSTEM_DECORATIONS` 与 `ROTATES_WITH_CONTENT`，flag 集照 `VirtualDisplayManager.kt:178-202`（含 `STEAL_TOP_FOCUS_DISABLED`）；建屏后回归断言：`dumpsys display` 中 VD 不含 `FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS`、`GestureNavAnim/GestureSildeOut/NavigationBar0` 仍在 display 0；属主进程保活照 m0 每 4s `userActivity(displayId)`。
- **剔除**：上游业务引擎 .so 业务引擎（Pipeline/资源加载）、OCR 桥端点。
- 改动尽量外挂模块化，压低 rebase 上游成本；AGPL LICENSE 与署名保留（公开动作按决策 #12 后置）。
- **DoD**：APK 可请求 Shizuku 权限、建虚拟屏、桥 ping 通、WebView 显示空白页、呼出悬浮球。

## 5. 阶段三：管道穿透与生命周期

1. 首启解压流水线（开屏进度条，磁盘余量 ≥2GB 校验）。
2. Shizuku 提权执行：关幻影进程查杀 → 建/探虚拟屏 → 拉起特权进程 + 桥。
3. 前台服务 ProcessBuilder 拉起 proot → 自愈清锁 → Gitee 热更新（断网超时降级不阻塞）→ wrapper.py → AzurPilot。
4. 全生命周期防护：stdin 管道破裂自尽 + 进程组全杀；划掉 App 进程归零（m0 教训：`am force-stop` 杀不掉 shell uid 残留，必须显式 kill）。
5. 重启恢复：检测 Shizuku 存活 → 不活跳引导页（决策 #11 的每次开机 30 秒手动链）。
- **DoD**：开屏进度条走完 → 自动热更新 → 外部浏览器开 127.0.0.1:22267；划掉 App 无残留 Python/proot/桥进程；重启手机经引导恢复可挂机状态。

## 6. 阶段四：交互闭环与体验

1. HTTP 探针轮询 22267，就绪后 WebView 自动载入并淡出开屏动画（沿用 m0 的 `vh` 注入修复）。
2. 悬浮窗 start/stop 映射 wrapper 薄 HTTP 接口（不调 pywebio 协议）。
3. 日志桥接：wrapper 读 AzurPilot 调度器日志推悬浮窗半透明日志板。
4. shizuku-m 未安装/未激活/与官方版冲突的引导页（README + 应用内图文，含"先卸载官方 Shizuku"提示）。
5. 分工：高频操作走悬浮窗，完整配置走 WebView。
- **DoD**：打开 App 到进控制台全程无感；游戏（虚拟屏）上点悬浮窗"开始"，AzurPilot 立即控制游戏，日志上悬浮窗。

## 7. 阶段五：真实设备抗压与交付

1. 多 ROM 兼容测试 + 已知特例矩阵（MIUI/HyperOS 安全设置与通知样式、ColorOS 权限监控、HarmonyOS 无无线调试入口——阶段五决策支持或排除）。
2. 长挂机 2~3 小时 + 息屏测试（LMK 与幻影进程验证）。
3. 断网/弱网容灾（Gitee 超时降级）。
4. 最低支持配置定档：桥方案有 m0 数据兜底（p50 0.109s）；若 Spike E 成功则实测 adb screencap/scrcpy 耗时，对照 AzurPilot 官方 >1s 不可用线。
5. shizuku-m 产品化前置：完整克隆重立 fork（分支+tag+规范版本号；若届时对外分发须替换原创图标——现图标衍生自上游禁用素材）。
6. Release 签名打包；执行决策 #12：三个仓库公开，发布页附源码链接（AGPL 义务）。
- **DoD**：免 root、仅 shizuku-m 的手机上一键安装、开屏热更新、悬浮窗、长稳挂机；README 含支持机型/ROM 清单与仓库地址。

## 8. 风险登记表（v3 重排）

| 风险 | v2 等级 | v3 等级 | 说明 |
|------|---------|---------|------|
| APK 内 exec proot 被 noexec 拦截 | 🔴 | 🟠 | UserLAnd/Andronix 量产先例 + targetSdk 28 备选 |
| adb 自连（配对/mDNS） | 🔴 | ⚪ | 移出主线，仅 Spike E 前置；脆的两步已被 shizuku-m 接管 |
| 幻影进程杀手 | 🔴 | 🟢 | m0 已产品化（`PermissionGrantHelper.kt:118-142`） |
| AzurPilot 内部 API 变动打破 wrapper | 🟡 | 🟡 | 面最小化 + 配置/进程组兜底 |
| 低端机截图耗时 | 🟡 | 🟡 | 桥方案有 m0 兜底数据；adb 方案阶段五实测 |
| HarmonyOS 无线调试 | 🟠 | 🟠 | 阶段五决策 |
| 素材转分发版权 | 🟡 | 🟡 | 不内置，Gitee 拉 |
| 上游 rebase 成本 | 🟡 | 🟡 | 最小侵入 fork |
| 重启手动引导的流失 | — | 🟠 | 无 root 物理不可约；预期管理写进用户文档 |
| shizuku-m 分发合规（图标/商店条款） | — | 🟡 | 决策 Q10 选不内置后对本项目无直接影响；其自身公开分发前需换图标 |

## 附录 A：m0 平移清单（rootfs/外壳复用项）

- **补丁**（`m0-archive/termux/patches/`）：`module/ocr/rpc.py`（OCR shim，改接 in-proc PP-OCR；2D 单通道堆叠 3ch）；`设备方法补丁`（设备方法全套：screenshot/click/swipe/shell/app 管理 + 虚拟屏 VID 探测）；`module/base/base.py`（early_ocr_import 改预热）；numpy2 vstack `list()` 补丁；jellyfish 纯 Python shim；pydantic 锁 v1。
- **资产**：PP-OCR det/rec/keys（`m0-archive/spike/m0/resource/base/model/ocr/`）；`seeds/deploy.yaml`（Gitee 镜像 + OCR 配置改为 in-proc）。
- **协议**：TCP 22300 五端点（OCR 端点退役）；10s 心跳（30s 无流量判死教训）；反向调用全局串行锁（ZMQ 非线程安全教训）。
- **经验**（`m0-archive/docs/devlog/`）：WebView `vh` 塌缩注入修复；RUN_COMMAND 权限只授清单声明方；AzurPilot 截图 BGR↔AzurPilot RGB 翻转；主界面顶栏 OCR 不可靠（只在深色页面读数）；游戏设置清单（待机模式关/展示结算角色关/剧情自动播放开+特快）；资产修补用 assets_fix.py 按名改不整文件覆盖。

## 附录 B：事实核查出处

- m0 归档核查：否决 proot-distro 的三条理由（`m0-archive 的共识记录:27,45,46`）在 v3 架构下全部化解（phantom 已可关、依赖现代化补丁已有、"APK 内 proot"由 Spike A 直接验证）；OCR 死链记录（`:43`）；桥性能数据（`:117`）。
- shizuku-m 核查：`D:\VSCodeCache\shizku-m\shizuku-m\`，v13.6.0 + 3 commits；`tcpip:5555` 机制（`manager/.../StarterActivity.kt:198-259`）；重启后需在线激活（`SHIZUKU-M.md:24`）；Apache-2.0 + 上游图标/商店限制（`README.md:77-103`）。
- fullcn：官方 Releases 大陆变体 `官方 Releases 大陆变体包`（github.com/LmeSzinc/AzurLaneAutoScript/releases）。
