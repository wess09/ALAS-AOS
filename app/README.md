# 本模块说明

Android 宿主（Kotlin/Compose）源码。产品说明见仓库根 [README.md](../README.md)，改动账册见根 `devlog.md`，阶段交接见根 `handoff/`，开发文档见根 `development.md`。

## 来源声明

本模块由 AGPL-3.0 授权的上游 Android GUI 项目（基线 commit `b2b0f54`，上游项目名见 Git 提交历史「阶段二 M2-a」）改造而来，已做大幅删减与重写：

- 分辨率选项移除，虚拟屏恒为 1280×720；
- 接入 proot 运行环境（内置 Ubuntu rootfs + AzurPilot）；
- UI 全量改为标准 Material 3（动态取色 + M3 现成组件）；
- 根包名、类名、资源名、文件名去除全部历史品牌前缀。

上游那份 README 不再随本仓维护；需要查阅上游用法请直接到上游仓库。本仓的构建与运行方式以 `development.md` 为准。
