<div align="center">

<img src="app/app/src/main/res/drawable-nodpi/azurpilot_android_logo.png" alt="AzurPilot for Android Logo" width="340">

# AzurPilot for Android

**No PC or Dedicated Server Required · Native Lightweight Execution · Seamless Background Virtual Display Automation**

An all-in-one integrated Android runtime environment tailored for the full-featured *Azur Lane* automation tool [AzurPilot](https://github.com/wess09/AzurPilot)

This repository is forked from [ALAS-AOS](https://github.com/Shinarin/ALAS-AOS), inheriting its host architecture, its PRoot containerization approach, its rootless privilege escalation design, and the AGPL-3.0 license.

<p align="center">
  <a href="README.md">简体中文</a> |
  <b>English</b> |
  <a href="README_ja.md">日本語</a> |
  <a href="README_zh-TW.md">繁體中文</a>
</p>

---

<!-- Core Environment & Platform Badges -->
<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-blue.svg?style=flat-square" alt="License: AGPL-3.0"></a>
  <a href="https://developer.android.com/about/versions/pie"><img src="https://img.shields.io/badge/Platform-Android%209.0%2B%20(API%2028%2B)-3DDC84.svg?style=flat-square&logo=android&logoColor=white" alt="Platform: Android 9.0+"></a>
  <a href="https://en.wikipedia.org/wiki/AArch64"><img src="https://img.shields.io/badge/Architecture-ARM64--v8a%20%2F%20x86__64-E10098.svg?style=flat-square&logo=arm&logoColor=white" alt="Arch: ARM64"></a>
  <a href="https://ubuntu.com/"><img src="https://img.shields.io/badge/Runtime-Ubuntu%2024.04%20LTS-E95420.svg?style=flat-square&logo=ubuntu&logoColor=white" alt="Runtime: Ubuntu 24.04"></a>
  <a href="https://www.python.org/"><img src="https://img.shields.io/badge/Python-3.14-3776AB.svg?style=flat-square&logo=python&logoColor=white" alt="Python 3.14"></a>
  <a href="https://github.com/astral-sh/uv"><img src="https://img.shields.io/badge/Packaging-uv-DE5FE9.svg?style=flat-square" alt="Packaging: uv"></a>
</p>

<!-- Tech Stack & Framework Badges -->
<p align="center">
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Host-Kotlin%20%7C%20Compose%20M3-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Host: Kotlin / Jetpack Compose"></a>
  <a href="https://react.dev/"><img src="https://img.shields.io/badge/WebUI-React%20%7C%20Vite-61DAFB.svg?style=flat-square&logo=react&logoColor=black" alt="WebUI: React + Vite"></a>
  <a href="https://github.com/wess09/shizuku-m"><img src="https://img.shields.io/badge/Backend-Shizuku%20%2F%20Root-brightgreen.svg?style=flat-square" alt="Backend: Shizuku / Root"></a>
  <a href="https://opencv.org/"><img src="https://img.shields.io/badge/Vision-OpenCV%20%7C%20RapidOCR-5C3EE8.svg?style=flat-square&logo=opencv&logoColor=white" alt="Vision: OpenCV + RapidOCR"></a>
  <a href="https://proot-me.github.io/"><img src="https://img.shields.io/badge/Isolation-PRoot-lightgrey.svg?style=flat-square" alt="Isolation: PRoot"></a>
  <a href="https://deepwiki.com/wess09/AzurPilot-for-Android"><img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki"></a>
</p>

<!-- Repo Metrics Badges -->
<p align="center">
  <a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest"><img src="https://img.shields.io/github/v/release/wess09/AzurPilot-for-Android?style=flat-square&color=007ec6&label=Latest%20Release" alt="Latest Release"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/releases"><img src="https://img.shields.io/github/downloads/wess09/AzurPilot-for-Android/total?style=flat-square&color=28a745&label=Downloads" alt="Total Downloads"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers"><img src="https://img.shields.io/github/stars/wess09/AzurPilot-for-Android?style=flat-square&color=f5a623&label=Stars" alt="Stars"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/network/members"><img src="https://img.shields.io/github/forks/wess09/AzurPilot-for-Android?style=flat-square&color=6f42c1&label=Forks" alt="Forks"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/issues"><img src="https://img.shields.io/github/issues/wess09/AzurPilot-for-Android?style=flat-square&color=d73a49&label=Issues" alt="Open Issues"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/commits/main"><img src="https://img.shields.io/github/last-commit/wess09/AzurPilot-for-Android?style=flat-square&color=586069&label=Last%20Commit" alt="Last Commit"></a>
</p>

<p align="center">
  <a href="#project-overview--metrics">Overview</a> •
  <a href="#knowledge-base--ai-qa">AI Q&A</a> •
  <a href="#introduction">Introduction</a> •
  <a href="#related-ecosystem-projects">Ecosystem</a> •
  <a href="#key-highlights">Highlights</a> •
  <a href="#system-architecture">Architecture</a> •
  <a href="#specifications--compatibility">Specs</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#app-previews">App Previews</a> •
  <a href="#ui-showcase">UI Showcase</a> •
  <a href="#dual-track-independent-updates">Dual-Track Updates</a> •
  <a href="#development-activity">Activity</a> •
  <a href="#star-history">Star History</a> •
  <a href="#community--support">Community</a>
</p>

</div>

---

## Project Overview & Metrics

<table width="100%">
  <thead>
    <tr>
      <th colspan="4" align="left">
        <img src="https://img.shields.io/badge/Project%20Overview-AzurPilot%20for%20Android-181717?style=flat-square&logo=github&logoColor=white" alt="Overview">
        <b>Core Operational & Development Metrics</b>
      </th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td width="25%"><b>Latest Release</b><br><a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest"><img src="https://img.shields.io/github/v/release/wess09/AzurPilot-for-Android?style=flat-square&color=007ec6" alt="Release"></a></td>
      <td width="25%"><b>Total Downloads</b><br><a href="https://github.com/wess09/AzurPilot-for-Android/releases"><img src="https://img.shields.io/github/downloads/wess09/AzurPilot-for-Android/total?style=flat-square&color=28a745" alt="Downloads"></a></td>
      <td width="25%"><b>Open Source License</b><br><a href="./LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0"></a></td>
      <td width="25%"><b>Main Branch Status</b><br><a href="https://github.com/wess09/AzurPilot-for-Android/actions"><img src="https://img.shields.io/badge/Build-Passing-brightgreen?style=flat-square&logo=githubactions&logoColor=white" alt="Build Status"></a></td>
    </tr>
    <tr>
      <td width="25%"><b>Repository Size</b><br><img src="https://img.shields.io/github/repo-size/wess09/AzurPilot-for-Android?style=flat-square&color=586069" alt="Repo Size"></td>
      <td width="25%"><b>Languages</b><br><img src="https://img.shields.io/badge/Language-Kotlin%20%7C%20Shell-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Language"></td>
      <td width="25%"><b>Privilege Backend</b><br><img src="https://img.shields.io/badge/Backend-Shizuku--m%20%7C%20Root-brightgreen?style=flat-square" alt="Backend"></td>
      <td width="25%"><b>Target System</b><br><img src="https://img.shields.io/badge/Android-API%2028%2B%20(ARM64%20%2F%20x86__64)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android Target"></td>
    </tr>
    <tr>
      <td colspan="4">
        <b>Quick Links:</b>
        <a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest"><img src="https://img.shields.io/badge/Release-Download%20APK-0052cc?style=flat-square&logo=android&logoColor=white" alt="Download"></a>
        <a href="https://deepwiki.com/wess09/AzurPilot-for-Android"><img src="https://img.shields.io/badge/Wiki-Ask%20DeepWiki-6366F1?style=flat-square" alt="Ask DeepWiki"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/issues/new/choose"><img src="https://img.shields.io/badge/Issue-Bug%20Report%20%26%20Request-d73a49?style=flat-square&logo=githubissues&logoColor=white" alt="New Issue"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/pulls"><img src="https://img.shields.io/badge/PR-Contribute%20Code-28a745?style=flat-square&logo=git&logoColor=white" alt="Pull Request"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers"><img src="https://img.shields.io/badge/Star-Follow%20Project-f5a623?style=flat-square&logo=github&logoColor=white" alt="Star"></a>
      </td>
    </tr>
  </tbody>
</table>

---

## Knowledge Base & AI Q&A

<table width="100%">
  <tr>
    <td width="70%" valign="middle">
      <img src="https://img.shields.io/badge/AI%20Doc-DeepWiki-6366F1?style=flat-square" alt="DeepWiki Tag"><br>
      <h3>Ask DeepWiki Codebase Q&A</h3>
      <p>Have questions about internal modules, PRoot sandboxing, Virtual Display rendering, or task scheduling rules? Ask DeepWiki directly—it is continuously indexed with the complete repository context for targeted architectural breakdown and code guidance.</p>
      <div>
        <a href="https://deepwiki.com/wess09/AzurPilot-for-Android">
          <img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki">
        </a>
        <a href="https://deepwiki.com/wess09/AzurPilot-for-Android">
          <img src="https://img.shields.io/badge/Knowledge%20Base-Instant%20Q%26A-0052cc?style=flat-square" alt="Ask Question">
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
      <h3>Finding AzurPilot for Android Helpful?</h3>
      <p>Starring this project is the greatest encouragement for the maintainers to keep optimizing and innovating. Click the button on the right to join our stargazers!</p>
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

## Introduction

**AzurPilot for Android** is dedicated to bringing the mature, desktop-grade *Azur Lane* automation assistant directly to native Android devices.

By embedding a full Linux runtime container (Ubuntu 24.04, shipped per device architecture as arm64-v8a and x86_64 Runtime variants) inside the APK package, coupled with lightweight PRoot container isolation, it achieves **completely Root-free** and reliable execution of CPython, precompiled OCR models, and local WebUI services. Leveraging background virtual displays and accessibility services, users can chat, play games, or work on their device's main screen without interruption while automated sortie tasks run seamlessly in the background.

---

## Related Ecosystem Projects

This project works closely with its related core projects; key dependencies and source projects are listed below:

<table width="100%">
  <tr>
    <td width="33%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot-for-Android">
          <img src="https://img.shields.io/badge/Repository-AzurPilot--for--Android-181717?style=for-the-badge&logo=github&logoColor=white" alt="AzurPilot-for-Android">
        </a>
      </div>
      <br>
      <b>Host Runtime (This Project)</b>
      <p>Integrated Android runtime base: PRoot rootless container, background Virtual Display, global overlay, and lifecycle management.</p>
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
      <b>Core Automation Engine</b>
      <p>The core *Azur Lane* automation tool, featuring computer vision recognition, sortie scheduling algorithms, and a local Web console.</p>
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
      <b>Source Project (this repo is forked from it)</b>
      <p>A pioneering implementation of Linux runtimes and rootless privilege escalation on mobile Android, from which this repo's host architecture and containerization approach evolved.</p>
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

## Key Highlights

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/Module-Deployment-0052cc?style=flat-square" alt="Tag"><br>
      <h3>Zero-Config One-Click Deployment</h3>
      <p>The entire runtime environment (Ubuntu Base, Python 3.14, scientific computing and computer vision dependencies, pre-bundled OCR model weights, and WebUI assets) is packaged inside the APK. It unpacks automatically into the private sandbox upon initial launch—no cross-compilation setup or huge online downloads required.</p>
      <div>
        <img src="https://img.shields.io/badge/Builtin%20Container-Ubuntu%2024.04-orange?style=flat-square" alt="Ubuntu">
        <img src="https://img.shields.io/badge/Package%20Manager-uv%20Preconfigured-blueviolet?style=flat-square" alt="uv">
        <img src="https://img.shields.io/badge/Experience-Out%20of%20the%20Box-brightgreen?style=flat-square" alt="Ready">
      </div>
    </td>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/Module-Running%20Mode-00875a?style=flat-square" alt="Tag"><br>
      <h3>Seamless Background Virtual Display</h3>
      <p>Leveraging Android's Virtual Display mechanism, both the game instance and screen capture operate entirely on an isolated display layer. The foreground main screen remains free for daily apps, chat, or gaming with zero visual obstruction or input interruption.</p>
      <div>
        <img src="https://img.shields.io/badge/Display%20Layer-Virtual%20Display-blue?style=flat-square" alt="Virtual Display">
        <img src="https://img.shields.io/badge/Foreground-Unrestricted-green?style=flat-square" alt="Free Foreground">
        <img src="https://img.shields.io/badge/Interference-Zero%20Occlusion-teal?style=flat-square" alt="Zero Interference">
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/Module-Interaction-403294?style=flat-square" alt="Tag"><br>
      <h3>Multi-Dimensional Floating Overlay</h3>
      <p>Built-in lightweight global floating controller accessible from any app or home screen. Quickly start/pause tasks, monitor scheduling queues, switch instances, and scroll real-time runtime log feeds with a single tap.</p>
      <div>
        <img src="https://img.shields.io/badge/Framework-FloatingX-blueviolet?style=flat-square" alt="FloatingX">
        <img src="https://img.shields.io/badge/Controls-Instant%20Start%2FStop-orange?style=flat-square" alt="Quick Action">
        <img src="https://img.shields.io/badge/Accessibility-Systemwide%20Overlay-lightgrey?style=flat-square" alt="Global Overlay">
      </div>
    </td>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/Module-Console-172b4d?style=flat-square" alt="Tag"><br>
      <h3>Complete Embedded WebUI Experience</h3>
      <p>Directly runs the native React + Vite management interface on device. Offers fine-grained instance configurations, sortie strategy plans, schedule automation rules, and live dashboards without requiring an external desktop browser.</p>
      <div>
        <img src="https://img.shields.io/badge/Frontend-React%20%2B%20Vite-61DAFB?style=flat-square&logo=react&logoColor=black" alt="React">
        <img src="https://img.shields.io/badge/Protocol-FastAPI%20%2F%20Uvicorn-009688?style=flat-square" alt="FastAPI">
        <img src="https://img.shields.io/badge/Network-Loopback%20Listening-9e9e9e?style=flat-square" alt="Localhost">
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/Module-Diagnostics-de350b?style=flat-square" alt="Tag"><br>
      <h3>Isolated Logging & Visual Diagnostic History</h3>
      <p>Android host logs and AzurPilot core logs are archived independently. An error snapshot is automatically recorded upon exceptions; includes one-tap diagnostic zip export and a 7-day auto-rotation policy to optimize storage usage.</p>
      <div>
        <img src="https://img.shields.io/badge/Diagnostics-Auto%20Snapshot%20Capture-red?style=flat-square" alt="Snapshot">
        <img src="https://img.shields.io/badge/Storage-7--Day%20Auto%20Pruning-green?style=flat-square" alt="Auto Clean">
        <img src="https://img.shields.io/badge/Export-One--Tap%20Zip%20Archive-blue?style=flat-square" alt="Zip Export">
      </div>
    </td>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/Module-Lifecycle-ff5630?style=flat-square" alt="Tag"><br>
      <h3>Dual-Track Independent Hot Updates</h3>
      <p>The host app and the underlying Linux Runtime (rootfs) possess decoupled distribution and upgrade channels. Updating the container preserves all configs and logs; updating the host requires only a lightweight APK without re-unpacking the runtime.</p>
      <div>
        <img src="https://img.shields.io/badge/Architecture-Dual%20Track%20Decoupled-purple?style=flat-square" alt="Dual Track">
        <img src="https://img.shields.io/badge/Integrity-SHA256%20Verified-blue?style=flat-square" alt="SHA256">
        <img src="https://img.shields.io/badge/Safety-Zero%20Config%20Loss-brightgreen?style=flat-square" alt="Safe">
      </div>
    </td>
  </tr>
</table>

---

## System Architecture

The project adopts a modular, decoupled layered architecture with clearly demarcated communication boundaries:

```mermaid
graph TD
    subgraph AndroidHost ["Android Host Layer (Kotlin / Jetpack Compose M3)"]
        UI["Compose UI Matrix\n(Home / Task Board / Schedule / Settings)"]
        Overlay["FloatingX Systemwide Floating Controller"]
        Lifecycle["Process Supervisor & Runtime Lifecycle Manager"]
        Bridge["Shizuku-m / Root su Privilege Relay"]
    end

    subgraph Container ["PRoot Sandbox Layer (Ubuntu 24.04 · arm64 / x86_64)"]
        PROOT["PRoot Virtualization Engine (Syscall Mapping)"]
        ENV["CPython 3.14 Runtime\n(OpenCV / RapidOCR / NumPy / uv)"]
        CORE["AzurPilot Automation Scheduling Engine"]
        WEB["React + Vite WebUI Console\n(FastAPI / Uvicorn on 127.0.0.1)"]
    end

    subgraph DeviceTarget ["System Capabilities & Target Apps"]
        VDisplay["Background Virtual Display"]
        InputService["MaaTouch / DroidCast / ADB Control Flow"]
        GameApp["Azur Lane Game Instance"]
    end

    UI --> Lifecycle
    Overlay --> Lifecycle
    Lifecycle --> PROOT
    PROOT --> ENV
    ENV --> CORE
    CORE --> WEB
    UI -. "WebView / Localhost Loopback" .-> WEB
    Bridge --> InputService
    Bridge --> VDisplay
    InputService --> GameApp
    VDisplay --> GameApp
    CORE --> InputService
```

---

## Specifications & Compatibility

<table width="100%">
  <thead>
    <tr>
      <th width="18%">Dimension</th>
      <th width="28%">Minimum Requirement</th>
      <th width="28%">Recommended Config</th>
      <th width="26%">Notes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>Operating System</b></td>
      <td><img src="https://img.shields.io/badge/Android-9.0%2B%20(API%2028)-green?style=flat-square" alt="Android 9.0+"></td>
      <td><img src="https://img.shields.io/badge/Android-12%20~%2015-brightgreen?style=flat-square" alt="Android 12~15"></td>
      <td>Requires modern Android permission isolation and scoped storage</td>
    </tr>
    <tr>
      <td><b>Processor Architecture</b></td>
      <td><img src="https://img.shields.io/badge/CPU-ARM64--v8a%20%2F%20x86__64-blue?style=flat-square" alt="ARM64"></td>
      <td><img src="https://img.shields.io/badge/CPU-High%20Efficiency%20Multi--Core-blue?style=flat-square" alt="Multi-core"></td>
      <td>Relies on 64-bit Linux binaries; 32-bit devices are not supported</td>
    </tr>
    <tr>
      <td><b>Storage Space</b></td>
      <td><img src="https://img.shields.io/badge/Free%20Space->=%202.5%20GB-yellow?style=flat-square" alt="2.5GB"></td>
      <td><img src="https://img.shields.io/badge/Free%20Space->=%205.0%20GB-brightgreen?style=flat-square" alt="5.0GB"></td>
      <td>Accommodates rootfs, dependencies, models, and diagnostic snapshots</td>
    </tr>
    <tr>
      <td><b>System RAM</b></td>
      <td><img src="https://img.shields.io/badge/RAM->=%204%20GB-yellow?style=flat-square" alt="4GB"></td>
      <td><img src="https://img.shields.io/badge/RAM->=%206%20GB%2B-brightgreen?style=flat-square" alt="6GB+"></td>
      <td>Ensures stability during concurrent execution of Python algorithms and game</td>
    </tr>
    <tr>
      <td><b>Privilege Backend</b></td>
      <td><img src="https://img.shields.io/badge/Backend-Shizuku%20%2F%20Root-orange?style=flat-square" alt="Shizuku / Root"></td>
      <td><img src="https://img.shields.io/badge/Backend-Shizuku--m%20(No%20Debugging)-brightgreen?style=flat-square" alt="Shizuku-m"></td>
      <td>Required for input simulation and Virtual Display projection authorization; MediaTek devices should use the patched build linked below</td>
    </tr>
  </tbody>
</table>

---

## Quick Start

<table width="100%">
  <tr>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/Step-01-blue?style=flat-square" alt="Step 1"><br>
        <h4>Get the APK</h4>
      </div>
      Go to <a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest">Releases</a> and download the <b>Full APK matching your device architecture</b> (arm64-v8a / x86_64; each bundles its pre-built Runtime rootfs image).
    </td>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/Step-02-indigo?style=flat-square" alt="Step 2"><br>
        <h4>Initial Extraction</h4>
      </div>
      Launch the app after installation and wait for the rootfs to unpack into the sandbox (approx. 1–3 minutes on first boot).
    </td>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/Step-03-purple?style=flat-square" alt="Step 3"><br>
        <h4>Grant Permissions</h4>
      </div>
      Authorize via <a href="https://github.com/wess09/shizuku-m/releases/tag/v13.6.0-m2.r1093.bcb2b62a">Shizuku-m</a> according to the prompt, or switch to the Root backend in Settings.
    </td>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/Step-04-green?style=flat-square" alt="Step 4"><br>
        <h4>Configure Automation</h4>
      </div>
      Check video streaming on the "Task Board" page, then enter the embedded "AzurPilot" WebUI to configure your sortie schedule.
    </td>
  </tr>
</table>

> [!TIP]
> **[Shizuku-m](https://github.com/wess09/shizuku-m)** — the patched build maintained by this project — is highly recommended: it operates without wireless debugging, works reliably without external Wi-Fi networks, and fixes the user-service startup failure on MediaTek devices. See the note below.

> [!WARNING]
> **On MediaTek devices use the [patched Shizuku-m build](https://github.com/wess09/shizuku-m/releases/tag/v13.6.0-m2.r1093.bcb2b62a), not the official 13.6.x.** Since 13.6, Shizuku initializes the privileged user-service process with an `Application`, which hits MediaTek's resource-preload hook injected into `LoadedApk.makeApplication` (`procName` is null → NPE) and the process immediately calls `System.exit(1)`. The symptom is that Shizuku is authorized and its binder is reachable, yet the privileged service always times out, and `service_boot_debug.log` is never written under `debug/`. Acknowledged by the official project but still unfixed ([#1198](https://github.com/RikkaApps/Shizuku/issues/1198) / [#1171](https://github.com/RikkaApps/Shizuku/issues/1171)); the patched build falls back to the pre-13.6 Context-only path.

> [!IMPORTANT]
> The **Full APK** bundles Runtime and unpacks it locally on first launch, with no network needed; the **Incremental APK** carries no Runtime and downloads the ~1GB Runtime for your device architecture from GitHub on first launch (Wi-Fi recommended; multi-mirror and multi-threaded segmented download supported, configurable in Settings). For later host-only updates, install the incremental APK over the existing app to reuse the Runtime already on disk.

---

## App Previews

#### 简体中文

| | Home | AzurPilot | Settings | Virtual Display |
|:---:|:---:|:---:|:---:|:---:|
| **Dark** | <img src="docs/screenshots/zh-CN-dark-home.jpg" width="260"/> | <img src="docs/screenshots/zh-CN-dark-overview.jpg" width="260"/> | <img src="docs/screenshots/zh-CN-dark-settings.jpg" width="260"/> | <img src="docs/screenshots/zh-CN-dark-virtual-screen.jpg" width="260"/> |
| **Light** | <img src="docs/screenshots/zh-CN-light-home.jpg" width="260"/> | <img src="docs/screenshots/zh-CN-light-overview.jpg" width="260"/> | <img src="docs/screenshots/zh-CN-light-settings.jpg" width="260"/> | <img src="docs/screenshots/zh-CN-light-virtual-screen.jpg" width="260"/> |

#### 繁體中文

| | Home | AzurPilot | Settings | Virtual Display |
|:---:|:---:|:---:|:---:|:---:|
| **Dark** | <img src="docs/screenshots/zh-TW-dark-home.jpg" width="260"/> | <img src="docs/screenshots/zh-TW-dark-overview.jpg" width="260"/> | <img src="docs/screenshots/zh-TW-dark-settings.jpg" width="260"/> | <img src="docs/screenshots/zh-TW-dark-virtual-screen.jpg" width="260"/> |
| **Light** | <img src="docs/screenshots/zh-TW-light-home.jpg" width="260"/> | <img src="docs/screenshots/zh-TW-light-overview.jpg" width="260"/> | <img src="docs/screenshots/zh-TW-light-settings.jpg" width="260"/> | <img src="docs/screenshots/zh-TW-light-virtual-screen.jpg" width="260"/> |

#### English

| | Home | AzurPilot | Settings | Virtual Display |
|:---:|:---:|:---:|:---:|:---:|
| **Dark** | <img src="docs/screenshots/en-dark-home.jpg" width="260"/> | <img src="docs/screenshots/en-dark-overview.jpg" width="260"/> | <img src="docs/screenshots/en-dark-settings.jpg" width="260"/> | <img src="docs/screenshots/en-dark-virtual-screen.jpg" width="260"/> |
| **Light** | <img src="docs/screenshots/en-light-home.jpg" width="260"/> | <img src="docs/screenshots/en-light-overview.jpg" width="260"/> | <img src="docs/screenshots/en-light-settings.jpg" width="260"/> | <img src="docs/screenshots/en-light-virtual-screen.jpg" width="260"/> |

#### 日本語

| | Home | AzurPilot | Settings | Virtual Display |
|:---:|:---:|:---:|:---:|:---:|
| **Dark** | <img src="docs/screenshots/ja-dark-home.jpg" width="260"/> | <img src="docs/screenshots/ja-dark-overview.jpg" width="260"/> | <img src="docs/screenshots/ja-dark-settings.jpg" width="260"/> | <img src="docs/screenshots/ja-dark-virtual-screen.jpg" width="260"/> |
| **Light** | <img src="docs/screenshots/ja-light-home.jpg" width="260"/> | <img src="docs/screenshots/ja-light-overview.jpg" width="260"/> | <img src="docs/screenshots/ja-light-settings.jpg" width="260"/> | <img src="docs/screenshots/ja-light-virtual-screen.jpg" width="260"/> |

---

## UI Showcase

<table width="100%">
  <thead>
    <tr>
      <th width="15%">Module</th>
      <th width="45%">Core Functionality</th>
      <th width="20%">Interaction</th>
      <th width="20%">Status Tag</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>Dashboard</b></td>
      <td>Presents overall system health, PRoot status, CPU/RAM utilization, and quick diagnostic links.</td>
      <td>Card metrics, 1-tap diagnosis</td>
      <td><img src="https://img.shields.io/badge/Status-Overview%20Hub-blue?style=flat-square" alt="Overview"></td>
    </tr>
    <tr>
      <td><b>Task Board</b></td>
      <td>Live preview of background Virtual Display, sortie queue management, and manual touch takeover.</td>
      <td>Low-latency stream, live takeover</td>
      <td><img src="https://img.shields.io/badge/Status-Execution%20Hub-orange?style=flat-square" alt="Task"></td>
    </tr>
    <tr>
      <td><b>Scheduler</b></td>
      <td>Calendar-based task planning, conditional triggers, automatic wakeup, and offline queuing.</td>
      <td>Flexible rule matching, cyclic loops</td>
      <td><img src="https://img.shields.io/badge/Status-Scheduler-purple?style=flat-square" alt="Schedule"></td>
    </tr>
    <tr>
      <td><b>AzurPilot Console</b></td>
      <td>Native in-app console: task configuration, instance management, live logs, statistics dashboards, and Runtime updates — UI generated from the upstream schema.</td>
      <td>Native rendering, no context switch</td>
      <td><img src="https://img.shields.io/badge/Status-Engine%20Core-61DAFB?style=flat-square" alt="Engine"></td>
    </tr>
    <tr>
      <td><b>Settings</b></td>
      <td>Manage dual-track update channels, runtime quotas, virtual display resolutions, and log exports.</td>
      <td>Persistent preferences, SHA checks</td>
      <td><img src="https://img.shields.io/badge/Status-Global%20Config-lightgrey?style=flat-square" alt="Settings"></td>
    </tr>
    <tr>
      <td><b>Floating Overlay</b></td>
      <td>Overlays systemwide over any app, displaying current progress with instant pause/resume and live logs.</td>
      <td>Mini capsule, draggable</td>
      <td><img src="https://img.shields.io/badge/Status-Floating%20Window-brightgreen?style=flat-square" alt="Overlay"></td>
    </tr>
  </tbody>
</table>

---

## Dual-Track Independent Updates

To maximize stability and update flexibility, the host application and Linux runtime are managed via decoupled channels:

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/Track-Host%20App%20Update-7F52FF?style=flat-square&logo=android&logoColor=white" alt="App Track"><br>
        <h4>Lightweight Host Overwrite</h4>
      </div>
      <ul>
        <li><b>Trigger</b>: Monitored via Android internal <code>VersionCode</code>.</li>
        <li><b>Payload</b>: Lightweight APK containing only native Android code.</li>
        <li><b>Process</b>: Standard system <code>PackageInstaller</code> in-place upgrade.</li>
        <li><b>Data Safety</b>: Completely preserves existing Linux rootfs, user configurations, and logs.</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/Track-Runtime%20Update-E95420?style=flat-square&logo=ubuntu&logoColor=white" alt="Runtime Track"><br>
        <h4>Decoupled Container Hot Replacement</h4>
      </div>
      <ul>
        <li><b>Trigger</b>: Checked against <code>latest.json</code> signature in GitHub Releases.</li>
        <li><b>Verification</b>: Strict SHA-256 hash and byte length check after download.</li>
        <li><b>Mechanism</b>: Gracefully stops Python services, swaps container rootfs, and preserves <code>/config</code> and data mounts.</li>
        <li><b>Fallback</b>: Continues running current version smoothly if network fails or user postpones.</li>
      </ul>
    </td>
  </tr>
</table>

---

## Development Activity

<table width="100%">
  <thead>
    <tr>
      <th colspan="3" align="left">
        <img src="https://img.shields.io/badge/Metrics-RepoBeats%20Style-5C3EE8?style=flat-square&logo=github&logoColor=white" alt="RepoBeats">
        <b>Repository Velocity & Responsiveness</b>
      </th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td width="33%">
        <b>Commit Frequency</b><br>
        <img src="https://img.shields.io/github/commit-activity/m/wess09/AzurPilot-for-Android?style=flat-square&color=00d4aa" alt="Commit Activity"><br>
        <small>Monthly commit activity</small>
      </td>
      <td width="33%">
        <b>Pull Requests Closed</b><br>
        <img src="https://img.shields.io/github/issues-pr-closed/wess09/AzurPilot-for-Android?style=flat-square&color=6f42c1" alt="Closed PRs"><br>
        <small>Total merged/closed PRs</small>
      </td>
      <td width="33%">
        <b>Issues Resolved</b><br>
        <img src="https://img.shields.io/github/issues-closed/wess09/AzurPilot-for-Android?style=flat-square&color=28a745" alt="Closed Issues"><br>
        <small>Successfully closed issues</small>
      </td>
    </tr>
    <tr>
      <td width="33%">
        <b>Active Branch</b><br>
        <img src="https://img.shields.io/badge/Branch-main-181717?style=flat-square&logo=git&logoColor=white" alt="Main Branch"><br>
        <small>Continuous delivery trunk</small>
      </td>
      <td width="33%">
        <b>Automated CI/CD</b><br>
        <img src="https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white" alt="CI"><br>
        <small>Native runner matrix verification</small>
      </td>
      <td width="33%">
        <b>Latest Commit</b><br>
        <a href="https://github.com/wess09/AzurPilot-for-Android/commits/main"><img src="https://img.shields.io/github/last-commit/wess09/AzurPilot-for-Android?style=flat-square&color=586069" alt="Last Commit"></a><br>
        <small>Track newest code evolution</small>
      </td>
    </tr>
  </tbody>
</table>

---

## Star History

Adaptive dark/light mode Star History chart reflecting project growth:

<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=wess09/AzurPilot-for-Android&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=wess09/AzurPilot-for-Android&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=wess09/AzurPilot-for-Android&type=Date" width="100%" />
  </picture>
  <br>
  <sub>Real-time data powered by <a href="https://star-history.com/#wess09/AzurPilot-for-Android&Date">Star-History</a> · Click to explore interactive graph</sub>
</div>

---

## Project Directory & Architecture

```
.
|-- app/                      # Android Host project source (Kotlin + Jetpack Compose)
|   |-- app/                  # Core application module, Compose UI, and state flow
|   |-- annotation-api/       # Annotation definitions and interface contracts
|   |-- ksp-processor/        # Compile-time KSP code generation processor
|   `-- gradle/               # Version catalog (libs.versions.toml)
|-- rootfs/                   # Linux runtime packaging scripts & dependency definitions
|   `-- build/                # build-azurpilot.sh rootfs slimming and bundling script
|-- .github/workflows/        # CI/CD pipeline automation (rootfs.yml)
`-- tools/                    # Build tooling and local debugging scripts
```

- **Build Tooling Standards**: Android Gradle Plugin 8.x, Kotlin 2.x, Jetpack Compose Material 3.
- **Automated Pipeline**: a matrix of native GitHub Actions runners (arm64 / x86_64) executes Ubuntu 24.04 slimming, uv dependency pre-installation, WebUI packaging, and high-ratio xz compression, publishing one artifact per architecture.
- **Release Security**: Production signing keys are secured via GitHub Actions Secrets to prevent tampering.

---

## Tech Stack & Dependency Acknowledgments

### Core Runtime & System Foundation

| Component | License Badge | Role |
| :--- | :--- | :--- |
| **Ubuntu Base 24.04** | <img src="https://img.shields.io/badge/License-Canonical-lightgrey?style=flat-square" alt="Canonical"> | Per-architecture Linux container base (arm64 / amd64) |
| **PRoot** | <img src="https://img.shields.io/badge/License-GPL--2.0-blue?style=flat-square" alt="GPL-2.0"> | Rootless userspace syscall emulation engine |
| **uv** | <img src="https://img.shields.io/badge/License-Apache--2.0%20%7C%20MIT-brightgreen?style=flat-square" alt="uv License"> | High-performance modern Python package manager |
| **CPython 3.14** | <img src="https://img.shields.io/badge/License-PSF--2.0-blue?style=flat-square" alt="PSF-2.0"> | Core Python execution runtime |
| **AzurPilot** | <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square" alt="GPL-3.0"> | Core automation logic and task engine |
| **ALAS-AOS** | <img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0"> | Source repository of this project; this repo is forked from it and inherits its host architecture and containerization approach |
| **MaaFwApp** | <img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0"> | Android host architecture and app framework template |

### Android Host Stack

| Library | License Badge | Usage |
| :--- | :--- | :--- |
| **AndroidX Suite** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Modern components: Jetpack Compose, Lifecycle, DataStore |
| **Koin** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Pragmatic lightweight dependency injection |
| **OkHttp** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | High-performance HTTP client |
| **Shizuku API** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Rootless inter-process privilege and IPC interface |
| **libsu** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Robust Shell execution framework under Root mode |
| **FloatingX** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Versatile Android systemwide floating window framework |
| **XXPermissions** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Runtime permission handling and state tracking |
| **Apache Commons Compress** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Archive stream extraction and file processing |
| **XZ for Java** | <img src="https://img.shields.io/badge/License-Public%20Domain-lightgrey?style=flat-square" alt="Public Domain"> | High-compression ratio rootfs archive decompression |

---

## Community & Support

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot-for-Android/graphs/contributors">
          <img src="https://img.shields.io/badge/Community-Contributors-blueviolet?style=for-the-badge&logo=github&logoColor=white" alt="Contributors">
        </a>
        <br><br>
        <h4>Open Source Collaboration</h4>
        <p>Warm thanks to all developers contributing code, architecture reviews, debugging, and testing. Pull requests and feedback are always welcome!</p>
        <a href="https://github.com/wess09/AzurPilot-for-Android/graphs/contributors">
          <img src="https://img.shields.io/badge/Contributors-GitHub%20Graph-181717?style=flat-square&logo=github&logoColor=white" alt="View Contributors">
        </a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/issues/new/choose">
          <img src="https://img.shields.io/badge/Feedback-New%20Issue-0052cc?style=flat-square&logo=githubissues&logoColor=white" alt="New Issue">
        </a>
      </div>
    </td>
    <td width="50%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers">
          <img src="https://img.shields.io/badge/Project-Star%20History-f5a623?style=for-the-badge&logo=star&logoColor=white" alt="Star History">
        </a>
        <br><br>
        <h4>Project Support</h4>
        <p>If AzurPilot for Android enhances your daily gameplay experience, consider starring the repo to support its continuous development.</p>
        <a href="https://star-history.com/#wess09/AzurPilot-for-Android&Date">
          <img src="https://img.shields.io/badge/Star%20Trend-Star--History-orange?style=flat-square" alt="Star History Link">
        </a>
      </div>
    </td>
  </tr>
</table>

---

## License Notice

This project is licensed under the [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE).

Bundled component licenses included in this repository:
- [`LICENSE-ALAS-AOS`](LICENSE-ALAS-AOS) — AGPL-3.0
- [`LICENSE-MaaFwApp`](LICENSE-MaaFwApp) — AGPL-3.0
- [`LICENSE-AzurPilot`](LICENSE-AzurPilot) — GPL-3.0

Original license texts for all Python dependencies and transitive packages (152 total) are archived on-device at `/opt/azurpilot/licenses`.

---

<div align="center">
  <sub>AzurPilot for Android is open-source and community-maintained. For issues or feature requests, feel free to open an Issue or Pull Request.</sub>
</div>
