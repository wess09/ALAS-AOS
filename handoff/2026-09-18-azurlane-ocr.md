# Handoff 2026-09-18 · azur_lane 字体 OCR 权重搬家上机（mxnet→纯 numpy），真机验证通过

> 上一篇：`2026-09-18-d3-double-rootcause-fix.md`（遗留后续项「azur_lane 字体模型上机」）。**本篇即该后续项的完成篇**：上游 cnocr densenet-lite-gru 权重（39 字符 AL 字体模型）已纯 numpy 移植上机，替代通用 PP-OCR 接管 azur_lane 识别，真机双酸试通过。

## 路线（用户拍板）与为什么

- 搬权重不搬环境：mxnet 无 ARM64 wheel 且已进 Apache Attic，整条 2019 依赖链（cnocr 1.2.2 + mxnet 1.6.0）不可上机；`.params` 是 mxnet 私有格式，权重可搬格式不能搬 → PC 端转储 npz，手机端纯 numpy 手写前向。
- 红线遵守：AzurPilot 上游代码零改动；`module/ocr/{rpc.py,al_numpy.py}`、patches、overlay 模型文件均为 AzurPilot 自有资产，走 overlay 机制（每次启动幂等铺 /opt/azurpilot，免重烘 rootfs）。
- 回滚锚点：commit `eb0f7cd`（任务前已 push）。

## 交付物（全部双源：rootfs/overlays/ + app/app/src/main/assets/azurpilot/overlay/，cmp 一致）

- `module/ocr/al_numpy.py`（新建）：densenet-lite（BN eps=1e-5、valid 池化、k(2,3) depthwise、末段 **k(2,1)** 池化——符号 json 实证，非文档 (2,2)）→ BiGRU（cuDNN 变体：gate 序 r/z/n，n=tanh(i2h_n+r·h2h_n)，h=(1-z)n+z·h_prev）→ FC(39)；预处理/补齐/0.5 置信门/width//4 截尾/CTC 解码/cand_alphabet 乘法掩码逐字复刻上游；零 module.* 依赖可独立 import。性能：im2col+sgemm 后 PC 单行 329ms→18ms。
- `models/ocr/azur_lane/{weights.npz,label_cn.txt}`：桌面 toolkit python（PC 侧 mxnet，合规只读用法）转储，76 数组 3.3MB，epoch 15 精度 99.43%。
- `module/ocr/rpc.py`（改）：双引擎路由——lang='azur_lane' → numpy 字体模型（单行/成批/atomic；整页 ocr()=PP-OCR det 出框 + AL rec）；其余 lang → PP-OCR 不变；`set_cand_alphabet` 恢复上游状态语义；AL 加载失败自动回落 PP-OCR；`_best_text/_all_texts` classmethod→实例方法（已查 spike-f-ocr-gate.py 兼容）。
- PC 侧工程（`.tmp/ocr-azurlane/`，不入库）：`dump_weights.py`/`dump_refs.py`（13 组参考批：真徽章/名字图/噪声，分层激活+golden）、`np_forward.py`/`test_al_numpy.py`（13/13 批 prob max|Δ|≈2e-6，字符串全同）、`test_rpc_routing.py`（路由/掩码/close 语义 PASS）。

## 真机验证（2026-09-18 晨，0.1.1-alpha.7 (52)，release）

1. **模型上机**：AzurPilot 日志 `AzurPilot OCR: loading azur_lane numpy model from ./models/ocr/azur_lane` → `azur_lane numpy model loaded (39 classes)`（16ms，无 fallback 警告；proot numpy 兼容，`np.lib.stride_tricks.as_strided` 路径无坑）。
2. **酸试 1·活动图选关（原 'ai' 误读场景）**：`[campaign 0.013s] ['D3', 'D1', 'B2']`——**D1 读准**（PP-OCR 时代读 'ai'/'01'）；顺利进图，D3 连刷两轮（BATTLE_0~7 ×2），`[OCR_OIL 0.02~0.07s]` 读数 8930→8653→8618 合理递减——**「活动图只刷一遍就停」的油量误读同步消除**。全程 0 ERROR / 0 MapDetectionError / 0 CampaignNameError。
3. **酸试 2·GemsFarming 'MRT' 崩溃**：未到 NextRun（昨日崩溃后 failure 延迟，当日日志窗口无调度记录）。OCR 栈与酸试 1 同源（dock 等级=同一 azur_lane 模型），留待自然调度复验——若再现 `ValueError: ... 'MRT'` 再立案。
4. **耗时**：campaign 13ms/行、OIL 20~70ms/次，ARM 端与 PC（18ms/行）同量级，挂机节奏无感。

## 「App 自体死亡」悬案·第三次疑似复发的澄清（收窄，未破）

- 现象：alpha.7 装机后 monkey 拉起，wrapper 一度不应答、ps 查无 alioth 进程；第二次拉起后 ~4 分钟自愈（wrapper/gui/runner 全上线）。
- 本次新证据：**无 crash 文件**（`log/crash/` 空）、**无新 tombstone**（最新 09-15 旧件）；app.log 两次 startup 横幅间零 W+ 行。死因**排除 App 代码崩溃**。
- 实际大头：启动链静默 PREPARING 段（overlay 同步→env_fix pip→热更新 ls-remote，release 版 Timber 只落 W+，logcat/文件日志全静默）期间进程"看起来死了"；叠加系统后台管理/install force-stop 余波。方向已明：非崩溃，是静默期+系统杀。

## 环境速查（本会话实测有效）

- adb forwards：`tcp:22300` 桥 / `tcp:22400` wrapper / `tcp:32267→tcp:22267` WebUI（**22267 不挂 forward**，撞桌面版 AzurPilot 的教训）。
- wrapper：`GET /status` `/logs?tail=N`（上限 2000）、`POST /start?config=azurpilot` `/stop` `/tool/start?name=...`。
- session.log：`/sdcard/Android/data/com.aliothmoon.azurpilot/files/log/proot/session.log`（proot 生命周期唯一可信流水）。
- 线上包名 `com.aliothmoon.azurpilot`（release，run-as 不可用）；proot 进程判活：`ps -A | grep libproot`。
- OCR 模型落盘位置（proot 内）：`/opt/azurpilot/models/ocr/azur_lane/weights.npz`——overlay 铺的，删了重启 App 会再铺。
- 构建：`cd app && JAVA_HOME='D:\VSCodeCache\shizku-m\build-env\jdk-17.0.2' GRADLE_USER_HOME='D:\VSCodeCache\azurpilot-azurpilot\.tmp\gradle-home' cmd //c 'gradlew.bat assembleRelease --console=plain'` → `app/app/build/outputs/apk/release/app-release.apk`，`adb install -r` 即可（overlay 资产随包走，免烘 rootfs）。

## 当前现场

- runner 运行中：Event D3 自律连刷（用户挂机意图，勿 /stop）。油 ~8600、币 ~138k。
- 账册：devlog.md 已记 ✅（本篇同步）；debug.md 无需新增（无新规则）；README 不受影响。

## 后续项（未做，勿擅自启动）

- **GemsFarming 自然调度复验**：到点看是否还崩 'MRT'；崩则立案（证据=wrapper /logs）。
- **「App 自体死亡」立案门槛**：下次复发时先查 `log/crash/`、tombstone、`ps -A | grep libproot` 三分支再动手——本次已排除代码崩溃方向。
- **下次新活动 0% 徽标**：字体模型已上机，理论上「'01' 毒害」不会再复发；若复发说明名字图 ROI/预处理有第二处差异，再查。
