# 上游 Issue #6000 跟进执行手册（交接其他会话窗口）

> 创建：2026-09-20 · 创建人：AzurPilot 主会话
> 用途：AzurPilot 上游 issue [LmeSzinc/AzurLaneAutoScript#6000](https://github.com/LmeSzinc/AzurLaneAutoScript/issues/6000) 的后续跟进。本文档自包含，接管会话无需其他上下文。
> **状态（2026-09-20 12:04 UTC）：证据已上传并回复（详见 devlog 同条目），进入「盯上游回应」阶段。**
> ~~原状态：本 issue 已移交其他会话窗口处理，AzurPilot 主会话不再跟踪。~~（本窗口已按本手册完成回复）

---

## 1. TL;DR（30 秒版）

- 我们于 2026-09-19 向 AzurPilot 上游提交了 issue #6000：**国服仓库材料页 UI 改版打废 `MATERIAL_CHECK` 模板，E 科研拆解/仓库开箱链卡死**。
- **上游 LmeSzinc 已于 2026-09-20 02:53 (UTC) 回复：「上传log和截图」**。
- **唯一待办：整理日志 + 截图证据，上传到 issue 并回复。**
- 证据素材大部分已在本地 `.tmp/` 备好（见 §4），缺的可从真机补拍（见 §5）。
- 若上游随后要求提 PR：做**黑底 + 稳定锚点版**模板（非我们现用的整屏实帧版），要点见 §7-B。

---

## 2. Issue 档案

| 项 | 值 |
|---|---|
| 链接 | https://github.com/LmeSzinc/AzurLaneAutoScript/issues/6000 |
| 标题 | [CN] 仓库材料页 UI 改版致 MATERIAL_CHECK 失效：E 科研/开箱拆解链卡死 `_storage_enter_material` |
| 状态 | **OPEN** |
| 提交时间 | 2026-09-19 13:28 UTC |
| 上游回应 | 2026-09-20 02:53 UTC，LmeSzinc：「上传log和截图」 |
| 提交账号 | Shinarin（gh 已登录） |

**已提交的正文要点**（回复时不要重复，只需补证据）：

- 现象：2022-08-17 `faf5a74e` 后从未更新的 `assets/cn/storage/MATERIAL_CHECK.png` 对新版材料页整帧匹配 ccoeff ≈ **0.04**（阈值 0.85）→ `_storage_in_material()` 永 False → `_storage_enter_material()` 无限空点 → `GameTooManyClickError`。
- 影响面：E 系科研拆解（`module/research/research.py:265-271`，8 种 genre 中唯一碰仓库的分支）+ 仓库任务开箱（`storage_use_box`）。
- 复现条件：过滤器含 E 项目（官方预设如 `series_9_blueprint_ta152` 默认含）+ 仓库有装备箱 + 刷出 `equipment_amount > 0` 的 E 项目。
- 时间线：2026-04-26 日志同链路旧模板跑通 → 2026-09-17 起必现；期间维护公告无仓库 UI 条目（静默改版）。
- 临时修复已验证：新实帧模板 ccoeff=1.0、他页 ≤0.27、拆解链 9 秒跑通。
- 已表态：**愿提 PR**（建议黑底 + 稳定锚点版）。
- 相关 issue：#4276（JP 同症状已修）、#4815（CN 同链 open）、#4934（CN closed）。

---

## 3. 环境与操作速查（取证/上传前必读）

```bash
# GitHub 访问必须走代理（一次性环境变量，勿写 git config）
export HTTPS_PROXY=http://127.0.0.1:7897
gh issue view 6000 --repo LmeSzinc/AzurLaneAutoScript        # 看最新动态
gh issue comment 6000 --repo LmeSzinc/AzurLaneAutoScript ... # 回复

# adb（Git Bash 下每条命令前必须）
export MSYS_NO_PATHCONV=1
# 设备：HONOR PPG-AN00，序列 AVAY025422002864
adb devices   # 确认在线

# 真机取证通道（release 版无 run-as，走 wrapper HTTP）
adb forward tcp:22400 tcp:22400          # wrapper 调度口
curl http://127.0.0.1:22400/status       # wrapper/gui/runner 在岗状态
curl http://127.0.0.1:22400/logs         # 拉日志（注意：只给最新 txt；
                                         # 拉 runner 日志需先 POST /start 解锁）
```

- 工作目录（AzurPilot 仓库）：`D:\VSCodeCache\azurpilot-azurpilot`；临时文件一律放 `.tmp/`（已 gitignore）。
- **红线：不改 AzurPilot 上游源码**；桌面端 `C:\other\AzurLaneAutoScript` **只读**（唯一例外：§6 的临时救场拷贝，用户已知情）。
- 账册规则：有重大进展就更新根目录 `devlog.md`（倒序）+ `handoff/` 新文件；push 需用户授权。

---

## 4. 现成证据清单（本地 .tmp/，上传前逐张目验）

| 文件 | 内容 | 建议用途 |
|---|---|---|
| `.tmp/azurpilot-log-0919.txt` (261KB) | **卡死期完整 runner 日志**：`_storage_enter_material` 循环空点 → `GameTooManyClickError` → runner exit/respawn（session 累计 87 次） | **主日志证据**，截取循环段直接上传 |
| `.tmp/live-material-check.png` (376×184) | 新版材料页左下角实拍放大图 | 展示"2022 模板锚点在新 UI 中的实际样子" |
| `.tmp/live-material-enter.png` | 新版材料页实拍（MATERIAL_ENTER 区域） | 新 UI 布局佐证 |
| `.tmp/upstream-material-check.png` (1280×720) | **上游 2022 版模板本体**（黑底 + 左下「素材」标记） | 与实拍对比，直观说明锚点失效 |
| `.tmp/vd-material2/3/4/6.png`、`.tmp/vd-material-box.png` | 虚拟屏实帧：新版材料页各状态（含开箱） | 新 UI 全貌截图（挑 1~2 张清晰的） |
| `.tmp/asset_score.py` | 14 个 storage 资产离线打分脚本（ccoeff + 色差） | 如需补打分数据可复跑 |
| `.tmp/upstream-issue-material-check.md` | issue 正文草稿（与已提交版一致） | 引用素材 |

**注意**：
- `.tmp/tpl-material-check.png` 是我们重制的新模板工作图（10240×5760 异常分辨率，是取证脚本的缩放产物），**不要上传**，避免混淆。
- 用户最初提供的手机截图 `Screenshot_20260917_214942_MainActivity.jpg` 在桌面已找不到；如需"用户视角截图"，用 §5 流程从真机补拍。
- 上传图片用 GitHub 网页拖拽最稳；`gh issue comment` 不直接支持附件，可把图先传到 issue 评论编辑框或引用已上传的 URL。

## 5. 缺证据时的真机补拍流程

1. 手机连 adb，App（更早的旧名）内点「开始挂机」让 wrapper/gui/runner 在岗。
2. 复现路径：让 AzurPilot 跑到 E 科研拆解（或手动进游戏仓库→材料页）。
3. 截虚拟屏画面：`adb forward tcp:22300 tcp:22300` 后用 `.tmp/vd_probe.py`（行 JSON 协议）截屏/点击；或直接在 App 悬浮窗/投屏界面系统截图。
4. 拉日志：`POST http://127.0.0.1:22400/start` 后 `GET /logs`。
5. 细节参考根目录 `devlog.md` 2026-09-19 条目的排查段。

## 6. 我方修复现状（回复上游时可引用的"已验证临时修复"细节）

- 补丁资产：`rootfs/patches/assets/cn/storage/MATERIAL_CHECK.png` 与 `app/app/src/main/assets/azurpilot/patches/assets/cn/storage/`（双源镜像，cmp 已验证一致；这是第 8 个真机校准资产，走 patches 通道不动上游源码）。
- 已随 **更早的旧名 v0.1.2** 发版（2026-09-19，GitHub Release: Shinarin/AzurPilot）。
- 验证数据：新材料页 ccoeff=1.0；装备/设计/拆解页 ≤0.27；色差 6.4 < 30（免改 assets.py）；拆解链 `0/15 → 15/15` 九秒跑通。
- **局限（对上游要诚实）**：我们的模板是整屏实帧（含格子内容），仅本账号验证；账号迁移稳健性未验证——这正是建议 PR 用黑底+锚点版的原因。
- 桌面端救场：把补丁 png 拷到 `C:\other\AzurLaneAutoScript\assets\cn\storage\`（热更新会冲掉，上游修复落地后删）。

## 7. 情景预案

**A. 上游认可后直接自己修** → 盯 merge；落地后：(1) 删除我们双源补丁资产并发新版；(2) 删桌面端救场文件；(3) 账册收官。

**B. 上游要求提 PR（大概率，因我们已表态愿提）** → 制作**黑底 + 稳定锚点版** `MATERIAL_CHECK.png`：
- 风格对齐 2022 旧版：黑底 1280×720，仅保留稳定锚点区域内容。
- 锚点建议取**底部「设计图/装备/材料」页签栏**或左下提示文字区——不含任何账号相关的格子内容。
- 验收：材料页 ccoeff ≥0.9；装备/设计/拆解/开箱/主界面等邻近页 ≤0.3；用 `.tmp/asset_score.py` 打分留证。
- PR 引用 #6000，附新旧 UI 对比图与打分表。

**C. 上游搁置/无后续** → 维持现状（我们的补丁已保住用户），把 issue 加入每周巡检；同类 #4815 open 16 个月的先例说明搁置是可能结局。

**D. 上游反问/质疑** → 高频问题备答：
- "为何桌面端没报？" → 触发面窄：E 在过滤器优先级垫底 + 需仓库存箱 + 需刷出带拆解的 E；我们考证桌面 50 天 Research 0 次实际触发（上次触发 2026-04-26，当时还是旧 UI）。
- "是不是你们 fork 改坏了？" → 三渠道（123clouddisk CDN / git.lyoko.io / GitHub master）内容已实证同源，`assets/cn` + `module` 对桌面全量 `diff -r` 全空；模板三处 sha256 全同（`1e87852a…`，即 2022 版）。

## 8. 参考链接

- Issue：https://github.com/LmeSzinc/AzurLaneAutoScript/issues/6000
- 相关：#4276（JP 已修）/ #4815 / #4934
- AzurPilot v0.1.2：https://github.com/Shinarin/AzurPilot/releases/tag/v0.1.2
- 本地权威记录：`devlog.md` 2026-09-19 两条（修复全案 + 发版）、`handoff/2026-09-19-release-v012.md`

---

## 执行检查单（接管会话按此推进）

- [x] `gh issue view 6000` 确认上游无更新的新回复
- [x] 目验 §4 证据，挑定上传集（日志 1 份 + 截图 2~4 张）
- [x] 缺料则按 §5 补拍（未缺料；真机 error 目录取证结论改为等效重打包，见 devlog 2026-09-20 跟进条目）
- [x] 上传并在 issue 回复（2026-09-20 12:04 UTC 已发：zip 打包件 + 节选 txt + 三张对比图）
- [x] 回复后更新 `devlog.md` + 本文档状态
- [ ] 进入情景预案对应分支：**当前 = 盯上游回应**（自修 → A；要 PR → B 做黑底+稳定锚点版；搁置 → C 周检）
