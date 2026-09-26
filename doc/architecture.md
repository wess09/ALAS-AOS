# 系统架构 / System architecture

> 本文说明 AzurPilot for Android 的分层结构：App 壳层、proot Runtime、网关协议与两条更新通道。
> This document describes the layered structure of AzurPilot for Android: the app shell, the
> proot runtime, the gateway protocol, and the two update channels.

## 中文

### 分层总览

```
┌─────────────────────────────────────────────────┐
│ App 壳层（Kotlin / Compose，进程 com.azurpilot.ghio）│
│  Provisioner → ProotHost → AzurPilotGateway      │
│  特权服务（Shizuku / root）：虚拟屏 + 桥接输入捕获   │
└───────────────────┬─────────────────────────────┘
                    │ proot（nativeLibraryDir 内九件套）
┌───────────────────▼─────────────────────────────┐
│ Runtime（Ubuntu 24.04 rootfs + Python 3.14）      │
│  上游 AzurPilot：WebUI + /android/* 薄接口         │
│  监听 127.0.0.1:25548                             │
└─────────────────────────────────────────────────┘
```

### App 壳层

- **RootfsProvisioner**（`app/app/src/main/java/com/azurpilot/ghio/provision/RootfsProvisioner.kt`）：
  负责 Runtime 的首次部署与升级，见 [runtime-provisioning.md](runtime-provisioning.md)。
- **ProotHost**（`proot/ProotHost.kt`）：以 App 进程为父拉起 proot 会话，
  崩溃退避重拉、stdin 保活约定、清理残留锁。
- **AzurPilotGateway**（`proot/`）：对 Runtime 的 WebSocket 富接口订阅方，
  全局唯一订阅，承载配置、日志、统计的实时推送。
- **特权服务**（`privileged/`、`remote/`）：经 Shizuku 或 root 以 `app_process`
  拉起独立进程，承载虚拟屏管理、桥接截屏与注入（`bridge/` 下的 native 代码）。

### proot Runtime

- rootfs 由 CI 构建（见 [multi-arch.md](multi-arch.md)），按设备 ABI 对应。
- proot 九件套以 `lib*.so` 形式打进 APK 的 `jniLibs`，安装后落在
  `nativeLibraryDir`——这是 targetSdk 35 下唯一保证可 `execve` 的位置。
- `PROOT_LOADER` 指向 `nativeLibraryDir` 中的 loader，绕开
  「App 不得 execve 私有目录文件」的限制（Spike A 实证结论）。
- 会话约定：**stdin 管道保持敞开**。App 进程退出导致 EOF 时，wrapper
  自行杀掉进程组，防孤儿链路。

### 网关协议

Runtime 在 `127.0.0.1:25548` 同时暴露三类接口：

| 接口 | 鉴权 | 用途 |
|---|---|---|
| `/api/v1/ws`（WebSocket） | 会话令牌 | 实例/总览/配置/日志/统计的富订阅 |
| `/android/*` | `X-AzurPilot-Android-Token` | App 专用的状态、日志、配置薄接口 |
| `/healthz` | 无 | 就绪探测 |

### 两条更新通道

- **App 更新**：轻量 APK（双 ABI、无 rootfs），由 `AppUpdateManager` 走
  Release 清单的 `apkUrl` 下载安装，见 [release-channel.md](release-channel.md)。
- **Runtime 更新**：按设备 ABI 从 `runtimes` 选择 `rootfs-<abi>.tar.xz`，
  由 RootfsProvisioner 下载解压，见 [runtime-provisioning.md](runtime-provisioning.md)。

## English

### Layer overview

```
┌──────────────────────────────────────────────────┐
│ App shell (Kotlin/Compose, process com.azurpilot.ghio)│
│  Provisioner → ProotHost → AzurPilotGateway       │
│  Privileged service (Shizuku/root): virtual display│
│  + bridge capture/input                           │
└───────────────────┬──────────────────────────────┘
                    │ proot (nine-lib set in nativeLibraryDir)
┌───────────────────▼──────────────────────────────┐
│ Runtime (Ubuntu 24.04 rootfs + Python 3.14)       │
│  Upstream AzurPilot: WebUI + /android/* thin API  │
│  Listening on 127.0.0.1:25548                     │
└──────────────────────────────────────────────────┘
```

### App shell

- **RootfsProvisioner** (`app/app/src/main/java/com/azurpilot/ghio/provision/RootfsProvisioner.kt`):
  owns first-time Runtime deployment and upgrades. See [runtime-provisioning.md](runtime-provisioning.md).
- **ProotHost** (`proot/ProotHost.kt`): spawns the proot session with the app
  process as parent, restarts with backoff on crash, keeps stdin alive, and
  cleans stale locks.
- **AzurPilotGateway** (`proot/`): the single WebSocket subscriber to the
  runtime's rich API; carries live config, log, and statistics pushes.
- **Privileged service** (`privileged/`, `remote/`): a separate process started
  through Shizuku or root via `app_process`. It owns virtual display
  management and the bridge capture/input native code (`bridge/`).

### proot runtime

- CI builds the rootfs (see [multi-arch.md](multi-arch.md)) per device ABI.
- The proot nine-library set ships inside the APK as `jniLibs` named
  `lib*.so`; after install they land in `nativeLibraryDir` — the only
  location guaranteed `execve`-able at targetSdk 35.
- `PROOT_LOADER` points at the loader inside `nativeLibraryDir`, which
  bypasses the "apps must not `execve` private-dir files" restriction
  (verified in Spike A).
- Session contract: **keep the stdin pipe open**. When the app process dies,
  the wrapper sees EOF and kills its own process group to avoid orphans.

### Gateway protocol

The runtime exposes three interfaces on `127.0.0.1:25548`:

| Interface | Auth | Purpose |
|---|---|---|
| `/api/v1/ws` (WebSocket) | session token | Rich subscriptions: instances, overview, config, logs, statistics |
| `/android/*` | `X-AzurPilot-Android-Token` | App-only thin API for status, logs, and config |
| `/healthz` | none | Readiness probe |

### Two update channels

- **App update**: a slim APK (both ABIs, no rootfs). `AppUpdateManager`
  downloads `apkUrl` from the release manifest. See
  [release-channel.md](release-channel.md).
- **Runtime update**: `rootfs-<abi>.tar.xz` selected from `runtimes` by
  device ABI, downloaded and extracted by RootfsProvisioner. See
  [runtime-provisioning.md](runtime-provisioning.md).
