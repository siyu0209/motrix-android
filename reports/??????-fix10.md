# 状态栏遮挡全适配（edge-to-edge + 系统栏/刘海 insets）· fix10

日期：2026-11-18　设备实测：Redmi K60（Android 14，横/竖屏）
产物：`dist/motrix-android-2.4.3-fix10-debug.apk`
SHA-256：`523AA69EC8FD980312AD2DD7089164880744308CBA274A6C9AD484E333290FFA`

## 为什么之前"没全适配"
不同手机/系统状态栏高度不同；**Android 15 起系统强制 App 全面屏（edge-to-edge）**，内容会画到状态栏底下。只按某台机器调间距，换机就会压住按钮。

## 这次的通用解法（对所有机型一致生效）
- 两个界面（原生 `MainActivity`、3B `MotrixWebActivity`）都显式：
  - `WindowCompat.setDecorFitsSystemWindows(window, false)` —— 统一按全面屏绘制；
  - `ViewCompat.setOnApplyWindowInsetsListener(...)` 用**系统真实 insets**（`systemBars + displayCutout`）给内容做上/左/右/下内缩 —— 状态栏、挖孔、刘海多高就缩多少，不再写死像素；
  - `isAppearanceLightStatusBars/NavigationBars = false` —— 深色背景下状态栏图标为白色，清晰可见。
- 附带：极矮横屏（≤480dp 高）设置卡再压紧，8 个分类两行稳进首屏。

## 真机逐屏验证（截图见 reports/）
| 界面 | 结果 |
|---|---|
| 原生首页 `fix10_native.png` | 状态栏与标题栏无遮挡、间距自然 |
| 3B 仪表板 `fix10_3b.png` | OCR 实测：状态栏时间 y≈32px，「仪表板」标题 y≈177px（状态栏高 90px），无重叠 |
| 3B 设置页 `fix10_settings.png` | 8 分类齐全（通用/外观/下载/BT/集成/网络/高级/关于）、3 列、无裁切 |
| 3B 设置·通用弹窗 `fix10_general2.png` | 弹窗标题「通用」y≈177px，在状态栏下方；通知开关/未读角标/取消齐全、无红条 |
| 原生 新建下载弹窗 `fix10_add.png` | 标题「新建下载」在状态栏下方，链接输入/种子/取消下载完整 |

## 说明
- 以上为 K60（Android 14）实测；因已改用"读系统真实 insets"而非写死，Android 15 / 刘海屏 / 平板 同样生效。
- 仍需你在另一部手机上装 fix10 复核一次；若有厂商特殊（如状态栏常驻更高），把截图发我即再校。
