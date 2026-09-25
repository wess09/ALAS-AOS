# Handoff 2026-09-17 · WebUI 闲置环修复（首版翻车 → CDP 坐实根因 → WebView 层终版）

> 上一篇：`2026-09-17-args-regen-fix.md`（活动列表冻结修复）。本篇记「闲置状态环不停转圈 + 环旁神秘方框」的完整修复链。期间另有两次已 push 交付：`706c242`（改名 更早的旧名 + 大凤图标）、`4e2178e`（运行配置卡一行化 ✅ + 闲置环首版修复 ❌ 后被本篇终版取代）。

## 根因（CDP 实证，与首版推断不同）

- AzurPilot `set_status` 闲置态：`put_loading(color="secondary").style("--loading-border-fill--")`。
- pywebio 的 `.style()` 由 webiojs `getWidgetElement` 以 `attr({style: n+";"+style})` 写在 **put_html 外包装 div**（spinner 父级）上；属性字符串原样保留。
- `azurpilot.css` 的 fill 规则（1.5rem + 四边同色 border，**无圆角**）因此全部落在 wrapper：画出**静态方框**（= 用户截图里「神秘方框」本体）。
- 内层真正的 `.spinner-border.text-secondary` 完全没被定制，保持 Bootstrap 默认：**0.75s 旋转 + border-right 透明缺口**。
- 首版修复（4e2178e，往 azurpilot.css fill 规则补 `animation:none`）打在 wrapper 上对 spinner 天然无效；两帧对比（缺口弧帧间移动）现形翻车。**推断修复必须验证到真正的目标元素。**

## 终版方案（用户约束：不动 AzurPilot 代码，防热更新冲突）

- `AzurPilotScreen.kt` 新增 `IDLE_SPINNER_FIX_JS`，`onPageFinished` 幂等注入 `<style>`（`!important` 碾压，不在意加载序）：
  1. `.spinner-border.text-secondary{animation:none !important;border-right-color:currentColor !important}`——按类名直打真 spinner：停转 + 补缺成完整圆。仅 secondary 命中（闲置/UpToDate/RemoteNotRunning 三 fill 态）；Running(success)/Warning 等动态态照常旋转。
  2. `div[style*="--loading-border-fill--"]{border:none !important;width:auto !important;height:auto !important}`——剥掉 wrapper 方框 artifact。
- 设备 azurpilot.css 还原上游 pristine（92c07aa 原样，`exec-out cat` 拉回 diff=空）；仓内双源补丁（`rootfs/patches/assets/gui/css/` + assets 副本）git rm——它们正是 4e2178e 引入的，本方案下不再需要。

## 方法资产（已沉淀 debug.md）

- **CDP 直查计算样式**：DEBUG 包已开 `setWebContentsDebuggingEnabled`；socket 名 `webview_devtools_remote_<pid>`（`cat /proc/net/unix | grep -i devtools` 拿，装机后 pid 变需重拿），`adb forward` 后 node 脚本 Runtime.evaluate。判「画没画」用 `borderTopStyle`（`none` 时 `border-*-color` 仍算出 currentColor，是坑）。
- **两帧对比判动没动**：同区域 crop 隔 ~1.2s 两帧 diff，像素级零差异 = 静止。
- adb 读文件用 `exec-out`（`adb shell cat` 走 pty 会加 CRLF，diff 全屏假差异）。

## 验证状态

- ✅ CDP 计算样式：spinner `animName:none`、`borderRight==borderLeft`、wrapper `borderTopStyle:none`。
- ✅ 两帧截图像素级零差异；目视「AzurPilot ◯ 闲置」完整静态灰圆、方框消失（与桌面 gooey 观感一致）。
- ✅ 设备 azurpilot.css = 上游 pristine，AzurPilot 树零改动，热更新无忧。
- ✅ 用户目视终验通过（状态行静态圆，不再转圈）。

## 设备状态

手机解锁亮屏、App 前台、AzurPilot WebUI 正常（总览页渲染健康）；设备 AzurPilot commit 92c07aa（上游 HEAD）。未私按「开始挂机」。
