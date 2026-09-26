# ADB 全流程测试手册 / ADB end-to-end testing guide

> 本文给出用 ADB 在真机上验证「安装 → Runtime 更新 → 会话就绪 → 网关健康」的完整步骤。
> This guide walks through verifying "install → Runtime update → session ready →
> gateway health" on a real device with ADB.

## 中文

### 前置条件

- 设备已开启 USB 调试并授权；`adb devices` 能看到设备。
- CI 已发布包含目标提交的 `*-update.apk`。
- 本手册的日志路径基于外部存储：
  `/sdcard/Android/data/com.azurpilot.ghio/files/log/`。

### 1. 安装

```bash
adb install -r -d AzurPilot-Android-<ver>-update.apk
```

- `-r` 覆盖安装保留数据；`-d` 允许版本码回落（CI 递增号可能低于本地
  时间戳版本号的构建，遇 `INSTALL_FAILED_VERSION_DOWNGRADE` 时必需）。
- 安装后确认：`adb shell dumpsys package com.azurpilot.ghio | grep versionName`。

### 2. Runtime 更新

1. 启动应用：`adb shell monkey -p com.azurpilot.ghio -c android.intent.category.LAUNCHER 1`。
2. 已装 Runtime 落后于发布时，启动即弹「发现 Runtime 更新」。
3. 用 `uiautomator dump` 定位「立即更新」按钮并 `input tap`：
   ```bash
   adb shell uiautomator dump /sdcard/ui.xml
   adb shell grep -o 'text="立即更新"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' /sdcard/ui.xml
   adb shell input tap <x> <y>
   ```
4. 877 MB 下载在代理网络下约 3 分钟；进度可见于部署页
   「正在从 GitHub 下载 Runtime N%」→「解压中 N%」。
5. 完成标志（app.log）：
   `rootfs installed from release: <rootfs_version>`，随后紧跟一次
   `proot session spawn`。

### 3. 会话就绪

- 新 rootfs 首启需重建全量字节码，WebUI 可能超过 90 秒才就绪；
  app.log 出现 `AzurPilot WebUI not ready within 90000ms` 属预期，不是故障。
- 就绪判据：session.log（`files/log/proot/session.log`）中
  `/android/status`、`/android/logs` 等请求开始返回 200。

### 4. 网关健康

```bash
adb forward tcp:25548 tcp:25548
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:25548/healthz          # 期望 200
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:25548/android/status   # 期望 403（无 token）
```

- `/healthz` 200 即网关就绪；`/android/status` 无 token 返回 403 说明鉴权正常。

### 5. 日志位置

| 日志 | 路径（设备上） | 内容 |
|---|---|---|
| app.log | `.../files/log/app.log` | App 壳层全量（Timber） |
| session.log | `.../files/log/proot/session.log` | proot stdout/stderr 与 WebUI 访问记录 |
| UI dump | `/sdcard/ui.xml` | 临时，验证后可删 |

### 排错速查

- **下载卡住**：确认设置的下载源；直连 GitHub 在受限网络不可达时，
  在「设置 → Runtime 卡 → 下载源」选一个镜像后重试。
- **`INSTALL_FAILED_VERSION_DOWNGRADE`**：加 `-d`。
- **解压后一直「等待服务就绪」**：首启字节码重建，等 3–10 分钟；
  或重启 App（二次启动恢复正常速度）。

## English

### Prerequisites

- A device with USB debugging authorized and visible in `adb devices`.
- A CI-published `*-update.apk` containing the commit under test.
- Log paths in this guide live on external storage:
  `/sdcard/Android/data/com.azurpilot.ghio/files/log/`.

### 1. Install

```bash
adb install -r -d AzurPilot-Android-<ver>-update.apk
```

- `-r` keeps app data; `-d` allows a version downgrade (CI-incremented
  version codes can be lower than local timestamp-based builds; required when
  you hit `INSTALL_FAILED_VERSION_DOWNGRADE`).
- Verify: `adb shell dumpsys package com.azurpilot.ghio | grep versionName`.

### 2. Runtime update

1. Launch the app:
   `adb shell monkey -p com.azurpilot.ghio -c android.intent.category.LAUNCHER 1`.
2. When the installed Runtime trails the release, the app shows the
   "Runtime update available" dialog on startup.
3. Locate the update button with `uiautomator dump` and tap it:
   ```bash
   adb shell uiautomator dump /sdcard/ui.xml
   adb shell grep -o 'text="立即更新"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' /sdcard/ui.xml
   adb shell input tap <x> <y>
   ```
4. The 877 MB download takes about 3 minutes through a proxy network; the
   provision screen shows "Downloading Runtime N%" and then "Extracting N%".
5. Completion marker (app.log):
   `rootfs installed from release: <rootfs_version>`, followed by a
   `proot session spawn` line.

### 3. Session readiness

- A fresh rootfs rebuilds all bytecode on first boot; the WebUI can take
  longer than 90 seconds. `AzurPilot WebUI not ready within 90000ms` in
  app.log is an expected warning, not a failure.
- Readiness criterion: requests such as `/android/status` and `/android/logs`
  start returning 200 in session.log (`files/log/proot/session.log`).

### 4. Gateway health

```bash
adb forward tcp:25548 tcp:25548
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:25548/healthz          # expect 200
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:25548/android/status   # expect 403 (no token)
```

- `/healthz` returning 200 means the gateway is ready; `/android/status`
  returning 403 without a token confirms authentication works.

### 5. Log locations

| Log | On-device path | Contents |
|---|---|---|
| app.log | `.../files/log/app.log` | Full app-shell log (Timber) |
| session.log | `.../files/log/proot/session.log` | proot stdout/stderr and WebUI access log |
| UI dump | `/sdcard/ui.xml` | Temporary; safe to delete after verification |

### Quick troubleshooting

- **Download stalls**: check the download source in Settings. When direct
  GitHub is unreachable on a restricted network, pick a mirror under
  "Settings → Runtime card → Download source" and retry.
- **`INSTALL_FAILED_VERSION_DOWNGRADE`**: add `-d`.
- **Stuck on "waiting for services" after extraction**: first-boot bytecode
  rebuild — wait 3–10 minutes, or restart the app (the second boot runs at
  normal speed).
