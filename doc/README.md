# AzurPilot for Android 技术文档 / Technical Documentation

> 本目录是面向维护者的技术文档，遵循 [Google 开发者文档风格指南](https://developers.google.com/style)。
> 每篇均为中英双语：先中文，后英文，两段内容互为镜像。
>
> This directory contains maintainer-facing technical documentation following the
> [Google Developer Documentation Style Guide](https://developers.google.com/style).
> Every document is bilingual: Chinese first, then English, with mirrored content.

## 文档索引 / Document index

| 文档 / Document | 内容 / Contents |
|---|---|
| [architecture.md](architecture.md) | 系统架构：App 壳层、Runtime、网关与更新通道 / System architecture: app shell, runtime, gateway, and update channels |
| [runtime-provisioning.md](runtime-provisioning.md) | Runtime 部署与更新：状态机、下载、校验、解压 / Runtime provisioning and updates: state machine, download, verification, extraction |
| [release-channel.md](release-channel.md) | 发布通道：`latest.json` 字段、镜像源、版本规则 / Release channel: `latest.json` fields, mirrors, version rules |
| [multi-arch.md](multi-arch.md) | 多架构支持：arm64 与 x86_64、CI 矩阵、限制 / Multi-architecture support: arm64 and x86_64, CI matrix, limitations |
| [adb-e2e-testing.md](adb-e2e-testing.md) | ADB 全流程测试手册 / ADB end-to-end testing guide |

## 阅读约定 / Conventions

- 中文 / Chinese：面向中文维护者，术语与代码注释保持一致。
- English / 英文：Follows Google style — sentence-case headings, present tense, active voice, and numbered steps for procedures.
- 代码引用 / Code references use `path:line` form against the repository root.
- 示例命令 / Example commands run on a host with `adb` unless stated otherwise.
