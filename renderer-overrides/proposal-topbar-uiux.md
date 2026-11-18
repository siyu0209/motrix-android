# proposal-topbar-uiux · 3B 窄屏顶栏避让方案说明

> 归属：UI/UX 美化与全端适配（角色5）｜对应缺陷 UI-002「窄屏标题与 M·O 徽标重叠（修复中）」
> 范围：纯 CSS 提案，**不改 JS/Kotlin，不改动 `assets/mobile-adapt.css` 本体**。
> 配套文件：`proposal-topbar-uiux.css`（本方案唯一交付样式）；事实依据：`docs/10-布局断点分析.md`。

---

## 0. 结论先行（TL;DR）

1. **根因不是“缺一段 padding”，而是“两轴兜底各自为政”。** 原版用「PanelShell header `pt-8`(32) + inset `m-2`(8)=40」做竖向让位、用「紧凑态 `ps-160`」做横向让位；`<640` 再覆盖成 `pt-44/56 + px-16`。**自绘标题（如 3B 仪表板 H1）脱离了 PanelShell，两轴兜底同时缺失 → 被 40px `position:fixed` 顶栏盖住。**（docs/10 §0、§4.5、§6.1）
2. **不改 DOM 的 CSS 兜底，且已定型为「方案 A：<768 统一竖向下移」。** 只给内容卡本体 `main[data-slot="sidebar-inset"]` 加 `padding-top:44px`（触屏 56px），单次生效、不叠加；`§2.2` 再把内建 `panel-shell-header` 顶部内距归零，避免「44+32」多余。**放弃 640–767 的「同行 ps-160」方案**——顶栏改全宽实底后，同行会被实底盖住标题（fix17 实测回归，见 `fix17-复核-顶栏遮挡回归.md`）。全程**不使用 `:has`**。
3. **顶栏 40px 竖轴 + 安卓状态栏安全区**（`env(safe-area-inset-top)`，为 0 时等价 40px）；断点对齐官方 **768 / 640 / pointer:coarse**，不自造 600/720。

---

## 1. 为什么现有 §20 还不够（会被本提案覆盖的地方）

`mobile-adapt.css` §20 已知写法（约 637–650 行）：
```css
@media (max-width:767.98px){
  html.mtx-mobile [class~="min-h-0"][class~="flex-1"][class~="overflow-hidden"]{
    padding-top:46px!important; box-sizing:border-box;
  }
  html.mtx-mobile .window-chrome{ background:var(--mtx-bg,#14161a); box-shadow:0 1px 0 rgba(0,0,0,.25); }
}
```

| 现有 §20 的短板 | 本提案的处理 |
|---|---|
| 固定 `padding-top:46px`，未含状态栏安全区 | 由 `main` 的 `padding-top:44/56` 承担竖向；顶栏实底高度在 §1 含 `safe-top` |
| 页面标题在**内建 PanelShell header** 内时 `46 + pt-8/44` 双倍空白 | §2.2 把 `panel-shell-header` 顶部内距归零，统一由 main 让位 |
| 自绘标题与徽标同行时横向压字 | <768 **一律竖向下移**，不再用 640–767 同行（同行会被实底盖住，见 fix17 复核） |
| 46px 对触屏偏小（官方触摸档 56px） | `pointer:coarse` 抬到 56px |
| **三属性选择器会双命中**：`<main data-slot="sidebar-inset">` 最终 class 也含 `min-h-0/flex-1/overflow-hidden`，§20 会同时给 main 与内层 div 各垫 46px（合计 ≈92px） | **不用三属性选择器**；只给 `main[data-slot="sidebar-inset"]` 单次垫 44/56，天然无双倍 |
| 与 §20 同时加载可能叠加/顺序打架 | `main[data-slot="sidebar-inset"]` 特异性更高，**无论顺序都覆盖 §20** |

---

## 2. 方案分层（每条：目标元素 / 断点 / 原因）

| 段 | 目标元素（selector 关键部分） | 断点 | 作用 |
|---|---|---|---|
| §0 | `html.mtx-mobile` | 全局 | 新增令牌 `--mtx-chrome-h` / `--mtx-chrome-top-gap` / `--mtx-chrome-leading-safe` / `--mtx-top-clear` |
| §1 | `.window-chrome` | 全部（mtx-mobile） | 条高含安全区、顶部安全区内距、左右刘海安全区、实底+阴影（避免“透视重叠”观感）；**保留 `z-index:30` 不改** |
| §2 | `main[data-slot="sidebar-inset"]` | `≤767.98`（触屏 56） | **核心**：内容卡整体下移 44px（触屏 56px），单次生效、无双倍 |
| §2.2 | `.web-window-chrome [data-slot="panel-shell-header"]` | `≤767.98` | 归零 header 顶部内距 + 横向 16px，消除「44+32」多余、统一标题顶线 |
| §2.3 | `[data-slot="sidebar-inset"] h1:first-child / h2:first-child` | `≤767.98` | 去掉标题自带 `margin-top`，让自绘头与 PanelShell 顶线一致 |
| §3 | `main[data-slot="sidebar-inset"]` | 横屏 & 高≤420 | 让位压到 42px，保首屏 |
| §5 | `.web-window-chrome [data-mtx-topbar-avoid]` | `≤767.98` | 少数必须与顶栏同行的控件，左内距 ≥160px 兜底 |

> 选择器细节：全部用 `[class~="..."]` 精确匹配单个 Tailwind 类（避免 `[class*="flex-1"]` 误伤 `flex-10` 之类）；语义锚点用 `[data-slot="..."]`；**不使用 `:has`**。

---

## 3. 尺寸 / 断点速查（取自 docs/10，落地以 160 为准）

| 量 | 值 | 备注 |
|---|---|---|
| 顶栏高（web） | **40px** | fixed / top:0 / z-index:30 / transparent |
| 顶栏左右内距（web） | 12 / 20px | 本方案在刘海横屏时取 `max(…, safe-left/right)` |
| m·o 徽标点击区 | 宽 72（glyph 44×10），x≈12..84 | 左侧控件簇合计 ≈152 → 官方取整 **160** |
| 侧栏断点 | **768px = 48rem** | `<768` 变 Sheet，`data-state=collapsed` |
| 内容卡外边距 | **8px**（`m-2`） | 让位计算需 +8 |
| 满血 header 上距 | 32px（`pt-8`） | 32+8=40，正好越过顶栏 |
| `<640` header 上距 | 44px（触屏 56） | 本方案用 `--mtx-top-clear` 统一承担 |
| 侧栏宽 | 200 / 288(移动) / 48(icon) | — |

---

## 4. 生效方式（注入顺序 · 必须）

1. **加载顺序**：`proposal-topbar-uiux.css` 必须在 `mobile-adapt.css` **之后**注入。推荐 `BridgeServer.injectMobile()` 在注入 `mobile-adapt.css` 之后追加一行：
   `<link rel="stylesheet" href="/__mtx/proposal-topbar-uiux.css">`（放到 `mobile-adapt.css` 后面即可）。
2. **作用域**：仅作用于 `html.mtx-mobile`（`mobile-adapt.js` 加的根开关）；未启用移动层时本文件对原站零影响。
3. **验证优先级**：真机/浏览器 DevTools 依次看
   - `.window-chrome` 计算高度 = `40 + env(safe-area-inset-top)`；
   - 内容容器 `padding-top` = `40+safe+gap`（窄屏）；
   - 仪表板标题 `getBoundingClientRect().top ≥ 40`；
   - 切换 360 / 640 / 800 / 1280 宽度，标题始终在顶栏下沿之下且不与徽标相交。

---

## 5. 风险与取舍（提前交底）

1. **内建 header 顶部内距被归零**（§2.2）：只影响 `<768`；`≥768` 桌面原始表现不变。若业务希望 640–767 仍保留官方“横向 ps-160 同排”方案，可把 §2.2 的 `padding-inline:16px` 拆成只在 `<640` 生效——但那样 640–767 的自绘标题仍需依赖 §2.1 竖向让位，二者任选其一，本提案默认“统一竖向下移”。
2. **`height` / `padding` 覆盖内联样式**（§1）：顶栏高度由 React 内联 `height` 写入，故用 `!important` 覆盖内联普通声明（合法，且 `env=0` 时等于原值）。若后续有人给顶栏加过渡动画，需留意 `height` 的过渡。
3. **内容卡圆角观感**：`SidebarInset` 顶部圆角（`rounded-xl`）在实底顶栏下不可见，视觉上卡片像从 40px 处开始。属观感取舍，换来的是“标题绝不压字”；如需要可再给卡片顶部加 `border-top-left/right-radius` 微调。
4. **`backdrop-filter` / `max()` 兼容**：老 WebView 若忽略 `backdrop-filter`，实底仍在（只是没有毛玻璃），不影响让位；`max()` 自 Chrome 79 起支持，安卓 WebView 基本满足。
5. **`fixed` 自绘头**：若某页整页 `position:fixed` 自绘头、根本不吃容器 padding，§2.1 管不到，靠 §5 的 `[data-mtx-topbar-avoid]` 兜底；这类页仍建议回到 §6 的 DOM 最优解。
6. **不做的事**：不改 `z-index`、不改断点常量、不动 `mobile-adapt.css` 本体、不动任何 JS/Kotlin、不隐藏任何功能入口。

---

## 6. 更彻底的解（供 Lead 决策，超出纯 CSS 范围）

- **首选**：把页面标题纳入 `PanelShell`，或至少给自绘头容器加 `data-slot="panel-shell-header"` 并保证处在 `.web-window-chrome` 作用域内 → 官方两轴兜底自动生效，本 CSS 可退化为“只补安全区”。
- 这需要改渲染层组件（DOM），按纪律须 Lead 与用户确认后再动；本提案先以纯 CSS 保证“绝不压字”。

---

## 7. 真机验收清单（对齐 reports/3B-UI缺陷清单 · UI-002 / UI-003）

| 检查项 | 360 | 640 | 800 | 1280 |
|---|---|---|---|---|
| 仪表板标题不与 M·O 徽标重叠、不被顶栏压字（UI-002） | ☐ | ☐ | ☐ | ☐ |
| 下载页左上筛选“全部/进行中/已完成/错误”完整可见可点（UI-003） | ☐ | ☐ | ☐ | ☐ |
| 顶部内容不被系统状态栏遮挡（安全区生效） | ☐ | ☐ | ☐ | ☐ |
| 竖/横屏切换后顶栏与标题关系正确 | ☐ | ☐ | ☐ | ☐ |
| 暗色模式下顶栏实底与文字对比足够 | ☐ | ☐ | ☐ | ☐ |
| 顶栏内按钮（切换侧栏/+）点击热区 ≥44dp、未被遮挡 | ☐ | ☐ | ☐ | ☐ |
