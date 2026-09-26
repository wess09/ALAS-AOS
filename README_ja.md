<div align="center">

<img src="app/app/src/main/res/drawable-nodpi/azurpilot_android_logo.png" alt="AzurPilot for Android Logo" width="340">

# AzurPilot for Android

**PC・常駐サーバー一切不要 · ネイティブ軽量動作 · 仮想ディスプレイによる干渉ゼロ自動周回**

多機能『アズールレーン』自動化ツール [AzurPilot](https://github.com/wess09/AzurPilot) 専用の Android モバイル統合実行環境

本リポジトリは [ALAS-AOS](https://github.com/Shinarin/ALAS-AOS) からのフォークで、ホストアーキテクチャ、PRoot によるコンテナ化路線、Root 不要の権限昇格設計、および AGPL-3.0 ライセンスを継承しています。

<p align="center">
  <a href="README.md">简体中文</a> |
  <a href="README_en.md">English</a> |
  <b>日本語</b> |
  <a href="README_zh-TW.md">繁體中文</a>
</p>

---

<!-- コア環境およびプラットフォームバッジ -->
<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-blue.svg?style=flat-square" alt="License: AGPL-3.0"></a>
  <a href="https://developer.android.com/about/versions/pie"><img src="https://img.shields.io/badge/Platform-Android%209.0%2B%20(API%2028%2B)-3DDC84.svg?style=flat-square&logo=android&logoColor=white" alt="Platform: Android 9.0+"></a>
  <a href="https://en.wikipedia.org/wiki/AArch64"><img src="https://img.shields.io/badge/Architecture-ARM64--v8a%20%2F%20x86__64-E10098.svg?style=flat-square&logo=arm&logoColor=white" alt="Arch: ARM64"></a>
  <a href="https://ubuntu.com/"><img src="https://img.shields.io/badge/Runtime-Ubuntu%2024.04%20LTS-E95420.svg?style=flat-square&logo=ubuntu&logoColor=white" alt="Runtime: Ubuntu 24.04"></a>
  <a href="https://www.python.org/"><img src="https://img.shields.io/badge/Python-3.14-3776AB.svg?style=flat-square&logo=python&logoColor=white" alt="Python 3.14"></a>
  <a href="https://github.com/astral-sh/uv"><img src="https://img.shields.io/badge/Packaging-uv-DE5FE9.svg?style=flat-square" alt="Packaging: uv"></a>
</p>

<!-- 技術スタックおよびフレームワークバッジ -->
<p align="center">
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Host-Kotlin%20%7C%20Compose%20M3-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Host: Kotlin / Jetpack Compose"></a>
  <a href="https://react.dev/"><img src="https://img.shields.io/badge/WebUI-React%20%7C%20Vite-61DAFB.svg?style=flat-square&logo=react&logoColor=black" alt="WebUI: React + Vite"></a>
  <a href="https://github.com/wess09/shizuku-m"><img src="https://img.shields.io/badge/Backend-Shizuku%20%2F%20Root-brightgreen.svg?style=flat-square" alt="Backend: Shizuku / Root"></a>
  <a href="https://opencv.org/"><img src="https://img.shields.io/badge/Vision-OpenCV%20%7C%20RapidOCR-5C3EE8.svg?style=flat-square&logo=opencv&logoColor=white" alt="Vision: OpenCV + RapidOCR"></a>
  <a href="https://proot-me.github.io/"><img src="https://img.shields.io/badge/Isolation-PRoot-lightgrey.svg?style=flat-square" alt="Isolation: PRoot"></a>
  <a href="https://deepwiki.com/wess09/AzurPilot-for-Android"><img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki"></a>
</p>

<!-- リポジトリ指標バッジ -->
<p align="center">
  <a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest"><img src="https://img.shields.io/github/v/release/wess09/AzurPilot-for-Android?style=flat-square&color=007ec6&label=Latest%20Release" alt="Latest Release"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/releases"><img src="https://img.shields.io/github/downloads/wess09/AzurPilot-for-Android/total?style=flat-square&color=28a745&label=Downloads" alt="Total Downloads"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers"><img src="https://img.shields.io/github/stars/wess09/AzurPilot-for-Android?style=flat-square&color=f5a623&label=Stars" alt="Stars"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/network/members"><img src="https://img.shields.io/github/forks/wess09/AzurPilot-for-Android?style=flat-square&color=6f42c1&label=Forks" alt="Forks"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/issues"><img src="https://img.shields.io/github/issues/wess09/AzurPilot-for-Android?style=flat-square&color=d73a49&label=Issues" alt="Open Issues"></a>
  <a href="https://github.com/wess09/AzurPilot-for-Android/commits/main"><img src="https://img.shields.io/github/last-commit/wess09/AzurPilot-for-Android?style=flat-square&color=586069&label=Last%20Commit" alt="Last Commit"></a>
</p>

<p align="center">
  <a href="#プロジェクト概要と開発指標">概要指標</a> •
  <a href="#ナレッジベースと-ai-質疑応答">AI 質疑応答</a> •
  <a href="#プロジェクト概要">概要</a> •
  <a href="#関連エコシステムプロジェクト">エコシステム</a> •
  <a href="#主な特長">主な特長</a> •
  <a href="#システムアーキテクチャ">アーキテクチャ</a> •
  <a href="#動作環境と互換性">環境仕様</a> •
  <a href="#クイックスタート">クイックスタート</a> •
  <a href="#アプリプレビュー">プレビュー</a> •
  <a href="#機能インターフェース一覧">UI一覧</a> •
  <a href="#デュアルトラック独立アップデート機構">更新機構</a> •
  <a href="#開発アクティビティ">開発状況</a> •
  <a href="#star-の推移">スター推移</a> •
  <a href="#コミュニティとサポート">コミュニティ</a>
</p>

</div>

---

## プロジェクト概要と開発指標

<table width="100%">
  <thead>
    <tr>
      <th colspan="4" align="left">
        <img src="https://img.shields.io/badge/Project%20Overview-AzurPilot%20for%20Android-181717?style=flat-square&logo=github&logoColor=white" alt="Overview">
        <b>プロジェクト主要指標・開発状況</b>
      </th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td width="25%"><b>最新リリース</b><br><a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest"><img src="https://img.shields.io/github/v/release/wess09/AzurPilot-for-Android?style=flat-square&color=007ec6" alt="Release"></a></td>
      <td width="25%"><b>総ダウンロード数</b><br><a href="https://github.com/wess09/AzurPilot-for-Android/releases"><img src="https://img.shields.io/github/downloads/wess09/AzurPilot-for-Android/total?style=flat-square&color=28a745" alt="Downloads"></a></td>
      <td width="25%"><b>オープンソースライセンス</b><br><a href="./LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0"></a></td>
      <td width="25%"><b>メインブランチ状況</b><br><a href="https://github.com/wess09/AzurPilot-for-Android/actions"><img src="https://img.shields.io/badge/Build-Passing-brightgreen?style=flat-square&logo=githubactions&logoColor=white" alt="Build Status"></a></td>
    </tr>
    <tr>
      <td width="25%"><b>リポジトリ容量</b><br><img src="https://img.shields.io/github/repo-size/wess09/AzurPilot-for-Android?style=flat-square&color=586069" alt="Repo Size"></td>
      <td width="25%"><b>主要開発言語</b><br><img src="https://img.shields.io/badge/Language-Kotlin%20%7C%20Shell-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Language"></td>
      <td width="25%"><b>権限昇格バックエンド</b><br><img src="https://img.shields.io/badge/Backend-Shizuku--m%20%7C%20Root-brightgreen?style=flat-square" alt="Backend"></td>
      <td width="25%"><b>対象プラットフォーム</b><br><img src="https://img.shields.io/badge/Android-API%2028%2B%20(ARM64%20%2F%20x86__64)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android Target"></td>
    </tr>
    <tr>
      <td colspan="4">
        <b>クイックリンク：</b>
        <a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest"><img src="https://img.shields.io/badge/Release-最新APKをダウンロード-0052cc?style=flat-square&logo=android&logoColor=white" alt="Download"></a>
        <a href="https://deepwiki.com/wess09/AzurPilot-for-Android"><img src="https://img.shields.io/badge/Wiki-Ask%20DeepWiki-6366F1?style=flat-square" alt="Ask DeepWiki"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/issues/new/choose"><img src="https://img.shields.io/badge/Issue-不具合報告・要望提出-d73a49?style=flat-square&logo=githubissues&logoColor=white" alt="New Issue"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/pulls"><img src="https://img.shields.io/badge/PR-コード貢献・プルリクエスト-28a745?style=flat-square&logo=git&logoColor=white" alt="Pull Request"></a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers"><img src="https://img.shields.io/badge/Star-プロジェクトを応援-f5a623?style=flat-square&logo=github&logoColor=white" alt="Star"></a>
      </td>
    </tr>
  </tbody>
</table>

---

## ナレッジベースと AI 質疑応答

<table width="100%">
  <tr>
    <td width="70%" valign="middle">
      <img src="https://img.shields.io/badge/AI%20Doc-DeepWiki-6366F1?style=flat-square" alt="DeepWiki Tag"><br>
      <h3>Ask DeepWiki リポジトリ AI 質疑応答</h3>
      <p>内部モジュール、PRoot サンドボックスの仕組み、仮想ディスプレイ描画ロジック、タスクスケジューリングルールに関する疑問がある場合、本リポジトリの全コードがインデックスされている DeepWiki に直接質問することで、的確なアーキテクチャ解説とコードガイドを即座に得られます。</p>
      <div>
        <a href="https://deepwiki.com/wess09/AzurPilot-for-Android">
          <img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki">
        </a>
        <a href="https://deepwiki.com/wess09/AzurPilot-for-Android">
          <img src="https://img.shields.io/badge/知識ベース検索-即時質問と回答-0052cc?style=flat-square" alt="Ask Question">
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
      <h3>AzurPilot for Android が役立っている場合</h3>
      <p>リポジトリに Star を付けることは、メンテナーの継続的な開発と技術的課題解決への最大の支援となります。右側のボタンをクリックしてぜひ応援してください！</p>
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

## プロジェクト概要

**AzurPilot for Android** は、デスクトップ環境で成熟した『アズールレーン』自動化アシスタントを、Android モバイル端末上で完全ネイティブ動作させることを目的としたプロジェクトです。

APK パッケージ内に完全な Linux 実行環境（Ubuntu 24.04、arm64-v8a / x86_64 の 2 種類の Runtime をデバイス構成別に提供）を内蔵し、軽量な PRoot コンテナ分離技術と組み合わせることで、**完全 Root 不要**の条件下で CPython、ビルド済み OCR モデル、およびローカル Web コンソールサービスを安定稼働させます。バックグラウンドの仮想ディスプレイとアクセシビリティを活用することで、スマートフォンのメイン画面でチャットやゲーム、日常操作を行いながら、バックグラウンドで何ら干渉されることなく自動出撃・巡航タスクを完了できます。

---

## 関連エコシステムプロジェクト

本プロジェクトは以下の関連プロジェクトと密接に連携しています：

<table width="100%">
  <tr>
    <td width="33%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot-for-Android">
          <img src="https://img.shields.io/badge/Repository-AzurPilot--for--Android-181717?style=for-the-badge&logo=github&logoColor=white" alt="AzurPilot-for-Android">
        </a>
      </div>
      <br>
      <b>ホスト実行環境（当プロジェクト）</b>
      <p>Android 端末向け統合実行基盤：PRoot Root不要コンテナ、バックグラウンド仮想ディスプレイ、全体オーバーレイ、ライフサイクル管理を提供。</p>
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
      <b>自動化エンジン本体</b>
      <p>『アズールレーン』自動化の中核：コンピュータビジョンによる画像認識、出撃スケジューリングアルゴリズム、ローカル Web 管理コンソールを包含。</p>
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
      <b>ソースプロジェクト（本リポジトリのフォーク元）</b>
      <p>モバイル Android における Linux 実行環境と Root 不要の権限昇格の先行実装で、本リポジトリのホストアーキテクチャとコンテナ化路線はここから発展しました。</p>
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

## 主な特長

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/モジュール-デプロイ形態-0052cc?style=flat-square" alt="Tag"><br>
      <h3>設定不要・ワンタップ即時デプロイ</h3>
      <p>Ubuntu Base、Python 3.14、科学計算および画像認識ライブラリ、プリセット済み OCR モデル、フロントエンド静的ファイルなど、全実行環境を APK 内に完全パッケージング。初回起動時に専用サンドボックスへ自動展開され、クロスコンパイル環境の構築や大容量の追加ダウンロードは一切不要です。</p>
      <div>
        <img src="https://img.shields.io/badge/内蔵コンテナ-Ubuntu%2024.04-orange?style=flat-square" alt="Ubuntu">
        <img src="https://img.shields.io/badge/パッケージ管理-uv%20プリセット-blueviolet?style=flat-square" alt="uv">
        <img src="https://img.shields.io/badge/体験-インストール後即起動-brightgreen?style=flat-square" alt="Ready">
      </div>
    </td>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/モジュール-動作モード-00875a?style=flat-square" alt="Tag"><br>
      <h3>バックグラウンド仮想ディスプレイによる干渉ゼロ自動周回</h3>
      <p>Android の仮想ディスプレイ（Virtual Display）機能を活用し、ゲーム本体および画面認識処理を完全に独立した仮想画面レイヤーで実行。メイン画面の通常利用や日常操作と完全に分離され、画面占有や操作の誤爆といった問題を完全に解決します。</p>
      <div>
        <img src="https://img.shields.io/badge/描画レイヤー-Virtual%20Display-blue?style=flat-square" alt="Virtual Display">
        <img src="https://img.shields.io/badge/メイン画面-自由に使用可能-green?style=flat-square" alt="Free Foreground">
        <img src="https://img.shields.io/badge/干渉度-ゼロ-teal?style=flat-square" alt="Zero Interference">
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/モジュール-操作コントロール-403294?style=flat-square" alt="Tag"><br>
      <h3>多機能グローバルフローティングパネル</h3>
      <p>他のアプリやホーム画面上でもワンタップで呼び出せる軽量フローティングコントローラーを標準装備。タスクの即時開始・一時停止、スケジュール確認、インスタンス切り替え、リアルタイムログの閲覧が可能です。</p>
      <div>
        <img src="https://img.shields.io/badge/フレームワーク-FloatingX-blueviolet?style=flat-square" alt="FloatingX">
        <img src="https://img.shields.io/badge/操作-即時開始%2F停止-orange?style=flat-square" alt="Quick Action">
        <img src="https://img.shields.io/badge/オーバーレイ-全画面対応-lightgrey?style=flat-square" alt="Global Overlay">
      </div>
    </td>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/モジュール-コンソール-172b4d?style=flat-square" alt="Tag"><br>
      <h3>本格内蔵 WebUI 体験</h3>
      <p>端末上でネイティブの React + Vite 管理画面を直接ホスト。PC ブラウザを経由することなく、インスタンスごとの詳細設定、出撃モード編成、タイマールール、リアルタイムデータ監視をスマホ画面上で完結できます。</p>
      <div>
        <img src="https://img.shields.io/badge/フロントエンド-React%20%2B%20Vite-61DAFB?style=flat-square&logo=react&logoColor=black" alt="React">
        <img src="https://img.shields.io/badge/通信-FastAPI%20%2F%20Uvicorn-009688?style=flat-square" alt="FastAPI">
        <img src="https://img.shields.io/badge/ネットワーク-ローカルループバック-9e9e9e?style=flat-square" alt="Localhost">
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/モジュール-保守・トラブルシューティング-de350b?style=flat-square" alt="Tag"><br>
      <h3>分離型ログ記録と診断トレーサビリティ</h3>
      <p>Android ホストログと AzurPilot コアログを独立管理。異常発生時には現在の画面スナップショットを自動保存します。ワンクリックの Zip 診断パッケージ書き出し機能と 7 日間の自動ローテーション・削除ポリシーにより、トラブルシュートとストレージ節約を両立しています。</p>
      <div>
        <img src="https://img.shields.io/badge/診断-エラー画面自動保存-red?style=flat-square" alt="Snapshot">
        <img src="https://img.shields.io/badge/ストレージ-7日間自動整理-green?style=flat-square" alt="Auto Clean">
        <img src="https://img.shields.io/badge/書き出し-ワンタップZip圧縮-blue?style=flat-square" alt="Zip Export">
      </div>
    </td>
    <td width="50%" valign="top">
      <img src="https://img.shields.io/badge/モジュール-ライフサイクル-ff5630?style=flat-square" alt="Tag"><br>
      <h3>デュアルトラック独立ホットアップデート機構</h3>
      <p>ホスト App と基礎 Linux ランタイム（rootfs）を完全に疎結合化。基礎環境の更新時でもユーザー設定や履歴ログはそのまま維持され、アプリ本体の更新時は軽量な差分 APK を上書きするだけで済み、ランタイムの再展開は不要です。</p>
      <div>
        <img src="https://img.shields.io/badge/アーキテクチャ-2系統独立設計-purple?style=flat-square" alt="Dual Track">
        <img src="https://img.shields.io/badge/完全性-SHA256検証-blue?style=flat-square" alt="SHA256">
        <img src="https://img.shields.io/badge/データ安全-設定完全保持-brightgreen?style=flat-square" alt="Safe">
      </div>
    </td>
  </tr>
</table>

---

## システムアーキテクチャ

本プロジェクトは明確に階層化・モジュール分離された設計を採用しており、各層の責務と通信境界が厳格に定義されています：

```mermaid
graph TD
    subgraph AndroidHost ["Android ホスト層 (Kotlin / Jetpack Compose M3)"]
        UI["Compose UI 画面マトリクス\n(ホーム / タスク / スケジュール / 設定)"]
        Overlay["FloatingX 全画面フローティングパネル"]
        Lifecycle["プロセス監視・Runtime ライフサイクル管理"]
        Bridge["Shizuku-m / Root su 権限中継"]
    end

    subgraph Container ["PRoot コンテナ分離層 (Ubuntu 24.04 · arm64 / x86_64)"]
        PROOT["PRoot 仮想化エンジン (Root不要システムコール変換)"]
        ENV["CPython 3.14 実行環境\n(OpenCV / RapidOCR / NumPy / uv)"]
        CORE["AzurPilot 自動化スケジューリングエンジン本体"]
        WEB["React + Vite 静的コンソール\n(FastAPI / Uvicorn 127.0.0.1 待受)"]
    end

    subgraph DeviceTarget ["システム機能および操作対象"]
        VDisplay["バックグラウンド仮想ディスプレイ (Virtual Display)"]
        InputService["MaaTouch / DroidCast / ADB 制御ストリーム"]
        GameApp["『アズールレーン』ゲーム本体"]
    end

    UI --> Lifecycle
    Overlay --> Lifecycle
    Lifecycle --> PROOT
    PROOT --> ENV
    ENV --> CORE
    CORE --> WEB
    UI -. "WebView / ローカルループバック通信" .-> WEB
    Bridge --> InputService
    Bridge --> VDisplay
    InputService --> GameApp
    VDisplay --> GameApp
    CORE --> InputService
```

---

## 動作環境と互換性

<table width="100%">
  <thead>
    <tr>
      <th width="18%">項目</th>
      <th width="28%">最小要件</th>
      <th width="28%">推奨構成</th>
      <th width="26%">補足説明</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>オペレーティングシステム</b></td>
      <td><img src="https://img.shields.io/badge/Android-9.0%2B%20(API%2028)-green?style=flat-square" alt="Android 9.0+"></td>
      <td><img src="https://img.shields.io/badge/Android-12%20~%2015-brightgreen?style=flat-square" alt="Android 12~15"></td>
      <td>近年の Android 権限分離およびスコープドストレージ仕様に準拠</td>
    </tr>
    <tr>
      <td><b>CPU アーキテクチャ</b></td>
      <td><img src="https://img.shields.io/badge/CPU-ARM64--v8a%20%2F%20x86__64-blue?style=flat-square" alt="ARM64"></td>
      <td><img src="https://img.shields.io/badge/CPU-高性能マルチコア-blue?style=flat-square" alt="Multi-core"></td>
      <td>64 ビット Linux バイナリに依存するため、32 ビット端末は非対応</td>
    </tr>
    <tr>
      <td><b>本体空き容量</b></td>
      <td><img src="https://img.shields.io/badge/空き容量->=%202.5%20GB-yellow?style=flat-square" alt="2.5GB"></td>
      <td><img src="https://img.shields.io/badge/空き容量->=%205.0%20GB-brightgreen?style=flat-square" alt="5.0GB"></td>
      <td>Ubuntu rootfs、依存ライブラリ、モデル、ログスナップショット用</td>
    </tr>
    <tr>
      <td><b>システムメモリ (RAM)</b></td>
      <td><img src="https://img.shields.io/badge/RAM->=%204%20GB-yellow?style=flat-square" alt="4GB"></td>
      <td><img src="https://img.shields.io/badge/RAM->=%206%20GB%20以上-brightgreen?style=flat-square" alt="6GB+"></td>
      <td>Python 画像認識プロセスとゲーム本体の同時実行を安定化</td>
    </tr>
    <tr>
      <td><b>権限バックエンド</b></td>
      <td><img src="https://img.shields.io/badge/方式-Shizuku%20%2F%20Root-orange?style=flat-square" alt="Shizuku / Root"></td>
      <td><img src="https://img.shields.io/badge/方式-Shizuku--m%20(デバッグ不要)-brightgreen?style=flat-square" alt="Shizuku-m"></td>
      <td>タッチ入力シミュレーションおよび仮想画面プロジェクション権限に使用；MediaTek 端末は下記の修正版を使用してください</td>
    </tr>
  </tbody>
</table>

---

## クイックスタート

<table width="100%">
  <tr>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/ステップ-01-blue?style=flat-square" alt="Step 1"><br>
        <h4>パッケージの入手</h4>
      </div>
      <a href="https://github.com/wess09/AzurPilot-for-Android/releases/latest">最新リリース</a>から<b>デバイスのアーキテクチャに合った完全版 APK</b>（arm64-v8a / x86_64、対応する Runtime イメージを内蔵）をダウンロードします。
    </td>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/ステップ-02-indigo?style=flat-square" alt="Step 2"><br>
        <h4>初回展開</h4>
      </div>
      インストール後にアプリを起動し、Linux ルートファイルシステムがサンドボックスに展開されるのを待ちます（初回のみ 1〜3 分）。
    </td>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/ステップ-03-purple?style=flat-square" alt="Step 3"><br>
        <h4>権限の付与</h4>
      </div>
      案内に従って <a href="https://github.com/wess09/shizuku-m/releases/tag/v13.6.0-m2.r1093.bcb2b62a">Shizuku-m</a> で権限を許可するか、設定画面で Root バックエンドに切り替えます。
    </td>
    <td width="25%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/ステップ-04-green?style=flat-square" alt="Step 4"><br>
        <h4>タスク設定</h4>
      </div>
      「タスク」タブで画面描画を確認し、内蔵「AzurPilot」WebUI 画面から出撃計画や自動周回設定を行います。
    </td>
  </tr>
</table>

> [!TIP]
> **[Shizuku-m](https://github.com/wess09/shizuku-m)**（本プロジェクトが保守する修正版）の利用を強く推奨します。ワイヤレスデバッグの有効化が不要で、外部 Wi-Fi ネットワークがない環境でも正常に起動でき、MediaTek 端末におけるユーザーサービスの起動失敗も修正済みです。詳細は下記の注意を参照してください。

> [!WARNING]
> **MediaTek 搭載端末では[修正版 Shizuku-m](https://github.com/wess09/shizuku-m/releases/tag/v13.6.0-m2.r1093.bcb2b62a) を使用し、公式の 13.6.x は使わないでください。** 13.6 以降 Shizuku は特権サービスプロセスを `Application` で初期化するようになり、MediaTek が `LoadedApk.makeApplication` に注入したリソース先読みコード（`procName` が null → NPE）を踏み、プロセスが即座に `System.exit(1)` します。症状は「Shizuku は認可済みで binder も到達可能なのに、特権サービスが必ずタイムアウトし、`debug/` 配下に `service_boot_debug.log` が生成されない」というものです。公式で確認済みですが未修正のままです（[#1198](https://github.com/RikkaApps/Shizuku/issues/1198) / [#1171](https://github.com/RikkaApps/Shizuku/issues/1171)）。修正版は 13.6 以前の Context-only 経路へフォールバックします。

> [!IMPORTANT]
> **完全版 APK** は Runtime を内蔵しており、初回起動時にローカルで展開するためネットワークは不要です。**軽量差分 APK** は Runtime を含まず、初回起動時に GitHub からデバイスのアーキテクチャに対応した約 1GB の Runtime を自動ダウンロードします（Wi-Fi 環境を推奨。マルチミラー・マルチスレッド分割ダウンロードに対応し、設定でダウンロード元を選択できます）。その後の Android アプリ本体のみの更新では、軽量差分 APK を上書きインストールするだけで、既存の Runtime を再利用できます。

---

## アプリプレビュー

#### 简体中文

| | ホーム | AzurPilot | 設定 | 仮想スクリーン |
|:---:|:---:|:---:|:---:|:---:|
| **ダーク** | <img src="docs/screenshots/zh-CN-dark-home.jpg" width="260"/> | <img src="docs/screenshots/zh-CN-dark-overview.jpg" width="260"/> | <img src="docs/screenshots/zh-CN-dark-settings.jpg" width="260"/> | <img src="docs/screenshots/zh-CN-dark-virtual-screen.jpg" width="260"/> |
| **ライト** | <img src="docs/screenshots/zh-CN-light-home.jpg" width="260"/> | <img src="docs/screenshots/zh-CN-light-overview.jpg" width="260"/> | <img src="docs/screenshots/zh-CN-light-settings.jpg" width="260"/> | <img src="docs/screenshots/zh-CN-light-virtual-screen.jpg" width="260"/> |

#### 繁體中文

| | ホーム | AzurPilot | 設定 | 仮想スクリーン |
|:---:|:---:|:---:|:---:|:---:|
| **ダーク** | <img src="docs/screenshots/zh-TW-dark-home.jpg" width="260"/> | <img src="docs/screenshots/zh-TW-dark-overview.jpg" width="260"/> | <img src="docs/screenshots/zh-TW-dark-settings.jpg" width="260"/> | <img src="docs/screenshots/zh-TW-dark-virtual-screen.jpg" width="260"/> |
| **ライト** | <img src="docs/screenshots/zh-TW-light-home.jpg" width="260"/> | <img src="docs/screenshots/zh-TW-light-overview.jpg" width="260"/> | <img src="docs/screenshots/zh-TW-light-settings.jpg" width="260"/> | <img src="docs/screenshots/zh-TW-light-virtual-screen.jpg" width="260"/> |

#### English

| | ホーム | AzurPilot | 設定 | 仮想スクリーン |
|:---:|:---:|:---:|:---:|:---:|
| **ダーク** | <img src="docs/screenshots/en-dark-home.jpg" width="260"/> | <img src="docs/screenshots/en-dark-overview.jpg" width="260"/> | <img src="docs/screenshots/en-dark-settings.jpg" width="260"/> | <img src="docs/screenshots/en-dark-virtual-screen.jpg" width="260"/> |
| **ライト** | <img src="docs/screenshots/en-light-home.jpg" width="260"/> | <img src="docs/screenshots/en-light-overview.jpg" width="260"/> | <img src="docs/screenshots/en-light-settings.jpg" width="260"/> | <img src="docs/screenshots/en-light-virtual-screen.jpg" width="260"/> |

#### 日本語

| | ホーム | AzurPilot | 設定 | 仮想スクリーン |
|:---:|:---:|:---:|:---:|:---:|
| **ダーク** | <img src="docs/screenshots/ja-dark-home.jpg" width="260"/> | <img src="docs/screenshots/ja-dark-overview.jpg" width="260"/> | <img src="docs/screenshots/ja-dark-settings.jpg" width="260"/> | <img src="docs/screenshots/ja-dark-virtual-screen.jpg" width="260"/> |
| **ライト** | <img src="docs/screenshots/ja-light-home.jpg" width="260"/> | <img src="docs/screenshots/ja-light-overview.jpg" width="260"/> | <img src="docs/screenshots/ja-light-settings.jpg" width="260"/> | <img src="docs/screenshots/ja-light-virtual-screen.jpg" width="260"/> |

---

## 機能インターフェース一覧

<table width="100%">
  <thead>
    <tr>
      <th width="15%">モジュール</th>
      <th width="45%">主要な機能・役割</th>
      <th width="20%">操作性</th>
      <th width="20%">ステータス</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>ホーム</b></td>
      <td>システム健全性、PRoot コンテナ状態、CPU/メモリ負荷、診断ログへのショートカットを表示。</td>
      <td>カード形式メトリクス、ワンタップ診断</td>
      <td><img src="https://img.shields.io/badge/状態-統合ダッシュボード-blue?style=flat-square" alt="Overview"></td>
    </tr>
    <tr>
      <td><b>タスク管理</b></td>
      <td>仮想画面のリアルタイムプレビュー、出撃キューの順序調整、全画面タッチ手動操作への介入。</td>
      <td>低遅延映像ストリーム、手動介入</td>
      <td><img src="https://img.shields.io/badge/状態-実行コントロール-orange?style=flat-square" alt="Task"></td>
    </tr>
    <tr>
      <td><b>スケジュール管理</b></td>
      <td>タスクカレンダー、定期トリガー条件、自動復帰およびオフラインキュー管理。</td>
      <td>柔軟なルールマッチング、周期的ループ</td>
      <td><img src="https://img.shields.io/badge/状態-スケジューラ-purple?style=flat-square" alt="Schedule"></td>
    </tr>
    <tr>
      <td><b>AzurPilot コンソール</b></td>
      <td>アプリ内ネイティブ コンソール：タスク設定、インスタンス管理、リアルタイム ログ、統計ダッシュボード、Runtime 更新。UI は上流スキーマから自動生成。</td>
      <td>ネイティブ描画、切り替え不要</td>
      <td><img src="https://img.shields.io/badge/状態-コアエンジン-61DAFB?style=flat-square" alt="Engine"></td>
    </tr>
    <tr>
      <td><b>システム設定</b></td>
      <td>更新系統の選択、ランタイム割り当て、仮想ディスプレイ解像度、ログアーカイブ・エクスポート。</td>
      <td>設定永続化、SHA チェック</td>
      <td><img src="https://img.shields.io/badge/状態-全体構成-lightgrey?style=flat-square" alt="Settings"></td>
    </tr>
    <tr>
      <td><b>フローティング窓</b></td>
      <td>他のアプリの上に常時表示。実行進捗の確認、即時の一時停止/再開、最新ログの簡易表示。</td>
      <td>小型カプセル、ドラッグ配置</td>
      <td><img src="https://img.shields.io/badge/状態-オーバーレイ-brightgreen?style=flat-square" alt="Overlay"></td>
    </tr>
  </tbody>
</table>

---

## デュアルトラック独立アップデート機構

環境の長期的な安定性と更新時の手軽さを両立させるため、ホスト層と実行環境を独立して更新できるデュアルトラック設計を採用しています：

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/トラック-ホスト%20App%20更新-7F52FF?style=flat-square&logo=android&logoColor=white" alt="App Track"><br>
        <h4>軽量ホストアプリの上書き更新</h4>
      </div>
      <ul>
        <li><b>トリガー</b>：Android 内部バージョン番号（<code>VersionCode</code>）を照合。</li>
        <li><b>ペイロード</b>：Android ネイティブコードのみを含む軽量 APK。</li>
        <li><b>手順</b>：システム標準の <code>PackageInstaller</code> による通常のインプレース更新。</li>
        <li><b>データ安全性</b>：端末に展開済みの Linux Runtime、設定、ログを完全に維持。</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <div align="center">
        <img src="https://img.shields.io/badge/トラック-Runtime%20環境更新-E95420?style=flat-square&logo=ubuntu&logoColor=white" alt="Runtime Track"><br>
        <h4>コンテナイメージのホットスワップ</h4>
      </div>
      <ul>
        <li><b>トリガー</b>：GitHub Releases の <code>latest.json</code> 署名を照合。</li>
        <li><b>整合性検証</b>：ダウンロード完了後に厳密な SHA-256 ハッシュとファイルサイズを検証。</li>
        <li><b>ホット置換</b>：Python サービスを安全に停止後、ルートディレクトリを安全に入れ替え（<code>/config</code> 及びデータ領域は保持）。</li>
        <li><b>フォールバック</b>：通信途絶や更新見送り時でも、現在の安定バージョンをそのまま継続利用。</li>
      </ul>
    </td>
  </tr>
</table>

---

## 開発アクティビティ

<table width="100%">
  <thead>
    <tr>
      <th colspan="3" align="left">
        <img src="https://img.shields.io/badge/Metrics-RepoBeats%20Style-5C3EE8?style=flat-square&logo=github&logoColor=white" alt="RepoBeats">
        <b>リポジトリ開発アクティビティと対応状況</b>
      </th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td width="33%">
        <b>コミット頻度</b><br>
        <img src="https://img.shields.io/github/commit-activity/m/wess09/AzurPilot-for-Android?style=flat-square&color=00d4aa" alt="Commit Activity"><br>
        <small>月間コミット状況</small>
      </td>
      <td width="33%">
        <b>クローズされた PR</b><br>
        <img src="https://img.shields.io/github/issues-pr-closed/wess09/AzurPilot-for-Android?style=flat-square&color=6f42c1" alt="Closed PRs"><br>
        <small>マージ・クローズ済み PR 総計</small>
      </td>
      <td width="33%">
        <b>解決済み Issue</b><br>
        <img src="https://img.shields.io/github/issues-closed/wess09/AzurPilot-for-Android?style=flat-square&color=28a745" alt="Closed Issues"><br>
        <small>対応完了済み Issue 総数</small>
      </td>
    </tr>
    <tr>
      <td width="33%">
        <b>メインブランチ</b><br>
        <img src="https://img.shields.io/badge/Branch-main-181717?style=flat-square&logo=git&logoColor=white" alt="Main Branch"><br>
        <small>継続的デリバリーライン</small>
      </td>
      <td width="33%">
        <b>自動ビルド (CI/CD)</b><br>
        <img src="https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white" alt="CI"><br>
        <small>ネイティブ Runner マトリクスによる自動検証</small>
      </td>
      <td width="33%">
        <b>最新コミット</b><br>
        <a href="https://github.com/wess09/AzurPilot-for-Android/commits/main"><img src="https://img.shields.io/github/last-commit/wess09/AzurPilot-for-Android?style=flat-square&color=586069" alt="Last Commit"></a><br>
        <small>最新コード差分を確認</small>
      </td>
    </tr>
  </tbody>
</table>

---

## Star の推移

ダーク/ライトモード連動の Star-History グラフで、プロジェクトの成長を確認できます：

<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=wess09/AzurPilot-for-Android&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=wess09/AzurPilot-for-Android&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=wess09/AzurPilot-for-Android&type=Date" width="100%" />
  </picture>
  <br>
  <sub>データソース：<a href="https://star-history.com/#wess09/AzurPilot-for-Android&Date">Star-History</a> · クリックでインタラクティブグラフを表示</sub>
</div>

---

## ディレクトリ構成とビルド体系

```
.
|-- app/                      # Android ホストプロジェクトソース (Kotlin + Jetpack Compose)
|   |-- app/                  # アプリ主要モジュール、Compose UI、状態フロー
|   |-- annotation-api/       # アノテーション定義およびインターフェース契約
|   |-- ksp-processor/        # コンパイル時 KSP コードジェネレータ
|   `-- gradle/               # バージョンカタログ (libs.versions.toml)
|-- rootfs/                   # Linux 実行環境パッケージスクリプトと依存定義
|   `-- build/                # build-azurpilot.sh イメージ最適化・統合スクリプト
|-- .github/workflows/        # CI/CD パイプライン自動化 (rootfs.yml)
`-- tools/                    # ビルド支援ツールとローカルデバッグスクリプト
```

- **開発ツール標準**：Android Gradle Plugin 8.x、Kotlin 2.x、Jetpack Compose Material 3。
- **自動ビルドフロー**：GitHub Actions のネイティブ Runner マトリクス（arm64 / x86_64）上で Ubuntu 24.04 イメージの最適化、uv による依存ライブラリ導入、フロントエンドビルド、高圧縮 xz アーカイブ化を実行し、構成ごとに成果物を出力・公開。
- **署名セキュリティ**：本番 APK 署名は GitHub Actions Secrets で安全に管理され、改ざんを防止。

---

## 技術スタックと謝辞

### コアランタイムおよびシステム基盤

| コンポーネント | ライセンス | 役割 |
| :--- | :--- | :--- |
| **Ubuntu Base 24.04** | <img src="https://img.shields.io/badge/License-Canonical-lightgrey?style=flat-square" alt="Canonical"> | 構成別にビルドされた Linux コンテナ基本イメージ（arm64 / amd64） |
| **PRoot** | <img src="https://img.shields.io/badge/License-GPL--2.0-blue?style=flat-square" alt="GPL-2.0"> | Root不要ユーザ空間システムコールエミュレーション |
| **uv** | <img src="https://img.shields.io/badge/License-Apache--2.0%20%7C%20MIT-brightgreen?style=flat-square" alt="uv License"> | 高速なモダン Python パッケージマネージャー |
| **CPython 3.14** | <img src="https://img.shields.io/badge/License-PSF--2.0-blue?style=flat-square" alt="PSF-2.0"> | コアインタープリタ実行環境 |
| **AzurPilot** | <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square" alt="GPL-3.0"> | 自動化ロジック本体およびタスクエンジン |
| **ALAS-AOS** | <img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0"> | 本プロジェクトのソースリポジトリ。本リポジトリはここからのフォークで、ホストアーキテクチャとコンテナ化路線を継承 |
| **MaaFwApp** | <img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0"> | Android ホストアーキテクチャおよびアプリ設計テンプレート |

### Android ホスト側技術スタック

| ライブラリ | ライセンス | 用途 |
| :--- | :--- | :--- |
| **AndroidX Suite** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Jetpack Compose、Lifecycle、DataStore 等の最新コンポーネント |
| **Koin** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 軽量で実用的な DI フレームワーク |
| **OkHttp** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 高速かつ安定した HTTP クライアント |
| **Shizuku API** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Root不要のプロセス間権限昇格・通信インターフェース |
| **libsu** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | Root モード時における堅牢な Shell 実行フレームワーク |
| **FloatingX** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 軽快で使い勝手の良い Android フローティングウィンドウ |
| **XXPermissions** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 動的パーミッション管理 |
| **Apache Commons Compress** | <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen?style=flat-square" alt="Apache-2.0"> | 実行環境アーカイブ展開とストリーム処理 |
| **XZ for Java** | <img src="https://img.shields.io/badge/License-Public%20Domain-lightgrey?style=flat-square" alt="Public Domain"> | 高圧縮率 rootfs アーカイブの解凍サポート |

---

## コミュニティとサポート

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot-for-Android/graphs/contributors">
          <img src="https://img.shields.io/badge/Community-Contributors-blueviolet?style=for-the-badge&logo=github&logoColor=white" alt="Contributors">
        </a>
        <br><br>
        <h4>オープンソースへの貢献</h4>
        <p>コードの提出、設計改善、不具合の調査、動作検証に参加してくださったすべての開発者に感謝いたします。Issue やプルリクエストの投稿を歓迎します！</p>
        <a href="https://github.com/wess09/AzurPilot-for-Android/graphs/contributors">
          <img src="https://img.shields.io/badge/貢献者リスト-GitHub%20Graph-181717?style=flat-square&logo=github&logoColor=white" alt="View Contributors">
        </a>
        <a href="https://github.com/wess09/AzurPilot-for-Android/issues/new/choose">
          <img src="https://img.shields.io/badge/フィードバック-New%20Issue-0052cc?style=flat-square&logo=githubissues&logoColor=white" alt="New Issue">
        </a>
      </div>
    </td>
    <td width="50%" valign="top">
      <div align="center">
        <a href="https://github.com/wess09/AzurPilot-for-Android/stargazers">
          <img src="https://img.shields.io/badge/Project-Star%20History-f5a623?style=for-the-badge&logo=star&logoColor=white" alt="Star History">
        </a>
        <br><br>
        <h4>プロジェクトの応援</h4>
        <p>AzurPilot for Android が周回に役立っている場合は、ぜひリポジトリの Star を押して開発をご支援ください。</p>
        <a href="https://star-history.com/#wess09/AzurPilot-for-Android&Date">
          <img src="https://img.shields.io/badge/Star%20推移グラフ-Star--History-orange?style=flat-square" alt="Star History Link">
        </a>
      </div>
    </td>
  </tr>
</table>

---

## ライセンス

本プロジェクトは [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE) のもとで公開されています。

プロジェクトに同梱されているライセンスファイル：
- [`LICENSE-ALAS-AOS`](LICENSE-ALAS-AOS) — AGPL-3.0
- [`LICENSE-MaaFwApp`](LICENSE-MaaFwApp) — AGPL-3.0
- [`LICENSE-AzurPilot`](LICENSE-AzurPilot) — GPL-3.0

各 Python 依存パッケージおよび推移的依存の原ライセンス（計 152 項目）は、端末内の `/opt/azurpilot/licenses` に保管されています。

---

<div align="center">
  <sub>AzurPilot for Android はオープンソースライセンスに準拠し、コミュニティにより維持されています。問題が発生した場合は Issue または Pull Request をご活用ください。</sub>
</div>
