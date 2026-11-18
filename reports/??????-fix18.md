# 3B 顶栏修复 · fix18（采纳 ui-ux 方案 A）

产物：`dist/motrix-android-2.4.3-fix18-debug.apk`
大小：18,683,935 B　SHA-256：`56D1CB38B7E1FB21F8F8B696E4734AE317F0416BC92422B689457B8ED760D929`

## 相对 fix17 的修正（fix17 在 640–767 有回归：标题被实底顶栏盖住）
- **删除** 640–767「同一行、横向 160px」规则（实底顶栏会让这一行标题被盖）。
- **统一竖移**：`@media(max-width:767.98px)` 给 `main[data-slot="sidebar-inset"]` 垫 44px（触屏 56px）——标题整体落到实底顶栏之下，任何窄宽都可见、不重叠。
- **归零 PanelShell header 顶部内距**：`@media(max-width:767.98px){ [data-slot="panel-shell-header"]{padding-block-start:0;padding-inline:16px} }`，避免下载/设置页出现 `44+32=76px` 偏高（ui-ux §2.2）。
- 去掉 `:has`（老 WebView 更稳）；顶栏保持实底 + 1px 阴影。

## 真机实测（Redmi K60 bcfa3113，`?vw=W`，innerWidth 已验证生效）
| 视口 | 「仪表板」标题 rect | 顶栏 WC | 判定 |
|---|---|---|---|
| 640 | x8, y73 | 高 40 | 标题在顶栏下方、可见、不重叠 |
| 700 | x8, y73 | 高 40 | 同上（fix17 的遮挡回归已消除） |
| 800 | x200, y17 | 高 40 | 侧栏展开、标题在侧栏右侧同行，不压徽标 |
| 1280 | x200, y16 | 高 40 | 宽屏无回归 |

- PanelShell 页（下载/设置）探针：`[data-slot="panel-shell-header"]` 计算 `padding-block-start=0`（已归零，无双倍 76px）。
- `vw=360` 模拟只定宽不定高（视口高≈148 CSS），截图失真，不作目测验收；从 CSS 看 <768 仅给 main 垫一次 44/56，无双倍。

## 证据
`reports/fix18_700b.png`（vw700 仪表板）、`reports/fix17_700.png`/`fix17_360.png`/`fix17_1280.png`（前一轮矩阵）
