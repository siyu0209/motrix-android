# 原生版 P1 三项：通知中心 + 主题深浅 + 多语言 · fix30

日期：2026-11-18　产物：`dist/motrix-android-2.4.3-fix30-debug.apk`
SHA-256：`E5F8466C036D35820194EA9B15C89EA14517AD91023B66A6563A7B033F42F31E`

一次性完成 P1 剩余三项：

## ① 通知中心（下载完成/失败历史）
- 新增 `NotificationStore`：把每个任务完成/失败记录持久化（名称、状态、时间、错误），最多保留 100 条，每个任务只记一次（用已通知 gid 去重，重启不重复）。
- 新增 `NotificationCenterActivity` + 列表页：标题「通知中心」、可「清空」，空态「暂无通知」；点 ⋮ 菜单 →「通知中心」进入。
- 真机验证：下载一个 10MB 文件完成后，通知中心正确列出「下载完成 · 11-18 20:03」。截图 `reports/notif1.png`、`reports/menu4.png`。

## ② 主题色 / 深浅色切换
- 色板拆成日间（`values/colors.xml` 浅色）+ 夜间（`values-night/colors.xml` 深色）；主题仍 `Theme.Material3.DayNight`。
- ⋮ 菜单 →「外观（深浅色）」：跟随系统 / 浅色 / 深色，选择即保存并即时生效。
- **关键修复**：MainActivity 原来 `configChanges` 里含 `uiMode`，导致切换深浅时系统不重建 Activity、颜色不刷新；已移除 `uiMode`，切换后自动重建、配色立即变。
- 真机验证：选「浅色」界面变白底浅色（截图 `reports/theme_light2.png`）。

## ③ 多语言（中文 / English）
- 全量新增 `values-en/strings.xml`（英文文案）；⋮ 菜单 →「语言」：跟随系统 / 中文 / English，选择即时切换（`AppCompatDelegate.setApplicationLocals` + 启动时按 `Prefs.langMode()` 应用）。
- 同时把首页计数行改为字符串资源 `counts_fmt`，英文下显示「0 active · 0 waiting · 0 finished」。
- 真机验证：切 English 后标题/按钮/图表/空态/计数全部英文（截图 `reports/lang_en2.png`）；随后已还原为「跟随系统」。

## 其它
- 完成后**已把主题与语言都还原为「跟随系统」**（即跟随你手机设置，当前为深色中文），不影响你日常使用。
- 三种能力均为增量，不改下载内核。

## 验证截图
`reports/menu4.png`（菜单新增三项）、`reports/notif1.png`（通知中心）、`reports/theme_light2.png`（浅色）、`reports/lang_en2.png`（英文）、`reports/fix30_final.png`（还原后）
