<div align="center">

<img src="app/app/src/main/res/drawable-nodpi/azurpilot_android_logo.png" alt="AzurPilot for Android Logo" width="340">

# AzurPilot for Android

**无需电脑与常驻服务器 · 原生轻量化运行 · 后台虚拟屏无感挂机**

全功能《碧蓝航线》自动化助手 [AzurPilot](https://github.com/wess09/AzurPilot) 专用的 Android 移动端一体化运行环境

<p align="center">
  <b>简体中文</b> |
  <a href="README_en.md">English</a> |
  <a href="README_ja.md">日本語</a> |
  <a href="README_zh-TW.md">繁體中文</a>
</p>

---

<!-- 核心环境与平台标签组 -->
<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-blue.svg?style=flat-square" alt="License: AGPL-3.0"></a>
  <a href="https://developer.android.com/about/versions/pie"><img src="https://img.shields.io/badge/Platform-Android%209.0%2B%20(API%2028%2B)-3DDC84.svg?style=flat-square&logo=android&logoColor=white" alt="Platform: Android 9.0+"></a>
  <a href="https://en.wikipedia.org/wiki/AArch64"><img src="https://img.shields.io/badge/Architecture-ARM64--v8a-E10098.svg?style=flat-square&logo=arm&logoColor=white" alt="Arch: ARM64"></a>
  <a href="https://ubuntu.com/"><img src="https://img.shields.io/badge/Runtime-Ubuntu%2024.04%20LTS-E95420.svg?style=flat-square&logo=ubuntu&logoColor=white" alt="Runtime: Ubuntu 24.04"></a>
  <a href="https://www.python.org/"><img src="https://img.shields.io/badge/Python-3.14-3776AB.svg?style=flat-square&logo=python&logoColor=white" alt="Python 3.14"></a>
  <a href="https://github.com/astral-sh/uv"><img src="https://img.shields.io/badge/Packaging-uv-DE5FE9.svg?style=flat-square" alt="Packaging: uv"></a>
</p>

<!-- 技术栈与框架标签组 -->
<p align="center">
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Host-Kotlin%20%7C%20Compose%20M3-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Host: Kotlin / Jetpack Compose"></a>
  <a href="https://react.dev/"><img src="https://img.shields.io/badge/WebUI-React%20%7C%20Vite-61DAFB.svg?style=flat-square&logo=react&logoColor=black" alt="WebUI: React + Vite"></a>
  <a href="https://shizuku.rikka.app/"><img src="https://img.shields.io/badge/Backend-Shizuku%20%2F%20Root-brightgreen.svg?style=flat-square" alt="Backend: Shizuku / Root"></a>
  <a href="https://opencv.org/"><img src="https://img.shields.io/badge/Vision-OpenCV%20%7C%20RapidOCR-5C3EE8.svg?style=flat-square&logo=opencv&logoColor=white" alt="Vision: OpenCV + RapidOCR"></a>
  <a href="https://proot-me.github.io/"><img src="https://img.shields.io/badge/Isolation-PRoot-lightgrey.svg?style=flat-square" alt="Isolation: PRoot"></a>
  <a href="https://deepwiki.com/wess09/AzurPilot-for-Android"><img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki"></a>
</p>

<!-- 仓库动态与社区指标标签组 -->
<p align="center">
  <a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest"><img src="https://img.shields.io/github/v/release/wess09/AzurPilot-for-Android?style=flat-square&color=007ec6&label=Latest%20Release" alt="Latest Release"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/releases"><img src="https://img.shields.io/github/downloads/wess09/AzurPilot-for-Android/total?style=flat-square&color=28a745&label=Downloads" alt="Total Downloads"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers"><img src="https://img.shields.io/github/stars/wess09/AzurPilot-for-Android?style=flat-square&color=f5a623&label=Stars" alt="Stars"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/network/members"><img src="https://img.shields.io/github/forks/wess09/AzurPilot-for-Android?style=flat-square&color=6f42c1&label=Forks" alt="Forks"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/issues"><img src="https://img.shields.io/github/issues/wess09/AzurPilot-for-Android?style=flat-square&color=d73a49&label=Issues" alt="Open Issues"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/commits/main"><img src="https://img.shields.io/github/last-commit/wess09/AzurPilot-for-Android?style=flat-square&color=586069&label=Last%20Commit" alt="Last Commit"></a>
</p>

<p align="center">
  <a href="#工程总览与指标">工程总览</a> •
  <a href="#代码知识库与-ai-问答">智能问答</a> •
  <a href="#项目概述">项目概述</a> •
  <a href="#关联生态工程">关联生态</a> •
  <a href="#核心特性">核心特性</a> •
  <a href="#系统架构全景">系统架构</a> •
  <a href="#环境规格与兼容性">环境规格</a> •
  <a href="#快速开始">快速开始</a> •
  <a href="#功能界面一览">功能界面</a> •
  <a href="#双轨独立更新机制">双轨更新</a> •
  <a href="#研发活跃度">研发活跃</a> •
  <a href="#star-增长趋势">增长趋势</a> •
  <a href="#社区生态与支持">社区支持</a>
</p>

</div>

---

## 工程总览与指标

<table width="100%">
  <thead>
    <tr>
      <th colspan="4" align="left">
        <img src="https://img.shields.io/badge/Project%20Overview-AzurPilot%20for%20Android-181717?style=flat-square&logo=github&logoColor=white" alt="Overview">
        <b>工程核心运行与研发指标</b>
      </th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td width="25%"><b>最新正式版</b><br><a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest"><img src="https://img.shields.io/github/v/release/wess09/AzurPilot-for-Android?style=flat-square&color=007ec6" alt="Release"></a></td>
      <td width="25%"><b>累计下载量</b><br><a href="https://github.com/wess09/AzurPilot-for-Android/releases"><img src="https://img.shields.io/github/downloads/wess09/AzurPilot-for-Android/total?style=flat-square&color=28a745" alt="Downloads"></a></td>
      <td width="25%"><b>开源许可证</b><br><a href="./LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0"></a></td>
      <td width="25%"><b>主分支状态</b><br><a href="https://github.com/wess09/AzurPilot-for-Android/actions"><img src="https://img.shields.io/badge/Build-Passing-brightgreen?style=flat-square&logo=githubactions&logoColor=white" alt="Build Status"></a></td>
    </tr>
    <tr>
      <td width="25%"><b>代码库体积</b><br><img src="https://img.shields.io/github/repo-size/wess09/AzurPilot-for-Android?style=flat-square&color=586069" alt="Repo Size"></td>
      <td width="25%"><b>代码语言</b><br><img src="https://img.shields.io/badge/Language-Kotlin%20%7C%20Shell-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Language"></td>
      <td width="25%"><b>提权执行方式</b><br><img src="https://img.shields.io/badge/Backend-Shizuku--m%20%7C%20Root-brightgreen?style=flat-square" alt="Backend"></td>
      <td width="25%"><b>目标系统</b><br><img src="https://img.shields.io/badge/Android-API%2028%2B%20(ARM64)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android Target"></td>
    </tr>
    <tr>
      <td colspan="4">
        <b>快速操作快捷入口：</b>
        <a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest"><img src="https://img.shields.io/badge/Release-下载最新安装包-0052cc?style=flat-square&logo=android&logoColor=white" alt="Download"></a>
        <a href="https://deepwiki.com/wess09/AzurPilot-for-Android"><img src="https://img.shields.io/badge/Wiki-Ask%20DeepWiki-6366F1?style=flat-square" alt="Ask DeepWiki"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/issues/new/choose"><img src="https://img.shields.io/badge/Issue-提交错误报告与需求-d73a49?style=flat-square&logo=githubissues&logoColor=white" alt="New Issue"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/pulls"><img src="https://img.shields.io/badge/PR-合并请求代码贡献-28a745?style=flat-square&logo=git&logoColor=white" alt="Pull Request"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers"><img src="https://img.shields.io/badge/Star-关注项目发展-f5a623?style=flat-square&logo=github&logoColor=white" alt="Star"></a>
      </td>
    </tr>
  </tbody>
</table>

---

## 代码知识库与 AI 问答

<table width="100%">
  <tr>
    <td width="70%" valign="middle">
      <img src="https://img.shields.io/badge/AI%20Doc-DeepWiki-6366F1?style=flat-square" alt="DeepWiki Tag"><br>
      <h3>Ask DeepWiki 代码库智能问答</h3>
      <p>对项目的内部模块、PRoot 沙盒机制、虚拟屏渲染逻辑或定时任务调度规则有任何疑问？直接向已索引本仓库全量上下文的 DeepWiki 发起提问，快速获取针对性的架构解析与代码导引。</p>
      <div>
        <a href="https://deepwiki.com/wess09/AzurPilot-for-Android">
          <img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki">
        </a>
        <a href="https://deepwiki.com/wess09/AzurPilot-for-Android">
          <img src="https://img.shields.io/badge/知识库检索-即时提问解答-0052cc?style=flat-square" alt="Ask Question">
        </a>
      </div>
    </td>
    <td width="30%" align="center" valign="middle">
      <a href="https://deepwiki.com/wess09/AzurPilot-for-Android">
        <img src="https://img.shields.io/badge/Ask%20DeepWiki-AI%20Chat-6366F1?style=for-the-badge&logoColor=white" alt="Ask DeepWiki"><br><br>
        <img src="https://img.shields.io/badge/Documentation-Auto%20Updated-brightgreen?style=flat-square" alt="Documentation Status">
      </a>
    </td>
  </tr>
</table>

---

<table width="100%">
  <tr>
    <td width="70%" valign="middle">
      <h3>如果 AzurPilot for Android 为你带来了便利</h3>
      <p>给项目点亮一颗 Star 是对维护者持续更新与技术攻关最大的肯定。只需点击右侧按钮即可加入关注者列表！</p>
      <div>
        <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers"><img src="https://img.shields.io/github/stars/wess09/AzurPilot-for-Android?style=for-the-badge&color=f5a623&logo=github&logoColor=white" alt="Star on GitHub"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/network/members"><img src="https://img.shields.io/github/forks/wess09/AzurPilot-for-Android?style=for-the-badge&color=6f42c1&logo=github&logoColor=white" alt="Fork on GitHub"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/watchers"><img src="https://img.shields.io/github/watchers/wess09/AzurPilot-for-Android?style=for-the-badge&color=007ec6&logo=github&logoColor=white" alt="Watch on GitHub"></a>
      </div>
    </td>
    <td width="30%" align="center" valign="middle">
      <a href="https://github.com/wess09/AzurPilot-for-Android">
        <img src="https://img.shields.io/badge/Star%20Project-Click%20To%20Star-f5a623?style=for-the-badge&logo=github&logoColor=white" alt="Star Project Now"><br><br>
        <img src="https://img.shields.io/badge/Status-Actively%20Maintained-brightgreen?style=flat-square" alt="Maintained">
      </a>
    </td>
  </tr>
</table>

---

## 项目概述

**AzurPilot for Android** 致力于将桌面端功能成熟的《碧蓝航线》自动化助手完整移植至 Android 移动端原生运行。

通过在 APK 安装包内预置完整的 Linux 运行时容器（Ubuntu 24.04 ARM64），结合轻量级 PRoot 容器隔离技术，实现了在 **完全免 Root** 条件下稳定运行 CPython、预编译 OCR 算法模型及本地 Web 控制台服务。借助后台虚拟屏幕与辅助交互服务，用户可以在手机主屏正常聊天、游戏或办公的同时，无感完成各项自动化巡航任务。

---

## 关联生态工程

本工程与上下游核心项目紧密联动，关键依赖与源流工程如下：

<table width="100%">
  <tr>
    <td width="33%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot-for-Android">
          <img src="https://img.shields.io/badge/Repository-AzurPilot--for--Android-181717?style=for-the-badge&logo=github&logoColor=white" alt="AzurPilot-for-Android">
        </a>
      </div>
      <br>
      <b>宿主运行环境 (当前项目)</b>
      <p>面向 Android 移动端的一体化运行基座，打通 PRoot 免 Root 容器、后台虚拟屏、全域悬浮窗与生命周期管理。</p>
      <hr>
      <div>
        <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin">
        <img src="https://img.shields.io/badge/UI-Compose%20M3-4285F4?style=flat-square" alt="Compose">
        <img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0">
      </div>
    </td>
    <td width="33%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot">
          <img src="https://img.shields.io/badge/Repository-AzurPilot-181717?style=for-the-badge&logo=github&logoColor=white" alt="AzurPilot">
        </a>
      </div>
      <br>
      <b>核心自动化引擎本体</b>
      <p>《碧蓝航线》自动化助手核心，包含完备的计算机视觉图像识别、出击调度算法与本地 Web 控制台服务。</p>
      <hr>
      <div>
        <img src="https://img.shields.io/badge/Language-Python%203.14-3776AB?style=flat-square&logo=python&logoColor=white" alt="Python">
        <img src="https://img.shields.io/badge/WebUI-React%20%2B%20Vite-61DAFB?style=flat-square&logo=react&logoColor=black" alt="React">
        <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square" alt="GPL-3.0">
      </div>
    </td>
    <td width="33%" valign="top">
      <div align="center">
        <a href="https://github.com/Shinarin/ALAS-AOS">
          <img src="https://img.shields.io/badge/Repository-ALAS--AOS-181717?style=for-the-badge&logo=github&logoColor=white" alt="ALAS-AOS">
        </a>
      </div>
      <br>
      <b>容器化方案源流</b>
      <p>移动端 Linux 运行时方案与免 Root 提权思路的基石项目，为 Android 自动化部署提供核心路线参考。</p>
      <hr>
      <div>
        <img src="https://img.shields.io/badge/Core-PRoot%20Linux-lightgrey?style=flat-square" alt="PRoot">
        <img src="https://img.shields.io/badge/Architecture-ARM64-E10098?style=flat-square" alt="ARM64">
        <img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0">
      </div>
    </td>
  </tr>
</table>

---

## 核心亮点

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/模块-部署形态-0052cc?style=flat-square" alt="Tag"><br>
      <h3>零配置一键部署</h3>
      <p>完整运行环境（Ubuntu Base、Python 3.14、科学计算与视觉依赖、预置 OCR 模型权重及前端资产）全内置于安装包中。安装后自动释放至私有沙盒，无需配置交叉编译环境或在线下载运行时大包。</p>
      <div>
        <img src="https://img.shields.io/badge/内置容器-Ubuntu%2024.04-orange?style=flat-square" alt="Ubuntu">
        <img src="https://img.shields.io/badge/依赖管理-uv%20预置-blueviolet?style=flat-square" alt="uv">
        <img src="https://img.shields.io/badge/体验-开箱即跑-brightgreen?style=flat-square" alt="Ready">
      </div>
    </td>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/模块-运行模式-00875a?style=flat-square" alt="Tag"><br>
      <h3>后台虚拟屏无感挂机</h3>
      <p>基于 Android 虚拟屏幕（Virtual Display）机制，游戏实例与图像捕获完全在独立的虚拟显示层运行。主屏操作与日常应用互不干扰，彻底告别物理屏幕占用或操作打断问题。</p>
      <div>
        <img src="https://img.shields.io/badge/渲染层-Virtual%20Display-blue?style=flat-square" alt="Virtual Display">
        <img src="https://img.shields.io/badge/主屏体验-前台全自由-green?style=flat-square" alt="Free Foreground">
        <img src="https://img.shields.io/badge/干扰度-零遮挡-teal?style=flat-square" alt="Zero Interference">
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/模块-交互控制-403294?style=flat-square" alt="Tag"><br>
      <h3>多维悬浮窗交互面板</h3>
      <p>内置轻量级全局浮窗控制器，在任意第三方 App 或桌面层级均可一键唤起。支持快速启停任务、检查调度队列、切换目标实例并实时滚动浏览运行态日志输出。</p>
      <div>
        <img src="https://img.shields.io/badge/框架-FloatingX-blueviolet?style=flat-square" alt="FloatingX">
        <img src="https://img.shields.io/badge/操作-秒级启停-orange?style=flat-square" alt="Quick Action">
        <img src="https://img.shields.io/badge/穿透-全局可用-lightgrey?style=flat-square" alt="Global Overlay">
      </div>
    </td>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/模块-控制台-172b4d?style=flat-square" alt="Tag"><br>
      <h3>完整内置 WebUI 体验</h3>
      <p>手机端直接承载原生 React + Vite 控制台前端，提供完整的实例细分配置、出击模式编排、定时调度规则与实时数据看板，无需额外借助电脑浏览器访问。</p>
      <div>
        <img src="https://img.shields.io/badge/前端-React%20%2B%20Vite-61DAFB?style=flat-square&logo=react&logoColor=black" alt="React">
        <img src="https://img.shields.io/badge/通信-FastAPI%20%2F%20Uvicorn-009688?style=flat-square" alt="FastAPI">
        <img src="https://img.shields.io/badge/网络-回环地址监听-9e9e9e?style=flat-square" alt="Localhost">
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/模块-运维排障-de350b?style=flat-square" alt="Tag"><br>
      <h3>分离式日志与诊断回溯</h3>
      <p>Android 宿主日志与 AzurPilot 业务日志双通道独立归档。异常时自动捕获并持久化当前画面快照；内置一键打包导出功能与 7 天自动轮转过期机制，兼顾排障与存储空间。</p>
      <div>
        <img src="https://img.shields.io/badge/排障-快照自动留存-red?style=flat-square" alt="Snapshot">
        <img src="https://img.shields.io/badge/存储-7天自动清理-green?style=flat-square" alt="Auto Clean">
        <img src="https://img.shields.io/badge/导出-Zip一键打包-blue?style=flat-square" alt="Zip Export">
      </div>
    </td>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/模块-生命周期-ff5630?style=flat-square" alt="Tag"><br>
      <h3>双轨独立热升级体系</h3>
      <p>宿主 App 与底层 Linux Runtime（rootfs）具备完全解耦的分发与升级机制。更新底层环境不破坏用户配置与历史日志；更新应用本体仅需轻量增量包，复用已有 Runtime。</p>
      <div>
        <img src="https://img.shields.io/badge/架构-双轨解耦-purple?style=flat-square" alt="Dual Track">
        <img src="https://img.shields.io/badge/完整性-SHA256校验-blue?style=flat-square" alt="SHA256">
        <img src="https://img.shields.io/badge/数据安全-配置零损耗-brightgreen?style=flat-square" alt="Safe">
      </div>
    </td>
  </tr>
</table>

---

## 系统架构全景

项目采用清晰的分层解耦设计，各层间职责清晰、通讯边界规范：

```mermaid
graph TD
    subgraph AndroidHost ["Android 宿主层 (Kotlin / Jetpack Compose M3)"]
        UI["Compose UI 界面矩阵\n(首页 / 任务编排 / 定时管理 / 全局设置)"]
        Overlay["FloatingX 全局悬浮控制面板"]
        Lifecycle["进程守护与 Runtime 生命周期管理器"]
        Bridge["Shizuku-m / Root su 权限接入中继"]
    end

    subgraph Container ["PRoot 容器隔离层 (Ubuntu 24.04 ARM64)"]
        PROOT["PRoot 虚拟化引擎 (免 Root 系统调用映射)"]
        ENV["CPython 3.14 运行环境\n(OpenCV / RapidOCR / NumPy / uv)"]
        CORE["AzurPilot 自动化调度引擎本体"]
        WEB["React + Vite 静态控制台\n(FastAPI / Uvicorn 监听 127.0.0.1)"]
    end

    subgraph DeviceTarget ["系统能力与受控目标"]
        VDisplay["后台虚拟显示屏 (Virtual Display)"]
        InputService["MaaTouch / DroidCast / ADB 控制流"]
        GameApp["《碧蓝航线》游戏实例"]
    end

    UI --> Lifecycle
    Overlay --> Lifecycle
    Lifecycle --> PROOT
    PROOT --> ENV
    ENV --> CORE
    CORE --> WEB
    UI -. "WebView / 本地回环通讯" .-> WEB
    Bridge --> InputService
    Bridge --> VDisplay
    InputService --> GameApp
    VDisplay --> GameApp
    CORE --> InputService
```

---

## 环境规格与兼容性

<table width="100%">
  <thead>
    <tr>
      <th width="18%">维度</th>
      <th width="28%">准入指标</th>
      <th width="28%">推荐配置</th>
      <th width="26%">说明</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>操作系统</b></td>
      <td><img src="https://img.shields.io/badge/Android-9.0%2B%20(API%2028)-green?style=flat-square" alt="Android 9.0+"></td>
      <td><img src="https://img.shields.io/badge/Android-12%20~%2015-brightgreen?style=flat-square" alt="Android 12~15"></td>
      <td>需满足现代 Android 权限隔离与私有存储机制</td>
    </tr>
    <tr>
      <td><b>处理器架构</b></td>
      <td><img src="https://img.shields.io/badge/CPU-ARM64--v8a-blue?style=flat-square" alt="ARM64"></td>
      <td><img src="https://img.shields.io/badge/CPU-高能效多核架构-blue?style=flat-square" alt="Multi-core"></td>
      <td>依赖 64 位 Linux 二进制包，不支持 32 位设备</td>
    </tr>
    <tr>
      <td><b>机身存储空间</b></td>
      <td><img src="https://img.shields.io/badge/可用空间->=%202.5%20GB-yellow?style=flat-square" alt="2.5GB"></td>
      <td><img src="https://img.shields.io/badge/可用空间->=%205.0%20GB-brightgreen?style=flat-square" alt="5.0GB"></td>
      <td>容纳完整 Ubuntu 根文件系统、依赖库及日志快照</td>
    </tr>
    <tr>
      <td><b>系统运行内存</b></td>
      <td><img src="https://img.shields.io/badge/RAM->=%204%20GB-yellow?style=flat-square" alt="4GB"></td>
      <td><img src="https://img.shields.io/badge/RAM->=%206%20GB%20或以上-brightgreen?style=flat-square" alt="6GB+"></td>
      <td>保证 Python 算法进程与游戏多任务并行稳定</td>
    </tr>
    <tr>
      <td><b>权限支持</b></td>
      <td><img src="https://img.shields.io/badge/方案-Shizuku%20%2F%20Root-orange?style=flat-square" alt="Shizuku / Root"></td>
      <td><img src="https://img.shields.io/badge/方案-Shizuku--m%20(免调试)-brightgreen?style=flat-square" alt="Shizuku-m"></td>
      <td>用于系统级触控模拟与虚拟屏幕投影授权</td>
    </tr>
  </tbody>
</table>

---

## 快速开始

<table width="100%">
  <tr>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/步骤-01-blue?style=flat-square" alt="Step 1"><br>
        <h4>获取安装包</h4>
      </div>
      前往 <a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest">Releases 最新版本</a> 下载<b>完整版 APK</b>（安装包包含预置 Runtime 镜像）。
    </td>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/步骤-02-indigo?style=flat-square" alt="Step 2"><br>
        <h4>初始化解包</h4>
      </div>
      安装完成后启动应用，等待底层 Linux 根文件系统自动释放至沙盒，初次解压需要 1 至 3 分钟。
    </td>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/步骤-03-purple?style=flat-square" alt="Step 3"><br>
        <h4>接入提权服务</h4>
      </div>
      按界面提示连接 <a href="https://github.com/Shinarin/shizuku-m">Shizuku-m</a> 授权，或在设置页面直接切换为 Root 执行后端。
    </td>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/步骤-04-green?style=flat-square" alt="Step 4"><br>
        <h4>配置巡航实例</h4>
      </div>
      在「挂机」页校验画面推流，进入内嵌「AzurPilot」WebUI 页面定制专属自动化出击规则。
    </td>
  </tr>
</table>

> [!TIP]
> 推荐使用 **Shizuku-m**，该分支支持免开启无线调试模式、无外部 WLAN 网络环境下亦能正常启动，极大地提升了移动场景下的稳定性。

> [!IMPORTANT]
> 初次安装务必选择**完整版 APK**。后续若仅有 Android 宿主代码更新，可下载仅十几兆的**轻量增量 APK**，直接覆盖安装即可，无需重新解压 Runtime。

---

## 功能界面一览

<table width="100%">
  <thead>
    <tr>
      <th width="15%">界面模块</th>
      <th width="45%">核心职责与承载能力</th>
      <th width="20%">交互特色</th>
      <th width="20%">状态标志</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>首页看板</b></td>
      <td>呈现系统健康概况、PRoot 容器状态、CPU/内存负荷、运行诊断日志快速通道。</td>
      <td>卡片式看板、一键诊断</td>
      <td><img src="https://img.shields.io/badge/状态-总览枢纽-blue?style=flat-square" alt="Overview"></td>
    </tr>
    <tr>
      <td><b>任务编排</b></td>
      <td>实时预览后台虚拟屏游戏画面、作业队列调度编排、全屏手动触控接管模式。</td>
      <td>低延迟画面推流、实时介入</td>
      <td><img src="https://img.shields.io/badge/状态-执行中枢-orange?style=flat-square" alt="Task"></td>
    </tr>
    <tr>
      <td><b>定时管理</b></td>
      <td>编排计划任务日历、定时触发执行条件、到点自动唤起与离线队列管理。</td>
      <td>规则弹性匹配、周期循环</td>
      <td><img src="https://img.shields.io/badge/状态-计划调度-purple?style=flat-square" alt="Schedule"></td>
    </tr>
    <tr>
      <td><b>AzurPilot WebUI</b></td>
      <td>完整呈现 React 控制台前端，细化出击关卡、配队策略、资源收集、建造管理。</td>
      <td>内嵌无缝 WebView 直连</td>
      <td><img src="https://img.shields.io/badge/状态-引擎大脑-61DAFB?style=flat-square" alt="Engine"></td>
    </tr>
    <tr>
      <td><b>系统设置</b></td>
      <td>管理双轨更新通道、运行时资源配额、显示分辨率参数、日志归档与导出中心。</td>
      <td>偏好持久化、安全校验</td>
      <td><img src="https://img.shields.io/badge/状态-全局配置-lightgrey?style=flat-square" alt="Settings"></td>
    </tr>
    <tr>
      <td><b>全局悬浮窗</b></td>
      <td>穿透显示在任何 App 之上，实时呈现调度进度、提供即时暂停/启动与日志速览。</td>
      <td>最小化胶囊、拖拽自适应</td>
      <td><img src="https://img.shields.io/badge/状态-全域浮窗-brightgreen?style=flat-square" alt="Overlay"></td>
    </tr>
  </tbody>
</table>

---

## 双轨独立更新机制

为确保环境的极致稳定与升级灵活性，系统采用宿主层与运行时解耦的双轨管理策略：

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/通道-宿主%20App%20升级-7F52FF?style=flat-square&logo=android&logoColor=white" alt="App Track"><br>
        <h4>轻量级宿主覆盖更新</h4>
      </div>
      <ul>
        <li><b>触发依据</b>：检测 Android 内部版本号（VersionCode）。</li>
        <li><b>升级载荷</b>：仅包含 Android 原生业务层的轻量 APK。</li>
        <li><b>更新过程</b>：调用系统标准 PackageInstaller 覆盖安装。</li>
        <li><b>数据保障</b>：完全复用手机已解压的 Linux Runtime 与全部已有配置文件。</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/通道-底层%20Runtime%20升级-E95420?style=flat-square&logo=ubuntu&logoColor=white" alt="Runtime Track"><br>
        <h4>解耦式容器镜像热替换</h4>
      </div>
      <ul>
        <li><b>触发依据</b>：读取 GitHub Latest release 中的 <code>latest.json</code> 签名。</li>
        <li><b>完整性验证</b>：下载后进行严格的 SHA-256 哈希比对与文件大小校验。</li>
        <li><b>热替换机制</b>：停止 Python 服务后安全替换容器根目录，保留 <code>/config</code> 及数据卷。</li>
        <li><b>容错回退</b>：网络异常或用户选择稍后时，无缝继续沿用当前稳定版本。</li>
      </ul>
    </td>
  </tr>
</table>

---

## 研发活跃度

<table width="100%">
  <thead>
    <tr>
      <th colspan="3" align="left">
        <img src="https://img.shields.io/badge/Metrics-RepoBeats%20Style-5C3EE8?style=flat-square&logo=github&logoColor=white" alt="RepoBeats">
        <b>仓库研发活跃度与响应态势</b>
      </th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td width="33%">
        <b>代码提交频度</b><br>
        <img src="https://img.shields.io/github/commit-activity/m/wess09/AzurPilot-for-Android?style=flat-square&color=00d4aa" alt="Commit Activity"><br>
        <small>月度提交活跃状态</small>
      </td>
      <td width="33%">
        <b>合并请求处理</b><br>
        <img src="https://img.shields.io/github/issues-pr-closed/wess09/AzurPilot-for-Android?style=flat-square&color=6f42c1" alt="Closed PRs"><br>
        <small>已归档合并请求总计</small>
      </td>
      <td width="33%">
        <b>问题反馈解决</b><br>
        <img src="https://img.shields.io/github/issues-closed/wess09/AzurPilot-for-Android?style=flat-square&color=28a745" alt="Closed Issues"><br>
        <small>已成功解决 Issue 统计</small>
      </td>
    </tr>
    <tr>
      <td width="33%">
        <b>研发分支</b><br>
        <img src="https://img.shields.io/badge/Branch-main-181717?style=flat-square&logo=git&logoColor=white" alt="Main Branch"><br>
        <small>主干持续交付流</small>
      </td>
      <td width="33%">
        <b>自动化构建</b><br>
        <img src="https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white" alt="CI"><br>
        <small>ARM64 Runner 持续验证</small>
      </td>
      <td width="33%">
        <b>最新提交记录</b><br>
        <a href="https://github.com/wess09/AzurPilot-for-Android/commits/main"><img src="https://img.shields.io/github/last-commit/wess09/AzurPilot-for-Android?style=flat-square&color=586069" alt="Last Commit"></a><br>
        <small>追踪最新代码演进</small>
      </td>
    </tr>
  </tbody>
</table>

---

## Star 增长趋势

采用自适应深浅模式的 Star-History 矢量历史图谱，直观反映项目发展脉络：

<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=wess09/AzurPilot-for-Android&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=wess09/AzurPilot-for-Android&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=wess09/AzurPilot-for-Android&type=Date" width="100%" />
  </picture>
  <br>
  <sub>数据源基于 <a href="https://star-history.com/#wess09/AzurPilot-for-Android&Date">Star-History</a> 实时更新 · 点击可查看交互式完整走势</sub>
</div>

---

## 工程构建全景

```
.
|-- app/                      # Android 宿主工程源码 (Kotlin + Jetpack Compose)
|   |-- app/                  # 核心应用模块、Compose UI 实现与状态流
|   |-- annotation-api/       # 注解与接口契约模块
|   |-- ksp-processor/        # 编译期 KSP 代码生成处理器
|   `-- gradle/               # 统一依赖版本目录 (libs.versions.toml)
|-- rootfs/                   # Linux 运行环境打包脚本与预置依赖定义
|   `-- build/                # build-azurpilot.sh 镜像裁剪与集成脚本
|-- .github/workflows/        # CI/CD 自动化流水线 (rootfs.yml)
`-- tools/                    # 构建辅助工具与本地调试脚本
```

- **编译环境规范**：Android Gradle Plugin 8.x、Kotlin 2.x、Jetpack Compose Material 3。
- **自动化构建流**：基于 GitHub Actions ARM64 Runner 执行 Ubuntu 24.04 镜像裁剪、uv 依赖预装、前端打包与 xz 高压缩归档。
- **发布签名安全**：正式签名存储于仓库 Actions Secrets 中，杜绝签名泄漏与非法窜改。

---

## 技术栈与依赖致谢

### 核心运行时与系统底座

| 依赖组件 | 许可证标识 | 职责定位 |
| :--- | :--- | :--- |
| **Ubuntu Base 24.04** | <img src="https://img.shields.io/badge/License-Canonical-lightgrey?style=flat-square" alt="Canonical"> | ARM64 Linux 容器基础底座 |
| **PRoot** | <img src="https://img.shields.io/badge/License-GPL--2.0-blue?style=flat-square" alt="GPL-2.0"> | 免 Root 用户空间系统调用仿真引擎 |
| **uv** | <img src="https://img.shields.io/badge/License-Apache--2.0%20%7C%20MIT-brightgreen?style=flat-square" alt="uv License"> | 现代高性能 Python 包管理工具 |
| **CPython 3.14** | <img src="https://img.shields.io/badge/License-PSF--2.0-blue?style=flat-square" alt="PSF-2.0"> | 核心解释器运行时 |
| **AzurPilot** | <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square" alt="GPL-3.0"> | 核心自动化决策逻辑与任务引擎 |
| **ALAS-AOS** | <img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0"> | Android 容器化运行路线的重要起点与参考基线 |
| **MaaFwApp** | <img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0"> | Android 宿主架构与工程框架模板 |

### Android 宿主技术栈

| 依赖库 | 许可证标识 | 用途说明 |
| :--- | :--- | :--- |
| **AndroidX Suite** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Jetpack Compose、Lifecycle、DataStore 等现代化组件 |
| **Koin** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 轻量级依赖注入框架 |
| **OkHttp** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 稳定高效的 HTTP 网络通讯客户端 |
| **Shizuku API** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 免 Root 进程间授权与交互通信接口 |
| **libsu** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Root 模式下稳健的 Shell 提权调用框架 |
| **FloatingX** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 优雅易用的 Android 全局悬浮窗框架 |
| **XXPermissions** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 动态权限申请与状态管理方案 |
| **Apache Commons Compress** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 运行时归档解压与文件流处理 |
| **XZ for Java** | <img src="https://img.shields.io/badge/License-Public%20Domain-lightgrey?style=flat-square" alt="Public Domain"> | 高压缩比 rootfs 归档格式解析支持 |

---

## 社区生态与支持

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot-for-Android/graphs/contributors">
          <img src="https://img.shields.io/badge/Community-Contributors-blueviolet?style=for-the-badge&logo=github&logoColor=white" alt="Contributors">
        </a>
        <br><br>
        <h4>开源共建与参与</h4>
        <p>感谢所有参与代码提交、架构改进、问题排查与功能验证的开发者。欢迎随时提交 Issue 与 PR 参与共建！</p>
        <a href="https://github.com/wess09/AzurPilot-for-Android/graphs/contributors">
          <img src="https://img.shields.io/badge/查看完整贡献名单-GitHub%20Graph-181717?style=flat-square&logo=github&logoColor=white" alt="View Contributors">
        </a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/issues/new/choose">
          <img src="https://img.shields.io/badge/提交反馈-New%20Issue-0052cc?style=flat-square&logo=githubissues&logoColor=white" alt="New Issue">
        </a>
      </div>
    </td>
    <td width="50%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers">
          <img src="https://img.shields.io/badge/Project-Star%20History-f5a623?style=for-the-badge&logo=star&logoColor=white" alt="Star History">
        </a>
        <br><br>
        <h4>项目成长与支持</h4>
        <p>如果 AzurPilot for Android 为你的游戏挂机体验提供了便利，欢迎前往仓库主页点亮 Star 支持项目演进。</p>
        <a href="https://star-history.com/#wess09/AzurPilot-for-Android&Date">
          <img src="https://img.shields.io/badge/Star%20趋势看板-Star--History-orange?style=flat-square" alt="Star History Link">
        </a>
      </div>
    </td>
  </tr>
</table>

---

## 许可证说明

本项目采用 [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE) 协议开源。

随项目一同分发的许可证文件：
- [`LICENSE-ALAS-AOS`](LICENSE-ALAS-AOS) — AGPL-3.0
- [`LICENSE-MaaFwApp`](LICENSE-MaaFwApp) — AGPL-3.0
- [`LICENSE-AzurPilot`](LICENSE-AzurPilot) — GPL-3.0

各 Python 依赖项及传递依赖的原许可证文本（共 152 项），均已随运行时镜像归档于设备端 `/opt/azurpilot/licenses`。

---

<div align="center">
  <sub>AzurPilot for Android 遵守开源协议并由社区驱动维护。如遇问题欢迎提交 Issue 或 Pull Request。</sub>
</div>
