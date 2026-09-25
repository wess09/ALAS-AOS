<div align="center">

<img src="app/app/src/main/res/drawable-nodpi/azurpilot_android_logo.png" alt="AzurPilot for Android Logo" width="340">

# AzurPilot for Android

**無需電腦與常駐伺服器 · 原生輕量化運作 · 背景虛擬螢幕無感掛機**

全功能《碧藍航線》自動化助手 [AzurPilot](https://github.com/wess09/AzurPilot) 專用的 Android 行動端一體化執行環境

<p align="center">
  <a href="README.md">简体中文</a> |
  <a href="README_en.md">English</a> |
  <a href="README_ja.md">日本語</a> |
  <b>繁體中文</b>
</p>

---

<!-- 核心環境與平台標籤組 -->
<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-blue.svg?style=flat-square" alt="License: AGPL-3.0"></a>
  <a href="https://developer.android.com/about/versions/pie"><img src="https://img.shields.io/badge/Platform-Android%209.0%2B%20(API%2028%2B)-3DDC84.svg?style=flat-square&logo=android&logoColor=white" alt="Platform: Android 9.0+"></a>
  <a href="https://en.wikipedia.org/wiki/AArch64"><img src="https://img.shields.io/badge/Architecture-ARM64--v8a-E10098.svg?style=flat-square&logo=arm&logoColor=white" alt="Arch: ARM64"></a>
  <a href="https://ubuntu.com/"><img src="https://img.shields.io/badge/Runtime-Ubuntu%2024.04%20LTS-E95420.svg?style=flat-square&logo=ubuntu&logoColor=white" alt="Runtime: Ubuntu 24.04"></a>
  <a href="https://www.python.org/"><img src="https://img.shields.io/badge/Python-3.14-3776AB.svg?style=flat-square&logo=python&logoColor=white" alt="Python 3.14"></a>
  <a href="https://github.com/astral-sh/uv"><img src="https://img.shields.io/badge/Packaging-uv-DE5FE9.svg?style=flat-square" alt="Packaging: uv"></a>
</p>

<!-- 技術棧與框架標籤組 -->
<p align="center">
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Host-Kotlin%20%7C%20Compose%20M3-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Host: Kotlin / Jetpack Compose"></a>
  <a href="https://react.dev/"><img src="https://img.shields.io/badge/WebUI-React%20%7C%20Vite-61DAFB.svg?style=flat-square&logo=react&logoColor=black" alt="WebUI: React + Vite"></a>
  <a href="https://shizuku.rikka.app/"><img src="https://img.shields.io/badge/Backend-Shizuku%20%2F%20Root-brightgreen.svg?style=flat-square" alt="Backend: Shizuku / Root"></a>
  <a href="https://opencv.org/"><img src="https://img.shields.io/badge/Vision-OpenCV%20%7C%20RapidOCR-5C3EE8.svg?style=flat-square&logo=opencv&logoColor=white" alt="Vision: OpenCV + RapidOCR"></a>
  <a href="https://proot-me.github.io/"><img src="https://img.shields.io/badge/Isolation-PRoot-lightgrey.svg?style=flat-square" alt="Isolation: PRoot"></a>
  <a href="https://deepwiki.com/wess09/AzurPilot-for-Android"><img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki"></a>
</p>

<!-- 倉庫動態與社群指標標籤組 -->
<p align="center">
  <a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest"><img src="https://img.shields.io/github/v/release/wess09/AzurPilot-for-Android?style=flat-square&color=007ec6&label=Latest%20Release" alt="Latest Release"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/releases"><img src="https://img.shields.io/github/downloads/wess09/AzurPilot-for-Android/total?style=flat-square&color=28a745&label=Downloads" alt="Total Downloads"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers"><img src="https://img.shields.io/github/stars/wess09/AzurPilot-for-Android?style=flat-square&color=f5a623&label=Stars" alt="Stars"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/network/members"><img src="https://img.shields.io/github/forks/wess09/AzurPilot-for-Android?style=flat-square&color=6f42c1&label=Forks" alt="Forks"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/issues"><img src="https://img.shields.io/github/issues/wess09/AzurPilot-for-Android?style=flat-square&color=d73a49&label=Issues" alt="Open Issues"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/commits/main"><img src="https://img.shields.io/github/last-commit/wess09/AzurPilot-for-Android?style=flat-square&color=586069&label=Last%20Commit" alt="Last Commit"></a>
</p>

<p align="center">
  <a href="#專案總覽與指標">專案總覽</a> •
  <a href="#程式碼知識庫與-ai-問答">智慧問答</a> •
  <a href="#專案概述">專案概述</a> •
  <a href="#關聯生態專案">關聯生態</a> •
  <a href="#核心特色">核心特色</a> •
  <a href="#系統架構全景">系統架構</a> •
  <a href="#環境規格與相容性">環境規格</a> •
  <a href="#快速開始">快速開始</a> •
  <a href="#功能介面一覽">功能介面</a> •
  <a href="#雙軌獨立更新機制">雙軌更新</a> •
  <a href="#研發活躍度">研發活躍</a> •
  <a href="#star-成長趨勢">成長趨勢</a> •
  <a href="#社群生態與支援">社群支援</a>
</p>

</div>

---

## 專案總覽與指標

<table width="100%">
  <thead>
    <tr>
      <th colspan="4" align="left">
        <img src="https://img.shields.io/badge/Project%20Overview-AzurPilot%20for%20Android-181717?style=flat-square&logo=github&logoColor=white" alt="Overview">
        <b>專案核心執行與研發指標</b>
      </th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td width="25%"><b>最新正式版</b><br><a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest"><img src="https://img.shields.io/github/v/release/wess09/AzurPilot-for-Android?style=flat-square&color=007ec6" alt="Release"></a></td>
      <td width="25%"><b>累計下載量</b><br><a href="https://github.com/wess09/AzurPilot-for-Android/releases"><img src="https://img.shields.io/github/downloads/wess09/AzurPilot-for-Android/total?style=flat-square&color=28a745" alt="Downloads"></a></td>
      <td width="25%"><b>開源授權</b><br><a href="./LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0"></a></td>
      <td width="25%"><b>主分支狀態</b><br><a href="https://github.com/wess09/AzurPilot-for-Android/actions"><img src="https://img.shields.io/badge/Build-Passing-brightgreen?style=flat-square&logo=githubactions&logoColor=white" alt="Build Status"></a></td>
    </tr>
    <tr>
      <td width="25%"><b>程式碼庫容量</b><br><img src="https://img.shields.io/github/repo-size/wess09/AzurPilot-for-Android?style=flat-square&color=586069" alt="Repo Size"></td>
      <td width="25%"><b>主要語言</b><br><img src="https://img.shields.io/badge/Language-Kotlin%20%7C%20Shell-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Language"></td>
      <td width="25%"><b>提權執行方式</b><br><img src="https://img.shields.io/badge/Backend-Shizuku--m%20%7C%20Root-brightgreen?style=flat-square" alt="Backend"></td>
      <td width="25%"><b>目標系統</b><br><img src="https://img.shields.io/badge/Android-API%2028%2B%20(ARM64)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android Target"></td>
    </tr>
    <tr>
      <td colspan="4">
        <b>快速操作捷徑：</b>
        <a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest"><img src="https://img.shields.io/badge/Release-下載最新安裝包-0052cc?style=flat-square&logo=android&logoColor=white" alt="Download"></a>
        <a href="https://deepwiki.com/wess09/AzurPilot-for-Android"><img src="https://img.shields.io/badge/Wiki-Ask%20DeepWiki-6366F1?style=flat-square" alt="Ask DeepWiki"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/issues/new/choose"><img src="https://img.shields.io/badge/Issue-回報問題與提出需求-d73a49?style=flat-square&logo=githubissues&logoColor=white" alt="New Issue"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/pulls"><img src="https://img.shields.io/badge/PR-合併請求程式碼貢獻-28a745?style=flat-square&logo=git&logoColor=white" alt="Pull Request"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers"><img src="https://img.shields.io/badge/Star-關注專案發展-f5a623?style=flat-square&logo=github&logoColor=white" alt="Star"></a>
      </td>
    </tr>
  </tbody>
</table>

---

## 程式碼知識庫與 AI 問答

<table width="100%">
  <tr>
    <td width="70%" valign="middle">
      <img src="https://img.shields.io/badge/AI%20Doc-DeepWiki-6366F1?style=flat-square" alt="DeepWiki Tag"><br>
      <h3>Ask DeepWiki 程式碼庫智慧問答</h3>
      <p>對專案的內部模組、PRoot 沙盒機制、虛擬螢幕渲染邏輯或排程排定規則有任何疑問？直接向已索引本倉庫全量上下文的 DeepWiki 發起提問，快速獲取具針對性的架構解析與程式碼指引。</p>
      <div>
        <a href="https://deepwiki.com/wess09/AzurPilot-for-Android">
          <img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki">
        </a>
        <a href="https://deepwiki.com/wess09/AzurPilot-for-Android">
          <img src="https://img.shields.io/badge/知識庫檢索-即時提問解答-0052cc?style=flat-square" alt="Ask Question">
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
      <h3>如果 AzurPilot for Android 為你帶來了便利</h3>
      <p>給專案點亮一顆 Star 是對維護者持續更新與技術攻關最大的肯定。只需點選右側按鈕即可加入關注者列表！</p>
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

## 專案概述

**AzurPilot for Android** 致力於將桌面端功能成熟的《碧藍航線》自動化助手完整移植至 Android 行動端原生執行。

透過在 APK 安裝包內預置完整的 Linux 執行階段容器（Ubuntu 24.04 ARM64），結合輕量級 PRoot 容器隔離技術，實現了在 **完全免 Root** 條件下穩定執行 CPython、預編譯 OCR 演算法模型及本地 Web 控制台服務。借助背景虛擬螢幕與無障礙互動服務，使用者可以在手機主螢幕正常聊天、遊戲或辦公的同時，無感完成各項自動化巡航與出擊任務。

---

## 關聯生態專案

本專案與上下游核心專案緊密連動，關鍵相依與源流專案如下：

<table width="100%">
  <tr>
    <td width="33%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot-for-Android">
          <img src="https://img.shields.io/badge/Repository-AzurPilot--for--Android-181717?style=for-the-badge&logo=github&logoColor=white" alt="AzurPilot-for-Android">
        </a>
      </div>
      <br>
      <b>宿主執行環境 (當前專案)</b>
      <p>面向 Android 行動端的一體化執行基座，打通 PRoot 免 Root 容器、背景虛擬螢幕、全域懸浮視窗與生命週期管理。</p>
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
      <b>核心自動化引擎本體</b>
      <p>《碧藍航線》自動化助手核心，包含完備的電腦視覺影像辨識、出擊排程演算法與本地 Web 控制台服務。</p>
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
      <p>行動端 Linux 執行環境方案與免 Root 提權思維的基石專案，為 Android 自動化部署提供核心技術路線參考。</p>
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

## 核心特色

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/模組-部署形態-0052cc?style=flat-square" alt="Tag"><br>
      <h3>零配置一鍵部署</h3>
      <p>完整執行環境（Ubuntu Base、Python 3.14、科學計算與電腦視覺依賴、預置 OCR 模型權重及前端資源）全內建於安裝包中。安裝後自動釋放至私有沙盒，無需配置交叉編譯環境或線上大量下載額外執行包。</p>
      <div>
        <img src="https://img.shields.io/badge/內建容器-Ubuntu%2024.04-orange?style=flat-square" alt="Ubuntu">
        <img src="https://img.shields.io/badge/依賴管理-uv%20預置-blueviolet?style=flat-square" alt="uv">
        <img src="https://img.shields.io/badge/體驗-開箱即用-brightgreen?style=flat-square" alt="Ready">
      </div>
    </td>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/模組-運作模式-00875a?style=flat-square" alt="Tag"><br>
      <h3>背景虛擬螢幕無感掛機</h3>
      <p>基於 Android 虛擬螢幕（Virtual Display）機制，遊戲執行個體與影像擷取完全在獨立的虛擬顯示層運作。主螢幕操作與日常應用互不干擾，徹底告別實體螢幕被佔用或操作被打斷的困擾。</p>
      <div>
        <img src="https://img.shields.io/badge/渲染層-Virtual%20Display-blue?style=flat-square" alt="Virtual Display">
        <img src="https://img.shields.io/badge/主螢幕體驗-前台全自由-green?style=flat-square" alt="Free Foreground">
        <img src="https://img.shields.io/badge/干擾度-零遮蔽-teal?style=flat-square" alt="Zero Interference">
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/模組-互動控制-403294?style=flat-square" alt="Tag"><br>
      <h3>多維懸浮視窗互動面板</h3>
      <p>內建輕量級全域浮窗控制器，在任何第三方 App 或桌面層級均可一鍵喚起。支援快速啟動/暫停任務、檢查排程佇列、切換目標實例並即時捲動瀏覽執行階段日誌輸出。</p>
      <div>
        <img src="https://img.shields.io/badge/框架-FloatingX-blueviolet?style=flat-square" alt="FloatingX">
        <img src="https://img.shields.io/badge/操作-秒級啟停-orange?style=flat-square" alt="Quick Action">
        <img src="https://img.shields.io/badge/穿透-全域可用-lightgrey?style=flat-square" alt="Global Overlay">
      </div>
    </td>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/模組-控制台-172b4d?style=flat-square" alt="Tag"><br>
      <h3>完整內建 WebUI 體驗</h3>
      <p>手機端直接承載原生 React + Vite 控制台前端，提供完整的實例細項設定、出擊模式編排、定時排程規則與即時數據儀表板，無需額外透過電腦瀏覽器存取。</p>
      <div>
        <img src="https://img.shields.io/badge/前端-React%20%2B%20Vite-61DAFB?style=flat-square&logo=react&logoColor=black" alt="React">
        <img src="https://img.shields.io/badge/通訊-FastAPI%20%2F%20Uvicorn-009688?style=flat-square" alt="FastAPI">
        <img src="https://img.shields.io/badge/網路-回環位址監聽-9e9e9e?style=flat-square" alt="Localhost">
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/模組-運維排障-de350b?style=flat-square" alt="Tag"><br>
      <h3>獨立式日誌與診斷回溯</h3>
      <p>Android 宿主日誌與 AzurPilot 業務日誌雙通道獨立封存。發生異常時自動擷取並持久化當前畫面快照；內建一鍵封裝匯出功能與 7 天自動輪替清除機制，兼顧排障與儲存空間控制。</p>
      <div>
        <img src="https://img.shields.io/badge/排障-快照自動儲存-red?style=flat-square" alt="Snapshot">
        <img src="https://img.shields.io/badge/儲存空間-7天自動清理-green?style=flat-square" alt="Auto Clean">
        <img src="https://img.shields.io/badge/匯出-Zip一鍵打包-blue?style=flat-square" alt="Zip Export">
      </div>
    </td>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/模組-生命週期-ff5630?style=flat-square" alt="Tag"><br>
      <h3>雙軌獨立熱更新體系</h3>
      <p>宿主 App 與底層 Linux Runtime（rootfs）具備完全解耦的分發與升級機制。更新底層環境不破壞使用者配置與歷史日誌；更新應用程式本體僅需輕量增量包，重複使用既有 Runtime。</p>
      <div>
        <img src="https://img.shields.io/badge/架構-雙軌解耦-purple?style=flat-square" alt="Dual Track">
        <img src="https://img.shields.io/badge/完整性-SHA256驗證-blue?style=flat-square" alt="SHA256">
        <img src="https://img.shields.io/badge/資料安全-設定零損耗-brightgreen?style=flat-square" alt="Safe">
      </div>
    </td>
  </tr>
</table>

---

## 系統架構全景

專案採用清晰的分層解耦設計，各層級職責明確、通訊邊界規範：

```mermaid
graph TD
    subgraph AndroidHost ["Android 宿主層 (Kotlin / Jetpack Compose M3)"]
        UI["Compose UI 介面矩陣\n(首頁 / 任務排程 / 定時管理 / 全域設定)"]
        Overlay["FloatingX 全域懸浮控制面板"]
        Lifecycle["處理程序守護與 Runtime 生命週期管理器"]
        Bridge["Shizuku-m / Root su 權限接入中繼"]
    end

    subgraph Container ["PRoot 容器隔離層 (Ubuntu 24.04 ARM64)"]
        PROOT["PRoot 虛擬化引擎 (免 Root 系統呼叫對映)"]
        ENV["CPython 3.14 執行環境\n(OpenCV / RapidOCR / NumPy / uv)"]
        CORE["AzurPilot 自動化調度引擎本體"]
        WEB["React + Vite 靜態控制台\n(FastAPI / Uvicorn 監聽 127.0.0.1)"]
    end

    subgraph DeviceTarget ["系統能力與受控目標"]
        VDisplay["背景虛擬顯示螢幕 (Virtual Display)"]
        InputService["MaaTouch / DroidCast / ADB 控制串流"]
        GameApp["《碧藍航線》遊戲實例"]
    end

    UI --> Lifecycle
    Overlay --> Lifecycle
    Lifecycle --> PROOT
    PROOT --> ENV
    ENV --> CORE
    CORE --> WEB
    UI -. "WebView / 本地回環通訊" .-> WEB
    Bridge --> InputService
    Bridge --> VDisplay
    InputService --> GameApp
    VDisplay --> GameApp
    CORE --> InputService
```

---

## 環境規格與相容性

<table width="100%">
  <thead>
    <tr>
      <th width="18%">維度</th>
      <th width="28%">基本規格指標</th>
      <th width="28%">建議配置</th>
      <th width="26%">說明</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>作業系統</b></td>
      <td><img src="https://img.shields.io/badge/Android-9.0%2B%20(API%2028)-green?style=flat-square" alt="Android 9.0+"></td>
      <td><img src="https://img.shields.io/badge/Android-12%20~%2015-brightgreen?style=flat-square" alt="Android 12~15"></td>
      <td>需符合現代 Android 權限隔離與沙盒儲存機制</td>
    </tr>
    <tr>
      <td><b>處理器架構</b></td>
      <td><img src="https://img.shields.io/badge/CPU-ARM64--v8a-blue?style=flat-square" alt="ARM64"></td>
      <td><img src="https://img.shields.io/badge/CPU-高效能多核心架構-blue?style=flat-square" alt="Multi-core"></td>
      <td>依賴 64 位元 Linux 二進位檔，不支援 32 位元設備</td>
    </tr>
    <tr>
      <td><b>本機儲存空間</b></td>
      <td><img src="https://img.shields.io/badge/可用空間->=%202.5%20GB-yellow?style=flat-square" alt="2.5GB"></td>
      <td><img src="https://img.shields.io/badge/可用空間->=%205.0%20GB-brightgreen?style=flat-square" alt="5.0GB"></td>
      <td>容納完整 Ubuntu 根檔案系統、依賴套件及診斷快照</td>
    </tr>
    <tr>
      <td><b>系統執行記憶體</b></td>
      <td><img src="https://img.shields.io/badge/RAM->=%204%20GB-yellow?style=flat-square" alt="4GB"></td>
      <td><img src="https://img.shields.io/badge/RAM->=%206%20GB%20或以上-brightgreen?style=flat-square" alt="6GB+"></td>
      <td>確保 Python 演算法處理程序與遊戲多工並行時之穩定性</td>
    </tr>
    <tr>
      <td><b>權限支援</b></td>
      <td><img src="https://img.shields.io/badge/方案-Shizuku%20%2F%20Root-orange?style=flat-square" alt="Shizuku / Root"></td>
      <td><img src="https://img.shields.io/badge/方案-Shizuku--m%20(免除錯模式)-brightgreen?style=flat-square" alt="Shizuku-m"></td>
      <td>用於系統層級觸控模擬與虛擬螢幕投影授權</td>
    </tr>
  </tbody>
</table>

---

## 快速開始

<table width="100%">
  <tr>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/步驟-01-blue?style=flat-square" alt="Step 1"><br>
        <h4>取得安裝包</h4>
      </div>
      前往 <a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest">Releases 最新版本</a> 下載<b>完整版 APK</b>（安裝包包含預置 Runtime 映像檔）。
    </td>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/步驟-02-indigo?style=flat-square" alt="Step 2"><br>
        <h4>初始化解壓縮</h4>
      </div>
      安裝完成後啟動應用程式，等待底層 Linux 根檔案系統自動釋放至沙盒，初次解壓需要 1 至 3 分鐘。
    </td>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/步驟-03-purple?style=flat-square" alt="Step 3"><br>
        <h4>接入提權服務</h4>
      </div>
      依照介面指示連接 <a href="https://github.com/Shinarin/shizuku-m">Shizuku-m</a> 授權，或在設定頁面直接切換為 Root 執行後端。
    </td>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/步驟-04-green?style=flat-square" alt="Step 4"><br>
        <h4>配置巡航任務</h4>
      </div>
      在「任務」頁面確認畫面串流，進入內嵌「AzurPilot」WebUI 頁面自訂專屬自動化出擊規則。
    </td>
  </tr>
</table>

> [!TIP]
> 推薦使用 **Shizuku-m**，該分支支援免開啟無線偵錯模式、無外部 WLAN 網路環境下亦能正常啟動，大幅提升行動情境下的穩定性。

> [!IMPORTANT]
> **完整版 APK** 內建 Runtime，首次啟動直接在本機解壓縮，無需連網；**輕量增量 APK** 不含 Runtime，首次啟動會自動從 GitHub 下載約 1GB 的 Runtime（建議在 Wi-Fi 環境下進行）。後續若僅有 Android 宿主程式碼更新，下載輕量增量 APK 直接覆蓋安裝即可，無需重新解壓 Runtime。

---

## 功能介面一覽

<table width="100%">
  <thead>
    <tr>
      <th width="15%">介面模組</th>
      <th width="45%">核心職責與承載能力</th>
      <th width="20%">互動特色</th>
      <th width="20%">狀態標誌</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>首頁儀表板</b></td>
      <td>呈現系統健康概況、PRoot 容器狀態、CPU/記憶體負載、執行診斷日誌捷徑。</td>
      <td>卡片式看板、一鍵診斷</td>
      <td><img src="https://img.shields.io/badge/狀態-總覽樞紐-blue?style=flat-square" alt="Overview"></td>
    </tr>
    <tr>
      <td><b>任務管理</b></td>
      <td>即時預覽背景虛擬螢幕遊戲畫面、作業佇列排程安排、全螢幕手動觸控接管模式。</td>
      <td>低延遲串流、即時手動介入</td>
      <td><img src="https://img.shields.io/badge/狀態-執行中樞-orange?style=flat-square" alt="Task"></td>
    </tr>
    <tr>
      <td><b>定時管理</b></td>
      <td>編排計畫任務行事曆、定時觸發執行條件、到點自動喚醒與離線佇列管理。</td>
      <td>規則彈性匹配、週期循環</td>
      <td><img src="https://img.shields.io/badge/狀態-計畫排程-purple?style=flat-square" alt="Schedule"></td>
    </tr>
    <tr>
      <td><b>AzurPilot WebUI</b></td>
      <td>完整呈現 React 控制台前端，細化出擊關卡、編隊策略、委託與資源收集、建造管理。</td>
      <td>內嵌無縫 WebView 直連</td>
      <td><img src="https://img.shields.io/badge/狀態-引擎大腦-61DAFB?style=flat-square" alt="Engine"></td>
    </tr>
    <tr>
      <td><b>系統設定</b></td>
      <td>管理雙軌更新通道、執行階段配額、虛擬螢幕解析度參數、日誌封存與匯出中心。</td>
      <td>偏好持久化、安全校驗</td>
      <td><img src="https://img.shields.io/badge/狀態-全域配置-lightgrey?style=flat-square" alt="Settings"></td>
    </tr>
    <tr>
      <td><b>全域懸浮視窗</b></td>
      <td>穿透顯示於任何 App 之上，即時呈現排程進度、提供即時暫停/啟動與最新日誌速覽。</td>
      <td>最小化膠囊、拖曳自適應</td>
      <td><img src="https://img.shields.io/badge/狀態-全域浮窗-brightgreen?style=flat-square" alt="Overlay"></td>
    </tr>
  </tbody>
</table>

---

## 雙軌獨立更新機制

為確保環境的極致穩定與升級彈性，系統採用宿主層與執行階段解耦的雙軌管理策略：

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/通道-宿主%20App%20升級-7F52FF?style=flat-square&logo=android&logoColor=white" alt="App Track"><br>
        <h4>輕量級宿主覆蓋更新</h4>
      </div>
      <ul>
        <li><b>觸發依據</b>：比對 Android 內部版本號（<code>VersionCode</code>）。</li>
        <li><b>升級酬載</b>：僅包含 Android 原生業務層的輕量 APK。</li>
        <li><b>更新過程</b>：呼叫系統標準 <code>PackageInstaller</code> 進行覆蓋安裝。</li>
        <li><b>資料保障</b>：完全重複使用手機已解壓的 Linux Runtime 與全部既有設定檔。</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/通道-底層%20Runtime%20升級-E95420?style=flat-square&logo=ubuntu&logoColor=white" alt="Runtime Track"><br>
        <h4>解耦式容器映像檔熱替換</h4>
      </div>
      <ul>
        <li><b>觸發依據</b>：讀取 GitHub Latest release 中的 <code>latest.json</code> 簽名。</li>
        <li><b>完整性驗證</b>：下載後執行嚴格的 SHA-256 雜湊對比與檔案大小校驗。</li>
        <li><b>熱替換機制</b>：安全停止 Python 服務後替換容器根目錄，保留 <code>/config</code> 及資料磁碟區。</li>
        <li><b>容錯回退</b>：網路異常或使用者選擇稍後時，無縫繼續沿用目前穩定版本。</li>
      </ul>
    </td>
  </tr>
</table>

---

## 研發活躍度

<table width="100%">
  <thead>
    <tr>
      <th colspan="3" align="left">
        <img src="https://img.shields.io/badge/Metrics-RepoBeats%20Style-5C3EE8?style=flat-square&logo=github&logoColor=white" alt="RepoBeats">
        <b>倉庫研發活躍度與回應態勢</b>
      </th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td width="33%">
        <b>程式碼提交頻率</b><br>
        <img src="https://img.shields.io/github/commit-activity/m/wess09/AzurPilot-for-Android?style=flat-square&color=00d4aa" alt="Commit Activity"><br>
        <small>月度提交活躍狀態</small>
      </td>
      <td width="33%">
        <b>合併請求處理</b><br>
        <img src="https://img.shields.io/github/issues-pr-closed/wess09/AzurPilot-for-Android?style=flat-square&color=6f42c1" alt="Closed PRs"><br>
        <small>已歸檔合併請求總計</small>
      </td>
      <td width="33%">
        <b>問題回饋解決</b><br>
        <img src="https://img.shields.io/github/issues-closed/wess09/AzurPilot-for-Android?style=flat-square&color=28a745" alt="Closed Issues"><br>
        <small>已成功解決 Issue 統計</small>
      </td>
    </tr>
    <tr>
      <td width="33%">
        <b>研發分支</b><br>
        <img src="https://img.shields.io/badge/Branch-main-181717?style=flat-square&logo=git&logoColor=white" alt="Main Branch"><br>
        <small>主幹持續交付串流</small>
      </td>
      <td width="33%">
        <b>自動化建置 (CI/CD)</b><br>
        <img src="https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white" alt="CI"><br>
        <small>ARM64 Runner 持續驗證</small>
      </td>
      <td width="33%">
        <b>最新提交紀錄</b><br>
        <a href="https://github.com/wess09/AzurPilot-for-Android/commits/main"><img src="https://img.shields.io/github/last-commit/wess09/AzurPilot-for-Android?style=flat-square&color=586069" alt="Last Commit"></a><br>
        <small>追蹤最新程式碼演進</small>
      </td>
    </tr>
  </tbody>
</table>

---

## Star 成長趨勢

採用自適應深淺模式的 Star-History 向量歷史圖譜，直觀反映專案發展脈絡：

<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=wess09/AzurPilot-for-Android&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=wess09/AzurPilot-for-Android&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=wess09/AzurPilot-for-Android&type=Date" width="100%" />
  </picture>
  <br>
  <sub>資料來源基於 <a href="https://star-history.com/#wess09/AzurPilot-for-Android&Date">Star-History</a> 即時更新 · 點選可檢視互動式完整走勢</sub>
</div>

---

## 專案建置全景

```
.
|-- app/                      # Android 宿主專案原始碼 (Kotlin + Jetpack Compose)
|   |-- app/                  # 核心應用模組、Compose UI 實作與狀態串流
|   |-- annotation-api/       # 註解與介面約定模組
|   |-- ksp-processor/        # 編譯期 KSP 程式碼生成處理器
|   `-- gradle/               # 統一依賴版本目錄 (libs.versions.toml)
|-- rootfs/                   # Linux 執行環境打包指令稿與相依套件定義
|   `-- build/                # build-azurpilot.sh 映像檔裁剪與整合指令稿
|-- .github/workflows/        # CI/CD 自動化工作流程 (rootfs.yml)
`-- tools/                    # 建置輔助工具與本機除錯指令稿
```

- **編譯環境規範**：Android Gradle Plugin 8.x、Kotlin 2.x、Jetpack Compose Material 3。
- **自動化建置串流**：基於 GitHub Actions ARM64 Runner 執行 Ubuntu 24.04 映像檔裁剪、uv 相依預先安裝、前端打包與 xz 高壓縮封存。
- **發布簽名安全**：正式簽名金鑰妥善保管於 GitHub Actions Secrets 中，杜絕簽名外洩與未經授權之竄改。

---

## 技術棧與相依致謝

### 核心執行環境與系統基底

| 依賴組件 | 授權識別標籤 | 職責定位 |
| :--- | :--- | :--- |
| **Ubuntu Base 24.04** | <img src="https://img.shields.io/badge/License-Canonical-lightgrey?style=flat-square" alt="Canonical"> | ARM64 Linux 容器基礎映像檔 |
| **PRoot** | <img src="https://img.shields.io/badge/License-GPL--2.0-blue?style=flat-square" alt="GPL-2.0"> | 免 Root 使用者空間系統呼叫仿真引擎 |
| **uv** | <img src="https://img.shields.io/badge/License-Apache--2.0%20%7C%20MIT-brightgreen?style=flat-square" alt="uv License"> | 現代高效能 Python 套件管理工具 |
| **CPython 3.14** | <img src="https://img.shields.io/badge/License-PSF--2.0-blue?style=flat-square" alt="PSF-2.0"> | 核心直譯器執行環境 |
| **AzurPilot** | <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square" alt="GPL-3.0"> | 核心自動化決策邏輯與任務排程引擎 |
| **ALAS-AOS** | <img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0"> | Android 容器化執行路線的重要起點與參考基線 |
| **MaaFwApp** | <img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0"> | Android 宿主架構與工程框架模板 |

### Android 宿主技術棧

| 相依套件庫 | 授權識別標籤 | 用途說明 |
| :--- | :--- | :--- |
| **AndroidX Suite** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Jetpack Compose、Lifecycle、DataStore 等現代化組件 |
| **Koin** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 輕量實用的依賴注入 (DI) 框架 |
| **OkHttp** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 穩定高效的 HTTP 網路通訊用戶端 |
| **Shizuku API** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 免 Root 行程間授權與通訊介面 |
| **libsu** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Root 模式下穩健的 Shell 提權呼叫框架 |
| **FloatingX** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 優雅易用的 Android 全域懸浮視窗框架 |
| **XXPermissions** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 動態權限申請與狀態管理方案 |
| **Apache Commons Compress** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 執行階段歸檔解壓縮與檔案串流處理 |
| **XZ for Java** | <img src="https://img.shields.io/badge/License-Public%20Domain-lightgrey?style=flat-square" alt="Public Domain"> | 高壓縮比 rootfs 歸檔格式解析支援 |

---

## 社群生態與支援

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot-for-Android/graphs/contributors">
          <img src="https://img.shields.io/badge/Community-Contributors-blueviolet?style=for-the-badge&logo=github&logoColor=white" alt="Contributors">
        </a>
        <br><br>
        <h4>開源共建與參與</h4>
        <p>感謝所有參與程式碼提交、架構改進、問題排查與功能驗證的開發者。歡迎隨時提交 Issue 與 PR 參與共建！</p>
        <a href="https://github.com/wess09/AzurPilot-for-Android/graphs/contributors">
          <img src="https://img.shields.io/badge/檢視完整貢獻名單-GitHub%20Graph-181717?style=flat-square&logo=github&logoColor=white" alt="View Contributors">
        </a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/issues/new/choose">
          <img src="https://img.shields.io/badge/提交回饋-New%20Issue-0052cc?style=flat-square&logo=githubissues&logoColor=white" alt="New Issue">
        </a>
      </div>
    </td>
    <td width="50%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers">
          <img src="https://img.shields.io/badge/Project-Star%20History-f5a623?style=for-the-badge&logo=star&logoColor=white" alt="Star History">
        </a>
        <br><br>
        <h4>專案成長與支援</h4>
        <p>如果 AzurPilot for Android 為你的遊戲掛機體驗提供了便利，歡迎前往倉庫首頁點亮 Star 支援專案演進。</p>
        <a href="https://star-history.com/#wess09/AzurPilot-for-Android&Date">
          <img src="https://img.shields.io/badge/Star%20趨勢看板-Star--History-orange?style=flat-square" alt="Star History Link">
        </a>
      </div>
    </td>
  </tr>
</table>

---

## 授權條款說明

本專案採用 [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE) 條款開源。

隨專案一同分發的授權條款檔案：
- [`LICENSE-ALAS-AOS`](LICENSE-ALAS-AOS) — AGPL-3.0
- [`LICENSE-MaaFwApp`](LICENSE-MaaFwApp) — AGPL-3.0
- [`LICENSE-AzurPilot`](LICENSE-AzurPilot) — GPL-3.0

各 Python 相依套件及傳遞相依的原授權文本（共 152 項），均已隨執行階段映像檔封存於設備端 `/opt/azurpilot/licenses`。

---

<div align="center">
  <sub>AzurPilot for Android 遵守開源協議並由社群驅動維護。如遇問題歡迎提交 Issue 或 Pull Request。</sub>
</div>
