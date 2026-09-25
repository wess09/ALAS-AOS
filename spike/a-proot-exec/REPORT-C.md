# Spike C 报告 · 幻影进程查杀（Phantom Process Killer）缓解真机复验

> 路线图 v3 阶段〇 · Spike C（`docs/roadmap-v3.md` §2，「真机复验必须过」）
> 日期：2026-09-15 ｜ 设备：HONOR PPG-AN00（真机，`AVAY025422002864`）｜ Android 16 / SDK 36 / 4KB 页 / SELinux Enforcing
> 证据文件：`dist/logs/phantom-*`（逐轮采样 CSV + 原始 logcat + AM_KILL 事件 + 应用落盘日志），APK：`dist/spikea-phantom-target35-debug.apk`

## 0. 结论（先给答案）

**PASS —— m0 产品化的两条命令在 Android 16 / MagicOS 10 上仍然有效，且在本机「任一条单独生效」。**

- 对照（默认配置，3 轮）：51 个子进程被 AMS 在 5 分钟节拍上成批 SIGKILL 掉 20 个（`Trimming phantom processes`），压回 31、proot 死亡。
- 缓解（两条命令都开，35 分钟 / 跨 ~7 个节拍）：48 个 guest 长命进程 + proot + sh **零伤亡、零 trim**。
- 可分性：flag=false 与 cap=INT_MAX **各自单独即可**（B1/B2 双通过）；m0 时代的两个旧键**无效**（L 轮 2 秒即被裁）。
- 结束状态：两条缓解命令保持开启；进入实验前见到的两个旧键已恢复原值。

三条事实构成结论：

1. **默认配置下真的会被收割**（对照轮 A / A2，冒烟轮 A0 同）：App（untrusted_app）自己 fork 的 proot 树共 51 个子进程，`PhantomProcessList.trimPhantomProcessesIfNecessary()` 在 **AMS 的 5 分钟节拍**上一次性 SIGKILL 掉 20 个（19×busybox + 1×`libproot.so`），进程数被压回 **31**，proot tracer 死亡 → 整条 guest 链路的存活上报中断（`PROOT_EXIT=137`）。三次默认配置观测（A0 冒烟 / A / A2）分别命中在 spawn 后 **+43s / +29s / +267s**（见 §2.4，均 ≤1 个节拍）。
2. **缓解轮 B（两条命令都开）**：48 个 guest 长命进程 + proot + sh 全程存活 **35 分钟**（跨 ~7 个节拍），窗口内 **0 次 trim**。
3. **可分性**：`settings_enable_monitor_phantom_procs=false`（关总闸）与 `device_config max_phantom_processes=2147483647`（顶配额）**各自单独生效**即可保住全部进程；本机（全局配额几乎由本 App 独占）两条都写是"双保险"。二者的可观测差异见 §3.4。

命令写入情况：两条命令在本机均**可写、无告警**（`exit=0`，无 stderr/stdout 输出），`dumpsys activity settings` 立刻反映新值（`max_phantom_processes=2147483647`）。

---

## 1. 实验对象与环境

### 1.1 设备事实

| 项 | 值 | 来源 |
|---|---|---|
| 型号 / 厂商 | PPG-AN00 / HONOR（MagicOS） | `getprop ro.product.model` |
| Android / SDK | 16 / 36 | `getprop ro.build.version.release/sdk` |
| 内核 / 页大小 | `6.6.89-android15-8-…-4k` / 4096 | `uname -a` / `getconf PAGE_SIZE` |
| SELinux | Enforcing | `getenforce` |
| App | `com.azurpilot.spikea` uid **10301** (`u0_a301`)，targetSdk 35，debuggable | `cmd package list packages -U` |
| cgroup | v2；app 组 `/sys/fs/cgroup/apps/uid_10301/pid_<appPid>/cgroup.procs` | 设备实测 |

### 1.2 进入实验时的原始设置值（本报告记录，供回滚/对照）

| 键 | 实验前实测值 | 说明 |
|---|---|---|
| `device_config activity_manager max_phantom_processes` | `null`（=默认 32） | 未被 m0 时代改过 |
| `settings global settings_enable_monitor_phantom_procs` | `null`（=默认 true，监控开） | 未被 m0 时代改过 |
| `settings global settings_config_disable_monitor_phantom_procs` | **`true`** | **m0 时代残留**（上游 fork `PermissionGrantHelper` 写入的 Android 12 Beta 旧键） |
| `settings global phantom_process_killer_enable` | **`false`** | 同上（旧键） |
| `settings global settings_activity_manager_max_phantom_processes` | `null` | m0 `setup_env.sh` 注释里写的键名（AOSP 无此键，无效） |
| `settings global stay_on_while_plugged_in` | 0 →（实验期间置 2 = USB 亮屏） | 仅为让 A/B 两轮屏幕状态一致 |

> 旧键是否为有效缓解手段：本报告 §3.5 的「L 轮」直接验证。

---

## 2. 机制与观测口径（为什么这两条命令能起效）

### 2.1 AOSP android16-release 源码（决定性的三处）

1. **总闸**（`services/core/java/com/android/server/am/PhantomProcessList.java`）：

```java
    void trimPhantomProcessesIfNecessary() {
        if (!mService.mSystemReady || !FeatureFlagUtils.isEnabled(mService.mContext,
                SETTINGS_ENABLE_MONITOR_PHANTOM_PROCS)) {
            return;                                    // ← 总闸：flag=false 直接不裁
        }
        ...
                if (mService.mConstants.MAX_PHANTOM_PROCESSES < mPhantomProcesses.size()) {
                    ... 排序后
                    for (int i = mTempPhantomProcesses.size() - 1;
                            i >= mService.mConstants.MAX_PHANTOM_PROCESSES; i--) {
                        final PhantomProcessRecord proc = mTempPhantomProcesses.get(i);
                        proc.killLocked("Trimming phantom processes", true);
                    }
```

2. **配额**（`services/core/java/com/android/server/am/ActivityManagerConstants.java`）：

```java
    private static final int DEFAULT_MAX_PHANTOM_PROCESSES = 32;
    private static final String KEY_MAX_PHANTOM_PROCESSES = "max_phantom_processes";
    ...
    private void updateMaxPhantomProcesses() {
        MAX_PHANTOM_PROCESSES = DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ACTIVITY_MANAGER, KEY_MAX_PHANTOM_PROCESSES,
                DEFAULT_MAX_PHANTOM_PROCESSES);
```
即 `device_config put activity_manager max_phantom_processes <N>` 直接改的就是这个常量（namespace = `activity_manager`）。

3. **flag 取值链**（`core/java/android/util/FeatureFlagUtils.java`）：

```java
    public static final String SETTINGS_ENABLE_MONITOR_PHANTOM_PROCS =
            "settings_enable_monitor_phantom_procs";
    ...
    public static boolean isEnabled(Context context, String feature) {
        // Override precedence: Settings.Global -> sys.fflag.override.* -> static list
        String value;
        if (context != null) {
            value = Settings.Global.getString(context.getContentResolver(), feature);
            if (!TextUtils.isEmpty(value)) return Boolean.parseBoolean(value);
        }
        value = SystemProperties.get(getSystemPropertyPrefix(feature) + feature);
        ...
        value = getAllFeatureFlags().get(feature);   // DEFAULT_FLAGS: true
        return Boolean.parseBoolean(value);
    }
```
`settings put global settings_enable_monitor_phantom_procs false` 命中第一优先级（Settings.Global），立即生效。
（另一条等价路径是 `persist.sys.fflag.override.settings_enable_monitor_phantom_procs`，需要更高权限，本 spike 未使用。）

### 2.2 「幻影」的判定 = 读 App 主进程的 cgroup

`PhantomProcessList.lookForPhantomProcessesLocked()` 遍历所有 app 进程，读 `nativeGetCgroupProcsPath(uid, pid)` 指向的文件；本机就是：

```
/sys/fs/cgroup/apps/uid_10301/pid_<appPid>/cgroup.procs     ← app 主进程 + 全部后代
```

**含义（对 v3 架构至关重要）**：进程算不算幻影，取决于 **fork 者的 cgroup**，与 uid 无关——
- App（untrusted_app）自己 fork 的 proot 整树 → **全在 app cgroup → 全部计入配额**（本 spike 的场景）；
- 由 Shizuku/shell 域进程 fork 的进程即使 uid 相同也落在别的 cgroup（设备实测 `memory:/` vs `memory:/apps/com.azurpilot.spikea`），不计入、`am force-stop` 也收不掉。

另：`trimPhantomProcessesIfNecessary()` 里的 `mPhantomProcesses` 是**全系统**所有 app 的幻影合集，配额是**全局**的（不是每 App 32）——本机除本 App 外长期只有 0~2 个他 App 幻影，所以 32 实质上就是本 App 的预算。

### 2.3 观测口径（三层互证）

| 口径 | 命令 | 说明 |
|---|---|---|
| **cgroup 计数（权威）** | `wc -l < /sys/fs/cgroup/apps/uid_10301/pid_<appPid>/cgroup.procs` 再 −1 | 与 AMS 裁进程时读的**同一个文件**，等于它的 `mPhantomProcesses` 视图 |
| 粗计数（仅交叉校验） | `ps -A -o USER,… \| grep -c u0_a301` | 含 App 自身；进程频繁生死时会漏读（实测出现过 `ps=17` 而 cgroup=50 的瞬时快照），且会把 `run-as` 自己拉起的同 uid 进程算进去 |
| 自报（独立第三源） | guest 脚本每 15s 打印 `GUEST_ALIVE=n/48`；App 每 30s 打印 `[HEARTBEAT] … prootAlive=… guestAlive=n/48`（用 `kill(pid,0)` 复核） | 从 App/guest 内部数存活，不经 adb 观测链 |
| 杀手证据 | `logcat -b events \| grep am_kill` + `logcat \| grep -E 'phantom\|Trimming'` | `AM_KILL` 事件带 reason 原文 |

> 判定"存活曲线"以 **cgroup 计数**为主、**App 心跳自报**为准绳：guest 脚本自身的循环会派生 1~2 个瞬时子进程（`cat`/`kill`/`date`），cgroup 读数因此有 ±2 的抖动；"某轮全存活"的结论以"proot 存活 + 心跳里 `guestAlive=48/48` 贯穿整窗"为准。
> 另注：本机（MagicOS）的 `logcat` 对普通 App 日志有过滤，`logcat`/`logcat -c` 不可靠；App 侧证据一律以 `run-as <pkg> cat files/spikea.log` 落盘文件为准（AM 系统日志不受影响）。

### 2.4 触发时机：收割不是"一超编就杀"，而是挂在 AMS 的 5 分钟节拍上

两处 gate 决定了"什么时候"被杀，三轮默认配置的实测口径（+29s / +43s / +267s）正是这条节拍的体现：

1. **扫描节拍 = 5 分钟**（`services/core/java/com/android/server/am/ActivityManagerService.java:15192`）：

```java
    case CHECK_EXCESSIVE_POWER_USE_MSG: {
        checkExcessivePowerUsage();
        removeMessages(CHECK_EXCESSIVE_POWER_USE_MSG);
        Message nmsg = obtainMessage(CHECK_EXCESSIVE_POWER_USE_MSG);
        sendMessageDelayed(nmsg, mConstants.POWER_CHECK_INTERVAL);   // DEFAULT_POWER_CHECK_INTERVAL = 5 * 60*1000
    }
```
该消息在开机时首次投递、此后每 5 分钟一次；`checkExcessivePowerUsage()` → `updateCpuStatsNow()`：

```java
    // AppProfiler.java:2169
    void updateCpuStatsNow() {
        final boolean monitorPhantomProcs = mService.mSystemReady && FeatureFlagUtils.isEnabled(
                mService.mContext, SETTINGS_ENABLE_MONITOR_PHANTOM_PROCS);
        ...
            if (MONITOR_CPU_USAGE && mLastCpuTime.get() < (now - MONITOR_CPU_MIN_TIME)) { ... haveNewCpuStats = true ... }
            if (monitorPhantomProcs && haveNewCpuStats) {
                mService.mPhantomProcessList.updateProcessCpuStatesLocked(mProcessCpuTracker);   // ← 这里才扫 app cgroup、建幻影记录、排 trim
            }
```

2. **flag=false 连"扫描/记帐"都不做**（不是只跳过 kill）：上句 `monitorPhantomProcs` 为假时 `updateProcessCpuStatesLocked()` 根本不调用，于是 AMS 侧连 PhantomProcessRecord 都不会建——本机 B 轮 35 分钟里 `dumpsys activity processes | grep -c PhantomProcessRecord` 恒为 1（那是别的系统进程），而默认配置的三轮里它会涨到 31~51。

**实践含义**：默认配置下，超额子进程最长可以活 **~5 分钟**（下一个节拍到来前），然后**整批**被裁；因此"缓解是否必要"的对照实验窗口必须 ≥1 个节拍（本报告 B/B1/B2 用 420s、B 用 2100s，A/A2 用 600s）。

---

## 3. 各轮结果

探针：App 经 proot 在 guest 内派生 **48 个 `busybox sleep 3600`**（+1 个 `busybox sh` 宿主脚本 +1 个 proot tracer = **50 个非 App 进程**），每 15s 自报存活数；宿主脚本按 §2.3 采样。

### 3.1 对照轮 A（缓解关闭 = 真默认）

设置：`settings_enable_monitor_phantom_procs=null`、`device_config max_phantom_processes=null`（dumpsys 显示 32）、两个 m0 旧键已清除。

观察窗 600s（App 内 `durationSec=720`），采样 20s 一次：

> 另有更早的一轮同配置"冒烟轮"（`dist/logs/phantom-A0-smoke-*`，窗口 120s）：它在 spawn 后 **+43s** 成批裁掉 19 个（18×busybox + 1×libproot.so），与本节 A 轮行为一致；该轮同时是 §2.4 节拍结论的第三次采样点。

| +s | 时刻 | cgroup 子进程 | busybox | proot | guest 自报 |
|---:|---|---:|---:|---|---|
| 0 | 20:43:21 | 51 | 49 | 7386 | GUEST_SPAWNED=48 |
| 20 | 20:43:42 | 51 | 50 | 7386 | GUEST_ALIVE=48/48 |
| 40 | 20:44:02 | **31** | 31 | **死亡** | PROOT_EXIT=137 after 29s |
| 60 | 20:44:22 | 31 | 31 | 死亡 | — |
| 120 | 20:45:21 | 31 | 31 | 死亡 | — |
| 300 | 20:48:22 | 31 | 31 | 死亡 | — |
| 600 | 20:53:22 | 30 | 30 | 死亡 | — |

（完整 31 行见 `dist/logs/phantom-A-samples.csv`；表内 `cgroup 子进程` = `/sys/fs/cgroup/apps/uid_10301/pid_<appPid>/cgroup.procs` 行数 −1。）

杀手原文（`logcat -b events`，`dist/logs/phantom-A-am_kill.txt`）——本轮恰逢一个 5 分钟节拍落在 spawn 完成之后（§2.4；spawn 实测只需 ~2-4s），**20 条同批次 kill，全部 reason 相同**：

```
09-15 20:43:50.843  3125  3213 I am_kill : [0,7386,libproot.so,0,Trimming phantom processes,3860]
09-15 20:43:50.843  3125  3213 I am_kill : [0,7403,busybox,0,Trimming phantom processes,3564]
...（19×busybox + 1×libproot.so，同一毫秒级批次）
09-15 20:50:30.112  3125  3213 I am_kill : [0,7443,busybox,0,Trimming phantom processes,3584]
```

对应的 logcat main 侧原文（`dist/logs/phantom-A-killer-lines.txt`）：

```
09-15 20:43:50.843  3125  3213 I ActivityManager: Killing PhantomProcessRecord {9fbebbf 7386:7259:libproot.so/u0a301}: Trimming phantom processes
09-15 20:43:50.843  3125  3213 I ActivityManager: Killing PhantomProcessRecord {6d1c28c 7403:7259:busybox/u0a301}: Trimming phantom processes
09-15 20:43:50.845  3125  3215 I ActivityManager: Process PhantomProcessRecord {9fbebbf 7386:7259:libproot.so/u0a301} died
```

应用侧落盘日志（`dist/logs/phantom-A-app-section.log`）：

```
[GUEST] GUEST_SPAWNED=48
[GUEST] GUEST_ALIVE=48/48 ts=1789476201
[GUEST] GUEST_ALIVE=48/48 ts=1789476218
[PHANTOM] PROOT_EXIT=137 after 29s        ← proot tracer 被 SIGKILL（137 = 128+9）
```

要点：**App 主进程没被杀**（`app=7259` 全程存活），被杀的是它的子孙；proot tracer 一死，guest 存活上报立刻中断，剩余 31 个 `busybox` 变孤儿继续挂着（`ps` 里 PPID=1），并且 `am force-stop` 收不掉它们。

### 3.2 缓解轮 B（两条命令都开）

设置：`settings put global settings_enable_monitor_phantom_procs false` + `device_config put activity_manager max_phantom_processes 2147483647`（两条命令 `exit=0`、无任何输出告警；`dumpsys activity settings` 立刻显示 `max_phantom_processes=2147483647`）。

观察窗 **2100s（35 分钟）**，采样 13 点（前 90s 密采 + 之后每 5 分钟）：

| +s | 时刻 | cgroup 子进程 | busybox | proot | guest 自报 |
|---:|---|---:|---:|---|---|
| 0 | 20:53:35 | 51 | 50 | 12452 | GUEST_SPAWNED=48 |
| 15 | 20:53:51 | 51 | 50 | 12452 | GUEST_ALIVE=48/48 |
| 30 | 20:54:04 | 51 | 50 | 12452 | GUEST_ALIVE=48/48 |
| 45 | 20:54:20 | 51 | 50 | 12452 | GUEST_ALIVE=48/48 |
| 60 | 20:54:34 | 51 | 50 | 12452 | GUEST_ALIVE=48/48 |
| 90 | 20:55:04 | 51 | 50 | 12452 | GUEST_ALIVE=48/48 |
| 390 | 21:00:04 | 51 | 50 | 12452 | GUEST_ALIVE=48/48 |
| 690 | 21:05:04 | 51 | 50 | 12452 | GUEST_ALIVE=48/48 |
| 990 | 21:10:04 | 51 | 50 | 12452 | GUEST_ALIVE=48/48 |
| 1290 | 21:15:04 | 51 | 50 | 12452 | GUEST_ALIVE=48/48 |
| 1590 | 21:20:05 | 51 | 50 | 12452 | GUEST_ALIVE=48/48 |
| 1890 | 21:25:05 | 51 | 50 | 12452 | GUEST_ALIVE=48/48 |
| 2100 | 21:28:35 | 51 | 50 | 12452 | GUEST_ALIVE=48/48 |

- **35 分钟零衰减**：51 个子进程、proot 与 48 个 guest 长命进程全存活；`[HEARTBEAT] … prootAlive=true guestAlive=48/48` 每 30s 一条不断（`dist/logs/phantom-B-app-section.log`）。
- **窗口内 `Trimming phantom` 零命中**：整轮 logcat 里 `grep -c "Trimming phantom"` = **0**；`am_kill` 事件仅 5 条，全部是无关 App 的缓存回收（reason `empty #81`，如 `com.hihonor.magichome:widget`），没有一条属于本 App。
- 对照 A 轮：同一探针在 A 轮 **29 秒**内就被裁掉 20 个进程；B 轮 35 分钟一个不差。

### 3.3 复核轮 A2（再关回去）

设置：与 A 轮完全相同（flag/cap 均回默认、旧键清除）。观察窗 600s，采样 20s 一次：

| +s | 时刻 | cgroup 子进程 | busybox | proot | guest 自报 |
|---:|---|---:|---:|---|---|
| 0 | 21:30:05 | 51 | 50 | 23336 | GUEST_SPAWNED=48 |
| 60 | 21:31:04 | 51 | 50 | 23336 | GUEST_ALIVE=48/48 |
| 180 | 21:33:05 | 51 | 50 | 23336 | GUEST_ALIVE=48/48 |
| 260 | 21:34:25 | 51 | 50 | 23336 | GUEST_ALIVE=48/48 |
| 280 | 21:34:44 | **31** | 31 | **死亡** | PROOT_EXIT=137 after **267s** |
| 600 | 21:40:05 | 31 | 31 | 死亡 | — |

- 本轮**不是**在 spawn 后 29s 被杀，而是等到 **+267s** 的第一个节拍（§2.4）才成批裁掉 —— 与 A 轮同为默认配置、行为一致（都在 ≤1 个节拍内），只是节拍落点不同。这解释了三轮"延迟"差异，也说明**对照轮必须跨过节拍**。
- 本轮的 20 条 trim 里有一条 **不属于本 App**：`com.hypergryph.skland` —— 直接实证了"**配额是全系统的**"（当时系统幻影 = 本 App 51 + skland 1 = 52 > 32，从最易杀的尾部开始裁；**52 − 20 = 32** 正好等于配额）：

```
09-15 21:34:32.126  3125  3213 I am_kill : [0,13684,com.hypergryph.skland,0,Trimming phantom processes,45404]
09-15 21:34:32.126  3125  3213 I am_kill : [0,23336,libproot.so,0,Trimming phantom processes,4648]
09-15 21:34:32.127  3125  3213 I am_kill : [0,23377,busybox,0,Trimming phantom processes,3956]
（共 20 条：18×busybox + 1×libproot.so + 1×skland）
```

### 3.4 可分性轮 B1 / B2（哪条是决定性的）

| 轮 | 设置 | 窗口 | 结果 | 关键旁观指标 |
|---|---|---|---|---|
| **B1** | 只 `settings put … monitor_phantom_procs false`（cap 保持默认 32） | 420s（跨 ≥1 节拍） | **全存活**：cgroup 恒 51、proot 存活、guest `48/48` 到 `uptimeSec=420`；trim 0 次 | `dumpsys` 里本 App 的 PhantomProcessRecord **恒为 0** —— flag=false 时 AMS 连"扫 cgroup / 建记录"都不做（§2.4 代码） |
| **B2** | 只 `device_config put … max_phantom_processes 2147483647`（flag 保持默认 true） | 420s（跨 ≥1 节拍） | **全存活**：proot 存活、App 心跳 `guestAlive=48/48` 到 `uptimeSec=420`；trim 0 次（cgroup 读数在中段由 51 变 49，属瞬时候选进程抖动，见 §2.3 注） | PhantomProcessRecord 涨到 **50** —— 扫描/记帐照常，只有 `MAX(INT_MAX) < size` 恒假，于是不裁 |

结论：**两条命令各自单独即可**，但作用层不同——flag 关掉的是"监控+记帐+裁剪"整条链，cap 关掉的只是"裁剪"这一步。因此：
- 若未来某 ROM 拒绝 `device_config put`（DeviceConfig 服务受限），只写 flag 一条就够了（本机实测 B1 通过）。
- 若未来某 ROM 的 flag 键被改名/失效，只写 cap 也够（本机实测 B2 通过）。
- 两条都写 = 双保险（也是 m0 与生产的既有形态）。

### 3.5 旧键轮 L（m0 时代写入的两个旧键是否有效）

设置：把实验开始时见到的 m0 残留键原样写回（`settings_config_disable_monitor_phantom_procs=true`、`phantom_process_killer_enable=false`），而 **flag/cap 保持默认**。

结果：**开跑约 2 秒即被裁剪** —— proot tracer 被 SIGKILL（`PROOT_EXIT=137 after 1s`），20 条 kill（18×busybox + 1×libproot.so + 1×`com.hypergryph.skland`），全程 `Trimming phantom processes`：

```
09-15 21:54:44.267  3125  3213 I am_kill : [0,10999,libproot.so,0,Trimming phantom processes,4404]
```

**判定：这两个键在 Android 16 上对幻影查杀无效**（AOSP 16 里搜不到这两个键名，`FeatureFlagUtils` 只认 `settings_enable_monitor_phantom_procs`）。它们只是 m0 时代 上游 fork 的 `PermissionGrantHelper.disablePhantomProcessKiller()` 写入的历史遗留（`m0-archive 里的 fork 基线/…/PermissionGrantHelper.kt:137-141`），在 Android 16 上已成空文。**生产只需两条现代命令**——顺带说明：实验结束时这两个旧键已被恢复成原值（见 §6 结束状态）。

---

## 4. 结论

1. **Spike C 判据达成**：路线图 v3 阶段〇 的"幻影进程查杀关闭真机复验必须过"——**过**。默认配置下确实会被收割（20 条 `Trimming phantom processes` 实证，proot tracer SIGKILL、进程数被压到配额 32 附近）；写入 m0 产品化的两条命令后，48 个 guest 长命进程 + proot + sh 在 35 分钟里零伤亡。
2. **哪条是决定性的**：本机两条**各自独立生效**（B1/B2 双通过），区别在作用层：
   - `settings put global settings_enable_monitor_phantom_procs false` → 关掉"监控+记帐+裁剪"整条链（AMS 连幻影记录都不建）；**推荐作为主手段**（一条命令、语义最彻底、即使 DeviceConfig 受限也有救）。
   - `device_config put activity_manager max_phantom_processes 2147483647` → 只把裁剪阈值顶到天花板（记录照建、只是不裁）；作为副手段。
   - 两者都写 = 双保险，也是 m0/生产既有形态，保持。
3. **只写旧键（`settings_config_disable_monitor_phantom_procs` / `phantom_process_killer_enable`）没用** —— 在 Android 16 上仍是"开跑 2 秒即被裁"。
4. **收割不是即时的**：挂在 AMS 的 `CHECK_EXCESSIVE_POWER_USE_MSG`（`POWER_CHECK_INTERVAL=5min`）节拍上；超额子进程最长能活 ~5 分钟，然后整批被 SIGKILL。任何"存活验证"窗口必须 ≥1 个节拍，否则会把"还没到节拍"误判成"没被杀"（本 spike 一开始就差点踩：A 轮 +29s 被杀、A2 轮 +267s 才被杀，同为默认配置）。
5. **配额是全系统的**：A2 轮实测连坐杀掉了 `com.hypergryph.skland` 的幻影（52 − 20 = 32 与配额吻合）。系统里其它 App 的幻影会挤占本 App 的 32 预算 —— 这也是"两条都写"更稳的理由之一。

---

## 5. 对阶段三 / 生产的注意事项

1. **重启持久性未测**（路线图归阶段三）：`device_config put` 属于 DeviceConfig，**可能随重启/系统更新复位**；`settings put global` 通常持久但 ROM 可能重置。生产形态必须让主 App 每次启动（或开机后首次运行）经 shizuku-m 幂等重设这两条命令，并把回读（`device_config get` / `settings get`）作为自检项。
2. **写入者权限**：本机实测 adb shell（uid 2000）可写；m0/v3 走 Shizuku（shell uid）同权。若未来某 ROM 拒绝 `device_config put`，退路是 `settings put global settings_enable_monitor_phantom_procs false` 单独生效（本机实测单独即可）。
3. **配额是全局的**：`max_phantom_processes` 不是每 App 配额，而是全系统幻影进程总数的上限；其它 App（如厂商预装/游戏助手）的幻影会挤占预算。把配额顶到 INT_MAX（或关掉总闸）后这条争抢才消失。
4. **被裁的是整棵树**：AMS 对每个命中者执行 `Process.killProcessQuiet(pid)` + `killProcessGroup(uid, pid)`。实测中 proot tracer 被 SIGKILL → guest 侧 48 个长命进程里有 19 个陪葬、其余 31 个变孤儿（PPID=1）且不会被 `am force-stop` 回收（它们已不在 app cgroup）。**前台服务/进程管理模块要假设"子进程随时可能整批消失"**，必须有存活探测 + 重建逻辑（好消息：48 个进程的派生实测只需 ~2-4 秒——busybox applet 是 fork 复用、不 exec，重建整树很便宜）。
5. **guest rootfs 必须有可用的 `/dev/null`**：busybox ash 会把每个后台作业的 stdin 指向 `/dev/null`（O_RDONLY，不创建），缺它时后台进程全灭（见 `debug.md`）。生产 rootfs 用 `proot -b /dev:/dev`（或 `/dev/null`）绑定宿主设备节点，不要用普通文件冒充。
6. **不要靠 `ps` 判断存活**：应在 App 内维护子进程句柄/pid 表 + `kill(pid,0)` 探测（本 spike 的 `[HEARTBEAT]` 即该形态的最小实现），并对 proot tracer 单独做存活检查。
7. **屏幕/前台状态**：本 spike 全轮保持亮屏 + App 前台（`stay_on_while_plugged_in=2`），保证 A/B 对照条件一致；后台熄屏场景下的行为（Doze、缓存冻结）留给阶段三的前台服务实测。

---

## 6. 复现步骤

```bash
# 0) 构建（沿用 Spike A 的便携工具链与 gradle home）
export JAVA_HOME=/d/VSCodeCache/shizku-m/build-env/jdk-21.0.2
export GRADLE_USER_HOME="D:/VSCodeCache/azurpilot-azurpilot/.tmp/spike-a/gradle-home"
cd spike/a-proot-exec
./gradlew --no-daemon -Pspike.targetSdk=35 :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk dist/spikea-phantom-target35-debug.apk

# 1) 安装 + 逐轮跑（A/B/A2/B1/B2/L，每轮自采样本、自收证据）
adb install -r "$(cygpath -m dist/spikea-phantom-target35-debug.apk)"
bash run-phantom-ab.sh A 600      # 对照：默认（≤10 min 观察）
bash run-phantom-ab.sh B 2100     # 缓解：两条命令（≥30 min 观察）
bash run-phantom-ab.sh A2 600     # 复核：再关回去
bash run-phantom-ab.sh B1 420     # 只开 flag=false
bash run-phantom-ab.sh B2 420     # 只开 cap=INT_MAX
bash run-phantom-ab.sh L 420      # 只开 m0 旧键
bash run-phantom-ab.sh final      # 结束状态：两条缓解命令保持开启

# 2) 单轮手工复现（不跑脚本）
adb shell "am start -n com.azurpilot.spikea/.MainActivity --es mode phantom --ei count 48 --ei durationSec 600"
watch -n5 'adb shell "wc -l < /sys/fs/cgroup/apps/uid_10301/pid_\$(ps -A -o PID,NAME | awk \"\$2==\\\"com.azurpilot.spikea\\\"{print \$1}\")/cgroup.procs"'
adb logcat -b events -d | grep am_kill
```

### 实验结束状态（本机实际）

| 项 | 结束值 | 说明 |
|---|---|---|
| `settings global settings_enable_monitor_phantom_procs` | `false` | **生产所需状态**（实验后保持开启缓解） |
| `device_config activity_manager max_phantom_processes` | `2147483647` | 同上 |
| `settings global settings_config_disable_monitor_phantom_procs` | `true` | 恢复为实验前原值（无效旧键，仅还原原状） |
| `settings global phantom_process_killer_enable` | `false` | 同上 |
| `settings global stay_on_while_plugged_in` | 0（实验期间为 2） | 实验期亮屏所需，已还原 |
| 设备上临时物 | 删除 `/data/local/tmp/phantom-sampler.sh`；App 已 `am force-stop` | 保留 `com.azurpilot.spikea` 安装（后续可复用） |

---

## 7. 交付物与未覆盖项

交付物：

| 文件 | 内容 |
|---|---|
| `spike/a-proot-exec/app/src/main/java/com/azurpilot/spikea/MainActivity.kt` | 新增 PHANTOM 模式（`--es mode phantom --ei count N --ei durationSec S`） |
| `spike/a-proot-exec/run-phantom-ab.sh` | 逐轮编排：设置切换、清场、采样、证据落盘 |
| `dist/spikea-phantom-target35-debug.apk` | 新版 APK（versionCode 2） |
| `dist/logs/phantom-<round>-samples.csv` | 每轮采样表（cgroup/ps/自报/AMS 数） |
| `dist/logs/phantom-<round>-am_kill.txt` / `-killer-lines.txt` | 杀手行为原文 |
| `dist/logs/phantom-<round>-app-section.log` | 该轮 App 落盘日志（guest 自报、PROOT_EXIT、心跳） |
| `dist/phantom-settings-history.txt` | 每轮设置变更流水（含命令 exit code 与输出） |

未覆盖项（留阶段三/五）：

1. 重启 / 系统更新后设置是否复位（阶段三：每次开机幂等重设）。
2. 熄屏 + Doze + 后台冻结组合下的存活（阶段三前台服务）。
3. 多 ROM 抽检（HyperOS/ColorOS，阶段五）；本机 MagicOS 10 上两条命令均无告警。
4. 真实 Ubuntu rootfs + Python 整树（阶段一产物）在同一口径下的复验——本 spike 的编排脚本可直接复用。
