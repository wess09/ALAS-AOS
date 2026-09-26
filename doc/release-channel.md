# 发布通道 / Release channel

> 本文说明固定 Release（`azurpilot-android-latest`）的 `latest.json` 字段、镜像源行为与版本号规则。
> This document covers the fixed release (`azurpilot-android-latest`): the `latest.json`
> fields, mirror behavior, and version-number rules.

## 中文

### `latest.json` 字段参考

| 字段 | 类型 | 说明 |
|---|---|---|
| `versionCode` / `versionName` | int / string | App 更新用；规则见下节 |
| `appCommit` | sha | 上次真正构建 APK 的本仓提交 |
| `azurpilotCommit` | sha | Runtime 所用上游 AzurPilot 提交 |
| `androidHostCommit` | sha | 构建该 Runtime 时的本仓提交 |
| `apkUrl` / `apkSha256` / `apkSize` | — | 轻量更新包（双 ABI、无 rootfs） |
| `runtimes.<abi>.version/url/sha256/size` | object | 按架构的 Runtime 包；`abi ∈ {arm64-v8a, x86_64}` |
| `fullApks.<abi>.url/sha256/size` | object | 按架构的完整版 APK（内置对应 rootfs） |
| `rootfsVersion` / `rootfsUrl` / `rootfsSha256` / `rootfsSize` | — | 旧版兼容字段，恒指向 arm64 包 |

兼容规则：旧版 App 只读扁平 `rootfs*` 字段，因此它们必须始终有效——发布脚本将其固定指向 arm64 条目。

### 版本号规则（CI `resolve` 步）

1. 读取上一份已发布 `latest.json` 的 `versionCode`/`versionName`/`appCommit`。
2. `appCommit` 可达且为本仓祖先，且 `git diff appCommit..HEAD -- '*.kt'` 为空
   → 沿用旧版本号（只发 Runtime，不动 App 版本）。
3. 否则 `versionCode = 旧值 + 1`，`versionName = 1.0.(旧补丁 + 1)`。
4. 无历史时：`versionCode = HEAD 提交时间戳`，`versionName = 1.0.提交计数`。

> 注意：本地构建的 versionCode 取 HEAD 时间戳，可能与 CI 递增出的版本号交错。
> adb 安装 CI 包遇 `INSTALL_FAILED_VERSION_DOWNGRADE` 时加 `-d`。

### 镜像源

`update/ReleaseUrls.kt`：`BASE` 为固定 Release 地址，镜像为 ghproxy 形态
（`前缀 + 完整 GitHub URL`）。内置五个：

| 前缀 | 实测行为（2026-09） |
|---|---|
| `https://ghproxy.net/` | 稳定但慢（4 并发聚合约 1 MB/s） |
| `https://gh-proxy.com/` | 约 1/6 概率无视 Range 回 200 全文件——对单连接无影响，多连接有风险 |
| `https://ghfast.top/` | 稳定，中等速度 |
| `https://gh.ddlc.top/` | 最快且并发稳定（本轮实测首选） |
| `https://gh-proxy.net/` | 对无 JS 引擎的下载器返回挑战页（HTML），**不可用于下载**，仅存留于列表待观察 |

用户可在设置中选择内置镜像或填自定义前缀（同样要求 ghproxy 形态，
`ReleaseUrls.normalizeCustom` 做规范化，非法输入回落直连）。
设置存储：`github_mirror`（`direct` / 镜像前缀 / `custom`）与
`github_mirror_custom`；旧版布尔开关 `use_github_mirror=true` 自动迁移为 ghproxy.net。

### 签名

- 正式包用 Release keystore（GitHub Secrets），v1+v2+v3 全开；CI 发布前用
  apksigner 按最低 SDK 分档逐一验证。
- 无签名材料时 CI 出 debug 包（仓内固定调试密钥，保证签名一致），
  只留构建产物、不发布。

## English

### `latest.json` field reference

| Field | Type | Meaning |
|---|---|---|
| `versionCode` / `versionName` | int / string | For app updates; rules in the next section |
| `appCommit` | sha | Repository commit the last published APK was built from |
| `azurpilotCommit` | sha | Upstream AzurPilot commit inside the Runtime |
| `androidHostCommit` | sha | Repository commit the Runtime was built from |
| `apkUrl` / `apkSha256` / `apkSize` | — | Slim update APK (both ABIs, no rootfs) |
| `runtimes.<abi>.version/url/sha256/size` | object | Per-ABI Runtime package; `abi ∈ {arm64-v8a, x86_64}` |
| `fullApks.<abi>.url/sha256/size` | object | Per-ABI full APK (bundles the matching rootfs) |
| `rootfsVersion` / `rootfsUrl` / `rootfsSha256` / `rootfsSize` | — | Legacy fields, always pointing at the arm64 package |

Compatibility rule: old app versions read only the flat `rootfs*` fields, so
they must stay valid — the publish step pins them to the arm64 entry.

### Version rules (CI `resolve` step)

1. Read `versionCode`/`versionName`/`appCommit` from the last published
   `latest.json`.
2. If `appCommit` is reachable, an ancestor of HEAD, and
   `git diff appCommit..HEAD -- '*.kt'` is empty → keep the previous version
   numbers (publish a Runtime only, no app version bump).
3. Otherwise `versionCode = previous + 1` and
   `versionName = 1.0.(previous patch + 1)`.
4. With no history: `versionCode = HEAD commit timestamp` and
   `versionName = 1.0.<commit count>`.

> Local builds derive `versionCode` from the HEAD timestamp, which can interleave
> with CI-incremented numbers. Add `-d` to `adb install` when you hit
> `INSTALL_FAILED_VERSION_DOWNGRADE`.

### Mirrors

`update/ReleaseUrls.kt`: `BASE` is the fixed release address; mirrors use the
ghproxy form (`prefix + full GitHub URL`). Five are built in:

| Prefix | Measured behavior (2026-09) |
|---|---|
| `https://ghproxy.net/` | Stable but slow (about 1 MB/s aggregated over 4 connections) |
| `https://gh-proxy.com/` | Ignores `Range` and returns 200 with the full file about 1 in 6 requests — harmless for a single connection, risky for segmented downloads |
| `https://ghfast.top/` | Stable, moderate speed |
| `https://gh.ddlc.top/` | Fastest with stable concurrency (measured first choice) |
| `https://gh-proxy.net/` | Returns a JS challenge page (HTML) to downloaders without a JS engine — **unusable for downloads**; kept in the list for observation only |

Users pick a built-in mirror or enter a custom prefix in Settings (same
ghproxy form; `ReleaseUrls.normalizeCustom` sanitizes it, and invalid input
falls back to direct). Storage: `github_mirror` (`direct` / a mirror prefix /
`custom`) plus `github_mirror_custom`; the legacy boolean
`use_github_mirror=true` migrates to ghproxy.net automatically.

### Signing

- Release builds use the release keystore (GitHub Secrets) with v1+v2+v3
  enabled; CI verifies each APK with apksigner per minimum-SDK bucket before
  publishing.
- Without signing material CI produces debug APKs (the repository's fixed
  debug key keeps signatures consistent) and keeps them as build artifacts
  only, unpublished.
