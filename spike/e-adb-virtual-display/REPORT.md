# Spike E · adb 直控虚拟显示屏（B′ + E 合并报告）

> 执行日期：2026-09-15 夜（22:15–22:33 真机实验窗口）
> 设备：HONOR PPG-AN00（Android 16 / SDK 36 / MagicOS 10.0.0.160 / SELinux Enforcing / 4KB 页）
> 通道：USB `AVAY025422002864` + TCP `192.168.50.190:5555`（shizuku-m 拉起 adbd）；adb 37.0.0-14910828
> 相关：`docs/roadmap-v3.md` 阶段〇 Spike E；靶子 = `m0-archive/docs/debug.md:281-285` §41

---

## 0. 结论速览

| 项 | 结论 | 证据 |
|----|------|------|
| B′（`tcpip:5555` connect + shell 域） | **PASS** | `adb connect` 秒连；`shell uid=2000(shell) context=u:r:shell:s0`；USB/TCP 双通道并存 |
| E0 能力清点 | 完成 | `cmd display` 无建屏动词；`screencap -d` / `input -d` 均存在但 **ID 命名空间不同** |
| E1 建虚拟屏（shell 域） | **PASS** | scrcpy-server v4.1 `new_display=1280x720/160`，owner=`com.android.shell (uid 2000)`；逐字配方见 §3 |
| E2 屏可见 + 投 App | **PASS** | `dumpsys` 见 VD（displayId/group/flags 全）；`am start -W --display <id>` Status ok |
| E3 截屏 | **PASS**（关键纠正） | `screencap -p -d <SF physical id>` 拿回真实 1280×720 画面（Settings UI、壁纸、自研 App UI 三种证据图） |
| E4 注入输入 | **PASS** | `input -d <logical id>`：BACK 令 App 从 VD 任务栈消失、滑动令 Settings 列表滚动（前后截图） |
| E5 复核 m0 §41 | **两条负面记录均被推翻** | §41 的 `screencap` 失败是 ID 命名空间错用；`input` 在"VD 可见且有 resumed 窗口"时确实生效 |
| E6 scrcpy 视频通路（可选） | PASS（sanity） | h264 1280×720 关键帧+P 帧实测，见 §6 |
| **VD 可见性保活** | **shell 域做不到** | framework 的 per-display-group 电源请求把 VD 熄掉后，`cmd display power-reset` / `requestDisplayPower(id, ON)` 都无法恢复画面（仅恢复窗口 surface，ColorFade 仍盖黑） |

**对 v3 的判定**：**adb 直控虚拟屏技术上成立（能力齐备），但它不能"甩掉" m0 桥** —— m0 桥的价值是"不依赖 adb 连接、进程归 App 自己管"；adb 侧新增的两个真实约束是：

1. **VD 的"点亮/可见性"是 framework 内部路径**（WM → DMS 的 display-group 电源请求），shell 域无等价入口；VD 被熄灭后纯 adb 侧无法把它重新点亮（只能重建）。本次实测：VD 创建后能正常渲染（设备处于交互态时），随后 1~2 分钟内被 framework 翻成 OFF → 变黑。
2. **adb 直控链全悬在 adb 会话上**：`app_process` 由 `adb shell` 启动，adb 传输一断，server 与 VD 同时死亡（本次三块 VD 全部如此收场）。生产化必须有常驻+自愈宿主。

→ 建议 v3 控制面保持 **m0 桥为主**，把"adb 直控 VD（截屏/注入）"登记为**备用/诊断通道**；若阶段三要把它做成生产路径，需要额外设计"VD 保活/重建"策略与 adb 断线自愈（成本已列在 §7）。

---

## 1. B′ 通道（shizuku-m `tcpip:5555` + 一次性 RSA 授权）

- 用户已激活 shizuku-m，设备 adbd 监听 `*:5555`（`service.adb.tcp.port=5555`）；PC 侧 `adb connect 192.168.50.190:5555` 秒连。
- `adb devices -l`（22:15）：
  ```
  AVAY025422002864       device product:PPG-AN00 model:PPG_AN00 device:HNPPG transport_id:1
  192.168.50.190:5555    device product:PPG-AN00 model:PPG_AN00 device:HNPPG transport_id:2
  ```
- 身份：`uid=2000(shell) gid=2000(shell) groups=...,1004(input),1011(adb),... context=u:r:shell:s0`（本 spike 全程 shell 域，无 root）。
- **断线行为（本次实测，重要）**：22:32:4x 起 TCP 传输掉为 `offline`，**同时 USB 传输从 `adb devices` 消失**（用户本人当时正在使用手机、真屏已亮并在滑屏，疑似顺手拔了 USB）。恢复尝试全部失败：
  ```
  adb disconnect/connect → already connected / failed to connect
  adb kill-server + start-server + connect → failed to connect (10061 目标计算机积极拒绝)
  adb reconnect offline → 传输被清空，再 connect 仍被拒
  ping 192.168.50.190 → 0% 丢包（网络层正常）
  adb mdns services → 无发现
  ```
  → 与路线图决策 #11 一致：**5555 消失后需用户在 shizuku-m 点"离线自连"**。本报告末尾的"设备侧清场"因此 **BLOCKED**（§8）。

---

## 2. E0 · 能力清点（原样输出见 `logs/e0-capability.txt`）

- `cmd display`（DisplayManagerService 的 shell 接口）：`help / show-notification / cancel-notifications / set-brightness / ab-logging-* / dwb-* / dmd-* / set-user-preferred-display-mode / get-user-preferred-display-mode / get-displays / dock / undock / enable-display / disable-display / power-reset / power-off` —— **没有任何"创建虚拟屏"动词**（`enable-display` 只对 connected display 有效）。
- `cmd display get-displays -i` → `0`（只有真屏；实验前基线）。
- `dumpsys SurfaceFlinger --display-id` → `Display 4630947145909893267 (HWC display 0): port=147 pnpId=QCM`（唯一显示）。
- `screencap --help` 全文要点（Android 16）：
  ```
  usage: screencap [-ahp] [-d display-id] [FILENAME]
     -a: captures all the active displays ... If both -a and -d are given, it ignores -d.
     -d: specify the display ID to capture (If the id is not given, it defaults to 4630947145909893267)
         see "dumpsys SurfaceFlinger --display-id" for valid display IDs.
     -p: outputs in png format.
     --hint-for-seamless If set will use the hintForSeamless path in SF
  ```
  → **`-d` 吃的是 `dumpsys SurfaceFlinger --display-id` 的 ID（int64，物理/SF 命名空间）**，这行 help 就是本 spike 破案的关键。
- `input`（无 `--help`，裸跑打印 usage）：
  ```
  Usage: input [<source>] [-d DISPLAY_ID] <command> [<arg>...]
  -d: specify the display ID.
        (Default: -1 for key event, 0 for motion event if not specified.)
  ```
  → **`input -d` 吃 logical display id（int32）**，与 `screencap` 不是一个命名空间（§4 有实证）。

---

## 3. E1 · 建虚拟屏（scrcpy-server 主路径）——**生产候选配方（逐字）**

### 3.1 取件与校验

```bash
# 最新稳定版 v4.1（2026-07-12 发布）
curl -sSL -o scrcpy-server-v4.1.jar \
  https://github.com/Genymobile/scrcpy/releases/download/v4.1/scrcpy-server-v4.1
# 校验（与同页面 SHA256SUMS.txt 一致）
sha256sum scrcpy-server-v4.1.jar
# deacb991ed2509715160ffdc7907e47b4160eb30d1566217e9047fd5b8850cae  (733706 bytes)

adb -s 192.168.50.190:5555 push scrcpy-server-v4.1.jar /data/local/tmp/scrcpy-server-v4.1.jar
adb -s 192.168.50.190:5555 shell 'sha256sum /data/local/tmp/scrcpy-server-v4.1.jar'   # 同一 sha256
```

### 3.2 启动（逐字，能工作的完整命令）

```bash
# 1) 端口转发（scid 缺省 = -1 → 抽象 socket 名固定为 "scrcpy"）
adb -s 192.168.50.190:5555 forward tcp:27183 localabstract:scrcpy

# 2) 设备侧起 server（tunnel_forward=true → server 建 LocalServerSocket 等客户端接入）
adb -s 192.168.50.190:5555 shell \
  'CLASSPATH=/data/local/tmp/scrcpy-server-v4.1.jar app_process / com.genymobile.scrcpy.Server 4.1 \
   log_level=debug tunnel_forward=true video=true audio=false control=false \
   new_display=1280x720/160 power_on=false cleanup=false'

# 3) PC 侧必须有一个"保持连接"的客户端（否则 server 退出、VD 随之消亡）
#    .tmp/spike-e/hold_client.py 27183 <dump.bin>  —— 只做 connect+recv 保活，不解析协议
python hold_client.py 27183 video_dump.bin
```

设备侧成功日志（原样）：

```
[server] INFO: Device: [HONOR] HONOR PPG-AN00 (Android 16)
[server] DEBUG: Using video encoder: 'c2.qti.avc.encoder'
[server] DEBUG: Video codec size alignment requirement: 2px
[server] INFO: New display: 1280x720/160 (id=9)
```

参数说明（本配方里每一个都是有意选的）：

| 参数 | 值 | 理由 |
|------|----|------|
| 位置参数 1 | `4.1` | **必须**=客户端版本号，server 用它做版本校验；缺了直接 `IllegalArgumentException: Missing client version` |
| `tunnel_forward` | `true` | PC 侧主动连（配 `adb forward`），不依赖设备侧 `adb reverse` |
| `video` | `true` | `new_display` 必须有 video（VD 的输出 surface 就是编码器输入面）；`audio=false` 省事 |
| `control` | `false` | 本 spike 只用 adb 侧 `input`/`screencap`，不需要 scrcpy 控制通道 |
| `new_display` | `1280x720/160` | 建 1280×720/160dpi 虚拟屏（与 v3 目标分辨率一致） |
| `power_on` | `false` | **绝不动真屏**（scrcpy 的 power-on 分支只在 `displayId==0` 时触发，`new_display` 模式下 displayId=-1 本就不会触发；显式关掉是双保险） |
| `cleanup` | `false` | **必须**：默认 `cleanup=true` 会派生 `CleanUp` 辅助进程并 `unlinkSelf()` **删掉刚 push 上去的 jar**（踩坑记录 §9-①） |
| `log_level` | `debug` | 取证用；生产可降回 `info` |

### 3.3 生命周期事实

- **VD 从不独立存活**：它就是 server 进程建的屏，server 一死 VD 即消失（`cmd display get-displays -i` 回到只有 `0`）。三块 VD 的收场各有原因：
  1. VD id=9：PC 侧后台任务 600s 超时 → `adb shell` 被工具杀掉 → server 打印 `Terminated` → VD 消失（之后 `screencap -d <旧 physical id>` 报 `Display Id … is not valid`）。
  2. VD id=10：server 在运行约 138s 后结束（保活客户端观测到 `server closed after 553026 bytes / 138.6s`），**死因未取证**（当时 adb 尚在；疑似被设备侧清理或 socket 异常）；VD 随之消失。
  3. VD id=11：adb 传输掉线（22:32:4x）→ `adb shell` 会话被杀 → server 死、VD 死。
- 客户端断开同样会带走 VD（server 往 socket 写失败 → 退出）。**"保活"是这条通路的硬要求**，且**保活方不止一个**：PC 侧客户端 + adb 会话本身都要稳。

---

## 4. E2/E3/E4 · 屏信息、截屏、注入（逐项取证）

### 4.1 屏信息（VD id=9 那一轮，`logs/e2-display-verify.txt`）

```
$ adb shell dumpsys SurfaceFlinger --display-id
Display 4630947145909893267 (HWC display 0): port=147 pnpId=QCM displayName=""
Display 11529215049274064657 (Virtual display): displayName="scrcpy" uniqueId="virtual:com.android.shell,2000,scrcpy,7"

$ adb shell dumpsys display | grep DisplayDeviceInfo{"scrcpy"
DisplayDeviceInfo{"scrcpy": uniqueId="virtual:com.android.shell,2000,scrcpy,7",
  1280 x 720, ..., density 160, 160.0 x 160.0 dpi, touch VIRTUAL, type VIRTUAL,
  owner com.android.shell (uid 2000), ...
  FLAG_ROTATES_WITH_CONTENT, FLAG_PRESENTATION, FLAG_OWN_CONTENT_ONLY,
  FLAG_DESTROY_CONTENT_ON_REMOVAL, FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS,
  FLAG_TRUSTED, FLAG_OWN_DISPLAY_GROUP, FLAG_ALWAYS_UNLOCKED,
  FLAG_TOUCH_FEEDBACK_DISABLED, FLAG_OWN_FOCUS }
  DisplayInfo{"scrcpy", displayId 9, displayGroupId 1, ..., layerStack 9, ...}
```

- `displayId`（logical）= 9 / 10 / 11（每次重建都变）；SF physical id = `11529215049274064657` / `11529215049802775650` / `11529215047539119414`（也随重建变）。
- **m0 对照（2026-09-15 深夜勘误，原"几乎一致"表述有误）**：m0 的 `AzurPilotVirtualDisplay`（`VirtualDisplayManager.kt:178-202`）与 scrcpy 有**一处生死差异**：m0 源码显式 `VD_SYSTEM_DECORATIONS = false`（`:39` 定义、`:187-189` 分支不执行），**从不设** `SHOULD_SHOW_SYSTEM_DECORATIONS`，也不设 `ROTATES_WITH_CONTENT`；另多 `DEVICE_DISPLAY_GROUP` + `STEAL_TOP_FOCUS_DISABLED`。AOSP 对该 flag 的原文："virtual displays without this flag shouldn't show home, navigation bar or wallpaper"——它是本 spike 副作用事件（§10）的分水岭。「§41 失败与 flag 无关」仅对截屏/注入命名空间成立，**不能**推广到 SystemUI 行为。

### 4.2 投 App 上去

```
$ adb shell am start -W --display 9 -a android.settings.SETTINGS
Starting: Intent { act=android.settings.SETTINGS }
Status: ok
LaunchState: WARM
Activity: com.android.settings/.HWSettings
$ adb shell dumpsys activity activities | grep -E 'Display #|state='
Display #9 (activities from top to bottom):
  state=RESUMED ...
```
（注意：若该 App 的任务已在真屏，`--display` 会把**任务搬过去**——这是副作用，实验里用自研 App 时无此问题。）

### 4.3 截屏：ID 命名空间是生死点

```
# ❌ 用 logical id（m0 §41 当年就是这么做的）
$ adb shell 'screencap -p -d 9 /data/local/tmp/vd.png; echo rc=$?'
Failed to take take screenshot. Display Id '9' is not valid.
Capturing failed.
rc=1

# ✅ 用 SF physical id（dumpsys SurfaceFlinger --display-id 的 ID）
$ adb shell 'screencap -p -d 11529215049274064657 /data/local/tmp/vd.png; echo rc=$?'
rc=0
# PNG 1280×720（5336B=全黑 / 43001B、978646B=真实画面）
```

拿到真实画面的三个证据（存 `logs/`）：

| 文件 | 内容 |
|------|------|
| `vd-id10-launcher-wallpaper.png`（978646B） | VD 上 HONOR 二级屏桌面（壁纸）——`screencap` 真在截 VD |
| `vd-id10-settings-before-swipe.png` / `...-after-swipe.png`（43001/42635B） | VD 上的「设置」列表：滑动**前/后** |
| `vd-id11-spikea-ui.png`（216065B） | VD 上运行我们自己的 SpikeA App（可控内容，复现用） |
| `vd-id9-black-frame.png`（5336B） | 反例：VD 被 framework 熄灭时的纯黑帧 |

`-a`（全部显示）实测只落了 `all.png` = 真屏内容（VD 不在 "active displays" 里）。

### 4.4 注入：`input -d` 用 logical id，且**确实生效**

命名空间实证（原样报错）：

```
$ adb shell 'input -d 11529215049802775650 tap 640 360; echo rc=$?'
Exception occurred while executing '-d':
java.lang.IllegalArgumentException: Error: Invalid arguments for display ID.
        at com.android.server.input.InputShellCommand.getDisplayId(InputShellCommand.java:184)
rc=255                      # int32 装不下 physical id → input 只吃 logical id

$ adb shell 'screencap -p -d 10 ...'
Failed to take take screenshot. Display Id '10' is not valid.     # 反向：screencap 不吃 logical id
```

效果实证（两个独立证据）：

1) **BACK 键改变 VD 任务栈**（`logs/e4-back-key-delivery.txt` 逐字转录）：
   - 注入前：`Display #9` 顶部任务 = `com.azurpilot.spikea/.MainActivity`，`state=RESUMED, visible=true, visibleRequested=true`
   - `adb shell 'input -d 9 keyevent 4'` → `rc=0`
   - 注入后：spikea 任务从 `Display #9` 消失，`com.hihonor.android.launcher/...SecondaryDisplayLauncher`（VD 的桌面）变为 `state=RESUMED`
2) **滑动滚动了 VD 上的设置列表**（`logs/e4-swipe-scroll.txt` + 前后截图）：
   - `adb shell 'input -d 10 swipe 640 550 640 150 500'` → `rc=0`
   - 前后帧 sha256 不同、列表内容位移（截图对比）

---

## 5. E5 · 复核 m0 §41（结论：两条负面记录都不成立）

m0 §41 的两句话与本次实测的对照：

| m0 §41 记录 | 本次实测 | 判定 |
|-------------|----------|------|
| 「`screencap -d <id>` 报 *Display Id is not valid*，**外部无法操纵/查看虚拟屏内容**」 | 用 **SF physical id** 可截到真实画面（三张证据图） | **推翻**：当年用了 logical id（Android 14+ 起 `screencap -d` 吃 physical/SF id） |
| 「`input -d <id> tap/keyevent` 全部返回成功却无任何效果」 | VD 处于"可见且有 resumed 焦点窗口"时，BACK/滑动**都有可验证效果** | **推翻**：当年更可能是"VD 处于熄灭/无焦点窗口"状态——此时事件没有可投递的目标，看起来像"静默忽略" |

机制解释（供 v3 参考，全部有 dumpsys 原文支撑）：

- **VD 的可见性由 display-group 电源请求决定**。scrcpy/m0 的 VD 都带 `FLAG_OWN_DISPLAY_GROUP` → 自成 group（dumpsys：`displayGroupId=1`），与真屏 group 0 解耦。`DisplayManagerService.requestPowerState(groupId, DisplayPowerRequest)` 的请求方是 **WindowManager**（AOSP `DisplayManagerService.java:5613`），shell 域没有等价入口。
- 请求为 OFF 时，链条是：DPC `mScreenState=OFF` → 屏幕被 **ColorFade 层**盖住 → SF 合成列表只剩 `1 Layers / Output Layer (ColorFade#…)` → `screencap` 得到纯黑；同时 WM 侧任务 `state=STOPPED / isSleeping=true`、窗口 `mHasSurface=false isOnScreen=false`（没有焦点窗口 → 注入无处可去）。
- 请求为 ON 时：`mHasSurface=true isReadyForDisplay()=true isOnScreen=true`，注入生效（§4.4）。
- **设备态关联**（本次两次对照）：
  - VD id=9 创建时设备 `mWakefulness=Dozing`（真屏 OFF）→ 建出来就是熄灭态（SF `powerMode=Off`），截屏黑；
  - VD id=10 / id=11 创建时设备 `mWakefulness=Awake`（真屏 ON）→ **创建即为 On**，截屏/注入全正常；
  - 但 VD id=10 在 1~2 分钟后**被 framework 自己翻成 OFF**（设备仍 Awake、真屏 BRIGHT，DPC 的 `mPowerRequest=policy=OFF`）→ 又变黑。
- shell 域能做的补救（都试过，记录在 `logs/e3c-*.txt`、`e3e-*.txt`、`e3f-*.txt`）：

| 手段 | 结果 |
|------|------|
| `cmd display power-reset <id>`（=`IDisplayManager.requestDisplayPower(id, STATE_UNKNOWN)`，AOSP `DisplayManagerShellCommand.java:620`） | 解析到"上次状态"→ 对熄灭的 VD **无效**（仍 Off） |
| `DisplayManagerGlobal.requestDisplayPower(<id>, STATE_ON)`（自研 `VDLab`，app_process+dex） | **真值返回**，SF `powerMode=On`、WM 恢复窗口 surface，**但 ColorFade 仍在 → 截屏仍黑** |
| `am start --display <id>` | 不会重新点亮已熄灭的 VD |
| 单纯 `input -d <id>` | 同上，不点亮 |

- 顺带发现（本 ROM）：`DisplayManagerGlobal` **没有** scrcpy 期望的 `requestDisplayPower(int, boolean)` 变体（只有 `(int, int)`）⇒ scrcpy 在 Android 15+ 的 `Device.setDisplayPower()` 路径在本机会抛 `NoSuchMethodException` 被静默吞掉（scrcpy 侧 `DisplayManager-wrapper.java:184-193`）。另：`SurfaceControl.getPhysicalDisplayIds()` 在本 ROM 也不存在（对应 scrcpy 的 Honor workaround，`Device.java:150-160`）——**HONOR 的显示栈确实有私货**。

---

## 6. E6 · scrcpy 视频通路 sanity（PASS，非必须）

同一 server 的视频流由保活客户端落盘（`logs/e6-video-stream.txt`）：三次运行都是 `h264 / 1280×720`，首个包 = IDR（31B+208B+SPS/PPS 39186B），静态画面 P 帧 ~116B：

| 运行 | VD | 设备态 | 字节数 / 包数 | 内容 |
|------|----|--------|---------------|------|
| run1 | id=9 | Dozing | 296817 / 227 | 黑帧（VD 熄灭） |
| run2 | id=10 | Awake | 553026 / 839 | 真实画面（壁纸/设置） |
| run3 | id=11 | Awake（复现） | 747988 / 337 | 真实画面（SpikeA UI） |

结论：scrcpy 的视频面本身没问题（阶段三若要"直接拿视频流当截图源"也可行），但**它不是本 spike 的必要件**——`screencap -d` 已经够用，且省一个编解码链。

---

## 7. 对 v3 的落地分析

**能力清单（shell 域）**：建 VD ✅ / 查 VD ✅ / 投 App ✅ / 截屏 ✅（physical id）/ 注入 ✅（logical id）/ 拿视频流 ✅ / **保活点亮 ❌**。

1. **能切 adb 吗？** 作为"截屏 + 注入"的数据通道：**能**（本 spike 全部打通），且不需要 m0 那套自研特权进程（scrcpy-server 一个 jar + 一个保活客户端即可）。
2. **能甩掉 m0 桥吗？不能**。m0 桥是 App 内进程、不依赖 adb 连接；adb 通路有两个硬约束：① VD 熄灭后纯 adb 无法点亮（只能重建）；② adb 直控链全悬在"adb 会话 + server 进程"上（VD 从不独立存活，本次三块 VD 的收场分别是任务超时、server 自灭、传输掉线）。要生产化必须补"常驻宿主 + 断线自愈 + VD 重建/保活"三件事，成本高于桥。
3. **建 VD 本身不需要 adb**：App/特权进程侧 `DisplayManager.createVirtualDisplay` 是 m0 已产品化的路径（flag 集与 scrcpy 等价，§4.1）——所以 v3 的"建屏"仍应留在宿主的 Shizuku 特权进程里。
4. **代价清单（若做备用通道）**：
   - 每次开机需 shizuku-m 激活 + `adb connect`（决策 #11 的 30 秒手动链，已有预期）；
   - rootfs 内置静态 aarch64 adb（路线图已列）+ 主 App 侧实现 `adb connect`/forward/保活客户端（约几十行）；
   - 手机侧常驻 `app_process` 需自愈监管（adb 断线 → 自动重连 → 重建 VD → 重投 App）；
   - 延迟指标留阶段五实测（AzurPilot 对 >1s 不可用线；桥有 m0 p50=0.109s 兜底）。
5. **建议**：控制面维持"桥为主"，本通路登记为**诊断/备用**；阶段五若要实测，按 §8 的复跑清单做。

---

## 8. 未决 / 待办 / 清场状态

**BLOCKED（通道）**：22:32:4x 起设备 adb 掉线（TCP `offline`、USB 消失、`adb connect` 被拒 10061、ping 正常）→ 需**用户在 shizuku-m 点"离线自连"**后继续。因此：

- **设备侧临时物未删**（通道恢复后执行）：
  ```
  adb shell rm -f /data/local/tmp/scrcpy-server-v4.1.jar /data/local/tmp/vdlab.jar \
      /data/local/tmp/ui_d9.xml /data/local/tmp/vd*.png /data/local/tmp/d0.png \
      /data/local/tmp/all.png /data/local/tmp/ok.png /data/local/tmp/pr*.png \
      /data/local/tmp/relight.png /data/local/tmp/repro*.png
  adb forward --remove tcp:27183
  adb shell 'cmd display get-displays -i'    # 期望只有 0
  adb shell 'ps -A | grep app_process'       # 期望空
  ```
  （`/data/local/tmp` 里另有 ~130 个 m0 时代的历史文件，**不属本次，未动**。）
- **已确认消失**：第一块 VD 在其 server 打印 `Terminated` 后消失（`screencap -d <旧 id>` 报 `Display Id … is not valid`）；第二块随 server 自灭消失（之后 `cmd display get-displays -i` 只见 `0`）；第三块随 adb 掉线消失。
- **宿主侧已清**：两个 server 后台任务、两个保活客户端均已结束；无残留进程；`adb forward --remove-all` 已执行（规则随传输死亡一并失效）。
- **纪律备注（如实记录）**：E0 能力清点时执行过一次 `input -d 0 tap 1 1`（仅为验证 `-d` 语法能否解析，坐标是屏幕左上角 (1,1) 状态栏死角）；当时真屏处于 OFF，无任何可观测影响，此后**再无任何 display 0 注入**。其余触及真屏的操作只有只读 dumpsys/screencap（截真屏那次见 `logs/display0-sanity-screencap.png`），以及后台的 `am start --display <VD>`（副作用是把已在真屏的 Settings 任务搬到 VD，见 §4.2 备注）。

**未测（留给后续）**：长稳（>10 min）、`screencap` 往返耗时基准（阶段五对比 1s 线）、`control=true` + scrcpy 自带 `start-app` 通道、`--hint-for-seamless`、多 VD 并存、`flex_display`。

**建议复跑清单（通道恢复 + 真屏亮 + 已解锁时）**：
1. §3.2 配方起 VD → `screencap -p -d <SF id>` 应立刻拿到非黑帧（验证"创建即可渲染"的稳定性）；
2. 记录 VD 从"亮"到"被 framework 熄灭"的时间与触发条件（本次观测 1~2 min，触发因素未定：疑似与真屏交互/超时有关）；
3. 测 screencap 往返耗时（10 次取 p50/p95）；
4. 若要评估"adb 备用控制面"，再补 `input` 的坐标精度与多指手势。

---

## 9. 新增坑点（同步进 `debug.md`）

1. **`cleanup=true`（默认）会删掉刚 push 的 scrcpy-server jar**：server 会派生 `CleanUp` 辅助进程，其 `main()` 第一件事是 `unlinkSelf()` 删 `SERVER_PATH`；之后同一 jar 的第二次 `app_process` 会以 `Aborted` + tombstone `No pending exception expected: java.lang.ClassNotFoundException: com.genymobile.scrcpy.Server` 收场（看起来像"设备坏了"，其实只是文件没了）。→ 用 `cleanup=false`，或每次都重 push。
2. **scrcpy-server 必须"有人连"才活着**：`tunnel_forward=true` 时 server 在 `accept()` 阻塞，客户端一断/从未连上，server 退出、VD 消亡。做实验要准备一个"只 connect+recv 的保活客户端"。
3. **`screencap -d` 与 `input -d` 是两个命名空间**：前者要 `dumpsys SurfaceFlinger --display-id` 的 int64 physical id（用 logical id 报 `Display Id 'N' is not valid`）；后者要 logical display id（给 physical id 直接 `IllegalArgumentException: Invalid arguments for display ID`）。
4. **VD 变黑 = framework 把该 display group 的电源请求置 OFF**（ColorFade 层盖屏），shell 域无解：`power-reset` 无效、`requestDisplayPower(id, ON)` 只恢复窗口 surface 不撤 ColorFade、`am start` 也不点亮。诊断口径：`dumpsys SurfaceFlinger | grep -A2 'Virtual Display <physicalId>'` 看 `powerMode`；`dumpsys display` 里该 displayId 的 DPC `mPowerRequest=policy=…`。
5. **HONOR 显示栈私货**：`DisplayManagerGlobal.requestDisplayPower(int,boolean)` 不存在（只有 `(int,int)`）、`SurfaceControl.getPhysicalDisplayIds()` 不存在 → scrcpy 的 Android 15+ 电源路径与 Honor workaround 分支在本机都会走空（静默失败/降级）。

## 10. 附：本 spike 用到的自制工具

- `.tmp/spike-e/hold_client.py`：scrcpy 保活客户端（connect + recv + 落盘，~40 行）。
- `.tmp/spike-e/vdlab/VDLab.java` → `vdlab.jar`（javac --release 8 + d8，纯反射）：
  - `VDLab methods`（列 `DisplayManagerGlobal` / `SurfaceControl` 的 power/display 方法）
  - `VDLab power <logicalId> <on|off>`（试 `requestDisplayPower` 的两种签名）
  - `VDLab sfpower <physicalId> <mode>` / `VDLab phyids`
  用法：`adb push vdlab.jar /data/local/tmp/ && adb shell 'CLASSPATH=/data/local/tmp/vdlab.jar app_process / VDLab power 10 true'`
  （构建配方：`javac --release 8 VDLab.java` → `d8 --lib <android.jar> --min-api 26 --output out VDLab.class` → zip 成 jar。）

---

## 12. 勘误与副作用事件补记（2026-09-15 深夜，主代理补）

> 本节由主代理在事件处置后补记：修正 §4.1/§7 的过时结论，并完整记录本 spike 最大的副作用事件。

### 12.1 勘误

1. ~~§4.1「m0 flag 集与 scrcpy 几乎一致」~~ → 已在 §4.1 就地修正：m0 显式不设 `SHOULD_SHOW_SYSTEM_DECORATIONS`，此为生死差异。
2. ~~§7「flag 集与 scrcpy 等价，§4.1」~~ → 同上勘误：v3 建屏 flag 以 m0 为准，**禁止** `SHOULD_SHOW_SYSTEM_DECORATIONS`。
3. §8「设备侧清场 BLOCKED」→ 已于 22:44 执行完毕：临时物（scrcpy-server jar / vdlab.jar / 设备侧证据图副本等）全删；`cmd display get-displays -i` 只剩 `0`；无 app_process 残留。证据图 6 张已留本地 `logs/`。

### 12.2 副作用事件：scrcpy VD 劫持主屏手势导航（本 spike 最大教训）

- **时间线**：22:15–22:33 实验窗口（三块 VD：id=9/10/11）；~22:35 用户报告主屏手势导航失效；22:41 主代理停掉实验子代理并 `kill 32246 32244`（scrcpy-server），VD 销毁；验证手势窗口回主屏、`get-displays` 只剩 0、无 scrcpy 进程；22:44 设备侧清场完成。
- **现象**：主屏边缘侧滑/上滑手势完全无响应；`dumpsys window windows` 显示 `GestureNavAnim`（`mDisplayId=9`）、`GestureSildeOut`（`mDisplayId=9`）、`NavigationBar0`（`mDisplayId=9`）——SystemUI（u0a10081）把手势导航三窗口建到了虚拟屏上（物证：`logs/e3c-power-reset.txt:19-32`）。
- **根因**：§3.2 配方建的 VD 带 `FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS`（scrcpy `--new-display` 默认行为，见 §4.1 flag 清单）。AOSP 原文："virtual displays without this flag shouldn't show home, navigation bar or wallpaper"。SystemUI 为带此 flag 的可信 VD 创建导航栏/手势窗口，MagicOS 10 的手势输入路由跟随这些窗口 → 主屏物理手势被投递到 1280×720 虚拟空间，表现为主屏手势"失灵"。
- **m0 为何不踩坑（代码级证据）**：`m0-archive 里的 fork 基线/.../VirtualDisplayManager.kt:39` `VD_SYSTEM_DECORATIONS = false`（`:187-189` 分支不执行）。**证据强度如实说明**：m0 项目此后长期暂停未用，"数月运行无事故"不成立；可信的是源码 flag 集与 AOSP 语义推导。
- **衍生约束（已落地）**：`debug.md` 同日 4 条坑点；`AGENTS.md`「虚拟屏实验纪律」（禁该 flag / 实验前后查手势窗口归属 / 清场标准）；`docs/roadmap-v3.md` 阶段二「VD flag 硬约束」+ 对 §41 证据措辞的修正。scrcpy 侧即使 `--no-vd-system-decorations` 也有被 ROM 忽略的公开记录（scrcpy#6684），不可作为兜底。
- **对 §5/§7 结论的影响**：无方向性变化。adb 直控登记为诊断/备用通道的判定叠加本事件风险项后更不改"桥为主"。
