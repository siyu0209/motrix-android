# Motrix Android 移动端适配层 · 使用说明（renderer-overrides/README.md）

本目录是给 Motrix Web 渲染层（`dist/renderer-web`）**叠加**的一层移动端适配：
**不改原业务逻辑、不动 React 代码、不臆造内部 class**，只叠加样式 + 运行时增强。
未启用时对原站零影响；启用后所有规则挂在根开关 `html.mtx-mobile` 下，可整体或分项关闭。

| 文件 | 作用 |
|---|---|
| `mobile-adapt.css` | 移动端适配样式（安全区 / 44px 触控 / 字号间距 / 窄屏单列 / 弹窗全屏 / 输入框防缩放 / 底部避让 / 横屏 / 暗色变量） |
| `mobile-adapt.js` | 运行时增强（viewport 注入、根 class、软键盘监听、返回键、DOM 小补丁），纯原生、幂等、无依赖 |
| `screens.md` | 各主要页面在手机上的适配要点与坑 |
| `README.md` | 本文件 |

> ⚠️ **需按实际 DOM 微调**：Motrix 是 React + Tailwind 构建，class 名可能随版本变化。
> 本层刻意只依赖「标准元素 / ARIA 角色 / Tailwind 通用工具类（如 `grid-cols-*`）/ 我们自定义的 `data-mtx-*` 钩子」。
> 若某处结构与假设不符（如弹窗不是 `role=dialog`、列表不是网格），在 `mobile-adapt.css` 末尾追加覆盖，
> 或在对应容器上补 `data-mtx-*` 钩子即可，**不需要改 JS 内核**。

---

## 一、注入方式（任选其一）

### 方式 A：构建时写进 index.html（推荐，最省事）
构建脚本把 `dist/renderer-web` 拷进 `android/app/src/main/assets/renderer-web` 后，
在该目录 `index.html` 的 `</head>` 前引 CSS、`</body>` 前引 JS（用相对路径）：

```html
<!-- index.html </head> 之前 -->
<link rel="stylesheet" href="./renderer-overrides/mobile-adapt.css">

<!-- index.html </body> 之前 -->
<script src="./renderer-overrides/mobile-adapt.js" defer></script>
```

同时确保 `renderer-overrides/` 两个文件被一起拷进 `assets/renderer-web/renderer-overrides/`。
（也可用 Android `WebViewAssetLoader` 把本地 assets 映射成 `https://appassets.androidplatform.net/...` 再引。）

### 方式 B：Android 端 onPageFinished 注入（不碰构建产物）
在 `WebViewClient.onPageFinished` 里按需注入，适合想保持 `dist` 原样：

```java
// 注入 CSS
webView.evaluateJavascript(
  "(function(){var l=document.createElement('link');" +
  "l.rel='stylesheet';l.href='https://appassets.androidplatform.net/renderer-overrides/mobile-adapt.css';" +
  "document.head.appendChild(l);})();", null);

// 注入 JS（读取 assets 文本后注入，或同样用 URL 动态 import）
webView.evaluateJavascript(jsText, null);
```

`evaluateJavascript` 对同一页面重复调用是安全的——本 JS 内部有幂等保护（`window.__MTX_MOBILE_ADAPT__.installed`），
重复注入只会安装一次。

### 方式 C：运行时配置（可选）
注入前可先塞配置覆盖默认行为：

```js
window.MTX_MOBILE_CONFIG = {
  injectViewport: true,   // 注入/校正 viewport meta（默认 true）
  backGuard: true,        // 接管返回键（默认 true）
  addClasses: true,       // 加 mtx-mobile 等根 class（默认 true）
  keyboard: true,         // 软键盘监听（默认 true）
  narrowBreakpoint: 600,  // 窄屏阈值，与 CSS 对齐
  keyboardThreshold: 120  // 判定键盘弹起的高度差
};
```

运行时也可调用：`window.__MTX_MOBILE_ADAPT__.enable() / disable() / setTheme('dark'|'light')`。

---

## 二、与 Android 宿主 / 业务页的约定（可选，但建议）

### 1) 返回键
`mobile-adapt.js` 用 history 哨兵接管返回键，优先级：
**关闭打开的弹窗 → 派发 `mtx:back`（页面可 `preventDefault()` 接管）→ 调 `MotrixAndroid.onBack()` → 都不处理才允许退出**。

- 业务页想自己处理返回（如返回上级列表），监听即可：
  ```js
  document.addEventListener('mtx:back', function (e) {
    // 处理了当前层返回，就阻止默认
    if (goBackOneLevel()) e.preventDefault();
  });
  ```
- 宿主侧建议暴露：`window.MotrixAndroid.onBack()`（无页面接管时回调）；如需硬退出再加 `exitApp()`。

### 2) 关闭弹窗
如项目弹窗不是原生 `<dialog>`，最好是给弹窗根节点加 `data-mtx-dialog`，并给关闭按钮加 `data-mtx-close`，
JS 的返回键关闭逻辑会自动点它（不破坏 React 状态）。

### 3) 常用钩子清单（加到对应 DOM 上即可获得对应适配）

| 钩子 | 加在 | 效果 |
|---|---|---|
| `data-mtx-app-shell` | 应用最外层壳 | 顶部留安全区 |
| `data-mtx-scroll` / `.mtx-scroll` | 主滚动容器 | 惯性滚动 + 底部避让手势条/底栏 |
| `data-mtx-bottom-bar` / `.mtx-bottom-bar` | 底部导航 | JS 测量高度写入 `--mtx-bottom-bar` |
| `data-mtx-fab` / `.mtx-fab` | 悬浮按钮 | 自动避让手势条与底栏 |
| `data-mtx-dialog` / `.mtx-dialog` | 弹窗根 | 窄屏全屏化 + 返回键可关 |
| `data-mtx-toast` / `.mtx-toast` | 贴底提示 | 抬高到安全区之上 |
| `data-mtx-reflow="single"` | 需要单列的多列容器 | 窄屏强制单列 |
| `data-mtx-icon-btn` | 纯图标按钮 | 强制 44×44 点击区 |
| `data-mtx-noselect` | 列表行/卡片 | 禁长按选中（防误触） |
| `data-mtx-selectable` | 磁力链接/密钥等 | 放开长按复制 |
| `data-mtx-input-bar` | 贴底输入栏 | 键盘弹起时抬到键盘之上 |

---

## 三、手机端视觉规范

### 1. 色彩（CSS 变量，已内置浅/暗两套，见 `mobile-adapt.css` §0）
| 令牌 | 浅色 | 暗色 | 用途 |
|---|---|---|---|
| `--mtx-bg` | `#f5f6f8` | `#14161a` | 页面底色 |
| `--mtx-surface` | `#ffffff` | `#1d2026` | 卡片/弹窗/底栏 |
| `--mtx-surface-2` | `#eef0f4` | `#262a32` | 次级填充 |
| `--mtx-text` | `#1c2024` | `#e7e9ee` | 主文字 |
| `--mtx-text-dim` | `#6b7280` | `#9aa3af` | 次要文字 |
| `--mtx-border` | `#e3e6eb` | `#2c313a` | 分隔线/描边 |
| `--mtx-primary` | `#3b82f6` | `#4f8cff` | 主色（进度/强调/链接） |
| `--mtx-danger` / `--mtx-success` | `#ef4444` / `#10b981` | 同 | 状态色 |

> 本层**不会**强行改 body 背景色。若希望统一换肤，用原生变量接入：
> 让应用把自身主色/背景映射到 `--mtx-*`，或用 `.mtx-dark` 强制暗色。

### 2. 字号梯度（根字号 16px，正文 15px）
`xs 12` · `sm 13` · `md 15` · `lg 17` · `xl 20` · `2xl 24`（px 基准，`--mtx-font-*` 可调）。
行高正文 1.5，标题 1.25–1.4。**输入框统一 ≥16px 防聚焦缩放**。

### 3. 触控
- 最小点击目标 **≥44×44px**（按钮抬 `min-height`，图标按钮 44×44）。
- 去 `300ms` 延迟（`touch-action: manipulation`）、去安卓点击灰块。
- 可点元素间距 ≥8px，防误触相邻按钮。

### 4. 间距 / 圆角
基准间距 `--mtx-gap: clamp(8px,2.6vw,16px)`；卡片圆角 12px，小控件 8px。

### 5. 暗色
跟随系统 `prefers-color-scheme`；也可 `html.mtx-dark` / `html.mtx-light` 手动强制（`setTheme()`）。

### 6. 动效
`fast 120ms` · 默认 `200ms` · `cubic-bezier(.2,.8,.2,1)`；`prefers-reduced-motion` 下自动几乎关闭。

### 7. 安全区
统一用 `env(safe-area-inset-*)`（带 `0px` 兜底），顶栏让状态栏、底部让手势条，横屏让左右挖孔。

---

## 四、TV / 平板预留说明

本层当前聚焦**手机竖屏（主）+ 部分横屏**。TV 与平板的**大屏远距离规范不做照搬**，后续单列实现：

- **平板（≥600dp / 宽 ≥720px）**：走多列布局，不是放大版手机。
  建议在 CSS 增加 `@media (min-width:720px)`：任务列表两列、设置页左右分栏（导航 + 内容）、弹窗恢复居中卡片（用 `html.mtx-no-fullscreen-dialog` 或媒体查询解除全屏）。
- **TV（10-foot UI，3 米外可视）**：需独立规范——
  焦点高亮/放大态（`transform: scale(1.08)` + 明显描边）、**最小点到 60–72dp 触控/焦点框**、正文 ≥24px、**16:9 横屏单列或双列栅格**、D-pad 上下左右可遍历、**防烧屏**（logo/进度条做每 N 分钟 2–3px 漂移、避免长时间静态高对比静止画面）。
  **禁止**直接把手机竖屏或 PC 触控布局搬到 TV。上述内容由后续 TV 专项补齐，本层先预留 `--mtx-*` 令牌与 `mtx-mobile` 开关，避免返工。

---

## 五、可开关清单（排查问题时的逃生阀）

| 开关（加在 `<html>` class） | 作用 |
|---|---|
| `mtx-mobile` | 总开关（JS 自动加；去掉即完全恢复原站） |
| `mtx-no-reflow` | 关闭窄屏单列重排 |
| `mtx-no-fullscreen-dialog` | 关闭弹窗全屏化（保留居中卡片） |
| `mtx-no-motion` | 关闭 reduced-motion 强制降级 |
| `mtx-dark` / `mtx-light` | 手动锁定暗/浅色 |

---

## 六、验证清单（交付前自测）
1. 竖屏 360×640 / 390×844 / 412×915：无横向滚动条，输入框聚焦不放大。
2. 刘海机型（`viewport-fit=cover`）：顶栏不被状态栏遮、底部不被手势条遮。
3. 打开任一模态：全屏铺满、可滚动、返回键先关弹窗而不是退出。
4. 弹出软键盘：输入框滚到可视区、贴底输入条抬到键盘之上、FAB 隐藏。
5. 横屏：顶栏压缩、左右安全区生效、内容不被挖孔遮挡。
6. 暗色系统：文字对比度正常（正文与底色对比 ≥ 4.5:1）。
7. 重复注入 2 次以上：无报错、无重复监听（幂等）。
