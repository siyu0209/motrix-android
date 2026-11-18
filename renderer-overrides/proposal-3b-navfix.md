# proposal-3b-navfix · D-3B-04 / D-3B-05 交互规范

> 角色：ui-ux（视觉/交互规范与验收基线）　|　配套实现文件：`renderer-overrides/proposal-3b-navfix.css`（纯 CSS 提案）
> 依据：`docs/11-3B缺陷需求转译.md`（D-3B-04、D-3B-05）、`docs/10-布局断点分析.md`（顶栏/侧栏/compact-header 几何）
> 约束：**不改 JS / 不改 Kotlin / 不改 `assets/mobile-adapt.css` 本体**；本文件只出"规范 + 可直接落地的选择器与 CSS"。
> 单位：WebView `viewport=device-width,initial-scale=1` → **1 CSS px = 1 dp**，故 44dp = 44 CSS px。

---

## TL;DR

- **D-3B-04（侧栏难召回）**：召回入口就是顶栏那个「切换侧栏」按钮 `[data-slot="sidebar-trigger"]`，实尺寸 `size-7 = 28px`。规范做法——**用透明 `::after` 命中层把热区扩到 ≥44×44dp**（28→44，各向外扩 8px），图标提到满不透明 + hover/active/focus 反馈，`z-index:60` 并 `pointer-events:auto` 保证不被内容/下载筛选栏遮挡。**纯 CSS 即可落地，无需改 JS**。
- **D-3B-05（缺 3B→原生 返回入口）**：**首选放在顶栏右侧 actions 槽**（`[data-slot="window-chrome-actions"]`），与「+ 新建」同簇、位置固定、`z-[60]`、拇指可达；视觉 32px、热区 44dp。备选右下角悬浮 FAB（48dp）。**该入口的点击动作需 Android 侧提供 `finish()` 桥**（建议复用/扩展 `window.MotrixAndroid.onBack()` 为 `exitToNative()`）——本规范只定义外观与放置，桥梁由 multiplatform-dev 实现。
- 两处都遵守 `docs/10` 的断点与安全区：断点 768 / 640 / pointer:coarse；刘海安全区叠加 `env(safe-area-inset-*)`。

---

## 1. 关键几何与可命中钩子（来自 docs/10 + bundle 直证）

| 对象 | 精确选择器 / 依据 | 关键值 |
|---|---|---|
| 3B 外壳根 | `.web-window-chrome`（`app-layout.tsx:102`） | 作用域根 |
| 顶栏条 | `.window-chrome`（`window-chrome.tsx:226-233`） | `position:fixed; z-index:30; 高40; padding-inline 12/20` |
| 顶栏 leading 槽 | `[data-slot="window-chrome-leading"]`（class 含 `relative z-[60] flex shrink-0 items-center gap-2`） | `z-60`，容纳 徽标+切换+新建 |
| 顶栏 actions 槽 | `[data-slot="window-chrome-actions"]`（class 含 `relative z-[60] ... gap-2`） | `z-60`，右侧「+ 新建」簇 |
| **折叠侧栏触发按钮** | `[data-slot="sidebar-trigger"]`（bundle @≈591443：`variant:"ghost" size:"icon" class:"size-7 [&>svg]:opacity-65..."`，`sr-only` 文案 `chrome.toggleSidebar`） | `size-7 = 28px`（**低于 44dp，D-3B-04 根因**） |
| 侧栏收起态 | `.group\/sidebar-wrapper[data-state="collapsed"]`（`globals.css:28-35`、`sidebar.tsx:153`） | `<768` 恒 `collapsed` |
| 侧栏抽屉（<768） | `[data-slot="sidebar"][data-mobile="true"]`（`sidebar.tsx:203-228`，宽 288、`max-w-[calc(100vw-2rem)]`） | 由 Radix Sheet 承担 overlay 滑出/收起 |
| 左簇安全区 | `--window-chrome-safe-area-leading = 160px`（`globals.css:728`） | 徽标 x≈12..84，左簇≈152 |

> 结论：D-3B-04 的"按钮"已有稳定钩子 `data-slot="sidebar-trigger"`，**无需 reverse 再定位**；D-3B-05 的按钮由实现方新增，统一打 `data-mtx-native-back` 钩子即可被本 CSS 接管。

---

## 2. D-3B-04　侧栏召回入口规范

### 2.1 尺寸与热区（核心）

| 项 | 现状 | 规范值 | 落点 |
|---|---|---|---|
| 视觉尺寸 | 28×28（size-7） | **保持 28（不放大视觉，避免顶栏拥挤）** | — |
| 可点击热区 | 28×28 | **≥44×44dp**（触屏加档 48） | `::after` 透明层：`width/height:44px; top/left:50%; translate(-50%,-50%)` |
| 外扩量 | — | 各向外扩 **8px**（44−28）/ 10px（48−28） | 见 CSS §1.2 |
| 圆角 | — | 10–12px（与顶栏其它 icon 钮一致） | — |

> 为什么用 `::after` 而不是 `width:44px`：直接改按钮盒会挤压 `gap-2` 的邻居（徽标/新建），造成布局位移；伪元素命中层**只扩大命中、不占布局**，最稳。若个别浏览器对伪元素命中支持异常，退化方案：`width:44px;height:44px;margin:-8px;`（占位仍 28）。

### 2.2 可见性

- 图标 `opacity` 从默认 `.65` 提到 **≈.92**（避免"看不清→以为不可点"）。
- `hover / :active / :focus-visible` 给背景高亮 + 焦点环（`2px` accent + `offset 2px`）。
- 收起态（`[data-state="collapsed"]`）可选加 6% 淡底，弱提示"点这里唤出"。

### 2.3 层级（z）——不被下载筛选/内容遮挡

- 顶栏 `.window-chrome` 为 `fixed; z-index:30`（覆盖层，高于内容流的 z 自动）。
- leading / actions 槽为 `relative z-[60]`，故槽内按钮天然高于内容层与下载状态分类栏。
- 规范：触发按钮显式 `z-index:60`、`pointer-events:auto`；命中层 `pointer-events:auto`。
- 若 D-3B-03 修复中给下载筛选栏加了 `position:sticky` 且 `z` 偏高，确保其 `z < 30`（内容层），不得压过顶栏交互。

### 2.4 overlay 滑出与"可再收起"

- `<768`：侧栏由 Sheet 抽屉以 overlay 方式滑出（含遮罩），点遮罩/关闭即收起——**这是现成机制，直接复用**。
- 规范补充：抽屉 `max-height:100dvh`（用动态视口，避免底部导航栏裁切）；左右/底部叠加 `env(safe-area-inset-*)`；遮罩 `z` 介于顶栏(30)与弹层(50)之间（取 45），保证遮罩内点击不穿透顶栏。
- `≥768` 收起态：按钮 `onToggle → toggleSidebar()` 展开为停靠列（图标态），再次点击可收起；同一入口两态可用。
- 验收：**单次点击召回成功率 100%（连点 10 次全命中）**；唤起后侧栏完整展开、无残影。

---

## 3. D-3B-05　3B → 原生 返回入口规范

### 3.1 位置（二选一，推荐 A）

| 方案 | 放置 | 优点 | 注意 |
|---|---|---|---|
| **A（推荐）** | 顶栏右侧 actions 槽：`[data-slot="window-chrome-actions"] [data-mtx-native-back]`，置于「+ 新建」之左（或最右） | 位置固定、拇指可达、天然 `z-[60]`、与顶栏视觉统一、无需悬浮层 | 与「+」保持 ≥8px 间距防误触 |
| B（备选） | 右下角悬浮 FAB：`[data-mtx-native-back][data-mtx-float]` | 不占顶栏空间、目标大 | `z-45`（高于内容/顶栏，低于弹层 50）；避让 `env(safe-area-inset-bottom/right)` |

> 选择建议：桌面/宽屏或顶栏已较空 → A；小屏顶栏拥挤 → B。默认给 **A**。

### 3.2 样式与触控尺寸

| 项 | A（顶栏内） | B（FAB） |
|---|---|---|
| 视觉尺寸 | 高 32px，宽按内容（min 32，含 icon+文字） | 48×48 圆形 |
| 触控热区 | **44×44dp**（`::after` 透明层） | 48dp（本身满足） |
| 圆角 | 10px | 9999px |
| 配色 | 底 `currentColor 8%` 透明、文字 `--mtx-text`、hover 14% / active 22% | 底 `--mtx-primary`、白字、投影 |
| 层级 | `z-60`（顶栏 actions 槽内） | `z-45` |
| 文案 | `返回原生` / `退出到 App`（配一个"返回/外部"图标） | 仅图标 + `aria-label` |

### 3.3 桥梁（Android 侧，由 multiplatform-dev 落地）

- 3B 界面按钮点击需调用原生桥，实现"回到原生壳页面"：
  - 建议新增 `window.MotrixAndroid.exitToNative()` → Android 侧 `finish()` / 切回 `MainActivity` 原生页；
  - 或复用现有 `window.MotrixAndroid.onBack()` / `mtx:native-back` 事件语义（`docs/07 §3`、`docs/08 §9`）扩展一个"退出到原生"动作，避免另造机制。
- 按钮 DOM 由实现方插入，**必须带 `data-mtx-native-back` 钩子**（本 CSS 依此接管样式）。
- 状态保持（验收项③）：返回原生后再进 3B，登录态/下载列表/滚动位置不丢——建议 WebView 实例复用（不要 `finish()` 整个 Activity 重建 WebView），桥只做"切回原生视图"。

> 本规范只覆盖 UI（位置/样式/触控/安全区）；`finish()` 行为本身不在 CSS 范围。

---

## 4. 安全区（env）避让一览

| 位置 | 规则 |
|---|---|
| 顶栏左右 | `.window-chrome{ padding-inline-start:max(12px, env(safe-area-inset-left)); padding-inline-end:max(20px, env(safe-area-inset-right)) }` |
| 顶栏竖向 | `padding-top: env(safe-area-inset-top,0)`（安卓主题不透明状态栏通常已吃掉，多为 0，保留兜底；竖向让位以 fix18 的 44/56 为准，勿重复叠加） |
| 侧栏抽屉 | `padding-left/right/bottom: env(safe-area-inset-*)`；`max-height:100dvh` |
| FAB(B) | `right: calc(16px + env(safe-area-inset-right)); bottom: calc(16px + env(safe-area-inset-bottom))` |

---

## 5. z-index 层级总表（避免互相遮挡）

| 层 | z | 对象 |
|---|---|---|
| 内容流 | 自动(0) | 页面 / 下载状态分类栏（须 <30） |
| 顶栏条 | 30 | `.window-chrome`（fixed 覆盖，透明） |
| 侧栏遮罩 | 45 | Sheet overlay（兜底） |
| 悬浮返回 FAB | 45 | `[data-mtx-native-back][data-mtx-float]` |
| 顶栏槽内控件 | 60 | leading / actions（含召回钮、返回钮） |
| 弹层 | 50 | Modal（保持原值，勿动） |

> 注：45 的遮罩/ FAB 与 50 弹层不冲突；顶栏槽内控件 z-60 高于顶栏本体 z-30 是有意的（同层叠上下文内抬升）。

---

## 6. 落地方式

1. 将 `proposal-3b-navfix.css` 在 `/__mtx/mobile-adapt.css` **之后**追加注入（例如新增 `/__mtx/proposal-3b-navfix.css`）。
2. D-3B-04：**无需改 JS**，注入即生效（扩大既有 `[data-slot="sidebar-trigger"]` 热区 + 反馈）。
3. D-3B-05：multiplatform-dev 新增按钮 DOM（带 `data-mtx-native-back`，位置按 §3.1 A），并接 `exitToNative()` 桥；样式由本 CSS 接管。
4. DevTools 自检：
   - `document.querySelector('[data-slot="sidebar-trigger"]')` 存在；
   - 该按钮 `::after` 计算宽高 = 44px（触屏 48）；
   - 侧栏收起态下点按 → 侧栏 `data-state` 变 `expanded`（Sheet 打开）；
   - 返回按钮点击触发 `exitToNative`（或 console 打印桥调用）且回到原生页。

---

## 7. 风险与限制（如实交底）

1. **伪元素命中层**：极少数 WebView 旧版对 `::after` 命中测试支持不佳 → 退化用 `width/height:44 + margin:-8`（见 §2.1）。
2. **邻居误触**：44dp 扩区可能轻触相邻按钮，已用 `gap:8px` 缓解；若线上仍抢点，改为"竖向 44、横向 40"或按实际坐标微调。
3. **Sheet 作用域**：overlay 抽屉通常 portal 到 `body`，故 §2 用全局 `[data-slot="sidebar"][data-mobile="true"]` 而非 `.web-window-chrome` 域；若该版未 portal，规则仍命中。
4. **`color-mix()` 兼容**：老 WebView（Chrome <111）不支持 → 提供纯 `rgba()` 回退或忽略（反馈缺失不影响功能）。
5. **返回桥语义**：若 `finish()` 会销毁 WebView，则"状态保持"验收项③会失败；需改成"切视图不销毁 WebView"。
6. **与 fix18 竖向让位的关系**：本文件只管横向安全区 + 召回/返回入口，**不重复叠加**竖向 44/56，避免 76px 类问题。

---

## 8. 验收清单（对齐 docs/11）

**D-3B-04**
- [ ] 下载页、Trackers 页，侧栏隐藏态：单次点击召回成功率 100%（连点 10 次全中）。
- [ ] `[data-slot="sidebar-trigger"]` 实际热区 ≥44×44dp（DevTools 量 `::after` 尺寸）。
- [ ] 触发按钮 z 高于内容与下载筛选栏，未被 40px 顶栏或页面元素拦截。
- [ ] 唤起后侧栏完整展开（overlay 滑出）、可再次收起、无残影。

**D-3B-05**
- [ ] 3B 界面存在可见可点的返回入口（`[data-mtx-native-back]`），位置固定、文案清晰。
- [ ] 单次点击稳定回原生（成功率 100%），无白屏/卡死。
- [ ] 返回后再进 3B，登录态/下载列表/滚动位置不丢。
- [ ] 与系统返回键行为一致、不冲突。
