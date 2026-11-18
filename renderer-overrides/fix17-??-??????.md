# fix17 顶栏避让 · UI/UX 复核（360/640/800/1280）

> 复核人：ui-ux-designer（角色5）｜对象：`dist/motrix-android-2.4.3-fix17-debug.apk`（SHA C9967341…）
> 方法：读 fix17 实际落地 CSS（`assets/mobile-adapt.css` §20）+ 逐张比对 `reports/fix17_*.png` 与 `fix16_700.png` 的 OCR/像素证据（本机无真机，证据取自 Lead 提供的真机 `?vw` 截图）。

## 结论（1 条 P1 回归 + 其余通过）

| 宽度 | 结论 | 依据 |
|---|---|---|
| **vw=700（640–767）** | ❌ **回归：标题被实底顶栏遮挡、不可见** | 见下「关键证据」 |
| vw=360（<640） | ✅/⚠ 逻辑上单次让位、无 92px 双倍；但该模拟视口高仅≈162 CSS px，截图不可用于目测 | CSS §20 仅 `main[data-slot="sidebar-inset"]` 垫 44/56 一次；`vw=360` 截图 OCR 仅读到系统时间，内容被压缩成一条，属 `?vw` 只定宽不定高的模拟假象 |
| vw=1280（≥768） | ✅ 无回归 | OCR：`仪表板@(129,211)` 在左侧栏导航内，正文/卡片齐全 |

## 关键证据：vw=700 标题消失

同分辨率 2400×1080、同滚动位置（`引擎@y404 / 就绪@y506 / 限速@y399` 两版完全一致）逐项 OCR：

- **fix16_700.png**：`+ 仪表板 @ (600,155)`（标题可见）
- **fix17_700.png**：**无任何「仪表板」**；y108–237 区带仅有左侧徽标（`灰 @cols4–7`），标题位置为空

即：**fix16 能看到的「仪表板」，到 fix17 变成看不见了**，其余元素位置一致 → 是 fix17 引入的遮挡，不是滚动/状态差异。

## 根因（CSS 叠层）

fix17 同时做了两件互相冲突的事：
1. **①顶栏全宽实底**：`.window-chrome{background:var(--mtx-bg)!important;...}`，该元素 `position:fixed; z-index:30`，实底矩形覆盖「原生状态栏以下 ~40px」整条带；
2. **②640–767 同行**：`[class~="min-w-0"]:has(>h1/h2){padding-inline-start:160px}` 只做**横向**让位，竖向**不下移** → 标题仍停在 y≈17 CSS（≈物理 y155，落在实底带内）。

→ 标题被 z30 实底顶栏盖住（黑底黑字，等于消失）。**①实底 与 ②同行 不能同时成立**：官网的「同行」本来就依赖顶栏透明（可透出标题），fix17 把顶栏改实底后就必然压住同一行的标题。

另：`?vw=700` 时视口约 700×315 CSS，`y17` 的标题在实底带内；而 `vw=360` 只是把宽度压到 360、高度按比例缩到≈162 CSS，纵向失真，不能作为 <640 的验收图。

## 建议修法（二选一，推荐 A）

**A. 统一竖向下移（推荐，和你已采纳的 §2.1 一致，且可顺带去掉 `:has`）**
把「竖向下移」从 `<640` 扩到 `<768`，删掉 640–767 的同行规则：
```css
/* <768 一律把标题移到实底顶栏之下；不再同行 */
@media (max-width: 767.98px){
  html.mtx-mobile main[data-slot="sidebar-inset"]{ padding-top:44px !important; box-sizing:border-box; }
}
@media (max-width: 767.98px) and (pointer: coarse){
  html.mtx-mobile main[data-slot="sidebar-inset"]{ padding-top:56px !important; }
}
/* 删除：@media (640–767){ [class~="min-w-0"]:has(>h1/h2){padding-inline-start:160px} } */
```
好处：实底顶栏保留；只垫一次、无 92px 双倍；去掉 `:has`（兼容老 WebView 更稳）；三档行为一致。代价：640–767 标题占 44/56px 竖向（该档已是横屏手机，可接受）。

**B. 保留同行（不推荐）**：仅对 `(min-width:640px) and (max-width:767.98px)` 把 `.window-chrome` 背景改回透明，并把标题与徽标在 40px 带内**垂直居中**。实现更碎、滚动内容又会透字，且仍依赖 `:has`。

## 待你定
采用 A 我再更新 `proposal-topbar-uiux.css` 对齐；构建 fix18 后我按 360/640/700/800/1280 复测「标题 top≥40 或与徽标不相交 + 无 92px 双倍 + 标题真实可见（OCR 找得到）」。
