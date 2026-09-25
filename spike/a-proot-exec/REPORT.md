# Spike A 报告 · APK 内 proot exec 真机实证

> 路线图 v3 阶段〇 · 唯一生死线探针（`docs/roadmap-v3.md` §2）
> 日期：2026-09-15 ｜ 设备：HONOR PPG-AN00（真机，`AVAY025422002864`）
> 证据文件：`dist/logs/`（原始 logcat + 落盘日志），APK：`dist/*.apk`

## 0. 结论（先给答案）

**PASS —— proot 路线在 targetSdk 35 上可行，不需要降级 targetSdk 28。**
targetSdk 28 变体同样全绿，可作为备选保留。

三条事实构成结论：

1. **nativeLibraryDir exec 可用**：`libproot.so`、`libbusybox.so`、`libproot-loader.so`、静态 shim 全部能从 `nativeLibraryDir` 直接 `execve`（含 debuggable=false 的 release 构建）。proot 的 ptrace 引擎正常驱动真进程（`PROOT_PTRACE_OK`）。
2. **客户机载荷在 app 私有目录可运行**：`proot -r <filesDir>/rootfs` 能运行 rootfs 内的 busybox（多合一，动态链接）、自定义静态 ELF、自定义动态 ELF，**包括客户机进程自己再 `execve` 子进程**（即 AzurPilot → python 子进程场景的同构探针）——在 targetSdk 35 下全部 PASS。
3. **唯一被系统拦的是"app 进程直接 execve 私有目录文件"**：targetSdk 35 下 `execve(filesDir/...)` 报 `error=13, Permission denied`（targetSdk 28 下允许）。proot 之所以不受影响：其客户机执行走 loader/解释器映射路径，不需要 app 对私有目录文件做 `execve`。

> 细节：探针里 `A1-busybox-exec`（直接 `libbusybox.so uname -m`）显示的 `exit=127 / applet not found` **不是** exec 被拦，而是 busybox 多合一程序按 `argv[0]` 选 applet、而文件名被改成 `libbusybox.so` 的用法性伪失败；用 argv0 shim 复跑（`A1c`）立即 PASS（输出 `aarch64`）。见 §3.4。

---

## 1. 实验对象与环境

### 1.1 设备事实（adb + 应用内双源）

| 项 | 值 | 来源 |
|---|---|---|
| 型号 / 厂商 | PPG-AN00 / HONOR（MagicOS） | `getprop ro.product.model` |
| Android / SDK | 16 / 36（security patch 2026-06-01） | `getprop ro.build.version.release/sdk` |
| 内核 | `6.6.89-android15-8-...-4k` | `uname -a` |
| 页面大小 | **4096**（4KB） | `getconf PAGE_SIZE` + 应用内 `Os.sysconf(_SC_PAGESIZE)` |
| SELinux | **Enforcing** | `getenforce`（adb shell；应用内读 enforce 被拒） |
| ABI | arm64-v8a | `getprop ro.product.cpu.abi` |
| 应用 SELinux 域 | targetSdk 35 → `u:r:untrusted_app:s0:...`；targetSdk 28 → `u:r:untrusted_app_27:s0:...` | 应用内读 `/proc/self/attr/current` |

### 1.2 二进制来源（Termux aarch64，含 SHA256 见 §7）

| 文件（jniLibs） | 来源包 | 版本 | 备注 |
|---|---|---|---|
| `libproot.so` | proot | 5.1.107.92 | Termux 补丁版（termux-exec 感知、shm helper、f2fs workaround） |
| `libproot-loader.so` | proot | 同上 | proot 的 loader（静态 ELF，guest 执行用） |
| `libtalloc.so` | libtalloc | 2.4.3 | DT_NEEDED 已由 `libtalloc.so.2` 改写成 `libtalloc.so` |
| `libandroid-shmem.so` | libandroid-shmem | 0.7 | proot 直接 NEEDED |
| `libbusybox.so` | busybox | 1.38.0-1 | **4KB stub 启动器**（非静态 busybox），DT_NEEDED 改写为 `libbusybox_app.so` |
| `libbusybox_app.so` | busybox | 同上 | 真正的 applet 载荷（876KB 共享对象，SONAME 仍是 `libbusybox.so.1.38.0`） |
| `libandroid-selinux.so` / `libpcre2-8.so` | libandroid-selinux 14.0.0.11-1 / pcre2 10.47 | | busybox 载荷的依赖链 |
| `libspike_shim.so` | 本次自建（NDK 静态） | | argv0 覆盖 shim，见 §3.4 |
| `hello_static` / `hello_dynamic` | 本次自建（NDK） | | rootfs 客户机探针（静态 / 动态链接） |

---

## 2. 阶梯结果总表

三份构建：`target35-debug`、`target28-debug`、`target35-release`（debuggable=false，验命名规则）。
每步判定：`PASS` = 退出码 0 且输出符合预期；`EXEC-FAILED` = `ProcessBuilder.start()` 抛 `IOException`（附 errno）；`FAIL(exit=n)` = 进程跑起来了但退出码非 0。

| 步骤 | 内容 | ts35-debug | ts35-release | ts28-debug |
|---|---|---|---|---|
| C1 | `/system/bin/sh` 控制组 | PASS | PASS | PASS |
| P0 | rootfs 文件模式清单 | PASS | PASS | PASS |
| A1 | `nld/libbusybox.so uname -m` | FAIL(127)※ | FAIL(127)※ | FAIL(127)※ |
| A1b | `nld/libbusybox.so id` | FAIL(127)※ | FAIL(127)※ | FAIL(127)※ |
| **A1c** | **shim 修正 argv0 后 exec busybox** | **PASS** | **PASS** | **PASS** |
| A2 | `nld/libproot.so --version` | PASS | PASS | PASS |
| A3 | `proot -0 /system/bin/sh -c '...'` | PASS | PASS | PASS |
| P1 | 直接 exec `rootfs/usr/bin/hello_static` | **EXEC-FAILED**（error=13） | **EXEC-FAILED** | PASS |
| P1b | 直接 exec `rootfs/bin/busybox`（动态） | **EXEC-FAILED**（error=13） | **EXEC-FAILED** | PASS |
| P1c | 直接 exec `rootfs/usr/bin/hello_dynamic` | **EXEC-FAILED**（error=13） | **EXEC-FAILED** | PASS |
| **A4** | `proot -r rootfs /bin/busybox uname -m` | **PASS** | **PASS** | **PASS** |
| A4b | `proot -r rootfs /usr/bin/hello_static` | PASS | PASS | PASS |
| A4e | `proot -r rootfs /usr/bin/hello_dynamic` | PASS | PASS | PASS |
| A5a | 客户机内 `execve`（静态子进程） | PASS | PASS | PASS |
| A5b | 客户机内 `execve`（busybox 子进程） | PASS | PASS | PASS |

※ argv[0] 用法性伪失败（见 §3.4），非 exec 被拦。A6（16KB 页）见 §4.4。

---

## 3. 关键证据（原样摘录，完整见 `dist/logs/*-final.log.txt`）

### 3.1 A0：nativeLibraryDir 清单（targetSdk 35 debug）

```
nativeLibraryDir=/data/app/~~-HVXnOUXmij9VHjQPfFZIw==/com.azurpilot.spikea-4-D7D-HMXpIfr3Mb2C4jLQ==/lib/arm64
applicationFlags=0x30a83e46 debuggable=true extractNativeLibs=true
pageSize=4096
processSelinuxContext=u:r:untrusted_app:s0:c45,c257,c512,c768
  nld| libandroid-selinux.so 179056 true
  nld| libandroid-shmem.so 14432 true
  nld| libbusybox.so 4320 true
  nld| libbusybox_app.so 876576 true
  nld| libpcre2-8.so 489384 true
  nld| libproot-loader.so 18136 true
  nld| libproot.so 244088 true
  nld| libspike_shim.so 2139176 true
  nld| libtalloc.so 31440 true
```

release（debuggable=false）构建 **9 个文件同样全部落地并被标记 canExecute=true** —— 证明 §4.1 命名规则在非 debuggable 安装下同样成立。

### 3.2 A2 / A3：proot 从 nativeLibraryDir 运行

```
[CMD ] libproot.so --version
[EXIT] 0
|  __ \  __ \_____  _____|   |_
|__|  |__|__\_____/\_____/\____| 5.1.107.92
built-in accelerators: process_vm = yes, seccomp_filter = yes

[CMD ] libproot.so -0 /system/bin/sh -c echo PROOT_PTRACE_OK; uname -m
[EXIT] 0
PROOT_PTRACE_OK
aarch64
```

### 3.3 P1 系列：targetSdk 35 直接 exec 私有目录 = 被拦（预期行为）

```
[CMD ] /data/user/0/com.azurpilot.spikea/files/rootfs/usr/bin/hello_static
[EXCEPTION] java.io.IOException: Cannot run program ".../hello_static": error=13, Permission denied
```

同一命令在 targetSdk 28 构建下：

```
[EXIT] 0
STATIC_GUEST_OK pid=23039 sysname=Linux machine=aarch64 release=6.6.89-android15-8-...
```

### 3.4 A1 / A1c：argv[0] 伪失败与 shim 修正

```
[CMD ] nld/libbusybox.so uname -m
[EXIT] 127
err| libbusybox.so: applet not found          ← busybox 把 argv[0] 当作 applet 名
```

修正探针（用自建静态 shim 把 argv[0] 覆写为 `busybox` 后再 exec 同一文件）：

```
[CMD ] nld/libspike_shim.so busybox nld/libbusybox.so uname -m
[EXIT] 0
out| aarch64
```

### 3.5 A4 / A4b / A4e / A5：proot -r 运行客户机载荷（targetSdk 35）

```
[CMD ] libproot.so -w / -r <filesDir>/rootfs /bin/busybox uname -m
[EXIT] 0
out| aarch64
err| linker: Warning: failed to find generated linker configuration from "/linkerconfig/ld.config.txt"   （无害）
err| linker: unable to get realpath for the library "...". Will use given path.                          （无害，proot fd 翻译限制）

[CMD ] libproot.so -w / -r <filesDir>/rootfs /usr/bin/hello_static
[EXIT] 0
out| STATIC_GUEST_OK pid=18342 sysname=Linux machine=aarch64 release=6.6.89-android15-8-...

[CMD ] libproot.so -w / -r <filesDir>/rootfs /usr/bin/hello_dynamic
[EXIT] 0
out| DYNAMIC_GUEST_OK pid=18358 machine=aarch64 release=6.6.89-android15-8-...

[CMD ] libproot.so -w / -r <filesDir>/rootfs /usr/bin/hello_dynamic --exec /bin/busybox uname -m   ← 客户机内再 execve
[EXIT] 0
out| aarch64
```

### 3.6 P0：rootfs 文件模式（节选）

```
-rwx--x--x 1 u0_a301 u0_a301   4320 ... rootfs/bin/busybox
-rwx--x--x 1 u0_a301 u0_a301 2285312 ... rootfs/system/bin/linker64
-rwx--x--x 1 u0_a301 u0_a301 1216216 ... rootfs/system/lib64/libc.so
```

---

## 4. 机制性发现（产品化必读）

### 4.1 APK 内 `lib*.so` 命名规则（AGP + 安装器双重过滤）

- **AGP 打包会丢弃 `jniLibs/` 中不匹配 `lib*.so` 的文件**（实测：`libtalloc.so.2`、`libbusybox.so.1.38.0` 静默消失，APK 里查无此文件）。
- **安装器解包到 nativeLibraryDir 时**，AOSP `ApkParsing.cpp::ValidLibraryPathLastSlash` 规定：非 debuggable 应用只接受 `lib` 前缀 + `.so` 后缀的文件名（debuggable 应用例外）。来源：[AOSP frameworks/base/libs/androidfw/ApkParsing.cpp](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/androidfw/ApkParsing.cpp)。
- **对策（本 spike 已实现）**：把 Termux 二进制的 `DT_NEEDED` 字符串原地改写为短名，并把文件改成同名：
  - `libproot.so`: `libtalloc.so.2` → `libtalloc.so`
  - `libbusybox.so`: `libbusybox.so.1.38.0` → `libbusybox_app.so`
  - 工具：`tools/patch-dynstr.py`（dynstr 原地缩短 + NUL 填充，其它偏移不受影响；改完用 `llvm-readelf -d` 复核）
- 需要 `android:extractNativeLibs=true`（本工程用 `packaging.jniLibs.useLegacyPackaging = true`），否则文件根本不会被解包到 nativeLibraryDir。

### 4.2 Termux busybox 的真实结构（踩坑点）

Termux 1.38 的 `bin/busybox` 只有 **4320 字节**：它是个启动器 stub，真正代码在 `lib/libbusybox.so.1.38.0`（876KB 共享对象，SONAME 未变）。stub 的 `DT_NEEDED` 指向该载荷；applet 选择依赖 **`argv[0]`**，所以：
- 直接 `exec` 改名后的 stub 并给 applet 参数（`libbusybox.so uname -m`）→ `applet not found`（exit 127）；
- 正解：用 argv0 shim（`libspike_shim.so <argv0> <path> [args...]`）或 symlink 命名。

### 4.3 `proot -r` 的 rootfs 自包含要求（踩坑点）

- rootfs 里被 proot 用来当解释器的 `system/bin/linker64`（以及宿主借用的 libc 等）**必须有 +x 权限**：否则报 `proot error: execve("/bin/busybox"): Permission denied`，**极易误判为 targetSdk/SELinux 拦截**。第一轮测试就踩了这个坑（见 `dist/logs/round1-*` 与 `debug.md`）。
- rootfs 客户机的动态库依赖需要**完整传递闭包**：只放 `liblog.so` 不放 `libc++.so` 会得到 `CANNOT LINK EXECUTABLE ... library "libc++.so" not found: needed by /system/lib64/liblog.so`。本 spike 的迷你 rootfs 是"Termux 二进制 + 宿主 bionic 库借用"的合成品，闭包 = `libc/libm/libdl/liblog/libc++ + ld-android.so + linker64`；**阶段一的 Ubuntu rootfs 不需要这套借用**（自带 glibc/解释器），但同样要保证 rootfs 内文件可执行。
- 无害噪声：`linkerconfig/ld.config.txt` 缺失警告、`readlink("/proc/self/fd/N") failed` + `unable to get realpath ... Will use given path`（proot 的 fd 翻译限制），不影响执行。

### 4.4 A6 · 16KB 页对齐

- 设备 `PAGE_SIZE=4096`（4KB），so 本机不存在 16KB 强约束。
- 但仍做了对齐核查：所有随包 ELF（Termux 二进制、自建二进制）的 `PT_LOAD` 对齐均为 `0x4000`（16KB）；`zipalign -c -P 16 -v 4` 对三份 APK 全部 **Verification successful**（见 §7 命令）。即：包内二进制天然兼容 16KB 页设备，未来无需为此返工。

---

## 5. 对路线图 v3 的输入

- 阶段〇进入阶段一条件之一（Spike A）**达成**：`libproot.so` 走 jniLibs/nativeLibraryDir + `PROOT_LOADER`/`PROOT_TMP_DIR`/`LD_LIBRARY_PATH` 环境注入的形态在 Android 16 真机上成立，且 **targetSdk 35 不需要降级**。
- 决策 #13 的备选（降级 targetSdk 28）依然有效且更宽松（私有目录可直接 exec），保留为兼容性后备（老 ROM/特殊机型）。
- 需要写进产品约束的清单：
  1. 放进 jniLibs 的一切（可执行文件 + 被 NEEDED 的库）**命名必须 `lib*.so` 且 DT_NEEDED 同步改写**（用 `tools/patch-dynstr.py`，构建流程里固化）。
  2. `extractNativeLibs=true` 必须显式保留（AGP 新默认是 false）。
  3. 前台服务的 proot 子进程需注入：`LD_LIBRARY_PATH=<nativeLibraryDir>`、`PROOT_LOADER=<nld>/libproot-loader.so`、`PROOT_TMP_DIR=<filesDir>/tmp`（Termux 编译期默认路径 `/data/data/com.termux/...` 在自家包内不存在，必须覆盖）。
  4. rootfs 内一切需要执行的文件（解释器、脚本、可执行）必须 +x；rootfs 打包时不要依赖 tar 解包后的 umask 偶然值。
  5. 多合一二进制（busybox 型）从 nld 执行时需要 argv0 控制手段（shim 或 symlink+私有目录——后者在 35 上不可行，故用 shim）。
  6. 阶段一 DoD 建议加一条：**每类 rootfs 内二进制（脚本/静态/动态/多合一）跑一遍 exec 自检**，沿用本 spike 的探针结构。

---

## 6. 复现步骤

```bash
# 0) 工具链（只读引用便携工具链；写目录全在仓内）
export JAVA_HOME=/d/VSCodeCache/shizku-m/build-env/jdk-21.0.2
export GRADLE_USER_HOME="D:/VSCodeCache/azurpilot-azurpilot/.tmp/spike-a/gradle-home"

# 1) 构建（两变体 + release 变体）
cd spike/a-proot-exec
./gradlew --no-daemon -Pspike.targetSdk=28 :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk dist/spikea-target28-debug.apk
./gradlew --no-daemon -Pspike.targetSdk=35 :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk dist/spikea-target35-debug.apk
./gradlew --no-daemon -Pspike.targetSdk=35 :app:assembleRelease
cp app/build/outputs/apk/release/app-release.apk dist/spikea-target35-release.apk

# 2) 真机跑阶梯（自动装/跑/收日志到 dist/logs/）
bash run-device-ladder.sh AVAY025422002864

# 3) 16KB 对齐核查
/d/VSCodeCache/shizku-m/build-env/android-sdk/build-tools/36.0.0/zipalign.exe -c -P 16 -v 4 dist/spikea-target35-debug.apk

# 4) release 变体（非 debuggable）单独安装 + 看 logcat
adb install -r dist/spikea-target35-release.apk
adb logcat -d -s SpikeA:I
```

重建二进制（若需从零复刻）：`tools/patch-dynstr.py` 用法见其文件头；自建探针源码在 `tools/{argv0_shim.c,guest_static.c,guest_dynamic.c}`，用便携 NDK 编译（示例）：

```bash
CC=/d/VSCodeCache/shizku-m/build-env/android-sdk/ndk/29.0.13113456/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android26-clang
"$CC" -static -O2 -Wl,-z,max-page-size=16384 -o libspike_shim.so tools/argv0_shim.c
"$CC" -static -O2 -Wl,-z,max-page-size=16384 -o hello_static tools/guest_static.c
"$CC" -O2 -Wl,-z,max-page-size=16384 -o hello_dynamic tools/guest_dynamic.c
```

---

## 7. 交付物清单与校验值

APK（SHA256）：

| 文件 | SHA256 |
|---|---|
| `dist/spikea-target35-debug.apk` | `6967f46ff7dd44f1e33b53e13e6985b99d163bc6ad84fc75c7348995089f95cc` |
| `dist/spikea-target28-debug.apk` | `c720fd6f087a2b2ac01a12b314fa85b8c9bb673419c455eb9c1c4bfacd620aba` |
| `dist/spikea-target35-release.apk` | `686d1e2bab3bae0a60688274d9f2bc60ab0b2875b0c6de257d4b1ecafe955cdf` |

> release APK 用 debug keystore 签名（spike 便利，非产品签名方案）。

源包（SHA256，`packages.termux.dev` 当日快照）：

| 包 | SHA256 |
|---|---|
| `proot_5.1.107.92_aarch64.deb` | `1f1c983509701f6826f568482c70673ee453a9ba38c9f5fa445a472d6b7524e9` |
| `libtalloc_2.4.3_aarch64.deb` | `ac81ad623d74c209718b9f3acb2dd702cc8a88c431e820d212229910b4db29da` |
| `busybox_1.38.0-1_aarch64.deb` | `1bb7f1d4c00cadd0e1117b6dd7110311b8bf749ef00b486e96cfdc11c98f8fd9` |
| `libandroid-selinux_14.0.0.11-1_aarch64.deb` | `00afd8c34087c2864737b51fd9d104dc5e955f6ec3c0f50c0c7ef5b4a56866b9` |
| `libandroid-shmem_0.7_aarch64.deb` | `0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6` |
| `pcre2_10.47_aarch64.deb` | `51f915d22de639bfca6ec029ae613987bbe3bc73626eede13319fd2e95f50b63` |

日志：

| 文件 | 内容 |
|---|---|
| `dist/logs/device-facts.txt` | adb 侧设备事实 |
| `dist/logs/target35-final.log.txt` / `target28-final.log.txt` / `target35-release-final.log.txt` | 最终一轮完整落盘日志（可直接读） |
| `dist/logs/target35.logcat.txt` / `target28.logcat.txt` / `target35-release.logcat.txt` | 最终一轮原始 logcat |
| `dist/logs/target35.spikea.log` / `target28.spikea.log` | 原始累积日志（含首轮异常，NUL 字节来自 `/proc/self/attr/current`） |
| `dist/logs/round1-*` | 第一轮（未修 chmod/命名）证据，用于复盘口径 |

---

## 8. 未覆盖项 / 下一步建议

1. **真实 Ubuntu rootfs 复验（阶段一 DoD）**：本 spike 的 rootfs 是 Android 风味合成品；Ubuntu ARM64 的 `PT_INTERP=/lib/ld-linux-aarch64.so.1` 走 proot 的另一条分支，需在阶段一产物上重跑 A4/A4e/A5 这类探针（探针结构可直接复用）。
2. **长时稳定性**：单次探针 ~100ms 级别，未做长挂机/内存与 fd 泄漏观察（留给阶段三前台服务）。
3. **多 ROM 兼容**：仅 HONOR MagicOS/Android 16 一台；HyperOS/ColorOS 等留待阶段五（本 spike 的失败面是"目标 SDK 语义"，与 ROM 关系小，但仍需抽检）。
4. **产品签名 release 安装**：本 spike 的 release 用 debug key 签名验证了命名规则；正式签名后建议再跑一次同款 `A0` 清单（1 分钟）。
5. `proot` 的 seccomp 加速在本机默认可用的（`seccomp_filter = yes`）且未被拦截；如未来机型报 kompat/seccomp 相关问题，`PROOT_NO_SECCOMP=1` 是本 spike 已验证的降级开关（见 A3r/A4r 探针定义）。
