# Motrix 手机端逐页适配要点（screens.md）

> 适用：Motrix Web 渲染层（React19 + Tailwind4）在安卓 WebView 手机端（竖屏为主，部分横屏）。
> 原则：**本层只叠样式 / 补钩子，不改业务逻辑与功能入口**；凡是需要改 DOM 结构的地方都标「⚠️ 结构项，需与 Lead 协调」。
> 选择器一律用「标准元素 / ARIA / Tailwind 通用工具类 / `data-mtx-*` 钩子」，不写死 Motrix 内部 class。

---

## 0. 全局外壳（侧边栏 + 内容区 + 顶部状态）

**适配要点**
- 桌面是「左侧固定侧边栏 + 右侧内容」；手机窄屏应转为**底部 Tab 或抽屉式侧栏**，内容区独占整宽。
- 顶部工具栏要吃 `safe-area-inset-top`（状态栏下移），底部要吃 `safe-area-inset-bottom`（手势条）。
- 主滚动容器加惯性滚动与底部避让（避免最后一条被手势条/底栏盖住）。

**坑**
- 侧边栏若用「固定定位 + 宽度写死」，窄屏会横溢 → 需媒体查询改宽度或转抽屉。**⚠️ 结构项**：真转抽屉要改 DOM（把侧栏移到抽屉容器），本层只能先用 CSS 折叠（如 `transform: translateX(-100%)` 隐藏 + 汉堡按钮唤出，按钮触发逻辑需业务配合）。
- 若内容区高度用 `100vh`，软键盘弹起会顶出 → 本层已注入 `--mtx-vh`，建议改用 `height: var(--mtx-vh)` 而非裸 `100vh`。

**落地手段**
- 外壳：`data-mtx-app-shell`（顶安全区）；滚动区：`data-mtx-scroll` / `.mtx-scroll`；底栏：`data-mtx-bottom-bar`；汉堡/FAB：`data-mtx-icon-btn` / `data-mtx-fab`。

---

## 1. 任务列表（全部 / 进行中 / 已完成 / 已停止）

**适配要点**
- 任务行在手机上应**单列纵向**：文件名一行（超长省略号）、进度条一行、元信息（速度/剩余/大小）小字铺开、操作按钮收进「更多」或右滑动。
- 分类切换（全部/进行中…）在手机上横向可滑动的 Tab 条，不要挤成两行。
- 行高 ≥ 56–64px，保证点按与滑动手势；行内操作图标 44×44 点击区。

**坑**
- 桌面用 `grid-cols-2/3` 铺任务卡片的，窄屏必须折叠为单列（本层已对 `grid-cols-*` 生效，若仍不生效说明不是网格 → 加 `data-mtx-reflow="single"`）。
- 任务名超长不截断会撑破行 → 给名称容器 `min-w-0 + overflow-hidden + text-ellipsis`（Tailwind：`min-w-0 truncate`）。本层对文本容器不强制，需要按实际加。
- 进度条若是绝对定位的细条，注意别被圆角/裁切吃掉。
- 列表项常被长按误选文本 → 给行加 `data-mtx-noselect`；但文件名若想可复制则改用 `data-mtx-selectable`。

**落地手段**
- 行操作：`data-mtx-row-action`（44×44 不撑排版）；卡片：`data-mtx-card`；单列：`data-mtx-reflow="single"`；防误选：`data-mtx-noselect`。

---

## 2. 新建任务（弹窗/表单）

**适配要点**
- 弹窗在手机上**全屏铺满**（本层对 `role=dialog`/`dialog`/`data-mtx-dialog` 已做窄屏全屏化），顶部标题栏固定、内容可滚动、底部「开始下载」按钮贴底且避让键盘。
- URL 输入框多行时自动撑高；文件名/保存路径等字段单列占满行（`data-mtx-field`）。
- 高级选项（请求头、代理、限速、校验等）默认折叠，避免首屏过长。

**坑**
- iOS/安卓聚焦输入框会缩放页面 → 输入框必须 ≥16px（本层已强制），但若某输入被内联样式压回 14px 会破功，需排查内联 `font-size`。
- 底部操作条在软键盘弹起时会被遮 → 用 `data-mtx-input-bar`，本层会在 `.mtx-keyboard-open` 时把它抬到键盘之上。
- 「保存路径」在安卓走 SAF，点选行为由宿主桥负责，样式层不干预；但按钮要有明确按下反馈。
- 多行 textarea 别用 `rows` 固定小高度导致只显示一行 → 允许自适应或给最小高度。

**落地手段**
- 表单字段：`data-mtx-field`；底部操作条：`data-mtx-input-bar`；弹窗：`data-mtx-dialog`（+ 关闭按钮 `data-mtx-close`）。

---

## 3. 任务详情 / 进度详情

**适配要点**
- 详情可能是抽屉或弹窗：手机上直接全屏页，返回键先关闭详情再退列表（本层返回键逻辑已支持「先关弹窗」）。
- 进度、速度曲线、分块图等在窄屏竖排堆叠，图表宽度自适应（`width:100%`，别写死 px）。
- 关键指标（下载/上传速度、剩余时间）用大字号突出，细节用小字。

**坑**
- 图表库（如按 `canvas` 渲染）在新容器宽度变化后不会自动重绘 → 需要在 `resize`/`orientationchange` 后触发一次图表 `resize()`（业务侧或加个轻量 `MutationObserver`，本层不擅自调库）。
- 抽屉宽度写死（如 480px）在手机上超出屏宽 → 窄屏改 `100%`。

**落地手段**
- 详情容器：`data-mtx-scroll`；若是弹窗：`data-mtx-dialog`；返回处理：监听 `mtx:back` 关详情并 `preventDefault()`。

---

## 4. 设置（Preferences，多标签 + 密集表单）

**适配要点**
- 桌面常用「左侧 tab 列表 + 右侧表单」；手机改为**顶部横向可滑 tab** 或「分组折叠」，一次只显示一组的表单。
- 表单控件单列满宽：开关行（左文字右开关）、下拉、输入、滑块（限速）逐行堆叠，行高 ≥48px。
- 滑块/数值输入这类精确控件要**加宽滑块轨道**、数值框 16px 防缩放；限速单位说明放小字。

**坑**
- 设置项极多，长表单滚动性能：避免每个开关都触发重排；滚动容器 `overscroll-behavior: contain` 防穿透（本层对 `.mtx-scroll` 已设）。
- 分组 tab 若用绝对定位的下划线动画，窄屏换行会错位 → 改横向滚动容器 + 单个 tab 不换行（`white-space: nowrap`）。
- 下拉若用原生 `<select>`，安卓弹系统选择器没问题；但自绘下拉面板可能在窄屏溢出 → 限制最大高度 + 内部滚动。

**落地手段**
- 表单区滚动：`data-mtx-scroll`；tab 切换用 `mtx:back` 不适用（不是层级），仅样式；输入：本层已强制 16px。

---

## 5. BT 任务 / Tracker / Peer

**适配要点**
- Tracker / Peer 常是**宽表格**（多列：地址、状态、上传、下载…）。手机上表格要转「卡片列表」或横向滚动，二选一：
  - 首选卡片式：每个 peer/tracker 一张小卡，字段纵向排列。
  - 次选横滚：`overflow-x:auto` + `-webkit-overflow-scrolling:touch`，首列冻结（`position:sticky;left:0`）。
- 种子信息（做种/下载中/连接数）用徽章小标签，颜色区分状态（用 `--mtx-primary/success/danger`）。

**坑**
- 宽表格若不处理，移动端直接横向溢出整页 → 必须给表格外层一个横滚容器（`data-mtx-scroll` 或专用 wrapper），并限制 `max-width:100%`。
- 地址/磁力链很长会撑破 → `word-break: break-all` + 卡片内换行；这类文本建议 `data-mtx-selectable` 便于长按复制。
- 实时刷新的 peer 列表频繁重绘，避免给每行加重阴影/模糊，低端机掉帧。

**落地手段**
- 横滚容器：`data-mtx-scroll`（注意：它是通用滚动标记，表格 wrapper 也可用）；卡片：`data-mtx-card`；可复制：`data-mtx-selectable`。

---

## 6. 统计（Statistics，图表页）

**适配要点**
- 曲线/柱状图在手机上**单列堆叠、宽度自适应**；图例可折叠或缩小。
- 大数字指标（累计下载/上传、今日流量）用大字号卡片突出，一屏能看全。
- 时间范围切换（今日/本周/…）做成横向可滑的 chip。

**坑**
- 图表 canvas/svg 定宽不随容器缩放 → 需容器 `width:100%` 且图表库支持响应式，或 resize 后手动重绘。
- 深色背景下网格线/文字对比不足 → 用 `--mtx-text-dim` 类中性色，别用纯黑网格线。
- 图表较多时首屏加载慢，给骨架/加载态（loading 占位），否则空白像卡死。

**落地手段**
- 图表所在滚动区：`data-mtx-scroll`；指标卡：`data-mtx-card`。

---

## 7. 通知 / Toast / 全局提示

**适配要点**
- 贴底 Toast 要避让手势条与底栏（本层 `data-mtx-toast` 已抬高）。
- 安卓系统通知由宿主 `MotrixAndroid.notify()` 负责，Web 内 Toast 只做即时反馈。
- 成功/失败用颜色 + 图标双编码（不只有颜色，照顾色弱）。

**坑**
- Toast 若 `position:fixed; bottom:0` 会被手势条盖住 → 用 `data-mtx-toast`。
- 多条 Toast 堆叠在窄屏可能盖住操作按钮 → 限制同屏最多 1–2 条，或顶部展示。

**落地手段**
- `data-mtx-toast`；`data-mtx-fab` 在键盘弹起时会自动隐藏避免遮挡。

---

## 8. 空状态 / 搜索 / 加载态

**适配要点**
- 空状态插图 + 一句引导（如「点右下角 + 新建任务」），插图宽度 ≤60% 屏宽，垂直居中。
- 搜索框吸顶、16px 防缩放、右侧清空按钮 44×44。
- 加载用骨架屏（skeleton）优于空白转圈，尤其任务列表首屏。

**坑**
- 空状态若用固定 `margin-top` 在大屏/小屏位置漂移 → 用 flex 居中。
- 搜索无结果与「真没任务」要区分文案，否则用户以为出错。

**落地手段**
- 空状态容器居中靠业务 flex；FAB 用 `data-mtx-fab` 指向「新建」。

---

## 9. 关于 / 检查更新

**适配要点**
- 版本号、开源许可、检查更新按钮单列；「检查更新」是网页按钮，真实更新走宿主（APK 安装包），需明确提示「将跳转安装」。
- 版权/品牌信息区（名称、图标、启动页）由 Android 资源层负责，Web 内「关于」页展示一致文案即可。

**坑**
- 桌面「检查更新」走 Electron autoUpdater，安卓没有 → 需宿主桥替代或隐藏该入口（**⚠️ 结构/逻辑项，与 Lead 确认**，避免点了无反应）。
- 外链（GitHub 等）在 WebView 里点击需 `shouldOverrideUrlLoading` 走系统浏览器，否则在 WebView 内打不开。

**落地手段**
- 纯展示页，滚动区 `data-mtx-scroll`；按钮 `data-mtx-icon-btn`/普通按钮已保证 44px。

---

## 10. 通用横屏场景（详情 / BT 表格 / 统计）

**适配要点**
- 横屏（尤其高度 ≤560px）把顶部工具栏压到 48px，左右吃 `safe-area-inset-left/right`（挖孔）。
- 横屏下 BT 宽表格更适合直接展示，可放开为横向滚动而非强制单列。
- 键盘弹起 + 横屏是极端场景：内容区会很矮，确保输入框仍可滚到可见位置（本层 focus 滚入逻辑已覆盖）。

**坑**
- 横屏 `100vh` 变化剧烈，安卓地址栏/键盘导致抖动 → 一律用 `--mtx-vh`。
- 横屏全屏弹窗若不留左右安全区会被挖孔相机区遮挡 → 本层横屏媒体查询已补 padding。

---

## 附：页面 × 钩子 速查

| 页面 | 关键钩子 |
|---|---|
| 外壳/侧栏 | `data-mtx-app-shell`、`data-mtx-icon-btn` |
| 任务列表 | `data-mtx-reflow="single"`、`data-mtx-card`、`data-mtx-noselect`、`data-mtx-row-action` |
| 新建任务 | `data-mtx-dialog`、`data-mtx-field`、`data-mtx-input-bar`、`data-mtx-close` |
| 任务详情 | `data-mtx-dialog`、`data-mtx-scroll` + 监听 `mtx:back` |
| 设置 | `data-mtx-scroll`、`data-mtx-field` |
| BT 表格 | `data-mtx-scroll`（横滚）、`data-mtx-card`、`data-mtx-selectable` |
| 统计 | `data-mtx-scroll`、`data-mtx-card` |
| 通知 | `data-mtx-toast`、`data-mtx-fab` |
