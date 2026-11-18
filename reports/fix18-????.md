# fix18 顶栏避让 · UI/UX 终审复核结论

- 复核人：角色5（UI/UX 全端适配工程师）
- 构建：`dist/motrix-android-2.4.3-fix18-debug.apk`，SHA-256 `56D1CB38B7E1FB21F8F8B696E4734AE317F0416BC92422B689457B8ED760D929`，18,683,935 B
- 实现口径：`@media(max-width:767.98px)` 给 `main[data-slot="sidebar-inset"]` 单次 `padding-top:44px`（`pointer:coarse` 56px）；`[data-slot="panel-shell-header"]` 归零 `padding-block-start`；去 `:has`；顶栏实底 + 1px 阴影，高 40、z-index 30 不变。

## 总结论：**通过**（UI-002「窄屏标题与 M·O 徽标重叠」修复成立，fix17 的 640–767 遮挡回归已消除，未引入新回归）

## 逐宽度判定

| 视口 | 证据来源 | 「仪表板」标题 rect / 位置 | 顶栏高 | 判定 |
|---|---|---|---|---|
| 360 | 无有效截图（`?vw=360` 只定宽不定高，视口高≈148 CSS，画面失真） | 由 CSS 推：<768 仅 `main` 单次 44/56，无三重属性双命中 | — | 逻辑通过，待真机竖屏补一张图 |
| 640 | Lead 真机实测 | x8, y73（顶栏下方，可见） | 40 | 通过 |
| 700 | `fix18_700b.png` OCR：仪表板@(171,340)；`fix18_700.png` OCR：仪表板@(119,340)（≈ y73 CSS） | 顶栏下方、可见、不重叠 | 40 | 通过（**fix17 遮挡回归已消除**） |
| 800 | Lead 真机实测 | x200, y17（侧栏展开，侧栏右侧同行，不压徽标） | 40 | 通过 |
| 1280 | `fix17_1280.png` OCR：仪表板@(129,211) + Lead 实测 x200,y16 | 宽屏无回归 | 40 | 通过 |

> 像素换算：截图 2400×1080，dpr≈3.43；标题物理 y340 ≈ 视口内 99 CSS，扣状态栏后 ≈73 CSS（= 44 padding + 标题自身行距），换算与 Lead 的 y73 一致，说明 44px 已真实生效且足以把标题落到 40px 实底顶栏之下。

## 76px（PanelShell 页顶距）核对
- Lead 探针 `[data-slot="panel-shell-header"]` 计算 `padding-block-start=0`（归零生效）；
- 叠加 `main` 的 44px → 下载/设置等 PanelShell 页顶距 = 44，**不是 76px**（§2.2 补丁已带）。

## 92px 双命中核对
- 已消除：`<768` 只对 `main[data-slot="sidebar-inset"]` 垫一次，不再用 `[class~="min-h-0"][class~="flex-1"][class~="overflow-hidden"]` 三属性选择器同时命中 `main` 与内层 div，无双倍内距。

## 实现核对（与 proposal 定稿一致）
- 顶栏：height 40、`background: var(--mtx-bg)` 实底、1px 阴影、`z-index:30` 均未改动本体；
- `:has` 已去除（老 WebView 更稳）；
- 断点严格 767.98 / pointer:coarse，未触碰 768 侧栏断点逻辑与其它核心操作路径。

## 建议
1) **接受 fix18**，UI-002 可结案；多宽度抽查无回归。
2) UI-003（下载页筛选被顶栏遮挡）需一张 **3B React 面板** `#/downloads` 顶部截图再确认——我方用 adb 触发侧栏「下载」会落到 App 的**原生竖屏下载页**（非 3B React 面板），未取到干净面板图，建议在 3B React 测试台（横屏 + `?vw=W`）补拍。
3) 若后续要彻底消除自绘标题的"补丁依赖"，最稳解仍是标题走 `PanelShell`（`data-slot="panel-shell-header"`），属 DOM 改动，需 Lead 与用户确认后再排期。

## 证据文件
- `reports/fix18_700b.png`（vw700 仪表板，OCR 仪表板@(171,340)）
- `reports/fix18_700.png`（vw700 仪表板，OCR 仪表板@(119,340)）
- `reports/fix17_1280.png`（宽屏，OCR 仪表板@(129,211)）
- `reports/顶栏重叠修复-fix18.md`（Lead 构建与实测记录）
