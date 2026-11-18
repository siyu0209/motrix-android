# 状态栏被遮（Android 15/16）修复 · fix14

日期：2026-11-18　产物：`dist/motrix-android-2.4.3-fix14-debug.apk`
size 18,683,123 B　SHA-256：`299638E6DC27E5FAE18B367DEA24D24DBC8D57A18A9E835D4C75DEC97C36A9A9`

## 现象
用户反馈（**另一部手机**）：顶部系统状态栏会遮挡按钮 / 下载页"下载中的状态栏被遮蔽"。

## 定位
- 本机（Redmi K60，**Android 14**，横屏 880×361 CSS）**无法复现**：系统状态栏 / 页面工具栏 / 表头三层干净分离，无重叠、无文字被遮（`dl2.png`、`dl3_scrolled.png`、`fix14_dl.png` 均正常）。
- 根因判断：**Android 15 / Android 16 对 `targetSdk ≥ 35` 的应用强制 edge-to-edge（内容绘制到状态栏/导航栏后方）**。我们的包原来 `targetSdk = 35`，在新系统上内容会顶到状态栏下面 → 顶部按钮被状态栏遮住、下载页顶部被盖。Android 14 不强制，所以本机看不出。

## 修复
1. **`app/build.gradle.kts`：`targetSdk 35 → 34`**（`compileSdk` 仍 35）。targetSdk<35 的应用在 Android 15/16 不会被强制 edge-to-edge，系统状态栏区域照常保留，内容不再钻到状态栏下面。
2. 保留上一版 `res/values-v35/themes.xml` 的 `windowOptOutEdgeToEdgeEnforcement=true` 作为双保险。

## 验证（本机 Android14）
- 安装成功；`dumpsys package` → `targetSdk=34` ✔
- 原生版首页：状态栏与标题栏连续衔接、无重叠、无黑带（`fix14_native.png`）✔
- 3B 下载页：状态栏/工具栏/底部条均正常、无遮挡（`fix14_dl.png`）✔（空列表，无表头可测）

## 仍需
你的那部"会遮挡"的手机多半是 **Android 15/16**。请把 fix14 装到那部手机上确认；如果还有遮挡，把该机连上（开了 USB 调试）我直接看它那块屏实调（不同厂商 ROM 的 insets 行为可能各有差别）。
